FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -U -Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 dependency:go-offline
COPY src ./src
RUN mvn -q -U -Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
COPY knowledge /repo/knowledge
COPY src /repo/src
COPY scripts /repo/scripts
COPY agent /repo/agent
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
