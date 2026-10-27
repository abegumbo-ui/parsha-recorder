# Parsha Recorder

A lightweight Android tablet app for capturing quick thoughts on the weekly Parsha and holidays.

- Home screen is a grid of folders: every Parsha (including combined weeks), holidays, and a General folder.
- This week's Parsha and the nearest upcoming holiday are pinned at the top with a big record button.
- **Record** tab: tap a folder to start recording. **Files** tab: tap a folder to open it and listen, rename, move, delete.
- Pause / finish, then listen and save (or discard). If the app is left mid-recording, the recording is kept.
- Calendar view shows the Parsha on each Shabbat and holidays. Diaspora/Israel toggle in the menu.
- Back up everything to one zip and restore it on another tablet (menu, top right). Restore merges without duplicating.

Layout adapts to screen size and DPI. Parsha logic comes from [KosherJava Zmanim](https://github.com/KosherJava/zmanim) (LGPL).

Build: `./gradlew assembleRelease` (JDK 17+, Android SDK 36). Output: `app/build/outputs/apk/release/app-release.apk` (signed with the debug key, for sideloading).
