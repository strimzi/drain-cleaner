# Strimzi Drain Cleaner

Strimzi Drain Cleaner helps safely drain Kubernetes and OpenShift nodes that run Strimzi-managed Apache Kafka® brokers and controllers. Its validating admission webhook intercepts pod eviction requests and annotates eligible pods for a rolling restart by the Strimzi Cluster Operator.

See [README.md](README.md) for deployment, configuration, and community information.

## Architecture

Single-module Maven project using Quarkus.

### Requirements

- **Java**: JDK 21 for development and CI, as configured in `pom.xml`. The container uses a Java 25 runtime.
- **Build tools**: Maven; make and Bash for the Makefile and shell workflows.
- **Helm**: Helm 3 for rendering and packaging; the `helm-unittest` plugin for chart tests and `yq` for regenerating installation manifests.
- **Container builds**: Docker. The Makefile invokes `docker` directly.
- **System tests**: A Kubernetes or OpenShift test cluster, `kubectl`, Helm for Helm-based installation, and a Drain Cleaner image the cluster can pull.

### Key Dependencies

- Quarkus REST with Jackson for the webhook, ArC for dependency injection, and the Fabric8 Kubernetes client through the Quarkus Kubernetes client extension.
- JUnit 5, Mockito, and REST Assured for Java tests.
- See [pom.xml](pom.xml) for dependency and plugin versions.

### Project Structure

| Path | Purpose |
|------|---------|
| `src/main/java/io/strimzi/` | `ValidatingWebhook` handles eviction requests; `KubernetesClientProducer` configures the Kubernetes client; `CertificateWatch` and `HealthCheck` handle certificate monitoring and health checks |
| `src/main/resources/` | Quarkus application configuration and native-image reflection configuration |
| `src/test/java/io/strimzi/` | Unit tests, cluster-backed tests in `systemtest/`, and shared test helpers in `utils/` |
| `src/test/resources/` | Test fixtures, including sample admission and eviction requests |
| `packaging/helm-charts/helm3/strimzi-drain-cleaner/` | Helm chart source, values, templates, chart documentation, and chart unit tests |
| `packaging/install/` | Installation bundles for Kubernetes with and without cert-manager, and for OpenShift |
| `Dockerfile`, `bin/` | Container build and startup script |
| `.github/workflows/`, `development-docs/` | CI and release automation; build, test, and release guides |

## Contributing

- **Commit sign-off (DCO)**: All commits MUST be signed off by the contributor using `git commit -s`. To fix a missing sign-off on your last commit, use `git commit --amend -s`. See [README.md#contributing](README.md#contributing).
- **Code style**: Checkstyle runs during Maven's `validate` phase for both main and test sources. Follow `.checkstyle/checkstyle.xml` and run `mvn checkstyle:check@validate` to check style locally.
- **Opening issues**:
  - You MUST use the appropriate [issue template](https://github.com/strimzi/.github/tree/main/.github/ISSUE_TEMPLATE) and follow every section. Do NOT skip or ignore template sections.
  - You MUST apply the labels suggested by the template. Do NOT open an issue without the appropriate labels.
- **Pull request prerequisites**:
  - Do NOT open a PR for an issue labeled `needs-triage`. Wait until triage is complete and the label is removed.
  - Do NOT open a PR for an issue labeled `needs-proposal` unless the corresponding proposal has been accepted and merged in [strimzi/proposals](https://github.com/strimzi/proposals). A proposal that is open or under review is not sufficient.
  - Do NOT open a PR without first commenting on the corresponding issue to signal your intent to work on it and waiting for a maintainer to assign it to you.
  - Before starting work, check for open PRs referencing the same issue. Do NOT open a duplicate PR.
  - You MUST use the dedicated [pull request template](https://github.com/strimzi/.github/blob/main/.github/PULL_REQUEST_TEMPLATE.md).
- **AI use**: You MUST follow the [Strimzi AI Contribution Policy](https://github.com/strimzi/governance/blob/main/AI_POLICY.md).
  - Review, understand, and verify all AI-assisted changes yourself. You are responsible for the contribution and must be able to explain it to reviewers.
  - Disclose AI use in the PR description, not in commit trailers. Do NOT list AI tools in `Signed-off-by`, `Co-authored-by`, `Assisted-by`, `Co-developed-by`, or similar trailers.
  - Do NOT use AI to complete `good-start` issues, respond directly to review comments, or post automated reviews on project PRs. Keep PRs focused and do not submit many AI-generated PRs in rapid succession.

## Developer Notes

### Common Development Tasks

**Building:**
- Package the JVM application: `mvn package` or `make java_package`.
- Run Quarkus locally with live reload: `mvn compile quarkus:dev`.
- Clean Maven build artifacts: `make clean`.
- Pass extra Maven arguments to Make targets via `MVN_ARGS`, for example `MVN_ARGS=-DskipTests make java_package`.

**Testing:**
- Run Java unit tests: `mvn test`.
- Run a specific test: `mvn -Dtest=ValidatingWebhookTest#testMethodName test` (replace `testMethodName` with the actual method name).
- Run Helm chart unit tests: `make helm_unittest`.
- Run system tests: `mvn verify -Psystemtest`. The profile runs `*ST` classes instead of the regular `*Test` classes. Verify your current Kubernetes context points to a test cluster before running these tests. See [TESTING.md](development-docs/TESTING.md) for setup and image configuration.

**Container Images:**
- Build locally with `make java_package` followed by `make docker_build`. The Dockerfile consumes the JVM build in `target/quarkus-app/`.
- `make docker_push` publishes the image. Set `DOCKER_REGISTRY`, `DOCKER_ORG`, and `DOCKER_TAG` to choose the destination; use `DOCKER_ARCHITECTURE` to select an image platform.
- `make all` packages Java, regenerates installation manifests, builds the image, and **pushes it to a registry**. Use the individual local targets when you do not intend to publish.

**Installation Manifests and Helm Charts:**
- Do NOT edit the root `install/` and `helm-charts/` directories. They are updated as part of a release.
- Change deployment templates or defaults in `packaging/helm-charts/helm3/strimzi-drain-cleaner/`. Run `make helm_unittest` and then `make helm_install` to regenerate the derived YAML under `packaging/install/`. `helm_install` renders files locally; it does not deploy to a cluster.
- Review the generated manifest diffs rather than editing rendered YAML directly. Do not edit Maven build outputs in `target/`.

## Documentation

- [README.md](README.md) covers usage, deployment, and configuration.
- The [Helm chart README](packaging/helm-charts/helm3/strimzi-drain-cleaner/README.md) documents chart values and installation. Update it when changing chart options.
- See [DEVELOPING.md](development-docs/DEVELOPING.md), [TESTING.md](development-docs/TESTING.md), and [RELEASING.md](development-docs/RELEASING.md) for contributor workflows.
