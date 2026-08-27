# Design notes

## The investigation

Symptom: NoScroll's accessibility service stops working, the system reports
that the app is not working properly, and re-enabling it in Settings does not
reliably help.

The device: Xiaomi 14T Pro (`2407FPN8EG`, codename `rothko`),
HyperOS 3.0.301.0, Android 16 (API 36). NoScroll 2.0.14, installed from Play
Store, package `com.newswarajya.noswipe.reelshortblocker`, accessibility
service running in its own `:as_process`.

### What the logs showed

With `logcat -G 64M` the ring buffers held two full days. In that window the
reset happened exactly **once**, at `08-23 09:59:26.511`, and it was a
deliberate settings write, not a crash:

```
I/ActivityManager: unbindService ... callers:
  AccessibilityManagerService.updateServicesLocked
  AccessibilityManagerService.onUserStateChangedLocked
  AccessibilityManagerService$AccessibilityContentObserver.onChange
  android.database.ContentObserver.onChange
```

`AccessibilityContentObserver.onChange` means the *setting* changed and the
system reacted by unbinding — nothing crashed.

Supporting facts, each of which kills a plausible theory:

- The crash buffer had no entries for the package.
- The NoScroll processes stayed alive until `13:48` — almost four hours later,
  when they were killed by "one-key clean" along with everything else.
- The package was in the `deviceidle` whitelist and in standby bucket 5
  (`EXEMPTED`), so power management was not involved.
- The phone was in active use at the time (a ride-hailing app on screen, a
  screenshot taken four seconds earlier). The "it breaks overnight" theory —
  the original hypothesis — was simply wrong.
- `lastUpdateTime` for NoScroll was over a month earlier, so it was not an
  app update.

### The antivirus theory, and why it was dropped

Xiaomi's security app looked like an excellent suspect. `VirusScanJobService`
started 4 seconds before the reset, `com.miui.guardprovider` parsed NoScroll's
manifest at that moment, a second scan fired immediately after, and
`com.miui.securitycenter` holds `WRITE_SECURE_SETTINGS`.

It did not survive testing. Force-running the periodic scan job:

```bash
adb shell cmd jobscheduler run -f com.miui.securitycenter 210610
```

left the service untouched. The second scan turned out to be a *consequence*
of the reset, not a cause: enabling an accessibility service triggers an
expedited scan job on its own. A virus-database update was also ruled out —
none had run that day.

**Conclusion: the culprit is unidentified.** Android does not log secure
settings writes, and the event is too rare to catch by waiting. Hence the
design goal shifted: fix the effect reliably, and capture evidence so a future
occurrence names the cause.

### Why one write is not enough

Restoring `accessibility_enabled = 1` leaves the service in `Crashed services`
and it never binds. What works, verified on the device, is a full cycle:
turn the master switch off, clear the service list, wait, write the service
back, turn the master switch on.

The 2-second pause is not cargo cult — without letting the system settle
between clearing and rewriting, the rebind does not take.

## Architecture

Five units, each with one responsibility and a defined interface. The seam
that makes the whole thing testable is `SecureSettings`: everything that
touches `Settings.Secure` goes through it, so the logic can be exercised
against a fake on the JVM with no device involved.

| Unit | Responsibility |
|---|---|
| `SecureSettings` | The only door to `Settings.Secure`. Interface + Android impl + fake |
| `AccessibilityStateReader` | Reads state. Changes nothing |
| `AccessibilityRepairer` | Performs the rebind cycle. Decides nothing |
| `AccessibilityRuntimeProbe` | Asks the accessibility manager what it really thinks, via `dumpsys` |
| `EvidenceCollector` | Gathers context about the reset. Repairs nothing |
| `GuardService` | Decides *when* to act, and wires the rest together |

Supporting: `EventLog` (journal), `PauseState` (pause), `HealthWorker`
(periodic backstop), `BootReceiver` (survive reboot), `MainActivity` (screen).

## Four defects found on the device

Unit tests passed long before the app actually worked. These three only
surfaced against the real system.

