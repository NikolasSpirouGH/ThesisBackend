FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

# Προεγκατάσταση εξαρτήσεων για πιο γρήγορο startup
RUN ./mvnw dependency:go-offline

# Εκθέτουμε τα ports
EXPOSE 8080 5005

# CMD για dev: spring-boot:run
CMD ["./mvnw", "spring-boot:run"]
