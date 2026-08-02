ALTER TABLE solve_solutions
    ADD COLUMN IF NOT EXISTS f2l_trace_json JSONB,
    ADD COLUMN IF NOT EXISTS comparison_json JSONB;
