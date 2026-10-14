FROM eclipse-temurin:23-jdk-alpine
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

COPY core/pom.xml core/
COPY app/pom.xml app/
COPY security/pom.xml security/
COPY db-storage/pom.xml db-storage/

RUN ./mvnw dependency:go-offline -B

COPY . .

CMD ["./mvnw", "clean", "verify"]