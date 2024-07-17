FROM maven:3.9.8-eclipse-temurin-21 AS build

WORKDIR /app

# Copy the pom.xml and download the dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the source code and build the project
COPY src ./src
RUN mvn clean package

# JRE-Image für das Ausführen der Anwendung
FROM eclipse-temurin:21-jdk 
WORKDIR /app

# Copy the built JAR file from the build stage
COPY --from=build /app/target/java-backend-1.0-SNAPSHOT.jar /app/

# Port for the HTTP-Server
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app/java-backend-1.0-SNAPSHOT.jar"]