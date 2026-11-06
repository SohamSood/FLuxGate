# Stage 1: Build the application with Maven
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Cache Maven dependencies layer
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Build the executable Spring Boot archive
COPY src/ ./src/
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Lightweight runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-privileged application user
RUN addgroup -S fluxgate && adduser -S fluxgate -G fluxgate
USER fluxgate:fluxgate

# Copy the built JAR from builder stage
COPY --from=builder /app/target/fluxgate-*.jar app.jar

# Port is dynamically assigned by Render (defaults to 8080)
ENV PORT=8080
EXPOSE ${PORT}

# JVM container options
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
