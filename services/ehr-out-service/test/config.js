const SQS_EHR_OUT_INCOMING_QUEUE_NAME = 'test-ehr-out-service-incoming';
const AWS_ACCOUNT_NO = '000000000000';
const LOCALSTACK_URL = 'http://localhost:4566';
const AWS_REGION = 'eu-west-2';
export const config = {
  nhsEnvironment: "dev",
  serviceUrl: "www.example.com",
  localstackEndpointUrl: LOCALSTACK_URL,
  region: AWS_REGION,
  awsAccountNo: AWS_ACCOUNT_NO,
  SQS_EHR_OUT_INCOMING_QUEUE_NAME,
  SQS_EHR_OUT_INCOMING_QUEUE_URL: `${LOCALSTACK_URL}/${AWS_ACCOUNT_NO}/${SQS_EHR_OUT_INCOMING_QUEUE_NAME}`
};

export const initialiseAppConfig = () => {
  process.env.SQS_EHR_OUT_INCOMING_QUEUE_URL = config.SQS_EHR_OUT_INCOMING_QUEUE_URL;
};
