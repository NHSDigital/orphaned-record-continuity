import { logError, logInfo, logWarning } from '../../../middleware/logging';
import { transferOutEhrCore } from '../transfer-out-ehr-core';
import { getEhrCoreAndFragmentIdsFromRepo } from '../../ehr-repo/get-ehr';
import expect from 'expect';
import { v4 as uuid } from 'uuid';
import { sendCore } from '../../gp2gp/send-core';
import {
  PresignedUrlNotFoundError,
  DownloadError,
  MessageIdUpdateError,
  StatusUpdateError,
  PatientRecordNotFoundError,
  GetPdsCodeError
} from '../../../errors/errors';
import {
  createAndStoreOutboundMessageIds,
  getNewMessageIdForOldMessageId,
  patientAndPracticeOdsCodesMatch,
  replaceMessageIdsInObject,
  updateConversationStatus
} from '../transfer-out-util';
import { parseMessageId } from '../../parser/parsing-utilities';
import { cleanupRecordsForTest } from '../../../utilities/integration-test-utilities';
import {
  createOutboundConversation,
  getOutboundConversationById
} from '../../database/dynamodb/outbound-conversation-repository';
import {
  AcknowledgementErrorCode,
  ConversationStatus as conversationStatus,
  ConversationStatus,
  FailureReason
} from '../../../constants/enums';
import { sendAcknowledgement } from '../../gp2gp/send-acknowledgement';

// Mocking
jest.mock('../../gp2gp/send-core');
jest.mock('../../gp2gp/pds-retrieval-request');
jest.mock('../../gp2gp/send-acknowledgement');
jest.mock('../../ehr-repo/get-ehr');
jest.mock('../../database/dynamodb/outbound-conversation-repository');
jest.mock('../../../middleware/logging');
jest.mock('../transfer-out-util');
jest.mock('../../parser/parsing-utilities');

