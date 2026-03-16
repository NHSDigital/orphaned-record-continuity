import { v4 as uuid } from 'uuid';
import {
  buildMessageIdReplacement,
  cleanupRecordsForTest,
  createInboundCompleteRecordForTest,
  createSmallEhrRecord
} from '../../../../utilities/integration-test-utilities';
import { createOutboundConversation } from '../outbound-conversation-repository';
import { storeOutboundMessageIds } from '../store-outbound-message-ids';
import {
  getCoreByOutboundConversationId,
  messageIdMatchOutboundCore,
  updateCoreStatusInDb
} from '../ehr-core-repository';
import { CoreStatus } from '../../../../constants/enums';

const INBOUND_CONVERSATION_ID = uuid().toUpperCase();
const NHS_NUMBER = '9000000001';
const INBOUND_CORE_MESSAGE_ID = uuid().toUpperCase();
const INBOUND_FRAGMENT_IDS = [uuid().toUpperCase(), uuid().toUpperCase(), uuid().toUpperCase()];
const ODS_CODE = 'B12345';

describe('ehr-core-repository', () => {
  afterAll(async () => {
    await cleanupRecordsForTest(INBOUND_CONVERSATION_ID);
  });

  describe('messageIdMatchOutboundCore', () => {
    beforeEach(async () => {
      await createInboundCompleteRecordForTest(
        INBOUND_CONVERSATION_ID,
        NHS_NUMBER,
        INBOUND_CORE_MESSAGE_ID,
        INBOUND_FRAGMENT_IDS
      );
    });

    it('should return true when the given messageId match the OutboundMessageId of core', async () => {
      // given
      const outboundConversationId = uuid().toUpperCase();
      const outboundCoreMessageId = uuid().toUpperCase();
      const outboundFragmentIds = INBOUND_FRAGMENT_IDS.map(() => uuid().toUpperCase());
      const messageIdReplacement = buildMessageIdReplacement(
        [INBOUND_CORE_MESSAGE_ID, ...INBOUND_FRAGMENT_IDS],
        [outboundCoreMessageId, ...outboundFragmentIds]
      );

      // when
      await createOutboundConversation(outboundConversationId, NHS_NUMBER, ODS_CODE);
      await storeOutboundMessageIds(messageIdReplacement, INBOUND_CONVERSATION_ID);

      const result = await messageIdMatchOutboundCore(
        outboundConversationId,
        outboundCoreMessageId
      );

      // then
      expect(result).toBe(true);
    });

    it('should return false if the given messageId does not match OutboundMessageId of core', async () => {
      // given
      const outboundConversationId = uuid().toUpperCase();
      const outboundCoreMessageId = uuid().toUpperCase();
      const outboundFragmentIds = INBOUND_FRAGMENT_IDS.map(() => uuid().toUpperCase());
      const messageIdReplacement = buildMessageIdReplacement(
        [INBOUND_CORE_MESSAGE_ID, ...INBOUND_FRAGMENT_IDS],
        [outboundCoreMessageId, ...outboundFragmentIds]
      );

      const nonExistMessageId = uuid().toUpperCase();

      // when
      await createOutboundConversation(outboundConversationId, NHS_NUMBER, ODS_CODE);
      await storeOutboundMessageIds(messageIdReplacement, INBOUND_CONVERSATION_ID);

      const result = await messageIdMatchOutboundCore(outboundConversationId, nonExistMessageId);

      // then
      expect(result).toBe(false);
    });
  });

  describe('updateCoreStatusInDb', () => {
    it('should update the CORE status successfully', async () => {
      // given
      const outboundConversationId = uuid().toUpperCase();
      const status = CoreStatus.OUTBOUND_SENT;

      await createSmallEhrRecord(
        INBOUND_CONVERSATION_ID,
        outboundConversationId,
        NHS_NUMBER,
        INBOUND_CORE_MESSAGE_ID
      );

      await updateCoreStatusInDb(outboundConversationId, status);
      const result = await getCoreByOutboundConversationId(outboundConversationId);
      const resultStatus = result.TransferStatus;
      // then
      expect(resultStatus).toEqual(status);
    });
  });
});
