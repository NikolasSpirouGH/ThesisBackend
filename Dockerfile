FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

# Προεγκατάσταση εξαρτήσεων για πιο γρήγορο startup
RUN ./mvnw dependency:go-offline

# Create target directory and set permissions for non-root users
RUN mkdir -p /app/target && \
    chmod -R 777 /app

# Εκθέτουμε τα ports
EXPOSE 8080 5005

# CMD για dev: spring-boot:run
CMD ["./mvnw", "spring-boot:run"]
