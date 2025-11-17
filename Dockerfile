FROM eclipse-temurin:17-jre

WORKDIR /app

COPY build/libs/steam-library-analyzer-0.0.1-SNAPSHOT.jar app.jar

RUN useradd -m myapp
USER myapp

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]