package server.auth;

import java.time.OffsetDateTime;
import java.util.List;

/** Narrow administrative account-role contract used by the HTTP adapters. */
public interface UserRoleOperations {
    UserPage findUsers(String query, String cursor, int limit);

    ManagedUser changeRole(String actorExternalId, long targetId, String role, OffsetDateTime expectedUpdatedAt);

    record ManagedUser(
            long id,
            String externalId,
            String displayName,
            String email,
            String role,
            boolean protectedAccount,
            OffsetDateTime updatedAt
    ) { }

    record UserPage(List<ManagedUser> items, String nextCursor) {
        public UserPage {
            items = List.copyOf(items);
        }
    }
}
