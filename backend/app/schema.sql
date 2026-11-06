-- =====================================================================
-- Smart Room Booking - PostgreSQL schema
-- Business rules enforced by the database itself are marked [BRxx].
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- needed to mix "=" and "&&" in one GiST exclusion constraint

DO $$ BEGIN
    CREATE TYPE user_role AS ENUM ('BOOKER', 'APPROVER', 'ADMIN');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE booking_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    email         TEXT        NOT NULL UNIQUE CHECK (email = lower(email)),
    password_hash TEXT        NOT NULL,
    full_name     TEXT        NOT NULL CHECK (length(trim(full_name)) > 0),
    role          user_role   NOT NULL DEFAULT 'BOOKER',
    faculty       TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS room_types (
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS rooms (
    id          BIGSERIAL PRIMARY KEY,
    code        TEXT        NOT NULL UNIQUE,
    name        TEXT        NOT NULL,
    building    TEXT        NOT NULL,
    floor       INT         NOT NULL,
    capacity    INT         NOT NULL CHECK (capacity > 0),
    type_id     INT         NOT NULL REFERENCES room_types(id),
    status      TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    description TEXT,
    image_url   TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS equipment (
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

-- M:N rooms <-> equipment
CREATE TABLE IF NOT EXISTS room_equipment (
    room_id      BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    equipment_id INT    NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    quantity     INT    NOT NULL DEFAULT 1 CHECK (quantity > 0),
    PRIMARY KEY (room_id, equipment_id)
);

-- Maintenance / closure periods [BR03]
CREATE TABLE IF NOT EXISTS room_closures (
    id         BIGSERIAL PRIMARY KEY,
    room_id    BIGINT      NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    start_time TIMESTAMPTZ NOT NULL,
    end_time   TIMESTAMPTZ NOT NULL,
    reason     TEXT        NOT NULL,
    created_by BIGINT      REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT closure_time_order CHECK (start_time < end_time)
);

-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bookings (
    id            BIGSERIAL PRIMARY KEY,
    room_id       BIGINT         NOT NULL REFERENCES rooms(id),
    booker_id     BIGINT         NOT NULL REFERENCES users(id),
    approver_id   BIGINT         REFERENCES users(id),
    start_time    TIMESTAMPTZ    NOT NULL,
    end_time      TIMESTAMPTZ    NOT NULL,
    attendees     INT            NOT NULL CHECK (attendees > 0),
    purpose       TEXT           NOT NULL CHECK (length(trim(purpose)) > 0),
    status        booking_status NOT NULL DEFAULT 'PENDING',
    reject_reason TEXT,
    decided_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version       INT            NOT NULL DEFAULT 1,

    -- [BR01] start must be before end
    CONSTRAINT booking_time_order CHECK (start_time < end_time),

    -- [BR09] a rejection must carry a reason
    CONSTRAINT reject_needs_reason CHECK (
        status <> 'REJECTED' OR (reject_reason IS NOT NULL AND length(trim(reject_reason)) > 0)
    ),

    -- [BR02 + BR05] No two active bookings of the same room may overlap.
    -- '[)' = start inclusive, end exclusive, so 09:00-10:00 and 10:00-11:00 do NOT overlap.
    -- Enforced inside PostgreSQL, so it also holds when two users submit at the same instant.
    CONSTRAINT no_overlapping_bookings EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status IN ('PENDING', 'APPROVED'))
);

CREATE INDEX IF NOT EXISTS idx_bookings_booker ON bookings (booker_id, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings (status, created_at);

-- Audit trail of every status change
CREATE TABLE IF NOT EXISTS booking_status_logs (
    id          BIGSERIAL PRIMARY KEY,
    booking_id  BIGINT         NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    from_status booking_status,
    to_status   booking_status NOT NULL,
    changed_by  BIGINT         NOT NULL REFERENCES users(id),
    changed_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    note        TEXT
);

CREATE TABLE IF NOT EXISTS notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    booking_id BIGINT      REFERENCES bookings(id) ON DELETE CASCADE,
    type       TEXT        NOT NULL,
    title      TEXT        NOT NULL,
    message    TEXT        NOT NULL,
    is_read    BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications (user_id, id DESC);

-- ---------------------------------------------------------------------
-- QR check-in and no-shows. Written as ALTERs so an existing database upgrades on the next start.
-- ---------------------------------------------------------------------
-- The code inside the QR poster on each room's door. Rotating it invalidates old posters.
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS checkin_code TEXT NOT NULL
    DEFAULT upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 10));

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS checked_in_at TIMESTAMPTZ;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS checked_in_by BIGINT REFERENCES users(id);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS checkin_method TEXT
    CHECK (checkin_method IN ('SELF', 'STAFF'));

-- Past bookings predate check-in, so they must not all be recorded as no-shows on upgrade.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = current_schema() AND table_name = 'bookings'
                     AND column_name = 'checkin_required') THEN
        ALTER TABLE bookings ADD COLUMN checkin_required BOOLEAN NOT NULL DEFAULT true;
        UPDATE bookings SET checkin_required = false WHERE start_time <= now();
    END IF;
END $$;

-- changed_by is NULL when the system made the change (an automatic no-show).
ALTER TABLE booking_status_logs ALTER COLUMN changed_by DROP NOT NULL;

-- ---------------------------------------------------------------------
-- Waitlist: when the requested time frees up, the oldest fitting entry becomes a PENDING booking.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS waitlist_entries (
    id         BIGSERIAL PRIMARY KEY,
    room_id    BIGINT      NOT NULL REFERENCES rooms(id),
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    start_time TIMESTAMPTZ NOT NULL,
    end_time   TIMESTAMPTZ NOT NULL,
    attendees  INT         NOT NULL CHECK (attendees > 0),
    purpose    TEXT        NOT NULL CHECK (length(trim(purpose)) > 0),
    status     TEXT        NOT NULL DEFAULT 'WAITING'
               CHECK (status IN ('WAITING', 'PROMOTED', 'CANCELLED', 'EXPIRED')),
    booking_id BIGINT      REFERENCES bookings(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT waitlist_time_order CHECK (start_time < end_time),

    -- One user can't queue twice for overlapping times in the same room.
    CONSTRAINT no_duplicate_waitlist EXCLUDE USING gist (
        room_id WITH =,
        user_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status = 'WAITING')
);

CREATE INDEX IF NOT EXISTS idx_waitlist_queue ON waitlist_entries (room_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_waitlist_user ON waitlist_entries (user_id, id DESC);
