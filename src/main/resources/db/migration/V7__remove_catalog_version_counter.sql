ALTER TABLE algorithm_catalog_state
    DROP COLUMN catalog_version;

UPDATE solve_solutions
SET algorithm_manifest_json = algorithm_manifest_json - 'catalogVersion'
WHERE algorithm_manifest_json ? 'catalogVersion';
