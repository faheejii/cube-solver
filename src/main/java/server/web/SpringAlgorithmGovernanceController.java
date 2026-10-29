package server.web;

import api.AlgorithmDefinitionRequest;
import api.AlgorithmRevisionActionRequest;
import api.AlgorithmRevisionRequest;
import database.AuthUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import server.catalog.AlgorithmGovernanceService;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.function.Supplier;

/** HTTP boundary for database-backed algorithm revision governance. */
@RestController
@RequestMapping("/api/algorithms")
final class SpringAlgorithmGovernanceController {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SpringDatabaseHealth databaseHealth;
    private final AlgorithmGovernanceService governance;

    SpringAlgorithmGovernanceController(SpringDatabaseHealth databaseHealth, AlgorithmGovernanceService governance) {
        this.databaseHealth = databaseHealth;
        this.governance = governance;
    }

    @GetMapping("/{id}")
    ResponseEntity<String> detail(@PathVariable("id") UUID id) {
        var actor = requireMaintainerAndDatabase();
        return invoke(() -> governance.detail(id, actor));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<?> deleteExperimentalAlgorithm(@PathVariable("id") UUID id) {
        var actor = requireMaintainerAndDatabase();
        try {
            governance.deleteExperimentalAlgorithm(id, actor);
            return SpringRequestSupport.noContent();
        } catch (AlgorithmGovernanceService.NotFoundException exception) {
            return SpringRequestSupport.error(404, "Algorithm not found");
        }
    }

    @PostMapping("/validate")
    ResponseEntity<String> validate(@RequestBody AlgorithmDefinitionRequest request) {
        requireMaintainerAndDatabase();
        return invoke(() -> governance.validate(request));
    }

    @PostMapping
    ResponseEntity<String> create(@RequestBody AlgorithmDefinitionRequest request) {
        var actor = requireMaintainerAndDatabase();
        return invoke(() -> governance.create(request, actor));
    }

    @PostMapping("/{id}/revisions")
    ResponseEntity<String> createRevision(@PathVariable("id") UUID id, @RequestBody AlgorithmRevisionRequest request) {
        var actor = requireMaintainerAndDatabase();
        return invoke(() -> governance.createRevision(id, request == null ? null : request.definition(),
                request == null ? null : request.expectedVersion(), actor));
    }

    @PostMapping("/{id}/revisions/{revisionId}/actions")
    ResponseEntity<String> action(@PathVariable("id") UUID id, @PathVariable("revisionId") UUID revisionId,
                                  @RequestBody AlgorithmRevisionActionRequest request) {
        var actor = requireMaintainerAndDatabase();
        return invoke(() -> governance.act(id, revisionId,
                request == null ? null : request.action(), request == null ? null : request.expectedVersion(),
                request == null ? null : request.reason(), actor));
    }

    private AuthUser requireMaintainerAndDatabase() {
        if (!databaseHealth.isConfigured()) throw new SpringDatabaseUnavailableException();
        return SpringRequestSupport.requireMaintainer();
    }

    private static ResponseEntity<String> invoke(Supplier<?> action) {
        try {
            return SpringRequestSupport.json(200, JSON.writeValueAsString(action.get()));
        } catch (AlgorithmGovernanceService.NotFoundException exception) {
            return SpringRequestSupport.error(404, "Algorithm not found");
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("Could not serialize algorithm governance response", exception);
        }
    }
}
