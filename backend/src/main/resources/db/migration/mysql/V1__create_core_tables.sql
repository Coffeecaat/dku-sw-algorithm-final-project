CREATE TABLE cafes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    short_description VARCHAR(500) NOT NULL,
    address VARCHAR(255) NOT NULL,
    road_address VARCHAR(255) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    phone VARCHAR(50),
    opening_hours VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    activated_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE menu_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cafe_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    price DECIMAL(12, 2) NOT NULL,
    description VARCHAR(500) NOT NULL,
    image_original_url VARCHAR(1000) NOT NULL,
    image_thumbnail_url VARCHAR(1000) NOT NULL,
    image_blurhash VARCHAR(255),
    representative BOOLEAN NOT NULL,
    released_at DATE,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cafe_metrics (
    cafe_id BIGINT NOT NULL,
    view_count_daily BIGINT NOT NULL,
    view_count_weekly BIGINT NOT NULL,
    view_count_total BIGINT NOT NULL,
    rating_average DECIMAL(4, 2) NOT NULL,
    rating_count BIGINT NOT NULL,
    rating_score DECIMAL(8, 3) NOT NULL,
    last_viewed_at DATETIME(6),
    hot_score DECIMAL(10, 3) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (cafe_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cafe_status_lat_lng ON cafes (status, latitude, longitude);
CREATE INDEX idx_menu_cafe_representative ON menu_items (cafe_id, representative, status);
CREATE INDEX idx_metric_hot_score ON cafe_metrics (hot_score DESC);
CREATE INDEX idx_metric_rating_score ON cafe_metrics (rating_score DESC);
