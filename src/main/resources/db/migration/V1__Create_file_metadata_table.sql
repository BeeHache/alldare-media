CREATE TABLE file_metadata (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    group_id UUID,
    owner_read BOOLEAN NOT NULL DEFAULT TRUE,
    owner_write BOOLEAN NOT NULL DEFAULT TRUE,
    group_read BOOLEAN NOT NULL DEFAULT FALSE,
    group_write BOOLEAN NOT NULL DEFAULT FALSE,
    world_read BOOLEAN NOT NULL DEFAULT FALSE,
    s3_key VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Trigger to automatically update 'updated_at' on row modification
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_file_metadata_updated_at
    BEFORE UPDATE ON file_metadata
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
