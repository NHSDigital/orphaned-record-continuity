const generatePdsRetrievalQuery = require('../generate-pds-retrieval-request');
const testData = require('./testData.json');

const mockUUID = 'dce9a411-ad97-426b-86b6-55baf2d0d6e4';

describe('generatePdsRetrievalQuery', () => {
  const testObjectMissing = {
    id: mockUUID,
    timestamp: '20200403092516',
    receivingService: {
      asid: testData.pds.asid
    },
    sendingService: {
      asid: testData.mhs.asid
    }
  };

  const testObjectComplete = {
    ...testObjectMissing,
    patient: {
      nhsNumber: testData.tppPatient.nhsNumber
    }
  };

  it('should throw error when nhsNumber is not defined in inputObject', () => {
    return expect(generatePdsRetrievalQuery(testObjectMissing)).rejects.toThrow(
      'nhsNumber is undefined'
    );
  });

  it('should not throw error when all required arguments are defined', () => {
    return expect(generatePdsRetrievalQuery(testObjectComplete)).resolves.toEqual(
      expect.anything()
    );
  });

  it('should have populate the xml template with all the required fields', async () => {
    const pdsRequestQuery = await generatePdsRetrievalQuery(testObjectComplete);

    const checkEntries = object => {
      Object.keys(object).map(key => {
        if (typeof object[key] === 'object') {
          checkEntries(object[key]);
        } else {
          expect(pdsRequestQuery).toContain(object[key]);
        }
      });
    };

    checkEntries(testObjectComplete);
  });

  it('should return a Promise (can use .then())', () => {
    return generatePdsRetrievalQuery({
      ...testObjectComplete,
      id: mockUUID
    }).then(response => {
      return expect(typeof response === 'string');
    });
  });

  it('should return response that is escaped (includes /n) and contains QUPA_IN000008UK02 opening and closing tags', () => {
    return generatePdsRetrievalQuery({
      ...testObjectComplete,
      id: mockUUID
    }).then(response => {
      expect(typeof response === 'string');
      expect(response).toContain('\n');
      return expect(response).toMatch(/^<QUPA_IN000008UK02(.*)<\/QUPA_IN000008UK02>$/gs);
    });
  });

  it('should throw error when receivingService and sendingObject is not defined in inputObject', () => {
    return expect(
      generatePdsRetrievalQuery({
        id: mockUUID,
        timestamp: '20200403092516',
        patient: {
          nhsNumber: testData.tppPatient.nhsNumber
        }
      })
    ).rejects.toThrow('Check template parameter error: asid is undefined, asid is undefined');
  });
});
