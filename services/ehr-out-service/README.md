# ehr-out-service

This component is part of the Repository, responsible for creating and handling of EHR transfers out from the Repository,
when the Orphaned/Stranded health record stored in the Repository's `ehr-repository` is requested by the next registering
practice.
When the a GP2GP EHR request on its incoming queue, the `ehr-out-service` accepts this and creates a record to track this outward transfer.
After the successful validation of the request and retrieval of patient's health record from `ehr-repository`, it sends the 
EHR out to the requesting practice via `gp2gp-messenger`.


## Prerequisites

Follow the links to download

- [Node](https://nodejs.org/en/download/package-manager/#nvm) - version 14.x
- [Docker](https://docs.docker.com/install/)

## Starting the app locally

1. Run `npm install` to install all node dependencies.
2. Configure local environment variables:
    - run `./tasks setup_test_integration_local`
3. Run `npm run start:local`
4. If successful, you will be able to reach the Swagger docs: [http://localhost:3000/swagger/](http://localhost:3000/swagger/)

Note: `npm run start:nodemon` can be used to build the app before launching the Express server on port `3000` using [nodemon](https://www.npmjs.com/package/nodemon) - it will watch and reload the server upon any file changes.

### Debugging and testing the app docker image

A Docker image can be built locally with:

1. Run `./tasks build`. This runs babel on source (needed?) but also uses `npm install` to ensure `package-lock,json` is up-to-date.
2. Run `./tasks build_docker_local`. This builds the docker containers `deductions/<component-name>:<commit-no>` and `deductions/<component-name>:latest` with the app in
3. Run `./tasks test_docker_local` to ensure the image has been built correctly
4. If the above fails, `./tasks run_docker_local` to debug production build

## Swagger

The swagger documentation for the app can be found at [http://localhost:3000/swagger](http://localhost:3000/swagger). To update it, change the
`src/swagger.json` file. You can use [this editor](https://editor.swagger.io/) which will validate your changes.

## Tests

### Unit tests

Run the unit tests with `npm run test:unit` (or `npm test` to run it with lint). 

Alternatively, `./tasks test` can be used to run the tests.

### Integration tests

Run `./tasks test_integration` to run integration tests.

### Coverage tests

Runs the coverage tests (unit test and integration test) and collects coverage metrics.
Run `./tasks test_coverage` to run coverage tests.

## Pre-commit Checks

Before committing, ensure you run the following tests:

1. Unit tests
2. Integration tests
3. Coverage tests
4. Local docker test

#### Environment variables

Below are the environment variables that are automatically set:

- `NHS_ENVIRONMENT` - is set to the current environment in which the container is deployed. It is set in Terraform and populated by the pipeline.gocd.yml for tests.
- `SERVICE_URL` - This is prepopulated by `tasks` and will configure it to service URL according to environment.
- `REPOSITORY_URI` - This is prepopulated by `tasks` (based on `IMAGE_REPO_NAME`)

## Access to AWS

In order to get sufficient access to work with terraform or AWS CLI, please export secrets from the AWS Access Portal for the environment you are using.