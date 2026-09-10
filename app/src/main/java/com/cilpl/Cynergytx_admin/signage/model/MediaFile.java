package com.cilpl.Cynergytx_admin.signage.model;

import android.net.Uri;

import org.json.JSONObject;

/**
 * A single media item (image or video) as described by the backend Playlist API.
 * See BACKEND_REQUIREMENTS.md, Section 1 for the field contract.
 */
public class MediaFile {

    public final String id;          // unique, stable id
    public final String type;        // "image" | "video"
    public final String url;         // direct download url
    public final long fileSize;      // bytes; <= 0 when unknown
    public final String version;     // checksum/version; "" when unknown
    public final int order;          // play sequence within a playlist
    public final int imageDurationSec; // seconds to show an image (default 8)

    public MediaFile(String id, String type, String url, long fileSize,
                     String version, int order, int imageDurationSec) {
        this.id = id == null ? "" : id;
        this.type = type == null ? "" : type;
        this.url = url == null ? "" : url;
        this.fileSize = fileSize;
        this.version = version == null ? "" : version;
        this.order = order;
        this.imageDurationSec = imageDurationSec <= 0 ? 8 : imageDurationSec;
    }

    // Type is detected from the backend "type" field AND, as a fallback, from the file's URL
    // extension. This makes playback robust even when the backend sends a blank/wrong "type"
    // (e.g. a PDF that isn't tagged "pdf" must still be rendered as a PDF, not loaded as an image).

    public boolean isPdf() {
        return "pdf".equalsIgnoreCase(type) || urlExt().equals(".pdf");
    }

    public boolean isVideo() {
        if (isPdf()) return false;
        if ("video".equalsIgnoreCase(type)) return true;
        switch (urlExt()) {
            case ".mp4": case ".mkv": case ".webm": case ".3gp": case ".mov": case ".m4v":
                return true;
            default: return false;
        }
    }

    public boolean isImage() {
        if (isPdf() || isVideo()) return false;   // never misclassify a pdf/video as an image
        if ("image".equalsIgnoreCase(type)) return true;
        switch (urlExt()) {
            case ".jpg": case ".jpeg": case ".png": case ".webp": case ".gif": case ".bmp":
                return true;
            default:
                // Unknown/blank type with no known extension → default to image (legacy behaviour).
                return type.isEmpty() && urlExt().isEmpty();
        }
    }

    /** Lower-cased file extension (including the dot) parsed from the URL, or "" if none. */
    private String urlExt() {
        try {
            String last = Uri.parse(url).getLastPathSegment();
            if (last != null && last.contains(".")) {
                String e = last.substring(last.lastIndexOf('.')).toLowerCase();
                if (e.length() <= 5) return e;
            }
        } catch (Exception ignored) { }
        return "";
    }

    /**
     * Stable local filename that encodes the version. When the backend bumps the
     * version, the filename changes, the old file becomes unreferenced and is evicted,
     * and the new one is downloaded. Presence of this exact file == "up to date".
     */
    public String localFileName() {
        String safeId = sanitize(id.isEmpty() ? ("f" + Integer.toHexString(url.hashCode())) : id);
        String safeVer = version.isEmpty() ? "v0" : sanitize(version);
        return safeId + "_" + safeVer + extension();
    }

    public String extension() {
        try {
            String last = Uri.parse(url).getLastPathSegment();
            if (last != null && last.contains(".")) {
                String ext = last.substring(last.lastIndexOf('.'));
                if (ext.length() <= 5) return ext.toLowerCase();
            }
        } catch (Exception ignored) { }
        if (isVideo()) return ".mp4";
        if (isPdf()) return ".pdf";
        return ".jpg";
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static MediaFile fromJson(JSONObject o) {
        return new MediaFile(
                o.optString("id", ""),
                o.optString("type", ""),
                o.optString("url", ""),
                o.optLong("fileSize", 0),
                o.optString("version", o.optString("checksum", "")),
                o.optInt("order", 0),
                o.optInt("imageDuration", 8)
        );
    }
}
