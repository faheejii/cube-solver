package server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Keeps the legacy CORS/origin policy and request metrics at the servlet boundary. */
final class SpringRequestPolicyFilter extends OncePerRequestFilter {
    private final OperationalMetrics metrics;

    SpringRequestPolicyFilter(OperationalMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var headers = response;
        headers.setHeader("Access-Control-Allow-Origin", SpringRequestSupport.configuredCorsOrigin());
        headers.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        headers.setHeader("Access-Control-Allow-Headers", "Content-Type");
        headers.setHeader("Access-Control-Allow-Credentials", "true");
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        if (!SpringRequestSupport.isTrustedMutation(request)) {
            write(SpringRequestSupport.error(403, "Untrusted request origin"), response);
            return;
        }

        metrics.requestStarted();
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            metrics.requestFinished(response.getStatus(), System.nanoTime() - startedAt);
        }
    }

    private static void write(org.springframework.http.ResponseEntity<String> entity, HttpServletResponse response)
            throws IOException {
        response.setStatus(entity.getStatusCode().value());
        entity.getHeaders().forEach((name, values) -> values.forEach(value -> response.setHeader(name, value)));
        var body = entity.getBody();
        if (body != null) {
            response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
            response.getWriter().write(body);
        }
    }
}
