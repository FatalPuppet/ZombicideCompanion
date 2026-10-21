# Changelog

All notable changes to **Zombicide Companion** are documented here. The project is in early development; version labels refer to incremental source patches, not verified production releases.

## [1.0.0] - 2026-10-21

### Added
- Core missions completed.
- Mission map with token position has been added.
- Added proper image for zombies, survivor selection, and player dashboard.

### Updated
- Rules reference in Mission in Progress pane has complete rules, searchable by text.
- Possible to mark a mission as complete.
- Starting Spawn and Red Spawn tokens are now shown as separate entities.

### Validation
- Integrity and source have been checked and reported passing.
- Build tested in both Virtual and Physical devices (Samsung S25 Ultra).

## [0.7.3] - 2026-10-19

### Added
- Core missions M8 — Zombie Police, M11 — The Ditch, M16 — Pale Shelter, M18 — The End of the Road, M20 — The Zombiefest, M22 — Crank It Up to 11, and M25 — Ned's Moloturbo.
- Mission-specific setup data for tiles, objectives, Epic Weapon Crates, colored Spawn Zones and Objectives, car tokens, Open Door tokens, and special setup elements where represented in mission data.
- Additional mission entries in the core mission selection list.

### Updated
- Preparation checklist supports more mission-specific component combinations, including Police Car and Muscle Car tokens and car restrictions.
- The checklist conditionally displays component rows instead of irrelevant zero-count rows.

### Validation
- ZIP integrity and basic source checks were reported as passing.
- An Android/Gradle build has not been run; compilation and in-app behavior still require testing.

## [0.7.2] - 2026-10-19

### Added
- Core missions M3 — The 24hrs Race of Zombicity, M4 — Drive-by Shooting, M6 — The Escape, M9 — Might Makes Right, M19 — Best Friends Forever, M21 — Heavy-duty, and M24 — Ram Speed.
- Mission-specific Police Car and Muscle Car preparation entries, including non-drivable car cases where represented.
- Additional mission-specific colored Objective and Spawn Zone setup support.

### Validation
- ZIP integrity and basic source checks were reported as passing.
- An Android/Gradle build was not run.

## [0.7.1] - 2026-10-19

### Added
- Mission M5 — Big W.
- Open Door token count support in mission data and the preparation checklist.
- Separate checklist entries for Red, Blue, and Green Objective tokens when a mission uses colored Objectives.

### Updated
- Open Door tokens are shown only for missions that require them.
- M5 setup includes two Open Door tokens and its colored Objective setup.

### Validation
- ZIP integrity was checked; no Android/Gradle build was run.

## [0.7.0] - 2026-10-19

### Added
- Core missions M2 — Y-Zone, M10 — Small Town, M15 — United We Stand, and M23 — Mercy Street.
- Colored Objective handling for M23, including separate Blue and Green Objective checklist entries.

### Validation
- Incremental source patch; build status was not verified.

## Earlier project state

### 0.6.9
- Simplified Survivor Selection instructions.

### 0.6.8
- Added mission M1 — City Blocks.

### 0.6.7
- Cleaned up mission selection: the mission card is the selection target; removed the separate “Select Mission” prompt and bottom Continue button.

### 0.6.6
- Moved the preparation progress tracker outside the scrolling component list so it stays in a stable position.
- Added a Gameplay screen with a rules button and a route back to Home.
- Added extracted core rulebook text asset for the rules view.

### 0.6.5
- Updated the First Player screen to use instructions and an empty-circle placeholder rather than a player-number/pip selection control.
- Added the red **BEGIN!** button on the map layout screen to start gameplay.

## Development notes

- Versions are patch labels used during iterative development.
- Unless explicitly stated above, a version should not be assumed to have been compiled, installed, or play-tested.
- Mission setup values and rules should be checked against the physical rulebook before relying on them during play.
