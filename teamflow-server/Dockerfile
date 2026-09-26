FROM public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-21@sha256:c2a2c58516d160f43b50f12baa427ca86989e0bc942609e04aff61da5d9a7d74 AS builder
WORKDIR /workspace
COPY pom.xml ./
COPY teamflow-common/pom.xml teamflow-common/pom.xml
COPY teamflow-core/pom.xml teamflow-core/pom.xml
COPY teamflow-server/pom.xml teamflow-server/pom.xml
COPY teamflow-common/src teamflow-common/src
COPY teamflow-core/src teamflow-core/src
COPY teamflow-server/src teamflow-server/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --no-transfer-progress -pl teamflow-server -am -Dmaven.test.skip=true package

FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre@sha256:49e21e16e3c86eb7816a44a67549910ed090fbeb40c29c525d58bf5e02e91b0f
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home teamflow
WORKDIR /app
COPY --from=builder /workspace/teamflow-server/target/teamflow-server-*.jar app.jar
RUN mkdir -p /app/uploads && chown -R teamflow:teamflow /app
USER teamflow
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
