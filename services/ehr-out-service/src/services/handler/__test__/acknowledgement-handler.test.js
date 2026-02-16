import { parseAcknowledgementMessage } from '../../parser/acknowledgement-parser';
import { storeAcknowledgement } from '../../database/dynamodb/ehr-fragment-repository';
import { acknowledgementMessageHandler } from '../acknowledgement-handler';
import { sendDeleteRequestToEhrRepo } from '../../ehr-repo/delete-ehr';
import { parseConversationId } from '../../parser/parsing-utilities';
import { logError, logInfo } from '../../../middleware/logging';
import { getNhsNumberByOutboundConversationId } from '../../database/dynamodb/outbound-conversation-repository';
import { messageIdMatchOutboundCore } from '../../database/dynamodb/ehr-core-repository';
import { updateConversationStatus, updateCoreStatus } from '../../transfer/transfer-out-util';

// Mocking
jest.mock('../../../middleware/logging');
jest.mock('../../parser/parsing-utilities');
jest.mock('../../parser/acknowledgement-parser');
jest.mock('../../database/dynamodb/outbound-conversation-repository');
jest.mock('../../database/dynamodb/ehr-core-repository');
jest.mock('../../database/dynamodb/ehr-fragment-repository');
jest.mock('../../ehr-repo/delete-ehr');
jest.mock('../../transfer/transfer-out-util');

