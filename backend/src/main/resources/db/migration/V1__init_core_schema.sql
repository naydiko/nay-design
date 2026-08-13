-- =====================================================================
-- V1__init_core_schema.sql
-- Core schema for nay-design (PostgreSQL 16)
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------
-- Shared trigger: keep updated_at current on every row update
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- app_user
-- =====================================================================
CREATE TABLE app_user (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email         varchar(320) NOT NULL,
    display_name  varchar(150) NOT NULL,
    first_name    varchar(100),
    last_name     varchar(100),
    phone_number  varchar(40),
    role          varchar(30)  NOT NULL,
    status        varchar(30)  NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uk_app_user_email UNIQUE (email)
);

CREATE INDEX idx_app_user_status ON app_user (status);

CREATE TRIGGER trg_app_user_updated_at
    BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- project
-- =====================================================================
CREATE TABLE project (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id     uuid          NOT NULL,
    name         varchar(160)  NOT NULL,
    description  text,
    project_type varchar(30)   NOT NULL,
    status       varchar(30)   NOT NULL,
    budget_min   numeric(12,2) CHECK (budget_min IS NULL OR budget_min >= 0),
    budget_max   numeric(12,2) CHECK (budget_max IS NULL OR budget_max >= 0),
    currency     varchar(3),
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_project_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_project_budget_range CHECK (budget_min IS NULL OR budget_max IS NULL OR budget_min <= budget_max)
);

CREATE INDEX idx_project_owner_id ON project (owner_id);
CREATE INDEX idx_project_status   ON project (status);

CREATE TRIGGER trg_project_updated_at
    BEFORE UPDATE ON project
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- level
-- =====================================================================
CREATE TABLE level (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   uuid          NOT NULL,
    name         varchar(120)  NOT NULL,
    elevation_mm numeric(10,2) NOT NULL DEFAULT 0,
    order_index  integer       NOT NULL DEFAULT 0,
    is_visible   boolean       NOT NULL DEFAULT true,

    min_x_mm     numeric(12,2),
    min_y_mm     numeric(12,2),
    max_x_mm     numeric(12,2),
    max_y_mm     numeric(12,2),

    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_level_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT ck_level_bbox CHECK (
        (min_x_mm IS NULL AND min_y_mm IS NULL AND max_x_mm IS NULL AND max_y_mm IS NULL)
        OR (min_x_mm <= max_x_mm AND min_y_mm <= max_y_mm)
    )
);

CREATE INDEX idx_level_project_id ON level (project_id);

CREATE TRIGGER trg_level_updated_at
    BEFORE UPDATE ON level
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- node
-- =====================================================================
CREATE TABLE node (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    level_id   uuid          NOT NULL,
    x_mm       numeric(12,4) NOT NULL,
    y_mm       numeric(12,4) NOT NULL,
    z_mm       numeric(12,4) NOT NULL DEFAULT 0,

    created_at timestamptz   NOT NULL DEFAULT now(),
    updated_at timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_node_level FOREIGN KEY (level_id) REFERENCES level (id) ON DELETE CASCADE
);

CREATE INDEX idx_node_level_id ON node (level_id);

CREATE TRIGGER trg_node_updated_at
    BEFORE UPDATE ON node
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- wall
-- =====================================================================
CREATE TABLE wall (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    level_id      uuid          NOT NULL,
    start_node_id uuid          NOT NULL,
    end_node_id   uuid          NOT NULL,
    thickness_mm  numeric(10,2) NOT NULL,
    height_mm     numeric(10,2) NOT NULL,
    kind          varchar(30)   NOT NULL,

    created_at    timestamptz   NOT NULL DEFAULT now(),
    updated_at    timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_wall_level      FOREIGN KEY (level_id) REFERENCES level (id) ON DELETE CASCADE,
    CONSTRAINT fk_wall_start_node FOREIGN KEY (start_node_id) REFERENCES node (id) ON DELETE RESTRICT,
    CONSTRAINT fk_wall_end_node   FOREIGN KEY (end_node_id) REFERENCES node (id) ON DELETE RESTRICT,
    CONSTRAINT ck_wall_distinct_nodes CHECK (start_node_id <> end_node_id),
    CONSTRAINT ck_wall_thickness_positive CHECK (thickness_mm > 0),
    CONSTRAINT ck_wall_height_positive    CHECK (height_mm > 0)
);

CREATE INDEX idx_wall_level_id      ON wall (level_id);
CREATE INDEX idx_wall_start_node_id ON wall (start_node_id);
CREATE INDEX idx_wall_end_node_id   ON wall (end_node_id);

CREATE TRIGGER trg_wall_updated_at
    BEFORE UPDATE ON wall
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- opening (doors, windows, archways within a wall)
-- =====================================================================
CREATE TABLE opening (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    wall_id               uuid          NOT NULL,
    type                  varchar(30)   NOT NULL,
    offset_from_start_mm  numeric(10,2) NOT NULL,
    width_mm              numeric(10,2) NOT NULL,
    height_mm             numeric(10,2) NOT NULL,
    sill_height_mm        numeric(10,2) NOT NULL DEFAULT 0,
    direction             varchar(10),
    swing                 varchar(10),

    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_opening_wall FOREIGN KEY (wall_id) REFERENCES wall (id) ON DELETE CASCADE,
    CONSTRAINT ck_opening_offset_nonneg   CHECK (offset_from_start_mm >= 0),
    CONSTRAINT ck_opening_width_positive  CHECK (width_mm > 0),
    CONSTRAINT ck_opening_height_positive CHECK (height_mm > 0),
    CONSTRAINT ck_opening_sill_nonneg     CHECK (sill_height_mm >= 0)
);

