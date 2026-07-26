# Build stage
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

# Copy maven executable and dependency definition files for caching
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Pre-fetch dependencies to speed up subsequent builds (Docker layer caching)
RUN ./mvnw dependency:go-offline -B

# Copy source code and package the application
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Run stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Create a system group and user to run the application as non-root (security hardening)
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring

# Copy built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose HTTP port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