describe('acknowledgement-handler.test.js', () => {
  // ============ COMMON PROPERTIES ============
  const CONVERSATION_ID = '63C1E862-8891-43E0-A53D-95EB5FCE1F08';
  const MESSAGE_REF = '9BD81F1F-DD62-470A-AAB8-B500FDA7A9EC';
  const NHS_NUMBER = 1245748541;
  // =================== END ===================

  it('should handle a positive acknowledgement with typecode AA successfully', async () => {
    // given
    const acknowledgementMessage = {
      acknowledgementTypeCode: 'AA',
      messageRef: MESSAGE_REF
    };

    // when
    parseConversationId.mockResolvedValueOnce(Promise.resolve(CONVERSATION_ID));
    parseAcknowledgementMessage.mockResolvedValueOnce(Promise.resolve(acknowledgementMessage));
    messageIdMatchOutboundCore.mockResolvedValueOnce(Promise.resolve(false));
    getNhsNumberByOutboundConversationId.mockResolvedValueOnce(Promise.resolve(NHS_NUMBER));
    storeAcknowledgement.mockResolvedValueOnce(undefined);

    await acknowledgementMessageHandler(acknowledgementMessage);

    // then
    expect(logInfo).toHaveBeenCalledTimes(1);
    expect(logInfo).toHaveBeenCalledWith(
      `Positive acknowledgement received for Conversation ID ${CONVERSATION_ID}, and NHS number ${NHS_NUMBER}.`
    );
  });

  it('should handle a negative acknowledgement with typecode AR successfully', async () => {
    // given
    const acknowledgementMessage = {
      acknowledgementTypeCode: 'AR',
      messageRef: MESSAGE_REF,
      acknowledgementDetail: 'floppy disk not found'
    };

    // when
    parseConversationId.mockResolvedValueOnce(Promise.resolve(CONVERSATION_ID));
    parseAcknowledgementMessage.mockResolvedValueOnce(Promise.resolve(acknowledgementMessage));
    messageIdMatchOutboundCore.mockResolvedValueOnce(Promise.resolve(false));
    getNhsNumberByOutboundConversationId.mockResolvedValueOnce(Promise.resolve(NHS_NUMBER));
    storeAcknowledgement.mockResolvedValueOnce(undefined);

    await acknowledgementMessageHandler(acknowledgementMessage);

    // then
    expect(logInfo).toHaveBeenCalledTimes(1);
    expect(logInfo).toHaveBeenCalledWith(
      `Negative acknowledgement received - detail: ${acknowledgementMessage.acknowledgementDetail} for Conversation ID ${CONVERSATION_ID}, and NHS number ${NHS_NUMBER}.`
    );
  });

  it('should handle a negative acknowledgement with typecode AE successfully', async () => {
    // given
    const acknowledgementMessage = {
      acknowledgementTypeCode: 'AE',
      messageRef: MESSAGE_REF,
      acknowledgementDetail: 'floppy disk not found'
    };

    // when
    parseConversationId.mockResolvedValueOnce(Promise.resolve(CONVERSATION_ID));
    parseAcknowledgementMessage.mockResolvedValueOnce(Promise.resolve(acknowledgementMessage));
    messageIdMatchOutboundCore.mockResolvedValueOnce(Promise.resolve(false));
    getNhsNumberByOutboundConversationId.mockResolvedValueOnce(Promise.resolve(NHS_NUMBER));
    storeAcknowledgement.mockResolvedValueOnce(undefined);

    await acknowledgementMessageHandler(acknowledgementMessage);

    // then
    expect(logInfo).toHaveBeenCalledTimes(1);
    expect(logInfo).toHaveBeenCalledWith(
      `Negative acknowledgement received - detail: ${acknowledgementMessage.acknowledgementDetail} for Conversation ID ${CONVERSATION_ID}, and NHS number ${NHS_NUMBER}.`
    );
  });

  it('should handle an unknown acknowledgement typecode successfully', async () => {
    // given
    const acknowledgementMessage = {
      acknowledgementTypeCode: 'ES'
    };

    // when
    parseConversationId.mockResolvedValueOnce(Promise.resolve(CONVERSATION_ID));
    parseAcknowledgementMessage.mockResolvedValueOnce(Promise.resolve(acknowledgementMessage));
    messageIdMatchOutboundCore.mockResolvedValueOnce(Promise.resolve(false));
    getNhsNumberByOutboundConversationId.mockResolvedValueOnce(Promise.resolve(NHS_NUMBER));
    storeAcknowledgement.mockResolvedValueOnce(undefined);

    await acknowledgementMessageHandler(acknowledgementMessage);

    // then
    expect(logError).toHaveBeenCalledTimes(1);
    expect(logError).toHaveBeenCalledWith('Acknowledgement type ES is unknown.');
  });

  it('should handle a positive integration acknowledgement successfully', async () => {
    // given
    const acknowledgementMessage = {
      acknowledgementTypeCode: 'AA',
      messageRef: MESSAGE_REF
    };

    // when
    parseConversationId.mockResolvedValueOnce(Promise.resolve(CONVERSATION_ID));
    parseAcknowledgementMessage.mockResolvedValueOnce(Promise.resolve(acknowledgementMessage));
    messageIdMatchOutboundCore.mockResolvedValueOnce(Promise.resolve(true));
    getNhsNumberByOutboundConversationId.mockResolvedValueOnce(Promise.resolve(NHS_NUMBER));
    storeAcknowledgement.mockResolvedValueOnce(undefined);
    sendDeleteRequestToEhrRepo.mockResolvedValueOnce(Promise.resolve({}));
    updateCoreStatus.mockResolvedValueOnce(undefined);
    updateConversationStatus.mockResolvedValueOnce(undefined);

    await acknowledgementMessageHandler(acknowledgementMessage);

    // then
    expect(logInfo).toHaveBeenCalledTimes(2);
    expect(sendDeleteRequestToEhrRepo).toHaveBeenCalledTimes(1);
    expect(sendDeleteRequestToEhrRepo.mock.calls[0][0]).toEqual(NHS_NUMBER);
    expect(sendDeleteRequestToEhrRepo.mock.calls[0][1]).toEqual(CONVERSATION_ID);
  });

  it('should handle a negative integration acknowledgement successfully', async () => {
    // given
    const acknowledgementMessage = {
      acknowledgementTypeCode: 'AE',
      messageRef: MESSAGE_REF
    };

    // when
    parseConversationId.mockResolvedValueOnce(Promise.resolve(CONVERSATION_ID));
    parseAcknowledgementMessage.mockResolvedValueOnce(Promise.resolve(acknowledgementMessage));
    messageIdMatchOutboundCore.mockResolvedValueOnce(Promise.resolve(true));
    getNhsNumberByOutboundConversationId.mockResolvedValueOnce(Promise.resolve(NHS_NUMBER));
    storeAcknowledgement.mockResolvedValueOnce(undefined);
    updateCoreStatus.mockResolvedValueOnce(undefined);
    updateConversationStatus.mockResolvedValueOnce(undefined);

    await acknowledgementMessageHandler(acknowledgementMessage);

    // then
    expect(logInfo).toHaveBeenCalledTimes(2);
    expect(logInfo).toHaveBeenCalledWith(
      `Negative integration acknowledgement received for Conversation ID ${CONVERSATION_ID}, and NHS number ${NHS_NUMBER}.`
    );
    expect(logInfo).toHaveBeenCalledWith(
      `Sending delete request to ehr out repository for Conversation ID ${CONVERSATION_ID}, and NHS number ${NHS_NUMBER}.`
    );
  });
});
