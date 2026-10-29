ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('user', 'maintainer', 'admin'));

CREATE TABLE user_role_changes (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT NOT NULL REFERENCES users(id),
    target_user_id BIGINT NOT NULL REFERENCES users(id),
    previous_role TEXT NOT NULL CHECK (previous_role IN ('user', 'maintainer', 'admin')),
    new_role TEXT NOT NULL CHECK (new_role IN ('user', 'maintainer', 'admin')),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_user_role_change_changed CHECK (previous_role <> new_role)
);

CREATE INDEX idx_user_role_changes_target ON user_role_changes (target_user_id, changed_at DESC);
