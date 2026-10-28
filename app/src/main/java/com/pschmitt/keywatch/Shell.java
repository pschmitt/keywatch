// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from Cast Fix (https://github.com/thayford/castfix), MIT, Copyright (c) 2026 thayford; see NOTICE.
package com.pschmitt.keywatch;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.muntashirakon.adb.AdbStream;

/**
 * adb shell on this TV over loopback (the TV's network adb on port 5555), with a time limit on every
 * command: libadb's open() can wait forever, or miss adbd's answer over loopback, and closing the connection
 * is what wakes a stuck thread. A stalled command is retried on a fresh connection; every command used here
 * is safe to repeat.
 */
final class Shell implements Closeable {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 5555;
    // The first connection waits for someone to tick "Always allow" on the TV.
    private static final long CONNECT_TIMEOUT_S = 120;
    private static final long COMMAND_TIMEOUT_S = 10;
    private static final int ATTEMPTS = 3;

    private final Context context;
    private final KeyLog log;
    private final ExecutorService pool = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "keywatch-adb");
        thread.setDaemon(true);
        return thread;
    });
    private AdbManager adb;

    Shell(Context context, KeyLog log) {
        this.context = context;
        this.log = log;
    }

    void connect() throws Exception {
        close();
        AdbManager manager = new AdbManager(context);
        Future<Boolean> attempt = pool.submit(() -> manager.connect(HOST, PORT));
        try {
            if (!attempt.get(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)) throw new IOException("adb refused the connection");
        } catch (Exception e) {
            attempt.cancel(true);
            closeQuietly(manager);
            throw e;
        }
        adb = manager;
        log.add("adb connected");
    }

    /** Runs a shell command and returns its output, reconnecting and retrying if it stalls or fails. */
    String run(String command) throws Exception {
        for (int attempt = 1; ; attempt++) {
            if (adb == null) connect();
            AdbManager current = adb;
            Future<String> result = pool.submit(() -> exec(current, command));
            try {
                return result.get(COMMAND_TIMEOUT_S, TimeUnit.SECONDS);
            } catch (TimeoutException | ExecutionException e) {
                result.cancel(true);
                close();
                if (attempt == ATTEMPTS) throw new IOException("adb kept failing: " + e);
                log.add("adb retry " + attempt + "/" + ATTEMPTS + ": " + (e instanceof TimeoutException ? "no answer" : e.getCause()));
            }
        }
    }

    @Override
    public void close() {
        AdbManager manager = adb;
        adb = null;
        closeQuietly(manager);
    }

    private static String exec(AdbManager adb, String command) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (AdbStream stream = adb.openStream("shell:" + command);
             InputStream in = stream.openInputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            try {
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            } catch (IOException e) {
                // libadb ends a finished command's stream this way
                if (!"Stream closed.".equals(e.getMessage())) throw e;
            }
        }
        return out.toString(StandardCharsets.UTF_8.name()).trim();
    }

    /** Closes off the calling thread: closing is what wakes a thread stuck in open(). */
    private void closeQuietly(AdbManager manager) {
        if (manager == null) return;
        Future<?> closing = pool.submit(() -> {
            manager.close();
            return null;
        });
        try {
            closing.get(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // abandoned; the socket goes with the process
        }
    }
}
