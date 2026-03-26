import nock from 'nock';
import { getPracticeAsid, resolveSdsFhirApiKey } from '../sds-fhir-client';
import { initializeConfig } from '../../../config';
import { logError } from '../../../middleware/logging';
import { SSMClient } from '@aws-sdk/client-ssm';

jest.mock('../../../config');
jest.mock('../../../middleware/logging');
const mockSsmSend = jest.fn();
jest.mock('@aws-sdk/client-ssm', () => ({
  SSMClient: jest.fn().mockImplementation(() => ({
    send: mockSsmSend
  })),
  GetParameterCommand: jest.fn().mockImplementation(input => ({ input }))
}));

describe('sds-fhir-client', () => {
  const sdsFhirUrl = 'http://localhost';
  const sdsFhirApiKey = 'ssm:/test/key';
  const odsCode = 'A123456';
  const serviceId = 'urn:nhs:names:services:gp2gp:RCMR_IN010000UK05';
  const expectedAsid = '123456789012';

  const mockSsmSend = jest.fn();
  SSMClient.mockImplementation(() => ({ send: mockSsmSend }));

  // Mock SSM to return 'key' for the general tests
  mockSsmSend.mockResolvedValueOnce({ Parameter: { Value: 'key' } });

  initializeConfig.mockReturnValue({ sdsFhirUrl, sdsFhirApiKeyParameterName: sdsFhirApiKey });
  const mockResponse = {
    entry: [
      {
        resource: {
          identifier: [
            {
              system: 'https://fhir.nhs.uk/Id/nhsSpineASID',
              value: expectedAsid
            },
            {
              system: 'https://fake-fhir',
              value: 'B12345-836483'
            }
          ]
        }
      }
    ]
  };
  const mockResponseNoAsidIdentifier = {
    entry: [
      {
        resource: {
          identifier: [
            {
              system: 'https://fake-fhir',
              value: 'B12345-836483'
            }
          ]
        }
      }
    ]
  };
  const mockResponseMultipleAsidIdentifier = {
    entry: [
      {
        resource: {
          identifier: [
            {
              system: 'https://fhir.nhs.uk/Id/nhsSpineASID',
              value: expectedAsid
            },
            {
              system: 'https://fhir.nhs.uk/Id/nhsSpineASID',
              value: expectedAsid
            },
            {
              system: 'https://fhir.nhs.uk/Id/nhsSpineASID',
              value: expectedAsid
            }
          ]
        }
      }
    ]
  };
  const mockResponseNoAsidEntries = {
    entry: []
  };
  const mockResponseMultipleAsidEntries = {
    entry: [{ resource: {} }, { resource: {} }, { resource: {} }]
  };

  it('should make request to SDS FHIR URL and return ASID', async () => {
    const scope = nock(sdsFhirUrl, {
      reqheaders: {
        apiKey: 'key'
      }
    })
      .get(`/Device`)
      .query({
        organization: `https://fhir.nhs.uk/Id/ods-organization-code|${odsCode}`,
        identifier: `https://fhir.nhs.uk/Id/nhsServiceInteractionId|${serviceId}`
      })
      .reply(200, mockResponse);

    const response = await getPracticeAsid(odsCode, serviceId);

    expect(scope.isDone()).toBe(true);
    expect(response).toBe(expectedAsid);
  });

  it('should resolve the API key from SSM when SDS_FHIR_API_KEY is an SSM parameter reference', async () => {
    const sdsFhirApiKeyProperty = 'ssm:/sds-fhir/key';
    initializeConfig.mockReturnValueOnce({
      sdsFhirUrl,
      sdsFhirApiKeyParameterName: sdsFhirApiKeyProperty
    });
    mockSsmSend.mockResolvedValueOnce({ Parameter: { Value: 'resolved-key' } });

    const scope = nock(sdsFhirUrl, {
      reqheaders: {
        apiKey: 'resolved-key'
      }
    })
      .get(`/Device`)
      .query({
        organization: `https://fhir.nhs.uk/Id/ods-organization-code|${odsCode}`,
        identifier: `https://fhir.nhs.uk/Id/nhsServiceInteractionId|${serviceId}`
      })
      .reply(200, mockResponse);

    const response = await getPracticeAsid(odsCode, serviceId);

    expect(scope.isDone()).toBe(true);
    expect(response).toBe(expectedAsid);
    expect(mockSsmSend).toHaveBeenCalledTimes(1);
  });

  it('should throw an error when a status other than 200 is received', async () => {
    const errorMessage = new Error('Request failed with status code 500');
    const scope = nock(sdsFhirUrl, {
      reqheaders: {
        apiKey: 'key'
      }
    })
      .get(`/Device`)
      .query({
        organization: `https://fhir.nhs.uk/Id/ods-organization-code|${odsCode}`,
        identifier: `https://fhir.nhs.uk/Id/nhsServiceInteractionId|${serviceId}`
      })
      .reply(500);

    await expect(getPracticeAsid(odsCode, serviceId)).rejects.toThrow(errorMessage);
    expect(scope.isDone()).toBe(true);
    expect(logError).toHaveBeenCalledWith(
      `Failed to retrieve ASID from FHIR for ODS Code: ${odsCode} - error: ${errorMessage.message}`
    );
  });

  it('should throw an error when no ASID identifiers are found in FHIR response', async () => {
    const errorMessage = new Error(`No ASID identifier found for ODS code ${odsCode}`);
    const scope = nock(sdsFhirUrl, {
      reqheaders: {
        apiKey: 'key'
      }
    })
      .get(`/Device`)
      .query({
        organization: `https://fhir.nhs.uk/Id/ods-organization-code|${odsCode}`,
        identifier: `https://fhir.nhs.uk/Id/nhsServiceInteractionId|${serviceId}`
      })
      .reply(200, mockResponseNoAsidIdentifier);

    await expect(getPracticeAsid(odsCode, serviceId)).rejects.toThrow(errorMessage);
    expect(scope.isDone()).toBe(true);
    expect(logError).toHaveBeenCalledWith(
      `Failed to retrieve ASID from FHIR for ODS Code: ${odsCode} - error: ${errorMessage.message}`
    );
  });

  it('should throw an error when multiple ASID identifiers are found in FHIR response', async () => {
    const errorMessage = new Error(`Multiple ASID identifiers found for ODS code ${odsCode}`);
    const scope = nock(sdsFhirUrl, {
      reqheaders: {
        apiKey: 'key'
      }
    })
      .get(`/Device`)
      .query({
        organization: `https://fhir.nhs.uk/Id/ods-organization-code|${odsCode}`,
        identifier: `https://fhir.nhs.uk/Id/nhsServiceInteractionId|${serviceId}`
      })
      .reply(200, mockResponseMultipleAsidIdentifier);

    await expect(getPracticeAsid(odsCode, serviceId)).rejects.toThrow(errorMessage);
    expect(scope.isDone()).toBe(true);
    expect(logError).toHaveBeenCalledWith(
      `Failed to retrieve ASID from FHIR for ODS Code: ${odsCode} - error: ${errorMessage.message}`
    );
  });

  it('should throw an error when no ASID entries are found in FHIR response', async () => {
    const errorMessage = new Error(`No ASID entries found for ODS code ${odsCode}`);
    const scope = nock(sdsFhirUrl, {
      reqheaders: {
        apiKey: 'key'
      }
    })
      .get(`/Device`)
      .query({
        organization: `https://fhir.nhs.uk/Id/ods-organization-code|${odsCode}`,
        identifier: `https://fhir.nhs.uk/Id/nhsServiceInteractionId|${serviceId}`
      })
      .reply(200, mockResponseNoAsidEntries);

    await expect(getPracticeAsid(odsCode, serviceId)).rejects.toThrow(errorMessage);
    expect(scope.isDone()).toBe(true);
    expect(logError).toHaveBeenCalledWith(
      `Failed to retrieve ASID from FHIR for ODS Code: ${odsCode} - error: ${errorMessage.message}`
    );
  });

  it('should throw an error when multiple ASID entries are found in FHIR response', async () => {
    const errorMessage = new Error(`Multiple ASID entries found for ODS code ${odsCode}`);
    const scope = nock(sdsFhirUrl, {
      reqheaders: {
        apiKey: 'key'
      }
    })
      .get(`/Device`)
      .query({
        organization: `https://fhir.nhs.uk/Id/ods-organization-code|${odsCode}`,
        identifier: `https://fhir.nhs.uk/Id/nhsServiceInteractionId|${serviceId}`
      })
      .reply(200, mockResponseMultipleAsidEntries);

    await expect(getPracticeAsid(odsCode, serviceId)).rejects.toThrow(errorMessage);
    expect(scope.isDone()).toBe(true);
    expect(logError).toHaveBeenCalledWith(
      `Failed to retrieve ASID from FHIR for ODS Code: ${odsCode} - error: ${errorMessage.message}`
    );
  });

  describe('resolveSdsFhirApiKey', () => {
    it('should throw an error when SDS_FHIR_API_KEY is not configured', async () => {
      await expect(resolveSdsFhirApiKey(null)).rejects.toThrow(
        'SDS FHIR API key is not configured. Please set the SDS_FHIR_API_KEY_SSM_PARAMETER_NAME or SDS_FHIR_API_KEY environment variable.'
      );
      await expect(resolveSdsFhirApiKey(undefined)).rejects.toThrow(
        'SDS FHIR API key is not configured. Please set the SDS_FHIR_API_KEY_SSM_PARAMETER_NAME or SDS_FHIR_API_KEY environment variable.'
      );
      await expect(resolveSdsFhirApiKey('')).rejects.toThrow(
        'SDS FHIR API key is not configured. Please set the SDS_FHIR_API_KEY_SSM_PARAMETER_NAME or SDS_FHIR_API_KEY environment variable.'
      );
    });

    it('should return plain API key when SDS_FHIR_API_KEY is not an SSM reference', async () => {
      const result = await resolveSdsFhirApiKey('plain-api-key');
      expect(result).toBe('plain-api-key');
    });

    it('should throw an error when SSM parameter name is empty', async () => {
      await expect(resolveSdsFhirApiKey('ssm:')).rejects.toThrow(
        'SDS FHIR API key SSM parameter name is empty'
      );
      await expect(resolveSdsFhirApiKey('ssm:   ')).rejects.toThrow(
        'SDS FHIR API key SSM parameter name is empty'
      );
    });

    it('should resolve API key from SSM parameter', async () => {
      const parameterName = '/resolve/test';
      const resolvedKey = 'resolved-api-key';
      mockSsmSend.mockResolvedValueOnce({ Parameter: { Value: resolvedKey } });

      const result = await resolveSdsFhirApiKey(parameterName);

      expect(result).toBe(resolvedKey);
      expect(mockSsmSend).toHaveBeenCalledWith(
        expect.objectContaining({
          input: expect.objectContaining({
            Name: parameterName,
            WithDecryption: true
          })
        })
      );
    });

    it('should resolve API key from SSM parameter with ssm: prefix', async () => {
      const parameterName = 'ssm:/resolve/prefix';
      const resolvedKey = 'resolved-api-key';
      mockSsmSend.mockResolvedValueOnce({ Parameter: { Value: resolvedKey } });

      const result = await resolveSdsFhirApiKey(parameterName);

      expect(result).toBe(resolvedKey);
      expect(mockSsmSend).toHaveBeenCalledWith(
        expect.objectContaining({
          input: expect.objectContaining({
            Name: '/resolve/prefix',
            WithDecryption: true
          })
        })
      );
    });

    it('should throw an error when SSM parameter cannot be resolved', async () => {
      const parameterName = '/error/test';
      mockSsmSend.mockResolvedValueOnce({ Parameter: null });

      await expect(resolveSdsFhirApiKey(parameterName)).rejects.toThrow(
        'SDS FHIR API key could not be resolved from SSM parameter "/error/test"'
      );
    });

    it('should cache resolved API key', async () => {
      const parameterName = '/cache/test';
      const resolvedKey = 'cached-key';
      mockSsmSend.mockResolvedValueOnce({ Parameter: { Value: resolvedKey } });

      // First call
      const result1 = await resolveSdsFhirApiKey(parameterName);
      expect(result1).toBe(resolvedKey);
      expect(mockSsmSend).toHaveBeenCalledTimes(1);

      // Second call should use cache
      const result2 = await resolveSdsFhirApiKey(parameterName);
      expect(result2).toBe(resolvedKey);
      expect(mockSsmSend).toHaveBeenCalledTimes(1); // Still 1, not 2
    });
  });
});
