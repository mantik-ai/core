DROP TABLE mantik_sub_deployment_info;
ALTER TABLE mantik_deployment_info RENAME COLUMN name TO evaluation_id;
