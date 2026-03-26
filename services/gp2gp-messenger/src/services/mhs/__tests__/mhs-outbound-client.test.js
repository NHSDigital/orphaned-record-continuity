import axios from 'axios';
import dateFormat from 'dateformat';
import { v4 as uuid } from 'uuid';
import { initializeConfig } from '../../../config';
import { logError } from '../../../middleware/logging';
import generatePdsRetrievalQuery from '../../../templates/generate-pds-retrieval-request';
import testData from '../../../templates/__tests__/testData.json';
import { sendMessage, stripXMLMessage } from '../mhs-outbound-client';
import { sendToQueue } from '../../sqs/sqs-client';

jest.mock('axios');
jest.mock('../../../config/logging');
jest.mock('../../../config');
jest.mock('../../../middleware/logging');
jest.mock('../../sqs/sqs-client');

const conversationId = uuid().toUpperCase();
const timestamp = dateFormat(Date.now(), 'yyyymmddHHMMss');
const interactionId = 'RCMR_IN030000UK06';
const odsCode = 'YES';
const url = 'http://url.com';

describe('mhs-outbound-client', () => {
  const message = 'message';
  const axiosHeaders = {
    headers: {
      'Content-Type': 'application/json',
      'Interaction-ID': interactionId,
      'Correlation-Id': conversationId,
      'Ods-Code': 'YES',
      'from-asid': testData.mhs.asid,
      'wait-for-response': 'false'
    }
  };
  const axiosBody = { payload: message };
  initializeConfig.mockReturnValue({
    repoAsid: testData.mhs.asid,
    mhsOutboundUrl: url,
    spineOdsCode: 'YES',
    pdsAsid: '928942012545'
  });

  beforeEach(() => {
    axios.post.mockResolvedValue(Promise.resolve({ status: 200, data: { test: 'tested' } }));
  });

  it('should reject with an Error if interactionId is not passed in', () => {
    return expect(sendMessage({ conversationId, odsCode, message })).rejects.toEqual(
      Error('interactionId must be passed in')
    );
  });

  it('should log an Error if interactionId is not passed in', async () => {
    await sendMessage({ conversationId, odsCode, message }).catch(() => {});
    expect(logError).toHaveBeenCalledTimes(1);
    expect(logError).toHaveBeenCalledWith(
      'validation failed',
      Error(['interactionId must be passed in'])
    );
  });

  it('should logs an Error if message is not passed in', () => {
    return expect(sendMessage({ conversationId, interactionId, odsCode })).rejects.toEqual(
      Error(['message must be passed in'])
    );
  });

  it('should logs an Error if conversationId is not passed in', () => {
    return expect(sendMessage({ interactionId, odsCode, message })).rejects.toEqual(
      Error(['conversationId must be passed in'])
    );
  });

  it('should reject with an Error if conversationId, message and interactionId are not passed in', async () => {
    const error = await sendMessage().catch(err => err);
    expect(error).toEqual(expect.anything(Error));
    expect(error.message).toEqual(expect.stringMatching('conversationId must be passed in'));
    expect(error.message).toEqual(expect.stringMatching('message must be passed in'));
    expect(error.message).toEqual(expect.stringMatching('interactionId must be passed in'));
  });

  it('should log an Error if conversationId, message and interactionId is not passed in', async () => {
    await sendMessage().catch(() => {});
    expect(logError).toHaveBeenCalledTimes(1);
  });

  it('throws an Error if interactionId is not passed in', () => {
    return expect(sendMessage({ conversationId, odsCode, message })).rejects.toThrow(
      Error('interactionId must be passed in')
    );
  });

  it('should call axios with specified odsCode if passed in', async () => {
    const odsCode = 'A123';
    const response = await sendMessage({ interactionId, conversationId, message, odsCode });
    expect(response.status).toBe(200);
    expect(axios.post).toHaveBeenCalledWith(url, axiosBody, {
      headers: {
        ...axiosHeaders.headers,
        'Ods-Code': odsCode
      }
    });
  });

  it('should make post adding attachments to body if passed in', async () => {
    const attachments = [{ some: 'attachment' }];
    const response = await sendMessage({
      interactionId,
      conversationId,
      odsCode,
      message,
      attachments
    });
    expect(response.status).toBe(200);
    expect(axios.post).toHaveBeenCalledWith(
      url,
      { ...axiosBody, attachments },
      {
        headers: {
          ...axiosHeaders.headers
        }
      }
    );
  });

  it('should make post adding external attachments (describing mid messages containing further large ehr fragments) to body if passed in', async () => {
    const external_attachments = [{ some: 'external attachment' }];
    const response = await sendMessage({
      interactionId,
      conversationId,
      odsCode,
      message,
      external_attachments
    });
    expect(response.status).toBe(200);
    expect(axios.post).toHaveBeenCalledWith(
      url,
      { ...axiosBody, external_attachments },
      {
        headers: {
          ...axiosHeaders.headers
        }
      }
    );
  });

  it('should make post adding both attachments and external attachments  if passed in', async () => {
    const attachments = [{ some: 'attachment' }];
    const external_attachments = [{ some: 'external attachment' }];
    const response = await sendMessage({
      interactionId,
      conversationId,
      odsCode,
      message,
      attachments,
      external_attachments
    });
    expect(response.status).toBe(200);
    expect(axios.post).toHaveBeenCalledWith(
      url,
      { ...axiosBody, attachments, external_attachments },
      {
        headers: {
          ...axiosHeaders.headers
        }
      }
    );
  });

  it('should call axios with wait-for-response header set to true for pds update', async () => {
    const interactionId = 'PRPA_IN000203UK03';
    const response = await sendMessage({ interactionId, conversationId, odsCode, message });
    expect(response.status).toBe(200);
    expect(axios.post).toHaveBeenCalledWith(url, axiosBody, {
      headers: {
        ...axiosHeaders.headers,
        'Interaction-ID': interactionId,
        'wait-for-response': 'false'
      }
    });
  });

  it('should call axios with wait-for-response header set to false for messages that do not support sync-async in mhs', async () => {
    const response = await sendMessage({ interactionId, conversationId, odsCode, message });
    expect(response.status).toBe(200);
    expect(axios.post).toHaveBeenCalledWith(url, axiosBody, {
      headers: {
        ...axiosHeaders.headers,
        'wait-for-response': 'false'
      }
    });
  });

  it('should use null for messageId by default, not pass it in headers, and return 200', async () => {
    const response = await sendMessage({ interactionId, conversationId, odsCode, message });
    expect(response.status).toBe(200);
    expect(axios.post).toHaveBeenCalledWith(url, axiosBody, axiosHeaders);
  });

  it('should call axios with specified messageId if passed in and return 200', async () => {
    const messageId = uuid().toUpperCase();
    const response = await sendMessage({
      interactionId,
      conversationId,
      odsCode,
      message,
      messageId
    });
    expect(response.status).toBe(200);
    expect(axios.post).toHaveBeenCalledWith(url, axiosBody, {
      headers: {
        ...axiosHeaders.headers,
        'Message-Id': messageId
      }
    });
  });

  it('should convert conversationId to upper case', async () => {
    // Given
    const lowerCaseConversationId = '517e7157-ed12-4d14-9bbe-1f3b6d485a4c';
    const upperCaseConversationId = lowerCaseConversationId.toUpperCase();

    // When
    await sendMessage({
      interactionId,
      conversationId: lowerCaseConversationId,
      odsCode,
      message
    });

    // Then
    expect(axios.post).toHaveBeenCalledWith(url, axiosBody, {
      headers: {
        ...axiosHeaders.headers,
        'Correlation-Id': upperCaseConversationId
      }
    });
  });

  it('should convert messageId to upper case', async () => {
    // Given
    const lowerCaseMessageId = '724bf695-98a1-493c-8c23-e7aee841c804';
    const upperCaseMessageId = lowerCaseMessageId.toUpperCase();

    // When
    await sendMessage({
      interactionId,
      conversationId,
      odsCode,
      message,
      messageId: lowerCaseMessageId
    });

    // Then
    expect(axios.post).toHaveBeenCalledWith(url, axiosBody, {
      headers: {
        ...axiosHeaders.headers,
        'Message-Id': upperCaseMessageId
      }
    });
  });

  it('should send request and response to observability queue', async () => {
    const response = await sendMessage({
      interactionId,
      conversationId,
      odsCode,
      message
    });

    expect(response.status).toBe(200);
    expect(sendToQueue).toHaveBeenCalledWith(
      expect.objectContaining({
        response: { data: response.data, status: response.status },
        request: { body: axiosBody, headers: axiosHeaders }
      }),
      expect.objectContaining({
        conversationId: {
          DataType: 'String',
          StringValue: conversationId
        }
      })
    );
  });

  it('should send errors to observability queue', async () => {
    axios.post.mockRejectedValue(new Error('some-error'));
    await sendMessage({
      interactionId,
      conversationId,
      odsCode,
      message
    }).catch(() => {});

    expect(sendToQueue).toHaveBeenCalledWith(
      expect.objectContaining({
        error: new Error('POST http://url.com - some-error'),
        request: { body: axiosBody, headers: axiosHeaders }
      }),
      expect.objectContaining({
        conversationId: {
          DataType: 'String',
          StringValue: conversationId
        }
      })
    );
  });

  it('should stringify and escape the payload (xml message)', async () => {
    const pdsRetrievalQuery = await generatePdsRetrievalQuery({
      id: conversationId,
      timestamp,
      receivingService: { asid: testData.pds.asid },
      sendingService: { asid: testData.mhs.asid },
      patient: { nhsNumber: testData.emisPatient.nhsNumber }
    });

    const response = await sendMessage({
      interactionId,
      conversationId,
      odsCode,
      message: pdsRetrievalQuery
    });

    expect(response.status).toBe(200);
    expect(axios.post).toHaveBeenCalledWith(
      url,
      { payload: stripXMLMessage(pdsRetrievalQuery) },
      axiosHeaders
    );
  });

  it('should throw an Error if there is an error with axios.post request', () => {
    axios.post.mockRejectedValue(new Error());
    return expect(
      sendMessage({ interactionId, conversationId, odsCode, message: 'message' })
    ).rejects.toEqual(Error(`POST ${url} - Request failed`));
  });

  it('should log an Error if there is an error with axios.post request', async () => {
    axios.post.mockRejectedValue(new Error());
    await sendMessage({ interactionId, conversationId, odsCode, message: 'message' }).catch(
      () => {}
    );
    expect(logError).toHaveBeenCalledTimes(1);
    expect(logError).toHaveBeenCalledWith(`POST ${url} - Request failed`, expect.anything());
  });
});

