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
import androidx.compose.ui.text.font.FontStyle
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
    RuleSourceOption("rio", "Rio", "WIP · mission content is being prepared", true),
    RuleSourceOption("pariz", "Pariz", "WIP · mission content is being prepared", true),
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
        if (selectedSource in setOf("core", "washington", "fort", "rio", "pariz", "digital")) {
            val fortMissions = (1..10).mapNotNull { number -> fortMissionInfo["M$number"]?.let { Triple("M$number", it.name, "${it.difficulty} · ${it.duration}") } }
            val digitalMissions = digitalMissionInfo.map { Triple(it.id, it.name, "${it.difficulty} · ${it.duration}") }
            val missionsToShow = when (selectedSource) {
                "washington" -> washingtonMissions
                "fort" -> fortMissions
                "rio", "pariz" -> emptyList()
                "digital" -> digitalMissions
                else -> coreMissions
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedSource == "rio" || selectedSource == "pariz") {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Mission library — WIP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (selectedSource == "rio") "Rio mission content is still being prepared. Missions will appear here when the source material has been added."
                                    else "Pariz mission content is still being prepared. Missions will appear here when the source material has been added.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
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
                        "washington" -> washington?.briefing?.takeIf { it.isNotBlank() } ?: "Briefing not yet transcribed from the source mission page."
                        "fort" -> fort?.briefing?.takeIf { it.isNotBlank() } ?: "Star-Spangled Banner campaign · Starting hour: ${fort?.startingHour ?: "see manual"}."
                        "digital" -> digital?.briefing ?: "Mission details are not available in this batch."
                        else -> mission.briefing
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic
                )
            }
            item {
                HorizontalDivider()
                Text("Objectives", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                (when (sourceId) {
                    "washington" -> washington?.objectives ?: emptyList()
                    "fort" -> fort?.objectives ?: emptyList()
                    "digital" -> digital?.objectives ?: emptyList()
                    else -> mission.objectives
                }).forEach { BulletText(it) }
            }
            item {
                HorizontalDivider()
                Text("Special rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                (when (sourceId) {
                    "washington" -> washington?.specialRules ?: emptyList()
                    "fort" -> fort?.specialRules ?: emptyList()
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
