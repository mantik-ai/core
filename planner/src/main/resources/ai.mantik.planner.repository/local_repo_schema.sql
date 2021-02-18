-- Database Schema for LocalRepository

-- Database is sqlite
-- Note: MUST be compatible to evolutions in evolution folder

-- Holds the content of mantik items
CREATE TABLE IF NOT EXISTS mantik_item (
    -- Item ID.
    item_id VARCHAR PRIMARY KEY,
    -- JSON/YAML original Content of Mantik Header
    mantikheader VARCHAR NOT NULL,
    -- File ID
    file_id VARCHAR,
    -- Kind of item
    kind VARCHAR NOT NULL DEFAULT '', -- Has default value to be compatible to migration.
    -- ID of the payload file mirrored in executor storage
    executor_storage_id VARCHAR
);

-- Holds the deployment info for a item id
CREATE TABLE IF NOT EXISTS mantik_deployment_info (
    item_id VARCHAR PRIMARY KEY,
    -- Name of the service
    name VARCHAR NOT NULL,
    -- URL inside the execution context
    internal_url VARCHAR NOT NULL,
    -- External URL, if an ingress service is activated
    external_url VARCHAR,
    timestamp TIMESTAMP,
    FOREIGN KEY (item_id) REFERENCES mantik_item(item_id)
);

CREATE TABLE IF NOT EXISTS mantik_sub_deployment_info (
    item_id VARCHAR NOT NULL,
    -- Name of  the Sub deployment info
    sub_id VARCHAR NOT NULL,

    -- Name of the service
    name VARCHAR NOT NULL,
    -- URL inside the execution context
    internal_url VARCHAR NOT NULL,

    PRIMARY KEY (item_id, sub_id),
    FOREIGN KEY (item_id) REFERENCES mantik_item (item_id)
);

CREATE INDEX IF NOT EXISTS mantik_sub_deployment_info_item_id ON mantik_sub_deployment_info(item_id);

-- Maps Mantik ids to Item ids.
CREATE TABLE IF NOT EXISTS mantik_name (
    -- Internal ID
    id UUID PRIMARY KEY,
    -- Account of the artifact (default library)
    account VARCHAR NOT NULL,
    -- Name of the artifact
    name VARCHAR NOT NULL,
    -- Version (default is latest)
    version VARCHAR NOT NULL,

    current_item_id TEXT NOT NULL,
    FOREIGN KEY (current_item_id) REFERENCES mantik_item(item_id)
);


CREATE UNIQUE INDEX IF NOT EXISTS mantik_artifact_name_version ON mantik_name (account, name, version);
CREATE INDEX IF NOT EXISTS  mantik_item_kind ON mantik_item (kind);