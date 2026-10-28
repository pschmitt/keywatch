// SPDX-License-Identifier: GPL-3.0-or-later
package com.pschmitt.keywatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the service after every restart and after the app is updated; BOOT_COMPLETED is one of the few moments a background app may start one. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        context.startForegroundService(new Intent(context, KeyWatchService.class));
    }
}