CREATE INDEX idx_opening_wall_id ON opening (wall_id);

CREATE TRIGGER trg_opening_updated_at
    BEFORE UPDATE ON opening
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- room
-- =====================================================================
CREATE TABLE room (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    level_id          uuid         NOT NULL,
    name              varchar(160) NOT NULL,
    type              varchar(40)  NOT NULL,

    floor_finish      varchar(120),
    wall_finish       varchar(120),
    ceiling_finish    varchar(120),

    ceiling_type      varchar(60),
    ceiling_height_mm numeric(10,2),

    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_room_level FOREIGN KEY (level_id) REFERENCES level (id) ON DELETE CASCADE,
    CONSTRAINT ck_room_ceiling_height_positive CHECK (ceiling_height_mm IS NULL OR ceiling_height_mm > 0)
);

CREATE INDEX idx_room_level_id ON room (level_id);
CREATE INDEX idx_room_type     ON room (type);

CREATE TRIGGER trg_room_updated_at
    BEFORE UPDATE ON room
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- room_wall (many-to-many join: a wall can border two rooms)
-- =====================================================================
CREATE TABLE room_wall (
    room_id uuid NOT NULL,
    wall_id uuid NOT NULL,

    PRIMARY KEY (room_id, wall_id),

    CONSTRAINT fk_room_wall_room FOREIGN KEY (room_id) REFERENCES room (id) ON DELETE CASCADE,
    CONSTRAINT fk_room_wall_wall FOREIGN KEY (wall_id) REFERENCES wall (id) ON DELETE CASCADE
);

CREATE INDEX idx_room_wall_wall_id ON room_wall (wall_id);

-- =====================================================================
-- vendor
-- =====================================================================
CREATE TABLE vendor (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name       varchar(160)  NOT NULL,
    country    varchar(100),
    website    varchar(2048),
    logo_url   varchar(2048),
    status     varchar(30)   NOT NULL,

    created_at timestamptz   NOT NULL DEFAULT now(),
    updated_at timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uk_vendor_name UNIQUE (name)
);

CREATE INDEX idx_vendor_status ON vendor (status);

CREATE TRIGGER trg_vendor_updated_at
    BEFORE UPDATE ON vendor
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- product
-- =====================================================================
CREATE TABLE product (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_id      uuid          NOT NULL,
    external_id    varchar(120),
    name           varchar(180)  NOT NULL,
    sku            varchar(100),
    category       varchar(120)  NOT NULL,
    collection     varchar(160),
    style          varchar(120),
    material       varchar(120),
    color          varchar(120),

    width_mm       numeric(10,2) CHECK (width_mm IS NULL OR width_mm > 0),
    depth_mm       numeric(10,2) CHECK (depth_mm IS NULL OR depth_mm > 0),
    height_mm      numeric(10,2) CHECK (height_mm IS NULL OR height_mm > 0),
    weight_grams   numeric(10,2) CHECK (weight_grams IS NULL OR weight_grams >= 0),

    price_amount   numeric(12,2) CHECK (price_amount IS NULL OR price_amount >= 0),
    price_currency varchar(3),

    status         varchar(30)   NOT NULL,

    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_product_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id) ON DELETE RESTRICT,
    CONSTRAINT uk_product_vendor_external UNIQUE (vendor_id, external_id)
);

CREATE INDEX idx_product_vendor_id ON product (vendor_id);
CREATE INDEX idx_product_category  ON product (category);
CREATE INDEX idx_product_status    ON product (status);

CREATE TRIGGER trg_product_updated_at
    BEFORE UPDATE ON product
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- product_media
-- =====================================================================
CREATE TABLE product_media (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  uuid          NOT NULL,
    url         varchar(2048) NOT NULL,
    type        varchar(30)   NOT NULL,
    alt_text    varchar(255),
    order_index integer       NOT NULL DEFAULT 0,

    created_at  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_product_media_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE
);

CREATE INDEX idx_product_media_product_id ON product_media (product_id);

-- =====================================================================
-- furniture_placement
-- =====================================================================
CREATE TABLE furniture_placement (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id        uuid          NOT NULL,
    product_id     uuid          NOT NULL,

    x_mm           numeric(12,4) NOT NULL DEFAULT 0,
    y_mm           numeric(12,4) NOT NULL DEFAULT 0,
    z_mm           numeric(12,4) NOT NULL DEFAULT 0,

    rotation_angle numeric(6,2)  NOT NULL DEFAULT 0,
    scale          numeric(8,4)  NOT NULL DEFAULT 1,

    is_locked      boolean       NOT NULL DEFAULT false,

    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_furniture_placement_room    FOREIGN KEY (room_id) REFERENCES room (id) ON DELETE CASCADE,
    CONSTRAINT fk_furniture_placement_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE RESTRICT,
    CONSTRAINT ck_furniture_placement_rotation CHECK (rotation_angle >= 0 AND rotation_angle < 360),
    CONSTRAINT ck_furniture_placement_scale_positive CHECK (scale > 0)
);

CREATE INDEX idx_furniture_placement_room_id    ON furniture_placement (room_id);
CREATE INDEX idx_furniture_placement_product_id ON furniture_placement (product_id);

CREATE TRIGGER trg_furniture_placement_updated_at
    BEFORE UPDATE ON furniture_placement
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

