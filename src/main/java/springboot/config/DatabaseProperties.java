package springboot.config;

import config.Dotenv;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Spring-bound view of the existing database settings. */
@ConfigurationProperties(prefix = "database")
public class DatabaseProperties {
    private String url;
    private String user;
    private String password;
    private PoolProperties pool = new PoolProperties();

    static DatabaseProperties fromLegacyDefaults() {
        var dotenv = Dotenv.loadDefault();
        var properties = new DatabaseProperties();
        properties.url = LegacyPropertyDefaults.legacyValue(dotenv, "database.url", "DATABASE_URL");
        properties.user = LegacyPropertyDefaults.legacyValue(dotenv, "database.user", "DATABASE_USER");
        properties.password = LegacyPropertyDefaults.legacyValue(dotenv, "database.password", "DATABASE_PASSWORD");
        var poolSize = LegacyPropertyDefaults.legacyValue(dotenv, "database.pool.size", "DATABASE_POOL_SIZE");
        if (poolSize != null) {
            try {
                properties.pool.size = Integer.parseInt(poolSize);
            } catch (NumberFormatException ignored) {
                properties.pool.size = 4;
            }
        }
        return properties;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public PoolProperties getPool() {
        return pool;
    }

    public void setPool(PoolProperties pool) {
        this.pool = pool;
    }

    public int getPoolSize() {
        return pool == null ? 4 : Math.max(1, pool.getSize());
    }

    public static class PoolProperties {
        private int size = 4;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }
}
