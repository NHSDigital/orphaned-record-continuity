const SQS_EHR_OUT_INCOMING_QUEUE_NAME = 'test-ehr-out-service-incoming';
const AWS_ACCOUNT_NO = '000000000000';
let localstackEndpointUrl = process.env.LOCALSTACK_URL || 'http://localhost:4566';
export const config = {
  nhsEnvironment: process.env.NHS_ENVIRONMENT,
  serviceUrl: process.env.SERVICE_URL,
  localstackEndpointUrl: localstackEndpointUrl,
  region: process.env.AWS_DEFAULT_REGION || 'eu-west-2',
  awsAccountNo: AWS_ACCOUNT_NO,
  SQS_EHR_OUT_INCOMING_QUEUE_NAME,
  SQS_EHR_OUT_INCOMING_QUEUE_URL: `${localstackEndpointUrl}/${AWS_ACCOUNT_NO}/${SQS_EHR_OUT_INCOMING_QUEUE_NAME}`
};

export const initialiseAppConfig = () => {
  process.env.SQS_EHR_OUT_INCOMING_QUEUE_URL = config.SQS_EHR_OUT_INCOMING_QUEUE_URL;
};
