# Reference Repository Analysis

## Purpose and scope

This document records a static analysis of the locally cloned reference
repository `java-spring-microservices/`. The clone is excluded from this
project's Git history and was not modified during the analysis.

The analysis establishes what the reference implementation does, what it needs
at runtime, and which assumptions should be verified or corrected while the
independent project is reconstructed. It is not evidence that the reference
applications currently compile or deploy successfully; build validation is a
separate milestone.

## Executive summary

The reference system contains five Spring Boot applications, one external
integration-test module, and one Java AWS CDK infrastructure module. The
patient service is the core domain service. It stores patients in PostgreSQL,
calls the billing service synchronously over gRPC when a patient is created,
and publishes a Protobuf event to Kafka. The analytics service consumes that
event. Clients enter through a Spring Cloud Gateway, which delegates bearer
token validation to the authentication service.

The architecture is suitable for demonstrating common microservice patterns,
but the reference implementation is a course-sized prototype rather than a
production-ready system. Important issues must be resolved before deployment:

1. The workstation currently exposes Java 8, but every module targets Java 21.
2. Maven and the AWS CLI are not currently available on `PATH`.
3. The analytics container exposes port 4002, while its application has no
   `server.port` setting and therefore normally listens on port 8080.
4. The production gateway configuration contains `http:/host...` instead of
   `http://host...` for the Auth OpenAPI route.
5. The gateway cannot start locally unless `AUTH_SERVICE_URL` is supplied.
6. The LocalStack infrastructure relies on host networking assumptions rather
   than the Cloud Map namespace it creates.
7. The load balancer uses default health-check behavior, but the gateway has no
   health endpoint or route at `/`.
8. The integration assertion for the patient list expects a `patients` field,
   while the controller returns a bare JSON array.
9. The deployment script deletes and immediately redeploys the stack without
   waiting for deletion to finish.
10. Database writes, gRPC calls, and Kafka publication do not form a reliable
    transaction; partial patient creation is possible.

These issues should be addressed deliberately during reconstruction, with a
test or documented verification attached to each correction.

## Repository inventory

| Module | Type | Responsibility | Primary interfaces |
| --- | --- | --- | --- |
| `patient-service` | Spring Boot MVC | Patient CRUD and workflow orchestration | REST 4000, outbound gRPC, Kafka producer, PostgreSQL |
| `billing-service` | Spring Boot/gRPC | Creates a placeholder billing account | HTTP 4001, gRPC 9001 |
| `analytics-service` | Spring Boot/Kafka | Consumes and logs patient events | Kafka consumer; intended HTTP 4002 |
| `auth-service` | Spring Boot MVC/Security | Authenticates seeded users and signs/validates JWTs | REST 4005, PostgreSQL |
| `api-gateway` | Spring Cloud Gateway | Public routing and token-validation filter | HTTP 4004 |
| `integration-tests` | Maven/JUnit/REST Assured | Gateway-level authentication and patient checks | Calls `localhost:4004` |
| `infrastructure` | Java AWS CDK | Synthesizes the LocalStack CloudFormation stack | VPC, RDS, MSK, ECS, ALB, logs, DNS health checks |
| `api-requests` | HTTP client examples | Manual REST requests | Gateway and direct-service URLs |
| `grpc-requests` | HTTP client example | Manual billing RPC | `localhost:9001` |

There is no root Maven aggregator. Every Maven module is independent and must
be built separately unless the reconstructed project adds an explicit parent
or aggregator.

## System context and communication

```text
Client
  |
  v
Application Load Balancer
  |
  v
Spring Cloud Gateway :4004
  |                         |
  | /auth/**                | /api/patients/** + JwtValidation
  v                         v
Auth Service :4005          Patient Service :4000
  |                           |       |        |
  v                           |       |        +--> Kafka topic "patient"
PostgreSQL                    |       |                 |
                              |       v                 v
                              |   PostgreSQL     Analytics Service
                              |
                              +--> Billing Service gRPC :9001
                                   (HTTP application port :4001)
```

