# Zombicide Companion mission data split

Copy the Kotlin files under `app/src/main/java/com/fatalpuppet/zombicide/` into the matching package path. `MainActivity.kt` remains in the root package; mission data files are under `data/missions/` in the filesystem but deliberately retain package `com.fatalpuppet.zombicide` to avoid changing call sites.

Files: MissionData, CoreMissions, WashingtonMissions, FortHendrixMissions, DigitalMissions, RioMissions, ParizMissions. Rio and Pariz are empty placeholders pending source material.

Important: existing mission setup metadata and tile lists were retained. Full source-derived detail was transcribed for Washington M1–M5 and Fort Hendrix M1–M5. Washington M6–M10 and Fort Hendrix M6–M10 retain their prior concise metadata pending complete transcription; they are not replaced with invented rules. No Gradle build was run.
