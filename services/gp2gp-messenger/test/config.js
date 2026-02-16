export const config = {
  nhsEnvironment: process.env.NHS_ENVIRONMENT || 'dev',
  gp2gpMessengerUrl: process.env.SERVICE_URL || 'http://localhost:3000', // FIXME: rename to add prefix
  e2eTestAuthorizationKeysForGp2gpMessenger:
    process.env.E2E_TEST_AUTHORIZATION_KEYS_FOR_GP2GP_MESSENGER
};
