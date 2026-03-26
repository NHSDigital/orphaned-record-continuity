import path from 'path';
import { readFileSync } from 'fs';
import axios from 'axios';
import nock from 'nock';

const FAKE_AUTH_KEY = 'fake-auth-key';
const MOCK_MHS_OUTBOUND_URL = 'http://localhost/mhs-outbound';
const MOCK_SDS_FHIR_URL = 'http://localhost/sds-fhir';
const FAKE_SDS_API_KEY = 'fake-sds-api-key';
const FAKE_REPO_ASID_CODE = 'fake_repo_asid_code';
const FAKE_DEST_ASID_CODE = 'fake_dest_asid_code';
const FAKE_REPO_ODS_CODE = 'B85002';
const FAKE_DEST_ODS_CODE = 'M85019';
const TEST_NHS_NUMBER = '9876543210';

export const setupEnvVarsForTest = () => {
  process.env.API_KEY_FOR_TEST_USER = FAKE_AUTH_KEY;
  process.env.GP2GP_MESSENGER_MHS_OUTBOUND_URL = MOCK_MHS_OUTBOUND_URL;
  process.env.SDS_FHIR_URL = MOCK_SDS_FHIR_URL;
  process.env.SDS_FHIR_API_KEY = FAKE_SDS_API_KEY;
  process.env.GP2GP_MESSENGER_REPOSITORY_ASID = FAKE_REPO_ASID_CODE;
  process.env.GP2GP_MESSENGER_REPOSITORY_ODS_CODE = FAKE_REPO_ODS_CODE;
};

export const EhrMessageType = {
  core: 'Core',
  fragment: 'Fragment',
  coreWithLargeMedicalHistory: 'CoreWithLargeMedicalHistory'
};

// Generate random UUIDs for tests, bypassing the jest mock for uuid package
const randomUUID = () => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'
    .replace(/[xy]/g, c => {
      const r = (Math.random() * 16) | 0;
      const v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    })
    .toUpperCase();
};
export const generateRandomIdsForTest = () => {
  return {
    conversationId: randomUUID(),
    messageId: randomUUID(),
    ehrRequestId: randomUUID()
  };
};

export const isSmallerThan256KB = input => {
  const jsObjectAsString = typeof input == 'string' ? input : JSON.stringify(input);
  return jsObjectAsString.length < 256 * 1024;
};

export const loadMessageAndUpdateIds = (
  messageType,
  { conversationId, messageId, ehrRequestId }
) => {
  const filename = `TestEhr${messageType}`;
  const filepath = path.join(__dirname, 'data', filename);

  const fileContentWithIdsReplaced = readFileSync(filepath, 'utf8')
    .replaceAll('__CONVERSATION_ID__', conversationId)
    .replaceAll('__MESSAGE_ID__', messageId)
    .replaceAll('__EHR_REQUEST_ID__', ehrRequestId)
    .replaceAll('__NHS_NUMBER__', TEST_NHS_NUMBER)
    .replaceAll('__REPO_ASID_CODE__', FAKE_REPO_ASID_CODE)
    .replaceAll('__REPO_ODS_CODE__', FAKE_REPO_ODS_CODE)
    .replaceAll('__DEST_ASID_CODE__', FAKE_DEST_ASID_CODE)
    .replaceAll('__DEST_ODS_CODE__', FAKE_DEST_ODS_CODE);
  return JSON.parse(fileContentWithIdsReplaced);
};

export const buildPostRequestBody = (
  messageType,
  ehrMessage,
  { conversationId, messageId, ehrRequestId }
) => {
  switch (messageType) {
    case EhrMessageType.core:
    case EhrMessageType.coreWithLargeMedicalHistory:
      return {
        conversationId,
        messageId,
        ehrRequestId,
        odsCode: FAKE_DEST_ODS_CODE,
        coreEhr: ehrMessage
      };
    case EhrMessageType.fragment:
      return {
        conversationId,
        messageId,
        odsCode: FAKE_DEST_ODS_CODE,
        fragmentMessage: ehrMessage
      };
    default:
      throw new TypeError('messageType should be either core or fragment.');
  }
};

export const createMockFhirScope = () => {
  /*
  Create a nock scope to mock the SDS FHIR api, which gp2gp messenger get ASID codes from.
  */
  const mockFhirResponse = {
    entry: [
      {
        resource: {
          identifier: [
            {
              system: 'https://fhir.nhs.uk/Id/nhsSpineASID',
              value: FAKE_DEST_ASID_CODE
            }
          ]
        }
      }
    ]
  };

  return nock(MOCK_SDS_FHIR_URL, {
    reqheaders: {
      apiKey: FAKE_SDS_API_KEY
    }
  })
    .get(`/Device`)
    .query(() => true)
    .reply(200, mockFhirResponse);
};

export const createMockMHSScope = (response = [200, 'OK']) => {
  /*
  Create a nock scope to mock the outbound MHS adaptor and capture request details.
  Uses an axios response interceptor to capture the request headers after they're sent,
  matching how the application logs them (from response.request.headers).
  */
  const body = {};
  const headers = {};
  let interceptorId;

  // Set up an axios response interceptor to capture the request headers from the response
  // This matches how the application logs them: response.request.headers
  interceptorId = axios.interceptors.response.use(
    axiosResponse => {
      if (axiosResponse.config.url === MOCK_MHS_OUTBOUND_URL) {
        // Capture headers from response.request.headers
        // Try to get headers by iterating keys in case they're in a special object
        const reqHeaders = axiosResponse.request?.headers;
        if (reqHeaders) {
          // First try direct assignment
          if (typeof reqHeaders === 'object') {
            Object.assign(headers, reqHeaders);
            // If that didn't work, try iterating keys
            if (Object.keys(headers).length === 0 && Object.keys(reqHeaders).length > 0) {
              for (const key of Object.keys(reqHeaders)) {
                headers[key] = reqHeaders[key];
              }
            }
          }
        }
      }
      return axiosResponse;
    },
    error => {
      // Also handle error responses
      return Promise.reject(error);
    }
  );

  const scope = nock(MOCK_MHS_OUTBOUND_URL)
    .post('')
    .reply(function (uri, requestBody) {
      Object.assign(body, requestBody);
      return response;
    });

  // Clean up interceptor when scope is done
  const originalIsDone = scope.isDone.bind(scope);
  scope.isDone = function () {
    const result = originalIsDone();
    if (interceptorId !== undefined) {
      axios.interceptors.response.eject(interceptorId);
    }
    return result;
  };

  scope.outboundBody = body;
  scope.outboundHeaders = headers;
  return scope;
};
