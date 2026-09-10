package springboot.config;

import config.Dotenv;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Spring-bound server settings. */
@ConfigurationProperties(prefix = "server")
public class ServerProperties {
    private int port = 8080;
    private int authRequestsPerMinute = 20;
    private CorsProperties cors = new CorsProperties();
    private CookieProperties cookie = new CookieProperties();

    static ServerProperties fromEnvironmentDefaults() {
        var dotenv = Dotenv.loadDefault();
        var properties = new ServerProperties();
        var port = EnvironmentPropertyDefaults.value(dotenv, "server.port", "SERVER_PORT");
        if (port != null) {
            try {
                properties.port = Integer.parseInt(port);
            } catch (NumberFormatException ignored) {
                properties.port = 8080;
            }
        }
        var corsOrigin = EnvironmentPropertyDefaults.value(dotenv, "server.cors.origin", "SERVER_CORS_ORIGIN");
        if (corsOrigin != null) {
            properties.cors.origin = corsOrigin;
        }
        var cookieSecure = EnvironmentPropertyDefaults.value(dotenv, "server.cookie.secure", "SERVER_COOKIE_SECURE");
        if (cookieSecure != null) {
            properties.cookie.secure = Boolean.parseBoolean(cookieSecure);
        }
        var authRateLimit = EnvironmentPropertyDefaults.value(dotenv, "server.auth.requestsPerMinute", "AUTH_RATE_LIMIT");
        if (authRateLimit != null) {
            try {
                properties.authRequestsPerMinute = Integer.parseInt(authRateLimit);
            } catch (NumberFormatException ignored) {
                properties.authRequestsPerMinute = 20;
            }
        }
        return properties;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getAuthRequestsPerMinute() {
        return Math.max(1, authRequestsPerMinute);
    }

    public void setAuthRequestsPerMinute(int authRequestsPerMinute) {
        this.authRequestsPerMinute = authRequestsPerMinute;
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
