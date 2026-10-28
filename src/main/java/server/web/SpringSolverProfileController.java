package server.web;

import api.UpdateSolverProfileRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import server.solve.SolverProfileOperations;

/** Maintainer-only endpoint for personal solver preferences. */
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
        var user = requireMaintainerAndDatabase();
        return SpringRequestSupport.json(200, profileJson(profiles.findProfile(user.externalId())));
    }

    @PutMapping
    ResponseEntity<String> replace(@RequestBody UpdateSolverProfileRequest request) {
        var user = requireMaintainerAndDatabase();
        var profile = profiles.replaceProfile(user.externalId(), request.disabledAlgorithms(),
                request.disabledAlgorithmIds(), request.enabledTrialRevisionIds());
        return SpringRequestSupport.json(200, profileJson(profile));
    }

    private database.AuthUser requireMaintainerAndDatabase() {
        if (!databaseHealth.isConfigured()) {
            throw new SpringDatabaseUnavailableException();
        }
        return SpringRequestSupport.requireMaintainer();
    }

    private static String profileJson(SolverProfileOperations.Profile profile) {
        return "{\"disabledAlgorithms\":" + JsonSupport.stringArrayJson(profile.disabledAlgorithms())
                + ",\"disabledAlgorithmIds\":" + JsonSupport.stringArrayJson(profile.disabledAlgorithmIds())
                + ",\"enabledTrialRevisionIds\":" + JsonSupport.stringArrayJson(profile.enabledTrialRevisionIds())
                + ",\"inactiveTrialRevisionIds\":" + JsonSupport.stringArrayJson(profile.inactiveTrialRevisionIds()) + "}";
    }
}