describe('stripXMLMessage', () => {
  const pdsQueryArguments = {
    id: conversationId,
    timestamp,
    receivingService: { asid: testData.pds.asid },
    sendingService: { asid: testData.mhs.asid },
    patient: { nhsNumber: testData.emisPatient.nhsNumber }
  };

  it('should return a string', async () => {
    const pdsRetrievalQuery = await generatePdsRetrievalQuery(pdsQueryArguments);
    expect(typeof stripXMLMessage(pdsRetrievalQuery) === 'string').toBe(true);
  });

  it('should remove spaces between xml tags', async () => {
    const pdsRetrievalQuery = await generatePdsRetrievalQuery(pdsQueryArguments);
    expect(stripXMLMessage(pdsRetrievalQuery).match(/> +</g)).toBe(null);
  });

  it('should trim the message', async () => {
    const pdsRetrievalQuery = await generatePdsRetrievalQuery(pdsQueryArguments);
    expect(stripXMLMessage(` ${pdsRetrievalQuery}     `).match(/^ +| +$/)).toBe(null);
  });

  it('should remove new lines (/n) and carriage return characters (/r)', async () => {
    const pdsRetrievalQuery = await generatePdsRetrievalQuery(pdsQueryArguments);
    expect(stripXMLMessage(pdsRetrievalQuery).match(/\r?\n|\r/g)).toBe(null);
  });
});
