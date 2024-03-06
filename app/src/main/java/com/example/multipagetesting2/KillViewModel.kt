package com.example.multipagetesting2

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander
import com.uk.tsl.rfid.asciiprotocol.commands.BarcodeCommand
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand
import com.uk.tsl.rfid.asciiprotocol.commands.InventoryCommand
import com.uk.tsl.rfid.asciiprotocol.commands.KillCommand
import com.uk.tsl.rfid.asciiprotocol.enumerations.Databank
import com.uk.tsl.rfid.asciiprotocol.enumerations.SelectTarget
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState
import com.uk.tsl.rfid.asciiprotocol.responders.IBarcodeReceivedDelegate
import com.uk.tsl.rfid.asciiprotocol.responders.ITransponderReceivedDelegate
import java.util.Locale
import java.util.zip.CRC32

class KillViewModel: ViewModel() {

    // Used to store the ASciiCommander shared instance
    private lateinit var mCommander: AsciiCommander

    // Live data value used to pass the scanned data back to the fragment
    var mTagBC: MutableLiveData<String> = MutableLiveData()

    // Responder used to capture what the user scans
    private var mBarcodeResponder: BarcodeCommand

    // Command used to kill the tag
    private var mKillCommand: KillCommand

    // Hold if the viewModel is enabled
    private var isEnabled = false

    init {
        mBarcodeResponder = BarcodeCommand()

        mBarcodeResponder = BarcodeCommand()
        mBarcodeResponder.setCaptureNonLibraryResponses(true)
        mBarcodeResponder.useEscapeCharacter = TriState.YES
        mBarcodeResponder.barcodeReceivedDelegate =
            IBarcodeReceivedDelegate { barcode -> mTagBC.postValue(barcode) }

        mKillCommand = KillCommand()
    }

    // Function to set and remove the responder
    fun setEnabled(state: Boolean) {
        val oldState = isEnabled
        isEnabled = state

        if (oldState != state) {
            if (isEnabled) {
                // Start listening to .iv and .bc
                getCommander().addResponder(mBarcodeResponder)
            } else {
                // Stop listening to .iv and .bc
                getCommander().removeResponder(mBarcodeResponder)
            }
        }
    }

    // Get and Set for the commander
    private fun getCommander(): AsciiCommander {
        return mCommander
    }
    fun setCommander(commander: AsciiCommander) {
        mCommander = commander
    }


    // Function used to kill the given tag
    fun killTarget(targetHex: String) {
        val targetAscii = hexToAscii(targetHex)
        Log.d("KillViewModel", "Target Hex: $targetHex")
        Log.d("KillViewModel", "Target Ascii: $targetAscii")
        val targetPass = hexToCrc32(targetHex)

        mKillCommand.resetParameters = TriState.YES
        mKillCommand.accessPassword = "00000000"
        mKillCommand.killPassword = targetPass
        mKillCommand.selectBank = Databank.ELECTRONIC_PRODUCT_CODE
        mKillCommand.selectData = targetHex
        mKillCommand.selectLength = targetHex.length.times(4)
        mKillCommand.selectOffset = 0x20
        mKillCommand.selectTarget = SelectTarget.SESSION_1


        getCommander().executeCommand(mKillCommand)
        Log.d("KillViewModel", "Kill command: ${mKillCommand.commandLine}")
    }

    // Function to check if the tag is alive
    fun checkTag(targetHex: String): Boolean {
        Log.e("Tag", "Check tag was called!")
        val invCommand = InventoryCommand.synchronousCommand()
        val epcSet = mutableSetOf<String>()
        invCommand.resetParameters = TriState.YES
        invCommand.outputPower = getCommander().deviceProperties.maximumCarrierPower
        val invResponder = InventoryCommand()
        invCommand.transponderReceivedDelegate = ITransponderReceivedDelegate { transponder, _ ->
            epcSet.add(transponder.epc)
            Log.e("Tag", "EPC:${transponder.epc}")
        }
        invResponder.transponderReceivedDelegate = ITransponderReceivedDelegate { transponder, _ ->
            epcSet.add(transponder.epc)
            Log.e("Tag", "EPC:${transponder.epc}")
        }

        if (!getCommander().hasSynchronousResponder) {
            getCommander().addSynchronousResponder()
        }
        //getCommander().addResponder(invResponder)
        getCommander().executeCommand(invCommand)

        Log.d("KillViewModel", "Checking for the tag $targetHex in the set:$epcSet")
        if (epcSet.contains(targetHex) || epcSet.contains(targetHex.uppercase(Locale.US))) {
            getCommander().removeResponder(invResponder)
            Log.e("Tag", "Tag found!")
            return false
        } else {
            getCommander().removeResponder(invResponder)
            Log.e("Tag", "Tag not found!")
            return true
        }
    }

    // Function to reset the device to its defaults
    fun resetDevice() {
        if (getCommander().isConnected()) {
            val fdCommand = FactoryDefaultsCommand()
            fdCommand.resetParameters = TriState.YES
            getCommander().executeCommand(fdCommand)
        }
    }

    // Function to convert the given hex value to ascii (and leave the unique as hex)
    fun hexToTagAscii(hex: String): String {
        Log.d("KillViewModel", "The given hex: $hex")
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
        Log.d("KillViewModel", "$asciiStr to hex")
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

}