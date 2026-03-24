import { v4 as uuid } from 'uuid';
import { EhrTransferTracker } from '../services/database/dynamodb/dynamo-ehr-transfer-tracker';
import {
  cleanupRecordsForTest,
  createInboundCompleteRecordForTest
} from '../utilities/integration-test-utilities';
import { FragmentStatus } from '../constants/enums';
import { isFragment } from '../models/fragment';
import { updateFragmentStatusInDb } from '../services/database/dynamodb/ehr-fragment-repository';

jest.mock('../middleware/logging');

describe('Database connection test', () => {
  // ================ CONSTANTS AND SETUPS =====================
  const NUMBER_OF_TRANSACTIONS = 100;

  const INBOUND_CONVERSATION_ID = uuid().toUpperCase();
  const NHS_NUMBER = '9000000001';
  const INBOUND_CORE_MESSAGE_ID = uuid().toUpperCase();
  const INBOUND_FRAGMENT_IDS = Array(NUMBER_OF_TRANSACTIONS)
    .fill('')
    .map(() => uuid().toUpperCase());
  const db = EhrTransferTracker.getInstance();

  beforeEach(async () => {
    await createInboundCompleteRecordForTest(
      INBOUND_CONVERSATION_ID,
      NHS_NUMBER,
      INBOUND_CORE_MESSAGE_ID,
      INBOUND_FRAGMENT_IDS
    );
  });

  afterEach(async () => {
    await cleanupRecordsForTest(INBOUND_CONVERSATION_ID);
  });

  it('should verify that the database connection pool is able to handle 100 concurrent transactions simultaneously', async () => {
    // given
    const databaseOperationPromises = [];
    for (const fragmentMessageId of INBOUND_FRAGMENT_IDS) {
      databaseOperationPromises.push(
        updateFragmentStatusInDb(
          INBOUND_CONVERSATION_ID,
          fragmentMessageId,
          FragmentStatus.OUTBOUND_SENT
        )
      );
    }

    // when
    await Promise.all(databaseOperationPromises);

    const recordsAfterTransaction =
      await db.queryTableByInboundConversationId(INBOUND_CONVERSATION_ID);
    const fragments = recordsAfterTransaction.filter(isFragment);
    // then
    fragments.forEach(fragment => {
      expect(fragment.TransferStatus).toEqual(FragmentStatus.OUTBOUND_SENT);
    });
  });
});
