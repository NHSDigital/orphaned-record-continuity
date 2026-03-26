const generateUpdateOdsRequest = require('../generate-update-ods-request');
import dateFormat from 'dateformat';
const { v4: uuid } = require('uuid');
const testData = require('./testData.json');

describe('generateUpdateOdsRequest', () => {
  const updateRequestWithoutPatientDetails = {
    id: uuid(),
    timestamp: dateFormat(Date.now(), 'yyyymmddHHMMss'),
    receivingService: {
      asid: testData.pds.asid
    },
    sendingService: {
      asid: testData.mhs.asid
    },
    newOdsCode: 'GPodsCode'
  };

  const validUpdateRequest = {
    ...updateRequestWithoutPatientDetails,
    patient: {
      nhsNumber: testData.tppPatient.nhsNumber,
      pdsId: testData.tppPatient.pdsId,
      pdsUpdateChangeNumber: testData.tppPatient.serialChangeNumber
    }
  };

  it('should throw error when nhsNumber is not defined in inputObject', () => {
    expect(() => generateUpdateOdsRequest(updateRequestWithoutPatientDetails)).toThrow(
      'Check template parameter error: nhsNumber is undefined, pdsId is undefined, pdsUpdateChangeNumber is undefined'
    );
  });

  it('should throw error when receivingService and sendingObject is not defined in inputObject', () => {
    expect(() =>
      generateUpdateOdsRequest({
        id: uuid(),
        timestamp: '20200101101010',
        patient: {
          nhsNumber: testData.tppPatient.nhsNumber,
          pdsId: testData.tppPatient.pdsId,
          pdsUpdateChangeNumber: testData.tppPatient.serialChangeNumber
        }
      })
    ).toThrow(
      'Check template parameter error: asid is undefined, asid is undefined, newOdsCode is undefined'
    );
  });

  it('should not throw error when all required arguments are defined', () => {
    expect(() => generateUpdateOdsRequest(validUpdateRequest)).not.toThrow();
  });

  it('should have populate the xml template with all the required fields', () => {
    const outboundRequest = generateUpdateOdsRequest(validUpdateRequest);

    expect(outboundRequest).toContain(validUpdateRequest.id);
    expect(outboundRequest).toContain(validUpdateRequest.timestamp);
    expect(outboundRequest).toContain(validUpdateRequest.receivingService.asid);
    expect(outboundRequest).toContain(validUpdateRequest.sendingService.asid);
    expect(outboundRequest.toUpperCase()).toContain(validUpdateRequest.newOdsCode.toUpperCase());
    expect(outboundRequest).toContain(validUpdateRequest.patient.nhsNumber);
    expect(outboundRequest).toContain(validUpdateRequest.patient.pdsId);
    expect(outboundRequest).toContain(validUpdateRequest.patient.pdsUpdateChangeNumber);
  });

  it('should uppercase the ODS code', () => {
    const updateODSRequest = generateUpdateOdsRequest(validUpdateRequest);
    expect(updateODSRequest).toContain('GPODSCODE');
  });
});
