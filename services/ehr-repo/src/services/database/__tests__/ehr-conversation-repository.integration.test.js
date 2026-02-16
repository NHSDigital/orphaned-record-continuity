import { v4 as uuid } from 'uuid';
import { logError } from '../../../middleware/logging';
import {
  getConversationById,
  getCurrentConversationIdForPatient,
  getMessageIdsForConversation,
  getConversationStatus,
  markRecordAsSoftDeleteForPatient,
  updateConversationCompleteness,
  updateConversationToCoreReceived
} from '../ehr-conversation-repository';
import { ConversationStatus, HealthRecordStatus, RecordType } from '../../../models/enums';
import {
  cleanupRecordsForTest,
  createConversationForTest
} from '../../../utilities/integration-test-utilities';
import { createCore } from '../ehr-core-repository';
import { EhrTransferTracker } from '../dynamo-ehr-transfer-tracker';
import { markFragmentAsReceivedAndCreateItsParts } from '../ehr-fragment-repository';
import { HealthRecordNotFoundError, CoreNotFoundError } from '../../../errors/errors';
import moment from 'moment-timezone';

jest.mock('../../../middleware/logging');

describe('ehr-conversation-repository', () => {
  // ========================= COMMON PROPERTIES =========================
  const db = EhrTransferTracker.getInstance();
  const markFragmentAsReceived = markFragmentAsReceivedAndCreateItsParts;
  const fail = (reason) => {
    throw new Error(reason);
  };
  const tableNameBackup = db.tableName;
  const mimicDynamodbFail = () => {
    db.tableName = 'non-exist-table';
  };
  const undoMimicDynamodbFail = () => {
    db.tableName = tableNameBackup;
  };
  // ========================= SET UP / TEAR DOWN ========================
  afterEach(() => {
    undoMimicDynamodbFail();
  });
  // =====================================================================

  describe('getConversationStatus', () => {
    it("should return status 'complete' when conversation status is Complete", async () => {
      // given
      const conversationId = uuid().toUpperCase();
      const nhsNumber = '1234567890';
      await createConversationForTest(conversationId, nhsNumber, {
        TransferStatus: ConversationStatus.COMPLETE
      });

      // when
      const status = await getConversationStatus(conversationId);

      // then
      expect(status).toEqual(HealthRecordStatus.COMPLETE);
    });

    it("should return status 'pending' when conversation status is not Complete", async () => {
      // given
      const conversationId = uuid().toUpperCase();
      const nhsNumber = '1234567890';
      await createConversationForTest(conversationId, nhsNumber, {
        TransferStatus: ConversationStatus.CONTINUE_REQUEST_SENT
      });

      // when
      const status = await getConversationStatus(conversationId);

      // then
      expect(status).toEqual(HealthRecordStatus.PENDING);
    });

    it("should return status 'notFound' when health record is not found", async () => {
      // given
      const conversationId = uuid().toUpperCase();

      // when
      const status = await getConversationStatus(conversationId);

      // then
      expect(status).toEqual(HealthRecordStatus.NOT_FOUND);
    });

    it('should throw error if there is a problem retrieving health record from database', async () => {
      // given
      const conversationId = uuid().toUpperCase();
      mimicDynamodbFail();

      try {
        // when
        await getConversationStatus(conversationId);
        fail('should have throw');
      } catch (err) {
        // then
        expect(err).not.toBeNull();
        expect(logError).toHaveBeenCalledWith(
          'Health Record could not be retrieved from database',
          err
        );
      }
    });
  });

  describe('updateConversationCompleteness', () => {
    it("should set conversation state to 'Complete' for a small health record", async () => {
      // given
      const conversationId = uuid().toUpperCase();
      const messageId = uuid().toUpperCase();
      const nhsNumber = '1234567890';
      await createConversationForTest(conversationId, nhsNumber, {
        TransferStatus: ConversationStatus.REQUEST_SENT
      });
      await createCore({ conversationId, messageId, fragmentMessageIds: [] });

      // when
      await updateConversationCompleteness(conversationId);

      // then
      const conversation = await getConversationById(conversationId);

      expect(conversation.TransferStatus).toBe(ConversationStatus.COMPLETE);
    });

    it('should not set State to Complete if there are still messages to be received', async () => {
      // given
      const conversationId = uuid().toUpperCase();
      const messageId = uuid().toUpperCase();
      const fragmentMessageId = uuid().toUpperCase();
      const nhsNumber = '1234567890';

      await createConversationForTest(conversationId, nhsNumber, {
        TransferStatus: ConversationStatus.CONTINUE_REQUEST_SENT
      });
      await createCore({ conversationId, messageId, fragmentMessageIds: [fragmentMessageId] });

      // when
      await updateConversationCompleteness(conversationId);

      // then
      const conversation = await getConversationById(conversationId);

      expect(conversation.TransferStatus).toBe(ConversationStatus.CONTINUE_REQUEST_SENT);
    });

    it('Should set State to Complete if all fragments are received', async () => {
      // given
      const conversationId = uuid().toUpperCase();
      const messageId = uuid().toUpperCase();
      const fragmentMessageIds = [uuid().toUpperCase(), uuid().toUpperCase(), uuid().toUpperCase()];
      const nhsNumber = '1234567890';

      await createConversationForTest(conversationId, nhsNumber, {
        TransferStatus: ConversationStatus.CONTINUE_REQUEST_SENT
      });
      await createCore({ conversationId, messageId, fragmentMessageIds: fragmentMessageIds });
      for (const fragmentId of fragmentMessageIds) {
        await markFragmentAsReceived(fragmentId, conversationId);
      }

      // when
      await updateConversationCompleteness(conversationId);

      // then
      const conversation = await getConversationById(conversationId);

      expect(conversation.TransferStatus).toBe(ConversationStatus.COMPLETE);
    });

    it('should throw an error when database query fails', async () => {
      // given
      const conversationId = 'not-valid';
      mimicDynamodbFail();

      try {
        // when
        await updateConversationCompleteness(conversationId);
        fail('should have throw');
      } catch (err) {
        // then
        expect(err).not.toBeNull();
        expect(logError).toHaveBeenCalledWith('Failed to update health record completeness', err);
      }
    });
  });

  describe('getCurrentConversationIdForPatient', () => {
    it('should return most recent complete health record conversation id', async () => {
      // given
      const nhsNumber = '9876543212';
      const previousHealthRecordConversationId = uuid().toUpperCase();
      const incompleteHealthRecordConversationId = uuid().toUpperCase();
      const currentHealthRecordConversationId = uuid().toUpperCase();

      await createConversationForTest(previousHealthRecordConversationId, nhsNumber, {
        TransferStatus: ConversationStatus.COMPLETE,
        CreatedAt: '2024-01-01T10:00:00+00:00'
      });

      await createConversationForTest(incompleteHealthRecordConversationId, nhsNumber, {
        TransferStatus: ConversationStatus.TIMEOUT
      });

      await createConversationForTest(currentHealthRecordConversationId, nhsNumber, {
        TransferStatus: ConversationStatus.COMPLETE
      });

      // when
      const actual = await getCurrentConversationIdForPatient(nhsNumber);

      // then
      expect(actual).toEqual(currentHealthRecordConversationId);
    });

    it('should throw an error if no complete health record is found', async () => {
      // given
      const nhsNumber = '9876543211';
      const incompleteHealthRecordConversationId = uuid().toUpperCase();
      await createConversationForTest(incompleteHealthRecordConversationId, nhsNumber, {
        TransferStatus: ConversationStatus.TIMEOUT
      });

      // when
      await expect(() => getCurrentConversationIdForPatient(nhsNumber))
        // then
        .rejects.toThrow(HealthRecordNotFoundError);
    });

    it('should throw an error when cannot find any health record', async () => {
      // given
      const nhsNumber = '1111111112';

      // when
      await expect(() => getCurrentConversationIdForPatient(nhsNumber))
        // then
        .rejects.toThrow(HealthRecordNotFoundError);
    });
  });

  describe('getMessageIdsForConversation', () => {
    it('should throw an error if no message found with given conversationId', async () => {
      // given
      const conversationId = uuid().toUpperCase();

      // when
      await expect(() => getMessageIdsForConversation(conversationId))
        // then
        .rejects.toThrow(CoreNotFoundError);
    });

    it('should return health record extract message id given a conversation id for a small health record', async () => {
      // given
      const messageId = uuid().toUpperCase();
      const conversationId = uuid().toUpperCase();
      await createCore({ conversationId, messageId, fragmentMessageIds: [] });

      // when
      const { coreMessageId, fragmentMessageIds } =
        await getMessageIdsForConversation(conversationId);

      // then
      expect(coreMessageId).toEqual(messageId);
      expect(fragmentMessageIds).toEqual([]);
    });

    it('should return health record extract message id and fragment message ids given singular fragment', async () => {
      // given
      const messageId = uuid().toUpperCase();
      const conversationId = uuid().toUpperCase();
      const fragmentMessageId = uuid().toUpperCase();

      await createCore({ conversationId, messageId, fragmentMessageIds: [fragmentMessageId] });
      await markFragmentAsReceived(fragmentMessageId, conversationId);

      // when
      const { coreMessageId, fragmentMessageIds } =
        await getMessageIdsForConversation(conversationId);

      // then
      expect(coreMessageId).toEqual(messageId);
      expect(fragmentMessageIds).toEqual([fragmentMessageId]);
    });

    it('should return health record extract message id and fragment message ids given nested fragments', async () => {
      // given
      const messageId = uuid().toUpperCase();
      const conversationId = uuid().toUpperCase();
      const fragmentMessageId = uuid().toUpperCase();
      const nestedFragmentId = uuid().toUpperCase();

      await createCore({ conversationId, messageId, fragmentMessageIds: [fragmentMessageId] });
      await markFragmentAsReceivedAndCreateItsParts(fragmentMessageId, conversationId, [
        nestedFragmentId
      ]);
      await markFragmentAsReceived(nestedFragmentId, conversationId);

      // when
      const { coreMessageId, fragmentMessageIds } =
        await getMessageIdsForConversation(conversationId);

      // then
      expect(coreMessageId).toEqual(messageId);
      expect(fragmentMessageIds.sort()).toEqual([fragmentMessageId, nestedFragmentId].sort());
    });
  });

  describe('markAllRecordAsDeletedForPatient', () => {
    // ========================= HELPER SETUPS FOR THIS BLOCK =========================
    let conversationIdUsed = [];
    const mockTime = new Date(Date.parse('2024-03-06T12:34:56+00:00'));

    beforeEach(async () => {
      jest.useFakeTimers().setSystemTime(mockTime);
    });

    afterEach(async () => {
      await Promise.all(conversationIdUsed.map(cleanupRecordsForTest));
      conversationIdUsed = [];
    });

    const makeConversationIdForTest = () => {
      const id = uuid().toUpperCase();
      conversationIdUsed.push(id);
      return id;
    };

    const createCompleteRecordForTest = async (
      nhsNumber,
      conversationId,
      messageId,
      fragmentMessageIds = []
    ) => {
      await createConversationForTest(conversationId, nhsNumber, {
        TransferStatus: ConversationStatus.COMPLETE
      });
      await createCore({ conversationId, messageId, fragmentMessageIds });
      for (const fragmentMessageId of fragmentMessageIds) {
        await markFragmentAsReceived(fragmentMessageId, conversationId);
      }
    };

    // ========================= TESTS BEGINS HERE =================================
    it('should return conversation id for the patient marked as deleted', async () => {
      // given
      const nhsNumber = '9898989898';
      const messageId = uuid().toUpperCase();
      const conversationId = makeConversationIdForTest();
      const fragmentIds = [uuid().toUpperCase(), uuid().toUpperCase(), uuid().toUpperCase()];
      await createCompleteRecordForTest(nhsNumber, conversationId, messageId, fragmentIds);

      // when
      const result = await markRecordAsSoftDeleteForPatient(nhsNumber);

      // then
      const healthRecordStatusAfterwards = await getConversationStatus(conversationId);
      expect(result).toEqual([conversationId]);
      expect(healthRecordStatusAfterwards).toEqual(HealthRecordStatus.COMPLETE);

      const deletedRecords = await db.queryTableByConversationId(
        conversationId,
        RecordType.ALL,
        true
      );
      expect(deletedRecords).toHaveLength(5); // conversation + core + 3 fragments

      const expectedDeletedAtTime = moment(mockTime).add(8, 'week').unix();

      for (const item of deletedRecords) {
        expect(item).toMatchObject({
          InboundConversationId: conversationId,
          DeletedAt: expectedDeletedAtTime
        });
      }
    });

    it('should return conversation ids for the patient marked as deleted when the patient has several health records', async () => {
      // given
      const nhsNumber = '6767676767';
      const firstMessageId = uuid().toUpperCase();
      const secondMessageId = uuid().toUpperCase();
      const firstConversationId = makeConversationIdForTest();
      const secondConversationId = makeConversationIdForTest();

      await createCompleteRecordForTest(nhsNumber, firstConversationId, firstMessageId);
      await createCompleteRecordForTest(nhsNumber, secondConversationId, secondMessageId);

      // when
      const result = await markRecordAsSoftDeleteForPatient(nhsNumber);

      // then
      expect(result).toHaveLength(2);
      expect(result.sort()).toEqual([firstConversationId, secondConversationId].sort());

      const deletedRecords = (
        await Promise.all(
          result.map((conversationId) =>
            db.queryTableByConversationId(conversationId, RecordType.ALL, true)
          )
        )
      ).flat();

      expect(deletedRecords).toHaveLength(4); // two sets of conversation + core

      const expectedDeletedAtTime = moment(mockTime).add(8, 'week').unix();

      for (const item of deletedRecords) {
        expect(item).toMatchObject({
          DeletedAt: expectedDeletedAtTime
        });
      }
    });
  });
  describe('updateConversationToCoreReceived', () => {
    it(`should set conversation state to ${ConversationStatus.CORE_RECEIVED} for a small health record`, async () => {
      // given
      const conversationId = uuid().toUpperCase();
      const messageId = uuid().toUpperCase();
      const nhsNumber = '1234567890';
      await createConversationForTest(conversationId, nhsNumber, {
        TransferStatus: ConversationStatus.REQUEST_SENT
      });
      await createCore({ conversationId, messageId, fragmentMessageIds: [] });

      // when
      await updateConversationToCoreReceived(conversationId);

      // then
      const conversation = await getConversationById(conversationId);

      expect(conversation.TransferStatus).toBe(ConversationStatus.CORE_RECEIVED);
    });

    it(`should set conversation state to ${ConversationStatus.CORE_RECEIVED} for a large health record`, async () => {
      // given
      const conversationId = uuid().toUpperCase();
      const messageId = uuid().toUpperCase();
      const fragmentMessageId = uuid().toUpperCase();
      const nhsNumber = '1234567890';
      await createConversationForTest(conversationId, nhsNumber, {
        TransferStatus: ConversationStatus.REQUEST_SENT
      });
      await createCore({ conversationId, messageId, fragmentMessageIds: [fragmentMessageId] });

      // when
      await updateConversationToCoreReceived(conversationId);

      // then
      const conversation = await getConversationById(conversationId);

      expect(conversation.TransferStatus).toBe(ConversationStatus.CORE_RECEIVED);
    });
  });
});
