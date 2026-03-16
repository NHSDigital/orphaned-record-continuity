export const portNumber = 3000;

export const initializeConfig = () => ({
  ehrServiceUrl: process.env.SERVICE_URL || `http://127.0.0.1:${portNumber}`,
  awsS3BucketName: process.env.S3_BUCKET_NAME || 'test-bucket',
  localstackUrl: process.env.LOCALSTACK_URL || 'http://localhost:4566',
  nhsEnvironment: process.env.NHS_ENVIRONMENT || 'local',
  consumerApiKeys: loadConsumerKeys()
});

const loadConsumerKeys = () => {
  const consumerApiKeys = {};
  Object.keys(process.env).forEach((envVarName) => {
    if (envVarName.startsWith('API_KEY_FOR_')) {
      const consumerName = envVarName.split('API_KEY_FOR_')[1];
      consumerApiKeys[consumerName] = process.env[envVarName];
    }
  });
  return consumerApiKeys;
};
