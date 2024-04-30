package com.example.multipagetesting2

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander
import com.uk.tsl.rfid.asciiprotocol.commands.BarcodeCommand
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
import com.uk.tsl.rfid.asciiprotocol.responders.IBarcodeReceivedDelegate
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

    private var mBarcodeResponder: BarcodeCommand
    private var isEnabled: Boolean = false
    // Making the LiveData object used to send the tags epc to the ui
    val epcNotification: MutableLiveData<String?> = MutableLiveData()

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

    // What power level the command is set to
    private var powerLevel = true


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

        mBarcodeResponder = BarcodeCommand()
        mBarcodeResponder.setCaptureNonLibraryResponses(true)
        mBarcodeResponder.useEscapeCharacter = TriState.YES
        mBarcodeResponder.barcodeReceivedDelegate =
            IBarcodeReceivedDelegate { barcode -> sendEpcNotification(barcode) }
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
        if (oldState != mEnabled) {
            if (mEnabled) {
                // Listen for transponders and triggers
                if (!getCommander().hasSynchronousResponder) {
                    getCommander().addSynchronousResponder()
                }
                getCommander().addResponder(mSignalStrengthResponder)
                getCommander().addResponder(mSwitchResponder)
                getCommander().addResponder(mBarcodeResponder)
            } else {
                // Stop listening for transponders and triggers
                if (getCommander().hasSynchronousResponder) {
                    getCommander().removeSynchronousResponder()
                }
                getCommander().removeResponder(mSwitchResponder)
                getCommander().removeResponder(mSignalStrengthResponder)
                getCommander().removeResponder(mBarcodeResponder)
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
    private fun getCommander(): AsciiCommander {
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
        if (getCommander().isConnected) {
            try {
                performTask {
                    getCommander().executeCommand(FactoryDefaultsCommand())
                    mFindTagCommand.resetParameters = TriState.YES
                    mFindTagCommand.takeNoAction = TriState.YES
                    getCommander().executeCommand(mFindTagCommand)

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
        if (getCommander().isConnected == true) {
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

            getCommander().executeCommand(switchActionCommand)

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
                // Getting only the ID of the cell to use as the data mask
                var targetOffset = 32
                if (targetEPC.startsWith("31") || targetEPC.startsWith("33")) {  // Primary cell tag
                    targetEPC = targetEPC.takeLast(10)
                    targetOffset += 56
                }
                // The below would filter only by the ID like primary cell above but is not used as it degrades performance
//                else if (targetEPC.startsWith("32") || targetEPC.startsWith("34")) {  // Immortal cell tag
//                    targetEPC = targetEPC.takeLast(12)
//                    targetOffset += 48
//                } else if (targetEPC.startsWith("35")) {  // Frozen Other tag
//                    targetEPC = targetEPC.takeLast(6)
//                    targetOffset += 72
//                }else if (targetEPC.startsWith("37")) {  // Mucus Sample tag
//                    targetEPC = targetEPC.takeLast(4)
//                    targetOffset += 80
//                }

                // Use the .ft command if it exists on the reader
                if (mUseFindTagCommand) {
                    Log.d("TagFinderViewModel", "Using the .ft command")
                    mFindTagCommand = FindTagCommand.synchronousCommand() // MAY NOT WORK WITH SYNCHRONOUS
                    mFindTagCommand.resetParameters = TriState.YES

                    if (getEnabled()) {
                        mFindTagCommand.selectData = targetEPC
                        mFindTagCommand.selectLength = targetEPC!!.length.times(4)
                        mFindTagCommand.selectOffset = targetOffset
                        Log.d("TagFinderViewModel", "getEnabled is true \n${mFindTagCommand.selectData}\n${mFindTagCommand.selectLength}\n${mFindTagCommand.selectOffset}")
                    }
                    if (powerLevel) {
                        mFindTagCommand.outputPower = getCommander().deviceProperties.maximumCarrierPower
                    } else {
                        mFindTagCommand.outputPower = 12
                    }

                    mFindTagCommand.takeNoAction = TriState.YES
                    getCommander().executeCommand(mFindTagCommand)
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
                        mInventoryCommand.selectOffset = targetOffset
                        mInventoryCommand.selectLength = targetEPC!!.length.times(4)
                        mInventoryCommand.selectAction = SelectAction.DEASSERT_SET_B_NOT_ASSERT_SET_A
                        mInventoryCommand.selectTarget = SelectTarget.SESSION_0
                        mInventoryCommand.useAlert = TriState.NO
                    }
                    if (powerLevel) {
                        mInventoryCommand.outputPower = getCommander().deviceProperties.maximumCarrierPower
                    } else {
                        mInventoryCommand.outputPower = 12
                    }

                    Log.d("TagFinderViewModel", "Inventory command: ${mInventoryCommand.commandLine}")
                    getCommander().executeCommand(mInventoryCommand)
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
        if (getCommander().isConnected == true) {
            val fdCommand = FactoryDefaultsCommand()
            fdCommand.resetParameters = TriState.YES
            getCommander().executeCommand(fdCommand)
            val switchCommand = SwitchActionCommand()
            switchCommand.resetParameters = TriState.YES
            getCommander().executeCommand((switchCommand))
        }
    }

    // Function to perform a given task and make sure its not already being done
    private fun performTask(runnable: Runnable) {
        val task = runnable

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

    // Setting the power level for the commands True is high and False is low
    fun setPowerLevel(givenLevel: Boolean) {
        if (givenLevel) {
            powerLevel = true
            Log.d("TagFinderFragment", "Power level is now max")
            updateTarget()
        } else {
            powerLevel = false
            Log.d("TagFinderFragment", "Power level is now low")
            updateTarget()
        }
    }

    // Getting the power level setting
    fun getPowerLevel(): Boolean {
        return powerLevel
    }

    private fun sendEpcNotification(message: String) {
        epcNotification.postValue(message)
    }

}