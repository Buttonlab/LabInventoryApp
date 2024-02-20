package com.example.multipagetesting2
// TODO: Make sure duplicates are not filtered out here but on the fragment side

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander
import com.uk.tsl.rfid.asciiprotocol.commands.BarcodeCommand
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand
import com.uk.tsl.rfid.asciiprotocol.commands.InventoryCommand
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState
import com.uk.tsl.rfid.asciiprotocol.responders.IBarcodeReceivedDelegate
import com.uk.tsl.rfid.asciiprotocol.responders.ITransponderReceivedDelegate
import com.uk.tsl.rfid.asciiprotocol.responders.TransponderData
import java.util.Locale
import java.util.zip.CRC32

class InventoryViewModel(): ViewModel() {

    private var isEnabled: Boolean = false
    private var tagsSeen: Int = 0
    private var uniquesOnly = true
    private var sendRSSI = false

    // This will be used to store the unique tags
    private val mUniqueTransponders = HashMap<String, TransponderData>()

    // The shared instance of the commander
    private lateinit var commander: AsciiCommander

    // Placeholder for inventory command (from the Java sample)
    private val inventoryCommand = InventoryCommand()

    // Making the LiveData object used to send the tags epc to the ui
    val epcNotification: MutableLiveData<String?> = MutableLiveData()

    // Making the LiveData object used to send the tags rssi to the ui
    val epcRssiNotification: MutableLiveData<String> = MutableLiveData()

    fun isBusy() = isEnabled

    fun setEnabled(state: Boolean) {
        val oldState = isEnabled
        isEnabled = state

        if (oldState != state) {
            if (isEnabled) {
                // Start listening to .iv and .bc
                getCommander().addResponder(mInventoryResponder)
                getCommander().addResponder(mBarcodeResponder)
            } else {
                // Stop listening to .iv and .bc
                getCommander().removeResponder(mInventoryResponder)
                getCommander().removeResponder(mBarcodeResponder)
            }
        }
    }

    private var mInventoryResponder: InventoryCommand
    private var mBarcodeResponder: BarcodeCommand
    //private val mAlertCommand: AlertCommand
    val command: InventoryCommand

    init {
        //mCommander = outCommander

        //mAlertCommand = AlertCommand()
        //mAlertCommand.duration = AlertDuration.SHORT

        // This is the command that will be used to perform configuration changes and inventories
        command = InventoryCommand()
        command.resetParameters = TriState.YES
        // Configure the type of inventory
        command.includeTransponderRssi = TriState.YES
        command.includeChecksum = TriState.YES
        command.includePC = TriState.YES
        command.includeDateTime = TriState.YES
        // Tell the reader to not make alert sound
        command.useAlert = TriState.NO

        // Use an InventoryCommand as a responder to capture all incoming inventory responses
        mInventoryResponder = InventoryCommand()
        // Also capture the responses that were not from App commands
        mInventoryResponder.setCaptureNonLibraryResponses(true)

        mInventoryResponder.transponderReceivedDelegate = ITransponderReceivedDelegate { transponder, moreAvailable ->
            if (sendRSSI) {
                sendEpcRssiNotification("${transponder.epc}:${transponder.rssi}")
            }

            if (transponder.epc != null && !mUniqueTransponders.containsKey(transponder.epc)) {
                sendEpcNotification("EPC:${transponder.epc}")

                tagsSeen++

                if (uniquesOnly) {
                    mUniqueTransponders[transponder.epc] = transponder
                    //Log.d("Tag", "EPC: ${transponder.epc}")
                }
            }
        }

        mBarcodeResponder = BarcodeCommand()
        mBarcodeResponder.setCaptureNonLibraryResponses(true)
        mBarcodeResponder.useEscapeCharacter = TriState.YES
        mBarcodeResponder.barcodeReceivedDelegate =
            IBarcodeReceivedDelegate { barcode -> sendEpcNotification("BC:$barcode") }
    }



    // This sets the commander that is needed for this ViewModel, must be set before doing anything else
    fun setCommander(inCommander: AsciiCommander) {
        commander = inCommander
    }

    private fun getCommander(): AsciiCommander {
        return commander
    }

    fun setSendRSSI(toSend: Boolean){
        sendRSSI = toSend
    }

    fun setUnique(setting: Boolean) {
        uniquesOnly = setting
    }

    fun clearUniques() {
        tagsSeen = 0
        mUniqueTransponders.clear()
    }

    fun removeFromSet(tag: String) {
        mUniqueTransponders.remove(tagAsciiToHex(tag.split(":", limit=2)[1]))
    }

    //
    // Reset the reader configuration to default command values
    //
    fun resetDevice() {
        if (getCommander().isConnected()) {
            val fdCommand = FactoryDefaultsCommand()
            fdCommand.resetParameters = TriState.YES
            getCommander().executeCommand(fdCommand)
        }
    }

    //
    // Update the reader configuration from the command
    // Call this after each change to the model's command
    //
    fun updateConfiguration() {
        if (getCommander().isConnected()) {
            try {
                command.takeNoAction = TriState.YES
                getCommander().executeCommand(command)
            } catch (e: Exception) {
                //sendMessageNotification(String.format(Locale.US,"Exception: %s", e.message))
                e.printStackTrace()
            }
        }
    }

    // Call this to set the power level of the reader True: max power, False: 12db
    fun setPowerLevel(setting: Boolean) {
        if (setting) {
            command.outputPower = getCommander().deviceProperties.maximumCarrierPower
            updateConfiguration()
            Log.d("Power Level", "Level should now be ${getCommander().deviceProperties.maximumCarrierPower}")
        } else {
            command.outputPower = 12
            updateConfiguration()
            Log.d("Power Level", "Level should now be 12")
        }
    }

    // Call this to check if the reader is at its max power setting
    fun isMaxPower(): Boolean? {
        try {
            return (command.outputPower == getCommander().deviceProperties.maximumCarrierPower)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("InventoryViewModel", "Failed to get command power level, probably null!")
            return null
        }
    }

    private fun sendEpcNotification(message: String) {
        epcNotification.postValue(message)
    }

    private fun sendEpcRssiNotification(message: String) {
        epcRssiNotification.postValue(message)
    }
}