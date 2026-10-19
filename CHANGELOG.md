# Changelog

## 0.4.0
- Updated Android Gradle Plugin to 9.4.0 and configured Gradle wrapper distribution for Gradle 9.6.0.
- Added an offline mission/component catalogue keyed by set and mission number.
- Added source-derived catalogue entries for Core missions M0, M1, and M2. Unknown quantities remain `null` rather than being guessed.
- Set app version to 0.4.0.

## 0.3.0
- Added required-token and component panel to the tile setup screen.

## 0.2.0
- Added home, source selection, mission overview, and tile setup flow.

## 0.5.0

- Updated the Gradle Wrapper distribution to Gradle 9.8.0.
- Added interactive preparation checkboxes for required tiles, mission tokens/components, and starting Zombie miniatures.
- Added a preparation progress count; checklist state is retained while navigating between screens during the current app session.
- Corrected the `MissionComponent` declaration so it is a regular data class.
