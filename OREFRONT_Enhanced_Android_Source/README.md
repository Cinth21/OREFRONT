# OREFRONT Enhanced — AI Command Wars v2.0 (Android Source)

**A native Android portrait-mode tactical strategy game with a stylized 2.5D/isometric battlefield.** The original Java game engine, six-player phone-hosted LAN protocol and game actions have been retained and expanded. This is an enhanced **working-source classroom prototype**, not a commercial 3D game or a pixel-perfect recreation of the cinematic concept poster.

## What has been implemented

- New dark futuristic **portrait main menu** with AI Mode and Local LAN Multiplayer, commander panel, and room discovery / code join.
- Redesigned **six-human-player lobby**: six distinct factions and colors, host badge, ready status, room code and displayed host IP, match settings, team changes in CUSTOM mode, game start.
- **Isometric-looking, perspective-style 2.5D terrain**, rocky obstacles, water decoration, trees, raised color-coded HQs, production buildings, infantry, tanks, aircraft, heroes, sector capture beacons and a mini-map. These use procedural Android Canvas vector shapes; no 3D engine or photoreal textures.
- **Economy HUD**: Gold, Oil, Iron, Energy; own team and base HP; live countdown and selected unit/building info.
- Three command tabs: **BUILD** (Mine, Refinery, Generator, Barracks, Factory, Airfield, Turret); **UNITS** (Soldier, Tank, Aircraft, Hero); **UPGRADE** (selected building).
- New **defensive Turret** that automatically damages nearby enemy units (host-authoritative). AI bots can build turrets too.
- Contextual AI intelligence warnings and building recommendations, plus economy/defense/unit planning and shortest-path BFS movement.
- **AI offline** (2 / 3 / 5 computer factions, Easy/Medium/Hard).
- **Genuine LAN sockets**, no Mac server or internet for gameplay: one Android phone is the authoritative host, up to five additional Android phones connect on the same hotspot/Wi-Fi; UDP room search, 6-digit join code and manual host IP fallback.
- Team modes 1v1, 2v2, 3v3, free-for-all, and custom. Host-configured player limit, map, duration, resource preset and optional bots.
- Disconnected player stays in the match with a preserved base, optional host AI takeover, reconnect with same phone token. No automatic victory merely because a player disconnected.
- Multiplayer snapshots for resources, bases, construction, troops, movement, sectors and results, and player-specific intelligence messages.

## Important differences from the reference image

The supplied concept art shows highly detailed, cinematic **true 3D models**. This Android app uses **lightweight vector-drawn 2.5D/isometric graphics** as a practical mobile prototype; the reference poster is a *design target*, **not a screenshot** of the produced APK. Fully modeled 3D terrain, detailed unit animation, music, fog-of-war and advanced selection/multi-unit orders would be a separate production phase. Portrait menus + portrait battles are implemented in this version to resemble the three portrait-screen concept.

## Current test status

1. **Passed** local pure-Java rule tests, 105 AI simulations, results, ownership checks, new turret functionality and state serialization.
2. **Passed** genuine TCP loopback test of one server and five real client sockets running on one test machine, including lobby, match, unit commands, reconnect and AI takeover. This is **not** equivalent to six physical phones.
3. Android-specific activity compiles under **temporary Android API stubs** (syntax/type-check only). There is **no Android SDK or Gradle installed in this working environment**, so the real Android APK build remains **unverified** here.
4. **NOT YET TESTED** on actual Android phones, and **six separate physical-device acceptance** is still outstanding.

## Build APK using GitHub Actions — no Android Studio on Mac

