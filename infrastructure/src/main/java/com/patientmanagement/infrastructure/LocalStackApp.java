package com.patientmanagement.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.BootstraplessSynthesizer;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Token;
import software.amazon.awscdk.services.ec2.ISubnet;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.CloudMapNamespaceOptions;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.rds.PostgresEngineVersion;
import software.amazon.awscdk.services.rds.PostgresInstanceEngineProps;

/** Defines the local AWS-shaped environment used by the application. */
public final class LocalStackApp extends Stack {

  private final Vpc vpc;
  private final Cluster ecsCluster;

  private LocalStackApp(App scope, String id, StackProps props) {
    super(scope, id, props);
    this.vpc = createVpc();
    this.ecsCluster = createEcsCluster();

    DatabaseInstance authDb = createDatabase("AuthServiceDb", "auth-service-db");
    DatabaseInstance patientDb = createDatabase("PatientServiceDb", "patient-service-db");
    CfnCluster kafka = createKafkaCluster();

    FargateService auth = createService("auth-service", List.of(4005),
        authDb, Map.of("JWT_SECRET", requiredEnv("JWT_SECRET")));
    FargateService billing = createService("billing-service", List.of(4001, 9001),
        null, Map.of());
    FargateService analytics = createService("analytics-service", List.of(4002),
        null, Map.of());
    FargateService patient = createService("patient-service", List.of(4000), patientDb,
        Map.of("BILLING_SERVICE_ADDRESS", "host.docker.internal",
            "BILLING_SERVICE_GRPC_PORT", "9001"));

    analytics.getNode().addDependency(kafka);
    patient.getNode().addDependency(kafka);
    patient.getNode().addDependency(billing);
    auth.getNode().addDependency(authDb);
    patient.getNode().addDependency(patientDb);

    createGatewayService();
  }

  private static String requiredEnv(String name) {
    String value = System.getenv(name);
    return value == null || value.isBlank()
        ? "WW91ckRldmVsb3BtZW50U2VjcmV0S2V5MTIzNDU2Nzg5MA=="
        : value;
  }

  private Vpc createVpc() {
    return Vpc.Builder.create(this, "PatientManagementVpc")
        .vpcName("PatientManagementVpc")
        .maxAzs(2)
        .build();
  }

  private Cluster createEcsCluster() {
    return Cluster.Builder.create(this, "PatientManagementCluster")
        .vpc(vpc)
        .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
            .name("patient-management.local")
            .build())
        .build();
  }

  private DatabaseInstance createDatabase(String id, String databaseName) {
    return DatabaseInstance.Builder.create(this, id)
        .engine(DatabaseInstanceEngine.postgres(PostgresInstanceEngineProps.builder()
            .version(PostgresEngineVersion.VER_17_2)
            .build()))
        .vpc(vpc)
        .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
        .allocatedStorage(20)
        .credentials(Credentials.fromGeneratedSecret("admin_user"))
        .databaseName(databaseName)
        .removalPolicy(RemovalPolicy.DESTROY)
        .build();
  }

  private CfnCluster createKafkaCluster() {
    return CfnCluster.Builder.create(this, "PatientManagementKafka")
        .clusterName("patient-management-kafka")
        .kafkaVersion("2.8.0")
        .numberOfBrokerNodes(2)
        .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
            .instanceType("kafka.m5.large")
            .clientSubnets(vpc.getPrivateSubnets().stream()
                .map(ISubnet::getSubnetId)
                .collect(Collectors.toList()))
            .brokerAzDistribution("DEFAULT")
            .build())
        .build();
  }

  private FargateService createService(String imageName, List<Integer> ports,
      DatabaseInstance database, Map<String, String> additionalEnvironment) {
    String id = imageName.replace('-', '_');
    FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder
        .create(this, id + "Task")
        .cpu(256)
        .memoryLimitMiB(512)
        .build();

    Map<String, String> environment = new java.util.HashMap<>();
    environment.putAll(additionalEnvironment);
    if (imageName.equals("patient-service") || imageName.equals("analytics-service")) {
      environment.put("SPRING_KAFKA_BOOTSTRAP_SERVERS",
          "localhost.localstack.cloud:4510,localhost.localstack.cloud:4511");
    }
    if (database != null) {
      environment.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://%s:%s/%s-db"
          .formatted(database.getDbInstanceEndpointAddress(),
              database.getDbInstanceEndpointPort(), imageName));
      environment.put("SPRING_DATASOURCE_USERNAME", "admin_user");
      environment.put("SPRING_DATASOURCE_PASSWORD",
          database.getSecret().secretValueFromJson("password").toString());
      environment.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
      environment.put("SPRING_SQL_INIT_MODE", "always");
    }

    ContainerDefinitionOptions container = ContainerDefinitionOptions.builder()
        .image(ContainerImage.fromRegistry(imageName))
        .environment(environment)
        .portMappings(ports.stream().map(port -> PortMapping.builder()
            .containerPort(port)
            .hostPort(port)
            .protocol(Protocol.TCP)
            .build()).toList())
        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
            .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                .logGroupName("/ecs/" + imageName)
                .retention(RetentionDays.ONE_DAY)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build())
            .streamPrefix(imageName)
            .build()))
        .build();

    taskDefinition.addContainer(id + "Container", container);
    return FargateService.Builder.create(this, id + "Service")
        .cluster(ecsCluster)
        .taskDefinition(taskDefinition)
        .serviceName(imageName)
        .assignPublicIp(false)
        .build();
  }

  private void createGatewayService() {
    FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder
        .create(this, "ApiGatewayTask")
        .cpu(256)
        .memoryLimitMiB(512)
        .build();
    taskDefinition.addContainer("ApiGatewayContainer", ContainerDefinitionOptions.builder()
        .image(ContainerImage.fromRegistry("api-gateway"))
        .environment(Map.of(
            "AUTH_SERVICE_URL", "http://host.docker.internal:4005",
            "PATIENT_SERVICE_URL", "http://host.docker.internal:4000"))
        .portMappings(List.of(PortMapping.builder()
            .containerPort(4004)
            .hostPort(4004)
            .protocol(Protocol.TCP)
            .build()))
        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
            .logGroup(LogGroup.Builder.create(this, "ApiGatewayLogGroup")
                .logGroupName("/ecs/api-gateway")
                .retention(RetentionDays.ONE_DAY)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build())
            .streamPrefix("api-gateway")
            .build()))
        .build());

    ApplicationLoadBalancedFargateService gateway =
        ApplicationLoadBalancedFargateService.Builder.create(this, "ApiGatewayService")
            .cluster(ecsCluster)
            .serviceName("api-gateway")
            .taskDefinition(taskDefinition)
            .desiredCount(1)
            .healthCheckGracePeriod(Duration.seconds(60))
            .build();

    gateway.getTargetGroup().configureHealthCheck(HealthCheck.builder()
        .path("/actuator/health")
        .port("4004")
        .build());

    CfnOutput.Builder.create(this, "GatewayDnsName")
        .value(gateway.getLoadBalancer().getLoadBalancerDnsName())
        .description("Use this hostname with the gateway listener port")
        .build();
  }

  public static void main(String[] args) {
    App app = new App(AppProps.builder().outdir("./cdk.out").build());
    StackProps props = StackProps.builder()
        .synthesizer(new BootstraplessSynthesizer())
        .build();
    new LocalStackApp(app, "patient-management", props);
    app.synth();
  }
}
