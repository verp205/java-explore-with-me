FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /app

COPY . .
RUN mvn install -N -f pom.xml
RUN mvn clean package -pl server -am -DskipTests

FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app

COPY --from=build /app/server/target/server-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "app.jar"]