FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# The Gradle build produces this artifact before `docker compose up --build`.
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home credit

USER credit

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
