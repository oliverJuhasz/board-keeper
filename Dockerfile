# syntax=docker/dockerfile:1

# Packages an application that has already been built: build the frontend and the backend
# first (see README.md). The backend cannot be built inside "docker build", because generating
# the jOOQ code needs Docker itself (ADR 0007).

# Stage 1: unpack the jar into layers. The libraries change rarely and the application's own
# code changes often; in separate image layers, an update only has to download the latter.
FROM eclipse-temurin:25-jre AS layers
WORKDIR /layers
COPY backend/app/target/board-keeper.jar board-keeper.jar
RUN java -Djarmode=tools -jar board-keeper.jar extract --layers --destination extracted

# Stage 2: the image that runs.
FROM eclipse-temurin:25-jre
RUN groupadd --system --gid 10001 boardkeeper \
    && useradd --system --uid 10001 --gid boardkeeper --no-create-home boardkeeper
WORKDIR /app
COPY --from=layers /layers/extracted/dependencies/ ./
COPY --from=layers /layers/extracted/spring-boot-loader/ ./
COPY --from=layers /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers /layers/extracted/application/ ./
# The application keeps all its data in the database and writes nothing to disk,
# so it runs as an unprivileged user.
USER boardkeeper
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "board-keeper.jar"]
