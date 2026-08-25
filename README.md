# NoScroll Guard

Keeps the [NoScroll](https://play.google.com/store/apps/details?id=com.newswarajya.noswipe.reelshortblocker)
accessibility service alive on Xiaomi HyperOS, which silently switches it off.

If you use an accessibility-based focus app and keep waking up to
*"this app isn't working properly"*, this is why — and this is a fix.

## The problem

On a Xiaomi 14T Pro running HyperOS 3 (Android 16), NoScroll's accessibility
service stops working at unpredictable intervals. The system writes
`secure accessibility_enabled = 0` while leaving `enabled_accessibility_services`
untouched, and marks the service as crashed:

```
$ adb shell dumpsys accessibility
     Bound services:{}
     Enabled services:{{com.newswarajya.noswipe.reelshortblocker/...NoScrollAccessibilityService}}
     Crashed services:{{com.newswarajya.noswipe.reelshortblocker/...NoScrollAccessibilityService}}
```

Once the service lands in `Crashed services`, flipping the toggle in Settings
does not always bring it back.

### The actual cause

It is not Xiaomi. `dumpsys activity exit-info` gives the verdict:

```
timestamp=2026-08-25 09:03:15.218  process=...:as_process
reason=1 (EXIT_SELF)  subreason=0  status=0
```

**`EXIT_SELF` — the app's accessibility process terminates itself.** Every
recorded death over two weeks reads the same way. Nothing *killed* it: there is
no `am_kill`, no low-memory kill, no Play Store update.

Read that carefully, though: `EXIT_SELF` says the process ended on its own, not
*why* it decided to. An app told from the outside that its access is being
withdrawn would exit exactly like this too. So this identifies the mechanism,
not the motive — and blaming the app outright would be going beyond the
evidence.

The chain is then:

1. NoScroll's `:as_process` exits on its own.
2. The system marks the service crashed and schedules a restart ~31s later.
3. The system server writes `accessibility_enabled = 0` in response.
4. You wake up to an app that is not blocking anything.

Step 3 is why the settings database blames `pkg:android` — the system reacting
to a dead service, not switching it off on a whim.

Battery optimisation, overnight cleanup, app updates and the Xiaomi antivirus
were each investigated and ruled out along the way; the notes are in
[docs/DESIGN.md](docs/DESIGN.md).

### Three failure modes, not one

**Permission switched off.** The obvious one: `accessibility_enabled = 0`, the
app tells you it is not working properly.

**Service stuck crashed.** The nastier one: the settings look *perfect* —
master switch on, service listed — while the service sits in
`Crashed services` doing nothing. The app believes it has its permission. You
get no warning at all, and a settings-only health check sees nothing wrong.

**Overlay permission revoked.** The quietest of all: the service runs, catches
every scroll — and cannot put its blocking screen on screen, because
`SYSTEM_ALERT_WINDOW` was set to `ignore`. The app's own notification still
reads *"active: monitoring and blocking"*. Nothing anywhere says otherwise:

```
$ adb shell cmd appops get <package> SYSTEM_ALERT_WINDOW
SYSTEM_ALERT_WINDOW: ignore; rejectTime=+3d13h35m ago
```

This app detects all three. It repairs the first two; the third it can only
report, because restoring an app-op needs `MANAGE_APP_OPS_MODES`, which is
signature-only and cannot be granted over ADB. The notification opens the exact
settings screen where you can restore it in two taps.

One trap worth knowing: restoring the overlay permission is not enough on its
own. Apps read it when they start, so a process that was already running keeps
behaving as if it were still denied. **Reopen the guarded app afterwards** —
the guard now says so in a notification, because this cost an afternoon of
"the permission is right there and it still does not block".

## The fix

Writing `accessibility_enabled = 1` is **not enough** — the service stays in
`Crashed services`. The full rebind cycle is:

```bash
settings put secure accessibility_enabled 0
settings delete secure enabled_accessibility_services
sleep 2
settings put secure enabled_accessibility_services "<package>/<service>"
settings put secure accessibility_enabled 1
```

The app performs exactly this whenever it detects the service is down.

## The key trick: no root, no Shizuku

`WRITE_SECURE_SETTINGS` looks unreachable for a normal app, but:

```
$ adb shell dumpsys package permission android.permission.WRITE_SECURE_SETTINGS
    prot=signature|privileged|development|installer|role
```

The `development` flag means it can be granted over ADB, **once**, and it
survives reboots:

```bash
adb shell pm grant io.github.haku4130.noscrollguard android.permission.WRITE_SECURE_SETTINGS
```

No root. No Shizuku, and therefore nothing to restart after every reboot.
The cable is needed exactly once, at install time.

Four permissions are granted this way, all of them carrying the `development`
flag — `install.sh` does it for you:

| Permission | What it buys |
|---|---|
| `WRITE_SECURE_SETTINGS` | Performing the rebind cycle |
| `DUMP` | Reading the accessibility manager's real state, and naming who wrote the setting |
| `GET_APP_OPS_STATS` | Seeing whether the guarded app may still draw overlays |
| `PACKAGE_USAGE_STATS` | Recording which app was on screen when a reset happened |

One thing is deliberately *not* on that list: `MANAGE_APP_OPS_MODES`, which
would let the app restore the overlay permission itself. It is signature-only,
with no `development` flag, so no amount of ADB will grant it. Hence detection
plus a notification rather than a silent fix.

## Install

Requires a computer with `adb` and, on the phone, two Developer options
toggles: **USB debugging (Security settings)** and **Install via USB**
(both need a Mi account).

```bash
git clone https://github.com/haku4130/hyperos-accessibility-guard
cd hyperos-accessibility-guard
./install.sh
```

The script builds the APK, installs it, grants three permissions, adds the app
to the battery whitelist and starts it.

One step remains manual, because it cannot be granted over ADB:
**Security → Permissions → Autostart → enable NoScroll Guard.**

## How it works

A `ContentObserver` watches `accessibility_enabled` and
`enabled_accessibility_services`, so the reaction is immediate and costs no
battery — no polling loop.

There are three entry points, and each exists for a reason learned the hard way:

| Source | When | Why it exists |
|---|---|---|
| `observer` | The moment the setting changes | The normal path |
| `startup` | When the service starts | The observer only sees *changes*. A reset that happened while the guard was dead leaves no event behind |
| `screen on` | Screen on / device unlocked | A stuck-crashed service changes no setting, so the observer never fires for it. Blocking only matters while you are looking at the screen, so this is where it gets caught — in seconds, not minutes |
| `health check` | Every 15 minutes | Backstop for the observer dying

Health is judged on two axes: the settings, and what the accessibility manager
actually thinks — the latter read from `dumpsys accessibility`. If the runtime
state cannot be determined, the settings verdict stands; missing information
never raises a false alarm.

Before repairing, the app records what it can: timestamp, foreground app,
screen state, and which package wrote the setting last. Evidence first, repair
second — otherwise the app's own writes overwrite the evidence.

### Naming the culprit

The settings database stores a `pkg:` field naming the last writer. It is
reachable through `dumpsys settings`, which needs the `DUMP` permission —
also a `development` permission, so also grantable over ADB.

Whether SELinux lets a regular app call `dumpsys` is device-dependent. **On
Xiaomi 14T Pro / HyperOS 3 it is not blocked**, verified by breaking the
setting over ADB and reading it back:

```
[observer] reset detected — master=off, service listed=yes, screen=on,
           foreground app=com.miui.home, setting written by=com.android.shell
[observer] Reset at 20:59, restored. Foreground app: com.miui.home.
           Setting written by: com.android.shell
```

`com.android.shell` is correct — that was `adb`. When a real reset happens,
that field will name the real culprit.

If `dumpsys` is blocked on your device, the field reads
`could not determine` and everything else still works.

## The screen

One screen: current state, a **Pause for 30 minutes** button, and the event
journal with a share button. The pause exists so you can deliberately turn
NoScroll off without the guard fighting you.

NoScroll's own pause button does not touch the system permission — verified by
sampling all three indicators across a pause/resume cycle — so the guard never
interferes with it.

## Limitations

- **Force-stop defeats it.** When HyperOS force-stops the app, its scheduled
  work is cancelled along with the process and it will not come back on its
  own. The battery whitelist, autostart and `BOOT_COMPLETED` are what prevent
  this; gaps will be visible in the journal.
- **The NoScroll package is hardcoded** in
  [`Constants.kt`](app/src/main/java/io/github/haku4130/noscrollguard/Constants.kt).
  Point it at a different accessibility service and it should work the same —
  the mechanism is not NoScroll-specific.
- **Tested on exactly one device**: Xiaomi 14T Pro, HyperOS 3.0.301.0,
  Android 16. Reports from other devices are welcome.

## Diagnosing your own device

If your accessibility app keeps stopping, run these before assuming anything.
Each one rules something in or out, and together they take about two minutes.
Everything here is read-only except where noted.

### 1. Is the permission actually on?

```bash
adb shell settings get secure accessibility_enabled
adb shell settings get secure enabled_accessibility_services
```

`accessibility_enabled` must be `1` and your service must appear in the list.
If the master switch is `0` while the service is still listed, something turned
it off — that is failure mode one.

### 2. Is the service actually running?

```bash
adb shell dumpsys accessibility | grep -E "Bound|Enabled|Crashed"
```

The interesting case is a service that appears in **both** `Bound services` and
`Crashed services`. The settings look perfect, the app believes it holds its
permission, and nothing works. That is failure mode two, and no UI anywhere
tells you about it.

### 3. Can it draw on screen?

```bash
adb shell cmd appops get <package> SYSTEM_ALERT_WINDOW
```

`allow` is what you want. `ignore` means the app can detect scrolling but
cannot show its blocking screen — the completely silent failure. Check
`rejectTime` in the output: it tells you how long the system has been refusing
overlay requests, which is usually how long the app has been useless.

### 4. Why did the process die?

```bash
adb shell dumpsys activity exit-info <package>
```

This is the single most useful command here, and it answered in one call what
two days of log reading could not. Read `reason=`:

| Reason | Meaning |
|---|---|
| `1 (EXIT_SELF)` | The process ended itself — not killed by anything |
| `13 (OTHER KILLS BY SYSTEM)` | Check `description=`; `normal_mem_pressure` means memory |
| `10 (USER REQUESTED)` + `FORCE STOP` | Someone force-stopped it — including you, from adb |
| `16 (PACKAGE UPDATED)` | The app was updated underneath itself |

### 5. Who wrote the setting?

```bash
adb shell dumpsys settings | grep accessibility_enabled
```

The `pkg:` field names the package that wrote the value last. Run it **before**
touching anything — the moment you or an app rewrites the setting, that name is
replaced and the evidence is gone. `pkg:android` means the system server did it,
which usually means it was reacting to something rather than acting on its own.

### 6. Make the logs survive the night

Default log buffers hold roughly fifteen minutes on a busy phone, which is
useless for something that breaks while you sleep:

```bash
adb shell logcat -G 64M
```

That survives until the next reboot and gave us two full days of history. When
something does break, pull both buffers — kill reasons live in `events`, not in
`main`:

```bash
adb logcat -d -b main -b system -v time > main.log
adb logcat -d -b events -v time > events.log
```

`am_kill` in the events buffer means ActivityManager killed the process. Its
*absence* next to an `am_proc_died` is meaningful: nothing killed it, it left on
its own.

### Repairing by hand

If you just want it working again without installing anything (writes settings,
needs **USB debugging (Security settings)** on Xiaomi):

```bash
adb shell settings put secure accessibility_enabled 0
adb shell settings delete secure enabled_accessibility_services
sleep 2
adb shell settings put secure enabled_accessibility_services "<package>/<service>"
adb shell settings put secure accessibility_enabled 1
```

And if the overlay permission was the problem:

```bash
adb shell appops set <package> SYSTEM_ALERT_WINDOW allow
```

**Then restart the guarded app.** Apps read that permission when they start, so
a process that is already running keeps behaving as if it were still denied.
This is not obvious and it cost an afternoon.

## Reported upstream

The findings were written up for the app's developer:
[docs/BUG-REPORT.md](docs/BUG-REPORT.md). The silent overlay failure in
particular looks fixable in the app itself — an accessibility blocker that
cannot draw should say so rather than reporting itself as active.

## Before you install this

If your accessibility service dies on a Xiaomi device, try these first — for
many people one of them is the actual cause, and then you need no app at all:

1. Developer options → turn off **MIUI optimisation**
2. Play Store → Play Protect → turn off app scanning
3. App info → turn off **Remove permissions if app is unused**
4. Security → Autostart → enable the app
5. Recents → long-press the card → lock it

This app is for the case where all of that is already done and the service
still dies.

## Build

```bash
brew install openjdk@21
brew install --cask android-commandlinetools
sdkmanager "platforms;android-36" "build-tools;36.0.0"
./gradlew test assembleDebug
```

Built against AGP 8.13.2, Kotlin 2.2.0, Gradle 9.5.1. Gradle 9.6+ does **not**
work: it removed an internal API that AGP 8.x depends on.

Design notes and the full investigation: [docs/DESIGN.md](docs/DESIGN.md).

## License

MIT
