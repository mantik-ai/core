-- Database Schema for LocalRepository
-- Note: there is no migration support yet
-- The schema is executed each time the client connects.

-- Database is sqlite

-- Holds the content of mantik items
CREATE TABLE IF NOT EXISTS mantik_item (
    -- Item ID.
    item_id VARCHAR PRIMARY KEY,
    -- JSON Content of Mantik file
    mantikfile VARCHAR NOT NULL,
    -- File ID
    file_id VARCHAR
);

-- Holds the deployment info for a item id
CREATE TABLE IF NOT EXISTS mantik_deployment_info (
    item_id VARCHAR PRIMARY KEY,
    name VARCHAR NOT NULL,
    url VARCHAR NOT NULL,
    timestamp TIMESTAMP,
    FOREIGN KEY (item_id) REFERENCES mantik_item(item_id)
);

-- Maps Mantik ids to Item ids.
CREATE TABLE IF NOT EXISTS mantik_name (
    -- Internal ID
    id UUID PRIMARY KEY,
    -- Name of the artifact
    name VARCHAR NOT NULL,
    -- Version (default is latest)
    version VARCHAR NOT NULL,

    current_item_id TEXT NOT NULL,
    FOREIGN KEY (current_item_id) REFERENCES mantik_item(item_id)
);


CREATE UNIQUE INDEX IF NOT EXISTS mantik_artifact_name_version ON mantik_name (name, version);
