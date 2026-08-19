# Patient Management Microservices

A software engineering project that implements a patient-management platform
using Java, Spring Boot, microservices, synchronous and asynchronous service
communication, and AWS-style infrastructure running locally with LocalStack.

> **Status:** Application modules reconstructed. Runtime build and LocalStack
> deployment validation are pending toolchain availability.

## Planned architecture

- **API gateway:** Spring Cloud Gateway
- **Authentication:** JWT-based authentication and authorization
- **Services:** Patient, Billing, Analytics, and Authentication
- **Synchronous communication:** gRPC between Patient and Billing
- **Asynchronous communication:** Kafka events from Patient to Analytics
- **Persistence:** PostgreSQL with a database-per-service approach
- **Infrastructure:** Docker, AWS CDK, CloudFormation, ECS/Fargate, RDS, MSK,
  Application Load Balancer, and CloudWatch through LocalStack

## Repository layout

```text
patient-service/     Patient REST API, PostgreSQL, gRPC client, Kafka producer
billing-service/     Billing gRPC endpoint
analytics-service/   Kafka event consumer
auth-service/        JWT login and validation
api-gateway/         Spring Cloud Gateway and JWT filter
docs/                Architecture notes and verification evidence
```

Project notes, decisions, and verification evidence belong in
[`docs/`](docs/README.md).

The upstream tutorial repository is cloned locally into
`java-spring-microservices/` as a reference implementation. That directory is
intentionally excluded from version control and will not be part of this
repository.

## Prerequisites

- Java 21
- Maven
- Docker Desktop with Docker Compose
- Git
- AWS CLI
- A LocalStack account or plan that supports the required AWS services

## Local configuration

Create a local environment file from the committed template:

```powershell
Copy-Item .env.example .env
```

Replace placeholder values in `.env` as needed. The `.env` file and LocalStack
state are ignored by Git.

## Development approach

Work is organized into small, verifiable milestones:

1. Analyze and document the reference architecture. **Complete.**
2. Reconstruct the Spring Boot services. **In progress.**
3. Validate REST, gRPC, Kafka, persistence, and JWT flows.
4. Containerize the services.
5. Define and deploy the LocalStack infrastructure.
6. Add integration tests and deployment evidence.

The LocalStack deployment workflow is:

```powershell
.\scripts\build-images.ps1
.\infrastructure\localstack-deploy.ps1
```

The deployment script expects LocalStack at `http://localhost:4566` and the
five application images to be tagged with `:latest`. It uses `lstk aws` for
LocalStack AWS API calls; the AWS CLI is not required for this workflow.

## Attribution

This project is independently maintained for educational purposes and is based
on concepts demonstrated in Chris Blakely's Java/Spring microservices course.
See [ATTRIBUTION.md](ATTRIBUTION.md) for source links and usage notes.
