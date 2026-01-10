# Stage 1: Build the application
#FROM eclipse-temurin:21-jdk AS builder

#WORKDIR /app

#COPY .mvn .mvn
#COPY mvnw .
#COPY pom.xml .

# Make mvnw executable
#RUN chmod +x ./mvnw

# Pre-install dependencies for faster startup
#RUN ./mvnw -q -DskipTests dependency:go-offline

#COPY src ./src

# Package the application
#RUN ./mvnw -q -DskipTests clean package

# Stage 2: Create the runtime image (without test files)
#FROM eclipse-temurin:21-jdk

#WORKDIR /app

# Copy only the JAR file from builder stage
#COPY --from=builder /app/target/mlapp-0.0.1-SNAPSHOT.jar /app/mlapp.jar

#EXPOSE 8080

#ENTRYPOINT ["java", "-jar", "/app/mlapp.jar"]
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY target/mlapp-0.0.1-SNAPSHOT.jar /app/mlapp.jar
ENTRYPOINT ["java","-jar","/app/mlapp.jar"]

