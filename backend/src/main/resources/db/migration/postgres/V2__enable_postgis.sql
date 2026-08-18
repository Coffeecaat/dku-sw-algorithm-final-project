CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE cafes
    ADD COLUMN IF NOT EXISTS location geography(Point, 4326);

CREATE INDEX IF NOT EXISTS idx_cafe_location_gist ON cafes USING GIST (location);
