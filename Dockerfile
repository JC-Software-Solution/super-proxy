# Build stage
FROM maven:3.9.11-eclipse-temurin-21 AS builder

WORKDIR /usr/src/app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /usr/app

COPY --from=builder /usr/src/app/target/superproxy-0.0.1.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]