import axios from 'axios';
import request from 'supertest';
import { v4 as uuid } from 'uuid';
import app from '../app';
import { pdsQueryActFailed } from '../services/pds/__tests__/data/pds-query-act-failed';
import { pdsRetrivealQueryResponseSuccess } from '../services/pds/__tests__/data/pds-retrieval-query-response-success';
import { expectStructuredLogToContain, transportSpy } from '../__builders__/logging-helper';
import { logger } from '../config/logging';
import { getPracticeAsid } from '../services/fhir/sds-fhir-client';

jest.mock('axios');
jest.mock('../services/fhir/sds-fhir-client');

describe('app', () => {
  beforeEach(() => {
    process.env.API_KEY_FOR_TEST_USER = 'correct-key';
    process.env.SPINE_ORG_CODE = 'YES';
    process.env.PDS_ASID = '928942012545';
  });

  afterEach(() => {
    if (process.env.API_KEY_FOR_TEST_USER) {
      delete process.env.API_KEY_FOR_TEST_USER;
    }
  });

  describe('GET /', () => {
    it('should return a 404 status code', done => {
      request(app).get('/').expect(404).end(done);
    });
  });

  describe('GET /any-text - an unspecified endpoint', () => {
    it('should return a 404 status code', done => {
      request(app).get('/any-text').expect(404).end(done);
    });

    describe('Swagger Documentation', () => {
      it('GET /swagger - should return a 301 status code (redirect) and text/html content type response', done => {
        request(app)
          .get('/swagger')
          .expect(301)
          .expect('Content-Type', 'text/html; charset=UTF-8')
          .end(done);
      });

      it('GET /swagger/index.html - should return a 200 status code and text/html content type response', done => {
        request(app)
          .get('/swagger/index.html')
          .expect(200)
          .expect('Content-Type', 'text/html; charset=utf-8')
          .end(done);
      });
    });
  });

  describe('GET /patient-demographics', () => {
    beforeEach(() => {
      logger.add(transportSpy);
    });

    it('should return a 200 status code for /patient-demographics/:nhsNumber', async () => {
      axios.post.mockImplementation(() =>
        Promise.resolve({ status: 200, data: pdsRetrivealQueryResponseSuccess })
      );

      const res = await request(app)
        .get('/patient-demographics/9999999999')
        .set('Authorization', 'correct-key')
        .set('TRACEID', 'a trace ID');

      expect(res.statusCode).toBe(200);
      expectStructuredLogToContain(transportSpy, {
        conversationId: expect.anything(),
        traceId: 'a trace ID'
      });
    });

    it('should return the response body for /patient-demographics/:nhsNumber', async () => {
      axios.post.mockImplementation(() =>
        Promise.resolve({ status: 200, data: pdsRetrivealQueryResponseSuccess })
      );
      const res = await request(app)
        .get('/patient-demographics/9999999999')
        .set('Authorization', 'correct-key');

      expect(res.statusCode).toBe(200);
      expect(res.body).toEqual(
        expect.objectContaining({
          data: {
            odsCode: 'B86041',
            patientPdsId: 'cppz',
            serialChangeNumber: '138'
          }
        })
      );
      expectStructuredLogToContain(transportSpy, {
        conversationId: expect.anything(),
        traceId: expect.anything()
      });
    });

    it('should return a 503 status code with Errors for /patient-demographics/:nhsNumber', async () => {
      axios.post.mockImplementation(() =>
        Promise.resolve({ status: 200, data: pdsQueryActFailed() })
      );
      const res = await request(app)
        .get('/patient-demographics/9999999999')
        .set('Authorization', 'correct-key');

      expect(res.statusCode).toBe(503);
      expectStructuredLogToContain(transportSpy, {
        conversationId: expect.anything(),
        traceId: expect.anything()
      });
    });

    it('should return a 404 status code without nhsNumber parameter', async () => {
      const res = await request(app).get('/patient-demographics');

      expect(res.statusCode).toBe(404);
      expectStructuredLogToContain(transportSpy, {
        traceId: expect.anything()
      });
    });
  });

  describe('POST /health-record-requests/:nhsNumber', () => {
    beforeEach(() => {
      logger.add(transportSpy);
    });

    it('should return a 401 status code for /health-record-requests/:nhsNumber when not authenticated because authorization header not set', async () => {
      const res = await request(app).post('/health-record-requests/9999999999');

      expect(res.statusCode).toBe(401);
      expectStructuredLogToContain(transportSpy, {
        traceId: expect.anything()
      });
    });

    it('should log with a generated uuid format traceId if traceId not set in request headers', async () => {
      await request(app).post('/health-record-requests/9999999999');

      expectStructuredLogToContain(transportSpy, {
        traceId: expect.stringMatching(/^[0-9a-fA-F-]{36}$/)
      });
    });

    it('should log with conversationId provided in request body', async () => {
      const requestBody = someHealthRecordRequestBody();
      requestBody.conversationId = uuid();

      await request(app)
        .post('/health-record-requests/9999999991')
        .set('Authorization', 'correct-key')
        .send(requestBody);

      expectStructuredLogToContain(transportSpy, {
        conversationId: requestBody.conversationId
      });
    });

    it('should log with traceId provided in request header', async () => {
      await request(app)
        .post('/health-record-requests/9999999999')
        .set('Authorization', 'correct-key')
        .set('traceid', 'our trace ID')
        .send(someHealthRecordRequestBody());

      expectStructuredLogToContain(transportSpy, {
        traceId: 'our trace ID'
      });
    });

    function someHealthRecordRequestBody() {
      return {
        conversationId: uuid(),
        repositoryOdsCode: 'the repo ods code',
        practiceOdsCode: 'some practice ods code',
        repositoryAsid: 'the repo asid'
      };
    }
  });

  describe('POST /health-record-requests/:nhsNumber/acknowledgement', () => {
    const practiceAsid = '200007389';
    const mockBody = {
      conversationId: uuid(),
      messageId: uuid(),
      odsCode: 'B1234',
      repositoryAsid: '20000018274'
    };

    beforeEach(() => {
      logger.add(transportSpy);
    });

    it('should successfully send acknowledgement and return 204', async () => {
      getPracticeAsid.mockResolvedValue(practiceAsid);
      axios.post.mockResolvedValue({ status: 204 });

      const res = await request(app)
        .post('/health-record-requests/9999999999/acknowledgement')
        .set('Authorization', 'correct-key')
        .send(mockBody);

      expect(res.statusCode).toBe(204);
      expectStructuredLogToContain(transportSpy, {
        conversationId: expect.anything(),
        messageId: expect.anything(),
        traceId: expect.anything()
      });
    });

    it('should error when send message fails', async () => {
      getPracticeAsid.mockResolvedValue(practiceAsid);
      axios.post.mockRejectedValue({ status: 500 });

      const res = await request(app)
        .post(`/health-record-requests/9999999999/acknowledgement`)
        .set('Authorization', 'correct-key')
        .send(mockBody);

      expect(res.statusCode).toBe(503);
      expectStructuredLogToContain(transportSpy, {
        conversationId: expect.anything(),
        messageId: expect.anything(),
        traceId: expect.anything()
      });
    });
  });
});
