export const portNumber = 3000;

export const initializeConfig = () => ({
  repoAsid: process.env.GP2GP_MESSENGER_REPOSITORY_ASID || '200000001161',
  repoOdsCode: (process.env.GP2GP_MESSENGER_REPOSITORY_ODS_CODE || 'B86041').toUpperCase(),
  pdsAsid: process.env.PDS_ASID,
  mhsOutboundUrl: process.env.GP2GP_MESSENGER_MHS_OUTBOUND_URL,
  nhsEnvironment: process.env.NHS_ENVIRONMENT || 'local',
  nhsNumberPrefix: false, // TODO: Updated to "false" in order to process real patients. Need a better solution than this.
  sdsFhirApiKeyParameterName: process.env.SDS_FHIR_API_KEY_SSM_PARAMETER_NAME || process.env.SDS_FHIR_API_KEY,
  sdsFhirUrl: process.env.SDS_FHIR_URL,
  spineOrgCode: process.env.SPINE_ORG_CODE,
  requestEhrOnlyForSafeListedOdsCodesToggle:
    process.env.REQUEST_EHR_ONLY_FOR_SAFE_LISTED_ODS_CODES === 'true', //Casting string to boolean
  safeListedOdsCodes: process.env.SAFE_LISTED_ODS_CODES,
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
