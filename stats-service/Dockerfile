FROM maven:3.9.4-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml ./pom.xml
COPY stats-service/pom.xml ./stats-service/pom.xml
COPY stats-service/stats-dto ./stats-service/stats-dto
COPY stats-service/stats-server ./stats-service/stats-server
COPY stats-service/stats-client ./stats-service/stats-client
COPY main-service/pom.xml ./main-service/pom.xml
COPY main-service/src ./main-service/src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY --from=builder /app/stats-service/stats-server/target/stats-server-0.0.1-SNAPSHOT.jar ./stats-server.jar
COPY --from=builder /app/main-service/target/main-service-0.0.1-SNAPSHOT.jar ./main-service.jar

CMD ["java", "-jar", "main-service.jar"]
