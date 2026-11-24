package au.com.birrung.tv.signage.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

            // A PowerPoint slot carries no renderable file of its own: the backend has already
            // converted the deck into one PNG per slide under "pptData". Expand those into
            // ordinary image items so the existing download, caching, offline and playback
            // path handles them with no special casing further down.
            if (isPptSlot(s)) {
                List<MediaFile> slides = expandPptSlides(s, i);
                // No slides means nothing we can show. Skip the slot rather than trying to
                // download the .ppt itself, which the player cannot render.
                if (slides.isEmpty()) continue;
                out.add(new Playlist(
                        "slot" + i, "slot" + i, "", false,
                        s.optString("startTime", "00:00"),
                        s.optString("endTime", "24:00"),
                        slides));
                continue;
            }

            JSONObject f = new JSONObject();
            try {
                f.put("id", "slot" + i);
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

    /**
     * Carries pre-rendered PPT slides across a schedule refresh.
     *
     * The login endpoint returns a deck as type "ppt" WITH its "pptData" slides, but the
     * refresh endpoint currently returns the same deck as type "unknown format" with no
     * slides at all. Taking that at face value would replace a working deck with nothing and
     * blank the screen. So when a refreshed slot points at a deck we already hold slides for,
     * those slides are kept.
     *
     * Matching is on the exact deck URL, so a genuinely new or replaced deck (the filename
     * carries a timestamp) never inherits the old slides.
     *
     * @return the incoming array as a string, with slides restored where applicable
     */
    public static String preservePptSlides(JSONArray incoming, String previousJson) {
        try {
            if (incoming == null) return "[]";
            String prev = previousJson == null ? "" : previousJson.trim();
            if (!prev.startsWith("[")) return incoming.toString();

            JSONArray old = new JSONArray(prev);
            java.util.HashMap<String, JSONArray> known = new java.util.HashMap<>();
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o == null) continue;
                JSONArray slides = o.optJSONArray("pptData");
                String url = o.optString("url", "");
                if (slides != null && slides.length() > 0 && !url.isEmpty()) known.put(url, slides);
            }
            if (known.isEmpty()) return incoming.toString();

            for (int i = 0; i < incoming.length(); i++) {
                JSONObject n = incoming.optJSONObject(i);
                if (n == null) continue;
                if (n.optJSONArray("pptData") != null) continue;          // already has slides
                JSONArray slides = known.get(n.optString("url", ""));
                if (slides == null) continue;                              // different deck
                if (!isPptSlot(n)) continue;                               // not a deck at all
                n.put("pptData", slides);
            }
            return incoming.toString();
        } catch (Exception e) {
            return incoming == null ? "[]" : incoming.toString();
        }
    }

    /**
     * A PowerPoint slot, by any of the ways the backend can express it: the pre-rendered slide
     * array, a ppt/pptx type, or a deck URL. Matching only type=="ppt" missed real .pptx decks,
     * which then fell through to the normal file path and were handed to the image loader.
     */
    private static boolean isPptSlot(JSONObject s) {
        if (s.optJSONArray("pptData") != null) return true;
        String t = s.optString("type", "");
        if ("ppt".equalsIgnoreCase(t) || "pptx".equalsIgnoreCase(t)) return true;
        String u = s.optString("url", "").toLowerCase();
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        return u.endsWith(".ppt") || u.endsWith(".pptx");
    }

    /**
     * Turns the backend's "pptData" array into one image item per slide, ordered by slide
     * number and each showing for its own "duration" seconds.
     */
    private static List<MediaFile> expandPptSlides(JSONObject slot, int slotIndex) {
        List<MediaFile> slides = new ArrayList<>();
        JSONArray arr = slot.optJSONArray("pptData");
        if (arr == null) return slides;

        for (int j = 0; j < arr.length(); j++) {
            JSONObject sl = arr.optJSONObject(j);
            if (sl == null) continue;
            String url = sl.optString("url", "");
            if (url.isEmpty()) continue;

            int slideNo = sl.optInt("slide", j + 1);
            JSONObject f = new JSONObject();
            try {
                // Slide URLs live under a per-deck hash directory, so replacing the deck
                // produces new URLs -> new ids -> new local filenames -> automatic
                // re-download, with the old slides evicted as unreferenced.
                f.put("id", "ppt" + slotIndex + "_s" + slideNo);
                f.put("type", "image");   // every slide is a pre-rendered PNG
                f.put("url", url);
                f.put("fileSize", sl.optLong("fileSize", 0));
                f.put("order", slideNo);
                f.put("imageDuration", sl.optInt("duration", 0));  // 0 -> MediaFile's default
                // Deliberately NO "version": the local filename is built from id + version, and
                // the two endpoints do not agree on the slot's version field (the login payload
                // carries "1.0", the refresh payload carries none). That made the SAME slide map
                // to two different filenames -- ppt0_s2_<hash>_1.0.png and ppt0_s2_<hash>_v0.png --
                // so content downloaded under one name was invisible under the other, the player
                // had nothing to show, and each sync evicted what the previous one had fetched.
                // The id above already encodes the slide URL, which contains the per-deck content
                // hash, so it is a complete version marker on its own.
            } catch (Exception ignored) { }
            slides.add(MediaFile.fromJson(f));
        }

        // Play in slide order regardless of the order the array arrives in.
        Collections.sort(slides, new Comparator<MediaFile>() {
            @Override public int compare(MediaFile a, MediaFile b) {
                return Integer.compare(a.order, b.order);
            }
        });
        return slides;
    }
}
