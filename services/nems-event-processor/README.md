# prm-deductions-nems-event-processor

This component is a Java service that receives NEMS events when there is a change of GP for a patient in PDS (Personal Demographics Service). It processes each event to filter for relevant use cases and extracts important information from each, and forwards this information to another queue for further use.

## Prerequisites

- Java 25 LTS
- Gradle 9.3

### Running the tests

#### All tests
To run all Unit and Integration tests and produce a coverage report, in your terminal, run `./tasks test_all`

#### Unit testing
These are easiest to run from your IDE, however, you can also run them from your terminal with: `./tasks test_unit`

#### Integration testing
The integration tests can be run from your terminal with: `./tasks test_integration` which will start and stop LocalStack for you.

If you want to run these from your IDE, you must first start LocalStack with: `./tasks start_localstack`
It is recommended that you stop LocalStack after running the tests with: `./tasks stop_localstack`
