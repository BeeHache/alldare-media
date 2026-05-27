CREATE TABLE file_metadata (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    group_id UUID,
    owner_read BOOLEAN NOT NULL,
    owner_write BOOLEAN NOT NULL,
    group_read BOOLEAN NOT NULL,
    group_write BOOLEAN NOT NULL,
    s3_key VARCHAR(255) NOT NULL,
    created_at TnNOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_updated_at_trigger
BEFORE UPDATE ON file_metadata
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();