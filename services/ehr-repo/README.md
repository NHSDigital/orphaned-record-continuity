# EHR Repository

A store for the Electronic Health Records (EHRs) of patients that are no longer registered with a GP Practice.

The stored the EHR is composed of the messages sent from its previous holder in its transfer over the GP2GP protocol.

## Prerequisites

- [Node](https://nodejs.org/en/download/package-manager/#nvm) - version 24.x
- [Docker](https://docs.docker.com/install/)

## Set up

From within `services/ehr-repository` run `make env` to install necessary dependencies to run localstack and tests


If you would like to run the app locally, you need to:
1. Run `npm install` to install all node dependencies as per `package.json`.
2. Copy the template.env file into a .env file and/or copy them into your IDE configurations (`Run -> Edit Configurations ->Environment Variables` in IntelliJ):
3. run `npm start:local`


## Running the tests

Run `npm run test:unit` for unit tests

Run `make test-coverage` on your machine for unit and integration tests


## Start the app locally

Run `docker-compose up -d` to spin up all the dependencies.

Run a development server with `npm run start:local`.


## Docker

Docker image can be build locally with

```
./tasks build_docker_local
```

### Swagger

The swagger documentation for the app can be found at `http://localhost:3000/swagger`. To update it, change the
`src/swagger.json` file. You can use the editor `https://editor.swagger.io/` which will validate your changes.
