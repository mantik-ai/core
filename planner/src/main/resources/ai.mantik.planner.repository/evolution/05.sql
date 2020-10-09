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