FROM maven:3.9.5-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY stats-dto/ stats-dto/
COPY stats-client/ stats-client/
COPY stats-service/ stats-service/
COPY main-service/ main-service/

RUN mvn clean package -DskipTests -pl main-service -am

FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

COPY --from=build /app/main-service/target/*.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]
