package au.com.birrung.tv.Activites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ListenableWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ContentFetchWorker extends Worker {
    public ContentFetchWorker(@NonNull Context context,
                              @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Call your API logic here
        AndroidTVActivity activity = (AndroidTVActivity) getApplicationContext();
        if (activity != null) {
            activity.fetchAndDisplayContent();
        }
        return Result.success();
    }
}