The directory named `api-gateway` is a Spring Cloud Gateway application. It is
not the managed AWS API Gateway service. AWS-style ingress is provided by an
Application Load Balancer created around the gateway's ECS/Fargate task.

## Ports

| Component | Configured application port | Other port | Observation |
| --- | ---: | ---: | --- |
| Patient | 4000 | - | Matches Dockerfile and CDK mapping |
| Billing | 4001 | gRPC 9001 | Both ports are exposed and mapped |
| Analytics | **8080 by default** | intended 4002 | Dockerfile/CDK use 4002, but application properties do not |
| API gateway | 4004 | - | Matches Dockerfile and CDK mapping |
| Authentication | 4005 | - | Matches Dockerfile and CDK mapping |
| LocalStack edge | 4566 | service ports 4510-4559 | Compose configuration is not present in the reference clone |
| PostgreSQL | dynamically exposed by LocalStack | - | Endpoint and port come from RDS tokens |

The analytics mismatch is a deployment blocker unless `SERVER_PORT=4002` is
injected or `server.port=4002` is added.

## HTTP API and gateway routing

### Authentication API

| External gateway request | Internal request after filter | Protection | Result |
| --- | --- | --- | --- |
| `POST /auth/login` | `POST /login` | Public | Returns `{ "token": "..." }` or 401 |
| `GET /auth/validate` | `GET /validate` | Public by design | Returns 200 or 401 from bearer-token validation |

The Auth controller does not use `@Valid` on `LoginRequestDTO`; consequently,
the DTO's email, blank, and password-length constraints are not enforced by
Spring MVC at this boundary.

### Patient API

The gateway matches `/api/patients/**`, removes the first path segment (`api`),
and forwards the resulting `/patients/**` request to the patient service.

| Method | Gateway path | Patient path | Success response |
| --- | --- | --- | --- |
| GET | `/api/patients` | `/patients` | 200 with a bare JSON array |
| POST | `/api/patients` | `/patients` | 200 with the created patient |
| PUT | `/api/patients/{id}` | `/patients/{id}` | 200 with the updated patient |
| DELETE | `/api/patients/{id}` | `/patients/{id}` | 204 |

All patient gateway requests require an `Authorization: Bearer <token>`
header. Direct calls to port 4000 bypass the gateway and therefore bypass its
JWT filter.

Notable API semantics:

- Duplicate email and missing-patient exceptions both produce HTTP 400;
  missing resources would conventionally produce 404.
- Date fields are strings in request/response DTOs and are parsed with
  `LocalDate.parse`, so malformed dates may escape the documented validation
  response unless explicitly handled.
- `registeredDate` is required only during creation and is not updated by PUT.
- DELETE delegates directly to the repository and has no explicit not-found
  response contract.
- OpenAPI JSON is routed publicly through `/api-docs/patients` and
  `/api-docs/auth`.
- In `application-prod.yml`, the Auth OpenAPI target contains a malformed URL:
  `http:/host.docker.internal:4005`.

## Authentication and JWT flow

1. `POST /auth/login` supplies an email and password.
2. `UserService` finds the user by email in the Auth PostgreSQL database.
3. BCrypt verifies the password hash.
4. `JwtUtil` creates an HMAC-signed token containing the email as subject, a
   `role` claim, issue time, and a ten-hour expiration.
5. A protected request reaches the gateway's `JwtValidation` filter.
6. The filter checks for the bearer header and calls the Auth service's
   `GET /validate` endpoint.
7. Auth verifies signature and standard signed claims. On success, the gateway
   forwards the original request to Patient.

Runtime requirements:

- Auth requires `JWT_SECRET`, which Spring maps to `jwt.secret`.
- The supplied CDK code embeds a Base64-encoded JWT secret directly into the
  synthesized task environment.
- Gateway requires `AUTH_SERVICE_URL`, mapped to `auth.service.url`.

Security limitations:

- The JWT secret is hard-coded in infrastructure source and CloudFormation.
- Roles are issued but never authorized; possession of any valid token is
  sufficient for every patient operation.
