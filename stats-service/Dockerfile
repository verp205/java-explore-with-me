FROM maven:3.9.4-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml ./pom.xml
COPY stats-service ./stats-service

RUN mvn -f stats-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

COPY --from=builder /app/stats-service/target/stats-service-0.0.1-SNAPSHOT.jar ./stats-server.jar

EXPOSE 9090
CMD ["java", "-jar", "stats-server.jar"]