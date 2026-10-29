package server.web;

import api.ChangeUserRoleRequest;
import database.AuthUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import server.auth.UserRoleOperations;

/** Admin-only account listing and role-management routes. */
@RestController
@RequestMapping("/api/admin/users")
final class SpringAdminUsersController {
    private final SpringDatabaseHealth databaseHealth;
    private final UserRoleOperations roles;

    SpringAdminUsersController(SpringDatabaseHealth databaseHealth, UserRoleOperations roles) {
        this.databaseHealth = databaseHealth;
        this.roles = roles;
    }

    @GetMapping
    ResponseEntity<String> list(
            @RequestParam(value = "q", required = false, defaultValue = "") String query,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        requireAdminAndDatabase();
        var page = roles.findUsers(query, cursor, limit);
        var items = page.items().stream().map(SpringAdminUsersController::userJson)
                .collect(java.util.stream.Collectors.joining(","));
        return SpringRequestSupport.json(200, "{\"items\":[" + items + "],\"nextCursor\":"
                + JsonSupport.nullableString(page.nextCursor()) + "}");
    }

    @PatchMapping("/{id}/role")
    ResponseEntity<String> changeRole(@PathVariable("id") long id, @RequestBody ChangeUserRoleRequest request) {
        var actor = requireAdminAndDatabase();
        return SpringRequestSupport.json(200,
                userJson(roles.changeRole(actor.externalId(), id, request.role(), request.expectedUpdatedAt())));
    }

    private AuthUser requireAdminAndDatabase() {
        if (!databaseHealth.isConfigured()) throw new SpringDatabaseUnavailableException();
        return SpringRequestSupport.requireAdmin();
    }

    private static String userJson(UserRoleOperations.ManagedUser user) {
        return "{\"id\":" + user.id() + ",\"externalId\":\""
                + SpringRequestSupport.escape(user.externalId()) + "\",\"displayName\":"
                + JsonSupport.nullableString(user.displayName())
                + ",\"email\":" + JsonSupport.nullableString(user.email())
                + ",\"role\":\"" + SpringRequestSupport.escape(user.role()) + "\",\"protected\":"
                + user.protectedAccount() + ",\"updatedAt\":\"" + SpringRequestSupport.escape(user.updatedAt().toString())
                + "\"}";
    }
}
