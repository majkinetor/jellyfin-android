# Upstream: what gets merged

**Required reading before changing anything in `jellyfin-android/`.** It is what jellyfin/jellyfin-android expects, from its repo (CI, `detekt.yaml`, `android-lint.xml`, `.editorconfig`, PR template), its [LLM policy](https://jellyfin.org/docs/general/contributing/llm-policies), and how its maintainer (@nielsvanvelzen) handled the last ~200 closed PRs. Last checked 2026-10-22.

## The LLM policy decides first

Since 2026-05-31, 15 of the 34 closed unmerged PRs from people outside the project (bots not counted) were closed with one line: *"We won't be accepting this contribution as it violates our LLM/"AI" Development Policy."* The code is not reviewed first. Among them:

- **#2238 "Allow WebView media playback without an additional user gesture"**: exactly the change in [issue 2](issues/002-autoplay-paused.md). It was rejected for the policy, not for the change.
- #2241 "Play downloaded music inside the app": a feature built in a Claude Code session, with a `Claude-Session:` line in the body.
- #2224, #2176, #2222, #2229: AI-written bodies (headings like *Summary / Problem / Changes / Testing*, emoji, bullet walls), or "AI assistance" on a large feature.
- #2200, a real one-line bug fix: *"Please write the PR description yourself per our LLM policy and fix your commit authorship."*

Merged, **with AI assistance disclosed**, which shows AI help itself is not the problem:

- #2194 (Codex), #2155 ("debugged and written by a LLM"), #2146 (Opus/Sonnet via Cursor), #2104 (Claude Code, "tested on 2 android devices + emulator").
- They are small, focused fixes. The body is short, plainly in the contributor's own words, and has an honest **Code assistance** section.

What the policy requires:

1. **The PR text and every comment are the contributor's own words.** LLM output is not allowed in "direct communication" (issues, PRs, review replies). Agents in this folder never write upstream PR or issue text for posting. The `issues/*.md` here are notes the owner rewrites in his own words before posting anything.
2. **Commits are authored by the contributor.** No `Co-Authored-By: Claude`, no `Claude-Session:` lines and no tool metafiles in an upstream PR. These trailers stay on our local commits. A PR goes from a **fresh branch** the owner makes: squashed, his authorship, his message.
3. **Honest disclosure goes in the template's *Code assistance* section**, in his words. It is not hidden.
4. **He must understand and be able to explain every line**, and handle review comments himself.
5. **Tested on a real device**, and he says so.
6. **Small and focused.** One fix per PR. A large PR needs small commits that each make sense alone.

## Repo conventions

- **CI on every PR**: `./gradlew detekt lint` (App / Lint) and the unit tests (App / Test). Both must pass. detekt only *reports*: upstream already has ~100 findings, and the build stays green. Still, **add no new findings**: compare against the files you touched. Run `bash build/check.sh [branch]` (Docker) before proposing anything.
- **Style**: `.editorconfig`: 4 spaces, max 140 columns, but **detekt flags anything over 120**, so keep lines ≤ 120; final newline, **no wildcard imports**. Kotlin official style, imports sorted alphabetically with Android/AndroidX/Kotlin/third-party/`org.jellyfin` mixed in one sorted block (as the existing files do).
- **detekt** (`detekt.yaml`): complexity, magic numbers, long methods, swallowed exceptions are flagged. Use `@Suppress("…")` only where the existing code does the same.
- **No hard-coded user-facing text.** Every label goes into `app/src/main/res/values/strings.xml` and only there. Translations come from Weblate, so never edit `values-*/`. Settings use `titleRes` / `summaryRes`.
- **Logging** with Timber. **DI** with Koin (`AppModule.kt`). **UI**: settings with Maxr1998 modernpreferences, new screens in Compose Material (1), as `ComposeFragment` + screen composable, like Downloads.
- **Web client hooks** go through the existing plugin mechanism (`assets/native/*Plugin.js`, listed in `nativeshell.js`) and `NativeInterface` methods.
- **Don't touch** `.github/` workflows, Gradle versions or dependencies in a feature or fix PR (#2241 changed a workflow).
- **Commit and PR title**: short, imperative ("Fix X", "Add Y"), following https://chris.beams.io/posts/git-commit/.
- **PR body**: the template's three sections: **Changes** (1–5 sentences), **Code assistance**, **Issues** (`Fixes #…`).

## Scope: what fits upstream

- **Good candidates**: small bug fixes in the app itself. From this folder: `fix/mic-silence` and `fix/autoplay-paused`.
  - `fix/mic-silence` changes behaviour (it pauses on microphone use), so expect discussion; an issue first is safer.
  - `fix/autoplay-paused`: #2238 was the same change. It is better proposed with the owner's own motivation (a spun-down drive) and reasoning, ideally after an issue.
- **Unlikely**: big app-side features that duplicate what the web client does, like a native *Recently played* screen. Ask in an issue or on Matrix first. That is why such work stays on `own/*` branches.
- **Better as an issue than a PR**: anything where the owner can't explain every line.

## Checklist before proposing a PR

- [ ] One focused change on a fresh branch off `upstream/master`, authored by the owner, without AI trailers
- [ ] `./gradlew detekt lint test` passes
- [ ] No hard-coded user-facing strings; new strings only in `values/strings.xml`
- [ ] Tested on a real phone; what was tested is known
- [ ] The owner has read the diff and can explain it
- [ ] Title, body and **Code assistance** written by the owner
