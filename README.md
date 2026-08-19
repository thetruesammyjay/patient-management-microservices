# Patient Management Microservices

A software engineering project that implements a patient-management platform
using Java, Spring Boot, microservices, synchronous and asynchronous service
communication, and AWS-style infrastructure running locally with LocalStack.

> **Status:** Initial project setup. Services and infrastructure will be added
> incrementally and validated at each milestone.

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

The application modules will be created in this repository as the project is
rebuilt and documented. Project notes, decisions, and verification evidence
belong in [`docs/`](docs/README.md).

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

1. Analyze and document the reference architecture.
2. Reconstruct and validate each Spring Boot service.
3. Verify REST, gRPC, Kafka, persistence, and JWT flows.
4. Containerize the services.
5. Define and deploy the LocalStack infrastructure.
6. Add integration tests and deployment evidence.

## Attribution

This project is independently maintained for educational purposes and is based
on concepts demonstrated in Chris Blakely's Java/Spring microservices course.
See [ATTRIBUTION.md](ATTRIBUTION.md) for source links and usage notes.
