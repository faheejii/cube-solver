package springboot.config;

import config.Dotenv;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Spring-bound view of the existing server compatibility settings. */
@ConfigurationProperties(prefix = "server")
public class ServerProperties {
    private int port = 8080;
    private CorsProperties cors = new CorsProperties();
    private CookieProperties cookie = new CookieProperties();

    static ServerProperties fromLegacyDefaults() {
        var dotenv = Dotenv.loadDefault();
        var properties = new ServerProperties();
        var port = LegacyPropertyDefaults.legacyValue(dotenv, "server.port", "SERVER_PORT");
        if (port != null) {
            try {
                properties.port = Integer.parseInt(port);
            } catch (NumberFormatException ignored) {
                properties.port = 8080;
            }
        }
        var corsOrigin = LegacyPropertyDefaults.legacyValue(dotenv, "server.cors.origin", "SERVER_CORS_ORIGIN");
        if (corsOrigin != null) {
            properties.cors.origin = corsOrigin;
        }
        var cookieSecure = LegacyPropertyDefaults.legacyValue(dotenv, "server.cookie.secure", "SERVER_COOKIE_SECURE");
        if (cookieSecure != null) {
            properties.cookie.secure = Boolean.parseBoolean(cookieSecure);
        }
        return properties;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public CorsProperties getCors() {
        return cors;
    }

    public void setCors(CorsProperties cors) {
        this.cors = cors;
    }

    public CookieProperties getCookie() {
        return cookie;
    }

    public void setCookie(CookieProperties cookie) {
        this.cookie = cookie;
    }

    public static class CorsProperties {
        private String origin = "http://localhost:5173";

        public String getOrigin() {
            return origin;
        }

        public void setOrigin(String origin) {
            this.origin = origin;
        }
    }

    public static class CookieProperties {
        private boolean secure = true;

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }
    }
}
