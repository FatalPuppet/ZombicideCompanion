package com.fatalpuppet.zombicide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.aspectRatio
import android.content.Context
import org.json.JSONObject
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatalpuppet.zombicide.data.RuleEntry
import com.fatalpuppet.zombicide.data.RuleRepository
import com.fatalpuppet.zombicide.data.RuleSection

private enum class AppScreen {
    HOME, RULES, RULE_SOURCE, MISSION_SELECT, MISSION_OVERVIEW, TILES, PLAYER_SELECTION, PLAYER_DASHBOARD, FIRST_PLAYER, MAP_LAYOUT, GAMEPLAY, RULEBOOK
}

private data class RuleSourceOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val available: Boolean
)

private val ruleSources = listOf(
    RuleSourceOption("core", "Core", "Zombicide 2nd Edition core missions", true),
    RuleSourceOption("washington", "Washington Z.C.", "Expansion missions", false),
    RuleSourceOption("fort", "Fort Hendrix", "Expansion missions", false),
    RuleSourceOption("digital", "Digital", "Use a digital mission source", false)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = RuleRepository(applicationContext)
        val sections = repository.loadSections()
        val missionTiles = loadMissionTiles(applicationContext, "core", "M0")
        setContent { CompanionApp(sections, missionTiles) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanionApp(sections: List<RuleSection>, missionTiles: List<String>) {
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var query by remember { mutableStateOf("") }
    var selectedEntry by remember { mutableStateOf<Pair<RuleSection, RuleEntry>?>(null) }
    var selectedSource by remember { mutableStateOf("core") }
    var selectedMissionId by remember { mutableStateOf("M0") }
    val context = LocalContext.current
    val selectedMissionTiles = remember(selectedMissionId) { loadMissionTiles(context, "core", selectedMissionId) }
    var preparedItems by remember { mutableStateOf(setOf<String>()) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text("Zombicide Companion", fontWeight = FontWeight.Bold)
                        Text("2nd Edition", style = MaterialTheme.typography.labelMedium)
                    }
                })
            }
        ) { padding ->
            Surface(Modifier.fillMaxSize().padding(padding)) {
                when {
                    selectedEntry != null -> RuleDetail(
                        selectedEntry!!.first,
                        selectedEntry!!.second,
                        onBack = { selectedEntry = null }
                    )
                    screen == AppScreen.HOME -> HomeScreen(
                        onStart = { screen = AppScreen.RULE_SOURCE },
                        onRules = { query = ""; screen = AppScreen.RULES }
                    )
                    screen == AppScreen.RULES -> RulesScreen(
                        sections = sections,
                        query = query,
                        onQueryChange = { query = it },
                        onSelectEntry = { selectedEntry = it },
                        onBack = { screen = AppScreen.HOME }
                    )
                    screen == AppScreen.RULE_SOURCE -> SourceSelectionScreen(
                        selectedSource = selectedSource,
                        onSelectSource = { selectedSource = it },
                        onBack = { screen = AppScreen.HOME },
                        onContinue = { screen = AppScreen.MISSION_SELECT }
                    )
                    screen == AppScreen.MISSION_SELECT -> MissionSelectionScreen(
                        selectedSource = selectedSource,
                        onBack = { screen = AppScreen.RULE_SOURCE },
                        onSelectMission = { missionId ->
                            selectedMissionId = missionId
                            preparedItems = emptySet()
                            screen = AppScreen.MISSION_OVERVIEW
                        }
                    )
                    screen == AppScreen.MISSION_OVERVIEW -> MissionOverviewScreen(
                        missionId = selectedMissionId,
                        onBack = { screen = AppScreen.MISSION_SELECT },
                        onBegin = { screen = AppScreen.TILES }
                    )
                    screen == AppScreen.TILES -> TileSetupScreen(
                        missionId = selectedMissionId,
                        tiles = selectedMissionTiles,
                        preparedItems = preparedItems,
                        onTogglePrepared = { itemId ->
                            preparedItems = if (itemId in preparedItems) preparedItems - itemId else preparedItems + itemId
                        },
                        onBack = { screen = AppScreen.MISSION_OVERVIEW },
                        onSetup = { screen = AppScreen.PLAYER_SELECTION }
                    )
                    screen == AppScreen.PLAYER_SELECTION -> PlayerSelectionScreen(
                        onBack = { screen = AppScreen.TILES },
                        onContinue = { screen = AppScreen.PLAYER_DASHBOARD }
                    )
                    screen == AppScreen.PLAYER_DASHBOARD -> PlayerDashboardScreen(
                        onBack = { screen = AppScreen.PLAYER_SELECTION },
                        onContinue = { screen = AppScreen.FIRST_PLAYER }
                    )
                    screen == AppScreen.FIRST_PLAYER -> FirstPlayerScreen(
                        onBack = { screen = AppScreen.PLAYER_DASHBOARD },
                        onContinue = { screen = AppScreen.MAP_LAYOUT }
                    )
                    screen == AppScreen.MAP_LAYOUT -> MapLayoutScreen(
                        tiles = selectedMissionTiles,
                        missionId = selectedMissionId,
                        onBack = { screen = AppScreen.FIRST_PLAYER },
                        onContinue = { screen = AppScreen.GAMEPLAY }
                    )
                    screen == AppScreen.GAMEPLAY -> GameplayScreen(
                        onRuleSet = { screen = AppScreen.RULEBOOK },
                        onHome = { screen = AppScreen.HOME }
                    )
                    screen == AppScreen.RULEBOOK -> RulebookScreen(
                        onBack = { screen = AppScreen.GAMEPLAY }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onStart: () -> Unit, onRules: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))
        Text("Ready for the next mission?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Set up your game one step at a time.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(76.dp)) {
            Text("START", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRules, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Rules reference")
        }
        Spacer(Modifier.weight(1f))
        Text("Offline companion · Core mission setup in progress", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SourceSelectionScreen(
    selectedSource: String,
    onSelectSource: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Choose a rules source", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Select the rulebook or mission source for this game.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ruleSources.forEach { source ->
                val selected = selectedSource == source.id
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectSource(source.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(source.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(3.dp))
                            Text(source.subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (selected) "✓" else if (source.available) "›" else "Later", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        BottomNavigationRow(onBack = onBack, primaryLabel = "Continue", onPrimary = onContinue)
    }
}

@Composable
private fun MissionSelectionScreen(
    selectedSource: String,
    onBack: () -> Unit,
    onSelectMission: (String) -> Unit
) {
    val source = ruleSources.first { it.id == selectedSource }
    val coreMissions = listOf(
        Triple("M0", "Zombicide Life", "Easy · 30 minutes"),
        Triple("M1", "City Blocks", "Medium · 45 minutes"),
        Triple("M2", "Y-Zone", "Hard · 60 minutes"),
        Triple("M3", "The 24hrs Race of Zombicity", "Medium · 90 minutes"),
        Triple("M4", "Drive-by Shooting", "Medium · 90 minutes"),
        Triple("M5", "Big W", "Hard · 90 minutes"),
        Triple("M6", "The Escape", "Hard · 90 minutes"),
        Triple("M7", "Grindhouse", "Hard · 45 minutes"),
        Triple("M8", "Zombie Police", "Hard · 30 minutes"),
        Triple("M9", "Might Makes Right", "Medium · 60 minutes"),
        Triple("M10", "Small Town", "Easy · 30 minutes"),
        Triple("M11", "The Ditch", "Medium · 30 minutes"),
        Triple("M12", "Car Crash", "Medium · 60 minutes"),
        Triple("M13", "Burning Streets", "Medium · 45 minutes"),
        Triple("M14", "Breakfast at Jesse's", "Medium · 45 minutes"),
        Triple("M15", "United We Stand", "Medium · 45 minutes"),
        Triple("M16", "Pale Shelter", "Medium · 45 minutes"),
        Triple("M17", "The Blight", "Hard · 60 minutes"),
        Triple("M18", "The End of the Road", "Hard · 60 minutes"),
        Triple("M19", "Best Friends Forever", "Hard · 45 minutes"),
        Triple("M20", "The Zombiefest", "Hard · 45 minutes"),
        Triple("M21", "Heavy-duty", "Hard · 60 minutes"),
        Triple("M22", "Crank It Up to 11", "Hard · 45 minutes"),
        Triple("M23", "Mercy Street", "Medium · 45 minutes"),
        Triple("M24", "Ram Speed", "Hard · 60 minutes"),
        Triple("M25", "Ned's Moloturbo", "Hard · 90 minutes")
    )
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Choose a mission", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Source: ${source.title}", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(16.dp))
        if (selectedSource == "core") {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(coreMissions) { mission ->
                    Card(Modifier.fillMaxWidth().clickable { onSelectMission(mission.first) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${mission.first} · ${mission.second}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("Difficulty: ${mission.third.substringBefore(" · ")}", style = MaterialTheme.typography.bodyMedium)
                            Text("Estimated time: ${mission.third.substringAfter(" · ")}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        } else {
            Card(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Mission library not added yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (selectedSource) {
                            "washington" -> "Washington Z.C. mission data will be added when its digital rulebook is provided."
                            "fort" -> "Fort Hendrix mission data will be added when its digital rulebook is provided."
                            else -> "Digital mission import is planned for a later step."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), horizontalArrangement = Arrangement.Start) {
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}

private data class MissionInfo(
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

private val coreMissionInfo = mapOf(
    "M0" to MissionInfo("M0", "Zombicide Life", "Easy", "30 minutes", 36, listOf("1V", "3V"),
        "The tutorial mission introduces the basic setup and objective flow.",
        listOf("Take all Objective tokens and all Epic Weapon Crates.", "Reach the Exit with all Survivors. A Survivor may escape at the end of their Turn only if no Zombies are in the Exit Zone."),
        listOf("Place 1 Brute, 1 Runner, and 2 Walkers in the Zones indicated by the mission map.", "Each Objective grants 5 Adrenaline Points to the Survivor who takes it.", "Each Epic Weapon Crate gives the Survivor who takes it a random available Epic Weapon."), 2, 2),
    "M1" to MissionInfo("M1", "City Blocks", "Medium", "45 minutes", 37, listOf("1V", "7R", "3R", "5R", "9R", "6R", "8V", "2V", "4V"),
        "Our shelter is safe for now, but food supplies will not last. The Survivors must explore the surrounding houses and gather supplies.",
        listOf("Get an Objective token with each Survivor. The game is lost if this objective can no longer be met.", "Get 3 food cards: Bag Of Rice, Canned Food, or Water. They may be distributed among Survivors.", "Then reach the Exit with all Survivors. A Survivor may escape at the end of their Turn only if no Zombies are in the Exit Zone."),
        listOf("Each Objective grants 5 Adrenaline Points; place it on the Survivor’s Dashboard. It does not take inventory space and cannot be traded.", "Each Epic Weapon Crate gives the Survivor who takes it a random available Epic Weapon."), 9, 9),
    "M2" to MissionInfo("M2", "Y-Zone", "Hard", "60 minutes", 38, listOf("7V", "5V", "2V", "9R", "6R", "4R", "1V", "8V", "3V"),
        "The pockets of resistance are falling, communications have gone silent, and the Survivors must leave as supplies dwindle.",
        listOf("Arm the team: provide the team with as many Epic Weapons as there are Survivors, up to the number available.", "Reach the Exit with all Survivors. A Survivor may escape at the end of their Turn only if no Zombies are in the Exit Zone."),
        listOf("Each Objective grants 5 Adrenaline Points to the Survivor who takes it.", "Each Epic Weapon Crate gives the Survivor who takes it a random available Epic Weapon."), 9, 9),
    "M3" to MissionInfo("M3", "The 24hrs Race of Zombicity", "Medium", "90 minutes", 39, listOf("2V", "8V", "9V", "7R", "3R", "6R", "1V", "5V", "4V"),
        "The Survivors search a rest home and nearby vehicles for supplies while surrounded by zombies.",
        listOf("Reach the Red Danger Level with at least 1 Survivor."),
        listOf("Cars can be driven. The Muscle Car contains an Epic Weapon Crate; searching the Police Car continues until a weapon is drawn, with Aaahh!! interrupting the Search.", "Each Objective grants 5 Adrenaline Points.", "Each Epic Weapon Crate grants a random available Epic Weapon."), 9, 10, policeCarCount = 1, muscleCarCount = 1),
    "M4" to MissionInfo("M4", "Drive-by Shooting", "Medium", "90 minutes", 40, listOf("5R", "7R", "4V", "3V", "6V", "8V"),
        "The Survivors must use the cars to destroy Red Spawn Zones and stop the relentless attacks on their shelter.",
        listOf("1. Blast the cars to block the main routes by destroying Red Spawn Zones. If this becomes impossible, the game is lost.", "2. Throw a Molotov at the Starting Spawn Zone."),
        listOf("Cars can be driven. The Muscle Car contains an Epic Weapon Crate; the Police Car can be searched for a weapon.", "Cars can be targeted by Ranged Attacks; one hit makes a car explode and resolves a Molotov effect. Destroying a car on a Red Spawn Zone moves that Spawn token to the Starting Spawn Zone.", "Each Objective grants 5 Adrenaline Points. Each Epic Weapon Crate grants a random available Epic Weapon."), 6, 8, policeCarCount = 1, muscleCarCount = 1),
    "M5" to MissionInfo("M5", "Big W", "Hard", "90 minutes", 41, listOf("8V", "1V", "7R", "4R", "5R", "9R", "2V", "3V", "6R"),
        "The Survivors search the infested city-center office buildings for supplies and a safer place to survive.",
        listOf("Open all buildings.", "Take at least 5 Objectives, including at least 1 colored Objective.", "Then reach the Exit with all Survivors. A Survivor may escape at the end of their Turn only if no Zombies are in the Exit Zone."),
        listOf("Place the Blue and Green Objectives randomly among the Red Objectives, facedown. At least one colored Objective must be taken to win.", "Tile 9R has two Open Doors and receives neither an Objective token nor an Epic Weapon Crate.", "Each Objective grants 5 Adrenaline Points to the Survivor who takes it.", "Each Epic Weapon Crate gives the Survivor who takes it a random available Epic Weapon."), 8, 8, true, 2),
    "M6" to MissionInfo("M6", "The Escape", "Hard", "90 minutes", 42, listOf("5R", "9R", "4V", "1V", "6V", "8V"),
        "The Survivors raid a Z-Mart for food and must escape with their supplies in the cars.",
        listOf("Gather 6 food cards (Canned Food, Rice, or Water).", "Reach the Exit with all Survivors inside the cars, carrying any stashed food. If this can no longer be achieved, the game is lost."),
        listOf("Cars can be driven. The Muscle Car contains an Epic Weapon Crate; the Police Car can be searched for a weapon.", "Survivors can spend 1 Action to stash food cards in a car in their Zone.", "Each Objective grants 5 Adrenaline Points. Each Epic Weapon Crate grants a random available Epic Weapon."), 6, 7, policeCarCount = 1, muscleCarCount = 1),
    "M7" to MissionInfo("M7", "Grindhouse", "Hard", "45 minutes", 43, listOf("3V", "4V", "6V", "8V"),
        "An accident at the nuclear power plant left technicians quarantined in a hospital wing. Prevent the infected from escaping.",
        listOf("Secure the buildings by gathering all Zombie Spawn tokens on the Starting Spawn Zone.", "Throw a Molotov at the Starting Spawn Zone to lock the infected in for good. The game is lost whenever a Zombie starts its Activation in a highlighted Zone."),
        listOf("Place 3 permanent Noise tokens in each highlighted Zone; they are not removed during the End Phase.", "At the end of Setup, spawn Zombies in both buildings on tiles 4V and 8V as if their doors had just been opened.", "A Survivor standing in an empty Spawn Zone may spend 1 Action to neutralize it by moving its Zombie Spawn token to the Starting Spawn Zone.", "Each Objective grants 5 Adrenaline Points. Each Epic Weapon Crate grants a random available Epic Weapon."), 4, 4, permanentNoiseTokensPerHighlightedZone = 3),
    "M8" to MissionInfo("M8", "Zombie Police", "Hard", "30 minutes", 44, listOf("3V", "4V", "5V", "6R", "8V", "9R"),
        "The Survivors discover a memo pointing to a bunker near the police station, but its remote-controlled entrance is locked and the district is overrun.",
        listOf("Take the Blue and Green Objectives to find the remote controls.", "Reach the Exit with all Survivors; the Exit Zone is usable only when no Zombies are in it."),
        listOf("Place the Blue and Green Objectives in the indicated Zones. Each Objective grants 5 Adrenaline Points.", "The building with the Exit cannot be opened until both colored Objectives have been taken.", "Cars can be driven. The Muscle Car contains an Epic Weapon Crate. When searching the Police Car, draw until a weapon is found; Aaahh!! interrupts the Search.", "Each Epic Weapon Crate grants a random available Epic Weapon."), 2, 7, true, policeCarCount = 1, muscleCarCount = 1),
    "M11" to MissionInfo("M11", "The Ditch", "Medium", "30 minutes", 47, listOf("4R", "2R", "9V", "7V"),
        "The Survivors must cross the narrow Ditch and return to their shelter before the weather and the zombie horde catch them.",
        listOf("Reach the Exit with all Survivors. Survivors may escape at the end of their Turn if the Exit Zone has no Zombies and no Abominations remain on the board."),
        listOf("The team starts with 2 Companions distributed among the Survivors. Place an Abomination in the indicated Zone; it does not move until there is an open path to the Survivors.", "Each Objective grants 5 Adrenaline Points. Each Epic Weapon Crate grants a random available Epic Weapon.", "The two Muscle Cars cannot be driven. Each contains an Epic Weapon; place an Epic Weapon Crate on each."), 4, 6, muscleCarCount = 2, nonDrivableMuscleCar = true, abominationCount = 1),
    "M12" to MissionInfo("M12", "Car Crash", "Medium", "60 minutes", 48, listOf("1R", "2R", "6R", "7V", "8R", "9V"),
        "Six injured Survivors are trapped in the wrecked car. Find first aid kits and turn them into Companions before the zombies reach them.",
        listOf("Turn all 6 injured Survivors in the Zone on tile 8R into Companions by delivering 6 first aid kits represented by Red Objectives.", "The game is lost whenever a Zombie activates in the injured Survivors' Zone."),
        listOf("Note the open doors on tile 8R. Place 6 unused Survivor miniatures in the indicated injured-Survivor Zone; set their ID Cards aside. They cannot act or carry Equipment, but count as Noise tokens.", "Each Red Objective grants 5 Adrenaline Points and a first aid kit. Place it on the Survivor's Dashboard; it takes no inventory slot and can be traded. Spend 1 Action to drop a Red Objective in the injured-Survivor Zone to turn one injured Survivor into a Companion.", "The crashed car cannot be driven or searched. Each Epic Weapon Crate grants a random available Epic Weapon."), 6, 6, crashedCarCount = 1, extraSurvivorCount = 6),
    "M13" to MissionInfo("M13", "Burning Streets", "Medium", "45 minutes", 49, listOf("1R", "2V", "3V", "4V", "6R", "7V"),
        "A massive zombie herd is approaching the shelter. Burn the side accesses, then block the road and torch the cars to divert the horde.",
        listOf("Throw a Molotov at each Red Spawn Zone to move its Red Zombie Spawn token to the Starting Spawn Zone.", "Drive both Cars to the highlighted parking Zone. Both must be parked there to win."),
        listOf("The Blue Objective is on tile 2V and the Green Objective is on tile 3V. Each grants 5 Adrenaline Points and a Molotov; treat the token as a Molotov card when taken.", "Throwing a Molotov at a Red Spawn Zone moves that Spawn token to the Starting Spawn Zone.", "Cars can be driven. The Muscle Car contains an Epic Weapon Crate. When searching the Police Car, draw until a weapon is found; Aaahh!! interrupts the Search.", "Each Epic Weapon Crate grants a random available Epic Weapon."), 3, 7, blueObjectiveCount = 1, greenObjectiveCount = 1, policeCarCount = 1, muscleCarCount = 1),
    "M14" to MissionInfo("M14", "Breakfast at Jesse's", "Medium", "45 minutes", 50, listOf("2V", "4R", "6R", "7V", "8R", "9V"),
        "Jesse's Diner is sealed behind reinforced doors, with people trapped inside. Rally the Companions and bring everyone to the Exit.",
        listOf("Rally all Companions and gather all Survivors on the Exit Zone, with no Zombies in it.", "The game is lost if a Survivor, including one on tile 4R, is eliminated."),
        listOf("Note the closed door between tiles 7V and 8R, the Green Objective on tile 2V, and the Green Door on tile 4R. Place 6 unused Survivor miniatures on the Exit Zone as Companions to rally.", "The door between tiles 7V and 8R makes the two parts of that building count as separate buildings for Zombie spawning only.", "Doors in the building spanning tiles 4R and 9V cannot be opened, except for the Green Door. The Green Door cannot be opened until the Green Objective has been taken.", "Each Objective grants 5 Adrenaline Points. Each Epic Weapon Crate grants a random available Epic Weapon."), 5, 6, greenObjectiveCount = 1, greenDoorCount = 1, extraSurvivorCount = 6),
    "M16" to MissionInfo("M16", "Pale Shelter", "Medium", "45 minutes", 52, listOf("4V", "2V", "3V", "1V"),
        "The Survivors find a promising shelter with reinforced doors, though several doors have been broken by an Abomination.",
        listOf("Barricade the broken doors using Objective tokens.", "Secure the shelter by eliminating all Zombies remaining in the building."),
        listOf("Note the doors on tiles 2V, 3V, and 4V. Place an Abomination in the indicated Zone.", "The four broken doors cannot be opened by Zombies or standard door-opening rules; Survivors may open them without door-opening Equipment. Zombies ignore them when moving and open them immediately if they pass through.", "Each Objective grants 5 Adrenaline Points. Spend 1 Action to place an Objective token from a Survivor's Dashboard into an empty Zone with a broken door to barricade it permanently.", "Each Epic Weapon Crate grants a random available Epic Weapon."), 4, 4, abominationCount = 1, brokenDoorCount = 4),
    "M17" to MissionInfo("M17", "The Blight", "Hard", "60 minutes", 53, listOf("1R", "2R", "3R", "4V", "5V", "6R", "7V", "8R", "9R"),
        "Cleanse the Blight: clear every Zombie from the building spanning tiles 7V, 5V, and 4V after opening it.",
        listOf("Win as soon as there are no Zombies in the Blight building, once it has been opened."),
        listOf("The Blight's doors can only be opened by Survivors at Orange Danger Level or higher.", "Each Objective grants 5 Adrenaline Points. Each Epic Weapon Crate grants a random available Epic Weapon."), 9, 9),
    "M18" to MissionInfo("M18", "The End of the Road", "Hard", "60 minutes", 54, listOf("4R", "7R", "1R", "2R", "6V", "3V"),
        "The Survivors discover two abandoned Muscle Cars and signs of people hiding nearby. They must find the survivors before the district overwhelms them.",
        listOf("Take all Objectives to save the survivors."),
        listOf("Shuffle 6 Red Objectives, the Blue Objective, and the Green Objective. Place 6 randomly on the indicated spaces, facedown, and set the remaining 2 aside without looking.", "Each Objective grants 5 Adrenaline Points. Taking a Red Objective also grants a Companion. The Blue Spawn Zone activates when the Blue Objective is taken; the Green Spawn Zone activates when the Green Objective is taken.", "The two Muscle Cars cannot be driven. Each contains an Epic Weapon; place an Epic Weapon Crate on each.", "Each Epic Weapon Crate grants a random available Epic Weapon."), 8, 8, true, blueSpawnCount = 1, greenSpawnCount = 1, muscleCarCount = 2, nonDrivableMuscleCar = true),
    "M20" to MissionInfo("M20", "The Zombiefest", "Hard", "45 minutes", 56, listOf("3V", "1R", "8R", "7R", "6V", "5V"),
        "The Survivors lure the district's zombie horde to a chosen location and try to wipe it out in a single, noisy party.",
        listOf("Take all Objectives.", "Empty the Zombie reserve."),
        listOf("Place the Blue and Green Objectives randomly among the Red Objectives, facedown. Note the Blue Door and Blue Spawn Zone on tile 6V, and the Green Door and Green Spawn Zone on tile 5V.", "Each Objective grants 5 Adrenaline Points. Taking a Blue or Green Objective also grants a Companion.", "The Blue and Green Doors open like standard doors. The matching colored Spawn Zone becomes active when its door opens. After either colored Door opens, eliminated Zombies are no longer returned to the reserve.", "Each Epic Weapon Crate grants a random available Epic Weapon."), 6, 6, true, blueSpawnCount = 1, greenSpawnCount = 1, blueDoorCount = 1, greenDoorCount = 1),
    "M22" to MissionInfo("M22", "Crank It Up to 11", "Hard", "45 minutes", 58, listOf("5V", "4R", "9V", "8V", "3R", "1R"),
        "The Survivors need to return to their shelter quickly, so they plan to lure the zombies out by turning the district's loudspeakers up to eleven.",
        listOf("Take all Objectives.", "Reach the Exit with all Survivors; the Exit Zone must contain no Zombies."),
        listOf("Each Objective grants 5 Adrenaline Points. Taking the Green Objective opens all closed doors on the board; spawn Zombies normally in buildings opened this way.", "Each Epic Weapon Crate grants a random available Epic Weapon."), 6, 6, greenObjectiveCount = 1),
    "M25" to MissionInfo("M25", "Ned's Moloturbo", "Hard", "90 minutes", 62, listOf("2R", "4V", "7R", "9R", "8R", "1R"),
        "A relentless Abomination follows the Survivors. They seek Ned, who can build a Moloturbo if they bring him the right components.",
        listOf("Bring a Molotov, the Blue Objective, and the Green Objective to Ned on tile 9R.", "Kill the Supabomination using Ned's Moloturbo."),
        listOf("Place the Blue and Green Objectives randomly among the Red Objectives, facedown. Note the open door on tile 9R. Place one Abomination and Ned in their indicated Zones.", "Whenever the Supabomination is eliminated before being killed by Ned's Moloturbo, return it to the Starting Spawn Zone.", "Each Objective grants 5 Adrenaline Points. The Blue and Green Objectives are recipe components; they can be dropped in Ned's Zone with 1 Action and cannot be recovered. When all three components are there, discard both colored Objectives and Ned grants a Survivor the Moloturbo.", "The Muscle Car can be driven and contains an Epic Weapon; place an Epic Weapon Crate on it. Each Epic Weapon Crate grants a random available Epic Weapon."), 6, 8, true, openDoorCount = 1, muscleCarCount = 1, abominationCount = 1, nedCount = 1),
    "M9" to MissionInfo("M9", "Might Makes Right", "Medium", "60 minutes", 45, listOf("4V", "2V", "9R", "3V"),
        "The Survivors locate a nearby group and set out to rescue three Companions.",
        listOf("Find the three Companions to win the game."),
        listOf("Place the Green Objective randomly among the Red Objectives, facedown. Taking it has no additional effect.", "Each Objective grants 5 Adrenaline Points. Taking a Red Objective also grants a Companion.", "The Police Car cannot be driven; when searching it, draw until a weapon card is drawn, with Aaahh!! interrupting the Search.", "Each Epic Weapon Crate grants a random available Epic Weapon."), 4, 4, greenObjectiveCount = 1, policeCarCount = 1, nonDrivablePoliceCar = true),
    "M10" to MissionInfo("M10", "Small Town", "Easy", "30 minutes", 46, listOf("2V", "1R", "7R", "5R"),
        "The Survivors explore the surrounding area in wider circles around their hideout to gather supplies.",
        listOf("Take all 4 Objectives.", "Take all 4 Epic Weapon Crates.", "Get a Molotov."),
        listOf("Each Objective grants 5 Adrenaline Points to the Survivor who takes it.", "Each Epic Weapon Crate gives the Survivor who takes it a random available Epic Weapon."), 4, 4),
    "M15" to MissionInfo("M15", "United We Stand", "Medium", "45 minutes", 51, listOf("1R", "2R", "8V", "4R", "9R", "3V", "7R", "5R", "6R"),
        "The Survivors must regroup in a safer building after a district raid attracts the infected.",
        listOf("Rally all Companions.", "Take all Objectives and Epic Weapon Crates in the building spanning tiles 3V and 8V."),
        listOf("Place 2 random Survivors in each Survivor Starting Zone, then choose 3 pairs to play. The remaining Survivors become Companions to rally.", "Each Objective grants 5 Adrenaline Points to the Survivor who takes it.", "Each Epic Weapon Crate gives the Survivor who takes it a random available Epic Weapon."), 9, 9),
    "M19" to MissionInfo("M19", "Best Friends Forever", "Hard", "45 minutes", 55, listOf("6V", "9V", "3V", "7R", "2R", "4R"),
        "The Survivors return to a looted district with Companion duos to gather supplies.",
        listOf("Win when every Survivor duo has swapped roles and the team has one Epic Weapon per Survivor, up to the number available."),
        listOf("Each Survivor starts with a rallied Companion; the duo cannot be separated. The Muscle Car contains an Epic Weapon and cannot be driven.", "Only Survivors at Yellow Danger Level or above can take Objectives. Each Objective grants 5 Adrenaline Points and swaps the Leader and Companion roles; each duo can take only one Objective.", "Each Epic Weapon Crate grants a random available Epic Weapon."), 7, 7, muscleCarCount = 1, nonDrivableMuscleCar = true),
    "M21" to MissionInfo("M21", "Heavy-duty", "Hard", "60 minutes", 57, listOf("8R", "3V", "9R", "7R", "2V", "1V"),
        "The Survivors must haul heavy supplies into a Muscle Car before leaving the area.",
        listOf("Stash all Objective tokens in the Muscle Car.", "Reach the Exit with all Survivors. The Muscle Car may escape with its passengers and stashed Objectives if no Zombies are in the Exit Zone."),
        listOf("Place the Blue Objective randomly among the Red Objectives, facedown. The Blue Spawn Zone becomes active when the Blue Objective is taken.", "Each Objective grants 5 Adrenaline Points and is carried on a Survivor Dashboard. Carrying one or more makes each Move Action cost 2 Actions and disables Move-related Skills. Spend 1 Action to drop an Objective into a Muscle Car in the same Zone with no Zombies.", "The Muscle Car can be driven and contains an Epic Weapon Crate. Each Epic Weapon Crate grants a random available Epic Weapon."), 6, 7, blueObjectiveCount = 1, blueSpawnCount = 1, muscleCarCount = 1),
    "M23" to MissionInfo("M23", "Mercy Street", "Medium", "45 minutes", 59, listOf("9V", "2R", "6R", "7V", "3R", "1R"),
        "The Survivors are worried about their neighbors on Mercy Street and set out to find them.",
        listOf("Rescue your fellow Survivors: find 4 Companions."),
        listOf("Place the Blue and Green Objectives randomly among the Red Objectives, facedown.", "Each Objective grants 5 Adrenaline Points. Taking a Red Objective also grants a Companion; taking a Blue or Green Objective spawns a Zombie card in that Survivor’s Zone (ignore Zombie Rushes).", "Each Epic Weapon Crate gives the Survivor who takes it a random available Epic Weapon."), 6, 6, true),
    "M24" to MissionInfo("M24", "Ram Speed", "Hard", "60 minutes", 60, listOf("3V", "6R", "8R", "9R", "7R", "4R", "1R", "2R", "5R"),
        "The Survivors must gather Epic Weapons, then use cars to escape through two Spawn Zones in a single Game Round.",
        listOf("Get as many Epic Weapons as there are Survivors, up to the number available.", "In one Game Round, throw Molotovs at both non-Starting Spawn Zones, then escape through those Zones with all Survivors aboard cars."),
        listOf("Place the Blue and Green Objectives randomly among the Red Objectives, facedown. Each Objective grants 5 Adrenaline Points; taking a Blue or Green Objective also grants a Molotov.", "Cars can be driven. When searching the Police Car, draw until a weapon card is drawn, with Aaahh!! interrupting the Search.", "Each Epic Weapon Crate grants a random available Epic Weapon."), 9, 9, true, policeCarCount = 1)
)

@Composable
private fun MissionOverviewScreen(missionId: String, onBack: () -> Unit, onBegin: () -> Unit) {
    val mission = coreMissionInfo[missionId] ?: coreMissionInfo.getValue("M0")
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("MISSION ${mission.id}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(mission.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${mission.difficulty} · About ${mission.duration}", style = MaterialTheme.typography.titleSmall)
            }
            item {
                Text("Mission briefing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(mission.briefing, style = MaterialTheme.typography.bodyLarge)
            }
            item {
                Divider()
                Text("Objectives", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                mission.objectives.forEach { BulletText(it) }
            }
            item {
                Divider()
                Text("Special rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                mission.specialRules.forEach { BulletText(it) }
            }
            item {
                Divider()
                Text("Tiles required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(mission.tiles.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                Text("Source: Core rulebook, mission page ${mission.page}.", style = MaterialTheme.typography.labelSmall)
            }
        }
        BottomNavigationRow(onBack = onBack, primaryLabel = "Begin", onPrimary = onBegin, primaryIsLarge = true)
    }
}

private data class MissionComponent(
    val name: String,
    val quantity: String,
    val placement: String
)

private val m0Components = listOf(
    MissionComponent("Spawn Zone tokens", "2", "Place at the Spawn Zone symbols on the mission map."),
    MissionComponent("Exit Zone token", "1", "Place at the Exit symbol, at the lower-left starting area."),
    MissionComponent("Objective tokens", "2", "Place in the Zones indicated by the mission map."),
    MissionComponent("Epic Weapon Crates", "2", "Place in the Zones indicated by the mission map."),
    MissionComponent("Open Door tokens", "0", "None specified for this mission.")
)

@Composable
private fun TileSetupScreen(
    missionId: String,
    tiles: List<String>,
    preparedItems: Set<String>,
    onTogglePrepared: (String) -> Unit,
    onBack: () -> Unit,
    onSetup: () -> Unit
) {
    val mission = coreMissionInfo[missionId] ?: coreMissionInfo.getValue("M0")
    val checklistComponents = buildList {
        add(MissionComponent("Red Spawn Zone tokens", "As shown", "Use the Red Spawn Zone symbols shown on the mission map."))
        if (mission.blueSpawnCount > 0) add(MissionComponent("Blue Spawn Zone token", mission.blueSpawnCount.toString(), "Keep aside until the Blue Spawn Zone becomes active, as specified by the mission."))
        if (mission.greenSpawnCount > 0) add(MissionComponent("Green Spawn Zone token", mission.greenSpawnCount.toString(), "Use the Green Spawn Zone symbol shown on the mission map."))
        if (missionId in listOf("M0", "M1", "M2", "M5", "M6", "M8", "M11", "M14", "M21", "M22")) {
            add(MissionComponent("Exit Zone token", "1", "Place at the Exit symbol shown on the mission map."))
        }
        val blueObjectives = if (mission.coloredObjectives) 1 else mission.blueObjectiveCount
        val greenObjectives = if (mission.coloredObjectives) 1 else mission.greenObjectiveCount
        val redObjectives = mission.objectiveCount - blueObjectives - greenObjectives
        if (redObjectives > 0) add(MissionComponent("Red Objective tokens", redObjectives.toString(), "Place on the Red Objective markings shown on the mission map."))
        if (blueObjectives > 0) add(MissionComponent("Blue Objective token", blueObjectives.toString(), "Place facedown randomly among the Red Objectives, if specified by the mission."))
        if (greenObjectives > 0) add(MissionComponent("Green Objective token", greenObjectives.toString(), "Place facedown randomly among the Red Objectives, if specified by the mission."))
        add(MissionComponent("Epic Weapon Crates", mission.crateCount.toString(), "Place on the Epic Weapon Crate markings shown on the mission map."))
        if (mission.openDoorCount > 0) {
            add(MissionComponent("Open Door tokens", mission.openDoorCount.toString(), "Place on the Open Door markings shown on the mission map."))
        }
        if (mission.blueDoorCount > 0) add(MissionComponent("Blue Door token", mission.blueDoorCount.toString(), "Place on the Blue Door marking; it opens like a standard door."))
        if (mission.greenDoorCount > 0) add(MissionComponent("Green Door token", mission.greenDoorCount.toString(), "Place on the Green Door marking; it opens like a standard door."))
        if (mission.brokenDoorCount > 0) add(MissionComponent("Broken Door tokens", mission.brokenDoorCount.toString(), "Place on the broken-door markings shown on the mission map."))
        if (mission.permanentNoiseTokensPerHighlightedZone > 0) add(MissionComponent("Permanent Noise tokens", "3 per highlighted Zone", "Place 3 Noise tokens in each highlighted Zone; these tokens are never removed during the End Phase."))
        if (mission.extraSurvivorCount > 0) add(MissionComponent("Unused Survivor miniatures", mission.extraSurvivorCount.toString(), if (missionId == "M12") "Place in the injured-Survivor Zone; set their ID Cards aside. They cannot act or carry Equipment." else "Place on the Exit Zone as Companions to rally."))
        if (mission.crashedCarCount > 0) add(MissionComponent("Crashed Car token", mission.crashedCarCount.toString(), "Place in the Zone shown on the mission map. It cannot be driven or searched."))
        if (mission.abominationCount > 0) add(MissionComponent("Abomination miniature", mission.abominationCount.toString(), "Place in the Zone indicated by the mission map."))
        if (mission.nedCount > 0) add(MissionComponent("Ned miniature", mission.nedCount.toString(), "Place in the Zone indicated by the mission map."))
        if (mission.policeCarCount > 0) {
            val note = if (mission.nonDrivablePoliceCar) "Required. This Police Car cannot be driven in this mission." else "Place the Police Car token on the mission map; it can be driven."
            add(MissionComponent("Police Car token", mission.policeCarCount.toString(), note))
        }
        if (mission.muscleCarCount > 0) {
            val note = if (mission.nonDrivableMuscleCar) "Required. This Muscle Car cannot be driven in this mission." else "Place the Muscle Car token on the mission map; it can be driven."
            add(MissionComponent("Muscle Car token", mission.muscleCarCount.toString(), note))
        }
    }
    val tileIds = tiles.map { "tile-${it.lowercase()}" }
    val componentIds = checklistComponents.map { "component-${it.name}" }
    val enemyIds = if (missionId == "M0") listOf("enemy-brute", "enemy-runner", "enemy-walkers") else emptyList()
    val requiredIds = tileIds + componentIds + enemyIds
    val checkedCount = requiredIds.count { it in preparedItems }
    val allPrepared = requiredIds.isNotEmpty() && requiredIds.all { it in preparedItems }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tiles & layout", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Mission ${mission.id} · ${mission.name}", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Tiles required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        tiles.forEachIndexed { index, tile ->
                            if (index > 0) Divider(Modifier.padding(vertical = 4.dp))
                            val itemId = "tile-${tile.lowercase()}"
                            ChecklistRow(
                                label = tile,
                                detail = "Tile ${index + 1} · rotation will be shown in the map setup",
                                checked = itemId in preparedItems,
                                onCheckedChange = { onTogglePrepared(itemId) },
                                prominent = true
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Source: Core rulebook, mission page ${mission.page}.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Tokens & components required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Tick each item as you prepare it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Spacer(Modifier.height(8.dp))
                        checklistComponents.forEachIndexed { index, component ->
                            if (index > 0) Divider(Modifier.padding(vertical = 4.dp))
                            val itemId = "component-${component.name}"
                            ChecklistRow(
                                label = component.name,
                                detail = component.placement,
                                quantity = component.quantity,
                                checked = itemId in preparedItems,
                                onCheckedChange = { onTogglePrepared(itemId) }
                            )
                        }
                    }
                }
            }
            if (missionId == "M0") item {
                Text("Starting Zombies", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        ChecklistRow("Brute", "1 miniature", "1", "enemy-brute" in preparedItems, { onTogglePrepared("enemy-brute") })
                        Divider(Modifier.padding(vertical = 2.dp))
                        ChecklistRow("Runner", "1 miniature", "1", "enemy-runner" in preparedItems, { onTogglePrepared("enemy-runner") })
                        Divider(Modifier.padding(vertical = 2.dp))
                        ChecklistRow("Walkers", "2 miniatures", "2", "enemy-walkers" in preparedItems, { onTogglePrepared("enemy-walkers") })
                    }
                }
                Text("Place the Zombies in the Zones indicated by the mission map.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            "Preparation progress: $checkedCount / ${requiredIds.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(onClick = onSetup, enabled = allPrepared) { Text("Survivor Selection", fontWeight = FontWeight.Bold) }
        }
        if (!allPrepared) {
            Text("Complete all preparation checkboxes to continue.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun MapLayoutScreen(tiles: List<String>, missionId: String, onBack: () -> Unit, onContinue: () -> Unit) {
    val tileCount = tiles.size
    val columns = when {
        missionId == "M4" -> 2 // Drive-by Shooting uses a vertical 3-row by 2-column layout.
        else -> when (tileCount) {
            2 -> 2
            3 -> 3
            4 -> 2
            6 -> 3
            8 -> 4
            9 -> 3
            else -> if (tileCount <= 3) tileCount.coerceAtLeast(1) else 3
        }
    }
    val rows = tiles.chunked(columns)
    var enlargedTile by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Mission map setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("${missionId} · ${coreMissionInfo[missionId]?.name ?: "Zombicide Life"}", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Text(if (enlargedTile == null) "Tile placement template" else "Enlarged tile: $enlargedTile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (enlargedTile == null)
                "Tap any tile to enlarge it for easier token placement. Tap the enlarged tile to return to the full map."
            else
                "Tap the tile to return to the full map. Tile images and verified token positions will be added later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        if (enlargedTile != null) {
            Card(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .clickable { enlargedTile = null },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(enlargedTile!!, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Rotation: —", style = MaterialTheme.typography.titleMedium)
                    Text("Tokens: —", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap to return to map", style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                rows.forEach { rowTiles ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        rowTiles.forEach { tile ->
                            Card(
                                modifier = Modifier.weight(1f).aspectRatio(1f)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outline)
                                    .clickable { enlargedTile = tile },
                                shape = RectangleShape,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(tile, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("↗", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        repeat(columns - rowTiles.size) {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White)
            ) { Text("BEGIN!", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun PlayerSelectionScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Survivor Selection", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Choose Survivors", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Choose the Survivors and distribute them among players as desired. All players form one team.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Text("Starting Equipment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Deal Starting Equipment cards randomly and as evenly as possible among all Survivors. If needed, choose who receives the extra card(s).", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back to materials") }
            Button(onClick = onContinue) { Text("Player Dashboard") }
        }
    }
}

@Composable
private fun PlayerDashboardScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Player Dashboard Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Each player should prepare their own dashboard and Survivor miniature.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Text("1. Choose a base-ring color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Each player takes one of the available colors for their Survivor base rings. Place the chosen ring under the Survivor miniature.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Text("2. Set the starting trackers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("• Life tracker: set it to the maximum value shown on the dashboard.", style = MaterialTheme.typography.bodyMedium)
        Text("• Skill tracker: set it to the blue level.", style = MaterialTheme.typography.bodyMedium)
        Text("• Reserve: place all three reserve markers in the top-right area of the dashboard.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(onClick = onContinue) { Text("First Player") }
        }
    }
}

@Composable
private fun FirstPlayerScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("First Player", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Choose the player holding the Fire Axe token. That player takes the first turn.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.first_player_token),
                contentDescription = "First Player Token",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(0.6f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(onClick = onContinue) { Text("Continue to Map") }
        }
    }
}

@Composable
private fun GameplayScreen(onRuleSet: () -> Unit, onHome: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Mission in Progress", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onRuleSet,
            modifier = Modifier.fillMaxWidth().height(68.dp)
        ) {
            Text("Rule-set", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onHome) { Text("Return to Main Page") }
    }
}

@Composable
private fun RulebookScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pages = remember {
        context.assets.open("rules/core_rulebook_text.txt")
            .bufferedReader().use { it.readText() }
            .split('\u000C')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Core Rulebook · Text Edition",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        Text(
            "Text extracted from the supplied rulebook PDF; images omitted. Page breaks are preserved where available.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(pages.size) { index ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Page ${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Text(pages[index], style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun loadMissionTiles(context: Context, setId: String, missionId: String): List<String> {
    return runCatching {
        val root = JSONObject(context.assets.open("missions/mission_components.json").bufferedReader().use { it.readText() })
        val sets = root.getJSONArray("sets")
        for (setIndex in 0 until sets.length()) {
            val set = sets.getJSONObject(setIndex)
            if (set.optString("setId") != setId) continue
            val missions = set.getJSONArray("missions")
            for (missionIndex in 0 until missions.length()) {
                val mission = missions.getJSONObject(missionIndex)
                if (mission.optString("missionId") == missionId) {
                    val tileArray = mission.getJSONArray("tiles")
                    return@runCatching (0 until tileArray.length()).map { tileArray.getString(it) }
                }
            }
        }
        emptyList()
    }.getOrElse { emptyList() }.ifEmpty { coreMissionInfo[missionId]?.tiles ?: listOf("1V", "3V") }
}

@Composable
private fun ChecklistRow(
    label: String,
    detail: String,
    quantity: String? = null,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    prominent: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onCheckedChange).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(label, style = if (prominent) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (quantity != null) {
            Spacer(Modifier.width(8.dp))
            Text("×$quantity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RulesScreen(
    sections: List<RuleSection>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelectEntry: (Pair<RuleSection, RuleEntry>) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Rules reference", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            label = { Text("Search core rules") },
            singleLine = true
        )
        val matches = sections.flatMap { section ->
            section.entries.filter { entry ->
                val haystack = "${section.title} ${entry.title} ${entry.summary} ${entry.details.joinToString(" ")}"
                haystack.contains(query, ignoreCase = true)
            }.map { section to it }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(matches) { (section, entry) ->
                Card(Modifier.fillMaxWidth().clickable { onSelectEntry(section to entry) }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(entry.summary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("${section.title} · Rulebook p. ${entry.sourcePages}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationRow(
    onBack: () -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    primaryIsLarge: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onBack) { Text("Back") }
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = if (primaryIsLarge) Modifier.height(56.dp) else Modifier
        ) {
            Text(primaryLabel, fontWeight = if (primaryIsLarge) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun BulletText(text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•")
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RuleDetail(section: RuleSection, entry: RuleEntry, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("‹ All rules", modifier = Modifier.clickable(onClick = onBack), color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(section.title.uppercase(), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Text(entry.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Rulebook page ${entry.sourcePages}", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(16.dp))
        Text(entry.summary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        entry.details.forEach { detail ->
            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•")
                Text(detail, modifier = Modifier.weight(1f))
            }
        }
    }
}
