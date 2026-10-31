package com.fatalpuppet.zombicide

data class MissionInfo(
    val id: String,
    val name: String,
    val difficulty: String,
    val duration: String,
    val page: Int,
    val tiles: List<String>,
    val briefing: String,
    val objectives: List<String>,
    val specialRules: List<String>,
    val objectiveCount: Int,
    val crateCount: Int,
    val coloredObjectives: Boolean = false,
    val openDoorCount: Int = 0,
    val blueObjectiveCount: Int = 0,
    val greenObjectiveCount: Int = 0,
    val blueSpawnCount: Int = 0,
    val greenSpawnCount: Int = 0,
    val policeCarCount: Int = 0,
    val muscleCarCount: Int = 0,
    val nonDrivablePoliceCar: Boolean = false,
    val nonDrivableMuscleCar: Boolean = false,
    val blueDoorCount: Int = 0,
    val greenDoorCount: Int = 0,
    val abominationCount: Int = 0,
    val nedCount: Int = 0,
    val brokenDoorCount: Int = 0,
    val crashedCarCount: Int = 0,
    val extraSurvivorCount: Int = 0,
    val permanentNoiseTokensPerHighlightedZone: Int = 0
)

data class WashingtonMissionInfo(val name: String, val difficulty: String, val duration: String, val tiles: List<String>, val objective: String, val briefing: String = "", val objectives: List<String> = listOf(objective), val specialRules: List<String> = emptyList(), val materials: List<String> = emptyList())

data class FortMissionInfo(
    val name: String,
    val difficulty: String,
    val duration: String,
    val tiles: List<String>,
    val objective: String,
    val startingHour: String, val briefing: String = "", val objectives: List<String> = listOf(objective), val specialRules: List<String> = emptyList(), val materials: List<String> = emptyList()
)

data class DigitalMissionInfo(
    val id: String,
    val name: String,
    val difficulty: String,
    val duration: String,
    val survivors: String,
    val briefing: String,
    val materials: String,
    val tiles: List<String>,
    val pdfPage: Int,
    val objectives: List<String>,
    val specialRules: List<String>
)
