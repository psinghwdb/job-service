package com.example.jobserver.config;

import lombok.Builder;
import lombok.Getter;

/**
 * Application configuration - loaded from environment variables with defaults.
 */
@Getter
@Builder
public class AppConfig {

    private final String dbHost;
    private final int dbPort;
    private final String dbUser;
    private final String dbPassword;
    private final String dbName;
    private final int dbPoolSize;
    private final int httpPort;
    private final int workerInstances;
    private final String externalApiUrl;

    /**
     * Load configuration from environment variables with sensible defaults.
     */
    public static AppConfig fromEnvironment() {
        return AppConfig.builder()
            .dbHost(getEnv("DB_HOST", "localhost"))
            .dbPort(getEnvInt("DB_PORT", 3307))
            .dbUser(getEnv("DB_USER", "root"))
            .dbPassword(getEnv("DB_PASS", "root"))
            .dbName(getEnv("DB_NAME", "jobs"))
            .dbPoolSize(getEnvInt("DB_POOL_SIZE", 10))
            .httpPort(getEnvInt("HTTP_PORT", 8067))
            .workerInstances(getEnvInt("WORKER_INSTANCES", 4))
            .externalApiUrl(getEnv("EXTERNAL_API_URL", "http://localhost:8081/"))
            .build();
    }

    public String getJdbcUrl() {
        return String.format(
            "jdbc:mysql://%s:%d/%s?allowPublicKeyRetrieval=true&useSSL=false",
            dbHost, dbPort, dbName
        );
    }

    private static String getEnv(String key, String defaultValue) {
        return System.getenv().getOrDefault(key, defaultValue);
    }

    private static int getEnvInt(String key, int defaultValue) {
        String value = System.getenv().get(key);
        if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }
}

