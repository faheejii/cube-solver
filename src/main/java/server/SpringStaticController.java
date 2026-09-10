package server;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import springboot.config.ServerProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serves frontend assets and preserves SPA fallback behavior for non-API routes. */
@Controller
final class SpringStaticController {
    private final Path frontendDistDir;
    private final ServerProperties serverProperties;

    SpringStaticController(Path frontendDistDir, ServerProperties serverProperties) {
        this.frontendDistDir = frontendDistDir;
        this.serverProperties = serverProperties;
    }

    @RequestMapping(value = "/**", method = RequestMethod.GET)
    ResponseEntity<Resource> staticFile(HttpServletRequest request) throws IOException {
        var requestPath = request.getRequestURI();
        if (requestPath.equals("/api") || requestPath.startsWith("/api/")) {
            return resource(404, "Not found", MediaType.TEXT_PLAIN);
        }
        if (frontendDistDir == null || !Files.isDirectory(frontendDistDir)) {
            return resource(200, "Frontend build not available.", MediaType.TEXT_PLAIN);
        }

        var relativePath = requestPath.equals("/") ? "index.html" : requestPath.substring(1);
        var target = frontendDistDir.resolve(relativePath).normalize();
        target = resolveGeneratedWorkerAlias(relativePath, target);
        if (!target.startsWith(frontendDistDir) || Files.isDirectory(target) || !Files.exists(target)) {
            if (hasFileExtension(relativePath)) {
                return resource(404, "Not found", MediaType.TEXT_PLAIN);
            }
            target = frontendDistDir.resolve("index.html");
        }
        var bytes = Files.readAllBytes(target);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentTypeFor(target)))
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, serverProperties.getCors().getOrigin())
                .body(new ByteArrayResource(bytes));
    }

    private ResponseEntity<Resource> resource(int status, String body, MediaType mediaType) {
        return ResponseEntity.status(status)
                .contentType(mediaType)
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, serverProperties.getCors().getOrigin())
                .body(new ByteArrayResource(body.getBytes(StandardCharsets.UTF_8)));
    }

    private Path resolveGeneratedWorkerAlias(String relativePath, Path target) throws IOException {
        if (Files.exists(target) || !relativePath.equals("assets/search-worker-entry.js")) {
            return target;
        }
        try (var files = Files.list(frontendDistDir.resolve("assets"))) {
            return files
                    .filter(path -> path.getFileName().toString().startsWith("search-worker-entry-"))
                    .filter(path -> path.getFileName().toString().endsWith(".js"))
                    .findFirst()
                    .orElse(target);
        }
    }

    private static boolean hasFileExtension(String path) {
        var lastSegment = path.substring(path.lastIndexOf('/') + 1);
        var extensionIndex = lastSegment.lastIndexOf('.');
        return extensionIndex > 0 && extensionIndex < lastSegment.length() - 1;
    }

    private static String contentTypeFor(Path file) {
        var name = file.getFileName().toString();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }
}
