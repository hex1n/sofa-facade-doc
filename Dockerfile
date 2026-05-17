FROM maven:3.9.9-eclipse-temurin-8 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:8-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends git openssh-client \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
ENV SOFA_DOC_CONFIG=/app/config.yml
COPY --from=build /workspace/target/sofa-facade-doc-*.jar /app/sofa-facade-doc.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/sofa-facade-doc.jar"]
