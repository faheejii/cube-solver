CREATE TABLE algorithm_catalog_state (
    singleton_id SMALLINT PRIMARY KEY CHECK (singleton_id = 1),
    catalog_version BIGINT NOT NULL DEFAULT 0 CHECK (catalog_version >= 0),
    seed_version TEXT,
    seed_checksum TEXT,
    initialized_at TIMESTAMPTZ
);

INSERT INTO algorithm_catalog_state (singleton_id, catalog_version)
VALUES (1, 0);

CREATE TABLE algorithm_catalog_algorithms (
    id UUID PRIMARY KEY,
    phase TEXT NOT NULL CHECK (phase IN ('setup', 'insert', 'oll', 'pll')),
    name TEXT NOT NULL,
    owner_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    seed_key TEXT UNIQUE,
    seed_order INTEGER CHECK (seed_order IS NULL OR seed_order >= 0),
    active_canonical_revision_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_algorithm_catalog_phase_name UNIQUE (phase, name)
);

CREATE UNIQUE INDEX uq_algorithm_catalog_seed_order
    ON algorithm_catalog_algorithms (seed_order)
    WHERE seed_order IS NOT NULL;

CREATE TABLE algorithm_catalog_revisions (
    id UUID PRIMARY KEY,
    algorithm_id UUID NOT NULL REFERENCES algorithm_catalog_algorithms(id) ON DELETE RESTRICT,
    revision_number INTEGER NOT NULL CHECK (revision_number > 0),
    definition_json JSONB NOT NULL,
    author_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    status TEXT NOT NULL CHECK (status IN ('canonical', 'experimental', 'test-only', 'deprecated')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_algorithm_catalog_revision_number UNIQUE (algorithm_id, revision_number),
    CONSTRAINT uq_algorithm_catalog_revision_algorithm UNIQUE (algorithm_id, id)
);

ALTER TABLE algorithm_catalog_algorithms
    ADD CONSTRAINT fk_algorithm_catalog_active_canonical_revision
    FOREIGN KEY (id, active_canonical_revision_id)
    REFERENCES algorithm_catalog_revisions (algorithm_id, id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX idx_algorithm_catalog_revisions_algorithm_status
    ON algorithm_catalog_revisions (algorithm_id, status, revision_number DESC);

CREATE TABLE algorithm_catalog_workflow_events (
    id BIGSERIAL PRIMARY KEY,
    action TEXT NOT NULL,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    revision_id UUID NOT NULL REFERENCES algorithm_catalog_revisions(id) ON DELETE RESTRICT,
    previous_status TEXT CHECK (previous_status IS NULL OR previous_status IN ('canonical', 'experimental', 'test-only', 'deprecated')),
    new_status TEXT CHECK (new_status IS NULL OR new_status IN ('canonical', 'experimental', 'test-only', 'deprecated')),
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_algorithm_catalog_workflow_revision_time
    ON algorithm_catalog_workflow_events (revision_id, occurred_at, id);

CREATE TABLE user_disabled_algorithm_ids (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    algorithm_id UUID NOT NULL REFERENCES algorithm_catalog_algorithms(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, algorithm_id)
);

CREATE TABLE user_enabled_trial_revision_ids (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    revision_id UUID NOT NULL REFERENCES algorithm_catalog_revisions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, revision_id)
);

CREATE FUNCTION reject_algorithm_seed_key_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.seed_key IS DISTINCT FROM OLD.seed_key THEN
        RAISE EXCEPTION 'algorithm seed keys are immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_algorithm_seed_key_immutable
    BEFORE UPDATE ON algorithm_catalog_algorithms
    FOR EACH ROW EXECUTE FUNCTION reject_algorithm_seed_key_changes();

CREATE FUNCTION reject_algorithm_revision_definition_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.algorithm_id IS DISTINCT FROM OLD.algorithm_id
       OR NEW.revision_number IS DISTINCT FROM OLD.revision_number
       OR NEW.definition_json IS DISTINCT FROM OLD.definition_json
       OR NEW.author_user_id IS DISTINCT FROM OLD.author_user_id
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'algorithm revision definitions are immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_algorithm_catalog_revision_definition_immutable
    BEFORE UPDATE ON algorithm_catalog_revisions
    FOR EACH ROW EXECUTE FUNCTION reject_algorithm_revision_definition_changes();