describe('transferOutEhrCore', () => {
  // ========================== CONSTANTS AND SETUP  ========================================
  const conversationId = '5BB36755-279F-43D5-86AB-DEFEA717D93F';
  const inboundConversationId = uuid().toUpperCase();
  const ehrRequestId = '870f6ef9-746f-4e81-b51f-884d64530bed';
  const incomingMessageId = '745A53BC-DC15-4706-9D49-1C96CDF76882';
  const messageId = '835A2B69-BAC0-4F6F-97A8-897350604380';
  const newMessageId = uuid().toUpperCase();
  const fragmentMessageIds = ['id1', 'id2', 'id3'];
  const messageIdWithReplacementsEhrCoreWithNoFragments = [
    { oldMessageId: messageId, newMessageId }
  ];
  const messageIdWithReplacementsEhrCoreWithFragments = [
    { oldMessageId: messageId, newMessageId },
    { oldMessageId: fragmentMessageIds[0], newMessageId: uuid().toUpperCase() },
    { oldMessageId: fragmentMessageIds[1], newMessageId: uuid().toUpperCase() },
    { oldMessageId: fragmentMessageIds[2], newMessageId: uuid().toUpperCase() }
  ];
  const odsCode = 'A12345';
  const nhsNumber = '1111111111';
  const ehrCore = {
    payload: 'payload XML',
    attachments: ['attachment 1', 'attachment 2'],
    external_attachments: ['ext attachment 1', 'ext attachment 2']
  };
  const ehrCoreWithUpdatedMessageId = {
    ...ehrCore,
    payload: 'payload XML with updated message ids'
  };
  const ehrCoreWithUpdatedReferencedFragmentMessageId = {
    ...ehrCore,
    payload: 'payload XML with updated referenced fragment message ids'
  };

  afterEach(async () => {
    jest.resetAllMocks();
    try {
      await cleanupRecordsForTest(inboundConversationId);
    } catch (error) {
      console.error('Error during cleanup after test:', error);
    }
  });

  // ========================== TEST STARTS HERE ========================================
  describe('transfer request validation checks', () => {
    it('should stop EHR transfer if the received EHR request is a duplicated one', async () => {
      // given
      const logInfoMessages = ['EHR transfer out request received'];
      const logWarningMessages = [
        `EHR out transfer with conversation ID ${conversationId} is already in progress`
      ];

      // when
      getOutboundConversationById.mockResolvedValueOnce('duplicate_transfer_request');
      await transferOutEhrCore({
        conversationId,
        nhsNumber,
        odsCode,
        ehrRequestId,
        incomingMessageId
      });

      // then
      expect(logInfo).toHaveBeenCalledTimes(1);
      expect(logInfo).toHaveBeenCalledWith(logInfoMessages[0]);
      expect(logWarning).toHaveBeenCalledTimes(1);
      expect(logWarning).toHaveBeenCalledWith(logWarningMessages[0]);
      expect(updateConversationStatus).not.toHaveBeenCalled();
      expect(sendCore).not.toHaveBeenCalled();
    });

    const testCasesForCoreTransferErrors = [
      {
        errorType: GetPdsCodeError,
        acknowledgementErrorCode: AcknowledgementErrorCode.ERROR_CODE_20_A,
        conversationStatus: conversationStatus.OUTBOUND_FAILED,
        failureReason: FailureReason.CORE_SENDING_FAILED
      },
      {
        errorType: PatientRecordNotFoundError,
        acknowledgementErrorCode: AcknowledgementErrorCode.ERROR_CODE_06_A
      },
      {
        errorType: DownloadError,
        acknowledgementErrorCode: AcknowledgementErrorCode.ERROR_CODE_10_A,
        conversationStatus: conversationStatus.OUTBOUND_FAILED,
        failureReason: FailureReason.EHR_DOWNLOAD_FAILED
      },
      {
        errorType: PresignedUrlNotFoundError,
        acknowledgementErrorCode: AcknowledgementErrorCode.ERROR_CODE_10_B,
        conversationStatus: conversationStatus.OUTBOUND_FAILED,
        failureReason: FailureReason.MISSING_FROM_REPO
      }
    ];

    it.each(testCasesForCoreTransferErrors)(
      'should stop the transfer and send negative acknowledgement if any part of the transfer failed with a $acknowledgementErrorCode.internalErrorCode $errorType.name',
      async ({ errorType, acknowledgementErrorCode, conversationStatus, failureReason }) => {
        // given
        // not all errorTypes have an external cause so don't have an 'error' field. We want it to always be null for this test anyway
        const error =
          errorType.length === 1
            ? new errorType(acknowledgementErrorCode)
            : new errorType(null, acknowledgementErrorCode);

        // when
        getOutboundConversationById.mockResolvedValueOnce(null);
        patientAndPracticeOdsCodesMatch.mockResolvedValue(true);
        updateConversationStatus.mockResolvedValue(undefined);
        getEhrCoreAndFragmentIdsFromRepo.mockRejectedValueOnce(error);
        await transferOutEhrCore({
          conversationId,
          nhsNumber,
          odsCode,
          ehrRequestId,
          incomingMessageId
        });

        // then
        expect(createOutboundConversation).toHaveBeenCalledWith(conversationId, nhsNumber, odsCode);
        expect(patientAndPracticeOdsCodesMatch).toHaveBeenCalledWith(nhsNumber, odsCode);
        expect(updateConversationStatus).toHaveBeenNthCalledWith(
          1,
          conversationId,
          ConversationStatus.OUTBOUND_STARTED
        );
        expect(sendAcknowledgement).toHaveBeenCalledWith(
          nhsNumber,
          odsCode,
          conversationId,
          incomingMessageId,
          acknowledgementErrorCode.gp2gpError
        );

        if (conversationStatus) {
          expect(updateConversationStatus).toHaveBeenNthCalledWith(
            2,
            conversationId,
            conversationStatus,
            failureReason
          );
        } else {
          expect(updateConversationStatus).toHaveBeenCalledTimes(1);
        }
        expect(sendCore).not.toHaveBeenCalled();
      }
    );

    it('should replace the main message ID in ehr core before sending out, if no fragment', async () => {
      // when
      getOutboundConversationById.mockResolvedValueOnce(null);
      patientAndPracticeOdsCodesMatch.mockResolvedValue(true);
      updateConversationStatus.mockResolvedValueOnce(undefined);
      getEhrCoreAndFragmentIdsFromRepo.mockResolvedValueOnce({ ehrCore, fragmentMessageIds: [] });
      parseMessageId.mockResolvedValueOnce(messageId);
      createAndStoreOutboundMessageIds.mockResolvedValueOnce(
        messageIdWithReplacementsEhrCoreWithNoFragments
      );
      replaceMessageIdsInObject.mockReturnValueOnce(ehrCoreWithUpdatedMessageId);
      getNewMessageIdForOldMessageId.mockReturnValueOnce(newMessageId);
      await transferOutEhrCore({
        conversationId,
        nhsNumber,
        odsCode,
        ehrRequestId,
        incomingMessageId
      });

      // then
      expect(replaceMessageIdsInObject).toBeCalledWith(
        ehrCore,
        messageIdWithReplacementsEhrCoreWithNoFragments
      );
      expect(sendCore).toBeCalledWith(
        conversationId,
        odsCode,
        ehrCoreWithUpdatedMessageId,
        ehrRequestId,
        newMessageId
      );
    });

    it('should create new Message IDs for a fragment and replace them in ehrCore, if the references them', async () => {
      // when
      getOutboundConversationById.mockResolvedValueOnce(null);
      createOutboundConversation.mockResolvedValueOnce(undefined);
      patientAndPracticeOdsCodesMatch.mockResolvedValue(true);
      updateConversationStatus.mockResolvedValueOnce(undefined);
      getEhrCoreAndFragmentIdsFromRepo.mockResolvedValueOnce({
        ehrCore,
        fragmentMessageIds,
        inboundConversationId
      });
      parseMessageId.mockResolvedValueOnce(messageId);
      createAndStoreOutboundMessageIds.mockResolvedValueOnce(
        messageIdWithReplacementsEhrCoreWithFragments
      );
      replaceMessageIdsInObject.mockReturnValueOnce(ehrCoreWithUpdatedReferencedFragmentMessageId);
      getNewMessageIdForOldMessageId.mockReturnValueOnce(newMessageId);
      await transferOutEhrCore({
        conversationId,
        nhsNumber,
        odsCode,
        ehrRequestId,
        incomingMessageId
      });

      // then
      expect(getEhrCoreAndFragmentIdsFromRepo).toHaveBeenCalledWith(nhsNumber, conversationId);
      expect(parseMessageId).toHaveBeenCalledWith(ehrCore);
      expect(createAndStoreOutboundMessageIds).toHaveBeenCalledWith(
        [messageId, ...fragmentMessageIds],
        inboundConversationId
      );
      expect(replaceMessageIdsInObject).toHaveBeenCalledWith(
        ehrCore,
        messageIdWithReplacementsEhrCoreWithFragments
      );
      expect(getNewMessageIdForOldMessageId).toHaveBeenCalledWith(
        messageId,
        messageIdWithReplacementsEhrCoreWithFragments
      );
      expect(sendCore).toBeCalledWith(
        conversationId,
        odsCode,
        ehrCoreWithUpdatedReferencedFragmentMessageId,
        ehrRequestId,
        newMessageId
      );
    });

    it('should send EHR Core on success', async () => {
      // when
      getOutboundConversationById.mockResolvedValueOnce(null);
      createOutboundConversation.mockResolvedValueOnce(undefined);
      patientAndPracticeOdsCodesMatch.mockResolvedValue(true);
      updateConversationStatus.mockResolvedValueOnce(undefined);
      getEhrCoreAndFragmentIdsFromRepo.mockResolvedValueOnce({ ehrCore, fragmentMessageIds });
      replaceMessageIdsInObject.mockReturnValueOnce(ehrCoreWithUpdatedMessageId);
      getNewMessageIdForOldMessageId.mockReturnValueOnce(newMessageId);

      await transferOutEhrCore({
        conversationId,
        nhsNumber,
        odsCode,
        ehrRequestId,
        incomingMessageId
      });

      // then
      expect(sendCore).toHaveBeenCalledTimes(1);
      expect(sendCore).toHaveBeenCalledWith(
        conversationId,
        odsCode,
        ehrCoreWithUpdatedMessageId,
        ehrRequestId,
        newMessageId
      );
    });

    it('should not send the EHR Core and should send negative acknowledgement if the patient NHS number cannot be found on the database', async () => {
      // given
      const acknowledgementErrorCode = AcknowledgementErrorCode.ERROR_CODE_06_A;
      const error = new PatientRecordNotFoundError(acknowledgementErrorCode);

      // when
      getOutboundConversationById.mockResolvedValueOnce(null);
      createOutboundConversation.mockRejectedValueOnce(error);
      sendAcknowledgement.mockResolvedValueOnce(null);

      await transferOutEhrCore({
        conversationId,
        nhsNumber,
        odsCode,
        ehrRequestId,
        incomingMessageId
      });

      // then
      expect(sendAcknowledgement).toHaveBeenCalledWith(
        nhsNumber,
        odsCode,
        conversationId,
        incomingMessageId,
        acknowledgementErrorCode.gp2gpError
      );
      expect(sendCore).not.toHaveBeenCalled();
    });

    it('should not send the EHR Core if the ODS code does not match in PDS', async () => {
      // given
      const message =
        "The patient's ODS Code in PDS does not match the requesting practice's ODS Code.";

      // when
      getOutboundConversationById.mockResolvedValueOnce(null);
      createOutboundConversation.mockResolvedValueOnce(undefined);
      patientAndPracticeOdsCodesMatch.mockResolvedValue(false);

      await transferOutEhrCore({
        conversationId,
        nhsNumber,
        odsCode,
        ehrRequestId,
        incomingMessageId
      });

      // then
      expect(getOutboundConversationById).toHaveBeenCalledWith(conversationId);
      expect(createOutboundConversation).toHaveBeenCalledWith(conversationId, nhsNumber, odsCode);
      expect(updateConversationStatus).toHaveBeenCalledWith(
        conversationId,
        ConversationStatus.OUTBOUND_FAILED,
        FailureReason.INCORRECT_ODS_CODE,
        message
      );
      expect(sendCore).not.toHaveBeenCalled();
    });

    it('should not send out the EHR Core if failed to update conversation status', async () => {
      // given
      const errorMessage = 'EHR transfer out request failed';
      const error = new StatusUpdateError();

      // when
      getOutboundConversationById.mockResolvedValueOnce(null);
      createOutboundConversation.mockResolvedValueOnce(undefined);
      patientAndPracticeOdsCodesMatch.mockResolvedValue(true);
      updateConversationStatus.mockRejectedValue(error);

      await transferOutEhrCore({
        conversationId,
        nhsNumber,
        odsCode,
        ehrRequestId,
        incomingMessageId
      });

      // then
      expect(getOutboundConversationById).toHaveBeenCalledWith(conversationId);
      expect(createOutboundConversation).toHaveBeenCalledWith(conversationId, nhsNumber, odsCode);
      expect(patientAndPracticeOdsCodesMatch).toHaveBeenCalledWith(nhsNumber, odsCode);
      expect(logError).toHaveBeenCalledWith(errorMessage, error);
      expect(sendCore).not.toBeCalled();
    });

    it('should not send out the EHR Core if we receive a fragment but the process to update its message ids fails', async () => {
      // when
      getOutboundConversationById.mockResolvedValueOnce(null);
      createOutboundConversation.mockResolvedValueOnce(undefined);
      patientAndPracticeOdsCodesMatch.mockResolvedValue(true);
      updateConversationStatus.mockResolvedValue(undefined);
      getEhrCoreAndFragmentIdsFromRepo.mockResolvedValueOnce({ ehrCore, fragmentMessageIds });
      replaceMessageIdsInObject.mockImplementationOnce(() => {
        throw new MessageIdUpdateError();
      });

      await transferOutEhrCore({
        conversationId,
        nhsNumber,
        odsCode,
        ehrRequestId,
        incomingMessageId
      });

      // then
      expect(getOutboundConversationById).toHaveBeenCalledWith(conversationId);

      expect(updateConversationStatus).toHaveBeenCalledTimes(2);
      expect(updateConversationStatus).toHaveBeenCalledWith(
        conversationId,
        ConversationStatus.OUTBOUND_STARTED
      );
      expect(updateConversationStatus).toHaveBeenCalledWith(
        conversationId,
        ConversationStatus.OUTBOUND_FAILED,
        FailureReason.CORE_SENDING_FAILED
      );
      expect(sendCore).not.toHaveBeenCalled();
    });

    // NOTE: Not migrating the previous test it('should update the registration request with the Outbound Message ID'),
    // as we will not be storing core message id at conversation level.
    // The outbound core message id will be stored during `getEhrCoreAndUpdateMessageIds`
  });
});
