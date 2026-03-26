import { v4 as uuid } from 'uuid';
import generateEhrRequestQuery from '../ehr-request-template';
import testData from './testData.json';

describe('generateEhrRequestQuery', () => {
  const ehrRequestId = uuid();
  const requestWithoutPatientDetails = {
    id: uuid(),
    timestamp: '20200403092516',
    sendingService: {
      odsCode: testData.mhs.odsCode,
      asid: testData.mhs.asid
    },
    receivingService: {
      odsCode: testData.emisPractise.odsCode,
      asid: testData.emisPractise.asid
    },
    ehrRequestId
  };

  const validRequest = {
    ...requestWithoutPatientDetails,
    patient: {
      nhsNumber: testData.emisPatient.nhsNumber
    }
  };

  it('should throw error when nhsNumber is not defined in inputObject', () => {
    expect(() => generateEhrRequestQuery(requestWithoutPatientDetails)).toThrow(
      'Check template parameter error: nhsNumber is undefined'
    );
  });

  it('should not throw error when all required arguments are defined', () => {
    expect(() => generateEhrRequestQuery(validRequest)).not.toThrow();
  });

  it('should have populate the xml template with all the required fields', () => {
    const ehrRequestQuery = generateEhrRequestQuery(validRequest);

    expect(ehrRequestQuery).toContain(validRequest.id);
    expect(ehrRequestQuery).toContain(validRequest.timestamp);
    expect(ehrRequestQuery).toContain(validRequest.sendingService.asid);
    expect(ehrRequestQuery.toUpperCase()).toContain(
      validRequest.sendingService.odsCode.toUpperCase()
    );

    expect(ehrRequestQuery).toContain(validRequest.receivingService.asid);
    expect(ehrRequestQuery.toUpperCase()).toContain(
      validRequest.receivingService.odsCode.toUpperCase()
    );

    expect(ehrRequestQuery).toContain(validRequest.patient.nhsNumber);
  });

  it('should upper case the sending service ODS code', () => {
    const ehrRequestQuery = generateEhrRequestQuery(validRequest);
    expect(ehrRequestQuery).toContain('B86041');
  });

  it('should upper case the receiving service ODS code', () => {
    const ehrRequestQuery = generateEhrRequestQuery(validRequest);
    expect(ehrRequestQuery).toContain('N82668');
  });

  it('should use uuid for ehrRequestId', () => {
    const ehrRequestQuery = generateEhrRequestQuery(validRequest);
    expect(ehrRequestQuery).toContain(ehrRequestId);
  });

  it('should throw error when receivingService and sendingObject is not defined in inputObject', () => {
    expect(() =>
      generateEhrRequestQuery({
        id: uuid(),
        timestamp: '20200403092516'
      })
    ).toThrow(
      'Check template parameter error: asid is undefined, odsCode is undefined, asid is undefined, odsCode is undefined'
    );
  });
});
