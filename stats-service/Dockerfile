FROM maven:3.9.4-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml ./pom.xml
COPY stats-service ./stats-service
COPY main-service ./main-service

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

COPY --from=builder /app/stats-service/stats-server/target/stats-server-0.0.1-SNAPSHOT.jar ./stats-server.jar

CMD ["java", "-jar", "stats-server.jar"]