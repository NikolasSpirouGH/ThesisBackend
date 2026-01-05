FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

# Pre-install dependencies for faster startup
RUN ./mvnw dependency:go-offline

COPY src ./src

# Package the application
RUN ./mvnw clean package -DskipTests

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "target/mlapp-0.0.1-SNAPSHOT.jar"]
