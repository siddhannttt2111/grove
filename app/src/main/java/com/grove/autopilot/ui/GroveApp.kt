package com.grove.autopilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.grove.autopilot.GroveViewModel
import com.grove.autopilot.player.PlaybackStore
import com.grove.autopilot.theme.GroveColor

@Composable
fun GroveApp(model: GroveViewModel, modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    val play by PlaybackStore.ui.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val showTabs = route.startsWith("home") || route.startsWith("library") || route.startsWith("search") || route.startsWith("settings")
    val showMini = showTabs && play.title.isNotBlank()
    val bottomPad = if (!showTabs) 0.dp else if (showMini) 156.dp else 84.dp

    Box(modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = "splash",
            modifier = Modifier.padding(bottom = bottomPad),
        ) {
            composable("splash") {
                SplashScreen {
                    nav.navigate("home") { popUpTo("splash") { inclusive = true } }
                }
            }
            composable("home") { HomeScreen(model, nav) }
            composable("library") { LibraryScreen(model, nav) }
            composable(
                "search?source={source}",
                arguments = listOf(navArgument("source") { type = NavType.StringType; defaultValue = "phone" }),
            ) { back ->
                SearchScreen(model, nav, back.arguments?.getString("source") ?: "phone")
            }
            composable("settings") { SettingsScreen(model, nav) }
            composable("player") { PlayerScreen(model, nav) }
            composable("queue") { QueueScreen(nav) }
            composable("vibe") { VibeScreen(model, nav) }
            composable("set") { SetScreen(nav) }
            composable(
                "group/{kind}/{name}",
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                ),
            ) { back ->
                GroupScreen(
                    model,
                    nav,
                    back.arguments?.getString("kind").orEmpty(),
                    android.net.Uri.decode(back.arguments?.getString("name").orEmpty()),
                )
            }
        }
        if (showTabs) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(GroveColor.Paper),
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(GroveColor.Line))
                if (showMini) {
                    MiniPlayer(play.title, play.artistLine(), play.playing, { nav.navigate("player") { launchSingleTop = true } }, model::toggle)
                }
                Row(Modifier.fillMaxWidth().height(72.dp)) {
                    Tab("home", "Home", Icons.Outlined.Home, route, nav)
                    Tab("library", "Library", Icons.Outlined.LibraryMusic, route, nav)
                    Tab("search?source=phone", "Search", Icons.Outlined.Search, route, nav)
                    Tab("settings", "Settings", Icons.Outlined.Settings, route, nav)
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(title: String, artist: String, playing: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(GroveColor.Glass)
            .border(1.dp, GroveColor.Moss.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = GroveColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text(artist, color = GroveColor.Mute, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(GroveColor.Moss)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tab(
    route: String,
    label: String,
    icon: ImageVector,
    current: String,
    nav: androidx.navigation.NavHostController,
) {
    val selected = current.startsWith(route.substringBefore("?"))
    Column(
        Modifier
            .weight(1f)
            .clickable {
                nav.navigate(route) {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) GroveColor.Moss else GroveColor.Mute)
        Text(label, color = if (selected) GroveColor.Moss else GroveColor.Mute, fontSize = 11.sp)
    }
}
