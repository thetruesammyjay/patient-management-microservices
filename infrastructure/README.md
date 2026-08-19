# Infrastructure module

`LocalStackApp` is a Java AWS CDK application. It synthesizes a
CloudFormation template into `cdk.out/` and does not contact AWS during
synthesis.

## Static review notes

- The stack creates separate RDS instances for Auth and Patient.
- Kafka is represented by a two-broker MSK cluster across the VPC private
  subnets.
- Application images are expected to exist locally with these names:
  `patient-service`, `billing-service`, `analytics-service`, `auth-service`,
  and `api-gateway`.
- The gateway ALB health check targets `/actuator/health` on port 4004.
- The current service-to-service addresses use `host.docker.internal`; this is
  an explicit LocalStack/Docker compatibility choice and should be replaced by
  verified Cloud Map addressing if the runtime supports it reliably.
- `JWT_SECRET` is read from the environment with a development fallback. A
  real deployment must provide a secret through an external secret mechanism.
- Database and Kafka resource creation is deliberately destructive on stack
  deletion (`RemovalPolicy.DESTROY`) because this is a local development
  environment.

## Synthesis command

From this directory, once Java 21 and Maven are available:

```powershell
mvn -B compile exec:java
```

The expected output is `cdk.out/localstack.template.json`.

`localstack-deploy.ps1` uses host Maven when available. Otherwise it runs the
same synthesis command in the Java 21 Maven container, so a host Java/Maven
installation is not required when Docker is healthy.
