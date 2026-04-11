CREATE TABLE resident_bookings (
    id                  UUID         PRIMARY KEY,
    resident_id         UUID         NOT NULL REFERENCES residents(id),
    requested_date      DATE         NOT NULL,
    building_name       VARCHAR(100) NOT NULL,
    room_number         VARCHAR(20),
    status              VARCHAR(20)  NOT NULL
                        CHECK (status IN ('REQUESTED','CONFIRMED','CANCELLED','COMPLETED','NO_SHOW')),
    purpose             VARCHAR(255),
    notes               TEXT,
    decision_reason     TEXT,
    created_by_user_id  UUID         REFERENCES users(id),
    updated_by_user_id  UUID         REFERENCES users(id),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_resident_bookings_resident ON resident_bookings(resident_id, requested_date DESC);
CREATE INDEX idx_resident_bookings_status ON resident_bookings(status, requested_date DESC);
CREATE INDEX idx_resident_bookings_building ON resident_bookings(building_name, requested_date DESC);

UPDATE analytics_snapshots
SET    metric_name = 'booking_conversion',
       updated_at  = NOW()
WHERE  metric_name = 'agreement_signthrough';
