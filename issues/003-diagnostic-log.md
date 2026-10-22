---
id: 3
title: Diagnostic log readable in the app
status: done
branch: own/diagnostic-log
upstream: local only
created: 2026-10-22
---

# Diagnostic log readable in the app

So test logs can be collected on the phone, without adb.

- Every log line at INFO or higher (the app's own, `JellyfinAudio`, and the web client's console as `WebView`) also goes to `files/logs/diagnostic.log`. When it reaches 1 MB it becomes `diagnostic.1.log`, so the log is at most 2 MB.
- **Settings → Diagnostics → Diagnostic log** shows the newest 100 kB, with **Share** and **Copy** (newest 200 kB as text, with app, device and WebView versions on top) and **Clear**.
- Settings opens from the web client's menu (*Client settings*).
