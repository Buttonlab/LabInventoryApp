package com.example.multipagetesting2

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander
import com.uk.tsl.rfid.asciiprotocol.commands.BarcodeCommand
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand
import com.uk.tsl.rfid.asciiprotocol.commands.InventoryCommand
import com.uk.tsl.rfid.asciiprotocol.commands.WriteTransponderCommand
import com.uk.tsl.rfid.asciiprotocol.enumerations.Databank
import com.uk.tsl.rfid.asciiprotocol.enumerations.QuerySelect
import com.uk.tsl.rfid.asciiprotocol.enumerations.QuerySession
import com.uk.tsl.rfid.asciiprotocol.enumerations.QueryTarget
import com.uk.tsl.rfid.asciiprotocol.enumerations.SelectAction
import com.uk.tsl.rfid.asciiprotocol.enumerations.SelectTarget
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState
import com.uk.tsl.rfid.asciiprotocol.responders.IBarcodeReceivedDelegate
import com.uk.tsl.rfid.asciiprotocol.responders.ITransponderReceivedDelegate

class WriteTagViewModel(): ViewModel() {

    // The shared instance of the commander
    private lateinit var commander: AsciiCommander

    // The tag EPC that is sent to the fragment is here
    private var strongestTag: String? = null
    private var strongestSignal: Int? = null
    val strongestTagNotification: MutableLiveData<String?> = MutableLiveData()

    // The Item data from the QR code is sent with this
    val scannedItemNotification: MutableLiveData<String?> = MutableLiveData()

    // The commands used in this viewmodel
    private var mInventoryResponder: InventoryCommand
    private var mBarcodeResponder: BarcodeCommand
    private var mWriteCommand: WriteTransponderCommand
    private val command: InventoryCommand  // This is used for setting reader properties

    init {
        command = InventoryCommand()
        command.resetParameters = TriState.YES
        command.includeTransponderRssi = TriState.YES
        command.useAlert = TriState.NO


        mInventoryResponder = InventoryCommand()
        mInventoryResponder.setCaptureNonLibraryResponses(true)
        mInventoryResponder.transponderReceivedDelegate = ITransponderReceivedDelegate { transponder, _ ->
            if ((strongestSignal == null || transponder.rssi > strongestSignal!!) &&
                (strongestTag != transponder.epc)) {
                strongestTag = transponder.epc
                sendStrongestNotification(strongestTag.toString())
            }
        }

        mBarcodeResponder = BarcodeCommand()
        mBarcodeResponder.setCaptureNonLibraryResponses(true)
        mBarcodeResponder.useEscapeCharacter = TriState.YES
        mBarcodeResponder.barcodeReceivedDelegate =
            IBarcodeReceivedDelegate { barcode -> sendScannedItemNotification(barcode) }

        mWriteCommand = WriteTransponderCommand.synchronousCommand()
        mWriteCommand.resetParameters = TriState.YES
        mWriteCommand.selectAction = SelectAction.DEASSERT_SET_B_NOT_ASSERT_SET_A  // This and the below settings do but are used in the SDK by TSL
        mWriteCommand.selectTarget = SelectTarget.SESSION_2
        mWriteCommand.querySelect = QuerySelect.ALL
        mWriteCommand.querySession = QuerySession.SESSION_2
        mWriteCommand.queryTarget = QueryTarget.TARGET_B


    }

    // This is used to send the command in its current state and return the success or failure
    fun sendWriteCommand(): Boolean {
        try {
            getCommander().executeCommand(mWriteCommand)

            return mWriteCommand.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // This function is used to set the target to be overwritten
    fun setWriteTarget(target: String) { // TODO: Need to set these when the user scan or loads the fragment
        if (target.length == 24) {
            mWriteCommand.selectData = target
            mWriteCommand.selectOffset = 0x20
            mWriteCommand.selectLength = target.length * 4
            mWriteCommand.bank = Databank.ELECTRONIC_PRODUCT_CODE
        }
    }

    // This function is used to set what data to write
    fun setWriteData(itemData: String) {
        if (itemData.length == 24) {
            try {
                mWriteCommand.data = hexToByteArray(itemData)
                mWriteCommand.length = mWriteCommand.data.size / 2
                mWriteCommand.offset = 0x02 // This is an assumption based off the TSL app and where the EPC is stored
                mWriteCommand.bank = Databank.ELECTRONIC_PRODUCT_CODE
                // TODO: Set the other information needed to send the replacement EPC
            } catch (e: Exception) {
                // TODO: Maybe do something here to warn the user if it fails to set the data
            }
        }
    }

    // This updates the write command to set the kill password
    fun setWriteKillPassword(itemData: String) {
        if (itemData.length == 24) {
            // Targeting the given tag
            mWriteCommand.selectData = itemData
            mWriteCommand.selectOffset = 0x20
            mWriteCommand.selectLength = itemData.length * 4

            // Setting it to write the kill password
            mWriteCommand.bank = Databank.RESERVED
            mWriteCommand.data = hexToByteArray(hexToCrc32(itemData))
            mWriteCommand.length = mWriteCommand.data.size / 2
            mWriteCommand.offset = 0

        }
    }

    fun setCommander(inCommander: AsciiCommander) {
        commander = inCommander
    }

    private fun getCommander(): AsciiCommander {
        return commander
    }

    fun setupDevice() {
        if (getCommander().isConnected()) {
            try {
                command.outputPower = 12
                command.takeNoAction = TriState.YES
                getCommander().executeCommand(command)
                getCommander().addResponder(mInventoryResponder)
                getCommander().addResponder(mBarcodeResponder)
                getCommander().addSynchronousResponder()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resetDevice() {
        if (getCommander().isConnected()) {
            val fdCommand = FactoryDefaultsCommand()
            fdCommand.resetParameters = TriState.YES
            getCommander().executeCommand(fdCommand)
        }
    }

    fun sendStrongestNotification(tagEPC: String) {
        strongestTagNotification.postValue(tagEPC)
    }

    fun sendScannedItemNotification(tagEPC: String) {
        scannedItemNotification.postValue(tagEPC)
    }

}