# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY services/api/mvnw services/api/mvnw.cmd services/api/pom.xml ./
COPY services/api/.mvn ./.mvn
COPY services/api/src ./src
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN adduser -D -u 10001 appuser && \
    mkdir -p /var/payagent/evidence && \
    chown -R appuser:appuser /var/payagent
USER appuser
COPY --from=build /workspace/target/payagent-api-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
