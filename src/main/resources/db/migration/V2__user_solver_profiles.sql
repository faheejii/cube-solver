CREATE TABLE user_disabled_algorithms (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    phase TEXT NOT NULL,
    case_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_user_disabled_algorithms PRIMARY KEY (user_id, phase, case_name),
    CONSTRAINT ck_user_disabled_algorithms_phase CHECK (phase IN ('setup', 'insert', 'oll', 'pll'))
);

ALTER TABLE solve_solutions
    ADD COLUMN disabled_algorithms_json JSONB;
