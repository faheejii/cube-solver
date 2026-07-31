CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    external_id TEXT NOT NULL UNIQUE,
    display_name TEXT,
    email TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS solve_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    solve_count INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS solves (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id BIGINT REFERENCES solve_sessions(id) ON DELETE SET NULL,
    client_attempt_id TEXT,
    scramble TEXT NOT NULL,
    cross_face_requested TEXT NOT NULL,
    timer_ms INTEGER,
    penalty TEXT NOT NULL DEFAULT 'none',
    official_ms INTEGER,
    dnf BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS solve_solutions (
    id BIGSERIAL PRIMARY KEY,
    solve_id BIGINT NOT NULL REFERENCES solves(id) ON DELETE CASCADE,
    mode TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ready',
    cross_face_requested TEXT,
    cross_face_chosen TEXT NOT NULL,
    solution TEXT NOT NULL,
    normalized_solution TEXT,
    f2l_setup_case_count INTEGER NOT NULL DEFAULT 0,
    f2l_insert_case_count INTEGER NOT NULL DEFAULT 0,
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

ALTER TABLE solves ADD COLUMN IF NOT EXISTS client_attempt_id TEXT;
UPDATE solves SET client_attempt_id = 'legacy-' || id
WHERE client_attempt_id IS NULL OR client_attempt_id = '';
ALTER TABLE solves ALTER COLUMN client_attempt_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_solves_user_client_attempt
    ON solves (user_id, client_attempt_id);

ALTER TABLE solve_solutions ADD COLUMN IF NOT EXISTS cross_face_requested TEXT;
UPDATE solve_solutions solution SET cross_face_requested = solve.cross_face_requested
FROM solves solve
WHERE solution.solve_id = solve.id AND solution.cross_face_requested IS NULL;
ALTER TABLE solve_solutions ALTER COLUMN cross_face_requested SET NOT NULL;
ALTER TABLE solve_solutions ADD COLUMN IF NOT EXISTS f2l_setup_case_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE solve_solutions ADD COLUMN IF NOT EXISTS f2l_insert_case_count INTEGER NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'solves' AND column_name = 'f2l_mode'
    ) THEN
        INSERT INTO solve_solutions (
            solve_id, mode, status, cross_face_requested, cross_face_chosen, solution, normalized_solution,
            cross_algorithm, cross_moves, cross_solved, cross_status,
            f2l_algorithm, f2l_moves, f2l_solved, f2l_status,
            oll_algorithm, oll_moves, oll_solved, oll_status,
            pll_algorithm, pll_moves, pll_solved, pll_status,
            solved_f2l_slots, total_moves, solve_elapsed_ms, fully_solved,
            solver_version, created_at, updated_at
        )
        SELECT
            id, f2l_mode, 'ready', cross_face_requested, cross_face_chosen, solution, normalized_solution,
            cross_algorithm, cross_moves, cross_solved, cross_status,
            f2l_algorithm, f2l_moves, f2l_solved, f2l_status,
            oll_algorithm, oll_moves, oll_solved, oll_status,
            pll_algorithm, pll_moves, pll_solved, pll_status,
            solved_f2l_slots, total_moves, solve_elapsed_ms, fully_solved,
            solver_version, created_at, created_at
        FROM solves
        ON CONFLICT (solve_id, mode) DO NOTHING;
    END IF;
END $$;

ALTER TABLE solves DROP COLUMN IF EXISTS cross_face_chosen;
ALTER TABLE solves DROP COLUMN IF EXISTS f2l_mode;
ALTER TABLE solves DROP COLUMN IF EXISTS solution;
ALTER TABLE solves DROP COLUMN IF EXISTS normalized_solution;
ALTER TABLE solves DROP COLUMN IF EXISTS cross_algorithm;
ALTER TABLE solves DROP COLUMN IF EXISTS cross_moves;
ALTER TABLE solves DROP COLUMN IF EXISTS cross_solved;
ALTER TABLE solves DROP COLUMN IF EXISTS cross_status;
ALTER TABLE solves DROP COLUMN IF EXISTS f2l_algorithm;
ALTER TABLE solves DROP COLUMN IF EXISTS f2l_moves;
ALTER TABLE solves DROP COLUMN IF EXISTS f2l_solved;
ALTER TABLE solves DROP COLUMN IF EXISTS f2l_status;
ALTER TABLE solves DROP COLUMN IF EXISTS oll_algorithm;
ALTER TABLE solves DROP COLUMN IF EXISTS oll_moves;
ALTER TABLE solves DROP COLUMN IF EXISTS oll_solved;
ALTER TABLE solves DROP COLUMN IF EXISTS oll_status;
ALTER TABLE solves DROP COLUMN IF EXISTS pll_algorithm;
ALTER TABLE solves DROP COLUMN IF EXISTS pll_moves;
ALTER TABLE solves DROP COLUMN IF EXISTS pll_solved;
ALTER TABLE solves DROP COLUMN IF EXISTS pll_status;
ALTER TABLE solves DROP COLUMN IF EXISTS solved_f2l_slots;
ALTER TABLE solves DROP COLUMN IF EXISTS total_moves;
ALTER TABLE solves DROP COLUMN IF EXISTS solve_elapsed_ms;
ALTER TABLE solves DROP COLUMN IF EXISTS fully_solved;
ALTER TABLE solves DROP COLUMN IF EXISTS solver_version;

CREATE TABLE IF NOT EXISTS user_stats (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    solve_count INTEGER NOT NULL DEFAULT 0,
    dnf_count INTEGER NOT NULL DEFAULT 0,
    best_single_ms INTEGER,
    latest_official_ms INTEGER,
    latest_solve_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_solves_user_created_at ON solves (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_solves_user_official_ms ON solves (user_id, official_ms);
CREATE INDEX IF NOT EXISTS idx_solves_user_session_created_at ON solves (user_id, session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_solve_solutions_solve_id ON solve_solutions (solve_id);
CREATE INDEX IF NOT EXISTS idx_solve_solutions_mode ON solve_solutions (mode);
CREATE INDEX IF NOT EXISTS idx_sessions_user_started_at ON solve_sessions (user_id, started_at DESC);
