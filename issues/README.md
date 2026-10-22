# Issues

One file per issue: `NNN-slug.md`, numbered in order, with a front-matter header:

| Field      | Values                                                                        |
| ---------- | ----------------------------------------------------------------------------- |
| `status`   | `open` (not started) · `testing` (in a build, being tried) · `done` (works) · `dropped` |
| `branch`   | `fix/<slug>` (meant for upstream) or `own/<slug>` (local only)                |
| `upstream` | upstream issue / PR link once reported, `not reported yet`, or `local only`   |

A finished issue stays here with `status: done`; its body is ready to paste into an upstream report.

Open issues are listed in the order they will be worked on.

| #   | Title                                                                    | Status  | Branch              |
| --- | ------------------------------------------------------------------------ | ------- | ------------------- |
| 1   | [Silent "playing" after microphone use](001-mic-silence.md)              | done    | fix/mic-silence     |
| 2   | [Album from thumbnail stays paused](002-autoplay-paused.md)              | done    | fix/autoplay-paused |
| 3   | [Diagnostic log in the app](003-diagnostic-log.md)                       | done    | own/diagnostic-log  |
| 5   | [Continue where I left off; recently played](005-continue-listening.md) | open    | —                   |
| 4   | [File system playlist support](004-filesystem-playlists.md)              | open    | — (server / web)    |
