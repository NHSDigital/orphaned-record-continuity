import { logInfo, logError } from '../../../../middleware/logging';
import { v4 } from 'uuid';
import { MessageIdUpdateError, ValidationError } from '../../../../errors/errors';
import { storeOutboundMessageIds } from '../store-outbound-message-ids';
import {
  cleanupRecordsForTest,
  createInboundCompleteRecordForTest
} from '../../../../utilities/integration-test-utilities';
import { EhrTransferTracker } from '../dynamo-ehr-transfer-tracker';
import { createOutboundConversation } from '../outbound-conversation-repository';
import { isCore } from '../../../../models/core';
import { RecordType } from '../../../../constants/enums';
import { isFragment } from '../../../../models/fragment';

jest.mock('../../../../middleware/logging');

describe('storeOutboundMessageIds', () => {
  // ================ CONSTANTS AND SETUPS =====================
  const uuid = () => v4().toUpperCase();
  const INBOUND_CONVERSATION_ID = uuid().toUpperCase();
  const NHS_NUMBER = '9000000001';
  const INBOUND_CORE_MESSAGE_ID = uuid().toUpperCase();
  const INBOUND_FRAGMENT_IDS = [uuid().toUpperCase(), uuid().toUpperCase(), uuid().toUpperCase()];
  const ODS_CODE = 'B12345';
  const db = EhrTransferTracker.getInstance();

  afterEach(async () => {
    await cleanupRecordsForTest(INBOUND_CONVERSATION_ID);
  });

  // ================ TEST STARTS HERE =====================
  it('should store the outbound message IDs in database', async () => {
    // given
    const outboundConversationId = uuid().toUpperCase();
    const outboundCoreMessageId = uuid().toUpperCase();
    const outboundFragmentMessageIds = INBOUND_FRAGMENT_IDS.map(uuid);
    let messageIdReplacements = [
      {
        oldMessageId: INBOUND_CORE_MESSAGE_ID,
        newMessageId: outboundCoreMessageId
      }
    ];
    INBOUND_FRAGMENT_IDS.forEach((_, i) => {
      messageIdReplacements.push({
        oldMessageId: INBOUND_FRAGMENT_IDS[i],
        newMessageId: outboundFragmentMessageIds[i]
      });
    });
    await createInboundCompleteRecordForTest(
      INBOUND_CONVERSATION_ID,
      NHS_NUMBER,
      INBOUND_CORE_MESSAGE_ID,
      INBOUND_FRAGMENT_IDS
    );
    await createOutboundConversation(outboundConversationId, NHS_NUMBER, ODS_CODE);

    // when
    await storeOutboundMessageIds(messageIdReplacements, INBOUND_CONVERSATION_ID);

    // then
    const records = await db.queryTableByOutboundConversationId(outboundConversationId);
    const core = records.filter(isCore)[0];

    expect(core).toMatchObject({
      Layer: RecordType.CORE,
      InboundConversationId: INBOUND_CONVERSATION_ID,
      InboundMessageId: INBOUND_CORE_MESSAGE_ID,
      OutboundConversationId: outboundConversationId,
      OutboundMessageId: outboundCoreMessageId
    });

    const fragments = records.filter(isFragment);
    expect(fragments.length).toBe(INBOUND_FRAGMENT_IDS.length);

    fragments.forEach(fragment => {
      const inboundMessageId = fragment.InboundMessageId;
      const expectedOutboundMessageId = messageIdReplacements.find(
        pair => pair.oldMessageId === inboundMessageId
      ).newMessageId;
      expect(expectedOutboundMessageId).not.toBeNull();

      expect(fragment).toMatchObject({
        Layer: `FRAGMENT#${inboundMessageId}`,
        InboundConversationId: INBOUND_CONVERSATION_ID,
        InboundMessageId: inboundMessageId,
        OutboundConversationId: outboundConversationId,
        OutboundMessageId: expectedOutboundMessageId
      });
    });
  });

  it('should log event if data persisted correctly', async () => {
    // given
    const oldMessageId = INBOUND_CORE_MESSAGE_ID;
    const newMessageId = uuid().toUpperCase();
    const outboundConversationId = uuid().toUpperCase();
    await createInboundCompleteRecordForTest(
      INBOUND_CONVERSATION_ID,
      NHS_NUMBER,
      INBOUND_CORE_MESSAGE_ID
    );
    await createOutboundConversation(outboundConversationId, NHS_NUMBER, ODS_CODE);

    // when
    await storeOutboundMessageIds([{ oldMessageId, newMessageId }], INBOUND_CONVERSATION_ID);

    // then
    const expectedLogMessage = 'Recorded outbound message IDs in database';
    expect(logInfo).toHaveBeenCalledWith(expectedLogMessage);
  });

  it('should throw an error when oldMessageId is invalid', async () => {
    // given
    const oldMessageId = 'INVALID-OLD-MESSAGE-ID';
    const newMessageId = uuid().toUpperCase();
    const outboundConversationId = uuid().toUpperCase();
    await createInboundCompleteRecordForTest(
      INBOUND_CONVERSATION_ID,
      NHS_NUMBER,
      INBOUND_CORE_MESSAGE_ID
    );
    await createOutboundConversation(outboundConversationId, NHS_NUMBER, ODS_CODE);

    // when
    await expect(storeOutboundMessageIds([{ oldMessageId, newMessageId }], INBOUND_CONVERSATION_ID))
      // then
      .rejects.toThrow(MessageIdUpdateError);

    expect(logError).toHaveBeenCalledWith(
      expect.stringMatching('Failed while trying to store outbound message ids in database'),
      expect.stringMatching(
        'Input array `messageIdReplacements` does not match the actual InboundMessageId records in database'
      )
    );
  });

  it('should throw an error when newMessageId is invalid', async () => {
    // given
    const oldMessageId = INBOUND_CORE_MESSAGE_ID;
    const newMessageId = 'INVALID-NEW-MESSAGE-ID';
    const outboundConversationId = uuid().toUpperCase();
    await createInboundCompleteRecordForTest(
      INBOUND_CONVERSATION_ID,
      NHS_NUMBER,
      INBOUND_CORE_MESSAGE_ID
    );
    await createOutboundConversation(outboundConversationId, NHS_NUMBER, ODS_CODE);

    // when
    await expect(storeOutboundMessageIds([{ oldMessageId, newMessageId }], INBOUND_CONVERSATION_ID))
      // then
      .rejects.toThrow(ValidationError);

    expect(logError).toHaveBeenCalledWith(
      expect.stringMatching('OutboundMessageIds must be valid UUID')
    );
  });
});
