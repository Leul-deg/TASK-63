-- V6: Housing agreement attachment file metadata

CREATE TABLE agreement_attachments (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    agreement_id     UUID         NOT NULL REFERENCES housing_agreements(id) ON DELETE CASCADE,
    original_filename TEXT        NOT NULL,
    stored_filename  TEXT         NOT NULL,    -- server-generated UUID.ext, never user-supplied
    content_type     VARCHAR(50)  NOT NULL,
    file_size_bytes  BIGINT       NOT NULL,
    uploaded_by      TEXT         NOT NULL,    -- username of uploader
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agreement_attachments_agreement ON agreement_attachments(agreement_id);
