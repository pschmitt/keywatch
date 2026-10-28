// SPDX-License-Identifier: GPL-3.0-or-later
package com.pschmitt.keywatch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.TextView;

import java.util.List;

/** Starting the app once is what lets Android deliver the boot broadcast to it; the screen shows the log. */
public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView view;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            List<String> lines = new KeyLog(MainActivity.this).tail(14);
            view.setText("KeyWatch\n\nThe YouTube and Star buttons are handled while this app runs in the background.\n"
                    + "On the first start the TV asks to allow USB debugging: choose \"Always allow\".\n\n"
                    + String.join("\n", lines));
            handler.postDelayed(this, 2_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new TextView(this);
        view.setTextSize(18);
        view.setPadding(64, 48, 64, 48);
        view.setGravity(Gravity.TOP);
        setContentView(view);
        startForegroundService(new Intent(this, KeyWatchService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }
}
