# # Use the official OpenJDK 17 image as a base
# FROM maven:3.8.4-openjdk-17 AS build

# # Set the working directory inside the container
# WORKDIR /app

# # Add a volume to store logs
# # VOLUME /tmp

# # Copy the JAR file to the container
# ARG JAR_FILE=target/*.jar
# COPY ${JAR_FILE} app.jar

# # Expose port 8080 (or your configured port)
# EXPOSE 8080

# # Run the Spring Boot app
# ENTRYPOINT ["java", "-jar", "app.jar"]


# Use the official OpenJDK 17 image as a base
FROM maven:3.8.4-openjdk-17 AS build

# Set the working directory inside the container
WORKDIR /app

# Copy the project files
COPY pom.xml .
COPY src ./src

# Build the application
RUN mvn package -DskipTests

# Copy the JAR file to the final stage
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Expose port 8080
EXPOSE 8080

# Run the Spring Boot app
ENTRYPOINT ["java", "-jar", "app.jar"]