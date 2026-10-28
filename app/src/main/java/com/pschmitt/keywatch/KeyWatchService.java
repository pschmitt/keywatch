// SPDX-License-Identifier: GPL-3.0-or-later
package com.pschmitt.keywatch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the key watcher running on the TV: connects to the TV's own adb, installs the script, starts it, and
 * checks every minute that it is still alive. A foreground service so it is not cut short after boot.
 */
public class KeyWatchService extends Service {
    private static final String CHANNEL = "keywatch";
    private static final long CHECK_MS = 60_000;
    private static final long RETRY_MS = 15_000;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "KeyWatch", NotificationManager.IMPORTANCE_MIN));
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_keywatch)
                .setContentTitle("KeyWatch")
                .setContentText("Remote buttons are handled")
                .setOngoing(true)
                .build();
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        if (RUNNING.compareAndSet(false, true)) {
            Thread loop = new Thread(this::loop, "keywatch");
            loop.setDaemon(true);
            loop.start();
        }
        return START_STICKY;
    }

    private void loop() {
        KeyLog log = new KeyLog(this);
        Shell shell = new Shell(this, log);
        log.add("service started");
        while (true) {
            long wait = CHECK_MS;
            try {
                installScript(shell, log);
                String state = shell.run("p=$(cat " + Script.PID + " 2>/dev/null); "
                        + "if grep -q keywatch /proc/$p/cmdline 2>/dev/null; then echo alive; else "
                        // setsid + the short sleep: the watcher must not be attached when this adb session closes
                        + "setsid " + Script.PATH + " > /dev/null 2>&1 < /dev/null & sleep 2; echo started; fi");
                if (!"alive".equals(state)) log.add("watcher " + state);
            } catch (Exception e) {
                log.add("adb: " + e);
                shell.close();
                wait = RETRY_MS;
            }
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** Writes the script when it changed and stops the old watcher (and its children) so the new one starts. */
    private void installScript(Shell shell, KeyLog log) throws Exception {
        String encoded = Base64.encodeToString(Script.build(this).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String result = shell.run("echo " + encoded + " | base64 -d > " + Script.PATH + ".new && "
                + "if cmp -s " + Script.PATH + ".new " + Script.PATH + "; then rm " + Script.PATH + ".new; echo same; else "
                + "mv " + Script.PATH + ".new " + Script.PATH + " && chmod 755 " + Script.PATH + "; "
                + "p=$(cat " + Script.PID + " 2>/dev/null); "
                + "if [ -n \"$p\" ]; then pkill -P $p; kill $p; fi; echo updated; fi");
        if (!"same".equals(result)) log.add("script " + result);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
