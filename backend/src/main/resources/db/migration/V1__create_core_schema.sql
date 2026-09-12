CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    provisioned_by BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_provisioned_by FOREIGN KEY (provisioned_by) REFERENCES users (id),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'TEAM_MEMBER')),
    CONSTRAINT ck_users_provisioning CHECK (
        (role = 'ADMIN' AND provisioned_by IS NULL)
        OR (role = 'TEAM_MEMBER' AND provisioned_by IS NOT NULL)
    )
);
CREATE INDEX ix_users_provisioned_by_role ON users (provisioned_by, role, id);

CREATE TABLE events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(2000) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_events PRIMARY KEY (id),
    CONSTRAINT fk_events_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);
CREATE INDEX ix_events_owner_created ON events (owner_id, created_at, id);

CREATE TABLE event_members (
    event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    added_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_event_members PRIMARY KEY (event_id, user_id),
    CONSTRAINT fk_event_members_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_event_members_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX ix_event_members_user_event ON event_members (user_id, event_id);

CREATE TABLE photos (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    uploaded_by BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    width_px INT NOT NULL,
    height_px INT NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    failure_code VARCHAR(50) NULL,
    CONSTRAINT pk_photos PRIMARY KEY (id),
    CONSTRAINT uk_photos_storage_key UNIQUE (storage_key),
    CONSTRAINT uk_photos_id_event UNIQUE (id, event_id),
    CONSTRAINT fk_photos_membership FOREIGN KEY (event_id, uploaded_by)
        REFERENCES event_members (event_id, user_id),
    CONSTRAINT ck_photos_size CHECK (file_size_bytes > 0),
    CONSTRAINT ck_photos_dimensions CHECK (width_px > 0 AND height_px > 0),
    CONSTRAINT ck_photos_status CHECK (status IN ('PENDING', 'READY', 'FAILED'))
);
CREATE INDEX ix_photos_event_status_created ON photos (event_id, status, created_at, id);
CREATE INDEX ix_photos_event_uploader_status ON photos (event_id, uploaded_by, status, created_at, id);
CREATE INDEX ix_photos_status_updated ON photos (status, updated_at);

CREATE TABLE galleries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    status VARCHAR(10) NOT NULL,
    pin_hash VARCHAR(255) NULL,
    pin_version INT NOT NULL DEFAULT 0,
    share_token VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    CONSTRAINT pk_galleries PRIMARY KEY (id),
    CONSTRAINT uk_galleries_event UNIQUE (event_id),
    CONSTRAINT uk_galleries_id_event UNIQUE (id, event_id),
    CONSTRAINT uk_galleries_share_token UNIQUE (share_token),
    CONSTRAINT fk_galleries_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT ck_galleries_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_galleries_pin CHECK (
        (pin_hash IS NULL AND pin_version = 0)
        OR (pin_hash IS NOT NULL AND pin_version > 0)
    ),
    CONSTRAINT ck_galleries_publication CHECK (
        (status = 'DRAFT' AND published_at IS NULL)
        OR (status = 'PUBLISHED' AND pin_hash IS NOT NULL AND published_at IS NOT NULL)
    )
);

CREATE TABLE gallery_photos (
    gallery_id BIGINT NOT NULL,
    photo_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    position INT NOT NULL,
    selected_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_gallery_photos PRIMARY KEY (gallery_id, photo_id),
    CONSTRAINT uk_gallery_photos_position UNIQUE (gallery_id, position),
    CONSTRAINT fk_gallery_photos_gallery FOREIGN KEY (gallery_id, event_id)
        REFERENCES galleries (id, event_id),
    CONSTRAINT fk_gallery_photos_photo FOREIGN KEY (photo_id, event_id)
        REFERENCES photos (id, event_id),
    CONSTRAINT ck_gallery_photos_position CHECK (position > 0)
);
CREATE INDEX ix_gallery_photos_photo_event ON gallery_photos (photo_id, event_id);
