package com.fatalpuppet.zombicide

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatalpuppet.zombicide.data.RuleEntry
import com.fatalpuppet.zombicide.data.RuleRepository
import com.fatalpuppet.zombicide.data.RuleSection

@Composable
private fun DrawableImage(
    name: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val drawableId = remember(name) { context.resources.getIdentifier(name.substringBeforeLast('.'), "drawable", context.packageName) }
    if (drawableId != 0) {
        Image(painter = painterResource(drawableId), contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Image not found: $name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AssetImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val bitmap = remember(path) {
        // Asset paths are relative to app/src/main/assets. Keep tokens plural-only.
        // Also try the filename at the assets root to tolerate older tile exports.
        val candidates = when {
            path.startsWith("tokens/") -> listOf(path)
            path.startsWith("maps/") -> listOf(path, path.removePrefix("maps/"))
            path.startsWith("tiles/") -> listOf(path, path.removePrefix("tiles/"))
            else -> listOf(path)
        }
        candidates.firstNotNullOfOrNull { candidate ->
            runCatching { context.assets.open(candidate).use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Image not found: $path", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private enum class AppScreen {
    HOME, RULES, RULE_SOURCE, MISSION_SELECT, MISSION_OVERVIEW, TILES, PLAYER_SELECTION, PLAYER_DASHBOARD, FIRST_PLAYER, MAP_LAYOUT, GAMEPLAY, EXPANSION_RULES, RULEBOOK
}

private data class RuleSourceOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val available: Boolean
)

private val ruleSources = listOf(
    RuleSourceOption("core", "Core", "Zombicide 2nd Edition core missions", true),
    RuleSourceOption("washington", "Washington Z.C.", "10 expansion missions", true),
    RuleSourceOption("fort", "Fort Hendrix", "10 expansion missions", true),
    RuleSourceOption("digital", "Digital", "45 digital missions · mission setup data is being completely updated", true)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = RuleRepository(applicationContext)
        val sections = repository.loadSections()
        setContent { CompanionApp(sections) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanionApp(sections: List<RuleSection>) {
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var query by remember { mutableStateOf("") }
    var rulesReturnScreen by remember { mutableStateOf(AppScreen.HOME) }
    var selectedEntry by remember { mutableStateOf<Pair<RuleSection, RuleEntry>?>(null) }
    var selectedSource by remember { mutableStateOf("core") }
    var selectedMissionId by remember { mutableStateOf("M0") }
    val context = LocalContext.current
    val selectedMissionTiles = remember(selectedSource, selectedMissionId) { loadMissionTiles(context, selectedSource, selectedMissionId) }
    var preparedItems by remember { mutableStateOf(setOf<String>()) }
    var completedMissions by remember { mutableStateOf(setOf<String>()) }
    val welcomePreferences = remember(context) { context.getSharedPreferences("zombicide_companion_preferences", Context.MODE_PRIVATE) }
    var showFirstRunHelp by remember { mutableStateOf(!welcomePreferences.getBoolean("first_run_help_shown", false)) }
    var showAboutDialog by remember { mutableStateOf(false) }

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
                        onRules = { query = ""; rulesReturnScreen = AppScreen.HOME; screen = AppScreen.RULES },
                        onVersionClick = { showAboutDialog = true }
                    )
                    screen == AppScreen.RULES -> RulesScreen(
                        sections = sections,
                        query = query,
                        onQueryChange = { query = it },
                        onSelectEntry = { selectedEntry = it },
                        onBack = { screen = rulesReturnScreen }
                    )
                    screen == AppScreen.RULE_SOURCE -> SourceSelectionScreen(
                        selectedSource = selectedSource,
                        onSelectSource = { selectedSource = it },
                        onBack = { screen = AppScreen.HOME },
                        onContinue = { screen = AppScreen.MISSION_SELECT }
                    )
                    screen == AppScreen.MISSION_SELECT -> MissionSelectionScreen(
                        selectedSource = selectedSource,
                        completedMissions = completedMissions,
                        onBack = { screen = AppScreen.RULE_SOURCE },
                        onSelectMission = { missionId ->
                            selectedMissionId = missionId
                            preparedItems = emptySet()
                            screen = AppScreen.MISSION_OVERVIEW
                        }
                    )
                    screen == AppScreen.MISSION_OVERVIEW -> MissionOverviewScreen(
                        missionId = selectedMissionId,
                        sourceId = selectedSource,
                        onBack = { screen = AppScreen.MISSION_SELECT },
                        onBegin = { screen = AppScreen.TILES }
                    )
                    screen == AppScreen.TILES -> TileSetupScreen(
                        missionId = selectedMissionId,
                        sourceId = selectedSource,
                        tiles = selectedMissionTiles,
                        preparedItems = preparedItems,
                        onTogglePrepared = { itemId ->
                            preparedItems = if (itemId in preparedItems) preparedItems - itemId else preparedItems + itemId
                        },
                        onBack = { screen = AppScreen.MISSION_OVERVIEW },
                        onSetup = { screen = AppScreen.PLAYER_SELECTION }
                    )
                    screen == AppScreen.PLAYER_SELECTION -> PlayerSelectionScreen(
                        sourceId = selectedSource,
                        onBack = { screen = AppScreen.TILES },
                        onContinue = { screen = AppScreen.PLAYER_DASHBOARD }
                    )
                    screen == AppScreen.PLAYER_DASHBOARD -> PlayerDashboardScreen(
                        sourceId = selectedSource,
                        onBack = { screen = AppScreen.PLAYER_SELECTION },
                        onContinue = { screen = AppScreen.FIRST_PLAYER }
                    )
                    screen == AppScreen.FIRST_PLAYER -> FirstPlayerScreen(
                        sourceId = selectedSource,
                        onBack = { screen = AppScreen.PLAYER_DASHBOARD },
                        onContinue = { screen = AppScreen.MAP_LAYOUT }
                    )
                    screen == AppScreen.MAP_LAYOUT -> MapLayoutScreen(
                        tiles = selectedMissionTiles,
                        missionId = selectedMissionId,
                        sourceId = selectedSource,
                        onBack = { screen = AppScreen.FIRST_PLAYER },
                        onContinue = { screen = AppScreen.GAMEPLAY }
                    )
                    screen == AppScreen.EXPANSION_RULES -> ExpansionRulesScreen(
                        sourceId = selectedSource,
                        onBack = { screen = AppScreen.GAMEPLAY }
                    )
                    screen == AppScreen.GAMEPLAY -> GameplayScreen(
                        missionId = selectedMissionId,
                        sourceId = selectedSource,
                        isComplete = selectedMissionId in completedMissions,
                        onToggleComplete = {
                            completedMissions = if (selectedMissionId in completedMissions) completedMissions - selectedMissionId else completedMissions + selectedMissionId
                        },
                        onRuleSet = { query = ""; rulesReturnScreen = AppScreen.GAMEPLAY; screen = AppScreen.RULES },
                        onExpansionRules = { screen = AppScreen.EXPANSION_RULES },
                        onHome = { screen = AppScreen.HOME }
                    )
                    screen == AppScreen.RULEBOOK -> RulebookScreen(
                        onBack = { screen = AppScreen.GAMEPLAY }
                    )
                }
                if (showFirstRunHelp) {
                    Dialog(onDismissRequest = {
                        welcomePreferences.edit().putBoolean("first_run_help_shown", true).apply()
                        showFirstRunHelp = false
                    }) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Welcome to Zombicide Companion", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Choose a mission source, select a mission, then follow the setup pages to prepare tiles and components, select Survivors, choose the first player, and arrange the map. During play, use the Rules reference to look up rules and mark the mission complete when finished.", style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = {
                                    welcomePreferences.edit().putBoolean("first_run_help_shown", true).apply()
                                    showFirstRunHelp = false
                                }, modifier = Modifier.align(Alignment.End)) {
                                    Text("Got it")
                                }
                            }
                        }
                    }
                }
                if (showAboutDialog) {
                    Dialog(onDismissRequest = { showAboutDialog = false }) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Zombicide Companion", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("A Zombicide 2nd Edition mission configurator helper. It guides you through mission selection, game setup, map preparation, and rules reference.", style = MaterialTheme.typography.bodyMedium)
                                Text("Developed by Mirko A. C.", style = MaterialTheme.typography.bodyMedium)
                                Text("GitHub repository", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable {
                                    showAboutDialog = false
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FatalPuppet/ZombicideCompanion"))) }
                                })
                                Button(onClick = { showAboutDialog = false }, modifier = Modifier.align(Alignment.End)) { Text("Close") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onStart: () -> Unit, onRules: () -> Unit, onVersionClick: () -> Unit) {
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
        Text(
            "Offline companion · v1.2.0.7",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clickable(onClick = onVersionClick),
            color = MaterialTheme.colorScheme.primary
        )
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
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val context = LocalContext.current
                        val logoName = when (source.id) {
                            "core" -> "logo_zombicide"
                            "fort" -> "logo_fort_hendrix"
                            "washington" -> "logo_washington_zc"
                            else -> ""
                        }
                        val logoId = remember(source.id) {
                            if (logoName.isBlank()) 0 else context.resources.getIdentifier(logoName, "drawable", context.packageName)
                        }
                        if (logoId != 0) {
                            Image(
                                painter = painterResource(logoId),
                                contentDescription = "${source.title} logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.width(88.dp).height(64.dp).padding(end = 12.dp)
                            )
                        }
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
    completedMissions: Set<String>,
    onBack: () -> Unit,
    onSelectMission: (String) -> Unit
) {
    val source = ruleSources.first { it.id == selectedSource }
    val washingtonMissions = listOf(
        Triple("M1", "Facing the Impossible", "Medium · 45 minutes"),
        Triple("M2", "Leave No One Behind", "Medium · 60 minutes"),
        Triple("M3", "DEFCON 5: Fade Out", "Medium · 45 minutes"),
        Triple("M4", "The War Zone", "Medium · 60 minutes"),
        Triple("M5", "DEFCON 4: Double Take", "Medium · 90 minutes"),
        Triple("M6", "DEFCON 3: Round House", "Medium · 90 minutes"),
        Triple("M7", "Get the Key Card", "Hard · 45 minutes"),
        Triple("M8", "Restore the Elevator", "Hard · 60 minutes"),
        Triple("M9", "DEFCON 2: Fast Pace", "Very Hard · 45 minutes"),
        Triple("M10", "DEFCON 1: Cocked Pistol", "Very Hard · 60 minutes")
    )
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
        if (selectedSource == "core" || selectedSource == "washington" || selectedSource == "fort" || selectedSource == "digital") {
            val fortMissions = (1..10).mapNotNull { number -> fortMissionInfo["M$number"]?.let { Triple("M$number", it.name, "${it.difficulty} · ${it.duration}") } }
            val digitalMissions = digitalMissionInfo.map { Triple(it.id, it.name, "${it.difficulty} · ${it.duration}") }
            val missionsToShow = when (selectedSource) {
                "washington" -> washingtonMissions
                "fort" -> fortMissions
                "digital" -> digitalMissions
                else -> coreMissions
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(missionsToShow) { mission ->
                    Card(Modifier.fillMaxWidth().clickable { onSelectMission(mission.first) }) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("${mission.first} · ${mission.second}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (mission.first in completedMissions) {
                                    Text("✓", color = Color(0xFF2E7D32), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                                }
                            }
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
                            "washington" -> ""
                            "fort" -> "Fort Hendrix mission data will be added when its digital rulebook is provided."
                            "digital" -> "Digital mission data from the first supplied batch will appear here."
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

private data class WashingtonMissionInfo(val name: String, val difficulty: String, val duration: String, val tiles: List<String>, val objective: String)

private data class FortMissionInfo(
    val name: String,
    val difficulty: String,
    val duration: String,
    val tiles: List<String>,
    val objective: String,
    val startingHour: String
)

private data class DigitalMissionInfo(
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

// Digital mission data transcribed from the user-provided Web Mission PDFs.
private val digitalMissionInfo = listOf(
    DigitalMissionInfo("WM1", "Amy, an Origin Story", "Medium", "45 minutes", "1 Survivor (Amy)",
        "Amy just saw her sensei turn into a zombie and kill his boss. She had to use his precious katana against him. Now, she’s all alone in the streets, at the beginning of a zombie invasion. What’s next? Zombicide!",
        "Zombicide: 2nd Edition", listOf("6R", "1V", "3V", "8V"), 1,
        listOf("Solo mission, played with Amy. Take all Objectives, then reach the Exit with all Survivors; a Survivor can escape at the end of their Turn only if the Exit Zone has no Zombies."),
        listOf("Amy starts with a Katana instead of regular Starting Equipment. Place Blue and Green Objectives randomly among Red Objectives, facedown; add Walkers in the indicated Zones.", "All doors are unlocked and can be opened without door-opening equipment. Each Objective gives 5 AP. The Green Objective allows Amy to drive the Pimpmobile; driving through a Zone with 4+ Zombies adds a Noise token that remains, and 3 Noise tokens cause the car to crash and the Mission to fail. The Blue Objective adds a random Survivor with no Equipment; Amy may immediately trade with them for free.", "Each Pimpweapon Crate gives a random available Pimpweapon; the Survivor may reorganize their inventory for free.")),
    DigitalMissionInfo("WM2", "Burn the Alley!", "Hard", "90 minutes", "Not specified",
        "You found something rather unusual. It doesn’t matter how many zombies you kill, more and more keep coming from the same street. Maybe a bomb will block the alley so you can avoid being overwhelmed.",
        "Zombicide: 2nd Edition", listOf("8R", "7R", "2V", "3V", "4V", "1V"), 2,
        listOf("Destroy the Green Spawn Zone using the firebomb; kill an Abomination (or more); take all Red Objectives. Accomplish these Objectives in any order."),
        listOf("Place an Abomination of your choosing in the indicated Zone.", "Never-ending spawn: each killed Zombie goes into a special reserve beside the Green Spawn Zone. Before regular spawning, that Zone spawns Walkers equal to half the reserve, then the reserve returns to the regular Zombie reserve; the Green Spawn Zone also spawns normally.", "The Green Objective is a firebomb. A Survivor taking a Red Objective may gain 5 AP or remove up to 5 Zombies from the special reserve. A Survivor holding the firebomb may discard it for free within Range 0–1 of the Green Spawn Zone to remove that Spawn Zone permanently and end the never-ending spawn rule.", "Each Pimpweapon Crate gives a random available Pimpweapon; the Survivor may reorganize their inventory for free.")),
    DigitalMissionInfo("WM3", "Doug, an Origin Story", "Hard", "45 minutes", "1 Survivor (Doug)",
        "After his office gets surrounded by zombies, Doug has only one plan in mind: save his client, hopefully find more survivors, and get out of the building alive.",
        "Zombicide: 2nd Edition", listOf("1R", "9V", "8R", "5V"), 3,
        listOf("Solo mission, played with Doug. Take all Objectives, then reach the Exit with all Survivors; a Survivor can escape at the end of their Turn only if the Exit Zone has no Zombies."),
        listOf("Place the Blue Objective randomly among Red Objectives, facedown. Tile 9V receives no Pimpweapon Crate. Place open and closed doors in the indicated Zones. Doug starts with a Sub-MG and Fire Axe instead of regular Starting Equipment; add Walkers in indicated Zones.", "Closed doors isolate parts of the building for Zombie spawning purposes only. Each Objective gives 5 AP. The Blue Objective adds a random Survivor with no Equipment; Doug may immediately trade with them for free.", "Doug’s Companion is the Client. Their Leader cannot Move more than twice per Turn and may draw 2 cards when Searching, choose 1 and discard the other, but a Walker immediately spawns in that Zone. Each time Doug kills at least 1 Zombie with a Sub-MG Ranged Action, he gains a free Melee Action before the end of his Turn; this can occur multiple times.", "Each Pimpweapon Crate gives a random available Pimpweapon; the Survivor may reorganize their inventory for free.")),
    DigitalMissionInfo("WM4", "All Systems Online", "Hard", "120 minutes", "Not specified",
        "Hey, this old antenna looks like it’s still working! We could set up a local communication network with this. However, we will need to activate two different circuits at the same time for it to work. We’re gonna need a lot of teamwork for this mission. There’s no time to waste. Let’s get this system started!",
        "Zombicide: 2nd Edition", listOf("8V", "6V", "5V", "9V", "4V", "7V"), 4,
        listOf("Find the Blue and Green Objectives. At the same time, 3 Survivors must be in the Zone containing the Blue Objective and 3 Survivors must be in the Zone containing the Green Objective. Then reach the Exit with all Survivors; a Survivor may escape at the end of their Turn only if the Exit Zone has no Zombies."),
        listOf("Place Blue and Green Objectives randomly among Red Objectives, facedown. There is no Survivor Starting Zone: players choose a different Street Zone for each Survivor. Some rooms are isolated by doors and count as separate buildings for Zombie spawning purposes only.", "Once found, the Blue and Green Objectives cannot be taken. After completing the “Now!” objective, remove both tokens; each Survivor earns 10 AP. Each Red Objective gives 5 AP to each Survivor.")),
    DigitalMissionInfo("WM5", "Ned, an Origin Story", "Medium", "45 minutes", "1 Survivor (Ned)",
        "Ned lost the keys to his bunker. After spending a lot of time trying to find them, he received a mysterious note: “I found a large set of keys. If you are the owner, come find me!”",
        "Zombicide: 2nd Edition", listOf("3V", "4V", "5V", "6V"), 5,
        listOf("Solo mission, played with Ned. Take the Green Objective, then reach the Exit Zone with Ned and another Survivor; they may escape at the end of their Turn only if the Exit Zone has no Zombies."),
        listOf("Place Blue and Green Objectives randomly among Red Objectives, facedown. Pick a random Survivor ID Card except Ned’s; that Survivor starts in the Starting Zone as a Companion, and Ned is their Leader. Some rooms isolated by doors count as separate buildings for Zombie spawning purposes only.", "Doors can be opened without door-opening equipment. Each Objective gives 5 AP; taking the Green Objective also allows the Survivor to reorganize their inventory for free.", "Taking the Blue Objective adds the Survivor set aside during Setup, with no Equipment. Ned may trade with them for free; the additional Survivor always plays after Ned.", "Each Pimpweapon Crate gives a random available Pimpweapon; the Survivor may reorganize their inventory for free.")),
    DigitalMissionInfo("WM6", "The Bunnylympics", "Medium", "90 minutes", "Not specified",
        "Bunny G challenged his friends to a game he called the Bunnylympics. He has a secret list with secret tasks fit for real heroes. He will reveal the list... as soon as he remembers what he wrote, because he lost the list. And the key to the shelter, too! (Don’t tell Ned.)",
        "Zombicide: 2nd Edition", listOf("1R", "2R", "3V", "6V", "8R", "9R"), 6,
        listOf("Accomplish at least 3 Bunny challenges by taking Red Objectives (the challenge can be increased to 4 or 5 for greater difficulty), then reach the Exit with all Survivors. A Survivor may escape at the end of their Turn only if the Exit Zone has no Zombies."),
        listOf("Place a Red Objective in the building on tile 1R, then place the Blue Objective randomly among the remaining Red Objectives, facedown. Tile 1R has Blue, Green, and Red Doors.", "The Police Car can be driven. When searching it, draw until a weapon card appears and discard the other cards; Aaahh!! spawns a Walker and interrupts the Search. Each time a Survivor takes a Pimpweapon Crate, roll a die (Lucky may reroll once); the result determines the reward.", "Taking the Blue Objective gives 5 AP and activates the Blue Spawn Zone, and the Blue Door can be opened. Throwing a Molotov at the active Blue Spawn Zone disables it; remove its Spawn token.", "Taking a Red Objective assigns a Bunny challenge: roll a die and reroll if that challenge has already been completed. Challenges can run concurrently. Red Objectives give no AP; completing a challenge gives 5 AP to all Survivors. The six challenges cover eliminating an Abominacop, rallying a Companion, eliminating 10 Zombies with the Police Car, removing 10 chosen tokens from a Zone, filling all Survivor inventories, and finding the Green Objective after a Runner is eliminated on a die roll of 6. Completing the last challenge allows the Green Door to open.")),
    DigitalMissionInfo("WM7", "Chasing Nightmares", "Medium", "120 minutes", "Not specified",
        "As brave and bold as they are, the kids confessed that they still have nightmares about the zombies, especially Abominations. Indeed, most of them are truly monstrous, and, well, sometimes they give the adults the heebie-jeebies too! Having the soul of a warrior, Tiger Sam suggested we face our own fears. We accepted the challenge and are now heading to the most infested district in town. I hope this is not another bad dream!",
        "Zombicide: 2nd Edition", listOf("1V", "2V", "5R", "7V", "8R", "9R"), 8,
        listOf("Kill your fears: eliminate at least 4 Abominations with different names. Each Survivor has an Objective token. This Mission uses the Abomination Fest rules (rulebook, p. 30)."),
        listOf("Place Blue and Green Objectives randomly among the Objectives, facedown. Each Objective gives 5 AP. Place the token on the Survivor’s Dashboard; it does not use an inventory slot. A Survivor gains Roll 6: +1 Damage while they have one or more Objective tokens. Only Tiger Sam can trade Objective tokens like Equipment cards.", "Taking a Red Objective also spawns an Abomination on the nearest active Spawn Zone. Taking the Blue or Green Objective activates the Spawn Zone of the corresponding color, then removes an active Spawn Zone token of the player’s choice, except the Starting Spawn Zone token.", "Each Pimpweapon Crate gives a random available Pimpweapon; the Survivor may reorganize their inventory for free. The Police Car can be driven; when searching it, draw until a weapon card appears and discard the other cards. Aaahh!! spawns a Walker and interrupts the Search.")),
    DigitalMissionInfo("WM8", "Common Enemies", "Hard", "120 minutes", "Not specified",
        "Old rivals have reignited their war during the zombie apocalypse, leaving the streets on fire and Survivors stranded in a building surrounded by Zombies.",
        "Zombicide: 2nd Edition", listOf("1V", "2V", "3V", "5R", "6R", "7V"), 1,
        listOf("Rescue the cop and the thug by taking the Green Objective. Then reach the Exit with all Survivors; a Survivor may escape at the end of their Turn if the Exit Zone has no Zombies."),
        listOf("Survivors cannot enter or cross Zones containing car tokens; Zombies are unaffected. Cars cannot be driven or searched.", "Each Objective gives 5 AP. Taking a Red Objective also gives a Fire Extinguisher token, which uses no inventory slot. Spend 1 Action at Range 1 from a car to discard the token and car, allowing the Zone to be entered or crossed normally.", "The Blue Door cannot open until the Blue Objective is taken; then the Blue Spawn Zone activates and the Spawn Zone token next to the Starting Zone is removed. The Green Door cannot open until the Green Objective is taken; then the Green Spawn Zone activates and the Spawn Zone token between tiles 2V and 6R is removed.", "Each Pimpweapon Crate gives a random available Pimpweapon; the Survivor may reorganize their inventory for free. Optional: when the Blue Door opens, spawn Zombies, then place 2 unchosen Survivor miniatures in the Green Objective Zone as Companions to rally.")),
    DigitalMissionInfo("WM9", "The Great Fire", "Very Hard", "90 minutes", "Not specified",
        "A fire has broken out in this building. Good news is it’s been evacuated and is probably full of goodies. Bad news is the fire is spreading fast. Get in and grab everything as quickly as you can!",
        "Zombicide: 2nd Edition", listOf("1R", "2R", "7V", "9V"), 1,
        listOf("Recover as many Pimpweapons as possible before the building is engulfed in flames. Then reach the Exit with all Survivors. A Survivor may escape through the Exit Zone at the end of their Turn only if there are no Zombies in it."),
        listOf("Set aside all Aaahh!! cards from the Pimpweapon deck. Closed rooms: doors isolate closed parts of the central building; these parts count as separate buildings for Zombie spawning purposes only. Board games, books, and plushies: each Objective gives 5 AP to the Survivor who takes it. Flimsy doors: Door Actions do not require Door-opening Equipment.", "The fire spreads: at the beginning of the first End Phase, place a Noise token in corner 1 of the middle Zone, as shown on the map. This special Noise token is a fire marker and is not removed during subsequent End Phases. At the beginning of each later End Phase, add a fire marker to all corners of the middle Zone that already have at least 1 fire marker. Then place 1 Noise token as a fire marker in the next corner clockwise that does not already have one. A corner cannot have more than 3 fire markers. When a corner reaches 3 fire markers, all building Zones on its tile are engulfed in flames and cannot be entered; any Survivor standing in such a Zone is eliminated and the Mission is lost.", "This smoke: during each End Phase, Survivors without the Scavenger or Tough Skill suffer 1 Damage if they stand in a Building Zone on a tile with 1 or 2 fire markers.", "Pimp my day: count the Pimpweapons found by all Survivors at the end of the game. 4: Trailblazer! Complete victory. 3: Not so hot. 1–2: You’re fired.")),
    DigitalMissionInfo("WM10", "Josh, an Origin Story", "Hard", "30 minutes", "1 Survivor (Josh)",
        "Josh heads to the police station to find out what charges have been brought against his friends, only to discover that the streets are filled with the dead.",
        "Zombicide: 2nd Edition", listOf("1R", "2R", "5R", "8R"), 4,
        listOf("Find and take the Green Objective. Then reach the Exit with all Survivors; a Survivor may escape at the end of their Turn if the Exit Zone has no Zombies."),
        listOf("Prepare a Dashboard for a Survivor of the player's choice, set it aside with their miniature and ID Cards, and equip them with a Baseball Bat. Shuffle the Blue and Green Objectives among Red Objectives, facedown. Josh starts with a Fire Axe and Pistol.", "The Police Car blocks Lines of Sight and Movement in its Zone and cannot be driven or searched. Each Objective gives 5 AP.", "When the Green Objective is taken, place the set-aside Survivor miniature in that Zone; the player now controls both Survivors. The Blue Spawn Zone is active from the start and becomes inactive when the Blue Objective is taken.", "Zombies may open doors along their route. If this opens a building for the first time, Dark Zones inside spawn at 1 Danger Level above the current level, up to Red.")),
    DigitalMissionInfo("WM11", "A Stupid Trick", "Hard", "60 minutes", "Not specified",
        "Clear the abandoned house of the crawling Walkers left by old traps, recover the traps, and get out safely.",
        "Zombicide: 2nd Edition", listOf("2R", "3R", "4R", "5R", "6R", "7V"), 5,
        listOf("Recover all Traps by taking all Objectives. There must be no Walkers in the buildings (other Zombie types are ignored). Then exit with all Survivors; a Survivor may escape at the end of their Turn if the Exit Zone has no Zombies."),
        listOf("Place Blue and Green Objectives randomly among the Objectives, facedown. Each Objective gives 5 AP.", "Red Objectives are triggered traps: roll a die when taking one. Odd: nothing happens. Even: place that many Walkers laying down as Crawlers; they cannot Move but can Attack and hinder Survivor Move Actions as usual.", "Taking the Blue or Green Objective gives 5 AP and triggers the luring mechanism: immediately resolve a Spawn in each Spawn Zone, then resume the Survivor's Turn.", "Each Pimpweapon Crate gives a random available Pimpweapon; the Survivor may reorganize their inventory for free.")),
    DigitalMissionInfo("WM12", "Ostara’s Flash Mob", "Hard", "30 minutes", "Not specified",
        "Ostara proposes a sudden raid on a huge warehouse full of boxes: grab the Pimpweapon Crates and escape before the Zombies react.",
        "Zombicide: 2nd Edition", listOf("1V", "2V", "3V", "4V", "7V", "9V"), 6,
        listOf("Get all Pimpweapon Crates. Then exit with all Survivors through any eligible street Zone on the edge of the board, provided it has no Zombies."),
        listOf("Split Survivors as evenly as possible between the 3 Starting Zones.", "When a door leading into the U-shaped storage building is opened, all other doors of that building open and the Green and Blue Spawn Zones become active.", "Cars cannot be driven. Each Pimpweapon Crate gives a random available Pimpweapon; the Survivor may reorganize their inventory for free. After all Pimpweapon Crates are taken, any street Zone on the board edge is an Exit Zone.")),
    DigitalMissionInfo("WM13", "You Got the Keys?", "Medium", "30 minutes", "Not specified",
        "Search a police station for supplies and the keys needed to get a seemingly pristine Police Car running.",
        "Zombicide: 2nd Edition", listOf("3R", "5R", "6R", "7V"), 7,
        listOf("Reach the Exit Zone with all Survivors and the Police Car. A Survivor may escape at the end of their Turn, or as a Passenger in the Police Car, if the Exit Zone has no Zombies."),
        listOf("Each Red Objective gives 5 AP. The Red Door cannot be opened.", "The Green Door cannot open until the Green Objective is taken. When the Green Door opens, the Green Spawn Zone activates; taking the Green Objective gives all Survivors 5 AP.", "The Blue Door cannot open until the Blue Objective is taken. Taking it gives all Survivors 5 AP; the Blue Objective is Equipment but takes no inventory slot and can be traded. The Police Car can only be driven after its owner, carrying the Blue Objective, enters it as Driver or Passenger.", "When searching the Police Car, draw cards until a weapon appears, discarding other cards. An Aaahh!! card interrupts the Search and spawns a Walker.")),
    DigitalMissionInfo("WM14", "The Ultimate Christmas Dinner", "Medium", "30 minutes", "Not specified",
        "Prepare a Christmas feast by finding a frozen turkey, rice, and canned food, then get out with all Survivors.",
        "Zombicide: 2nd Edition", listOf("1V", "5V", "7V", "9R"), 1,
        listOf("Take the Green Objective (the frozen turkey), find a Bag of Rice card and a Canned Food card, then reach the Exit with all Survivors. A Survivor may escape at the end of their Turn if the Exit Zone has no Zombies."),
        listOf("Shuffle the Blue Objective among the Red Objectives, facedown. Remove all Bag of Rice and Canned Food cards from the Equipment deck; set aside one of each and two Aaahh!! cards to form the Christmas Gifts deck.", "Each Objective gives 5 AP and lets the Survivor draw the top Christmas Gift for free; it occupies a Backpack slot and cannot be removed or traded.", "The first Aaahh!! drawn places a Walker as usual, then an Abomination on the Spawn Start. Opening the Red Door on tile 5V activates the Green Spawn Zone. The Blue Door requires the Blue Objective; the Green Door requires the Green Objective. The Green Objective occupies an Inventory Slot and can be traded.")),
    DigitalMissionInfo("WM15", "Saint Sylvester Marathon", "Hard", "45 minutes", "Not specified",
        "Race through streets full of fast Zombies to collect isotonic drinks and escape before too many Runners leave the board.",
        "Zombicide: 2nd Edition", listOf("2R", "3V", "5R", "8R"), 2,
        listOf("Take all Objectives, then reach the Exit with all Survivors. The Exit must contain no Zombies and no Abominations."),
        listOf("Place an Abomination and Runners in the Zones indicated on the map. Each Objective gives 5 AP to all Survivors.", "Abominations move 2 Zones instead of 1. After each Spawn Step, add one Runner to each Spawn Zone. Runners target the Exit and are removed when they reach it; if 6 or more Runners escape, the Mission is lost.", "The Survivor taking the last Objective may take any card from the Equipment deck, then reorganize their inventory for free. Shuffle the deck afterwards.")),
    DigitalMissionInfo("WM16", "Red Light, Green Light!", "Hard", "45 minutes", "Not specified",
        "Eliminate a rooted, evolving Abomination before its presence drives nearby Zombies into a frenzy.",
        "Zombicide: 2nd Edition", listOf("1V", "3V", "5V", "6V", "8V", "9V"), 3,
        listOf("Kill the Abomination, then reach the Exit with all Survivors. A Survivor may escape at the end of their Turn if the Exit Zone has no Zombies."),
        listOf("Shuffle the Blue and Green Objectives among the Red Objectives, facedown. Place an Abomination in the indicated Zone and set aside all Abomination cards from the Zombie deck.", "Red doors are locked and cannot be opened. Each Objective gives 5 AP; taking an Objective places a Red Zombie Spawn token in that Zone. Blue and Green Objectives are placed on a Dashboard, take no Inventory slot, and can be traded. Taking either activates the Blue Spawn Zone, which always spawns at Blue Danger Level.", "At the end of the Players Phase, Zombies of the same type as the last drawn Zombie card gain an extra Activation for each Survivor in Line of Sight of the Abomination. The Abomination has no Actions and cannot move or attack. A Survivor may spend an Action to use a Molotov and discard both Blue and Green Objectives to kill it; the killer gains 5 AP.")),
    DigitalMissionInfo("WM17", "Elle, an Origin Story", "Very Hard", "30 minutes", "1 Survivor (Elle)",
        "Elle searches a maze of dead ends for a missing Kid Survivor and a rumored secret lair.",
        "Zombicide: 2nd Edition", listOf("1R", "3V", "4V", "8R"), 4,
        listOf("Solo mission played with Elle. Find the Companion (Green Objective) and find the Exit Zone in any order, then reach the Exit with Elle and the Companion. They may escape at the end of their Turn if the Exit Zone has no Zombies."),
        listOf("Elle starts with a Fire Axe and a Pistol. Choose any Kid Survivor, set aside their miniature and ID card, and shuffle the Green and Blue Objectives among the Red Objectives, facedown. Each Objective gives 5 AP.", "During each Spawn Step, roll a die for each Spawn Zone before drawing a card: on 1–2, spawn normally; on 3+, that Zone does not spawn, but if the tile's building is open, spawn 1 Walker in each of its Dark Zones.", "When the second Red Objective is taken, place the Exit token in that Zone. Taking the Green Objective places the Kid Survivor in that Zone as a Companion, with Elle as Leader. The Blue Objective gives Elle either a Plenty of Bullets or Plenty of Shells card; she may reorganize her inventory for free, then shuffle the Equipment deck.")),
    DigitalMissionInfo("WM18", "Wanda, an Origin Story", "Hard", "30 minutes", "2 Survivors (Wanda & Ned)",
        "Wanda and Ned gather weapons and food after escaping a diner overrun by Zombies.",
        "Zombicide: 2nd Edition", listOf("2R", "3V", "4R", "5R", "9R"), 5,
        listOf("Take at least 2 Red Objectives and gather and keep at least 2 food cards (Bag of Rice, Canned Food, or Water), then reach the Exit with both Survivors. A Survivor may escape at the end of their Turn if the Exit Zone has no Zombies."),
        listOf("Ned starts with a Sniper Rifle and Wanda with a Machete; there is no additional Starting Equipment. Shuffle the Green Objective among the Red Objectives, facedown. Set aside all Chainsaw cards and 1 Flashlight card, and place Walkers in the indicated Zones. Tile 9R has no Objective token or Pimpweapon Crate.", "Each Objective gives 5 AP. Closed Red doors cannot be opened. The broken Pimpmobile cannot be driven and can be searched once per Survivor: Wanda gets a Chainsaw and Ned gets a Flashlight.", "The Green Spawn Zone becomes active as soon as the Green Objective is taken.")),
    DigitalMissionInfo("WM19", "A New Dawn – Part I", "Hard", "30 minutes", "1 Survivor initially",
        "A lone Survivor searches for other living people and hopes to build a small community.",
        "Zombicide: 2nd Edition", listOf("2V", "4V", "5V", "8V"), 6,
        listOf("Take the Green and Blue Objectives, then reach the Exit with all Survivors and Companions. A Survivor may escape at the end of their Turn if the Exit Zone has no Zombies."),
        listOf("Shuffle the Green and Blue Objectives among the Red Objectives, facedown. Remove all Aaahh!! cards from the Pimpweapons deck. The starting Survivor has a Fire Axe and Pistol. Choose an additional Survivor and prepare their Dashboard with a Baseball Bat and Pistol from the Starting Equipment deck.", "Doors inside buildings isolate parts of them, treated as separate buildings for Zombie spawning only. Each Objective gives 5 AP.", "Taking the Green Objective places the additional Survivor in its Zone with the same AP as the starting Survivor; they can be played immediately after the starting Survivor that Game Round. Taking the Blue Objective places a random Survivor in its Zone as a Companion. During each ensuing End Phase, roll a die; on 1–3, spawn a Walker in an adjacent Zone with an open route and Line of Sight to the Companion.")),
    DigitalMissionInfo("WM20", "A New Dawn – Part II", "Hard", "30 minutes", "2 Survivors",
        "Two Survivors and Rod search the Locusts’ former shelter for a map hidden in a vault, then escape.",
        "Zombicide: 2nd Edition", listOf("1R", "5R", "7V", "6V"), 1,
        listOf("Take the Blue Objective, then reach the Exit with all Survivors and Companions. A Survivor or Companion may escape at the end of their Turn if the Exit Zone has no Zombies."),
        listOf("Shuffle the Blue Objective among the Red Objectives, facedown. Survivors start with a Fire Axe, Baseball Bat, and 2 Pistols, distributed as desired. Choose a Survivor to lead Rod, a random Companion, and place Rod in that Leader’s Zone.", "Indoor Spawn Zones are active only while at least one Survivor is in the building. If Rod is inside too, spawn one Danger Level higher, up to Red. The Green Door is blocked. Doors inside buildings isolate areas for Zombie spawning purposes only.", "Each Objective gives 5 AP. The Blue Objective can only be taken while Rod is in the same Zone; if found without him, leave it revealed and spend another Action to take it, with no AP gained. Taking the Blue Objective activates the Blue Spawn Zone.")),
    DigitalMissionInfo("WM21", "A New Dawn – Part III", "Hard", "30 minutes", "2 Survivors initially",
        "After escaping the Locusts, the Survivors become separated and must reunite, gather allies, and get away.",
        "Zombicide: 2nd Edition", listOf("8V", "9V", "4R", "3R", "2V", "1V"), 2,
        listOf("Take the Blue and Green Objectives, then reach the Exit with all Survivors and Companions. A Survivor or Companion may escape at the end of their Turn if the Exit Zone has no Zombies."),
        listOf("Do not place Objectives on tiles 3R and 4R. Place Blue and Red Objectives on tiles 8V and 9V, and Green and Red Objectives on tiles 2V and 1V, shuffled within each pair. Place a Survivor in each Starting Zone; each starts at Yellow Danger Level, with a Fire Axe and Pistol, and suffers 1 Wound. Place a Companion in each Starting Zone and make the Survivor there their Leader. Set aside 2 Survivors and their Dashboards with random remaining Starting Equipment.", "The Red Door is blocked. Each Objective gives 5 AP. When the Blue or Green Objective is taken, place a new Survivor in that Zone; they can be played immediately after the Survivor who found them. The Pimpmobile opens the Blue and Green Doors immediately when it enters their Zones.", "The Green Spawn Zone is inactive until the Blue or Green Door is opened. Draw a Zombie Card for it when activated; it remains active thereafter.")),
    DigitalMissionInfo("WM23", "The Agatha-pocalypse", "Medium", "120 minutes", "Not specified",
        "Help Agatha find her treasures while keeping her calm and eventually defeat the Abominacop.",
        "Zombicide: 2nd Edition", listOf("2V", "9V", "5R", "3V", "4R", "6V"), 3,
        listOf("Take the Blue and Green Objectives, then eliminate the Abominacop in the meat grinder’s Zone."),
        listOf("Remove the Abominacop from the Abomination deck. Remove a Molotov from the Equipment deck and place it in the Pimpmobile, replacing its Pimpweapon. The Blue Objective is on tile 2V; the Green Objective is on tile 3V, outside the Objective Zone. A Survivor starts with a Noise token on their Dashboard to represent Agatha; it counts as an additional Noise token, can be traded like Equipment, and grants Taunt to its holder. Set aside 2 Agatha counters.", "Remove one Agatha counter during each End Phase. When the pool is empty, Agatha cries: her holder must use Taunt once each Turn if possible, remove any Abomination from the board and place the Abominacop in the Police Car’s Zone, and activate the Blue Spawn Zone. Once per game, a Survivor holding Agatha in the bathtub Zone may spend 1 Action to add 3 counters.", "Each Objective gives 5 AP and adds 1 Agatha counter. Once both colored Objectives are taken, counters can no longer be gained or lost. The meat grinder Zone eliminates all Zombies in it for 1 Action; it is the only way to eliminate the Abominacop. Only doors marked with Door tokens can be opened.", "The Police Car cannot be driven until the Abominacop is on the board; when searched, draw until a weapon appears, discarding other cards. The Pimpmobile cannot be driven and has no Pimpweapon; the first Survivor searching it takes the hidden Molotov. Each Pimpweapon Crate gives a random available Pimpweapon.")),
    DigitalMissionInfo("WM24", "The Last Passage", "Hard", "120 minutes", "Not specified",
        "Gather fuel to activate the generators, open the reinforced doors, and escape through the only passage out of the district.",
        "Zombicide: 2nd Edition", listOf("8V", "3R", "7V", "1V", "6R", "4V", "5R", "9R", "2V"), 5,
        listOf("Open the doors by taking the Blue and Green Objectives. Then reach the Exit with all Survivors; a Survivor may escape at the end of their Turn if the Exit Zone has no Zombies."),
        listOf("The Blue Objective is on tile 8V and the Green Objective on tile 2V. Each Pimpweapon Crate gives a random available Pimpweapon and allows free inventory reorganization.", "Each Objective gives 5 AP. Red Objectives represent fuel: a Survivor may take a Blue or Green Objective only while carrying at least 3 Red Objectives, and must discard 3 Red Objectives to do so. The Blue Spawn Zone activates when the Blue Objective is taken, and the Blue Door can only be opened then. The Green Spawn Zone and Green Door work the same way for the Green Objective.", "The Pimpmobile can be driven and contains one Pimpweapon Crate. As soon as a Survivor interacts with it by taking the crate or getting in, place 4 Noise tokens on it; they move with the car and one is removed during each End Phase. When the last is removed, resolve a Molotov effect in the car’s Zone and remove the car token.")),
    DigitalMissionInfo("WM25", "Creepy Corners", "Hard", "45 minutes", "6 Survivors",
        "Collect food from an underground passage and reach the subway station before the horde overwhelms the Survivors.",
        "Zombicide: 2nd Edition", listOf("7R", "5R", "8R", "6R"), 7,
        listOf("Take all Objectives, then reach the Exit with all Survivors. A Survivor may escape at the end of their Turn if the Exit Zone has no Zombies."),
        listOf("There is no Objective token on tile 8R, which contains the Exit. Each Red Objective gives 5 AP to all Survivors; place it on the taker’s Dashboard, where it occupies an Equipment slot and can be traded. If inventory is full, discard an Equipment card to make room.", "The closed Red Doors cannot be opened. The Blue Door on tile 6R and Green Door on tile 8R are linked by an underground tunnel: they are adjacent for movement but do not share Line of Sight. Opening the Blue Door also opens the Green Door, triggers a Zombie Spawn in tile 8R’s Dark Zone and the Blue Spawn Zone, and activates the Blue Spawn Zone. Each Pimpweapon Crate gives a random available Pimpweapon.")),
    DigitalMissionInfo("WM26", "A Quiet Place", "Hard", "60 minutes", "6 Survivors",
        "Search intact houses for rare supplies, then escape quietly as the Zombies converge on the area.",
        "Zombicide: 2nd Edition", listOf("3V", "1R", "4V", "7R"), 8,
        listOf("Take all Objectives, then reach the Exit with all Survivors. A Survivor may escape at the end of their Turn if the Exit Zone has no Zombies."),
        listOf("Doors between tiles 1R and 7R isolate parts of the building for Zombie spawning purposes. Each Objective gives 5 AP and occupies an Equipment slot on the taker’s Dashboard; if their inventory is full, discard an Equipment card to make room.", "Whenever a Survivor makes Noise, place a Noise token beside the closest Red Spawn Zone that does not already have one. If all Red Spawn Zones have a Noise token, ignore this rule. These tokens do not affect Zombie routing. During the next Zombies’ Phase, draw Zombie Cards for the Green Spawn Zone and each Red Spawn Zone with a Noise token, then remove those Noise tokens during the following End Phase.")),
    DigitalMissionInfo("WM27", "The Dead Elite Squad", "Very Hard", "120 minutes", "6 Survivors",
        "A standalone Fort Hendrix side mission: rescue an elite soldier trapped in barracks, eliminate the sniper squad, and escape.",
        "Zombicide: 2nd Edition and Fort Hendrix", listOf("16R", "17V", "18V", "19V", "20R", "21R"), 1,
        listOf("Rescue the elite soldier, eliminate all Shooter Walkers on the board and in reserve, and find a secure Exit, in any order. Then reach the Exit with all Survivors and the elite soldier. The game is lost if any Survivor or the elite soldier is eliminated."),
        listOf("Starting hour: 10am. Shuffle Blue and Green Objectives among the Red Objectives, facedown. Place 3 Shooter Walkers as indicated and use Penny's miniature as the elite soldier. Use Fort Hendrix Advanced Equipment decks.", "Each Objective gives 5 AP. After both colored Objectives are taken, Shooter Walkers are removed from the game instead of returning to reserve. Whenever a Red Objective is taken, roll a die: on 4+, place or move the Exit to that Zone. If no Exit was placed when the last Red Objective is taken, place it in the Survivor Starting Zone.", "A Survivor may spend 1 Action in the elite soldier's Zone to become her Leader. Once during each of their Turns, the Leader may eliminate up to 2 Zombies in Line of Sight, except Abominations and Shooter Walkers, ignoring Dark Zone and Nighttime rules; this earns no AP and generates 1 Noise per Zombie. The elite soldier is immune to Friendly Fire. Closed doors isolate parts of buildings for spawning purposes. Each Epic Weapon Crate gives a random available Epic Weapon.")),
    DigitalMissionInfo("WM28", "A Risky Deal", "Hard", "90 minutes", "6 Survivors",
        "A standalone Washington, Z.C. side mission to rescue two Secret Agents from the White House and escort them to the secret presidential bunker.",
        "Zombicide: 2nd Edition and Washington Z.C.", listOf("10R", "11R", "12R", "13V", "14V", "15V"), 3,
        listOf("Rescue both Secret Agents and find the entrance to the Secret Presidential Bunker. Then reach the Exit with all Survivors and both agents; they may escape at the end of their Turn if the Exit Zone has no Zombies. Losing any Survivor or agent loses the game."),
        listOf("Starting hour: 11am. Shuffle Blue and Green Objectives among Red Objectives, facedown. Players divide Survivors equally between the two Starting Zones. Taylor and the President miniatures are the Secret Agent Companions. Use Washington Z.C. Advanced Equipment decks.", "The two floor boards are separate: highlighted stair Zones are adjacent for movement but do not share Line of Sight. Spawn Zones are active only if at least one Survivor is on that floor.", "Each Objective gives 5 AP. Blue and Green doors cannot open until their matching Objectives are taken. Once both colored doors are open, roll a die at each End Phase: on 1–4, place the Exit in the indicated Zone and stop rolling; on 5–6, spawn a Runner on each active Spawn Zone. A Survivor may spend 1 Action in a Secret Agent's Zone to become their Leader. The President's Leader gains Spare Parts; Taylor's Leader gains Night Vision. The Companions cannot suffer Friendly Fire. Each Epic Weapon Crate gives a random available Epic Weapon.")),
    DigitalMissionInfo("WM29", "The Santa Hellpers", "Medium", "60 minutes", "Not specified",
        "Help Santa deliver six Christmas gifts before dawn, then escape.",
        "Zombicide: 2nd Edition", listOf("1R", "2R", "3V", "4V", "5R", "9R"), 5,
        listOf("Deliver 6 Christmas Gifts by placing all Survivors' Red Objectives in Objective Zones. Then reach the Exit with all Survivors; the Exit Zone must have no Zombies."),
        listOf("Do not place Objective tokens or Epic Weapon Crates on the board or in the Muscle Car. Each Survivor starts with one non-tradeable Red Objective. Remove all Aaahh!! cards from the Epic Weapon deck.", "A Survivor may spend 1 Action to place their Red Objective in an Objective Zone with no Objective and no Zombie; they gain 5 AP and the Objective cannot be taken.", "At each End Phase, while Epic Weapons remain available, roll a die and place a random Epic Weapon card in the correspondingly numbered Zone; it can be taken like an Objective. When no Epic Weapons remain, roll once more and place the Exit instead. The Muscle Car can be driven and has no Epic Weapon Crate. Optional difficulty rule: if no Epic Weapon card can be drawn during an End Phase while this rule is active, the game is lost.")),
    DigitalMissionInfo("WM30", "A New Dawn – Part IV", "Hard", "60 minutes", "6 Survivors",
        "Split into two teams to clear the Locust shelter, recover supplies, and eliminate the Zombies on both separate maps.",
        "Zombicide: 2nd Edition", listOf("1V", "2V", "3V", "6R", "7R", "8R"), 6,
        listOf("Take all Red Objectives, then take both Green and Blue Objectives. Win when no Zombies remain on either map at the end of a Player's Phase."),
        listOf("Play on two separate boards with no connection or Line of Sight. Place 3 Survivors in each Starting Zone; each group must have a Fire Axe from the Starting and regular Equipment decks. Place 8 Walkers and 4 Runners as shown.", "Red Objectives give 5 AP. Each Epic Weapon Crate gives a random available Epic Weapon. Blue and Green Spawn Zones are active from the start.", "The Blue Door cannot be opened until all Red Objectives on its map have been taken. The Green Door has the same restriction for its map; the Red Door cannot be opened. When a Red Objective is taken, immediately draw a Zombie Card for a Red Spawn Zone on the other map, then remove that Spawn token. When a Blue or Green Objective is taken, draw a Zombie Card for the matching colored Spawn Zone, then remove that Spawn token.")),
    DigitalMissionInfo("WM31", "A New Dawn – Part V", "Medium", "60 minutes", "6 Survivors",
        "Search an abandoned warehouse for supplies while Rod's trap and the Zombies threaten the team.",
        "Zombicide: 2nd Edition", listOf("5V", "6V", "7V", "9V"), 7,
        listOf("Take both Red Objectives, then gain access to the Exit by opening the Blue or Green Door (or both). Finally, reach the Exit with all Survivors; the Exit Zone must have no Zombies."),
        listOf("Shuffle the Green and Blue Objectives among Red Objectives, facedown. Place 3 Survivors in each Starting Zone; one group starts with a Fire Axe and the other with a Crowbar. Set aside a Survivor miniature without a colored base to represent Rod and place him in Zone 1.", "Doors inside buildings isolate parts of buildings for spawning purposes only. Each Objective gives 5 AP to all Survivors. Blue and Green Spawn Zones are inactive initially; when the matching Objective is taken, open the corresponding door and draw a Zombie Card for that Spawn Zone, which then becomes active.", "Rod cannot be attacked, is not a Noise token, and is never targeted by Zombies. Epic Weapon Crates and Objectives cannot be taken while Rod is in the same Zone. Survivors starting their Activation on the same tile as Rod have 1 fewer Action. During each End Phase, roll a die: on 1–4, place Rod in the corresponding numbered Zone and spawn a Zombie Card there at Blue Danger Level; on 5–6, remove Rod from the map. Each Epic Weapon Crate gives a random available Epic Weapon.")),
    DigitalMissionInfo("WM32", "A New Dawn – Part VI", "Hard", "75 minutes", "6 Survivors",
        "Trap Rod inside the warehouse by luring him into the highlighted Zone and locking the Green and Blue Doors.",
        "Zombicide: 2nd Edition", listOf("1R", "2R", "3V", "4V", "7V", "9V"), 8,
        listOf("Move Rod into the highlighted Zone, then lock the Green and Blue Doors to trap him."),
        listOf("Shuffle Green and Blue Objectives among the Red Objectives, facedown. Set aside a Survivor miniature without a colored base to represent Rod. The building is already open; place Zombies as indicated and draw one Zombie Card for each Blue and Green Spawn Zone.", "Printed doors cannot be opened. Survivors cannot access any Street Zone except the highlighted Zone. Place 3 Walkers in the highlighted Zone at the beginning of every Spawn step.", "Objectives give 5 AP to the Survivor who takes them. The Green and Blue Doors can be locked only under the conditions described on the mission map and rules. Each Epic Weapon Crate gives a random available Epic Weapon.")),
    DigitalMissionInfo("WM33", "Crazy Taxi", "Medium", "60 minutes", "6 Survivors",
        "Stop a crazed taxi driver by planting explosives in the path of a constantly moving Muscle Car, then escape.",
        "Zombicide: 2nd Edition", listOf("5V", "6V", "7V", "8V"), 1,
        listOf("Find and plant explosives to destroy the car, and find the keys by taking the Green Objective, in any order. Then reach the Exit with all Survivors; the Exit Zone must have no Zombies."),
        listOf("Shuffle Blue and Green Objectives among Red Objectives, facedown. Red Objectives give 5 AP to the Survivor who takes them; keep them beside the Dashboard. A Survivor carrying 2 Red Objectives may spend 1 Action in a Street Zone to plant them. When the Muscle Car enters that Zone, eliminate all Actors there and remove the car; this earns no AP.", "Taking the Blue Objective gives 5 AP to all Survivors and places a Blue Spawn token in that Zone; the Blue Spawn Zone becomes active. Taking the Green Objective gives 5 AP to all Survivors; the Green Door cannot open until it is taken.", "At the end of the Player Phase, the Muscle Car moves 4 Zones following the map arrows toward the First Spawn Zone. If it leaves the map, place it at the right-side Red Spawn Zone and continue its movement. Whenever it enters a Zone with an Actor, roll 3 dice; each 4+ deals 2 Wounds to a Survivor or 1 hit to a Zombie, with Survivors targeted first. Actors may enter its Zone freely. Red Doors cannot open; internal doors divide buildings for spawning. The Muscle Car cannot be driven and has no Epic Weapon Crate. Each Epic Weapon Crate gives a random available Epic Weapon.")),
    DigitalMissionInfo("WM34", "Playground Time", "Medium", "120 minutes", "6 Survivors",
        "Rescue four hidden children from the old school and find a safe exit.",
        "Zombicide: 2nd Edition", listOf("1V", "2V", "3V", "4V", "5R", "6V", "7V", "8R", "9V"), 2,
        listOf("Rescue the four hidden kids as Companions and find the Exit, in any order. Then reach the Exit with all Survivors; the Exit Zone must have no Zombies."),
        listOf("Shuffle Blue and Green Objectives among Red Objectives, facedown. Place Bunny G, Lou, Ostara, and Odin in the indicated Zones without colored bases; set aside their ID cards. Each Objective gives 5 AP.", "A Survivor may spend 1 Action in a hidden kid's Zone to turn them into a Companion. Companions cannot suffer Friendly Fire. When both Blue and Green Objectives are taken, place the Exit in the Zone where the second Objective was taken.", "The Police Car can be driven. When searching it, draw cards until a Weapon card appears and discard the other cards; an Aaahh!! spawns a Walker as usual and interrupts the Search. Internal doors divide buildings for spawning purposes. Each Epic Weapon Crate gives a random available Epic Weapon.")),
    DigitalMissionInfo("WM35", "The Annoying Parrot", "Hard", "60 minutes", "6 Survivors",
        "Collect all Objectives, then destroy the First Spawn Zone with a Molotov to reveal the Exit and escape.",
        "Zombicide: 2nd Edition", listOf("1V", "3V", "5R", "6V"), 4,
        listOf("Take all Objectives, destroy the First Spawn Zone to reveal the Exit, then reach the Exit with all Survivors. The Exit Zone must have no Zombies."),
        listOf("Shuffle Blue and Green Objectives among Red Objectives, facedown. Place a Walker, Runner, and Brute as indicated. All three Spawn Zones are active from the start. Red Objectives give 5 AP to the taker; Blue and Green Objectives give 5 AP to all Survivors.", "Each Search Action requires a die roll: on 1–4, place one Zombie matching the last drawn Zombie card in the First Spawn Zone; ignore this if the discard pile is empty. If no miniatures of that type remain, this triggers extra Activations. On 5–6, nothing happens.", "Each time an Epic Weapon is taken, draw a Zombie Card for the Blue Spawn Zone. Each time an Objective is taken, draw a Zombie Card for the Green Spawn Zone. After all Objectives are taken, a Survivor must use a Molotov targeting the First Spawn Zone; replace its token with the Exit. Doors inside buildings divide them for spawning purposes. Each Epic Weapon Crate gives a random available Epic Weapon.")),
    DigitalMissionInfo("WM36", "Zombies Ate My Turkey!", "Special", "45 minutes", "2 Survivors",
        "Zoe and Julio must gather food, rescue their parents, and escape in time for Thanksgiving dinner.",
        "Zombicide: 2nd Edition", listOf("1R", "6R", "7V", "9R"), 5,
        listOf("Reach the Exit with both Survivors carrying at least 3 Food Equipment cards (Canned Food, Bag of Rice, and Water) and at least 1 Companion. Any Survivor may escape at the end of their Turn if the Exit Zone has no Zombies. Rank the result by Companions saved: S=4, A=3, B=2, C=1."),
        listOf("Play with only 2 Survivors. Shuffle the Green Objective among the Red Objectives, facedown. Set aside 4 Survivors as Companions and place one in each Objective Zone. Each Red Objective gives 5 AP.", "A Survivor may spend 1 Action to rescue a Companion in their Zone. Each Companion gives their Leader +1 Die: Combat in addition to any Blue Skills. A Survivor may lead multiple Companions. Losing a Companion does not cause defeat.", "When the Green Objective is taken, all Survivors gain 5 AP and the Exit token is placed in that Zone. Each Epic Weapon Crate gives a random available Epic Weapon and lets the taker search the Equipment deck for Canned Food, Bag of Rice, or Water, then reorganize for free. Doors inside buildings divide them for spawning purposes.")),
    DigitalMissionInfo("WM37", "The Forgotten Warehouse", "Medium", "90 minutes", "6 Survivors",
        "Search an old army warehouse for Epic Weapons, discover the special Blue Objective weapon, and escape.",
        "Zombicide: 2nd Edition", listOf("1R", "2R", "4R", "7V", "8R", "9R"), 6,
        listOf("Take all Epic Weapon Crates and find the REALLY Epic Weapon (Blue Objective), in any order. Then reach the Exit with all Survivors; the Exit Zone must have no Zombies."),
        listOf("Shuffle the Blue Objective among Red Objectives, facedown. Doors inside buildings divide them for spawning purposes. Each Red Objective gives 5 AP to the taker; Blue and Green Objectives give 5 AP to all Survivors.", "The Green Door can be opened only after the Green Objective is taken. Blue and Green Spawn Zones are inactive at the start; each becomes active when its matching Objective is taken.", "When the Blue Objective is taken, draw an Epic Weapon card, ignoring any Aaahh!! cards, and give it Damage 3; place a Noise token on the card as a reminder. If the Blue Door is still closed, open it automatically. Each Epic Weapon Crate gives a random available Epic Weapon and allows free inventory reorganization.")),
    DigitalMissionInfo("WM40", "Running Over the Dead!", "Hard", "75 minutes", "6 Survivors",
        "Repair Uncle Clay's old Muscle Car into a road warrior, then drive through the Blue Spawn Zone and escape.",
        "Zombicide: 2nd Edition", listOf("1V", "2R", "3R", "4V", "5V", "6V", "7V", "8V", "9V"), 7,
        listOf("Find the Muscle Car's key (Blue Objective) and battery (Green Objective), drive over the Blue Spawn Zone, then reach the Exit with all Survivors. The Exit Zone must have no Zombies."),
        listOf("Shuffle Blue and Green Objectives among Red Objectives, facedown. Place 3 Survivors in each Starting Zone as indicated. The Blue Spawn Zone is active from the start. The Muscle Car cannot be driven until both Blue and Green Objectives are taken.", "Taking Blue and Green Objectives gives 5 AP to all Survivors. There is no Epic Weapon Crate in the Muscle Car. Survivors may only use the Fast Drive Action while driving (move 2 consecutive Zones and perform a Car Attack). Each Red Objective gives 5 AP to the taker; keep these beside the board as the Pimped Out Pool.", "After a Car Attack, remove one Red Objective from the Pimped Out Pool for each Zombie remaining in the Car's Zone. If a Red Objective must be removed but none remain, the car breaks and the game is lost. When the Muscle Car enters the Blue Spawn Zone, replace that Spawn token with the Exit token. Doors divide buildings for spawning purposes."))
)

private val fortMissionInfo = mapOf(
    "M1" to FortMissionInfo("Gathering the Braves", "Medium", "60 minutes", listOf("3V", "7R", "6V", "1R", "2R", "9V"), "Gather all Survivors in the same Zone.", "10am"),
    "M2" to FortMissionInfo("Digital Clues", "Medium", "60 minutes", listOf("17R", "2R", "18R", "20R", "19R", "1R"), "Go to the Command Office on tile 19R.", "7am"),
    "M3" to FortMissionInfo("Send My Love to Linda", "Medium", "90 minutes", listOf("17R", "21R", "9R", "18V", "4V", "19V"), "Take Objective cards until you find a lead to Penny's mother.", "9am"),
    "M4" to FortMissionInfo("Secret Lab", "Medium", "60 minutes", listOf("17R", "16R", "19V", "20V"), "Take Objective cards until you find a way to prevent the zombies from spreading various plagues.", "4am"),
    "M5" to FortMissionInfo("Pathogen Eden", "Medium", "45 minutes", listOf("7R", "6R", "19V", "16R", "4R", "17V"), "Destroy the laboratories and any Zombies standing in them; take the Refrigerators Objective card on tile 19V.", "8pm"),
    "M6" to FortMissionInfo("Area Romero", "Medium", "120 minutes", listOf("2V", "21V", "18R", "17R", "19V", "16V"), "Check the Officer's room on tile 16V.", "10pm"),
    "M7" to FortMissionInfo("The Headquarters", "Medium", "120 minutes", listOf("21V", "18V", "9R", "20R", "17V", "16V"), "Take the Objective card on tile 16V to start the decontamination protocol.", "11am"),
    "M8" to FortMissionInfo("In the Army Now", "Very Hard", "120 minutes", listOf("9V", "8R", "16R", "18V", "21V", "7R", "19V", "20R", "17R"), "Take Objective cards until you discover what happened in Fort Hendrix.", "4am"),
    "M9" to FortMissionInfo("Pandora's Box", "Special", "Special duration", listOf("16V", "5V", "7R", "3V", "9V", "17V"), "Recover Hendrix's legacy on tile 16V.", "5am"),
    "M10" to FortMissionInfo("Purple Haze", "Very Hard", "90 minutes", listOf("21R", "2V", "16R", "20R", "4R", "3R", "18R", "19R"), "Prevent outside Zombies from entering Fort Hendrix, and find a way to destroy the Green Spawn Zone and lock the base's accesses.", "1am")
)

private val washingtonMissionInfo = mapOf(
    "M1" to WashingtonMissionInfo("Facing the Impossible", "Medium", "45 minutes", listOf("2V", "7R", "4R", "5R", "8R", "3R"), "Open all doors in the diner and take all Objective cards."),
    "M2" to WashingtonMissionInfo("Leave No One Behind", "Medium", "60 minutes", listOf("2R", "1R", "8R", "6R", "7V", "4V"), "Save as many people as possible. Take all Objective cards."),
    "M3" to WashingtonMissionInfo("DEFCON 5: Fade Out", "Medium", "45 minutes", listOf("6V", "8V", "3V", "4V", "1V", "2V"), "Explore the area and take the Objective cards, except the card revealed automatically at 7 PM."),
    "M4" to WashingtonMissionInfo("The War Zone", "Medium", "60 minutes", listOf("10V", "11V", "9R", "1R", "6R", "3R"), "Turn the projector off, then reach the Exit with all Survivors."),
    "M5" to WashingtonMissionInfo("DEFCON 4: Double Take", "Medium", "90 minutes", listOf("13V", "12R", "11R", "10R", "15R", "14R"), "Explore the White House wing to find a suitable shelter."),
    "M6" to WashingtonMissionInfo("DEFCON 3: Round House", "Medium", "90 minutes", listOf("11R", "15R", "10R", "14R", "12R", "13R"), "Find the bunker’s entrance."),
    "M7" to WashingtonMissionInfo("Get the Key Card", "Hard", "45 minutes", listOf("15V", "11R", "10R", "13V", "14V", "12R"), "Find the bunker’s keycard. An Abomination wears it around its neck; do not use Molotovs to obtain it."),
    "M8" to WashingtonMissionInfo("Restore the Elevator", "Hard", "60 minutes", listOf("12V", "11R", "13V", "14V", "15V"), "Find and switch on the power generator."),
    "M9" to WashingtonMissionInfo("DEFCON 2: Fast Pace", "Very Hard", "45 minutes", listOf("12R", "10R", "11R", "13R", "14R", "15R"), "Open the presidential bunker by reaching its entrance and reading the Objective card."),
    "M10" to WashingtonMissionInfo("DEFCON 1: Cocked Pistol", "Very Hard", "60 minutes", listOf("10V", "11V", "7R", "2V", "1R", "8V"), "Fight for your new life. Find a way to exit the area.")
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
    "M7" to MissionInfo("M7", "Grindhouse", "Hard", "45 minutes", 43, listOf("6V", "4V", "3V", "8V"),
        "An accident at the nuclear power plant left technicians quarantined in a hospital wing. Prevent the infected from escaping.",
        listOf("Secure the buildings by gathering all Zombie Spawn tokens on the Starting Spawn Zone.", "Throw a Molotov at the Starting Spawn Zone to lock the infected in for good. The game is lost whenever a Zombie starts its Activation in a highlighted Zone."),
        listOf("Place 3 permanent Noise tokens in each highlighted Zone; they are not removed during the End Phase.", "At the end of Setup, spawn Zombies in both buildings on tiles 4V and 8V as if their doors had just been opened.", "A Survivor standing in an empty Spawn Zone may spend 1 Action to neutralize it by moving its Zombie Spawn token to the Starting Spawn Zone.", "Each Objective grants 5 Adrenaline Points. Each Epic Weapon Crate grants a random available Epic Weapon."), 4, 4, permanentNoiseTokensPerHighlightedZone = 3),
    "M8" to MissionInfo("M8", "Zombie Police", "Hard", "30 minutes", 44, listOf("3V", "4V", "6R", "9R", "8V", "5V"),
        "The Survivors discover a memo pointing to a bunker near the police station, but its remote-controlled entrance is locked and the district is overrun.",
        listOf("Take the Blue and Green Objectives to find the remote controls.", "Reach the Exit with all Survivors; the Exit Zone is usable only when no Zombies are in it."),
        listOf("Place the Blue and Green Objectives in the indicated Zones. Each Objective grants 5 Adrenaline Points.", "The building with the Exit cannot be opened until both colored Objectives have been taken.", "Cars can be driven. The Muscle Car contains an Epic Weapon Crate. When searching the Police Car, draw until a weapon is found; Aaahh!! interrupts the Search.", "Each Epic Weapon Crate grants a random available Epic Weapon."), 2, 7, true, policeCarCount = 1, muscleCarCount = 1),
    "M11" to MissionInfo("M11", "The Ditch", "Medium", "30 minutes", 47, listOf("4R", "2R", "9V", "7V"),
        "The Survivors must cross the narrow Ditch and return to their shelter before the weather and the zombie horde catch them.",
        listOf("Reach the Exit with all Survivors. Survivors may escape at the end of their Turn if the Exit Zone has no Zombies and no Abominations remain on the board."),
        listOf("The team starts with 2 Companions distributed among the Survivors. Place an Abomination in the indicated Zone; it does not move until there is an open path to the Survivors.", "Each Objective grants 5 Adrenaline Points. Each Epic Weapon Crate grants a random available Epic Weapon.", "The two Muscle Cars cannot be driven. Each contains an Epic Weapon; place an Epic Weapon Crate on each."), 4, 6, muscleCarCount = 2, nonDrivableMuscleCar = true, abominationCount = 1),
    "M12" to MissionInfo("M12", "Car Crash", "Medium", "60 minutes", 48, listOf("9V", "1R", "8R", "6R", "7V", "2R"),
        "Six injured Survivors are trapped in the wrecked car. Find first aid kits and turn them into Companions before the zombies reach them.",
        listOf("Turn all 6 injured Survivors in the Zone on tile 8R into Companions by delivering 6 first aid kits represented by Red Objectives.", "The game is lost whenever a Zombie activates in the injured Survivors' Zone."),
        listOf("Note the open doors on tile 8R. Place 6 unused Survivor miniatures in the indicated injured-Survivor Zone; set their ID Cards aside. They cannot act or carry Equipment, but count as Noise tokens.", "Each Red Objective grants 5 Adrenaline Points and a first aid kit. Place it on the Survivor's Dashboard; it takes no inventory slot and can be traded. Spend 1 Action to drop a Red Objective in the injured-Survivor Zone to turn one injured Survivor into a Companion.", "The crashed car cannot be driven or searched. Each Epic Weapon Crate grants a random available Epic Weapon."), 6, 6, crashedCarCount = 1, extraSurvivorCount = 6),
    "M13" to MissionInfo("M13", "Burning Streets", "Medium", "45 minutes", 49, listOf("7V", "4V", "1R", "6R", "2V", "3V"),
        "A massive zombie herd is approaching the shelter. Burn the side accesses, then block the road and torch the cars to divert the horde.",
        listOf("Throw a Molotov at each Red Spawn Zone to move its Red Zombie Spawn token to the Starting Spawn Zone.", "Drive both Cars to the highlighted parking Zone. Both must be parked there to win."),
        listOf("The Blue Objective is on tile 2V and the Green Objective is on tile 3V. Each grants 5 Adrenaline Points and a Molotov; treat the token as a Molotov card when taken.", "Throwing a Molotov at a Red Spawn Zone moves that Spawn token to the Starting Spawn Zone.", "Cars can be driven. The Muscle Car contains an Epic Weapon Crate. When searching the Police Car, draw until a weapon is found; Aaahh!! interrupts the Search.", "Each Epic Weapon Crate grants a random available Epic Weapon."), 3, 7, blueObjectiveCount = 1, greenObjectiveCount = 1, policeCarCount = 1, muscleCarCount = 1),
    "M14" to MissionInfo("M14", "Breakfast at Jesse's", "Medium", "45 minutes", 50, listOf("9V", "4R", "6R", "2V", "8R", "7V"),
        "Jesse's Diner is sealed behind reinforced doors, with people trapped inside. Rally the Companions and bring everyone to the Exit.",
        listOf("Rally all Companions and gather all Survivors on the Exit Zone, with no Zombies in it.", "The game is lost if a Survivor, including one on tile 4R, is eliminated."),
        listOf("Note the closed door between tiles 7V and 8R, the Green Objective on tile 2V, and the Green Door on tile 4R. Place 6 unused Survivor miniatures on the Exit Zone as Companions to rally.", "The door between tiles 7V and 8R makes the two parts of that building count as separate buildings for Zombie spawning only.", "Doors in the building spanning tiles 4R and 9V cannot be opened, except for the Green Door. The Green Door cannot be opened until the Green Objective has been taken.", "Each Objective grants 5 Adrenaline Points. Each Epic Weapon Crate grants a random available Epic Weapon."), 5, 6, greenObjectiveCount = 1, greenDoorCount = 1, extraSurvivorCount = 6),
    "M16" to MissionInfo("M16", "Pale Shelter", "Medium", "45 minutes", 52, listOf("4V", "2V", "3V", "1V"),
        "The Survivors find a promising shelter with reinforced doors, though several doors have been broken by an Abomination.",
        listOf("Barricade the broken doors using Objective tokens.", "Secure the shelter by eliminating all Zombies remaining in the building."),
        listOf("Note the doors on tiles 2V, 3V, and 4V. Place an Abomination in the indicated Zone.", "The four broken doors cannot be opened by Zombies or standard door-opening rules; Survivors may open them without door-opening Equipment. Zombies ignore them when moving and open them immediately if they pass through.", "Each Objective grants 5 Adrenaline Points. Spend 1 Action to place an Objective token from a Survivor's Dashboard into an empty Zone with a broken door to barricade it permanently.", "Each Epic Weapon Crate grants a random available Epic Weapon."), 4, 4, abominationCount = 1, brokenDoorCount = 4),
    "M17" to MissionInfo("M17", "The Blight", "Hard", "60 minutes", 53, listOf("7V", "5V", "4V", "2R", "9R", "6R", "8R", "1R", "3R"),
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
private fun MissionOverviewScreen(missionId: String, sourceId: String, onBack: () -> Unit, onBegin: () -> Unit) {
    val mission = coreMissionInfo[missionId] ?: coreMissionInfo.getValue("M0")
    val washington = washingtonMissionInfo[missionId]
    val fort = fortMissionInfo[missionId]
    val digital = digitalMissionInfo.firstOrNull { it.id == missionId }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("MISSION $missionId" + if (sourceId == "digital" && digital != null) " · PDF page ${digital.pdfPage}" else "", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    when (sourceId) {
                        "washington" -> washington?.name ?: mission.name
                        "fort" -> fort?.name ?: "Fort Hendrix"
                        "digital" -> digital?.name ?: missionId
                        else -> mission.name
                    },
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold
                )
                Text(
                    when (sourceId) {
                        "washington" -> "${washington?.difficulty ?: ""} · About ${washington?.duration ?: ""}"
                        "fort" -> "${fort?.difficulty ?: ""} · About ${fort?.duration ?: ""}"
                        "digital" -> "${digital?.difficulty ?: ""} · About ${digital?.duration ?: ""}" + (digital?.survivors?.takeIf { it != "Not specified" }?.let { " · $it" } ?: "")
                        else -> "${mission.difficulty} · About ${mission.duration}"
                    }, style = MaterialTheme.typography.titleSmall
                )
            }
            item {
                Text("Mission briefing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    when (sourceId) {
                        "washington" -> "Washington Z.C. campaign mission. Follow the expansion rulebook for the complete briefing and special rules."
                        "fort" -> "Star-Spangled Banner campaign · Starting hour: ${fort?.startingHour ?: "see manual"}."
                        "digital" -> digital?.briefing ?: "Mission details are not available in this batch."
                        else -> mission.briefing
                    }, style = MaterialTheme.typography.bodyLarge
                )
            }
            item {
                HorizontalDivider()
                Text("Objectives", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                (when (sourceId) {
                    "washington" -> listOf(washington?.objective ?: "Follow the objective described in the Washington Z.C. rulebook.")
                    "fort" -> listOf(fort?.objective ?: "Consult the Fort Hendrix mission sheet.")
                    "digital" -> digital?.objectives ?: emptyList()
                    else -> mission.objectives
                }).forEach { BulletText(it) }
            }
            item {
                HorizontalDivider()
                Text("Special rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                (when (sourceId) {
                    "washington" -> listOf("Use the Washington Z.C. expansion rules and mission map.")
                    "fort" -> listOf("Use the Fort Hendrix campaign rules, including Advanced Equipment, KO / R.I.P., Clock and Night rules, and Shooter Walkers.")
                    "digital" -> digital?.specialRules ?: emptyList()
                    else -> mission.specialRules
                }).forEach { BulletText(it) }
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
    sourceId: String,
    tiles: List<String>,
    preparedItems: Set<String>,
    onTogglePrepared: (String) -> Unit,
    onBack: () -> Unit,
    onSetup: () -> Unit
) {
    val mission = coreMissionInfo[missionId] ?: coreMissionInfo.getValue("M0")
    val checklistComponents = if (sourceId == "digital") buildList {
        when (missionId) {
            "WM1" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Place the Starting Spawn Zone token shown on the mission map."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token shown on the mission map."))
                add(MissionComponent("Epic Weapon Crates", "4", "Place 4 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "4", "Place 4 Red Objective tokens."))
                add(MissionComponent("Muscle Car token", "1", "Place 1 Muscle Car token."))
                add(MissionComponent("Walker miniatures", "4", "Place 4 Walkers in the indicated Zones."))
            }
            "WM2" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Green Spawn Zone token", "1", "Prepare 1 Green Spawn Zone token."))
                add(MissionComponent("Epic Weapon Crates", "6", "Place 6 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "5", "Place 5 Red Objective tokens."))
                add(MissionComponent("Green Objective token", "1", "Place 1 Green Objective token."))
                add(MissionComponent("Abomination miniature", "1", "Place 1 Abomination in the indicated Zone."))
            }
            "WM3" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token."))
                add(MissionComponent("Epic Weapon Crates", "4", "Place 4 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "4", "Place 4 Red Objective tokens."))
                add(MissionComponent("Red Open Door tokens", "2", "Place 2 Red Open Door tokens."))
                add(MissionComponent("Red Closed Door tokens", "2", "Place 2 Red Closed Door tokens."))
                add(MissionComponent("Walker miniatures", "2", "Place 2 Walkers in the indicated Zones."))
            }
            "WM4" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token."))
                add(MissionComponent("Epic Weapon Crates", "6", "Place 6 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "6", "Place 6 Red Objective tokens."))
                add(MissionComponent("Red Open Door tokens", "3", "Place 3 Red Open Door tokens."))
            }
            "WM6" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token shown on the mission map."))
                add(MissionComponent("Epic Weapon Crates", "6", "Place 6 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "6", "Place 6 Red Objective tokens."))
                add(MissionComponent("Red Closed Door token", "1", "Prepare 1 Red Closed Door token."))
                add(MissionComponent("Blue Closed Door token", "1", "Prepare 1 Blue Closed Door token."))
                add(MissionComponent("Green Closed Door token", "1", "Prepare 1 Green Closed Door token."))
                add(MissionComponent("Police Car token", "1", "Prepare 1 Police Car token."))
            }
            "WM7" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token shown on the mission map."))
                add(MissionComponent("Blue Spawn Zone token", "1", "Prepare 1 Blue Spawn Zone token."))
                add(MissionComponent("Green Spawn Zone token", "1", "Prepare 1 Green Spawn Zone token."))
                add(MissionComponent("Epic Weapon Crates", "6", "Place 6 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "6", "Place 6 Red Objective tokens."))
                add(MissionComponent("Police Car token", "1", "Prepare 1 Police Car token."))
            }
            "WM8" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token shown on the mission map."))
                add(MissionComponent("Blue Spawn Zone token", "1", "Prepare 1 Blue Spawn Zone token."))
                add(MissionComponent("Green Spawn Zone token", "1", "Prepare 1 Green Spawn Zone token."))
                add(MissionComponent("Epic Weapon Crates", "6", "Place 6 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "4", "Place 4 Red Objective tokens."))
                add(MissionComponent("Blue Objective token", "1", "Prepare 1 Blue Objective token."))
                add(MissionComponent("Green Objective token", "1", "Prepare 1 Green Objective token."))
                add(MissionComponent("Blue Closed Door token", "1", "Prepare 1 Blue Closed Door token."))
                add(MissionComponent("Green Closed Door token", "1", "Prepare 1 Green Closed Door token."))
                add(MissionComponent("Police Car token", "1", "Prepare 1 Police Car token."))
                add(MissionComponent("Muscle Car token", "1", "Prepare 1 Muscle Car token."))
            }
            "WM10" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token shown on the mission map."))
                add(MissionComponent("Blue Spawn Zone token", "1", "Prepare 1 Blue Spawn Zone token."))
                add(MissionComponent("Green Spawn Zone token", "1", "Prepare 1 Green Spawn Zone token."))
                add(MissionComponent("Epic Weapon Crates", "4", "Place 4 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "4", "Place 4 Red Objective tokens."))
                add(MissionComponent("Red Closed Door tokens", "2", "Prepare 2 Red Closed Door tokens."))
            }
            "WM11" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token shown on the mission map."))
                add(MissionComponent("Epic Weapon Crates", "6", "Place 6 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "6", "Place 6 Red Objective tokens."))
            }
            "WM12" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token shown on the mission map."))
                add(MissionComponent("Blue Spawn Zone token", "1", "Prepare 1 Blue Spawn Zone token."))
                add(MissionComponent("Green Spawn Zone token", "1", "Prepare 1 Green Spawn Zone token."))
                add(MissionComponent("Epic Weapon Crates", "9", "Place 9 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "6", "Place 6 Red Objective tokens."))
                add(MissionComponent("Muscle Car tokens", "3", "Prepare 3 Muscle Car tokens."))
            }
            "WM13" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token shown on the mission map."))
                add(MissionComponent("Green Spawn Zone token", "1", "Prepare 1 Green Spawn Zone token."))
                add(MissionComponent("Epic Weapon Crates", "9", "Place 9 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "4", "Place 4 Red Objective tokens."))
                add(MissionComponent("Blue Objective token", "1", "Prepare 1 Blue Objective token."))
                add(MissionComponent("Green Objective token", "1", "Prepare 1 Green Objective token."))
                add(MissionComponent("Police Car token", "1", "Prepare 1 Police Car token."))
                add(MissionComponent("Red Closed Door tokens", "2", "Prepare 2 Red Closed Door tokens."))
                add(MissionComponent("Blue Closed Door token", "1", "Prepare 1 Blue Closed Door token."))
                add(MissionComponent("Green Closed Door token", "1", "Prepare 1 Green Closed Door token."))
            }
            "WM14" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token shown on the mission map."))
                add(MissionComponent("Green Spawn Zone token", "1", "Prepare 1 Green Spawn Zone token."))
                add(MissionComponent("Epic Weapon Crates", "4", "Place 4 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "3", "Place 3 Red Objective tokens."))
                add(MissionComponent("Blue Objective token", "1", "Prepare 1 Blue Objective token."))
                add(MissionComponent("Green Objective token", "1", "Prepare 1 Green Objective token."))
                add(MissionComponent("Red Closed Door token", "1", "Prepare 1 Red Closed Door token."))
                add(MissionComponent("Red Open Door token", "1", "Prepare 1 Red Open Door token."))
                add(MissionComponent("Blue Closed Door token", "1", "Prepare 1 Blue Closed Door token."))
                add(MissionComponent("Green Closed Door token", "1", "Prepare 1 Green Closed Door token."))
            }
            "WM15" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token shown on the mission map."))
                add(MissionComponent("Epic Weapon Crates", "4", "Place 4 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "4", "Place 4 Red Objective tokens."))
                add(MissionComponent("Abomination miniature", "1", "Prepare 1 Abomination miniature."))
                add(MissionComponent("Runner miniatures", "3", "Prepare 3 Runner miniatures."))
            }
            "WM16" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Blue Spawn Zone token", "1", "Prepare 1 Blue Spawn Zone token."))
                add(MissionComponent("Epic Weapon Crates", "6", "Place 6 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "4", "Place 4 Red Objective tokens."))
                add(MissionComponent("Blue Objective token", "1", "Prepare 1 Blue Objective token."))
                add(MissionComponent("Green Objective token", "1", "Prepare 1 Green Objective token."))
                add(MissionComponent("Red Closed Door tokens", "9", "Prepare 9 Red Closed Door tokens."))
                add(MissionComponent("Abomination miniature", "1", "Prepare 1 Abomination miniature."))
            }
            "WM17" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token shown on the mission map."))
                add(MissionComponent("Epic Weapon Crates", "4", "Place 4 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "3", "Place 3 Red Objective tokens."))
                add(MissionComponent("Blue Objective token", "1", "Prepare 1 Blue Objective token."))
                add(MissionComponent("Green Objective token", "1", "Prepare 1 Green Objective token."))
            }
            "WM18" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token shown on the mission map."))
                add(MissionComponent("Green Spawn Zone token", "1", "Prepare 1 Green Spawn Zone token."))
                add(MissionComponent("Epic Weapon Crates", "4", "Place 4 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "3", "Place 3 Red Objective tokens."))
                add(MissionComponent("Green Objective token", "1", "Prepare 1 Green Objective token."))
                add(MissionComponent("Red Open Door tokens", "2", "Prepare 2 Red Open Door tokens."))
                add(MissionComponent("Red Closed Door token", "1", "Prepare 1 Red Closed Door token."))
                add(MissionComponent("Muscle Car token", "1", "Prepare 1 Muscle Car token."))
                add(MissionComponent("Walker miniatures", "5", "Prepare 5 Walker miniatures."))
            }
            "WM19" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone token", "1", "Prepare 1 Red Spawn Zone token."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token shown on the mission map."))
                add(MissionComponent("Epic Weapon Crates", "4", "Place 4 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "4", "Place 4 Red Objective tokens."))
                add(MissionComponent("Red Closed Door tokens", "4", "Prepare 4 Red Closed Door tokens."))
            }
            "WM20" -> {
                add(MissionComponent("Survivor Starting Zone token", "1", "Place at the Starting Zone shown on the mission map."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Blue Spawn Zone token", "1", "Place the Blue Spawn Zone token shown on the map; it activates when the Blue Objective is taken."))
                add(MissionComponent("Exit Zone token", "1", "Place at the Exit shown on the map."))
                add(MissionComponent("Objective tokens", "4", "Use 4 Red Objectives and shuffle the Blue Objective among them."))
                add(MissionComponent("Pimpweapon Crates", "4", "Place the 4 Pimpweapon Crates shown on the map."))
                add(MissionComponent("Closed Door tokens", "4", "Place the closed doors shown on the map."))
                add(MissionComponent("Companion miniature (Rod)", "1", "Use a random Companion to represent Rod."))
            }
            "WM21" -> {
                add(MissionComponent("Survivor Starting Zones", "2", "Place a Survivor in each Starting Zone."))
                add(MissionComponent("Spawn Zone tokens", "As shown", "Prepare the colored Spawn Zones shown on the map."))
                add(MissionComponent("Exit Zone token", "1", "Place at the Exit shown on the map."))
                add(MissionComponent("Objective tokens", "4", "Use the Blue, Green, and Red Objectives as indicated."))
                add(MissionComponent("Pimpweapon Crates", "6", "Place the 6 Pimpweapon Crates shown on the map."))
                add(MissionComponent("Pimpmobile token", "1", "Place the Pimpmobile shown on the map."))
                add(MissionComponent("Closed Door tokens", "As shown", "Place the Red, Blue, and Green doors as shown; the Red Door is blocked."))
                add(MissionComponent("Companion miniatures", "2", "Place a Companion in each Starting Zone."))
            }
            "WM23" -> {
                add(MissionComponent("Survivor Starting Zone token", "1", "Place at the Starting Zone shown on the map."))
                add(MissionComponent("Spawn Zone tokens", "As shown", "Prepare the colored Spawn Zones shown on the map."))
                add(MissionComponent("Objective tokens", "As shown", "Place the Blue Objective on tile 2V and the Green Objective on tile 3V, outside its Objective Zone."))
                add(MissionComponent("Pimpweapon Crates", "6", "Place the Pimpweapon Crates shown on the map."))
                add(MissionComponent("Police Car token", "1", "Place the Police Car shown on the map."))
                add(MissionComponent("Pimpmobile token", "1", "Place the Pimpmobile shown on the map."))
                add(MissionComponent("Agatha counters", "2", "Use two counters of any kind for Agatha’s counter pool."))
                add(MissionComponent("Abominacop miniature", "1", "Set the Abominacop aside; it enters play through the special rules."))
            }
            "WM24" -> {
                add(MissionComponent("Survivor Starting Zone token", "1", "Place at the Starting Zone shown on the map."))
                add(MissionComponent("Spawn Zone tokens", "As shown", "Prepare the colored Spawn Zones shown on the map."))
                add(MissionComponent("Exit Zone tokens", "7", "Use the Exit tokens shown on the map."))
                add(MissionComponent("Objective tokens", "As shown", "Use Red Objectives as fuel and place Blue and Green Objectives on tiles 8V and 2V."))
                add(MissionComponent("Pimpweapon Crates", "9", "Place the 9 Pimpweapon Crates shown on the map."))
                add(MissionComponent("Pimpmobile token", "1", "Place the Pimpmobile shown on the map."))
                add(MissionComponent("Open and closed Door tokens", "As shown", "Place the reinforced doors shown on the map."))
                add(MissionComponent("Noise tokens", "4", "Place 4 Noise tokens on the Pimpmobile when a Survivor first interacts with it."))
            }
            "WM25" -> {
                add(MissionComponent("Survivor Starting Zone token", "1", "Place at the Starting Zone shown on the map."))
                add(MissionComponent("Spawn Zone tokens", "3", "Prepare the 3 Spawn Zones shown on the map."))
                add(MissionComponent("Exit Zone token", "1", "Place at tile 8R as shown on the map."))
                add(MissionComponent("Objective tokens", "3", "Place Red Objectives as shown; do not place one on tile 8R."))
                add(MissionComponent("Pimpweapon Crates", "4", "Place the 4 Pimpweapon Crates shown on the map."))
                add(MissionComponent("Closed Door tokens", "As shown", "The Red Doors cannot be opened; the Blue and Green subway doors are linked."))
            }
            "WM26" -> {
                add(MissionComponent("Survivor Starting Zone token", "1", "Place at the Starting Zone shown on the map."))
                add(MissionComponent("Spawn Zone tokens", "As shown", "Prepare the colored Spawn Zones shown on the map."))
                add(MissionComponent("Exit Zone token", "1", "Place at the Exit shown on the map."))
                add(MissionComponent("Red Objective tokens", "4", "Place the 4 Objectives shown on the map."))
                add(MissionComponent("Pimpweapon Crates", "4", "Place the 4 Pimpweapon Crates shown on the map."))
                add(MissionComponent("Closed Door token", "1", "Place the door between tiles 1R and 7R."))
            }
            "WM9" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Survivor Starting Zone token shown on the mission map."))
                add(MissionComponent("Red Spawn Zone tokens", "2", "Prepare 2 Red Spawn Zone tokens."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token shown on the mission map."))
                add(MissionComponent("Red Objective tokens", "4", "Place 4 Objective tokens; each gives 5 Adrenaline Points to the Survivor who takes it."))
                add(MissionComponent("Pimpweapon Crates", "4", "Place 4 Pimpweapon Crates."))
                add(MissionComponent("Red Closed Door tokens", "5", "Place 5 Red Closed Door tokens."))
                add(MissionComponent("Noise tokens for fire markers", "As needed", "Use Noise tokens as fire markers and follow the fire-spread rules. Fire markers remain between End Phases."))
            }
            "WM5" -> {
                add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
                add(MissionComponent("Red Spawn Zone token", "1", "Prepare 1 Red Spawn Zone token."))
                add(MissionComponent("Exit Zone token", "1", "Place the Exit Zone token."))
                add(MissionComponent("Epic Weapon Crates", "4", "Place 4 Epic Weapon Crates."))
                add(MissionComponent("Red Objective tokens", "4", "Place 4 Red Objective tokens."))
                add(MissionComponent("Red Closed Door tokens", "6", "Place 6 Red Closed Door tokens."))
            }
            else -> {
                add(MissionComponent("Spawn Zone tokens", "As shown", "Match the colored Spawn Zones and Starting Spawn Zone shown on the mission sheet."))
                add(MissionComponent("Objective tokens", "As shown", "Use the colors and quantities shown on the mission sheet; some Objectives are placed facedown."))
                add(MissionComponent("Epic Weapon Crates", "As shown", "Place the crates shown on the mission map."))
                add(MissionComponent("Doors / cars / other tokens", "As shown", "Check the mission sheet for any doors, vehicles, Abominations, or special tokens required."))
            }
        }
    } else if (sourceId == "fort") buildList {
        val redSpawn = if (missionId == "M4") 0 else 2
        if (redSpawn > 0) add(MissionComponent("Red Spawn Zone tokens", redSpawn.toString(), "Prepare the Red Spawn tokens for the mission map."))
        add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn token for the mission map."))
        if (missionId in listOf("M6", "M7", "M8", "M10")) add(MissionComponent("Green Spawn Zone token", "1", "Prepare the Green Spawn token for the mission map."))
        val objectives = mapOf("M1" to 6, "M2" to 6, "M3" to 6, "M4" to 8, "M5" to 8, "M6" to 8, "M7" to 6, "M8" to 9, "M9" to 8, "M10" to 8)
        val notes = mapOf("M1" to 2, "M2" to 1, "M3" to 1, "M4" to 3, "M5" to 2, "M6" to 2, "M8" to 2, "M9" to 1, "M10" to 4)
        val weapons = mapOf("M1" to 6, "M2" to 6, "M3" to 6, "M4" to 4, "M5" to 6, "M6" to 8, "M7" to 6, "M8" to 9, "M9" to 8, "M10" to 8)
        add(MissionComponent("Objective cards", objectives[missionId].toString(), "Prepare Objective Set #${missionId.removePrefix("M")} for this mission."))
        notes[missionId]?.let { count ->
            val nums = (1..count).joinToString(", ") { "#$it" }
            add(MissionComponent("Notes", count.toString(), "Required notes: $nums."))
        }
        add(MissionComponent("Epic Weapon Crates", weapons[missionId].toString(), "Prepare the Epic Weapon Crates for this mission."))
        if (missionId == "M4") add(MissionComponent("Red Door tokens", "3", "Prepare 3 Red Door tokens."))
        if (missionId in listOf("M5", "M6")) add(MissionComponent("Green Door token", "1", "Prepare 1 Green Door token."))
        if (missionId == "M6") add(MissionComponent("Blue Door token", "1", "Prepare 1 Blue Door token."))
        if (missionId == "M4") add(MissionComponent("Muscle Car", "1", "Prepare 1 Muscle Car token."))
    } else if (sourceId == "washington") buildList {
        // Washington Z.C. component quantities supplied by the user.
        val redSpawnCounts = mapOf("M1" to 2, "M2" to 2, "M3" to 1, "M4" to 2, "M5" to 3, "M6" to 2, "M7" to 2, "M8" to 2, "M9" to 2, "M10" to 3)
        val greenSpawnCounts = mapOf("M4" to 1, "M5" to 1, "M7" to 1)
        val weaponCrateCounts = mapOf("M1" to 6, "M2" to 6, "M3" to 6, "M4" to 5, "M5" to 6, "M6" to 6, "M7" to 6, "M8" to 5, "M9" to 5, "M10" to 6)
        val noiseCounts = mapOf("M1" to 6, "M2" to 6)
        val redDoorCounts = mapOf("M5" to 4, "M8" to 5)
        val blueDoorCounts = mapOf("M5" to 1, "M7" to 1, "M8" to 1)
        val greenDoorCounts = mapOf("M5" to 1, "M7" to 1, "M8" to 1)
        redSpawnCounts[missionId]?.takeIf { it > 0 }?.let { add(MissionComponent("Red Spawn Zone tokens", it.toString(), "Prepare the red Spawn Zone tokens for this mission.")) }
        add(MissionComponent("Starting Spawn Zone token", "1", "Prepare the Starting Spawn Zone token."))
        greenSpawnCounts[missionId]?.takeIf { it > 0 }?.let { add(MissionComponent("Green Spawn Zone tokens", it.toString(), "Prepare the green Spawn Zone token.")) }
        if (missionId == "M4") add(MissionComponent("Exit Zone token", "1", "Place at the Exit symbol shown on the mission map."))
        weaponCrateCounts[missionId]?.takeIf { it > 0 }?.let { add(MissionComponent("Epic Weapon Crates", it.toString(), "Prepare the Epic Weapon Crates for this mission.")) }
        noiseCounts[missionId]?.takeIf { it > 0 }?.let { add(MissionComponent("Noise tokens", it.toString(), "Prepare the Noise tokens for this mission.")) }
        redDoorCounts[missionId]?.takeIf { it > 0 }?.let { add(MissionComponent("Red Door tokens", it.toString(), "Prepare the red Door tokens for this mission.")) }
        blueDoorCounts[missionId]?.takeIf { it > 0 }?.let { add(MissionComponent("Blue Door token", it.toString(), "Prepare the blue Door token for this mission.")) }
        greenDoorCounts[missionId]?.takeIf { it > 0 }?.let { add(MissionComponent("Green Door token", it.toString(), "Prepare the green Door token for this mission.")) }

        val humveeCounts = mapOf("M1" to 1, "M2" to 1)
        val objectiveCardCounts = mapOf("M1" to 7, "M2" to 6, "M3" to 7, "M4" to 9, "M5" to 9, "M6" to 6, "M7" to 6, "M8" to 8, "M9" to 6, "M10" to 9)
        val notesCounts = mapOf("M1" to 2, "M5" to 5, "M6" to 6, "M7" to 3, "M8" to 5, "M10" to 1)
        val noteNumbers = mapOf("M1" to listOf(1, 2), "M5" to listOf(1, 2, 3, 4, 5), "M6" to listOf(1, 2, 3, 4, 5, 6), "M7" to listOf(1, 2, 3), "M8" to listOf(1, 2, 3, 4, 5), "M10" to listOf(1))
        val lightBeamCounts = mapOf("M4" to 3)
        humveeCounts[missionId]?.let { add(MissionComponent("Humvee Car token", it.toString(), "Prepare the Humvee Car token for this mission.")) }
        objectiveCardCounts[missionId]?.let { add(MissionComponent("Objective cards", it.toString(), "Prepare the Objective cards and recover Objective Set #${missionId.removePrefix("M")} for this mission.")) }
        notesCounts[missionId]?.let {
            val label = if (missionId == "M7") "Abomination Notes" else "Notes"
            val numbers = noteNumbers[missionId].orEmpty().joinToString(", ") { noteNumber -> "#$noteNumber" }
            add(MissionComponent(label, it.toString(), "Required notes: $numbers."))
        }
        lightBeamCounts[missionId]?.let { add(MissionComponent("Light Beams", it.toString(), "Prepare the Light Beam tokens for this mission.")) }
    } else buildList {
        add(MissionComponent(
            "Red Spawn Zone tokens",
            when (missionId) {
                "M0" -> "1"
                "M1" -> "2"
                "M16" -> "3"
                in listOf("M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9", "M10", "M11", "M12", "M13", "M14", "M15", "M17", "M18", "M19", "M20", "M21", "M22", "M23", "M24", "M25") -> "2"
                else -> "As shown"
            },
            "Place on the normal Red Spawn Zone symbols shown on the mission map."
        ))
        if (missionId in listOf("M0", "M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9", "M10", "M11", "M12", "M13", "M14", "M15", "M16", "M17", "M18", "M19", "M20", "M21", "M22", "M23", "M24", "M25")) {
            add(MissionComponent("Starting Spawn Zone token", "1", "Place on the mission map's Starting Spawn Zone."))
        }
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

    fun requiredTileAsset(tile: String): String = "tiles/t${tile.lowercase()}.png"

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tiles & layout", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(when (sourceId) {
            "digital" -> "MISSION $missionId" + (digitalMissionInfo.firstOrNull { it.id == missionId }?.let { " · PDF page ${it.pdfPage}" } ?: "")
            "washington" -> "MISSION $missionId"
            "fort" -> "MISSION $missionId"
            else -> "MISSION ${mission.id}"
        }, style = MaterialTheme.typography.labelLarge)
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
                        tiles.chunked(3).forEachIndexed { rowIndex, tileRow ->
                            if (rowIndex > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                tileRow.forEach { tile ->
                                    val itemId = "tile-${tile.lowercase()}"
                                    val tileImage = requiredTileAsset(tile)
                                    val tileChecked = itemId in preparedItems
                                    Column(
                                        modifier = Modifier.weight(1f)
                                            .clickable { onTogglePrepared(itemId) }
                                            .padding(vertical = 4.dp, horizontal = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (true) {
                                            Box(modifier = Modifier.width(64.dp).height(64.dp), contentAlignment = Alignment.Center) {
                                                AssetImage(
                                                    path = tileImage,
                                                    contentDescription = "Tile $tile",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Fit
                                                )
                                                if (tileChecked) {
                                                    Text(
                                                        "✓",
                                                        color = Color(0xFF16803C),
                                                        style = MaterialTheme.typography.headlineMedium,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        modifier = Modifier.align(Alignment.TopEnd)
                                                    )
                                                }
                                            }
                                        } else {
                                            // Temporary fallback until each tile illustration is added to drawable-nodpi.
                                            Text(if (tileChecked) "✓ $tile" else tile, color = if (tileChecked) Color(0xFF16803C) else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                repeat(3 - tileRow.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Tokens & components required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap each token or component image as you prepare it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Spacer(Modifier.height(8.dp))
                        checklistComponents.forEachIndexed { index, component ->
                            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
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
                        ChecklistRow("Brute", "1 miniature", "1", "enemy-brute" in preparedItems, { onTogglePrepared("enemy-brute") }, drawableName = "brute")
                        HorizontalDivider(Modifier.padding(vertical = 2.dp))
                        ChecklistRow("Runner", "1 miniature", "1", "enemy-runner" in preparedItems, { onTogglePrepared("enemy-runner") }, drawableName = "runner")
                        HorizontalDivider(Modifier.padding(vertical = 2.dp))
                        ChecklistRow("Walkers", "2 miniatures", "2", "enemy-walkers" in preparedItems, { onTogglePrepared("enemy-walkers") }, drawableName = "walker")
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
private fun MapLayoutScreen(tiles: List<String>, missionId: String, sourceId: String, onBack: () -> Unit, onContinue: () -> Unit) {
    val tileCount = tiles.size
    val columns = when {
        sourceId == "digital" && missionId in listOf("WM1", "WM2", "WM3", "WM4", "WM5") -> 2
        sourceId == "fort" && missionId in listOf("M2", "M3", "M6", "M9", "M10") -> 2
        sourceId == "fort" -> 3
        sourceId == "washington" && missionId in listOf("M2", "M3", "M6", "M10") -> 2
        sourceId == "washington" && missionId in listOf("M5", "M7") -> 2
        sourceId == "washington" && missionId == "M4" -> 3
        sourceId == "washington" && missionId == "M1" -> 3
        sourceId == "washington" && missionId in listOf("M8", "M9") -> 3
        missionId in listOf("M4", "M6", "M8", "M12", "M13", "M14", "M18", "M19", "M20", "M21", "M25") -> 2 // These missions use vertical 3-row by 2-column layouts.
        missionId in listOf("M7", "M9", "M10", "M11", "M16") -> 2 // These missions use 2-column layouts.
        else -> when (tileCount) {
            2 -> 2
            3 -> 3
            4 -> 3
            6 -> 3
            8 -> 3
            9 -> 3
            else -> if (tileCount <= 3) tileCount.coerceAtLeast(1) else 3
        }
    }
    val rows = tiles.chunked(columns)
    var enlargedTile by remember { mutableStateOf<String?>(null) }
    var showMissionMap by remember { mutableStateOf(false) }

    fun tileRotationDegrees(tile: String): Float {
        if (sourceId == "washington") {
            val washingtonRotations = mapOf(
                "M1" to listOf("R", "RR", "R", "R", "N", "L"),
                "M2" to listOf("R", "RR", "N", "R", "L", "LL"),
                "M3" to listOf("L", "R", "L", "R", "R", "RR"),
                "M4" to listOf("N", "L", "L", "L", "R", "R"),
                "M5" to listOf("N", "L", "L", "L", "L", "L"),
                "M6" to listOf("L", "L", "L", "L", "L", "L"),
                "M7" to listOf("L", "L", "L", "L", "N", "R"),
                "M8" to listOf("LL", "L", "N", "R", "L"),
                "M9" to listOf("N", "N", "N", "N", "N", "N"),
                "M10" to listOf("L", "N", "R", "L", "N", "RR")
            )
            val tileIndex = washingtonMissionInfo[missionId]?.tiles?.indexOfFirst { it.equals(tile, ignoreCase = true) } ?: -1
            val direction = washingtonRotations[missionId]?.getOrNull(tileIndex) ?: "N"
            return when (direction) {
                "R" -> 90f
                "RR", "LL" -> 180f
                "L" -> 270f
                else -> 0f
            }
        }
        if (sourceId == "fort") {
            val fortRotations = mapOf(
                "M1" to listOf("N", "LL", "N", "L", "N", "L"),
                "M2" to listOf("L", "LL", "N", "R", "R", "L"),
                "M3" to listOf("N", "LL", "R", "L", "RR", "LL"),
                "M4" to listOf("LL", "L", "L", "N"),
                "M5" to listOf("LL", "LL", "N", "LL", "L", "L"),
                "M6" to listOf("LL", "N", "L", "LL", "L", "R"),
                "M7" to listOf("LL", "LL", "N", "R", "R", "N"),
                "M8" to listOf("RR", "R", "N", "N", "RR", "N", "N", "L", "L"),
                "M9" to listOf("LL", "N", "N", "R", "R", "N"),
                "M10" to listOf("R", "RR", "N", "RR", "N", "RR", "R", "N")
            )
            val tileIndex = fortMissionInfo[missionId]?.tiles?.indexOfFirst { it.equals(tile, ignoreCase = true) } ?: -1
            val direction = fortRotations[missionId]?.getOrNull(tileIndex) ?: "N"
            return when (direction) {
                "R" -> 90f
                "RR", "LL" -> 180f
                "L" -> 270f
                else -> 0f
            }
        }
        if (sourceId == "digital") {
            val digitalDirections = mapOf(
                "WM1" to listOf("M", "L", "L", "RR"),
                "WM2" to listOf("L", "R", "L", "L", "N", "R"),
                "WM3" to listOf("L", "N", "RR", "N"),
                "WM4" to listOf("N", "N", "RR", "L", "N", "L"),
                "WM5" to listOf("RR", "RR", "N", "RR")
            )
            val missionTiles = digitalMissionInfo.firstOrNull { it.id == missionId }?.tiles.orEmpty()
            val occurrenceIndex = missionTiles.indices.firstOrNull { index ->
                missionTiles[index].equals(tile, ignoreCase = true) &&
                    missionTiles.take(index).count { it.equals(tile, ignoreCase = true) } == 0
            } ?: -1
            val direction = digitalDirections[missionId]?.getOrNull(occurrenceIndex) ?: "N"
            return when (direction) {
                "R" -> 90f
                "RR" -> 180f
                "L" -> 270f
                "M" -> 0f
                else -> 0f
            }
        }
        // Mission-specific rotations are entered explicitly from the user's instructions.
        return when (missionId to tile.uppercase()) {
            "M0" to "1V" -> 90f
            "M0" to "3V" -> 180f
            "M1" to "1V" -> 90f
            "M1" to "7R" -> 90f
            "M1" to "5R" -> 90f
            "M1" to "6R" -> 90f
            "M1" to "8V" -> 270f
            "M1" to "4V" -> 180f
            // M2: Y-Zone
            "M2" to "7V" -> 180f
            "M2" to "5V" -> 270f
            "M2" to "2V" -> 90f
            "M2" to "9R" -> 180f
            "M2" to "6R" -> 90f
            "M2" to "1V" -> 270f
            "M2" to "8V" -> 270f
            "M2" to "3V" -> 270f
            // M5: Big W
            "M5" to "8V" -> 90f
            "M5" to "1V" -> 90f
            "M5" to "7R" -> 180f
            "M5" to "5R" -> 90f
            "M5" to "2V" -> 270f
            "M5" to "3V" -> 270f
            "M5" to "6R" -> 180f
            // M6: The Escape
            "M6" to "5R" -> 90f
            "M6" to "9R" -> 180f
            "M6" to "4V" -> 90f
            // M3: The 24hrs Race of Zombicity
            "M3" to "2V" -> 180f
            "M3" to "8V" -> 90f
            "M3" to "9V" -> 180f
            "M3" to "7R" -> 270f
            "M3" to "3R" -> 180f
            "M3" to "6R" -> 270f
            "M3" to "1V" -> 270f
            "M3" to "5V" -> 90f
            "M3" to "4V" -> 180f
            // M4: Drive-by Shooting
            "M4" to "5R" -> 90f
            "M4" to "7R" -> 270f
            "M4" to "4V" -> 180f
            "M4" to "3V" -> 180f
            "M4" to "6V" -> 180f
            "M4" to "8V" -> 180f
            // M7: Grindhouse
            "M7" to "6V" -> 270f
            "M7" to "4V" -> 270f
            "M7" to "3V" -> 180f
            "M7" to "8V" -> 270f
            // M8: Zombie Police
            "M8" to "3V" -> 0f
            "M8" to "4V" -> 0f
            "M8" to "6R" -> 270f
            "M8" to "9R" -> 0f
            "M8" to "8V" -> 270f
            "M8" to "5V" -> 90f
            // M9: Might Makes Right
            "M9" to "4V" -> 270f
            "M9" to "2V" -> 180f
            "M9" to "9R" -> 270f
            "M9" to "3V" -> 180f
            // M10: Small Town
            "M10" to "2V" -> 90f
            "M10" to "1R" -> 270f
            "M10" to "7R" -> 90f
            "M10" to "5R" -> 180f
            // M12: Car Crash
            "M12" to "9V" -> 180f
            "M12" to "1R" -> 90f
            "M12" to "8R" -> 90f
            "M12" to "6R" -> 90f
            "M12" to "2R" -> 90f
            // M13: Burning Streets
            "M13" to "7V" -> 90f
            "M13" to "1R" -> 180f
            "M13" to "6R" -> 270f
            "M13" to "3V" -> 180f
            // M14: Breakfast at Jesse's
            "M14" to "9V" -> 180f
            "M14" to "6R" -> 270f
            "M14" to "2V" -> 270f
            "M14" to "8R" -> 270f
            "M14" to "7V" -> 180f
            // M15: United We Stand
            "M15" to "1R" -> 270f
            "M15" to "2R" -> 90f
            "M15" to "8V" -> 180f
            "M15" to "4R" -> 90f
            "M15" to "9R" -> 180f
            "M15" to "3V" -> 90f
            "M15" to "7R" -> 180f
            "M15" to "6R" -> 90f
            // M16: Pale Shelter
            "M16" to "4V" -> 90f
            "M16" to "3V" -> 90f
            "M16" to "1V" -> 90f
            // M17: The Blight
            "M17" to "7V" -> 180f
            "M17" to "5V" -> 270f
            "M17" to "4V" -> 270f
            "M17" to "9R" -> 90f
            "M17" to "6R" -> 90f
            "M17" to "8R" -> 180f
            "M17" to "1R" -> 270f
            "M17" to "3R" -> 180f
            // M18: The End of the Road
            "M18" to "4R" -> 270f
            "M18" to "1R" -> 90f
            "M18" to "2R" -> 180f
            "M18" to "6V" -> 90f
            "M18" to "3V" -> 270f
            // M19: Best Friends Forever
            "M19" to "6V" -> 180f
            "M19" to "9V" -> 270f
            "M19" to "7R" -> 180f
            "M19" to "2R" -> 270f
            "M19" to "4R" -> 180f
            // M20: The Zombiefest
            "M20" to "1R" -> 180f
            "M20" to "8R" -> 90f
            "M20" to "7R" -> 270f
            "M20" to "6V" -> 90f
            "M20" to "5V" -> 90f
            // M21: Heavy-duty
            "M21" to "8R" -> 90f
            "M21" to "3V" -> 90f
            "M21" to "9R" -> 90f
            "M21" to "7R" -> 270f
            "M21" to "1V" -> 270f
            // M22: Crank It Up to 11
            "M22" to "5V" -> 180f
            "M22" to "4R" -> 90f
            "M22" to "9V" -> 270f
            "M22" to "3R" -> 270f
            "M22" to "1R" -> 180f
            // M23: Mercy Street
            "M23" to "9V" -> 180f
            "M23" to "2R" -> 90f
            "M23" to "3R" -> 180f
            // M24: Ram Speed
            "M24" to "8R" -> 270f
            "M24" to "7R" -> 270f
            "M24" to "1R" -> 270f
            "M24" to "5R" -> 270f
            // M25: Ned's Moloturbo
            "M25" to "2R" -> 90f
            "M25" to "7R" -> 90f
            "M25" to "9R" -> 90f
            "M25" to "8R" -> 270f
            // M11: The Ditch
            "M11" to "4R" -> 270f
            "M11" to "2R" -> 270f
            "M11" to "9V" -> 90f
            "M11" to "7V" -> 270f
            else -> 0f
        }
    }

    fun rotationLabel(degrees: Float): String = when (degrees.toInt()) {
        90 -> "↻ 90° RIGHT"
        180 -> "↻ 180° RIGHT"
        270 -> "↺ 90° LEFT"
        else -> "↑ UP · 0°"
    }

    fun tileAsset(tile: String): String {
        val normalized = tile.lowercase()
        val number = normalized.filter { it.isDigit() }
        val face = normalized.firstOrNull { it == 'r' || it == 'v' }?.toString() ?: "r"
        return "tiles/tile_${number}${face}_up.png"
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Mission map setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "${missionId} · ${when (sourceId) { "washington" -> washingtonMissionInfo[missionId]?.name ?: "Washington Z.C."; "fort" -> fortMissionInfo[missionId]?.name ?: "Fort Hendrix"; "digital" -> digitalMissionInfo.firstOrNull { it.id == missionId }?.name ?: missionId; else -> coreMissionInfo[missionId]?.name ?: "Zombicide Life" }}  ⓘ",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable { showMissionMap = true }
        )
        Spacer(Modifier.height(8.dp))
        if (enlargedTile == null) {
            Text("Tile placement template", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Tap any tile to enlarge it. Tile artwork is shown upright; mission rotations are applied to the map layout.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))

        if (enlargedTile != null) {
            val tile = enlargedTile!!
            val drawable = tileAsset(tile)
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (true) {
                    AssetImage(
                        path = drawable,
                        contentDescription = "Tile $tile, upright",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            .graphicsLayer { rotationZ = tileRotationDegrees(tile) }
                            .clickable { enlargedTile = null }
                    )
                    Text(
                        "${rotationLabel(tileRotationDegrees(tile))} · $tile",
                        modifier = Modifier.align(Alignment.TopCenter).padding(6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            .border(1.dp, MaterialTheme.colorScheme.error)
                            .clickable { enlargedTile = null },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(tile, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("↑ UP", style = MaterialTheme.typography.titleMedium)
                        Text("IMAGE NOT FOUND", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (sourceId == "washington" && missionId in listOf("M5", "M7")) {
                    val positions = if (missionId == "M5") mapOf(0 to "13V", 2 to "12R", 4 to "11R", 6 to "10R", 5 to "15R", 7 to "14R")
                        else mapOf(0 to "15V", 2 to "11R", 4 to "10R", 6 to "13V", 5 to "14V", 7 to "12R")
                    repeat(4) { rowIndex ->
                        Row(Modifier.fillMaxWidth(if (missionId in listOf("M5", "M7")) 0.92f else 1f).align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            repeat(2) { colIndex ->
                                val tile = positions[rowIndex * 2 + colIndex]
                                if (tile == null) Spacer(Modifier.weight(1f).aspectRatio(1f)) else {
                                    Card(modifier = Modifier.weight(1f).aspectRatio(1f).border(0.5.dp, MaterialTheme.colorScheme.outline).clickable { enlargedTile = tile }, shape = RectangleShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            AssetImage(path = tileAsset(tile), contentDescription = "Tile $tile", modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = tileRotationDegrees(tile) })
                                            Text(rotationLabel(tileRotationDegrees(tile)), modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (sourceId == "washington" && missionId == "M8") {
                    // Upper group: horizontal 1x2, with tile 12V in the right-hand position.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                        val tile = "12V"
                        Card(modifier = Modifier.weight(1f).aspectRatio(1f).border(0.5.dp, MaterialTheme.colorScheme.outline).clickable { enlargedTile = tile }, shape = RectangleShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                AssetImage(path = tileAsset(tile), contentDescription = "Tile $tile", modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = tileRotationDegrees(tile) })
                                Text(rotationLabel(tileRotationDegrees(tile)), modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Lower group: clean 2x2 formation, separated slightly from the upper group.
                    listOf(listOf("11R", "13V"), listOf("14V", "15V")).forEach { tileRow ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            tileRow.forEach { tile ->
                                Card(modifier = Modifier.weight(1f).aspectRatio(1f).border(0.5.dp, MaterialTheme.colorScheme.outline).clickable { enlargedTile = tile }, shape = RectangleShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        AssetImage(path = tileAsset(tile), contentDescription = "Tile $tile", modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = tileRotationDegrees(tile) })
                                        Text(rotationLabel(tileRotationDegrees(tile)), modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                } else if (sourceId == "washington" && missionId == "M4") {
                    // Upper group: tiles 10V and 11V stacked vertically, centered on the seam below.
                    listOf("10V", "11V").forEach { tile ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Card(modifier = Modifier.fillMaxWidth(0.5f).aspectRatio(1f).border(0.5.dp, MaterialTheme.colorScheme.outline).clickable { enlargedTile = tile }, shape = RectangleShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    AssetImage(path = tileAsset(tile), contentDescription = "Tile $tile", modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = tileRotationDegrees(tile) })
                                    Text(rotationLabel(tileRotationDegrees(tile)), modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }
                        }
                    }
                    // Lower group: the remaining four tiles in a 2x2 formation.
                    listOf(listOf("9R", "1R"), listOf("6R", "3R")).forEach { tileRow ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            tileRow.forEach { tile ->
                                Card(modifier = Modifier.weight(1f).aspectRatio(1f).border(0.5.dp, MaterialTheme.colorScheme.outline).clickable { enlargedTile = tile }, shape = RectangleShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        AssetImage(path = tileAsset(tile), contentDescription = "Tile $tile", modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = tileRotationDegrees(tile) })
                                        Text(rotationLabel(tileRotationDegrees(tile)), modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                } else rows.forEach { rowTiles ->
                    Row(
                        Modifier.fillMaxWidth(if (sourceId == "fort" && missionId == "M10") 0.52f else 1f)
                            .align(Alignment.CenterHorizontally),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        rowTiles.forEach { tile ->
                            val drawable = tileAsset(tile)
                            Card(
                                modifier = Modifier.weight(1f).aspectRatio(1f)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outline)
                                    .clickable { enlargedTile = tile },
                                shape = RectangleShape,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (true) {
                                        AssetImage(
                                            path = drawable,
                                            contentDescription = "Tile $tile, upright",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                                .graphicsLayer { rotationZ = tileRotationDegrees(tile) }
                                        )
                                        Text(
                                            rotationLabel(tileRotationDegrees(tile)),
                                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(tile, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("↑ UP", style = MaterialTheme.typography.labelSmall)
                                            Text("IMAGE MISSING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
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

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = if (enlargedTile != null) ({ enlargedTile = null }) else onBack, modifier = Modifier.weight(1f)) {
                Text(if (enlargedTile != null) "Back to map" else "Back")
            }
            if (enlargedTile == null) {
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White)
                ) { Text("BEGIN!", fontWeight = FontWeight.Bold) }
            }
        }
    }
    if (showMissionMap) {
        Dialog(onDismissRequest = { showMissionMap = false }) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${missionId} · ${when (sourceId) { "washington" -> washingtonMissionInfo[missionId]?.name ?: "Washington Z.C."; "fort" -> fortMissionInfo[missionId]?.name ?: "Fort Hendrix"; "digital" -> digitalMissionInfo.firstOrNull { it.id == missionId }?.name ?: missionId; else -> coreMissionInfo[missionId]?.name ?: "Zombicide Life" }}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    AssetImage(
                        path = "maps/map_${missionId.lowercase()}${when (sourceId) { "washington" -> "z"; "fort" , "fort_hendrix" -> "f"; else -> "" }}.png",
                        contentDescription = "General map for $missionId showing token locations",
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).height(420.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showMissionMap = false }) { Text("Close map") }
                }
            }
        }
    }
}

@Composable
private fun PlayerSelectionScreen(sourceId: String, onBack: () -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Survivor Selection", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
            DrawableImage(
                name = when (sourceId) { "washington" -> "survivor3.png"; "fort" -> "survivor4.png"; else -> "survivor1.png" },
                contentDescription = "Survivor illustration 1",
                modifier = Modifier.width(150.dp).height(180.dp).graphicsLayer { rotationZ = -30f; translationX = -100f }
            )
            DrawableImage(
                name = "survivor2.png",
                contentDescription = "Survivor illustration 2",
                modifier = Modifier.width(150.dp).height(180.dp).graphicsLayer { rotationZ = 30f; translationX = 100f }
            )
        }
        Text("Choose Survivors", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Choose the Survivors and distribute them among players as desired. All players form one team.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text("Health and Skills", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Classic Survivors have 3 Health. Kid Survivors have 2 Health and can use the Slippery Skill.", style = MaterialTheme.typography.bodyMedium)
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
private fun PlayerDashboardScreen(sourceId: String, onBack: () -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Player Dashboard Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Each player should prepare their own dashboard and Survivor miniature.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        DrawableImage("dashboard.png", "Player dashboard reference", Modifier.fillMaxWidth().height(125.dp), ContentScale.Fit)
        Spacer(Modifier.height(12.dp))
        Text("1. Choose a base-ring color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Each player takes one of the available colors for their Survivor base rings. Place the chosen ring under the Survivor miniature.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Text("2. Set the starting trackers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("• Life tracker: set it to the maximum value shown on the dashboard.", style = MaterialTheme.typography.bodyMedium)
        Text("• Skill tracker: set it to the blue level.", style = MaterialTheme.typography.bodyMedium)
        Text("• Danger Zone tracker: set it to 0, at the far-left position.", style = MaterialTheme.typography.bodyMedium)
        Text("• Reserve: place all three reserve markers in the top-right area of the dashboard.", style = MaterialTheme.typography.bodyMedium)
        if (sourceId == "washington" || sourceId == "fort") {
            Spacer(Modifier.height(8.dp))
            Text("3. Prepare the campaign components", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Prepare the Campaign Sheet. The Clock will be used as the First Player marker for this campaign.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DrawableImage("campaign_sheet.png", "Campaign sheet", Modifier.fillMaxWidth().height(105.dp), ContentScale.Fit)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(onClick = onContinue, modifier = Modifier.weight(1f)) { Text("First Player") }
        }
    }
}

@Composable
private fun FirstPlayerScreen(sourceId: String, onBack: () -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("First Player", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Choose the player holding the Fire Axe token. That player takes the first turn.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (sourceId == "washington" || sourceId == "fort") {
                DrawableImage(
                    "clock.png",
                    "Clock used as the First Player marker",
                    Modifier.fillMaxWidth(0.6f).aspectRatio(1f),
                    ContentScale.Fit
                )
            } else {
                AssetImage(
                    path = "tokens/first_player_token.png",
                    contentDescription = "First Player Token",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(0.6f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        }
        if (sourceId == "fort") {
            Spacer(Modifier.height(12.dp))
            Text("Players have to agree who is Penny's leader.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DrawableImage("penny.png", "Penny companion miniature", Modifier.width(104.dp).height(132.dp), ContentScale.Fit)
                Text("Penny is a companion. If she dies, the Campaign is lost.", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(onClick = onContinue) { Text("Continue to Map") }
        }
    }
}

@Composable
private fun GameplayScreen(
    missionId: String,
    sourceId: String,
    isComplete: Boolean,
    onToggleComplete: () -> Unit,
    onRuleSet: () -> Unit,
    onExpansionRules: () -> Unit,
    onHome: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Mission in Progress", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "${missionId} · ${when (sourceId) { "washington" -> washingtonMissionInfo[missionId]?.name ?: "Washington Z.C."; "fort" -> fortMissionInfo[missionId]?.name ?: "Fort Hendrix"; "digital" -> digitalMissionInfo.firstOrNull { it.id == missionId }?.name ?: missionId; else -> coreMissionInfo[missionId]?.name ?: "Zombicide Life" }}",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onToggleComplete,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isComplete) Color(0xFF2E7D32) else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (isComplete) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text(if (isComplete) "✓ Mission complete!" else "Mark mission complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onRuleSet,
            modifier = Modifier.fillMaxWidth().height(68.dp)
        ) {
            Text("Rules Reference", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (sourceId == "washington" || sourceId == "fort") {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onExpansionRules,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Special expansion rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onHome) { Text("Return to Main Page") }
    }
}

@Composable
private fun ExpansionRulesScreen(sourceId: String, onBack: () -> Unit) {
    val isFort = sourceId == "fort"
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Special expansion rules", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(if (isFort) "Fort Hendrix" else "Washington Z.C.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            if (isFort) {
                item {
                    ExpansionRuleCard("Military equipment rule — Fort Hendrix", "When performing a Search in any room of a Military tile, draw from the Military Equipment cards.")
                }
            }
            item {
                ExpansionRuleCard("Advanced equipment rules", "Replace the original equipment cards with the dedicated advanced equipment cards. These include All-out dice and Keeping the Equipment rules.")
            }
            item {
                ExpansionRuleCard("KO / R.I.P. rules", "A Survivor can be KO for one turn and helped by another Survivor. If they are not helped in time, they die and cannot be used again in the campaign. The campaign itself is not lost.")
            }
            item {
                ExpansionRuleCard("Clock & Night rules", "The Clock represents the First Player. Apply the campaign's Night rules and light-source rules.")
            }
            if (isFort) {
                item {
                    ExpansionRuleCard("Shooter Walker — Fort Hendrix", "Replace standard Walker cards #001–#040 with Shooter Walker cards #041–#080.")
                }
            } else {
                item {
                    ExpansionRuleCard("Secret Service rule — Washington Z.C.", "When performing a Search in any Secret Service room or in the Humvee, draw from the Secret Service Equipment cards.")
                }
                item {
                    ExpansionRuleCard("Humvee rule — Washington Z.C.", "The Humvee allows Survivors to search for Secret Service Equipment. It cannot be driven.")
                }
                item {
                    ExpansionRuleCard("Corridors rule — Washington Z.C.", "Corridors act like roads: they provide continuous line of sight and cannot be searched.")
                }
                item {
                    ExpansionRuleCard("Sandbags rule — Washington Z.C.", "Sandbags do not interrupt line of sight and always allow Survivors to pass through without penalty. Zombies cannot move across them normally; they can cross only when they receive an Extra Activation.")
                }
                item {
                    ExpansionRuleCard("Tank rule — Washington Z.C.", "The Tank blocks line of sight to anyone who is not on it. Survivors can Move over it by spending 1 additional Action. Zombies cannot Move over it unless they receive an Extra Activation.")
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text("Back to Mission") }
    }
}

@Composable
private fun ExpansionRuleCard(title: String, description: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
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
    if (setId == "washington") return washingtonMissionInfo[missionId]?.tiles ?: emptyList()
    if (setId == "fort") return fortMissionInfo[missionId]?.tiles ?: emptyList()
    if (setId == "digital") return digitalMissionInfo.firstOrNull { it.id == missionId }?.tiles ?: emptyList()
    // These mission tile orders are explicitly defined in the app source from the user's layout instructions.
    if (setId == "core" && missionId in listOf("M12", "M13", "M14", "M15")) {
        return coreMissionInfo[missionId]?.tiles ?: listOf("1V", "3V")
    }
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
    }.getOrElse { emptyList() }.let { found ->
        if (found.isNotEmpty()) found
        else if (setId == "fort") emptyList()
        else coreMissionInfo[missionId]?.tiles ?: listOf("1V", "3V")
    }
}

@Composable
private fun ChecklistRow(
    label: String,
    detail: String,
    quantity: String? = null,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    prominent: Boolean = false,
    drawableName: String? = null
) {
    val tokenAsset = remember(label) {
        val key = label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val aliases = when {
            label.contains("Starting Spawn Zone", ignoreCase = true) -> listOf("spawn_1st")
            label.contains("Spawn Zone", ignoreCase = true) -> when {
                label.contains("Blue", true) -> listOf("spawn_blue")
                label.contains("Green", true) -> listOf("spawn_green")
                label.contains("Red", true) -> listOf("spawn_red")
                else -> listOf("spawn_red", "spawn_blue", "spawn_green")
            }
            label.contains("Objective card", ignoreCase = true) -> listOf("objective_card")
            label.contains("Objective", ignoreCase = true) -> when {
                label.contains("Blue", true) -> listOf("objective_blue")
                label.contains("Green", true) -> listOf("objective_green")
                else -> listOf("objective_red")
            }
            label.contains("Epic Weapon Crate", ignoreCase = true) -> listOf("pimp_crate")
            label.contains("Exit Zone", ignoreCase = true) -> listOf("exit")
            label.contains("Open Door", ignoreCase = true) -> listOf("door_red_open", "door_blue_open", "door_green_open")
            label.contains("Red Door", ignoreCase = true) -> listOf("door_red")
            label.contains("Blue Door", ignoreCase = true) -> listOf("door_blue")
            label.contains("Green Door", ignoreCase = true) -> listOf("door_green")
            label.contains("Police Car", ignoreCase = true) -> listOf("police_car")
            label.contains("Muscle Car", ignoreCase = true) -> listOf("muscle_car")
            label.contains("Broken Door", ignoreCase = true) -> listOf("door_red", "door_blue", "door_green")
            label.contains("Noise", ignoreCase = true) -> listOf("noise")
            label.contains("Crashed Car", ignoreCase = true) -> listOf("muscle_car", "police_car")
            label.contains("Humvee", ignoreCase = true) -> listOf("humvee_car")
            label.contains("Notes", ignoreCase = true) -> listOf("notes")
            label.contains("Light Beam", ignoreCase = true) -> listOf("light_beam")
            label.contains("First Player", ignoreCase = true) -> listOf("spawn_1st")
            label.contains("Player Start", ignoreCase = true) -> listOf("player_start")
            label.contains("Survivor Starting Zone", ignoreCase = true) -> listOf("player_start")
            else -> listOf("${key}_token", key)
        }
        // Alias names already include the color suffix (e.g. spawn_red, objective_red).
        // Do not prepend the color from the label, which creates invalid names like red_spawn_red.
        aliases.firstOrNull()?.let { "tokens/$it.png" } ?: "tokens/$key.png"
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onCheckedChange).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (true) {
            Box(modifier = Modifier.width(60.dp).height(60.dp).padding(end = 4.dp), contentAlignment = Alignment.Center) {
                if (drawableName != null) {
                    DrawableImage(
                        name = drawableName,
                        contentDescription = "$label miniature",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AssetImage(
                        path = tokenAsset,
                        contentDescription = "$label token",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (checked) {
                    Text(
                        "✓",
                        color = Color(0xFF16803C),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
        } else {
            // Keep a checkbox for checklist items that do not have a token illustration.
            Checkbox(checked = checked, onCheckedChange = null)
        }
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
            label = { Text("Search rules") },
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
