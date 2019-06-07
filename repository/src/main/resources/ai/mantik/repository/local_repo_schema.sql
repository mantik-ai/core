-- Database Schema for LocalRepository
-- Note: there is no migration support yet
-- The schema is executed each time the client connects.

-- Database is sqlite

CREATE TABLE IF NOT EXISTS mantik_artifact (
    -- Internal ID
    id UUID PRIMARY KEY,
    -- Name of the artifact
    name VARCHAR NOT NULL,
    -- Version (default is latest)
    version VARCHAR NOT NULL,
    -- JSON Content of Mantik file
    mantikfile VARCHAR NOT NULL,
    -- ID of File Repository (optional)
    file_id VARCHAR
);

CREATE UNIQUE INDEX IF NOT EXISTS mantik_artifact_name_version ON mantik_artifact (name, version);
