FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Dependencies are resolved from the pom alone so they stay cached across source changes.
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /build/target/rag-api-*.jar /app/rag-api.jar

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "/app/rag-api.jar"]
