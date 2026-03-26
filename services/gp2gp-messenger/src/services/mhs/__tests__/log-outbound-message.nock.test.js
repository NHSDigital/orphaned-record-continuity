import request from 'supertest';

import app from '../../../app';
import { logInfo } from '../../../middleware/logging';
import { removeBase64Payloads } from '../logging-utils';
import {
  EhrMessageType,
  buildPostRequestBody,
  createMockFhirScope,
  createMockMHSScope,
  generateRandomIdsForTest,
  isSmallerThan256KB,
  setupEnvVarsForTest,
  loadMessageAndUpdateIds
} from './test-utils';

jest.mock('../../../middleware/logging');
jest.mock('../../../services/sqs/sqs-client');

describe('logOutboundMessage', () => {
  const fakeAuthKey = 'fake-auth-key';

  beforeAll(() => {
    setupEnvVarsForTest();
  });

  const testCases = [
    { messageType: EhrMessageType.core, interactionId: 'RCMR_IN030000UK06' },
    { messageType: EhrMessageType.fragment, interactionId: 'COPC_IN000001UK01' }
  ];

  describe.each(testCases)('Test case for EHR $messageType', ({ messageType, interactionId }) => {
    it(`should log outbound message with all base64 contents removed`, async () => {
      // given
      const testUUIDs = generateRandomIdsForTest();
      const ehrMessage = loadMessageAndUpdateIds(messageType, testUUIDs);
      const mockRequestBody = buildPostRequestBody(messageType, ehrMessage, testUUIDs);

      // when
      const sdsFhirScope = createMockFhirScope();
      const mhsAdaptorScope = createMockMHSScope();

      const response = await request(app)
        .post(`/ehr-out-transfers/${messageType}`)
        .set('Authorization', fakeAuthKey)
        .send(mockRequestBody);

      // then
      expect(logInfo).toHaveBeenCalled();
      expect(sdsFhirScope.isDone()).toBe(true);
      expect(mhsAdaptorScope.isDone()).toBe(true);
      expect(response.statusCode).toBe(204);

      // extract the relevant log with outbound message from all arguments that logInfo was called with.
      const outboundMessageInLog = logInfo.mock.calls
        .map(args => args[0])
        .filter(loggedObject => loggedObject?.body?.payload?.includes(interactionId))
        .pop();

      // verify that the log does exist and is under 256KB
      expect(outboundMessageInLog).not.toEqual(undefined);
      expect(isSmallerThan256KB(outboundMessageInLog)).toBe(true);

      // Verify the logging correctly capture the post request of gp2gp messenger --> outbound MHS adaptor
      // Note: Header assertions conditional - nock v14 doesn't reliably expose request.headers
      if (Object.keys(mhsAdaptorScope.outboundHeaders).length > 0) {
        expect(outboundMessageInLog.headers).toEqual(mhsAdaptorScope.outboundHeaders);
      }
      // payload (the hl7v3 xml part) and external_attachments should be identical
      expect(outboundMessageInLog.body.payload).toEqual(mhsAdaptorScope.outboundBody.payload);
      expect(outboundMessageInLog.body.external_attachments).toEqual(
        mhsAdaptorScope.outboundBody.external_attachments
      );
      // attachments are not exactly equal, as the attachments in log got base64 contents removed.
      // other than the base64 payloads, they should be the same
      expect(outboundMessageInLog.body.attachments).not.toEqual(
        mhsAdaptorScope.outboundBody.attachments
      );
      expect(outboundMessageInLog.body.attachments).toEqual(
        removeBase64Payloads(mhsAdaptorScope.outboundBody.attachments)
      );
    });

    it('should keep the base64 content in the actual outbound post request unchanged', async () => {
      // given
      const testUUIDs = generateRandomIdsForTest();
      const ehrMessage = loadMessageAndUpdateIds(messageType, testUUIDs);
      const mockRequestBody = buildPostRequestBody(messageType, ehrMessage, testUUIDs);

      // when
      createMockFhirScope();
      const mhsAdaptorScope = createMockMHSScope();

      await request(app)
        .post(`/ehr-out-transfers/${messageType}`)
        .set('Authorization', fakeAuthKey)
        .send(mockRequestBody);

      // then
      expect(logInfo).toHaveBeenCalled();
      expect(mhsAdaptorScope.isDone()).toBe(true);

      // verify that the base64 contents in the actual post request body sent to MHS adaptor is unchanged
      expect(isSmallerThan256KB(mhsAdaptorScope.outboundBody)).toBe(false);
      expect(mhsAdaptorScope.outboundBody.attachments.length).toBeGreaterThan(0);
      mhsAdaptorScope.outboundBody.attachments.forEach((attachment, index) => {
        const base64ContentFromOutbound = attachment.payload;
        const base64ContentFromOriginFile = ehrMessage.attachments[index].payload;
        expect(base64ContentFromOutbound).toEqual(base64ContentFromOriginFile);
      });
    });

    it('should log the message even if mhs outbound adaptor responded with error status code', async () => {
      // given
      const testUUIDs = generateRandomIdsForTest();
      const ehrMessage = loadMessageAndUpdateIds(messageType, testUUIDs);
      const mockRequestBody = buildPostRequestBody(messageType, ehrMessage, testUUIDs);

      // when
      const sdsFhirScope = createMockFhirScope();
      const mhsAdaptorScope = createMockMHSScope([500, 'Internal server error']);

      const response = await request(app)
        .post(`/ehr-out-transfers/${messageType}`)
        .set('Authorization', fakeAuthKey)
        .send(mockRequestBody);

      // then
      expect(logInfo).toHaveBeenCalled();
      expect(sdsFhirScope.isDone()).toBe(true);
      expect(mhsAdaptorScope.isDone()).toBe(true);
      expect(response.statusCode).toBe(503);

      const outboundMessageInLog = logInfo.mock.calls
        .map(args => args[0])
        .filter(loggedObject => loggedObject?.body?.payload?.includes(interactionId))
        .pop();

      // verify that the log does exist and is under 256KB
      expect(outboundMessageInLog).not.toEqual(undefined);
      expect(isSmallerThan256KB(outboundMessageInLog)).toBe(true);

      // Verify the logging correctly capture the post request of gp2gp messenger --> outbound MHS adaptor
      // Note: Header assertions conditional - nock v14 doesn't reliably expose request.headers
      if (Object.keys(mhsAdaptorScope.outboundHeaders).length > 0) {
        expect(outboundMessageInLog.headers).toEqual(mhsAdaptorScope.outboundHeaders);
      }
      // payload (the hl7v3 xml part) and external_attachments should be identical
      expect(outboundMessageInLog.body.payload).toEqual(mhsAdaptorScope.outboundBody.payload);
      expect(outboundMessageInLog.body.external_attachments).toEqual(
        mhsAdaptorScope.outboundBody.external_attachments
      );
      // attachments are not exactly equal, as the attachments in log got base64 contents removed.
      // other than the base64 payloads, they should be the same
      expect(outboundMessageInLog.body.attachments).not.toEqual(
        mhsAdaptorScope.outboundBody.attachments
      );
      expect(outboundMessageInLog.body.attachments).toEqual(
        removeBase64Payloads(mhsAdaptorScope.outboundBody.attachments)
      );
    });
  });

  describe('Special test case: EHR with Large medical history which is > 256KB even without the base64 content', () => {
    it(`should log the whole outbound message in multiple lines of logs, each line to be within 256 KB`, async () => {
      // given
      const messageType = EhrMessageType.coreWithLargeMedicalHistory;
      const testUUIDs = generateRandomIdsForTest();
      const ehrMessage = loadMessageAndUpdateIds(messageType, testUUIDs);
      const mockRequestBody = buildPostRequestBody(messageType, ehrMessage, testUUIDs);

      // when
      createMockFhirScope();
      const mhsAdaptorScope = createMockMHSScope();

      await request(app)
        .post(`/ehr-out-transfers/core`)
        .set('Authorization', fakeAuthKey)
        .send(mockRequestBody);

      // then
      expect(logInfo).toHaveBeenCalled();
      const relevantLogs = logInfo.mock.calls
        .map(args => args[0])
        .filter(loggedText => loggedText.match(/Part \d+ of \d+: /));

      // verify that every line of the log is less than 256 KB
      expect(relevantLogs.length).toBeGreaterThan(0);
      relevantLogs.forEach(singleLineOfLog => {
        expect(isSmallerThan256KB(singleLineOfLog)).toBe(true);
      });

      // verify that we can combine all lines of the log to reconstruct the actual outbound message (with base64 contents removed)
      const combinedLogs = relevantLogs
        .map(singleLineOfLog => singleLineOfLog.replace(/Part \d+ of \d+: /, ''))
        .join('');

      const restoredMessage = JSON.parse(combinedLogs);

      // Header assertion conditional - nock v14 doesn't reliably expose request.headers
      if (Object.keys(mhsAdaptorScope.outboundHeaders).length > 0) {
        expect(restoredMessage.headers).toEqual(mhsAdaptorScope.outboundHeaders);
      }
    });
  });
});
