package com.photoexhibition.service;

/** Small presentation helper: task records never expose the server's storage root. */
public final class TaskFileService {
    private TaskFileService() { }

    public static String fileKey(String raw) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(String.valueOf(raw).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static String relativePath(UserPathService paths, String raw) {
        return relativePath(paths, raw, null);
    }

    public static String relativePath(UserPathService paths, String raw, Long ownerUserId) {
        if (raw == null || raw.isBlank()) return raw;
        String value = paths == null ? null : paths.extractTenantRelativePhotoPath(raw);
        if (value != null && !value.equals(raw)) return value.replace('\\', '/');
        String normalized = raw.replace('\\', '/');
        if (ownerUserId != null) {
            String marker = "/" + ownerUserId + "/";
            int markerAt = normalized.indexOf(marker);
            if (markerAt >= 0) {
                return normalized.substring(markerAt + marker.length() - 1);
            }
            if (normalized.equals("/" + ownerUserId)) return "/";
        }
        // Unknown absolute roots cannot safely be displayed as relative paths.
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*") || normalized.contains("://")) {
            return normalized.substring(normalized.lastIndexOf('/') + 1);
        }
        return normalized;
    }
}
