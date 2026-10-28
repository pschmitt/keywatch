// SPDX-License-Identifier: GPL-3.0-or-later
package com.pschmitt.keywatch;

import android.content.Context;

/**
 * The watcher script that runs as the shell user on the TV. It reads the remote's input device (found by name,
 * the /dev/input node changes between boots) and starts an app for the buttons listed in res/values/config.xml.
 * It keeps a lock, so a second start does nothing.
 */
final class Script {
    static final String PATH = "/data/local/tmp/keywatch.sh";
    static final String PID = "/data/local/tmp/keywatch.pid";

    private Script() {
    }

    static String build(Context context) {
        StringBuilder cases = new StringBuilder();
        for (String entry : context.getResources().getStringArray(R.array.app_keys)) {
            int colon = entry.indexOf(':');
            String scan = entry.substring(0, colon).trim();
            String pkg = entry.substring(colon + 1).trim();
            // both end up in a shell script: allow only what a scan code and a package name are made of
            if (!scan.matches("[0-9a-f]{8}") || !pkg.matches("[A-Za-z0-9_.]+")) {
                throw new IllegalArgumentException("bad app_keys entry: " + entry);
            }
            cases.append("                    ").append(scan).append(") launch ").append(pkg).append(" ;;\n");
        }
        return "#!/system/bin/sh\n"
                + "# Written by the KeyWatch app; edit res/values/config.xml there, not this file.\n"
                + "LOCK=" + PID + "\n"
                + "# After a reboot the pid in the lock file can belong to an unrelated process, so check its name too.\n"
                + "if [ -f \"$LOCK\" ] && grep -q keywatch \"/proc/$(cat \"$LOCK\")/cmdline\" 2>/dev/null; then\n"
                + "    exit 0\n"
                + "fi\n"
                + "echo $$ > \"$LOCK\"\n"
                + "\n"
                + "launch() {\n"
                + "    monkey -p \"$1\" -c android.intent.category.LEANBACK_LAUNCHER 1 > /dev/null 2>&1 &\n"
                + "}\n"
                + "\n"
                + "while :; do\n"
                + "    dev=$(getevent -pl 2>/dev/null | awk '/^add device/ {d = $4} /name:.*\"Google TV Remote Keyboard\"/ {print d; exit}')\n"
                + "    if [ -z \"$dev\" ]; then\n"
                + "        sleep 5\n"
                + "        continue\n"
                + "    fi\n"
                + "    scan=\"\"\n"
                + "    getevent \"$dev\" 2>/dev/null | while read -r type code value; do\n"
                + "        case \"$type $code\" in\n"
                + "            \"0004 0004\") scan=$value ;;\n"
                + "            \"0001 00f0\" | \"0001 01a7\")\n"
                + "                [ \"$value\" = \"00000001\" ] || continue\n"
                + "                case \"$scan\" in\n"
                + cases
                + "                esac\n"
                + "                ;;\n"
                + "        esac\n"
                + "    done\n"
                + "    sleep 2\n"
                + "done\n";
    }
}
