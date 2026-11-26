# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy the Maven wrapper and project object model
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Grant execution permissions to the wrapper
RUN chmod +x mvnw

# Copy the source code
COPY src src

# Build the jar file, skipping tests since GitHub Actions handles them
RUN ./mvnw clean package -DskipTests

# Stage 2: Create the lightweight production image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy only the compiled jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose standard Spring Boot port
EXPOSE 8080

# Execute the application
ENTRYPOINT ["java", "-jar", "app.jar"]