**Repair cascade.** The repair writes the same settings the observer watches,
so every write triggered another repair — one reset produced five repairs and
five notifications. Fixed with two guards: an in-progress flag and a
3-second quiet window after each repair.

**Lost journal entries.** `GuardApp.eventLog()` constructed a new `EventLog`
per call, so the `@Synchronized` inside it guarded nothing — concurrent
threads each read the file into their own copy and overwrote each other. The
journal is now a single instance per process.

**Duplicate evidence entries.** The repair lock was claimed *after* evidence
collection, but collection runs `dumpsys` and takes ~100 ms. Every observer
thread woken by the same reset walked through that window and logged its own
copy — three entries for one event, some of them describing the mid-repair
state. The lock is now claimed before any work begins.

**No check on startup.** This was a hole in the design, not the code. The
observer only fires on *changes*. If the setting was broken while the guard
was dead — reboot, force-stop, memory cleanup — there is no event left to
catch, and the next scheduled check was up to 15 minutes away. A reset left
the phone unprotected for that entire window. Fixed by checking state
immediately when the service starts.

## The fourth failure mode: repaired but still dead

Days of real use surfaced one more. The guard restored the permission at 09:37
after the app's process died at 09:36, and every system-visible signal went
green — settings right, service bound, `Crashed services` empty, both processes
alive, the app's own notification reading "active". Blocking stayed broken for
the next three and a half hours.

The evidence was in app-ops. Its `time=` field records when the op was last
actually *used*, and for `SYSTEM_ALERT_WINDOW` it stopped at 09:36 — the moment
of the crash. The app had drawn nothing since, despite holding the permission.

Restarting the app fixed it, which raised the obvious question: can the guard do
that itself? No, and the negative results are worth recording:

| Attempt | Result |
|---|---|
| `am kill` / `killBackgroundProcesses` | Same PIDs — a foreground service is not a background process |
| `FORCE_STOP_PACKAGES` | `signature\|privileged`, no `development` flag |
| Disabling the service for 20s so the process is reclaimed as empty | Survived all 20s with the same PID |

Only launching the app remains, and that needs `SYSTEM_ALERT_WINDOW` — granted
for the background-activity-start exemption it carries, not for drawing.

Doing it immediately would mean a phone lighting up with someone else's app at
three in the morning. So a repair raises a persisted flag instead, and the app
is opened at the next unlock, when the screen is already the user's focus. The
flag lives in `SharedPreferences` because the guard's own process may die in
between, and it is cleared only after `startActivity` actually returns — a
silently dropped launch leaves it pending for the next attempt.

## Testing approach

30 JVM unit tests cover the logic: state reading, the rebind cycle (including
an assertion on the exact *order* of writes, which is the knowledge that was
expensive to acquire and would be easy to break), the journal, the pause, and
the restart flag.

Device-dependent parts — `dumpsys`, `UsageStatsManager`, `PowerManager` — have
no unit tests on purpose. They cannot be faked meaningfully, and a test built
on stubs would only assert that the stubs work.

Those were verified by hand instead, by breaking the setting over ADB in six
scenarios: guard alive, guard killed, pause on, pause off, after reboot, and
on a clean install. Every one restored, except with pause active, where
standing down is the correct behaviour.

## Things worth knowing if you fork this

- `adb shell settings put secure` requires **USB debugging (Security
  settings)** on Xiaomi; without it you get a `SecurityException` on
  `WRITE_SECURE_SETTINGS`.
- Installing over ADB requires the separate **Install via USB** toggle, which
  Xiaomi turns back off periodically.
- A `settings put` with an empty string fails from the shell with
  `Bad arguments` — use `settings delete`. From inside the app an empty
  string is fine.
- After a `force-stop`, `WorkManager` jobs disappear from `jobscheduler`
  entirely. Nothing inside the app can recover from that; only user launch,
  autostart, or `BOOT_COMPLETED` will.
- The service is declared `exported="false"`, so
  `am start-foreground-service` cannot start it from ADB. Launch the activity
  instead.
