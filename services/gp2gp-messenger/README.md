# Deductions GP2GP messenger

This is an implementation of a component to handle the sending of the GP2GP message set used to transfer a patient's Electronic Health Record between GP Practices.
It uses the GP2GP message format to transfer orphaned and stranded records out of a secure NHS repository.

This component will communicate with the Message Handler Service (MHS) using the [NIA MHS Adaptor](https://github.com/NHSDigital/integration-adaptor-mhs) and other components being developed by the Orphaned Record Continuity programme.

The initial version will send health records that are encoded in the HL7 format. A subsequent enhancement will be access to the components of the Health Record so that other services can use this component to send and receive Health Records with the need to implement the encoding and fragmentation strategies of the [GP2GP v2.2a](https://data.developer.nhs.uk/dms/mim/6.3.01/Domains/GP2GP/Document%20files/GP2GP%20IM.htm) message specification.

## Prerequisites

- [Node](https://nodejs.org/en/download/package-manager/#nvm) - version 14.x
- [Docker](https://docs.docker.com/install/)
 
## Set up

If you would like to run the app locally, you need to:

1. Run `npm install` to install all node dependencies as per `package.json`.

1. Set up the env variables and/or copy them into your IDE configurations (`Run -> Edit Configurations ->Environment Variables` in IntelliJ):

    ```bash
    export E2E_TEST_AUTHORIZATION_KEYS_FOR_GP2GP_MESSENGER=auth-key-2
    export REPOSITORY_URI=$IMAGE_REPO_NAME   
    export NHS_SERVICE=gp2gp-messenger
    export SERVICE_URL=http://${NHS_SERVICE}:3000
    export NHS_ENVIRONMENT=local
    export GP2GP_MESSENGER_REPOSITORY_ASID=deduction-asid
    export GP2GP_MESSENGER_REPOSITORY_ODS_CODE=deduction-ods
    ```

    > Locally, the variables `GP2GP_MESSENGER_REPOSITORY_ASID`, `GP2GP_MESSENGER_REPOSITORY_ODS_CODE` can be set
      to any value
  
1. The app will use a fake MHS when `NHS_ENVIRONMENT` is set to `local` or `dev`.

## Running the tests

Run the unit tests by  running `./tasks test_unit`
or on your machine with `npm run test:unit`

Run the integration tests with:
`./tasks test_integration`

You can also run them with `npm run test:integration` but that will require some additional manual set-up

Run the coverage tests (unit test and integration test)

By running `./tasks test_coverage`

or run `npm run test:coverage` on your machine

## Start the app locally

1. Run a development server with `npm run start:local`

### Swagger

The swagger documentation for the app can be found at `http://localhost:3000/swagger`. To update it, change the
`src/swagger.json` file. You can use the editor `https://editor.swagger.io/` which will validate your changes.

### Example request

```bash
curl -X POST "http://localhost:3000/ehr-request" -H "accept: application/json" -H "Authorization: auth-key-1" -H "Content-Type: application/json" -d "{ \"nhsNumber\": \"some-nhs-number\", \"odsCode\": \"some-ods-code\"}"
```

## Start the app in production mode

Compile the code with `npm run build`, and then start the server with `npm start`.

## Config

Ensure you have a VPN connection set up to the `dev` environment ([see this Confluence page](https://gpitbjss.atlassian.net/wiki/spaces/TW/pages/1832779966/VPN+for+Deductions+Services)).

## Access to AWS from CLI

## Access to AWS

In order to get sufficient access to work with terraform or AWS CLI, please follow the instructions on this [confluence pages](https://gpitbjss.atlassian.net/wiki/spaces/TW/pages/11384160276/AWS+Accounts+and+Roles)
and [this how to?](https://gpitbjss.atlassian.net/wiki/spaces/TW/pages/11286020174/How+to+set+up+access+to+AWS+from+CLI)

As a note, this set-up is based on the README of assume-role [tool](https://github.com/remind101/assume-role)
