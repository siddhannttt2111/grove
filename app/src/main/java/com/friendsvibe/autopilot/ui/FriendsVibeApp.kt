package com.friendsvibe.autopilot.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import com.friendsvibe.autopilot.local.LibraryViewModel
import com.friendsvibe.autopilot.spotify.SpotifySessionViewModel

object Routes {
    const val Home = "home"
    const val LocalPick = "local/pick"
    const val LocalSession = "local/session/{trackId}"
    const val SpotifyPick = "spotify/pick"
    const val SpotifySession = "spotify/session"

    fun localSession(trackId: Long) = "local/session/$trackId"
}

@Composable
fun FriendsVibeApp() {
    val nav = rememberNavController()
    val activity = LocalContext.current as ComponentActivity
    val libraryVm: LibraryViewModel = viewModel(activity)
    val spotifyVm: SpotifySessionViewModel = viewModel(activity)

    NavHost(navController = nav, startDestination = Routes.Home) {
        composable(Routes.Home) {
            HomeScreen(
                onPhone = { nav.navigate(Routes.LocalPick) },
                onSpotify = { nav.navigate(Routes.SpotifyPick) },
            )
        }
        composable(Routes.LocalPick) {
            LibraryPickScreen(
                libraryVm = libraryVm,
                onBack = { nav.popBackStack() },
                onPick = { id -> nav.navigate(Routes.localSession(id)) },
            )
        }
        composable(
            Routes.LocalSession,
            arguments = listOf(navArgument("trackId") { type = NavType.LongType }),
        ) { entry ->
            LocalSessionScreen(
                trackId = entry.arguments?.getLong("trackId") ?: 0L,
                libraryVm = libraryVm,
                spotifyVm = spotifyVm,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.SpotifyPick) {
            SpotifyPickScreen(
                spotifyVm = spotifyVm,
                onBack = { nav.popBackStack() },
                onPick = { nav.navigate(Routes.SpotifySession) },
            )
        }
        composable(Routes.SpotifySession) {
            SpotifySessionScreen(
                spotifyVm = spotifyVm,
                onBack = { nav.popBackStack() },
            )
        }
    }
}

@Composable
private fun HomeScreen(onPhone: () -> Unit, onSpotify: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("FRIENDS VIBE", color = Amber, style = MaterialTheme.typography.labelLarge)
            Text("Pick one song.", style = MaterialTheme.typography.displaySmall, color = Paper)
            Text("The set continues.", style = MaterialTheme.typography.displaySmall, color = Paper)
            Text(
                "Phone files can fade on this device. A Spotify song stays in the Spotify app, in its own session.",
                color = Mist,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SessionChoice(
                title = "Phone library",
                body = "Score the rest of your files, then crossfade the queue.",
                onClick = onPhone,
            )
            SessionChoice(
                title = "Spotify",
                body = "Allowlisted Premium accounts. Search and queue. No download, no mix.",
                onClick = onSpotify,
            )
        }
    }
}

@Composable
private fun SessionChoice(title: String, body: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Panel)
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 18.dp, vertical = 16.dp)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Paper)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Mist)
    }
}