- Authentication endpoints are configured with `permitAll`, relying on
  controller logic rather than a security filter for `/validate`.
- Gateway-to-Auth validation is a network call on every protected request; no
  timeout, retry, circuit breaker, or local JWT verification is configured.
- The gateway does not propagate verified identity/role headers to downstream
  services, and the patient service independently trusts direct callers.
- The seeded test account is appropriate only for local/testing use.

## Patient persistence and workflow

The `Patient` JPA entity contains a generated UUID, name, unique email, address,
date of birth, and registration date. PostgreSQL is the intended deployment
database. H2 is also present as a dependency for local/test startup.

`data.sql` creates the patient table and idempotently inserts 15 sample rows.
CDK injects:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=update`
- `SPRING_SQL_INIT_MODE=always`
- `SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT=60000`

Patient creation currently executes in this order:

```text
validate unique email
  -> save Patient
  -> blocking Billing gRPC call
  -> submit Kafka send
  -> return response
```

There is no `@Transactional` boundary, outbox, compensation, idempotency key,
or retry policy. If billing fails after the database save, the API can fail
while leaving the patient stored. If Kafka delivery later fails, the database
and billing state can exist without an analytics event.

## gRPC communication

Both Patient and Billing contain a copy of `billing_service.proto`:

```text
BillingService.CreateBillingAccount(BillingRequest) -> BillingResponse
```

The request carries patient ID, name, and email. The response carries an
account ID and status. Patient uses a blocking plaintext channel whose address
and port are configured by:

- `BILLING_SERVICE_ADDRESS` (default `localhost`)
- `BILLING_SERVICE_GRPC_PORT` (default `9001`)

Billing listens on 9001 but implements only a stub: it logs the request and
always returns account ID `12345` with status `ACTIVE`. It has no billing
database or domain model.

Risks and reconstruction requirements:

- The duplicated `.proto` files can drift. A shared contract module or
  contract-versioning check should be considered.
- No deadline, retry policy, error mapping, channel shutdown, TLS, or service
  authentication is configured.
- The CDK address `host.docker.internal` depends on LocalStack's container and
  host-network behavior rather than service discovery.

## Kafka communication

Patient and Analytics contain matching `patient_event.proto` definitions. A
Patient creation produces a byte-array message to topic `patient` with:

- patient ID
- name
- email
- event type `PATIENT_CREATED`

Analytics consumes the byte array as consumer group `analytics-service`,
deserializes it as Protobuf, and logs the fields. It does not persist metrics or
expose an analytics API.

Producer configuration uses `StringSerializer` for keys and
`ByteArraySerializer` for values. Consumer configuration uses the matching
deserializers. CDK injects a three-address LocalStack Kafka bootstrap string
into every service, including services that do not use Kafka.

Reliability limitations:

- `KafkaTemplate.send` is asynchronous, but the producer only catches errors
  thrown immediately. Broker delivery failures can complete later and go
  unobserved.
- No event key is supplied, so per-patient partition ordering is not defined.
- No topic creation policy, retry/dead-letter handling, idempotent consumption,
  schema registry, or contract compatibility check is present.
- The event contains patient email, which should be treated as personal data in
  logs and event retention policies.

## Docker images

Each application has a two-stage Dockerfile:

1. Maven 3.9.9 with Eclipse Temurin 21 resolves dependencies and runs
   `mvn clean package`.
2. An OpenJDK 21 image runs the generated executable JAR.

Expected image names are:

- `patient-service`
- `billing-service`
- `analytics-service`
- `auth-service`
- `api-gateway`

The CDK stack references these short registry names directly. Local image
availability and LocalStack's ECS image-resolution behavior must therefore be
verified before deployment.

Container hardening and operability are minimal: images run as root, use a full
JDK rather than a smaller runtime, have no container health checks, pin no image
digest, and include no JVM/container resource tuning. These are improvement
opportunities, not prerequisites for the first functional build.

## LocalStack/CDK infrastructure

The Java CDK stack synthesizes `cdk.out/localstack.template.json` using a
`BootstraplessSynthesizer` and defines:

- a two-Availability-Zone VPC;
- separate PostgreSQL RDS instances/databases for Auth and Patient;
- Route 53 TCP health-check resources for both database endpoints;
- an MSK cluster;
- an ECS cluster with the namespace `patient-management.local`;
- Fargate services for Auth, Billing, Analytics, and Patient;
- an application-load-balanced Fargate service for the gateway;
- one-day CloudWatch log groups for every application.

### Current LocalStack considerations

As checked on 2026-08-19, the official LocalStack plan table lists ECS, RDS,
ELBv2, and Cloud Map outside the Hobby tier, while the Student tier includes
them. The plan table describes service availability, not complete API parity.
See the [LocalStack plan matrix](https://docs.localstack.cloud/aws/licensing/).

LocalStack documents native PostgreSQL support for major versions 13 through
17, but states that minor-version selection is not available. The CDK request
for PostgreSQL `17.2` should therefore be treated as PostgreSQL 17 behavior in
LocalStack. See the [LocalStack RDS documentation](https://docs.localstack.cloud/aws/services/rds/).

LocalStack supports creating local Kafka clusters through MSK APIs; the
repository's exact one-broker/two-subnet CDK shape still needs a deployment
test. See the [LocalStack MSK documentation](https://docs.localstack.cloud/aws/services/kafka/).

LocalStack's CDK documentation warns that stack updates can produce inconsistent
state and recommends delete/redeploy when necessary. That agrees with the
reference script's intent, but the script must wait for deletion to finish.
See the [LocalStack CDK documentation](https://docs.localstack.cloud/aws/connecting/infrastructure-as-code/aws-cdk/).

### Infrastructure risks

| Severity | Finding | Likely effect |
| --- | --- | --- |
| Blocker | Analytics maps port 4002 while the app normally listens on 8080 | Task/traffic cannot reach Analytics on 4002 |
| Blocker | Gateway's default ALB health check likely targets `/`, with no healthy route there | Gateway target may remain unhealthy |
| High | Service calls use `host.docker.internal` instead of task discovery | Behavior depends on LocalStack/Docker port forwarding |
| High | Cloud Map namespace is created but no services explicitly opt into Cloud Map registration | Namespace does not solve service addressing |
| High | Deployment script does not wait after `delete-stack` | Redeploy can race with stack deletion |
| High | No LocalStack Compose file or pinned LocalStack image exists | Environment is not reproducible |
| High | JWT secret is embedded in source/template | Secret is exposed and rotation is manual |
| Medium | MSK cluster name is misspelled `kafa-cluster` | Confusing resource naming |
| Medium | One broker is declared across private subnets from two AZs | May conflict with MSK topology validation/emulation |
| Medium | Route 53 health-check dependencies order creation only | They do not guarantee database readiness before app startup |
| Medium | All services receive Kafka configuration | Unnecessary coupling and confusing runtime configuration |
| Medium | No explicit CloudFormation IAM capability in deployment command | Generated IAM resources may make deployment fail |
| Medium | No explicit ALB listener/output contract is documented | Example requests may use the wrong port or stale DNS name |
| Medium | Docker images use unqualified local names | Runtime may try an unintended registry/pull path |

The reference HTTP request files contain a previously generated load-balancer
hostname and sometimes use `:4004`. The actual ALB endpoint and listener port
must always be discovered from the deployed stack rather than committed as a
fixed address.

## Runtime configuration matrix

| Variable | Consumer | Required | Reference value/source |
| --- | --- | --- | --- |
| `SPRING_DATASOURCE_URL` | Auth, Patient | Deployment | Generated from each RDS endpoint |
| `SPRING_DATASOURCE_USERNAME` | Auth, Patient | Deployment | `admin_user` |
| `SPRING_DATASOURCE_PASSWORD` | Auth, Patient | Deployment | CDK-generated Secrets Manager value |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Auth, Patient | Deployment | `update` |
| `SPRING_SQL_INIT_MODE` | Auth, Patient | Deployment | `always` |
| `SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT` | Auth, Patient | Deployment | `60000` |
| `JWT_SECRET` | Auth | Yes | Hard-coded Base64 value in reference CDK |
| `AUTH_SERVICE_URL` | Gateway | Yes | `http://host.docker.internal:4005` in CDK |
| `BILLING_SERVICE_ADDRESS` | Patient | Optional | Defaults to `localhost`; CDK uses host bridge |
| `BILLING_SERVICE_GRPC_PORT` | Patient | Optional | Defaults/CDK use `9001` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Patient, Analytics | Functional requirement | LocalStack broker ports 4510-4512 |
| `SPRING_PROFILES_ACTIVE` | Gateway | Deployment | `prod` |
| `SERVER_PORT` | Analytics | Needed with current source | Should be 4002 unless properties are corrected |

