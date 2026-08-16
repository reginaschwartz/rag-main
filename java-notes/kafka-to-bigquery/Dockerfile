FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build
COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/kafka-to-bigquery-1.0.0.jar /app/pipeline.jar

ENTRYPOINT ["java", "-cp", "/app/pipeline.jar", "com.example.pipeline.EventProducerApp"]
