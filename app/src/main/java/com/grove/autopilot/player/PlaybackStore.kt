package com.grove.autopilot.player

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.grove.autopilot.model.Energy
import com.grove.autopilot.model.PlayUi
import com.grove.autopilot.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

sealed interface Command {
    data class Play(val track: Track) : Command
    data object Toggle : Command
    data object Next : Command
    data object Previous : Command
    data class EnergyChange(val energy: Energy) : Command
    data object Ban : Command
    data class Seek(val positionMs: Long) : Command
    data object Stop : Command
}

object PlaybackStore {
    val ui = MutableStateFlow(PlayUi())
    val commands = ConcurrentLinkedQueue<Command>()

    @Volatile
    var phoneLibrary: List<Track> = emptyList()

    @Volatile
    var generation: Int = 0

    @Volatile
    var preferredEnergy: Energy = Energy.Steady

    @Volatile
    var crossfade: Boolean = true

    fun bump(): Int = synchronized(this) { ++generation }

    fun send(context: Context, command: Command) {
        commands.add(command)
        val intent = Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_COMMAND)
        ContextCompat.startForegroundService(context, intent)
    }
}
