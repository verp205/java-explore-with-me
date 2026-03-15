FROM eclipse-temurin:21-jre-jammy
VOLUME /tmp
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
COPY target/main-service-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app.jar"]