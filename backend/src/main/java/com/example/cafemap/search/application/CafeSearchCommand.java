package com.example.cafemap.search.application;

public record CafeSearchCommand(
        String query,
        String normalizedQuery,
        String compactNormalizedQuery,
        Double latitude,
        Double longitude,
        Double radiusKm,
        Double swLat,
        Double swLng,
        Double neLat,
        Double neLng,
        int limit) {

    public boolean hasBounds() {
        return swLat != null && swLng != null && neLat != null && neLng != null;
    }

    public double minLatitude() {
        return Math.min(swLat, neLat);
    }

    public double maxLatitude() {
        return Math.max(swLat, neLat);
    }

    public double minLongitude() {
        return Math.min(swLng, neLng);
    }

    public double maxLongitude() {
        return Math.max(swLng, neLng);
    }
}
