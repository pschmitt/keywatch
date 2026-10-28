# KeyWatch

Starts an app from a button on an Android TV remote **without an accessibility service**, so holding
volume up or down keeps repeating.

Remapping apps (and launchers with key helpers) work through an accessibility service that filters key
events. On some firmware that service drops the auto-repeat of a held volume key. KeyWatch does it the
other way round: a small shell script runs as the `shell` user on the TV, reads the remote's input device
with `getevent`, and starts the app with `monkey`. The app only keeps that script installed and running.

Tested on a Google TV Streamer (Google TV 14, build `UTTK.260317.003`) with its stock remote. Other
devices and remotes should work if the remote's input device is named `Google TV Remote Keyboard`
(change the name in `Script.java` otherwise); I have not tried any.

## How it works

1. A boot receiver (`BOOT_COMPLETED`, also after an update) starts a foreground service.
2. The service connects to the TV's own adb over loopback (`127.0.0.1:5555`) with a key the app creates
   for itself. The TV asks once to allow it; choose "Always allow".
3. It writes `/data/local/tmp/keywatch.sh`, generated from `app/src/main/res/values/config.xml`, and starts
   it detached (`setsid`). A lock file lets only one copy run.
4. Every minute it checks that the watcher is alive and starts it again if not. When the script changed,
   the old watcher and its children are stopped first.

The adb client is [libadb-android](https://github.com/MuntashirAkon/libadb-android). Over loopback a
command now and then gets no answer, so every command has a 10 s limit and is retried on a fresh connection.

## Requirements

- adbd listening on **TCP port 5555** on the TV. Google TV does not do that by default: `adb tcpip 5555`
  from a computer lasts until the next reboot, so use something that switches it on at boot (the TV I
  built this for uses an "ADB auto enable" app). Network adb is reachable from the whole LAN; only keys
  you allowed can use it, so decide whether you are comfortable with that.
- Android 11 or later (`minSdk 30`).

## Install

```sh
nix-shell shell.nix   # or any JDK 17 with the Android SDK 34 and build-tools 34.0.0
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.pschmitt.keywatch/.MainActivity
```

On NixOS pass `-Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/34.0.0/aapt2`.
The release build is signed with the debug key; it is meant to be sideloaded.

Start the app **once** after installing: Android does not deliver the boot broadcast to an app that was
never opened. Keep the TV awake and accept the "Allow USB debugging?" dialog with "Always allow". The app's
screen shows its log (also `adb logcat -s KeyWatch`).

## Configure the buttons

Edit `app/src/main/res/values/config.xml`, rebuild and `adb install -r`:

```xml
<string-array name="app_keys" translatable="false">
    <item>000c0077:com.google.android.youtube.tv</item>
    <item>000c0186:org.jellyfin.androidtv</item>
</string-array>
```

Each item is `<scan code>:<package>`. The scan code tells buttons apart that send the same key: on the
Google TV Streamer remote the YouTube button (`000c0077`) and the Netflix button (`000c0078`) both send key
`0xf0`. The Star button sends `000c0186` (key `0x1a7`). To find the code of another button:

```sh
adb shell getevent -l          # note the remote's /dev/input/eventN
adb shell getevent /dev/input/eventN
```

press the button and read the value on the `0004 0004` line. Only keys `0xf0` and `0x1a7` are
watched; add other key codes to the `case` in `Script.java`.

## Uninstall

```sh
adb shell 'p=$(cat /data/local/tmp/keywatch.pid); pkill -P $p; kill $p'
adb uninstall com.pschmitt.keywatch
adb shell rm -f /data/local/tmp/keywatch.sh /data/local/tmp/keywatch.pid
```

## Licence

GPL-3.0-or-later, see `LICENSE`. Parts are adapted from [Cast Fix](https://github.com/thayford/castfix)
(MIT); see `NOTICE`.
