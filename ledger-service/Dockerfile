# Build context is the REPO ROOT:
#   docker build -f ledger-service/Dockerfile -t swiftpay/ledger-service .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY ledger-service/pom.xml ledger-service/pom.xml
COPY analytics-worker/pom.xml analytics-worker/pom.xml

RUN mvn -q -pl ledger-service -am dependency:go-offline || true

COPY ledger-service/src ledger-service/src

RUN mvn -q -pl ledger-service -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd -r -u 1000 swiftpay
COPY --from=build /workspace/ledger-service/target/ledger-service-*.jar app.jar
USER swiftpay
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
