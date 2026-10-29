package server.auth;

import database.persistence.entity.UserEntity;
import database.persistence.repository.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import springboot.config.AdminProperties;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
class SpringUserRoleService implements UserRoleOperations {
    private static final int MAX_PAGE_SIZE = 100;
    private final UserJpaRepository users;
    private final JdbcTemplate jdbc;
    private final String bootstrapEmail;

    @Autowired
    SpringUserRoleService(UserJpaRepository users, JdbcTemplate jdbc, AdminProperties adminProperties) {
        this.users = users;
        this.jdbc = jdbc;
        this.bootstrapEmail = adminProperties.getEmail();
    }

    @Override
    @Transactional(readOnly = true)
    public UserPage findUsers(String query, String cursor, int requestedLimit) {
        var limit = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedLimit));
        var afterId = decodeCursor(cursor);
        var normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        var found = users.searchAfter(afterId, normalizedQuery, PageRequest.of(0, limit + 1));
        var hasMore = found.size() > limit;
        var page = found.subList(0, Math.min(limit, found.size())).stream().map(this::toManagedUser).toList();
        var nextCursor = hasMore && !page.isEmpty() ? encodeCursor(page.getLast().id()) : null;
        return new UserPage(page, nextCursor);
    }

    @Override
    @Transactional
    public ManagedUser changeRole(String actorExternalId, long targetId, String role, OffsetDateTime expectedUpdatedAt) {
        var normalizedRole = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        if (!List.of("user", "maintainer", "admin").contains(normalizedRole)) {
            throw new IllegalArgumentException("role must be user, maintainer, or admin");
        }
        if (expectedUpdatedAt == null) {
            throw new IllegalArgumentException("expectedUpdatedAt is required");
        }

        // Serialize role changes so concurrent demotions cannot remove the final admin.
        jdbc.query("SELECT pg_advisory_xact_lock(741923106)", resultSet -> null);
        var actor = users.findByExternalId(actorExternalId)
                .orElseThrow(() -> new AuthOperations.UnauthorizedException("Authentication required"));
        if (!"admin".equals(actor.getRole())) {
            throw new AuthOperations.ForbiddenException("Administrator access required");
        }
        if (actor.getId() == targetId) {
            throw new AuthOperations.ForbiddenException("You cannot change your own role");
        }
        var target = users.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!target.getUpdatedAt().equals(expectedUpdatedAt)) {
            throw new AuthOperations.ConflictException("This account changed. Refresh the list and try again.");
        }
        var previousRole = target.getRole();
        if (previousRole.equals(normalizedRole)) {
            return toManagedUser(target);
        }
        if (isBootstrapAccount(target) && !"admin".equals(normalizedRole)) {
            throw new AuthOperations.ForbiddenException("The configured bootstrap administrator cannot be demoted");
        }
        if ("admin".equals(previousRole) && !"admin".equals(normalizedRole)
                && users.countByRole("admin") <= 1) {
            throw new AuthOperations.ConflictException("The last administrator cannot be demoted");
        }

        target.setRole(normalizedRole);
        var saved = users.saveAndFlush(target);
        jdbc.update("""
                        INSERT INTO user_role_changes(actor_user_id, target_user_id, previous_role, new_role)
                        VALUES (?, ?, ?, ?)
                        """,
                actor.getId(), saved.getId(), previousRole, normalizedRole);
        return toManagedUser(saved);
    }

    private boolean isBootstrapAccount(UserEntity user) {
        return bootstrapEmail != null && user.getEmail() != null
                && bootstrapEmail.equalsIgnoreCase(user.getEmail().trim());
    }

    private ManagedUser toManagedUser(UserEntity user) {
        return new ManagedUser(user.getId(), user.getExternalId(), user.getDisplayName(), user.getEmail(), user.getRole(),
                isBootstrapAccount(user), user.getUpdatedAt());
    }

    private static String encodeCursor(long id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Long.toString(id).getBytes(StandardCharsets.UTF_8));
    }

    private static long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            var decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            var id = Long.parseLong(decoded);
            if (id < 0) throw new IllegalArgumentException();
            return id;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid user cursor");
        }
    }
}
