# Bug report sent to the NoScroll developer

Draft of the report below. The developer's support address, listed on
[their own site](https://curizic.com/noscroll/), is **support@curizic.com**.

---

**Subject:** NoScroll 2.0.14 — accessibility service stops silently on HyperOS 3 (three distinct failure modes, with logs)

Hello,

I use NoScroll daily and it does its job well, so I spent several days
diagnosing why it kept stopping on my phone rather than uninstalling it. I
found three separate failure modes. One of them looks like something you can
fix directly, and I think it affects far more users than report it — because it
is completely silent.

**Device:** Xiaomi 14T Pro (`2407FPN8EG`), HyperOS 3.0.301.0, Android 16 (API 36)
**NoScroll:** 2.0.14 (versionCode 112), installed from Play Store

---

## 1. The silent one: overlay permission is revoked, the app still reports "active"

This is the most important one.

At some point `SYSTEM_ALERT_WINDOW` was set to `ignore` for NoScroll:

```
$ adb shell cmd appops get com.newswarajya.noswipe.reelshortblocker SYSTEM_ALERT_WINDOW
SYSTEM_ALERT_WINDOW: ignore; rejectTime=+3d13h35m ago
```

The accessibility service kept running and kept receiving scroll events, but it
could no longer draw the blocking screen. `rejectTime` shows the system had been
refusing overlay requests for three and a half days.

During all of that, NoScroll's own foreground notification read:

> **NoScroll is active:** Monitoring and blocking short-form video content.

So from the user's side there is no signal at all. The app says it is working,
the accessibility permission is genuinely on, and Reels and Shorts scroll
freely. I only found it by dumping app-ops.

**Suggested fix:** check `Settings.canDrawOverlays()` when the service starts
*and* when it detects it should be blocking, and if it is false, say so — change
the notification text and the in-app status to something like "cannot block:
overlay permission missing", ideally with a tap-through to
`ACTION_MANAGE_OVERLAY_PERMISSION`. Silence is the worst possible response here,
because the user believes they are protected when they are not.

**A related trap I hit while fixing it:** restoring the permission while the app
was running changed nothing, because the permission is only read at startup. I
had to restart NoScroll before it took effect. Re-checking on `onResume`, or
whenever the service is about to block, would fix that too.

## 2. The accessibility process exits, and the system then disables accessibility

`:as_process` terminates and the system reacts by turning the master switch off:

```
$ adb shell dumpsys activity exit-info com.newswarajya.noswipe.reelshortblocker

timestamp=2026-08-25 09:03:15.218 pid=22527
process=com.newswarajya.noswipe.reelshortblocker:as_process
reason=1 (EXIT_SELF) subreason=0 (UNKNOWN) status=0
importance=125 pss=35MB rss=139MB
```

The corresponding system log:

```
09:03:15.204 I/ActivityManager: Process ...:as_process (pid 22527) has died: prcp FGS
09:03:15.207 W/ActivityManager: Scheduling restart of crashed service
             ...NoScrollAccessibilityService in 30934ms for connection
```

Every recorded exit in the two-week history reads `reason=1 (EXIT_SELF)`:
2026-08-10, 08-19, 08-20, 08-23, 08-25. There is no `am_kill` event, no
low-memory kill, and no `Killing` line from ActivityManager — so the system was
not killing it. I checked and ruled out battery optimisation (the package is in
the `deviceidle` whitelist, standby bucket 5 / EXEMPTED), overnight cleanup, app
updates, and the Xiaomi antivirus.

I want to be careful about what this proves. `EXIT_SELF` means the process ended
itself; it does not say why it decided to. If something external tells the app
its access is being withdrawn and it shuts down cleanly, the exit code looks
identical. So this may be a crash path in your code, or it may be a correct
response to something else — you are far better placed to tell which.

Two things that may or may not be relevant:

- The main process was using **225 MB pss / 205 MB rss** at the time of one of
  these exits, which seems large and might make the process a target under
  memory pressure.
- Google's account security review has started showing a card recommending that
  accessibility access be revoked from NoScroll. I found no evidence in the logs
  of Google actually acting on it (Play Protect's scan that morning reported zero
  findings), but you may be seeing reports that correlate with it.

## 3. The service gets stuck marked as crashed

After the process exits, the service can end up listed in `Bound services` and
`Crashed services` at the same time, while the settings look perfectly healthy:

```
$ adb shell settings get secure accessibility_enabled
1
$ adb shell dumpsys accessibility
     Bound services:{Service[label=NoScroll, ...]}
     Enabled services:{{com.newswarajya.noswipe.reelshortblocker/...}}
     Crashed services:{{com.newswarajya.noswipe.reelshortblocker/...}}
```

In this state the app believes it holds its permission, and toggling the switch
in Settings does not reliably recover it. What does recover it is a full rebind:

```bash
settings put secure accessibility_enabled 0
settings delete secure enabled_accessibility_services
sleep 2
settings put secure enabled_accessibility_services "<package>/<service>"
settings put secure accessibility_enabled 1
```

Writing `accessibility_enabled = 1` on its own is not enough — the service stays
in `Crashed services`. Worth knowing if you ever add self-recovery.

---

## Why I am writing rather than just uninstalling

The app works well when it works, and the failure modes above are invisible to
an ordinary user — you get no error, no notification, nothing. People will
assume the app is unreliable and leave without telling you, which is a shame,
because item 1 in particular looks straightforward to fix.

I ended up writing a small watchdog for my own phone that restores the
accessibility service and warns when the overlay permission goes missing:
https://github.com/haku4130/hyperos-accessibility-guard — the README there has
the full diagnosis. Please feel free to take anything useful from it.

Happy to run any diagnostic you would like on this device, or to test a build.

Best regards
