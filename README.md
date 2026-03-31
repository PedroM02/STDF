# STDF

This repository contains the STDF project. The Java module is in `pt.ulisboa.tecnico.sdtf`, and that is where the tests are built and executed.

## Requirements

To run the tests you need:

- Java 17
- Maven 3.9+
- a writable `/tmp` directory

You can confirm the installed versions with:

```bash
java -version
mvn -version
```

## Project structure

- `pt.ulisboa.tecnico.sdtf/`: Java source code, Maven configuration, and tests
- `contract/`: Solidity contract and compiled artifact
- `blocks/`: persisted block data

## Running the tests

Move into the Maven module:

```bash
cd pt.ulisboa.tecnico.sdtf
```

Run the test suite with:

```bash
mvn test
```

This is the supported command to execute the current working tests in the repository.

## What `mvn test` runs

The Maven configuration has been updated so that `mvn test` runs the maintained tests that match the current codebase, including:

- client tests
- the new Byzantine and security-oriented blockchain tests
- HotStuff replica safety tests
- contract approval-frontrunning mitigation tests

## Old tests that no longer work

Some older test files are still present in the repository, but they no longer match the current source APIs and are therefore excluded from `mvn test`.

The excluded files are:

- `tests/blockchain/InMemoryLedgerTests.java`
- `tests/gateway/LocalAppendGatewayTests.java`
- `tests/links/PerfectLinkTests.java`
- `tests/links/RetryLinkTests.java`
- `tests/links/AuthenticatedPerfectLinkTests.java`
- `tests/consensus/ViewChangeSimulator.java`