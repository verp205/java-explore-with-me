FROM maven:3.9.5-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY stats-dto/ stats-dto/
COPY stats-client/ stats-client/
COPY stats-service/ stats-service/

RUN mvn clean package -DskipTests -pl stats-client -am

FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app