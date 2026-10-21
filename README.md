Zombicide Companion
An Android companion app for Zombicide 2nd Edition, intended to help a gaming group prepare missions, consult rules, and move through the setup process.
Project status: early availability of full core set / testing additional content from Washington ZC expansion. The latest prepared source is 1.0.0 (21 October 2026).

Current features
- Mission selection for a growing subset of the core rulebook missions.
- Mission overview with briefing, objectives, difficulty, estimated duration, and setup information as represented in the app data.
- Tile preparation based on the selected mission's tile list.
- Component preparation checklist with progress tracking.
- Mission-dependent checklist entries for:
    - Spawn Zone tokens, including colored Spawn Zones where configured.
    - Red, Blue, and Green Objective tokens where required.
    - Epic Weapon Crates.
    - Exit Zone tokens where configured.
    - Open Door tokens only for missions that require them.
    - Police Car and Muscle Car tokens as separate entries, including non-drivable car cases where represented.
- Survivor Selection, player-dashboard preparation guidance, and first-player selection guidance.
- Map Layout stage and a BEGIN! action to proceed to the gameplay screen.
- Rules section based on extracted core rulebook text, with rule lookup/detail views.
- Navigation back to Home from the gameplay/rules flow.
  Core missions currently listed
  The current mission-selection source contains these core missions:
- M0 — Zombicide Life (Tutorial)
- M1 — City Blocks
- M2 — Y-Zone
- M3 — The 24hrs Race of Zombicity
- M4 — Drive-by Shooting
- M5 — Big W
- M6 — The Escape
- M8 — Zombie Police
- M9 — Might Makes Right
- M10 — Small Town
- M11 — The Ditch
- M15 — United We Stand
- M16 — Pale Shelter
- M18 — The End of the Road
- M19 — Best Friends Forever
- M20 — The Zombiefest
- M21 — Heavy-duty
- M22 — Crank It Up to 11
- M23 — Mercy Street
- M24 — Ram Speed
- M25 — Ned's Moloturbo

  This is a selected subset, not the complete core mission book. Missions not listed above have not yet been added to the current mission-selection list.
  Current setup flow
  The intended main flow is:
1. Home — choose Start or Rules.
2. Rules source — select Core, Washington Z.C., Fort Hendrix, or Digital.
3. Mission selection — choose a core mission.
4. Mission overview — review the briefing and objectives.
5. Tiles / materials preparation — prepare required tiles and tick off the required components.
6. Survivor Selection — prepare survivor miniatures and dashboards.
7. First Player — follow the first-player setup instructions.
8. Map Layout — arrange tiles and place mission components.
9. Gameplay — access rules and return to Home.
   Exact navigation and content should be checked in the running app because this README describes the intended/current source flow, not a tested release.
   Rules-source availability
- Core: the current mission data is for Zombicide 2nd Edition core missions.
- Washington Z.C.: currently being implemented; mission data has been added, finalizing additional rules, and images.
- Fort Hendrix: listed as a future source; mission data has not been added.
- Digital: future import/source option; not implemented as a full mission library.
  Technology and development environment
  The project has been developed with:
- Kotlin
- Jetpack Compose / Material 3
- Android Studio 2026.1.2 Patch 1
- compileSdk 37
- Kotlin 2.2.10
- Android Gradle Plugin 9.3.1
- Compose BOM 2026.02.01
- Gradle 9.8.0 (project requirement)
  Application package: com.fatalpuppet.zombicide