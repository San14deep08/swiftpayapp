# Build context is the REPO ROOT:
#   docker build -f analytics-worker/Dockerfile -t swiftpay/analytics-worker .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY ledger-service/pom.xml ledger-service/pom.xml
COPY analytics-worker/pom.xml analytics-worker/pom.xml

RUN mvn -q -pl analytics-worker -am dependency:go-offline || true

COPY analytics-worker/src analytics-worker/src

RUN mvn -q -pl analytics-worker -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd -r -u 1000 swiftpay
COPY --from=build /workspace/analytics-worker/target/analytics-worker-*.jar app.jar
USER swiftpay
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
