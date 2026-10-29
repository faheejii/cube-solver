CREATE FUNCTION reject_algorithm_catalog_history_deletion()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'algorithm catalog identities, revisions, and workflow events are retained';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_algorithm_catalog_algorithm_no_delete
    BEFORE DELETE ON algorithm_catalog_algorithms
    FOR EACH ROW EXECUTE FUNCTION reject_algorithm_catalog_history_deletion();

CREATE TRIGGER trg_algorithm_catalog_revision_no_delete
    BEFORE DELETE ON algorithm_catalog_revisions
    FOR EACH ROW EXECUTE FUNCTION reject_algorithm_catalog_history_deletion();

CREATE TRIGGER trg_algorithm_catalog_event_append_only
    BEFORE UPDATE OR DELETE ON algorithm_catalog_workflow_events
    FOR EACH ROW EXECUTE FUNCTION reject_algorithm_catalog_history_deletion();
