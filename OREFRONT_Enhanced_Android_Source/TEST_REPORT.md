# OREFRONT Enhanced v2.0 — Test Report

## Completed in the development environment

| Test | Result | What it proves |
|---|---|---|
| `StrategySmokeTest` | PASS | Six human slots, configuration, base economies, buildings, movement ownership, 105 AI simulation runs, reconnect |
| `SixPlayerLanTest` | PASS | One authoritative host + five **separate real TCP socket clients** on one machine: join, room cap, ready, match start, commands, snapshots, disconnect, AI takeover, reconnect |
| `BattleResultsTest` | PASS | Combat victory, identical result encoded in state, match time result |
| `EnhancedFeatureTest` | PASS | New Turret building, automatic defensive fire, serialized defense structure, faction resource ownership, intelligence |
| Android activity Java type/syntax check | PASS with temporary Android API stubs | Valid Java method signatures under a minimal stub of Android APIs; **not equivalent to a real Android build** |
| GitHub Android APK build | NOT RUN | Requires uploading files and running GitHub Actions (Android SDK + Gradle) |
| Physical Android runtime | NOT RUN | Install and open the built APK on Android to verify rendering/touch/screens |
| Six physical Android devices on hotspot | NOT RUN | **Required** before final six-phone acceptance |

## Physical acceptance test for classroom

- [ ] Build new enhanced APK successfully using GitHub Actions
- [ ] Open AI Mode and construct/upgrade buildings on a real Android device
- [ ] Host room on Android over Wi-Fi / hotspot; 5 more Android phones join; 7th is rejected
- [ ] All 6 show correct player colors, username, factions and ready indicators
- [ ] Host starts a 3v3 match; all six see the same match timer, resources and map
- [ ] All players independently build a structure, recruit troops, move, and attack on their own devices
- [ ] All players see the same result and winning team
- [ ] Disconnection keeps the base; host AI takeover option works; same device reconnects
- [ ] Repeat 1v1, 2v2, FFA, custom, manual IP fallback
- [ ] Check heat, memory, battery use, layout clipping, and any hotspot device limits

## Known scope limits

- The concept reference picture is cinematic marketing art. Runtime is a native Android **2.5D vector isometric prototype**, not real mesh-based 3D.
- Game uses **single authoritative Android host** and can not migrate if it exits.
- Socket integration tests on one computer are not six-phone tests.
- Target SDK 28 was retained for local networking compatibility; production publication would require modern Android target policies, updated permissions, security review, and signing.
