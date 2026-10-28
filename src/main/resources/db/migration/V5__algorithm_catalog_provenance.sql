CREATE TABLE algorithm_catalog_legacy_aliases (
    alias TEXT PRIMARY KEY,
    algorithm_id UUID NOT NULL REFERENCES algorithm_catalog_algorithms(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_algorithm_catalog_legacy_alias_algorithm
    ON algorithm_catalog_legacy_aliases (algorithm_id);

ALTER TABLE solve_solutions
    ADD COLUMN algorithm_manifest_json JSONB;

COMMENT ON COLUMN solve_solutions.algorithm_manifest_json IS
    'Server-captured catalog revision/profile manifest; null for legacy saved solutions.';
