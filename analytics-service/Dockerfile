FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/analytics-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 4002
ENTRYPOINT ["java", "-jar", "app.jar"]
