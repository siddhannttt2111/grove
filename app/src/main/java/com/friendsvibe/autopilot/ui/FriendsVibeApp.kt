package com.friendsvibe.autopilot.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
    Box(Modifier.fillMaxSize().background(Ink)) {
        Box(
            Modifier
                .size(220.dp)
                .offset(x = 210.dp, y = (-30).dp)
                .clip(CircleShape)
                .background(Wash),
        )
        Box(
            Modifier
                .size(120.dp)
                .offset(x = (-40).dp, y = 460.dp)
                .clip(CircleShape)
                .background(Amber),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Discover", style = MaterialTheme.typography.displaySmall, color = Paper)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Panel)
                    .border(1.dp, Amber.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("ONE SONG", color = Amber, style = MaterialTheme.typography.labelLarge)
                Text("Keep the same vibe", style = MaterialTheme.typography.headlineMedium, color = Paper)
                Text(
                    "Pick one song. The set continues from the phone library, or inside Spotify.",
                    color = Mist,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text("Start a session", style = MaterialTheme.typography.titleLarge, color = Paper)
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
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SessionChoice(title: String, body: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Ink)
            .border(1.dp, Line, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 18.dp, vertical = 16.dp)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Paper)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Mist)
    }
}
