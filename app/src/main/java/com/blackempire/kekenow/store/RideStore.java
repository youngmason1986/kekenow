package com.blackempire.kekenow.store;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.blackempire.kekenow.model.RideRecord;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * File-system CRUD for {@link RideRecord} objects.
 *
 * Files live at: {@code {Context.getFilesDir()}/rides/<uuid>.json}
 * Writes are atomic: write to .tmp, then rename.
 * FileObserver in RiderFragment monitors the rides/ directory.
 */
public class RideStore {

    private static final String TAG = "KekeNow";
    private static final String RIDES_DIR = "rides";

    private final File ridesDir;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public RideStore(Context context) {
        ridesDir = new File(context.getFilesDir(), RIDES_DIR);
        if (!ridesDir.exists()) {
            boolean created = ridesDir.mkdirs();
            if (!created) {
                Log.e(TAG, "RideStore: could not create rides directory");
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Write
    // ------------------------------------------------------------------ //

    public interface WriteCallback {
        void onSuccess(RideRecord record);
        void onError(Exception e);
    }

    /** Serialises the record and writes it atomically. */
    public void create(RideRecord record, WriteCallback callback) {
        executor.execute(() -> {
            try {
                writeAtomic(record);
                mainHandler.post(() -> callback.onSuccess(record));
            } catch (IOException | JSONException e) {
                Log.e(TAG, "RideStore.create failed for " + record.id, e);
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /** Updates the status field of an existing ride file. */
    public void updateStatus(String id, String status, WriteCallback callback) {
        executor.execute(() -> {
            try {
                File file = new File(ridesDir, id + ".json");
                if (!file.exists()) {
                    throw new IOException("Ride file not found: " + id);
                }
                RideRecord record = readFile(file);
                if (record == null) {
                    throw new IOException("Could not parse ride file: " + id);
                }
                record.status = status;
                writeAtomic(record);
                mainHandler.post(() -> callback.onSuccess(record));
            } catch (IOException | JSONException e) {
                Log.e(TAG, "RideStore.updateStatus failed for " + id, e);
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /** Rewrites an existing ride file in full (e.g. to attach rider details on accept). */
    public void update(RideRecord record, WriteCallback callback) {
        executor.execute(() -> {
            try {
                File file = new File(ridesDir, record.id + ".json");
                if (!file.exists()) {
                    throw new IOException("Ride file not found: " + record.id);
                }
                writeAtomic(record);
                mainHandler.post(() -> callback.onSuccess(record));
            } catch (IOException | JSONException e) {
                Log.e(TAG, "RideStore.update failed for " + record.id, e);
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  Read
    // ------------------------------------------------------------------ //

    public interface ListCallback {
        void onResult(List<RideRecord> records);
    }

    /** Returns all rides (any status), newest-first, via callback on the main thread. */
    public void listAll(ListCallback callback) {
        executor.execute(() -> {
            List<RideRecord> records = readAll();
            mainHandler.post(() -> callback.onResult(records));
        });
    }

    /** Returns only pending rides, newest-first. */
    public void listPending(ListCallback callback) {
        executor.execute(() -> {
            List<RideRecord> all = readAll();
            List<RideRecord> pending = new ArrayList<>();
            for (RideRecord r : all) {
                if (RideRecord.STATUS_PENDING.equals(r.status)) {
                    pending.add(r);
                }
            }
            mainHandler.post(() -> callback.onResult(pending));
        });
    }

    public interface GetCallback {
        void onResult(RideRecord record);     // null if not found / parse error
    }

    public void get(String id, GetCallback callback) {
        executor.execute(() -> {
            File file = new File(ridesDir, id + ".json");
            RideRecord record = file.exists() ? readFile(file) : null;
            mainHandler.post(() -> callback.onResult(record));
        });
    }

    // ------------------------------------------------------------------ //
    //  Synchronous helpers (always called from executor thread)
    // ------------------------------------------------------------------ //

    private List<RideRecord> readAll() {
        File[] files = ridesDir.listFiles(
            (dir, name) -> name.endsWith(".json"));
        List<RideRecord> records = new ArrayList<>();
        if (files == null) return records;

        for (File f : files) {
            RideRecord r = readFile(f);
            if (r != null) {
                records.add(r);
            }
        }
        // Sort newest-first by createdAt string (ISO-8601 sorts lexicographically)
        Collections.sort(records, (a, b) -> {
            if (a.createdAt == null) return 1;
            if (b.createdAt == null) return -1;
            return b.createdAt.compareTo(a.createdAt);
        });
        return records;
    }

    private RideRecord readFile(File file) {
        try (FileReader reader = new FileReader(file)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return RideRecord.fromJson(new JSONObject(sb.toString()));
        } catch (IOException | JSONException e) {
            Log.e(TAG, "RideStore: failed to parse " + file.getName(), e);
            return null;
        }
    }

    private void writeAtomic(RideRecord record) throws IOException, JSONException {
        String json = record.toJson().toString(2);
        File tmp = new File(ridesDir, record.id + ".tmp");
        File target = new File(ridesDir, record.id + ".json");

        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(json.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
        if (!tmp.renameTo(target)) {
            throw new IOException("Atomic rename failed for " + record.id);
        }
    }

    /** Returns the absolute path to the rides directory (for FileObserver). */
    public String getRidesDirectory() {
        return ridesDir.getAbsolutePath();
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
