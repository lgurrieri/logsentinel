# =============================================================
# Stage 1 – Build (Maven 3.9 + JDK 25)
# =============================================================
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build

# Dependency layer cache: resolve deps before copying source
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn -T 4C -q -DskipTests clean package

# =============================================================
# Stage 2 – Runtime (JRE 25, non-root)
# =============================================================
FROM eclipse-temurin:25-jre-noble AS runtime

# Create dedicated non-root system user/group
RUN groupadd --system --gid 1001 appgroup \
 && useradd --system --uid 1001 --gid appgroup --no-create-home appuser

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /build/target/*.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
