# Solo se usa con `docker compose --profile app up -d`.
# En desarrollo la app corre con ./mvnw fuera de Compose (§3).
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=America/Bogota
RUN useradd --system --create-home --shell /usr/sbin/nologin porteriaya
COPY --from=build /build/target/porteriaya-*.jar app.jar
USER porteriaya
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
