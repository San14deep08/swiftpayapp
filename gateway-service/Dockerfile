# Build context is the REPO ROOT (needed for the multi-module Maven reactor):
#   docker build -f gateway-service/Dockerfile -t swiftpay/gateway-service .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY ledger-service/pom.xml ledger-service/pom.xml
COPY analytics-worker/pom.xml analytics-worker/pom.xml

# Pre-fetch dependencies so this layer caches across source-only changes.
RUN mvn -q -pl gateway-service -am dependency:go-offline || true

COPY gateway-service/src gateway-service/src

RUN mvn -q -pl gateway-service -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd -r -u 1000 swiftpay
COPY --from=build /workspace/gateway-service/target/gateway-service-*.jar app.jar
USER swiftpay
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
