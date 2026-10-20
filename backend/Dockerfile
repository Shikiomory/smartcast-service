FROM eclipse-temurin:23-jdk-alpine

RUN apk add --no-cache bash dos2unix

WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN dos2unix mvnw && chmod +x mvnw

COPY core/pom.xml core/
COPY app/pom.xml app/
COPY security/pom.xml security/
COPY db-storage/pom.xml db-storage/

RUN bash mvnw dependency:go-offline -B

COPY . .

CMD ["./mvnw", "clean", "verify"]