package au.com.birrung.tv.signage.model;

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

    /**
     * A YouTube link (watch / youtu.be / embed / shorts). YouTube cannot be downloaded or cached —
     * it is streamed through the YouTube IFrame player, so it is handled separately from normal
     * media files (no local storage, no download, requires internet).
     */
    public boolean isYouTube() {
        String u = url.toLowerCase();
        return u.contains("youtube.com/") || u.contains("youtu.be/");
    }

    /** The YouTube playlist id (…list=PLxxx) when the link is a playlist, else "". */
    public String youTubePlaylistId() {
        try {
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("[?&]list=([A-Za-z0-9_-]+)").matcher(url);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) { }
        return "";
    }

    /** The YouTube video id parsed from the URL, or "" when it can't be determined. */
    public String youTubeId() {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "(?:youtu\\.be/|youtube\\.com/(?:embed/|shorts/|v/|live/|watch\\?(?:.*&)?v=))([A-Za-z0-9_-]{6,})")
                    .matcher(url);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) { }
        return "";
    }

    /**
     * Web content shown in a WebView rather than downloaded: a YouTube link, or anything the
     * backend marks as {@code "type":"url"}. These are streamed and need internet.
     */
    public boolean isWeb() {
        return isYouTube() || "url".equalsIgnoreCase(type) || "web".equalsIgnoreCase(type);
    }

    /**
     * A PowerPoint deck. The app never renders these itself: the backend converts each deck
     * into PNG slides and sends them in "pptData", which {@code ContentSchedule} expands into
     * ordinary image items. This exists so a raw .ppt URL can be recognised and skipped rather
     * than being mistaken for an image and handed to the image loader.
     */
    public boolean isPpt() {
        if (isWeb()) return false;
        String e = urlExt();
        return "ppt".equalsIgnoreCase(type) || "pptx".equalsIgnoreCase(type)
                || e.equals(".ppt") || e.equals(".pptx");
    }

    public boolean isPdf() {
        if (isWeb()) return false;
        return "pdf".equalsIgnoreCase(type) || urlExt().equals(".pdf");
    }

    public boolean isVideo() {
        if (isWeb() || isPdf() || isPpt()) return false;
        if ("video".equalsIgnoreCase(type)) return true;
        switch (urlExt()) {
            case ".mp4": case ".mkv": case ".webm": case ".3gp": case ".mov": case ".m4v":
                return true;
            default: return false;
        }
    }

    public boolean isImage() {
        if (isWeb() || isPdf() || isPpt() || isVideo()) return false; // never misclassify these
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
        String safeId = sanitize(id.isEmpty() ? "f" : id);
        return safeId + "_" + urlFingerprint() + extension();
    }

    /**
     * Short, stable fingerprint of the content URL. This replaces the backend's "version" in the
     * local filename, and it is what makes caching reliable.
     *
     * The login and refresh endpoints do not agree about "version" -- one sends "1.0", the other
     * sends nothing -- so building the name from it meant the SAME file mapped to two different
     * names (…_1.0.mp4 and …_v0.mp4). The app downloaded under one name, looked for the other,
     * found nothing and showed a stuck loader; worse, each sync evicted the other's files as
     * unreferenced, so the two payloads kept deleting each other's downloads forever.
     *
     * The URL identifies the content and this backend already changes it whenever content is
     * replaced (screenFile&lt;timestamp&gt;.pptx, ppt_slides/&lt;deck hash&gt;/…), so it versions
     * correctly on its own. A replaced file that somehow kept its URL is still caught by the
     * fileSize check in isDownloaded().
     */
    private String urlFingerprint() {
        return Integer.toHexString(url.hashCode());
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
