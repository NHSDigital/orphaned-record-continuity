### prm-repo-ehr-transfer-service

This component of Orphaned Record Continuity (ORC) is responsible for the requesting and receiving (incoming transfer) 
of a patient's Electronic Health Record over GP2GP.

It is the first receiver of all inbound messages (from MHS inbound) for the ORC and will send on 
irrelevant messages to a downstream "not handled" queue so that they can be actioned by the service responsible.

## Prerequisites

- Java 25 LTS
- Gradle 9.3

### AWS helpers

This repository imports shared AWS helpers
from [prm-deductions-support-infra](https://github.com/nhsconnect/prm-deductions-support-infra/). They can be
found `utils` directory after running any task from `tasks` file.

## Set up

### Running the application

In IntelliJ editor, use the green `play` button next to the main class `Gp2gpmessagehandlerApplication` to run the
application

In your terminal with `./gradlew bootRun`.

This application relies on the queues to send/receive messages. They are spun up with `docker-compose.localstack-local.yml`.
You can access the queues using the Active MQ console on: `http://localhost:8161/`

### Running the tests

#### All tests
To run all Unit and Integration tests and produce a coverage report, in your terminal, run `./tasks test_all`

#### Unit testing
These are easiest to run from your IDE, however, you can also run them from your terminal with: `./tasks test_unit`

#### Integration testing
The integration tests can be run from your terminal with: `./tasks test_integration` which will start and stop LocalStack for you.

If you want to run these from your IDE, you must first start LocalStack with: `./tasks start_localstack`
It is recommended that you stop LocalStack after running the tests with: `./tasks stop_localstack`

### Config

If you need to add any new configuration items, update the `src/main/resources/application.properties` file per
environment as well as add the environment variables in `./tasks` `configure_local_envariables`.

Ensure you have VPN connection set up to `dev` environment:
[CLICK HERE](https://gpitbjss.atlassian.net/wiki/spaces/TW/pages/1832779966/VPN+for+Deductions+Services)

### Setup

In AmazonMQ settings for the `dev` provision. Edit the `deductor-amq-broker-${NHS_ENVIRONMENT}`
security group inbound rules. Add new rule that allows All TCP from the `${NHS_ENVIRONMENT} VPN VM security group`,
apply before running the following:

```
// Starts the server locally using `.env`
$ NHS_ENVIRONMENT=dev 
```

## Access to AWS

In order to get sufficient access to work with terraform or AWS CLI:

Make sure to unset the AWS variables:

```
unset AWS_ACCESS_KEY_ID
unset AWS_SECRET_ACCESS_KEY
unset AWS_SESSION_TOKEN
```

As a note, the following set-up is based on the README of assume-role [tool](https://github.com/remind101/assume-role)

Set up a profile for each role you would like to assume in `~/.aws/config`, for example:

```
[profile default]
region = eu-west-2
output = json

[profile admin]
region = eu-west-2
role_arn = <role-arn>
mfa_serial = <mfa-arn>
source_profile = default
```

The `source_profile` needs to match your profile in `~/.aws/credentials`.

```
[default]
aws_access_key_id = <your-aws-access-key-id>
aws_secret_access_key = <your-aws-secret-access-key>
```

## Assume role with elevated permissions

In order to get sufficient access to work with terraform or AWS CLI, please export secrets from the AWS Access Portal for the environment you are using

## AWS SSM Parameters Design Principles

When creating the new ssm keys, please follow the agreed convention as per the design specified below:

* all parts of the keys are lower case
* the words are separated by dashes (`kebab case`)
* `env` is optional

### Design:

Please follow this design to ensure the ssm keys are easy to maintain and navigate through:

| Type               | Design                                  | Example                                               |
| -------------------| ----------------------------------------| ------------------------------------------------------|
| **User-specified** |`/repo/<env>?/user-input/`               | `/repo/${var.environment}/user-input/db-username`     |
| **Auto-generated** |`/repo/<env>?/output/<name-of-git-repo>/`| `/repo/output/prm-deductions-base-infra/root-zone-id` |

## WireMock for Integration Testing

When using WireMock for integration testing, it is essential to configure your endpoints to point to `localhost`.

### Configuration

To set up WireMock within your tests, use the `@WireMockTest(httpPort = 8080)` annotation at the class level. The port number `8080` is configurable and can be replaced with any port of your choice. This annotation ensures that WireMock is active on the specified port during the test execution.

### Stubbing

When stubbing responses with WireMock, you must ensure that the URL used in the stubs matches the local server configuration. Specifically, the URL should be in the format `http://localhost:{PORT}` where `{PORT}` corresponds to the port number defined in the `@WireMockTest` annotation.

### Example

Here is an example of a test class using WireMock:

```java
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

@WireMockTest(httpPort = 8080)
public class MyIntegrationTest {

    @Test
    public void testEndpoint() {
        stubFor(get(urlEqualTo("/some-endpoint"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Hello, WireMock!")));

        // Your test code here, e.g., making an HTTP request to http://localhost:8080/some-endpoint
    }
}
```

In this example:

* The @WireMockTest(httpPort = 8080) annotation configures WireMock to run on port 8080.
* The stubFor method is used to define a stub for the endpoint /some-endpoint, returning a 200 OK response with the body "Hello, WireMock!".
* Your test logic would involve making an HTTP request to http://localhost:8080/some-endpoint to verify the stubbed response.

By following this approach, you can effectively use WireMock to simulate and test interactions with external services in your integration tests.