1. Extract the ZIP and open the source folder in Finder or VS Code.
2. Create a **new empty GitHub repository**, e.g. `OREFRONT-Enhanced`. This avoids conflicts with the previous published `OREFRONT-AI-Command-Wars` project. Don't add a default README when creating it.
3. Upload the **contents** of the extracted folder so the repository's **root** directly contains `app/`, `tests/`, `settings.gradle`, `build.gradle`, and `.github/workflows/build-apk.yml`.
4. `.github` is hidden in Finder. Show hidden items with **Command + Shift + .**. Alternatively create `.github/workflows/build-apk.yml` inside GitHub's browser using the exact workflow source included in this ZIP.
5. Commit files on the `main` branch. Open **Actions → Build OREFRONT Enhanced Android APK**. The workflow will test the core game then run `gradle :app:assembleDebug` in GitHub's Linux runner.
6. A green check indicates the Gradle build and automated tests succeeded. Open the run → **Summary → Artifacts → OREFRONT-Enhanced-Android-APK**, download and extract the ZIP to obtain **`OREFRONT-Enhanced.apk`**.
7. In your new GitHub repository, open **Releases → Draft a new release**. Create tag `v2.0.0`, title `OREFRONT Enhanced v2.0`, upload **`OREFRONT-Enhanced.apk`** to Release Assets, and click **Publish release**. Only after publishing can classmates download the APK from the public release link you send on Messenger.
8. Install on an Android phone and test offline AI mode first; then run a two-phone hotspot match; then three to six phones. Android may require permission to install APKs from your browser. The APK is a debug build for classroom testing, not Play Store distribution.

> **Note:** This enhanced version uses a new Android `applicationId` (`com.student.orefront.enhanced`) so it can coexist with the older OREFRONT installation. All phones in a LAN match must install the **same enhanced APK version**.

## How to play

**AI:** Main Menu → PLAY AGAINST AI → select difficulty. The AI uses economy development, route planning, and defensive turrets.

**LAN:** One phone turns on hotspot (or uses shared Wi-Fi). All phones connect to that network. The first phone opens **LOCAL LAN MULTIPLAYER** to host and shows a six-digit room code and host IP. Other phones choose **SEARCH LAN ROOMS**, or **JOIN BY CODE + IP** and input the room code and IP. Each player taps **I'M READY**; the host chooses mode/map/player cap and taps **START MATCH**.

**Battle:** Tap a BUILD tab item, then a free tile near your base; production costs resources. Create the appropriate facility before buying Soldiers, Tanks or Aircraft. Tap **UNITS** and recruit. Tap your colored unit on the map, then tap a reachable tile to move it. Units automatically attack hostile units/bases in range. Capture yellow sectors, protect your HQ, and win via conquest or timed score. **UPGRADE** improves selected buildings. Host can assign AI takeover for disconnected factions.

## Cautions for 6-player LAN

- Some hotspots have device limits fewer than five clients or isolate clients. A Wi-Fi router/access point may work better.
- If UDP broadcast room discovery fails, use **manual host IP + room code**.
- Keep the host phone awake, unlocked, and the app running; it is the single match authority. No host migration exists.
- The game uses local TCP port 38417 and UDP discovery port 38418. No cloud service is involved in LAN play.
- This implementation uses event-triggered snapshots and a compact socket protocol, but bandwidth and real-world device smoothness have not been profiled with six physical phones.

## Source files

- `app/src/main/java/com/student/orefront/MainActivity.java`: all native UI screens, touch commands, 2.5D renderer.
- `app/src/main/java/com/student/orefront/GameCore.java`: game engine, economics, AI, units, pathfinding, turret combat, snapshots.
- `app/src/main/java/com/student/orefront/LanRoom.java`: phone-hosted LAN sockets, room code, discovery, reconnect, authoritative commands.
- `tests/*.java`: gameplay, six-client protocol and turret tests.
- `.github/workflows/build-apk.yml`: GitHub cloud APK build.
- `docs/Concept_Reference_Only.png`: illustration of the intended long-term visual direction; not a screenshot.

See `SETUP-CEBUANO.md` for shorter, step-by-step instructions and `TEST_REPORT.md` for verifiable testing status.
