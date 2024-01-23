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
        // Handle the sound alerts in the App
        command.useAlert = TriState.NO

        // Use an InventoryCommand as a responder to capture all incoming inventory responses
        mInventoryResponder = InventoryCommand()
        // Also capture the responses that were not from App commands
        mInventoryResponder.setCaptureNonLibraryResponses(true)

        mInventoryResponder.transponderReceivedDelegate = ITransponderReceivedDelegate { transponder, moreAvailable ->
            if (sendRSSI && transponder.epc.startsWith("21") && transponder.epc.endsWith("21")) {
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

    // Function to convert the tag hex to the crc32 password
    fun hexToCrc32(hex: String): String {
        val crcCalc = CRC32()
        val byteArray = ByteArray(hex.length / 2)
        for (i in byteArray.indices) {
            val index = i * 2
            val j = hex.substring(index, index + 2).toInt(16)
            byteArray[i] = j.toByte()
        }
        crcCalc.update(byteArray)
        return "%08X".format(crcCalc.value)
    }

    // Function to convert the given hex value to ascii (and leave the unique as hex)
    fun hexToTagAscii(hex: String): String {
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

    // Function to convert an ascii string to crc32
    fun asciiToCrc32(ascii: String): String {
        val hexVal = buildString {
            for (char in ascii) {
                append(String.format("%02X", char.code))
            }
        }
        val crcVal = hexToCrc32(hexVal)
        return crcVal
    }

    // Function to convert a list of ascii string to crc32
    fun asciiToCrc32(values: List<String>): String {
        val combinedVal = values.joinToString("").uppercase(Locale.US)
        val hexVal = buildString {
            for (char in combinedVal) {
                append(String.format("%02X", char.code))
            }
        }
        val crcVal = hexToCrc32(hexVal)
        return crcVal
    }

    private fun sendEpcNotification(message: String) {
        epcNotification.postValue(message)
    }

    private fun sendEpcRssiNotification(message: String) {
        epcRssiNotification.postValue(message)
    }
}