# Parsha Recorder

A lightweight Android tablet app for capturing quick thoughts on the Parsha, Yom Tov and Gemara.

- Home has three categories: **Parsha**, **Yom Tov** and **Gemara**, plus quick-record cards for this week's Parsha, the nearest Yom Tov and today's Daf Yomi.
- Parsha lists every parsha (including combined weeks) by sefer. Gemara lists every masechta in order (by seder), and each masechta lists its dapim in order.
- Open any folder for a big red record button and a Recordings button that lists, plays, renames, moves and deletes what is there.
- Pause / finish, then listen and save (or discard). If the app is left mid-recording, the recording is kept.
- Calendar shows the Parsha on each Shabbos and the yomim tovim. Diaspora/Israel toggle in the menu.
- Recordings are also saved where a file manager can see them: `Music/Parsha Recorder/Parsha|Yom Tov|Gemara/...`.
- Back up everything to one zip and restore it on another tablet (menu, top right). Restore merges without duplicating.
- Home-screen widget and an app-icon shortcut: one tap records straight into this week's Parsha.
- Updates itself from this repo's GitHub releases (menu, Check for updates).

Layout adapts to screen size and DPI; long lists (about 2,700 dapim) are virtualized so only what is on screen is built. Parsha and Daf Yomi logic comes from [KosherJava Zmanim](https://github.com/KosherJava/zmanim) (LGPL).

Build: `./gradlew assembleRelease` (JDK 17+, Android SDK 36). Output: `app/build/outputs/apk/release/app-release.apk` (signed with the debug key, for sideloading).
