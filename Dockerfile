FROM maven:3.9.13-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package \
    && cp target/task-management-api-*.jar target/app.jar

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build --chown=10001:10001 /workspace/target/app.jar /app/app.jar

USER 10001:10001

EXPOSE 10000

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
