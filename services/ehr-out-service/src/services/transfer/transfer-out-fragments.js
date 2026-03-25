import {
  getFragment,
  getFragmentConversationAndMessageIdsFromEhrRepo
} from '../ehr-repo/get-fragment';
import { setCurrentSpanAttributes } from '../../config/tracing';
import { replaceMessageIdsInObject, updateFragmentStatus } from './transfer-out-util';
import { sendFragment } from '../gp2gp/send-fragment';
import { logError, logInfo } from '../../middleware/logging';
import { config } from '../../config';
import { DownloadError, PresignedUrlNotFoundError } from '../../errors/errors';
import {
  getAllFragmentIdsToBeSent,
  getAllMessageIdPairs
} from '../database/dynamodb/ehr-fragment-repository';
import { FailureReason, FragmentStatus } from '../../constants/enums';

export async function transferOutFragmentsForNewContinueRequest({
  conversationId,
  nhsNumber,
  odsCode
}) {
  setCurrentSpanAttributes({ conversationId });
  logInfo(`Initiated the EHR Fragment transfer.`);
  const { inboundConversationId, messageIds } =
    await getFragmentConversationAndMessageIdsFromEhrRepo(nhsNumber);
  logInfo('Retrieved all fragment Message IDs for transfer.');

  const messageIdsWithReplacements = await getAllMessageIdPairs(messageIds, inboundConversationId);

  await getAndSendMessageFragments(
    messageIdsWithReplacements,
    inboundConversationId,
    conversationId,
    odsCode
  );
}

export async function transferOutFragmentsForRetriedContinueRequest({
  conversationId,
  nhsNumber,
  odsCode
}) {
  setCurrentSpanAttributes({ conversationId });
  logInfo(`Retrying the EHR Fragment transfer.`);
  const { inboundConversationId } =
    await getFragmentConversationAndMessageIdsFromEhrRepo(nhsNumber);
  logInfo('Retrieved all fragment Message IDs for transfer.');

  const messageIdsWithReplacementsEligibleForSending =
    await getAllFragmentIdsToBeSent(inboundConversationId);

  logInfo(
    `Found ${messageIdsWithReplacementsEligibleForSending.length} Message ID replacements eligible to be sent.`
  );

  await getAndSendMessageFragments(
    messageIdsWithReplacementsEligibleForSending,
    inboundConversationId,
    conversationId,
    odsCode
  );
}

const getAndSendMessageFragments = async (
  messageIdsWithReplacements,
  inboundConversationId,
  outboundConversationId,
  odsCode
) => {
  let count = 0;

  for (const messageIdWithReplacement of messageIdsWithReplacements) {
    try {
      const { oldMessageId, newMessageId } = messageIdWithReplacement;

      let fragment = await getFragment(inboundConversationId, oldMessageId);
      fragment = replaceMessageIdsInObject(fragment, messageIdsWithReplacements);

      await sendFragment(
        inboundConversationId,
        outboundConversationId,
        odsCode,
        fragment,
        newMessageId,
        oldMessageId
      );

      logInfo(
        `Fragment ${++count} of ${
          messageIdsWithReplacements.length
        } sent to the GP2GP Messenger - with old Message ID ${oldMessageId}, and new Message ID ${newMessageId}.`
      );

      if (config.fragmentTransferRateLimitTimeoutMilliseconds)
        await new Promise(executor =>
          setTimeout(executor, config.fragmentTransferRateLimitTimeoutMilliseconds)
        );
    } catch (error) {
      await handleFragmentTransferError(
        error,
        inboundConversationId,
        messageIdWithReplacement.oldMessageId
      );
      throw error;
    }
  }

  logInfo(`All fragments have been successfully sent to GP2GP Messenger.`);
};

const handleFragmentTransferError = async (error, inboundConversationId, inboundMessageId) => {
  let failureReason;

  switch (true) {
    case error instanceof PresignedUrlNotFoundError:
      failureReason = FailureReason.MISSING_FROM_REPO;
      break;
    case error instanceof DownloadError:
      failureReason = FailureReason.DOWNLOAD_FAILED;
      break;
    default:
      failureReason = FailureReason.SENDING_FAILED;
      logError('Fragment transfer request failed', error);
      break;
  }

  await updateFragmentStatus(
    inboundConversationId,
    inboundMessageId,
    FragmentStatus.OUTBOUND_FAILED,
    failureReason
  );
};
