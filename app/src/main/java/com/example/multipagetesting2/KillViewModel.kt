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
        val targetAscii = hexToTagAscii(targetHex)
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

}