No real secret values should be added to `.env.example` or committed files.

## Tests and verification coverage

### Module tests

Patient, Billing, and Analytics each have only a Spring `contextLoads` test.
Auth has no test source in the clone. The gateway has no test source. There are
no repository, controller, gRPC contract, Kafka producer/consumer, JWT, or
gateway filter tests.

Context tests may require runtime properties even though they do not exercise
behavior. In particular, Auth requires `jwt.secret`. Build validation must
distinguish a genuine code failure from missing test-profile configuration.

### External integration tests

`AuthIntegrationTest` checks successful login and invalid credentials through
`http://localhost:4004`. It prints a generated JWT, which should be removed or
redacted because test logs can be retained.

`PatientIntegrationTest` logs in and calls `GET /api/patients`, but asserts
`body("patients", notNullValue())`. The controller returns a bare list, so the
assertion should validate the root collection instead.

Missing end-to-end coverage includes:

- missing, malformed, expired, and incorrectly signed tokens;
- patient create, update, delete, duplicate email, validation, and not-found;
- verification that Patient called Billing;
- verification that Analytics consumed the Kafka event;
- database state and failure recovery;
- gateway routing/OpenAPI routes;
- LocalStack stack resources and ALB health.

## Local workstation readiness

The following read-only checks were run on 2026-08-19:

