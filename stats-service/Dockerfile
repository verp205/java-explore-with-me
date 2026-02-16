FROM maven:3.9.12-eclipse-temurin-21 AS build

WORKDIR /app

COPY . .

RUN mvn install -N

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app

ARG SERVICE
COPY --from=build /app/${SERVICE}/target/${SERVICE}-0.0.1-SNAPSHOT.jar ./${SERVICE}.jar

EXPOSE 9090

CMD ["java", "-jar", "${SERVICE}.jar"]