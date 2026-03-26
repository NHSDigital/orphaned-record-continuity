import { when } from 'jest-when';
import request from 'supertest';
import { v4 as uuid } from 'uuid';
import app from '../../../app';
import { logInfo, logError } from '../../../middleware/logging';
import { sendMessage } from '../../../services/mhs/mhs-outbound-client';
import { handlePdsResponse } from '../../../services/pds';
import generatePdsRetrievalQuery from '../../../templates/generate-pds-retrieval-request';

jest.mock('../../../config/logging');
jest.mock('../../../config/', () => ({
  initializeConfig: jest.fn().mockReturnValue({
    pdsAsid: 'pdsAsid',
    repoAsid: 'repoAsid',
    spineOrgCode: 'code'
  })
}));
jest.mock('../../../services/pds/pds-response-handler');
jest.mock('../../../middleware/logging');
jest.mock('../../../middleware/auth');
jest.mock('../../../services/mhs/mhs-outbound-client');
jest.mock('../../../templates/generate-pds-retrieval-request');

describe('/patient-demographics/:nhsNumber', () => {
  const interactionId = 'QUPA_IN000008UK02';
  const mockUUID = 'ebf6ee70-b9b7-44a6-8780-a386fccd759c';
  const mockNoDataUUID = 'fdb5c732-9e82-48ef-991b-8cd54b485748';
  const mockNoPatientUID = 'ebf6ee70-b9b7-64a6-8780-a386fccd759d';
  const mockNoPdsUUID = '6db9d011-98a9-48d3-b65a-cd83688dfc71';
  const mockErrorUUID = 'fd9271ea-9086-4f7e-8993-0271518fdb6f';
  const testSerialChangeNumber = '2';
  const testPatientPdsId = 'cppz';
  const fakerequest =
    '<QUPA_IN000008UK02 xmlns="urn:hl7-org:v3" xmlns:hl7="urn:hl7-org:v3"></QUPA_IN000008UK02>';
  const message = `
<PDSResponse xmlns="urn:hl7-org:v3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" classCode="OBS" moodCode="EVN" xsi:schemaLocation="urn:hl7-org:v3 ../../Schemas/PRPA_MT000201UK03.xsd">
      <pertinentSerialChangeNumber classCode="OBS" moodCode="EVN">
        <code code="2" codeSystem="2.16.840.1.113883.2.1.3.2.4.17.35"/>
        <value value="${testSerialChangeNumber}"/>
      </pertinentSerialChangeNumber>
    <patientCareProvisionEvent classCode="PCPR" moodCode="EVN">
      <code codeSystem="2.16.840.1.113883.2.1.3.2.4.17.37" code="1"/>
      <effectiveTime>
        <low value="20140212"/>
      </effectiveTime>
      <id root="2.16.840.1.113883.2.1.3.2.4.18.1" extension="${testPatientPdsId}"/>
    </patientCareProvisionEvent>
</PDSResponse>`;

  const messageNoPdsId = `
<PDSResponse xmlns="urn:hl7-org:v3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" classCode="OBS" moodCode="EVN" xsi:schemaLocation="urn:hl7-org:v3 ../../Schemas/PRPA_MT000201UK03.xsd">
      <pertinentSerialChangeNumber classCode="OBS" moodCode="EVN">
        <code code="2" codeSystem="2.16.840.1.113883.2.1.3.2.4.17.35"/>
        <value value="${testSerialChangeNumber}"/>
      </pertinentSerialChangeNumber>
</PDSResponse>`;

  const sendMessageErrorMessage =
    '<QUPA_IN000008UK02 xmlns="urn:hl7-org:v3" xmlns:hl7="urn:hl7-org:v3"><Error></Error></QUPA_IN000008UK02>';

  beforeEach(() => {
    uuid.mockImplementation(() => mockUUID);
    handlePdsResponse.mockResolvedValue({
      serialChangeNumber: testSerialChangeNumber,
      patientPdsId: testPatientPdsId
    });

    when(sendMessage)
      .mockResolvedValue({ status: 503, data: 'MHS error' })
      .calledWith({
        interactionId,
        conversationId: mockUUID,
        message: fakerequest,
        odsCode: 'code'
      })
      .mockResolvedValue({ status: 200, data: message })
      .calledWith({
        interactionId,
        conversationId: mockNoDataUUID,
        message: fakerequest,
        odsCode: 'code'
      })
      .mockResolvedValue({ status: 200 })
      .calledWith({
        interactionId,
        conversationId: mockUUID,
        message: sendMessageErrorMessage,
        odsCode: 'code'
      })
      .mockRejectedValue(Error('rejected'))
      .calledWith({
        interactionId,
        conversationId: mockNoPdsUUID,
        message: fakerequest,
        odsCode: 'code'
      })
      .mockResolvedValue({ status: 200, data: messageNoPdsId })
      .calledWith({
        interactionId,
        conversationId: mockErrorUUID,
        message: fakerequest,
        odsCode: 'code'
      })
      .mockResolvedValue({ status: 500, data: '500 MHS Error' })
      .calledWith({
        interactionId,
        conversationId: mockNoPatientUID,
        message: fakerequest,
        odsCode: 'code'
      })
      .mockResolvedValue({ status: 200, data: 'no patient details' });

    generatePdsRetrievalQuery.mockResolvedValue(fakerequest);
  });

  it('should return a 200 with MHS message passed back', done => {
    request(app).get('/patient-demographics/9999999999').expect(200).end(done);
  });

  it('should return a 200 and update the logs', done => {
    request(app)
      .get('/patient-demographics/9999999999')
      .expect(200)
      .expect(() => {
        expect(logInfo).toHaveBeenCalledTimes(3);
        expect(logInfo).toHaveBeenCalledWith(
          `200 PDS response received for conversation id: ${mockUUID}`
        );
      })
      .end(done);
  });

  it('should return a 200 and parse the pds response if the response is valid', done => {
    request(app)
      .get('/patient-demographics/9999999999')
      .expect(200)
      .expect(async () => {
        await handlePdsResponse(message).then(result =>
          expect(result).toEqual({
            serialChangeNumber: testSerialChangeNumber,
            patientPdsId: testPatientPdsId
          })
        );
      })
      .end(done);
  });

  it('should return a 200', done => {
    request(app).get('/patient-demographics/9999999999').expect(200).end(done);
  });

  it('should return an error if :nhsNumber is less than 10 digits', done => {
    const errorMessage = [{ nhsNumber: "'nhsNumber' provided is not 10 characters" }];
    request(app)
      .get('/patient-demographics/99')
      .expect(422)
      .expect('Content-Type', /json/)
      .expect(res => {
        expect(res.body).toEqual({
          errors: errorMessage
        });
        expect(logError).toHaveBeenCalledTimes(1);
        expect(logError).toHaveBeenCalledWith('validation-failed', { errors: errorMessage });
      })
      .end(done);
  });

  it('should return an error if :nhsNumber is not numeric', done => {
    const errorMessage = [{ nhsNumber: "'nhsNumber' provided is not numeric" }];
    request(app)
      .get('/patient-demographics/xxxxxxxxxx')
      .expect(422)
      .expect('Content-Type', /json/)
      .expect(res => {
        expect(res.body).toEqual({
          errors: errorMessage
        });
        expect(logError).toHaveBeenCalledTimes(1);
        expect(logError).toHaveBeenCalledWith('validation-failed', { errors: errorMessage });
      })
      .end(done);
  });

  it('should return a 503 if sendMessage throws an error', done => {
    generatePdsRetrievalQuery.mockResolvedValue(sendMessageErrorMessage);

    request(app)
      .get('/patient-demographics/9999999999')
      .expect(res => {
        expect(res.status).toBe(503);
        expect(res.body.errors).toEqual(['rejected']);
      })
      .end(done);
  });

  it('should return a 503 with error message if mhs returns a 500 status code', done => {
    uuid.mockImplementation(() => mockErrorUUID);

    request(app)
      .get('/patient-demographics/9999999999')
      .expect(res => {
        expect(res.status).toBe(503);
        expect(res.body.errors).toEqual(['MHS Error: 500 MHS Error']);
      })
      .end(done);
  });

  it('should return a 503 with error message if mhs returns a 503 status code', done => {
    uuid.mockImplementation(() => '893b17bc-5369-4ca1-a6aa-579f2f5cb318');

    request(app)
      .get('/patient-demographics/9999999999')
      .expect(res => {
        expect(res.status).toBe(503);
        expect(res.body.errors).toEqual(['Unexpected Error: MHS error']);
      })
      .end(done);
  });

  it('should call logError if mhs returns a 503 status code', done => {
    uuid.mockImplementation(() => '893b17bc-5369-4ca1-a6aa-579f2f5cb318');

    request(app)
      .get('/patient-demographics/9999999999')
      .expect(() => {
        expect(logError).toHaveBeenCalledTimes(1);
        expect(logError).toHaveBeenCalledWith(
          'PDS retrieval error',
          Error('Unexpected Error: MHS error')
        );
      })
      .end(done);
  });

  it('should returns a 503 status code with message if generatePdsRetrievalQuery throws an error', done => {
    generatePdsRetrievalQuery.mockRejectedValue(
      Error('Check template parameter error: asid is undefined')
    );

    request(app)
      .get('/patient-demographics/9999999999')
      .expect(res => {
        expect(res.status).toBe(503);
        expect(res.body.errors).toEqual(['Check template parameter error: asid is undefined']);
      })
      .end(done);
  });

  it('should call logError when generatePdsRetrievalQuery throws an error', done => {
    generatePdsRetrievalQuery.mockRejectedValue(
      Error('Check template parameter error: asid is undefined')
    );

    request(app)
      .get('/patient-demographics/9999999999')
      .expect(() => {
        expect(logError).toHaveBeenCalledTimes(1);
        expect(logError).toHaveBeenCalledWith(
          'PDS retrieval error',
          Error('Check template parameter error: asid is undefined')
        );
      })
      .end(done);
  });

  it('should return a 503 if message does not include the interactionId', done => {
    generatePdsRetrievalQuery.mockResolvedValue('<Header></Header>');

    request(app)
      .get('/patient-demographics/9999999999')
      .expect(res => {
        expect(res.status).toBe(503);
        expect(res.body.errors).toEqual(['interactionId is not included in the message']);
      })
      .end(done);
  });

  it('should call logError if message does not include the interactionId', done => {
    generatePdsRetrievalQuery.mockResolvedValue('<Header></Header>');

    request(app)
      .get('/patient-demographics/9999999999')
      .expect(() => {
        expect(logError).toHaveBeenCalledTimes(1);
        expect(logError).toHaveBeenCalledWith(
          'PDS retrieval error',
          Error('interactionId is not included in the message')
        );
      })
      .end(done);
  });
});
