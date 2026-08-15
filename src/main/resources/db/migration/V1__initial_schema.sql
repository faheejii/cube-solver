CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    external_id TEXT NOT NULL UNIQUE,
    display_name TEXT,
    email TEXT,
    password_hash TEXT,
    password_salt TEXT,
    password_iterations INTEGER,
    role TEXT NOT NULL DEFAULT 'user',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_users_complete_password_credential CHECK (
        (password_hash IS NULL AND password_salt IS NULL AND password_iterations IS NULL)
        OR
        (password_hash IS NOT NULL AND password_salt IS NOT NULL AND password_iterations > 0)
    ),
    CONSTRAINT ck_users_role CHECK (role IN ('user', 'admin'))
);

CREATE UNIQUE INDEX uq_authenticated_users_email
    ON users (LOWER(email))
    WHERE password_hash IS NOT NULL;

CREATE TABLE auth_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_sessions_user_id ON auth_sessions (user_id);
CREATE INDEX idx_auth_sessions_expires_at ON auth_sessions (expires_at);

CREATE TABLE solve_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    solve_count INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE solves (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id BIGINT REFERENCES solve_sessions(id) ON DELETE SET NULL,
    client_attempt_id TEXT NOT NULL,
    scramble TEXT NOT NULL,
    cross_face_requested TEXT NOT NULL,
    timer_ms INTEGER,
    penalty TEXT NOT NULL DEFAULT 'none',
    official_ms INTEGER,
    dnf BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_solves_user_client_attempt
    ON solves (user_id, client_attempt_id);
CREATE INDEX idx_solves_user_created_at ON solves (user_id, created_at DESC);
CREATE INDEX idx_solves_user_official_ms ON solves (user_id, official_ms);
CREATE INDEX idx_solves_user_session_created_at ON solves (user_id, session_id, created_at DESC);

CREATE TABLE solve_solutions (
    id BIGSERIAL PRIMARY KEY,
    solve_id BIGINT NOT NULL REFERENCES solves(id) ON DELETE CASCADE,
    mode TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ready',
    cross_face_requested TEXT NOT NULL,
    cross_face_chosen TEXT NOT NULL,
    solution TEXT NOT NULL,
    normalized_solution TEXT,
    f2l_setup_case_count INTEGER NOT NULL DEFAULT 0,
    f2l_insert_case_count INTEGER NOT NULL DEFAULT 0,
    f2l_trace_json JSONB NOT NULL,
    comparison_json JSONB,
    cross_algorithm TEXT NOT NULL,
    cross_moves INTEGER NOT NULL,
    cross_solved BOOLEAN NOT NULL,
    cross_status TEXT NOT NULL,
    f2l_algorithm TEXT NOT NULL,
    f2l_moves INTEGER NOT NULL,
    f2l_solved BOOLEAN NOT NULL,
    f2l_status TEXT NOT NULL,
    oll_algorithm TEXT NOT NULL,
    oll_moves INTEGER NOT NULL,
    oll_solved BOOLEAN NOT NULL,
    oll_status TEXT NOT NULL,
    pll_algorithm TEXT NOT NULL,
    pll_moves INTEGER NOT NULL,
    pll_solved BOOLEAN NOT NULL,
    pll_status TEXT NOT NULL,
    solved_f2l_slots TEXT NOT NULL,
    total_moves INTEGER NOT NULL,
    solve_elapsed_ms DOUBLE PRECISION NOT NULL,
    fully_solved BOOLEAN NOT NULL,
    solver_version TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_solve_solutions_solve_mode UNIQUE (solve_id, mode)
);

CREATE INDEX idx_solve_solutions_solve_id ON solve_solutions (solve_id);
CREATE INDEX idx_solve_solutions_mode ON solve_solutions (mode);

CREATE TABLE user_stats (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    solve_count INTEGER NOT NULL DEFAULT 0,
    dnf_count INTEGER NOT NULL DEFAULT 0,
    best_single_ms INTEGER,
    latest_official_ms INTEGER,
    latest_solve_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sessions_user_started_at ON solve_sessions (user_id, started_at DESC);
