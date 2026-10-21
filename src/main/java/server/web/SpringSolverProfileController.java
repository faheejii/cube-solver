package server.web;

import api.UpdateSolverProfileRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import server.solve.SolverProfileOperations;

/** Admin-only endpoint for each administrator's personal solver profile. */
@RestController
@RequestMapping("/api/admin/solver-profile")
final class SpringSolverProfileController {
    private final SpringDatabaseHealth databaseHealth;
    private final SolverProfileOperations profiles;

    SpringSolverProfileController(SpringDatabaseHealth databaseHealth, SolverProfileOperations profiles) {
        this.databaseHealth = databaseHealth;
        this.profiles = profiles;
    }

    @GetMapping
    ResponseEntity<String> get() {
        var user = requireAdminAndDatabase();
        var disabled = profiles.findDisabledAlgorithms(user.externalId());
        return SpringRequestSupport.json(200, profileJson(disabled));
    }

    @PutMapping
    ResponseEntity<String> replace(@RequestBody UpdateSolverProfileRequest request) {
        var user = requireAdminAndDatabase();
        var disabled = profiles.replaceDisabledAlgorithms(user.externalId(), request.disabledAlgorithms());
        return SpringRequestSupport.json(200, profileJson(disabled));
    }

    private database.AuthUser requireAdminAndDatabase() {
        if (!databaseHealth.isConfigured()) {
            throw new SpringDatabaseUnavailableException();
        }
        return SpringRequestSupport.requireAdmin();
    }

    private static String profileJson(java.util.List<String> disabled) {
        return "{\"disabledAlgorithms\":" + JsonSupport.stringArrayJson(disabled) + "}";
    }
}
