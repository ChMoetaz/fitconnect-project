package com.fitconnect.backend.util;

/**
 * Geographic helpers. Kept in one place so the great-circle distance is not reimplemented per
 * service (both CommunityService and CoachRecommendationService use it for their /nearby filter).
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {
    }

    /**
     * Great-circle distance between two points in kilometres (Haversine formula). Accurate enough
     * for "coaches/groups within X km" without needing a PostGIS extension.
     */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.asin(Math.sqrt(a));
    }
}
