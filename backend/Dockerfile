# Stage 1: Build the application
FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
# Download dependencies to allow caching
RUN mvn dependency:go-offline -B
# Copy source code and package
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Minimal runtime environment
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Ensure conservative JVM memory settings for 256MB container limits
# -XX:MaxRAMPercentage=75.0 allocates ~192MB to heap.
# -XX:+UseSerialGC minimizes GC memory footprint and overhead for low-resource containers.
# -Xss256k reduces the thread stack size to save memory.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseSerialGC -Xss256k"

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/photoshare-backend-0.0.1-SNAPSHOT.jar app.jar

# Expose the expected container port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
