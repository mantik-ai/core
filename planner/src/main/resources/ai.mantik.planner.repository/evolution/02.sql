ALTER TABLE mantik_item ADD COLUMN kind VARCHAR NOT NULL DEFAULT '';
CREATE INDEX IF NOT EXISTS  mantik_item_kind ON mantik_item (kind);

-- Kind is filled using Post-Migration step
