# WildFly Core

WildFly Core is the core runtime of the WildFly application server. It provides the kernel, management layer, and foundational subsystems on which WildFly is built.

## Project basics

- **Language:** Java 17+
- **Build system:** Maven (multi-module)
- **License:** Apache License 2.0

## Build commands

```shell
# Full build
mvn clean install

# Build skipping tests
mvn clean install -DskipTests

# Build a single module (e.g. controller)
mvn clean install -pl controller -DskipTests

# Run tests for a single module
mvn clean install -pl controller
```

## Testing

Tests use JUnit and are run via maven-surefire-plugin. Integration tests live under `testsuite/`.

```shell
# Run all tests
mvn test

# Run a specific test class
mvn test -pl controller -Dtest=SomeTestClass

# Run testsuite integration tests
mvn clean install -pl testsuite/standalone -DskipTests=false
```

## Code style

- The project uses Checkstyle enforced at build time (`wildfly-checkstyle-config`).
- Suppressions are in `checkstyle-suppressions.xml` at the project root.
- Run `mvn checkstyle:checkstyle` to check style separately.

## Key modules

| Module | Purpose |
|--------|---------|
| `controller` | Management model, operations, and the core controller |
| `server` | Standalone server runtime |
| `host-controller` | Domain mode host controller |
| `process-controller` | Process lifecycle management |
| `cli` | Command-line management interface |
| `domain-management` | Domain management resources and operations |
| `elytron` | Security integration (Elytron) |
| `subsystem` | Base subsystem infrastructure |
| `logging` | Logging subsystem |
| `deployment-scanner` | Deployment scanner subsystem |
| `remoting` | Remoting subsystem |
| `io` | IO subsystem (XNIO/workers) |
| `testsuite` | Integration tests |

## Architecture notes

- The management model is tree-structured with resources, attributes, and operations.
- Subsystems register as extensions and contribute resources to the management model.
- The controller module is the heart of the project -- it handles operation execution, the model, and service container integration.
- `core-feature-pack` packages everything into a Galleon feature pack consumed by WildFly.

## Deep dives

Detailed guides on specific topics live in `.agents/deep-dives/`:

- [Subsystem Development](.agents/deep-dives/subsystem-development.md) -- adding/modifying resources, attributes, operations, XML schemas, model versioning, transformers, and testing.

## Contributing

- PRs go to the `master` branch.
- JIRA keys follow the pattern `WFCORE-XXXX` and should be in commit messages and PR titles.
- The GitHub PR template is at `.github/PULL_REQUEST_TEMPLATE.md`.
