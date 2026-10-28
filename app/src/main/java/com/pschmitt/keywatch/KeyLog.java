// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from Cast Fix (https://github.com/thayford/castfix), MIT, Copyright (c) 2026 thayford; see NOTICE.
package com.pschmitt.keywatch;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Log (files/keywatch.log, also logcat tag KeyWatch), shown on the app's screen. */
final class KeyLog {
    private static final String TAG = "KeyWatch";
    private static final int KEEP_LINES = 200;

    private final File file;

    KeyLog(Context context) {
        file = new File(context.getFilesDir(), "keywatch.log");
    }

    synchronized void add(String line) {
        Log.i(TAG, line);
        List<String> lines = tail(KEEP_LINES - 1);
        lines.add(new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date()) + "  " + line);
        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.w(TAG, "could not write the log", e);
        }
    }

    synchronized List<String> tail(int count) {
        List<String> lines = new ArrayList<>();
        if (file.exists()) {
            try {
                lines.addAll(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.w(TAG, "could not read the log", e);
            }
        }
        return new ArrayList<>(lines.subList(Math.max(0, lines.size() - count), lines.size()));
    }
}
