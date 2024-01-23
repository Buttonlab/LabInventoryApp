package com.example.multipagetesting2

import androidx.lifecycle.ViewModel
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander
import com.uk.tsl.rfid.asciiprotocol.commands.BatteryStatusCommand
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState

class SettingsViewModel(): ViewModel() {

    // The shared instance of the commander
    private lateinit var commander: AsciiCommander

    // Placeholder for battery command
    private val bCommand = BatteryStatusCommand.synchronousCommand()


    // Get and set for the ascii commander instance
    fun setCommander(inCommander: AsciiCommander) {
        commander = inCommander
    }
    private fun getCommander(): AsciiCommander {
        return commander
    }

    fun resetDevice() {
        if (getCommander().isConnected()) {
            val fdCommand = FactoryDefaultsCommand()
            fdCommand.resetParameters = TriState.YES
            getCommander().executeCommand(fdCommand)
        }
    }

    fun getBatteryLevel(): String {
        if (!getCommander().hasSynchronousResponder) {
            getCommander().addSynchronousResponder()
        }
        getCommander().executeCommand(bCommand)
        return bCommand.batteryLevel.toString()
    }

    fun isConnected() = commander.isConnected

    fun connectionState() = commander.connectionState.toString()

}