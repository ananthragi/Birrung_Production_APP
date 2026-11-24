package au.com.birrung.tv.signage.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A playlist assigned to a screen for a time window.
 * Time-slot matching mirrors the existing logic (full-day + midnight-cross support).
 */
public class Playlist {

    public final String id;
    public final String name;
    public final String version;
    public final boolean isDefault;
    public final String startTime;  // "HH:mm"
    public final String endTime;    // "HH:mm"
    public final List<MediaFile> files;

    public Playlist(String id, String name, String version, boolean isDefault,
                    String startTime, String endTime, List<MediaFile> files) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.version = version == null ? "" : version;
        this.isDefault = isDefault;
        this.startTime = startTime;
        this.endTime = endTime;
        this.files = files;
    }

    /** A slot that covers the whole day (a "forever"/default slot, not a timed session). */
    public boolean isFullDay() {
        if (!"00:00".equals(startTime) && !"00:00:00".equals(startTime)) return false;
        int end = toSeconds(endTime);
        return end >= 24 * 3600 - 1; // 23:59:59 or later (24:00, 24:00:59, ...)
    }

    public boolean isActiveAt(int nowSeconds) {
        int start = toSeconds(startTime);
        int end = toSeconds(endTime);
        if (start < 0 || end < 0) return false;

        // Full day
        if ("00:00".equals(startTime) && ("24:00".equals(endTime) || "23:59:59".equals(endTime)))
            return true;

        // Normal slot
        if (start <= end) return nowSeconds >= start && nowSeconds <= end;

        // Crosses midnight
        return nowSeconds >= start || nowSeconds <= end;
    }

    public static int toSeconds(String t) {
        try {
            if ("24:00".equals(t)) return 24 * 3600;
            String[] p = t.split(":");
            int h = Integer.parseInt(p[0].trim());
            int m = Integer.parseInt(p[1].trim());
            int s = p.length >= 3 ? Integer.parseInt(p[2].trim()) : 0;
            return h * 3600 + m * 60 + s;
        } catch (Exception e) {
            return -1;
        }
    }

    public static int nowSeconds() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 3600
                + c.get(Calendar.MINUTE) * 60
                + c.get(Calendar.SECOND);
    }

    public static Playlist fromJson(JSONObject o) {
        List<MediaFile> files = new ArrayList<>();
        JSONArray arr = o.optJSONArray("files");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject f = arr.optJSONObject(i);
                if (f != null) files.add(MediaFile.fromJson(f));
            }
        }
        Collections.sort(files, new Comparator<MediaFile>() {
            @Override public int compare(MediaFile a, MediaFile b) {
                return Integer.compare(a.order, b.order);
            }
        });
        return new Playlist(
                o.optString("id", ""),
                o.optString("name", ""),
                o.optString("version", ""),
                o.optBoolean("isDefault", false),
                o.optString("startTime", "00:00"),
                o.optString("endTime", "24:00"),
                files
        );
    }
}
