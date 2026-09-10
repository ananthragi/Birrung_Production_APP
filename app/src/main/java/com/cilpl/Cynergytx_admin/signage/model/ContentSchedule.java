package com.cilpl.Cynergytx_admin.signage.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The full content assigned to a screen. Parses the NEW backend contract
 * (see BACKEND_REQUIREMENTS.md) and also gracefully adapts the CURRENT legacy
 * "data" slot array, so this module works before and after the backend upgrade.
 */
public class ContentSchedule {

    public final String companyName;
    public final String timezone;
    public final String mqttUrl;
    public final List<Playlist> playlists;

    public ContentSchedule(String companyName, String timezone, String mqttUrl, List<Playlist> playlists) {
        this.companyName = companyName;
        this.timezone = timezone;
        this.mqttUrl = mqttUrl;
        this.playlists = playlists;
    }

    /**
     * Active playlist for the current time.
     * A SPECIFIC timed session (e.g. Breakfast/Lunch/Dinner) ALWAYS wins over a full-day /
     * "forever" slot when both are active — this is what makes sessions actually switch
     * instead of the full-day slot always taking over.
     * Order: active timed session → active full-day slot → explicit default → first → null.
     */
    public Playlist getActivePlaylist() {
        int now = Playlist.nowSeconds();
        Playlist activeSpecific = null;  // a timed session active right now
        Playlist activeFullDay = null;   // a full-day / "forever" slot active right now
        Playlist def = null;             // an explicit default, if the backend marks one

        for (Playlist p : playlists) {
            if (p.isDefault) { if (def == null) def = p; continue; }
            if (!p.isActiveAt(now)) continue;
            if (p.isFullDay()) { if (activeFullDay == null) activeFullDay = p; }
            else if (activeSpecific == null) activeSpecific = p;
        }

        if (activeSpecific != null) return activeSpecific;
        if (activeFullDay != null) return activeFullDay;
        if (def != null) return def;
        // Outside every configured session (and no full-day/default) → nothing should play.
        // The caller shows the "No content configured at this time" screen.
        return null;
    }

    /** True when the schedule has content but NONE is scheduled for the current time. */
    public boolean isOutsideSession() {
        return !playlists.isEmpty() && getActivePlaylist() == null;
    }

    public List<MediaFile> allFiles() {
        List<MediaFile> all = new ArrayList<>();
        for (Playlist p : playlists) all.addAll(p.files);
        return all;
    }

    /** A remote URL from the active playlist, used to measure real internet throughput. */
    public String getActiveContentUrl() {
        Playlist p = getActivePlaylist();
        if (p != null && !p.files.isEmpty()) return p.files.get(0).url;
        return null;
    }

    public static ContentSchedule parse(String json, String fallbackCompany, String fallbackMqtt) {
        List<Playlist> playlists = new ArrayList<>();
        String company = fallbackCompany;
        String tz = "";
        String mqtt = fallbackMqtt;

        try {
            String trimmed = json == null ? "" : json.trim();

            if (trimmed.startsWith("{")) {
                // New contract: { companyName, timezone, mqttUrl, playlists:[...] }
                JSONObject root = new JSONObject(trimmed);
                company = root.optString("companyName", fallbackCompany);
                tz = root.optString("timezone", "");
                mqtt = root.optString("mqttUrl", root.optString("MQTT_URL", fallbackMqtt));

                JSONArray pls = root.optJSONArray("playlists");
                if (pls != null) {
                    for (int i = 0; i < pls.length(); i++) {
                        JSONObject p = pls.optJSONObject(i);
                        if (p != null) playlists.add(Playlist.fromJson(p));
                    }
                } else {
                    JSONArray data = root.optJSONArray("data");
                    if (data != null) playlists.addAll(adaptLegacySlots(data));
                }
            } else if (trimmed.startsWith("[")) {
                // Legacy contract: [ { type, url, startTime, endTime }, ... ]
                playlists.addAll(adaptLegacySlots(new JSONArray(trimmed)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ContentSchedule(company, tz, mqtt, playlists);
    }

    /** Turn each legacy time slot into a single-file playlist so it still plays locally. */
    private static List<Playlist> adaptLegacySlots(JSONArray data) {
        List<Playlist> out = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject s = data.optJSONObject(i);
            if (s == null) continue;

            JSONObject f = new JSONObject();
            try {
                f.put("id", "slot" + i + "_" + Integer.toHexString(s.optString("url", "").hashCode()));
                f.put("type", s.optString("type", ""));
                f.put("url", s.optString("url", ""));
                f.put("order", 1);
                // Carry the backend's fileSize through so the app can verify a download is complete
                // (reject/resume truncated files) and pre-check storage before downloading.
                f.put("fileSize", s.optLong("fileSize", 0));
                // Version/checksum if the backend provides it (changes the local filename → re-download).
                f.put("version", s.optString("version", s.optString("checksum", "")));
            } catch (Exception ignored) { }

            List<MediaFile> files = new ArrayList<>();
            files.add(MediaFile.fromJson(f));

            out.add(new Playlist(
                    "slot" + i, "slot" + i, "", false,
                    s.optString("startTime", "00:00"),
                    s.optString("endTime", "24:00"),
                    files));
        }
        return out;
    }
}
