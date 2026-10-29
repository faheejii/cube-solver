DROP TRIGGER trg_algorithm_catalog_algorithm_no_delete ON algorithm_catalog_algorithms;
DROP TRIGGER trg_algorithm_catalog_revision_no_delete ON algorithm_catalog_revisions;
DROP TRIGGER trg_algorithm_catalog_event_append_only ON algorithm_catalog_workflow_events;
DROP FUNCTION reject_algorithm_catalog_history_deletion();

ALTER TABLE algorithm_catalog_revisions
    DROP CONSTRAINT algorithm_catalog_revisions_status_check;
ALTER TABLE algorithm_catalog_revisions
    ADD CONSTRAINT algorithm_catalog_revisions_status_check
    CHECK (status IN ('canonical', 'experimental', 'test-only', 'deprecated', 'superseded'));

ALTER TABLE algorithm_catalog_workflow_events
    DROP CONSTRAINT algorithm_catalog_workflow_events_previous_status_check;
ALTER TABLE algorithm_catalog_workflow_events
    ADD CONSTRAINT algorithm_catalog_workflow_events_previous_status_check
    CHECK (previous_status IS NULL OR previous_status IN ('canonical', 'experimental', 'test-only', 'deprecated', 'superseded'));
ALTER TABLE algorithm_catalog_workflow_events
    DROP CONSTRAINT algorithm_catalog_workflow_events_new_status_check;
ALTER TABLE algorithm_catalog_workflow_events
    ADD CONSTRAINT algorithm_catalog_workflow_events_new_status_check
    CHECK (new_status IS NULL OR new_status IN ('canonical', 'experimental', 'test-only', 'deprecated', 'superseded'));

UPDATE algorithm_catalog_workflow_events
SET new_status = 'superseded'
WHERE action = 'superseded'
  AND previous_status IN ('experimental', 'test-only')
  AND new_status = 'deprecated';

UPDATE algorithm_catalog_revisions revision
SET status = 'superseded', version = version + 1
WHERE revision.status = 'deprecated'
  AND EXISTS (
      SELECT 1
      FROM algorithm_catalog_workflow_events event
      WHERE event.revision_id = revision.id
        AND event.action = 'superseded'
        AND event.previous_status IN ('experimental', 'test-only')
        AND event.new_status = 'superseded'
  );

CREATE FUNCTION guard_algorithm_catalog_algorithm_delete()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.seed_key IS NOT NULL
       OR OLD.active_canonical_revision_id IS NOT NULL
       OR EXISTS (SELECT 1 FROM algorithm_catalog_revisions WHERE algorithm_id = OLD.id) THEN
        RAISE EXCEPTION 'only empty, non-seed experimental algorithm identities may be deleted';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_algorithm_catalog_algorithm_delete_guard
    BEFORE DELETE ON algorithm_catalog_algorithms
    FOR EACH ROW EXECUTE FUNCTION guard_algorithm_catalog_algorithm_delete();

CREATE FUNCTION guard_private_algorithm_revision_delete()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status NOT IN ('experimental', 'superseded')
       OR EXISTS (
           SELECT 1 FROM algorithm_catalog_workflow_events
           WHERE revision_id = OLD.id AND new_status IN ('test-only', 'canonical')
       ) THEN
        RAISE EXCEPTION 'only never-shared experimental revisions may be deleted';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_algorithm_catalog_revision_delete_guard
    BEFORE DELETE ON algorithm_catalog_revisions
    FOR EACH ROW EXECUTE FUNCTION guard_private_algorithm_revision_delete();

CREATE FUNCTION guard_private_algorithm_event_delete()
RETURNS TRIGGER AS $$
DECLARE
    revision_status TEXT;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'algorithm workflow events are immutable';
    END IF;

    SELECT status INTO revision_status
    FROM algorithm_catalog_revisions
    WHERE id = OLD.revision_id;

    IF revision_status NOT IN ('experimental', 'superseded')
       OR EXISTS (
           SELECT 1 FROM algorithm_catalog_workflow_events
           WHERE revision_id = OLD.revision_id AND new_status IN ('test-only', 'canonical')
       ) THEN
        RAISE EXCEPTION 'workflow history can only be deleted with a never-shared experimental algorithm';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_algorithm_catalog_event_delete_guard
    BEFORE UPDATE OR DELETE ON algorithm_catalog_workflow_events
    FOR EACH ROW EXECUTE FUNCTION guard_private_algorithm_event_delete();
