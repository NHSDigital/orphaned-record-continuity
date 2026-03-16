export const portNumber = 3000;

export const config = () => ({
  nhsEnvironment: process.env.NHS_ENVIRONMENT || 'local',
  repoToGpServiceUrl: process.env.SERVICE_URL || `http://127.0.0.1:${portNumber}`,
  gp2gpMessengerServiceUrl: process.env.GP2GP_MESSENGER_SERVICE_URL || 'http://localhost:3001',
  gp2gpMessengerAuthKeys: process.env.GP2GP_MESSENGER_AUTHORIZATION_KEYS,
  ehrRepoServiceUrl: process.env.EHR_REPO_SERVICE_URL,
  ehrRepoAuthKeys: process.env.EHR_REPO_AUTHORIZATION_KEYS,
  repositoryAsid: process.env.REPOSITORY_ASID,
  fragmentTransferRateLimitTimeoutMilliseconds:
    process.env.FRAGMENT_TRANSFER_RATE_LIMIT_TIMEOUT_MILLISECONDS || 0,
  dynamodbGsiTimeoutMilliseconds: process.env.DYNAMODB_GSI_TIMEOUT_MILLISECONDS || 0,
  consumerApiKeys: loadConsumerKeys()
});

const loadConsumerKeys = () => {
  const consumerObjectKeys = {};
  Object.keys(process.env).forEach(envVarName => {
    if (envVarName.startsWith('API_KEY_FOR_')) {
      const consumerName = envVarName.split('API_KEY_FOR_')[1];
      consumerObjectKeys[consumerName] = process.env[envVarName];
    }
  });
  return consumerObjectKeys;
};

/*
  We are taking this timestamp when the system first boots up.
  We have seen cases where the fargate container may crash and restart when attempting to send a large EHR.
  In the event this happens, we want the system to be able to restart and continue where it left off.
 */
const serviceStartedTimestamp = Date.now();

/*
  If a 'duplicate' continue request comes in for an EHR with status CONTINUE_REQUEST_RECEIVED but the service has only
  started within the last 5 minutes, we can safely assume it has just crashed and rebooted and the request is safe to retry.
 */
export const hasServiceStartedInTheLast5Minutes = () => {
  return Date.now() - serviceStartedTimestamp <= 5 * 60 * 1000;
};
