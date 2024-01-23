package com.example.multipagetesting2

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand
import com.uk.tsl.rfid.asciiprotocol.commands.FindTagCommand
import com.uk.tsl.rfid.asciiprotocol.commands.InventoryCommand
import com.uk.tsl.rfid.asciiprotocol.commands.SwitchActionCommand
import com.uk.tsl.rfid.asciiprotocol.enumerations.QuerySession
import com.uk.tsl.rfid.asciiprotocol.enumerations.QueryTarget
import com.uk.tsl.rfid.asciiprotocol.enumerations.SelectAction
import com.uk.tsl.rfid.asciiprotocol.enumerations.SelectTarget
import com.uk.tsl.rfid.asciiprotocol.enumerations.SwitchAction
import com.uk.tsl.rfid.asciiprotocol.enumerations.SwitchState
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState
import com.uk.tsl.rfid.asciiprotocol.responders.ISignalStrengthCountDelegate
import com.uk.tsl.rfid.asciiprotocol.responders.ISignalStrengthReceivedDelegate
import com.uk.tsl.rfid.asciiprotocol.responders.ISwitchStateReceivedDelegate
import com.uk.tsl.rfid.asciiprotocol.responders.SignalStrengthResponder
import com.uk.tsl.rfid.asciiprotocol.responders.SwitchResponder
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class TagFinderViewModel(): ViewModel() {

    // The Ascii Commander shared instance
    private lateinit var mCommander: AsciiCommander

    // The instances used to issue commands
    private var mInventoryCommand: InventoryCommand
    private var mFindTagCommand: FindTagCommand

    // The responder to capture incoming RSSI responses
    private var mSignalStrengthResponder: SignalStrengthResponder

    // The switch state responder
    private var mSwitchResponder: SwitchResponder

    // If the reader can support the .ft (find tag) command
    private var mUseFindTagCommand = false

    // The target tag to look for
    @Volatile
    private var mTargetTagEpc: String? = null

    // Is the user scanning right now
    private var mScanning = false

    // Control
    private var mEnabled = false

    // The variables required for running the preformTask function
    private var mbusy: Boolean
    private val executorService = Executors.newSingleThreadExecutor()
    private val mainThreadHandler = Handler(Looper.getMainLooper())
    private var mTimeTaken: Double = 0.0


    // Initializing the various functions
    init {
        mbusy = false
        mInventoryCommand = InventoryCommand.synchronousCommand()
        mFindTagCommand = FindTagCommand.synchronousCommand()
        mSignalStrengthResponder = SignalStrengthResponder()
        mSwitchResponder = SwitchResponder()

        mSwitchResponder.switchStateReceivedDelegate = ISwitchStateReceivedDelegate {switchState ->
            when (switchState) {
                SwitchState.OFF -> {
                    mScanning = false
                    // Fake a signal report for both percentage and RSSI to indicate action stopped
                    if (mSignalStrengthResponder.rawSignalStrengthReceivedDelegate != null) {
                        Log.d("TagFinderViewModel", "Trying to send empty signal strength")
                        mSignalStrengthResponder.rawSignalStrengthReceivedDelegate.signalStrengthReceived(null)
                    }
                    if (mSignalStrengthResponder.percentageSignalStrengthReceivedDelegate != null) {
                        Log.d("TagFinderViewModel", "Trying to send empty signal strength")
                        mSignalStrengthResponder.percentageSignalStrengthReceivedDelegate.signalStrengthReceived(0)
                        mSignalStrengthResponder.percentageSignalStrengthReceivedDelegate.signalStrengthReceived(null)
                    }
                }
                SwitchState.SINGLE -> {
                    mScanning =true
                }
            }
        }
    }


    // Check if the reader can use the .ft (find tag) command
    fun isFindTagCommandAvailable(): Boolean {
        return mUseFindTagCommand
    }

    // Get and Set for raw mSignalStrengthResponder delegate
    fun getRawSignalDelegate(): ISignalStrengthReceivedDelegate? {
        return mSignalStrengthResponder.rawSignalStrengthReceivedDelegate
    }
    fun setRawSignalDelegate(delegate: ISignalStrengthReceivedDelegate?) {
        Log.d("TagFinderViewModel", "setRawSignalDelegate")
        mSignalStrengthResponder.rawSignalStrengthReceivedDelegate = delegate
    }

    // Get and Set for percentage mSignalStrengthResponder delegate
    fun getPercentageSignalDelegate(): ISignalStrengthReceivedDelegate? {
        return mSignalStrengthResponder.percentageSignalStrengthReceivedDelegate
    }
    fun setPercentageSignalDelegate(delegate: ISignalStrengthReceivedDelegate?) {
        Log.d("TagFinderViewModel", "setPercentageSignalDelegate")
        mSignalStrengthResponder.percentageSignalStrengthReceivedDelegate = delegate
    }

    // Get and Set for mSignalStrengthResponder transponder count delegate
    fun getSignalStrengthCountDelegate(): ISignalStrengthCountDelegate? {
        return mSignalStrengthResponder.signalStrengthCountDelegate
    }
    fun setSignalStrengthCountDelegate(delegate: ISignalStrengthCountDelegate?) {
        mSignalStrengthResponder.signalStrengthCountDelegate = delegate
    }

    // Get and Set for if the command responders are active
    fun getEnabled(): Boolean {
        return mEnabled
    }
    fun setEnabled(state: Boolean) {
        val oldState = mEnabled
        mEnabled = state
        if (oldState != mEnabled && getCommander() != null) {
            if (mEnabled) {
                Log.d("TagFinderViewModel", "Adding responders")
                // Listen for transponders and triggers
                if (!getCommander()!!.hasSynchronousResponder) {
                    getCommander()!!.addSynchronousResponder()
                    Log.d("TagFinderViewModel", "Adding sync responder")
                } else {
                    Log.d("TagFinderViewModel", "Already has sync responder")
                }
                getCommander()!!.addResponder(mSignalStrengthResponder)
                getCommander()!!.addResponder(mSwitchResponder)
            } else {
                // Stop listening for transponders and triggers
                if (getCommander()!!.hasSynchronousResponder) {
                    getCommander()!!.removeSynchronousResponder()
                    Log.d("TagFinderViewModel", "Removing sync responder")
                } else {
                    Log.d("TagFinderViewModel", "Had no sync responder")
                }
                getCommander()!!.removeResponder(mSwitchResponder)
                getCommander()!!.removeResponder(mSignalStrengthResponder)
            }
        }
    }

    // Get and Set for target EPC
    fun getTargetTagEpc(): String? {
        return mTargetTagEpc
    }
    fun setTargetTagEpc(targetTagEpc: String?) {
        Log.d("TagFinderViewModel", "setTargetTagEpc($targetTagEpc)")
        if (targetTagEpc != null) {
            if (targetTagEpc.length != 24) {
                Log.d("TagFinderViewModel", "Converting to hex: ${tagAsciiToHex(targetTagEpc).uppercase(Locale.US)}")
                mTargetTagEpc = tagAsciiToHex(targetTagEpc).uppercase(Locale.US)
            } else {
                mTargetTagEpc = targetTagEpc.uppercase(Locale.US)
                Log.d("TagFinderViewModel", "mTargetTagEpc = ${targetTagEpc.uppercase(Locale.US)}")
            }
        }
    }

    // Get and Set for is scanning
    fun isScanning(): Boolean {
        return mScanning
    }
    fun setScanning(scanning: Boolean) {
        mScanning = scanning
    }

    // Get and set for the Ascii Commander
    private fun getCommander(): AsciiCommander? {
        return mCommander
    }
    fun setCommander(commander: AsciiCommander?) {
        if (commander != null) {
            mCommander = commander
        } else {
            Log.e("TagFinderViewModel", "The given commander was null!")
        }
    }

    // Reset the reader
    fun resetDevice() {
        Log.d("TagFinderViewModel", "resetDevice")
        if (getCommander() != null && getCommander()?.isConnected == true) {
            try {
                performTask {
                    getCommander()!!.executeCommand(FactoryDefaultsCommand())
                    mFindTagCommand.resetParameters = TriState.YES
                    mFindTagCommand.takeNoAction = TriState.YES
                    getCommander()!!.executeCommand(mFindTagCommand)

                    mUseFindTagCommand = mFindTagCommand.isSuccessful
                    updateTargetParams()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("TagFinderViewModel", "Failed to reset the device!")
            }
        }
    }

    // Calling the function to update the target the reader is searching for
    fun updateTarget() {
        Log.d("TagFinderViewModel", "updateTarget")
        if (!mbusy) {
            try {
                performTask {
                    updateTargetParams()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("TagFinderViewModel", "Failed to reset the device!")
            }
        }
    }

    // Update the target the reader is searching for
    fun updateTargetParams() {
        Log.d("TagFinderViewModel", "updateTargetParams($mTargetTagEpc)")
        if (getCommander() != null && getCommander()?.isConnected == true) {
            // Configure the switch actions
            val switchActionCommand = SwitchActionCommand.synchronousCommand() // MAY NOT WORK WITH SYNCHRONOUS
            switchActionCommand.resetParameters = TriState.YES
            switchActionCommand.asynchronousReportingEnabled = TriState.YES

            if (!mTargetTagEpc.isNullOrEmpty()) {
                // Configure the single press switch action for the appropriate command
                switchActionCommand.singlePressAction = if (mUseFindTagCommand) SwitchAction.FIND_TAG else SwitchAction.INVENTORY
                // Lower the repeat delay to maximise the response rate
                switchActionCommand.singlePressRepeatDelay = 10
            }

            getCommander()!!.executeCommand(switchActionCommand)

            var success = false

            if (mTargetTagEpc.isNullOrEmpty()) {
                Log.e("TagFinderViewModel", "mTargetTagEpc is null!")
                success = false
            } else {
                // Ensuring the given hex has a valid number of bits
                var targetEPC = mTargetTagEpc
                if (targetEPC!!.length % 2 == 1){
                    targetEPC += "0"
                }

                // Use the .ft command if it exists on the reader
                if (mUseFindTagCommand) {
                    Log.d("TagFinderViewModel", "Using the .ft command")
                    mFindTagCommand = FindTagCommand.synchronousCommand() // MAY NOT WORK WITH SYNCHRONOUS
                    mFindTagCommand.resetParameters = TriState.YES

                    if (getEnabled()) {
                        mFindTagCommand.selectData = targetEPC
                        mFindTagCommand.selectLength = mTargetTagEpc!!.length.times(4)
                        mFindTagCommand.selectOffset = 0x20
                        Log.d("TagFinderViewModel", "getEnabled is true \n${mFindTagCommand.selectData}\n${mFindTagCommand.selectLength}\n${mFindTagCommand.selectOffset}")
                    }

                    mFindTagCommand.takeNoAction = TriState.YES
                    getCommander()!!.executeCommand(mFindTagCommand)
                    success = mFindTagCommand.isSuccessful
                } else {
                    Log.d("TagFinderViewModel", "Using the .iv command")
                    // Use the inventory command to get the RSSI if the dedicated command doesnt exist
                    mInventoryCommand = InventoryCommand.synchronousCommand() // MAY NOT WORK WITH SYNCHRONOUS
                    mInventoryCommand.resetParameters = TriState.YES
                    mInventoryCommand.takeNoAction = TriState.YES

                    if (getEnabled()) {
                        Log.d("TagFinderViewModel", "getEnabled is true \n${mFindTagCommand.selectData}\n${mFindTagCommand.selectLength}\n${mFindTagCommand.selectOffset}")
                        mInventoryCommand.includeTransponderRssi = TriState.YES
                        mInventoryCommand.querySession = QuerySession.SESSION_0
                        mInventoryCommand.queryTarget = QueryTarget.TARGET_B
                        mInventoryCommand.inventoryOnly = TriState.NO
                        mInventoryCommand.selectData = targetEPC
                        mInventoryCommand.selectOffset = 0x20
                        mInventoryCommand.selectLength = mTargetTagEpc!!.length.times(4)
                        mInventoryCommand.selectAction = SelectAction.DEASSERT_SET_B_NOT_ASSERT_SET_A
                        mInventoryCommand.selectTarget = SelectTarget.SESSION_0
                        mInventoryCommand.useAlert = TriState.NO
                    }

                    Log.d("TagFinderViewModel", "Kill command: ${mInventoryCommand.commandLine}")
                    getCommander()!!.executeCommand(mInventoryCommand)
                    success = mInventoryCommand.isSuccessful
                }
            }

            if (success) {
                Log.d("TagFinderViewModel", "Successfully updated the target")
            } else {
                Log.e("TagFinderViewModel", "Failed to update the target")
            }
        }
    }

    // Function to fully reset reader settings
    fun cleanup() {
        Log.d("TagFinderViewModel", "Clearing all settings")
        if (getCommander() != null && getCommander()?.isConnected == true) {
            val fdCommand = FactoryDefaultsCommand()
            fdCommand.resetParameters = TriState.YES
            getCommander()!!.executeCommand(fdCommand)
            val switchCommand = SwitchActionCommand()
            switchCommand.resetParameters = TriState.YES
            getCommander()!!.executeCommand((switchCommand))
        }
    }

    // Function to perform a given task and make sure its not already being done
    private fun performTask(runnable: Runnable) {
        val task = runnable
        if (getCommander() == null) {
            Log.e("TagFinderViewModel", "No Ascii Commander set")
        } else {
            val startTime = Date()
            executorService.execute {
                lateinit var exception: Exception
                try {
                    mbusy = true
                    task.run()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("TagFinderViewModel", "Error when running given task")
                } finally {
                    mainThreadHandler.post {
                        mbusy = false
                        val finishTime = Date()
                        mTimeTaken = (finishTime.time - startTime.time)/ 1000.0
                    }
                }
            }
        }
    }

    // Function to convert the given hex value to ascii (and leave the unique as hex)
    fun hexToTagAscii(hex: String): String {
        Log.d("TagFinderViewModel", "The given hex: $hex")
        if (hex.startsWith("http")) {
            return hex.split("/").last()
        }
        try {
            var hexIn = hex
            if (hex.startsWith("68747470")) {
                val tempConversion = hexToAscii(hex)
                val lastSlashIndex = tempConversion.lastIndexOf('/') + 1
                if (lastSlashIndex != -1) {
                    hexIn = hex.substring(lastSlashIndex * 2)
                }
            }

            val type = hexToAscii(hexIn.substring(0,2))
            if (type == "1" || type == "3") {
                val toConvert = hexIn.dropLast(4)
                val unique = hexIn.takeLast(4)
                return hexToAscii(toConvert) +
                        unique
            } else if (type == "2" || type == "4") {
                val toConvert1 = hexIn.substring(0,12)
                val clone = hexIn.substring(12, 14)
                val passage = hexIn.substring(14, 16)
                val toConvert2 = hexIn.substring(16, 20)
                val unique = hexIn.substring(20)
                return hexToAscii(toConvert1) +
                        clone +
                        passage +
                        hexToAscii(toConvert2) +
                        unique
            } else {
                return ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val hexPattern = "^[0-9a-fA-F]+$".toRegex()
            if (!hexPattern.matches(hex)) { // This will just return the existing given value if it contains non-hex characters
                return hex
            }
            return ""
        }

    }

    // Function to convert the given ascii value to hex assuming its in tag format
    fun tagAsciiToHex(asciiStr: String): String {
        Log.d("TagFinderViewModel", "ascii to hex: $asciiStr")
        try {
            val type = asciiStr.first().toString()
            if (type == "1" || type == "3") {
                val toConvert = asciiStr.dropLast(4)
                val unique = asciiStr.takeLast(4)
                return asciiToHex(toConvert) +
                        unique
            } else if (type == "2" || type == "4") {
                val toConvert1 = asciiStr.substring(0, 6)
                val clone = asciiStr.substring(6, 8)
                val passage = asciiStr.substring(8, 10)
                val toConvert2 = asciiStr.substring(10, 12)
                val unique = asciiStr.substring(12)
                return asciiToHex(toConvert1) +
                        clone +
                        passage +
                        asciiToHex(toConvert2) +
                        unique
            } else {
                return ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }

    }

    // Function to convert a given ascii string to hex
    fun asciiToHex(asciiStr: String): String {
        return buildString {
            for (char in asciiStr) {
                append(char.code.toString(16))
            }
        }
    }


    // Function to convert the given hex value to ascii
    fun hexToAscii(hex: String): String {
        return buildString {
            for (i in hex.indices step 2) {
                val str = hex.substring(i, i + 2)
                val value = str.toInt(16)
                if (value in 1..127) {
                    append(str.toInt(16).toChar())
                } else {
                    append('?')
                }

            }
        }
    }

}