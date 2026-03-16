import { v4 as uuid } from 'uuid';

import { NhsNumberNotFoundError } from '../../../../errors/errors';
import {
  cleanupRecordsForTest,
  createInboundCompleteRecordForTest,
  createInboundRecordWithConversationTransferStatusForTest
} from '../../../../utilities/integration-test-utilities';
import {
  createOutboundConversation,
  getNhsNumberByOutboundConversationId,
  getOutboundConversationById,
  updateOutboundConversationStatus
} from '../outbound-conversation-repository';
import {
  AcknowledgementErrorCode,
  ConversationStatus,
  CoreStatus,
  FailureReason,
  FragmentStatus,
  RecordType
} from '../../../../constants/enums';
import { logError, logInfo } from '../../../../middleware/logging';
import { EhrTransferTracker } from '../dynamo-ehr-transfer-tracker';
import { buildUpdateParamFromItem } from '../../../../utilities/dynamodb-helper';
import { isConversation } from '../../../../models/conversation';
import { isCore } from '../../../../models/core';
import { isFragment } from '../../../../models/fragment';

// Mocking
jest.mock('../../../../middleware/logging');

describe('outbound-conversation-repository', () => {
  // ================ CONSTANTS AND SETUPS =====================
  const INBOUND_CONVERSATION_ID = uuid().toUpperCase();
  const NHS_NUMBER = '9000000001';
  const INBOUND_CORE_MESSAGE_ID = uuid().toUpperCase();
  const INBOUND_FRAGMENT_IDS = [uuid().toUpperCase(), uuid().toUpperCase(), uuid().toUpperCase()];
  const ODS_CODE = 'B12345';
  const db = EhrTransferTracker.getInstance();

  afterAll(async () => {
    await cleanupRecordsForTest(INBOUND_CONVERSATION_ID);
  });

  // ================ TEST STARTS HERE =====================
  describe('createOutboundConversation', () => {
    it('should create outboundConversation with correct values', async () => {
      // given
      await createInboundCompleteRecordForTest(
        INBOUND_CONVERSATION_ID,
        NHS_NUMBER,
        INBOUND_CORE_MESSAGE_ID,
        INBOUND_FRAGMENT_IDS
      );

      const conversationId = uuid().toUpperCase();

      // when
      await createOutboundConversation(conversationId, NHS_NUMBER, ODS_CODE);

      // then
      const conversation = await getOutboundConversationById(conversationId);

      expect(conversation).not.toBeNull();
      expect(conversation.OutboundConversationId).toBe(conversationId);
      expect(conversation.NhsNumber).toBe(NHS_NUMBER);
      expect(conversation.DestinationGp).toBe(ODS_CODE);
      expect(conversation.InboundConversationId).toBe(INBOUND_CONVERSATION_ID);
      expect(conversation.TransferStatus).toBe(ConversationStatus.OUTBOUND_STARTED);
    });

    it('should log event if data persisted correctly', async () => {
      // given
      await createInboundCompleteRecordForTest(
        INBOUND_CONVERSATION_ID,
        NHS_NUMBER,
        INBOUND_CORE_MESSAGE_ID,
        INBOUND_FRAGMENT_IDS
      );

      const conversationId = uuid().toUpperCase();

      // when
      await createOutboundConversation(conversationId, NHS_NUMBER, ODS_CODE);

      // then
      expect(logInfo).toHaveBeenCalled();
      expect(logInfo).toHaveBeenCalledWith('Outbound conversation has been stored');
    });

    it('should throw when no conversation exists for NHS number', async () => {
      // given
      const conversationId = uuid().toUpperCase();
      const nhsNumber = '9000000002';
      let acknowledgementError;

      // when
      try {
        await createOutboundConversation(conversationId, nhsNumber, ODS_CODE);
      } catch (err) {
        acknowledgementError = err.acknowledgementErrorCode;
      }

      // then
      expect(acknowledgementError).toEqual(AcknowledgementErrorCode.ERROR_CODE_06_A);
    });

    it('should throw when conversation transfer status indicates EHR is not available to send out', async () => {
      // given
      await createInboundRecordWithConversationTransferStatusForTest(
        INBOUND_CONVERSATION_ID,
        NHS_NUMBER,
        INBOUND_CORE_MESSAGE_ID,
        ConversationStatus.INBOUND_FAILED,
        INBOUND_FRAGMENT_IDS
      );

      const conversationId = uuid().toUpperCase();
      let acknowledgementError;

      // when
      try {
        await createOutboundConversation(conversationId, NHS_NUMBER, ODS_CODE);
      } catch (err) {
        acknowledgementError = err.acknowledgementErrorCode;
      }

      // then
      expect(acknowledgementError).toEqual(AcknowledgementErrorCode.ERROR_CODE_06_B);
    });

    it('should log errors when nhs number is invalid', async () => {
      // given
      await createInboundCompleteRecordForTest(
        INBOUND_CONVERSATION_ID,
        NHS_NUMBER,
        INBOUND_CORE_MESSAGE_ID,
        INBOUND_FRAGMENT_IDS
      );

      const conversationId = uuid().toUpperCase();
      let errorMessage;

      // when
      try {
        await createOutboundConversation(conversationId, '123', ODS_CODE);
      } catch (err) {
        errorMessage = err.message;
      }

      // then
      expect(logError).toHaveBeenCalled();
      expect(errorMessage).toContain('NhsNumber must be a 10 digits number');
    });

    it('should log errors when conversationId is invalid', async () => {
      // given
      await createInboundCompleteRecordForTest(
        INBOUND_CONVERSATION_ID,
        NHS_NUMBER,
        INBOUND_CORE_MESSAGE_ID,
        INBOUND_FRAGMENT_IDS
      );

      let errorMessage;

      // when
      try {
        await createOutboundConversation('invalid-conversation-id', NHS_NUMBER, ODS_CODE);
      } catch (err) {
        errorMessage = err.message;
      }

      // then
      expect(logError).toHaveBeenCalled();
      expect(errorMessage).toContain('OutboundConversationId is not a valid UUID');
    });

    it('should clear any previous outbound record before starting new outbound transfer', async () => {
      // ========================= GIVEN =============================
      await createInboundCompleteRecordForTest(
        INBOUND_CONVERSATION_ID,
        NHS_NUMBER,
        INBOUND_CORE_MESSAGE_ID,
        INBOUND_FRAGMENT_IDS
      );

      const previousOutboundConversationId = uuid().toUpperCase();
      const previousDestinationGp = 'A12345';
      const newOutboundConversationId = uuid().toUpperCase();
      const items = await db.queryTableByInboundConversationId(
        INBOUND_CONVERSATION_ID,
        RecordType.ALL
      );
      const mockPreviousOutboundTransfer = items.map(item => {
        let changes = { OutboundConversationId: previousOutboundConversationId };
        if (isConversation(item)) {
          changes.TransferStatus = ConversationStatus.OUTBOUND_COMPLETE;
          changes.DestinationGp = previousDestinationGp;
        } else {
          changes.TransferStatus = FragmentStatus.OUTBOUND_COMPLETE;
          changes.OutboundMessageId = uuid().toUpperCase();
        }
        return buildUpdateParamFromItem(item, changes);
      });
      await db.updateItemsInTransaction(mockPreviousOutboundTransfer);

      // to verify the mock previous outbound record was created correctly
      const previousRecord = await db.queryTableByOutboundConversationId(
        previousOutboundConversationId
      );
      expect(previousRecord).toHaveLength(5);

      // ========================= WHEN =============================
      await createOutboundConversation(newOutboundConversationId, NHS_NUMBER, ODS_CODE);

      // ========================= THEN =============================
      const conversation = await getOutboundConversationById(newOutboundConversationId);

      expect(conversation.OutboundConversationId).toBe(newOutboundConversationId);
      expect(conversation.InboundConversationId).toBe(INBOUND_CONVERSATION_ID);
      expect(conversation.NhsNumber).toBe(NHS_NUMBER);
      expect(conversation.DestinationGp).toBe(ODS_CODE);
      expect(conversation.TransferStatus).toBe(ConversationStatus.OUTBOUND_STARTED);

      const updatedRecords = await db.queryTableByOutboundConversationId(newOutboundConversationId);
      expect(updatedRecords).toHaveLength(5); // conversation + core + 3 fragments
      const coreAndFragments = [
        ...updatedRecords.filter(isCore),
        ...updatedRecords.filter(isFragment)
      ];

      for (const item of coreAndFragments) {
        expect(item.OutboundConversationId).toBe(newOutboundConversationId);
        expect(item.InboundConversationId).toBe(INBOUND_CONVERSATION_ID);
        expect(item.TransferStatus).toBe(CoreStatus.INBOUND_COMPLETE);
        expect(item.OutboundMessageId).toBeUndefined();
      }

      const previousRecordAfterward = await getOutboundConversationById(
        previousOutboundConversationId
      );
      expect(previousRecordAfterward).toBeNull();
    });
  });

  describe('getOutboundConversationById', () => {
    it('should retrieve the outbound conversation by conversation id', async () => {
      // given
      await createInboundCompleteRecordForTest(
        INBOUND_CONVERSATION_ID,
        NHS_NUMBER,
        INBOUND_CORE_MESSAGE_ID,
        INBOUND_FRAGMENT_IDS
      );

      const conversationId = uuid().toUpperCase();
      await createOutboundConversation(conversationId, NHS_NUMBER, ODS_CODE);

      // when
      const outboundConversation = await getOutboundConversationById(conversationId);

      // then
      const expectedStatus = ConversationStatus.OUTBOUND_STARTED;
      expect(outboundConversation.NhsNumber).toBe(NHS_NUMBER);
      expect(outboundConversation.TransferStatus).toBe(expectedStatus);
      expect(outboundConversation.DestinationGp).toBe(ODS_CODE);
      expect(outboundConversation.OutboundConversationId).toBe(conversationId);
    });

    it('should return null when it cannot find the outbound conversation', async () => {
      // given
      await createInboundCompleteRecordForTest(
        INBOUND_CONVERSATION_ID,
        NHS_NUMBER,
        INBOUND_CORE_MESSAGE_ID,
        INBOUND_FRAGMENT_IDS
      );

      const nonExistentConversationId = uuid().toUpperCase();

      // when
      const result = await getOutboundConversationById(nonExistentConversationId);

      // then
      expect(result).toBeNull();
    });
  });

  describe('updateOutboundConversationStatus', () => {
    it('should change the TransferStatus of conversation', async () => {
      // given
      await createInboundCompleteRecordForTest(
        INBOUND_CONVERSATION_ID,
        NHS_NUMBER,
        INBOUND_CORE_MESSAGE_ID,
        INBOUND_FRAGMENT_IDS
      );

      const conversationId = uuid().toUpperCase();
      const status = ConversationStatus.OUTBOUND_CONTINUE_REQUEST_RECEIVED;
      await createOutboundConversation(conversationId, NHS_NUMBER, ODS_CODE);

      // when
      await updateOutboundConversationStatus(conversationId, status);

      const outboundConversation = await getOutboundConversationById(conversationId);
      const conversationStatus = outboundConversation.TransferStatus;
      // then
      expect(conversationStatus).toBe(status);
    });

    it('should be able to store a failure reason if given', async () => {
      // given
      await createInboundCompleteRecordForTest(
        INBOUND_CONVERSATION_ID,
        NHS_NUMBER,
        INBOUND_CORE_MESSAGE_ID,
        INBOUND_FRAGMENT_IDS
      );

      const conversationId = uuid().toUpperCase();
      const status = ConversationStatus.OUTBOUND_FAILED;
      const failureReason = FailureReason.EHR_DOWNLOAD_FAILED;
      await createOutboundConversation(conversationId, NHS_NUMBER, ODS_CODE);

      // when
      await updateOutboundConversationStatus(conversationId, status, failureReason);

      const outboundConversation = await getOutboundConversationById(conversationId);

      // then
      expect(outboundConversation.TransferStatus).toBe(status);
      expect(outboundConversation.FailureReason).toBe(failureReason);
    });
  });

  describe('getNhsNumberByOutboundConversationId', () => {
    it('should return the nhs number of a registration-request', async () => {
      // given
      await createInboundCompleteRecordForTest(
        INBOUND_CONVERSATION_ID,
        NHS_NUMBER,
        INBOUND_CORE_MESSAGE_ID,
        INBOUND_FRAGMENT_IDS
      );

      const conversationId = uuid().toUpperCase();
      await createOutboundConversation(conversationId, NHS_NUMBER, ODS_CODE);

      // when
      const returnedNhsNumber = await getNhsNumberByOutboundConversationId(conversationId);

      // then
      expect(returnedNhsNumber).toEqual(NHS_NUMBER);
    });

    it('should throw an error if cannot find the nhs number related to given conversation id', async () => {
      // given
      await createInboundCompleteRecordForTest(
        INBOUND_CONVERSATION_ID,
        NHS_NUMBER,
        INBOUND_CORE_MESSAGE_ID,
        INBOUND_FRAGMENT_IDS
      );

      const conversationId = uuid().toUpperCase();

      // when
      await expect(getNhsNumberByOutboundConversationId(conversationId))
        // then
        .rejects.toThrow(NhsNumberNotFoundError);
    });
  });
});
