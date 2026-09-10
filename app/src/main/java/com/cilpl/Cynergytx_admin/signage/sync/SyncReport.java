package com.cilpl.Cynergytx_admin.signage.sync;

import com.cilpl.Cynergytx_admin.signage.model.MediaFile;

import java.util.ArrayList;
import java.util.List;

/** Result of a content sync pass — ready to be reported to the backend dashboard. */
public class SyncReport {

    public int alreadyPresent = 0;
    public int downloaded = 0;
    public final List<MediaFile> failed = new ArrayList<>();
    public final List<MediaFile> shortfall = new ArrayList<>(); // could not fit in 16GB
    public long usageBytes = 0;
    public long budgetBytes = 0;

    public boolean hasShortfall() { return !shortfall.isEmpty(); }

    public String summary() {
        return "present=" + alreadyPresent
                + " downloaded=" + downloaded
                + " failed=" + failed.size()
                + " shortfall=" + shortfall.size()
                + " usageMB=" + (usageBytes / (1024 * 1024))
                + " freeBudgetMB=" + (budgetBytes / (1024 * 1024));
    }
}
