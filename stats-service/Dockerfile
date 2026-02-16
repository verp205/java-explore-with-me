# Stage 1: Build
FROM maven:3.9.4-eclipse-temurin-21 AS builder
WORKDIR /app

# Скопировать все модули, чтобы Maven мог собрать проект
COPY stats-server/pom.xml ./pom.xml
COPY stats-service/pom.xml ./stats-service/pom.xml
COPY stats-service/stats-dto ./stats-service/stats-dto
COPY stats-service/stats-client ./stats-service/stats-client
COPY stats-service/stats-server ./stats-service/stats-server

# Сборка только stats-server
RUN mvn -f stats-service/pom.xml clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY --from=builder /app/stats-service/stats-server/target/app.jar ./app.jar

EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
