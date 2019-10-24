
-- Holds the content of mantik items
CREATE TABLE IF NOT EXISTS mantik_item (
    -- Item ID.
    item_id VARCHAR PRIMARY KEY,
    -- JSON/YAML JSON Content of Mantik file
    mantikfile VARCHAR NOT NULL,
    -- File ID
    file_id VARCHAR
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
