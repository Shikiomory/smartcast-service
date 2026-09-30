FROM eclipse-temurin:23-jdk-alpine
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

COPY core/pom.xml core/
COPY app/pom.xml app/

RUN ./mvnw dependency:go-offline -B

COPY . .

CMD ["./mvnw", "clean", "verify"]