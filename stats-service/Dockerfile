FROM eclipse-temurin:21-jre-jammy

WORKDIR /app


COPY stats-service/target/stats-service-*.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]