FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline --no-transfer-progress
COPY src src
RUN mvn package -DskipTests --no-transfer-progress

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=builder /app/target/contextcanvas-mcp-server-*.jar mcp-server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "mcp-server.jar"]