| Tool | Observed state | Required action before build milestone |
| --- | --- | --- |
| Java | OpenJDK 8 at `C:\Program Files\Java\java-1.8.0-openjdk` | Install/select JDK 21 and update `JAVA_HOME`/`PATH` |
| Maven | `mvn` not found | Install Maven or use module wrappers where present |
| Docker | Docker 29.1.5 | Verify daemon access separately |
| Docker Compose | v5.0.1 | Suitable for Compose workflow |
| AWS CLI | `aws` not found | Install AWS CLI v2 before LocalStack deployment |
| Git | 2.47.0.windows.2 | Available |

Maven wrapper scripts exist for the five Spring application modules. The
Infrastructure and Integration Tests modules do not include wrappers, so a
system Maven installation or a new root wrapper will be necessary.

## Recommended reconstruction sequence

1. Establish a Java 21 toolchain and a reproducible root Maven strategy.
2. Create a root Maven aggregator and shared configuration without changing
   service behavior.
3. Reconstruct contracts and the five services module by module.
4. Add test profiles so every module can build independently.
5. Correct the known gateway and analytics configuration defects.
6. Add unit/contract tests for JWT, gateway filtering, patient behavior, gRPC,
   and Kafka serialization.
7. Build and tag all five images with one fail-fast script.
8. Add a pinned LocalStack Compose environment and verify licensed services.
9. Rework service discovery, health checks, secrets, and deployment waiting in
   the CDK/deployment workflow.
10. Add bottom-up infrastructure checks followed by API-level integration
    tests and documented evidence.

## Milestone acceptance criteria

This analysis milestone is complete when:

- the reference clone remains ignored and unchanged;
- architecture, ports, routes, persistence, JWT, gRPC, Kafka, Docker, CDK,
  environment variables, and test coverage are documented;
- known defects and LocalStack risks are explicitly recorded;
- no claim is made that compilation or deployment has been validated; and
- the next milestone begins with the Java 21/build-tool prerequisite rather
  than silently copying the reference source.

