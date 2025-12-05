# ============================
# Stage 1: Build with Maven
# ============================
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml first for better caching
COPY pom.xml ./

# Download dependencies (skip jOOQ codegen as it needs DB)
RUN mvn -q dependency:go-offline -Djooq.skip=true

# Copy the source (jOOQ classes are pre-generated)
COPY src ./src

# Build fat JAR (skip tests and jOOQ generation)
RUN mvn -q clean package -DskipTests -Djooq.skip=true


# ============================
# Stage 2: Runtime image
# ============================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy fat JAR from builder stage (shade plugin creates uber-jar without -fat suffix)
COPY --from=builder /app/target/job-server-*.jar app.jar

# Expose HTTP port (matches AppConfig default)
EXPOSE 8067

# Run Vert.x application
ENTRYPOINT ["java", "-jar", "app.jar", "run", "com.example.jobserver.MainVerticle"]
