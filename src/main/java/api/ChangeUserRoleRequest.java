package api;

import java.time.OffsetDateTime;

public record ChangeUserRoleRequest(String role, OffsetDateTime expectedUpdatedAt) { }
