# Zombicide Companion — 0.5.0

An offline-first Android companion for Zombicide 2nd Edition. This is an early source project, not a verified APK.

## Included

- Home screen with a prominent **Start** button and a separate **Rules reference** button.
- Rules-source selection: Core, Washington Z.C., Fort Hendrix, and Digital.
- Mission selection flow. The first available mission is M0 — **Zombicide Life (Tutorial)** from the core rulebook.
- Mission overview with difficulty, estimated duration, briefing, objectives, special setup, and tile requirements.
- Tile layout screen for M0: tile 1V on the left and tile 3V on the right, following the mission diagram on core rulebook page 36.
- Searchable core-rules reference, with detail views and rulebook page references.
- Offline rules data stored in `app/src/main/assets/rules/core_rules.json`.

## Open in Android Studio

1. Extract the ZIP.
2. Open the `ZombicideCompanion` directory in Android Studio.
3. Use the Embedded JDK / a compatible JDK and allow Gradle sync to finish. The included `gradlew` and `gradlew.bat` launchers download and run the pinned Gradle 9.8.0 distribution when invoked from a terminal; Android Studio may still require its standard wrapper JAR for IDE-managed wrapper sync.
4. Run the `app` configuration on an Android device or emulator.

The project uses Gradle 9.8.0 via `gradle/wrapper/gradle-wrapper.properties`, Android Gradle Plugin 9.4.0, `compileSdk 37`, `minSdk 26`, Kotlin 2.2.10, Jetpack Compose, and Material 3. Plugin and dependency versions may need adjustment for the installed Android Studio / SDK setup.

## Current limitations

- The project has not been built or tested in this environment. Run Gradle Sync and Build in Android Studio to verify it locally.
- Only M0 has mission-specific content. Other core missions, Washington Z.C., Fort Hendrix, and Digital mission import are placeholders.
- The tile screen provides a checklist for required tiles, tokens/components, and starting Zombie miniatures. Guided map placement of Spawn Zones, tokens, Survivors, and Zombies is not implemented yet.
- Mission briefing text is a concise paraphrase of the uploaded core rulebook. Consult the printed rulebook for the full mission map and exact component placement.
- Rule summaries are concise paraphrases, not a complete reproduction or definitive rules adjudicator.

## Version 0.2.0 changes

- Replaced the Rules/Setup tabs with a home screen featuring Start and a separate Rules reference button.
- Added rules-source selection and a mission-selection step.
- Added the M0 mission overview and Begin action.
- Added the first tile-layout screen, with Back navigation to the mission overview.


## Version 0.3.0

The M0 Tiles & layout screen now lists the required mission tokens/components: 2 Spawn Zone tokens, 1 Exit Zone token, 2 Objective tokens, 2 Epic Weapon Crates, and no Open Door tokens. It also reiterates the starting Zombie miniatures. Counts are based on the M0 mission page (printed page 36) in the provided core rulebook.

## Mission component catalogue

`app/src/main/assets/missions/mission_components.json` stores mission data by set and mission ID. It supports tiles, Spawn Zones, Exit Zones, Objectives, Epic Weapon Crates, Open Door tokens, starting enemies, source pages, and unknown/unverified quantities (`null`). M0 is populated from the supplied core rulebook. M1 and M2 have source-derived tile and objective/crate counts; Spawn Zone counts are intentionally left unverified rather than guessed. Expansion mission data is pending the relevant source files.


## Version 0.5.0

- Uses Gradle 9.8.0 exactly, with Android Gradle Plugin 9.4.0.
- Adds tick boxes to mark tiles, required components, and starting Zombie miniatures as prepared.
- Shows preparation progress and retains checkbox state while moving between screens during the same app session.

Note: the source archive has been checked for ZIP integrity, but an Android build has not been verified in this environment. Gradle 9.8.1 is a newer security/bug-fix patch; this project intentionally pins 9.8.0 to match the requested version.
