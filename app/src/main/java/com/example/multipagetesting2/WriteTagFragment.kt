package com.example.multipagetesting2

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.multipagetesting2.databinding.FragmentWriteTagBinding
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class WriteTagFragment : Fragment() {

    // Variables for the entire fragment
    private var _binding: FragmentWriteTagBinding? = null
    private val binding get() = _binding!!

    // Initialize the basic ViewModel
    private val basicModel: BasicViewModel by activityViewModels()

    // Initialize the main ViewModel
    private lateinit var viewModel: WriteTagViewModel

    // The UI elements that will be modified to show information
    private lateinit var writeInstructions: TextView
    private lateinit var writeData: TextView
    private lateinit var targetEPC: TextView
    private lateinit var writeBtn: Button

    // Getting the substitutions recieved from the API
    val substitutions = DataRepository.substitutions

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(WriteTagViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWriteTagBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onPause() {
        super.onPause()

        // Call this to set the reader to default settings for other fragments
        viewModel.resetDevice()
        viewModel.strongestTagNotification.removeObservers(viewLifecycleOwner)
        viewModel.strongestTagNotification.postValue(null)
        viewModel.scannedItemNotification.removeObservers(viewLifecycleOwner)
        viewModel.scannedItemNotification.postValue(null)
    }

    override fun onResume() {
        super.onResume()

        // Call this to set the reader to the required settings
        viewModel.setCommander(getCommander()!!)
        viewModel.resetDevice()
        getCommander()?.clearResponders()
        viewModel.setupDevice()

        // Binding the UI elements
        writeInstructions = binding.writeInstructions
        writeData = binding.writeData
        targetEPC = binding.targetEPC
        writeBtn = binding.writeBtn

        // Displaying the target and item if they have already been scanned
        if (basicModel.writeTarget.isNotEmpty() && basicModel.writeItem != null) {
            targetEPC.setText(basicModel.writeTarget)
            displayTarget(basicModel.writeItem)
        } else if (basicModel.writeTarget.isNotEmpty()) {
            targetEPC.setText(basicModel.writeTarget)
        } else if (basicModel.writeItem != null) {
            displayTarget(basicModel.writeItem)
        }

        // Displaying the correct instructions
        displayInstructions()

        // The scanned QR code will show up here
        viewModel.scannedItemNotification.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                // Getting the ascii conversion of the message
                val asciiItem = hexToTagAscii(message)
                basicModel.writeItem = cellFromEPC(asciiItem)

                // Getting name if it is a basicItem type
                if (asciiItem.startsWith("66")) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val response = DataRepository.getCellByID(asciiItem)
                            if (response.isSuccessful && response.body() != null) {
                                basicModel.writeItem = response.body()!!
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        withContext(Dispatchers.Main) {
                            displayTarget(basicModel.writeItem)
                        }
                    }
                } else {  // Display other types without API as it is unneeded
                    displayTarget(basicModel.writeItem)
                }

                // Displaying the correct instructions
                displayInstructions()
            }
        }

        // This is where the RFID scanned tag will be received
        viewModel.strongestTagNotification.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                targetEPC.setText(message)
                basicModel.writeTarget = message

                // Displaying the correct instructions
                displayInstructions()
            }
        }

        // Copying the target epc on a tap
        targetEPC.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("RFID tag", basicModel.writeTarget)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "EPC value copied!", Toast.LENGTH_SHORT).show()
        }

        // Writing the tag when the button is pressed
        writeBtn.setOnClickListener {
            if (basicModel.writeTarget.isNotEmpty() && basicModel.writeItem != null) {
                viewModel.setWriteTarget(basicModel.writeTarget.uppercase())
                viewModel.setWriteData(tagAsciiToHex(reprCell(basicModel.writeItem!!)).uppercase(Locale.US))
                val epcSuccess = viewModel.sendWriteCommand() // This sends the command to set the new EPC
                Log.d("WriteTagFragment", "Did epc write work? ${epcSuccess}")
                viewModel.setWriteKillPassword(tagAsciiToHex(reprCell(basicModel.writeItem!!)).uppercase(Locale.US))
                val killSuccess = viewModel.sendWriteCommand() // This sends the command to set the kill password
                Log.d("WriteTagFragment", "Did kill pass write work? ${killSuccess}")

                // Alerting the user of success or failure
                if (epcSuccess && killSuccess) {
                    Toast.makeText(requireContext(), "The tag was written successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "The tag write failed!", Toast.LENGTH_SHORT).show()
                }

                // Clearing these as the user has sent the write command
                basicModel.writeTarget = ""
                basicModel.writeItem = null
                targetEPC.text = ContextCompat.getString(requireContext(), R.string.rfid_scan_tag)
                writeData.text = ContextCompat.getString(requireContext(), R.string.scan_item_qr_code)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun getCommander(): AsciiCommander? {
        return AsciiCommander.sharedInstance()
    }

    private fun displayTarget(target: CellItem?) {
        var textVisible = ""
        if (target != null) {
            if (target.type.equals("1") || target.type.equals("3")) {
                val genotype = substitutions?.subs?.get("genotype")?.get(target.genotype) ?: target.genotype ?: ""
                val distNum = target.distNum?.toInt(36) ?: ""
                val year = target.year ?: ""
                val owner = substitutions?.subs?.get("owner")?.get(target.owner) ?: target.owner ?: ""
                val passage = target.passage?.toInt(36) ?: ""
                val surface = substitutions?.subs?.get("surface")?.get(target.surface) ?: target.surface ?: ""
                val number = target.number?.toInt(36) ?: ""

                textVisible = "$genotype$distNum$year   $owner\nOn:$surface   Psg#$passage   #$number"
            } else if (target.type.equals("2") || target.type.equals("4")) {
                val targetType = substitutions?.subs?.get("targetType")?.get(target.cellType) ?: target.cellType ?: ""
                val genemod = substitutions?.subs?.get("genemod")?.get(target.genemod) ?: target.genemod ?: ""
                val gene1 = substitutions?.subs?.get("gene1")?.get(target.gene1) ?: target.gene1 ?: ""
                val gene2 = substitutions?.subs?.get("gene2")?.get(target.gene2) ?: target.gene2 ?: ""
                val resistance = substitutions?.subs?.get("resistance")?.get(target.resistance) ?: target.resistance ?: ""
                val clone = target.clone?.toInt(16) ?: ""
                val passage = target.passage?.toInt(16) ?: ""
                val number = target.number?.toInt(36) ?: ""
                val owner = substitutions?.subs?.get("owner")?.get(target.owner) ?: target.owner ?: ""

                textVisible = "$targetType    $genemod   $gene1   $gene2\n$resistance  Clone#$clone  Psg#${passage}  #${number}  ${owner}"
            } else if (target.type.equals("5")) {
                val otherType = substitutions?.subs?.get("targetType")?.get(target.otherType) ?: target.otherType ?: ""
                val otherGenemod = substitutions?.subs?.get("genemod")?.get(target.otherGenemod) ?: target.otherGenemod ?: ""
                val gene1 = substitutions?.subs?.get("gene1")?.get(target.gene1) ?: target.gene1 ?: ""
                val gene2 = substitutions?.subs?.get("gene2")?.get(target.gene2) ?: target.gene2 ?: ""
                val primaryResistance = substitutions?.subs?.get("resistance")?.get(target.primaryResistance) ?: target.primaryResistance ?: ""
                val vectorResistance = substitutions?.subs?.get("resistance")?.get(target.vectorResistance) ?: target.vectorResistance ?: ""
                val clone = target.clone?.toInt(16) ?: ""
                val number = target.number?.toInt(36) ?: ""
                val owner = substitutions?.subs?.get("owner")?.get(target.owner) ?: target.owner ?: ""

                textVisible = "$otherType    $otherGenemod   $gene1   $gene2\n$primaryResistance  $vectorResistance  Clone#$clone  #${number}  ${owner}"
            } else if (target.type.equals("6")) {
                val name = if (!target.name.isNullOrEmpty()) target.name.replace("\n", "") else target.id

                textVisible = "Basic Item:\n${name}"
            } else if (target.type.equals("7")) {
                val targetType = substitutions?.subs?.get("targetType")?.get(target.cellType) ?: target.cellType ?: ""
                val source = substitutions?.subs?.get("source")?.get(target.source) ?: target.source ?: ""
                val genemod = substitutions?.subs?.get("genemod")?.get(target.genemod) ?: target.genemod ?: ""
                val gene1 = substitutions?.subs?.get("gene1")?.get(target.gene1) ?: target.gene1 ?: ""
                val gene2 = substitutions?.subs?.get("gene2")?.get(target.gene2) ?: target.gene2 ?: ""
                val media = substitutions?.subs?.get("media")?.get(target.media) ?: target.media ?: ""
                val supplements = target.clone?.toInt(16) ?: ""
                val owner = substitutions?.subs?.get("owner")?.get(target.owner) ?: target.owner ?: ""

                textVisible = "$targetType    $genemod   $gene1   $gene2\n$source    $media    $supplements    ${owner}"
            } else {
                textVisible = "ERROR: Invalid type"
            }
        }
        // Displaying the item on the UI
        writeData.setText(textVisible)
    }

    private fun displayInstructions() {
        if (basicModel.writeTarget.isEmpty() && basicModel.writeItem == null) { // Both empty
            writeInstructions.text = ContextCompat.getString(requireContext(), R.string.write_instructions_1)
            writeBtn.isEnabled = false
        } else if (basicModel.writeTarget.isEmpty() && basicModel.writeItem != null) { // Item set
            writeInstructions.text = ContextCompat.getString(requireContext(), R.string.write_instructions_2)
            writeBtn.isEnabled = false
        } else if (basicModel.writeTarget.isNotEmpty() && basicModel.writeItem == null) { // Target set
            writeInstructions.text = ContextCompat.getString(requireContext(), R.string.write_instructions_3)
            writeBtn.isEnabled = false
        } else if (basicModel.writeTarget.isNotEmpty() && basicModel.writeItem != null) { // Both set
            writeInstructions.text = ContextCompat.getString(requireContext(), R.string.write_instructions_4)
            writeBtn.isEnabled = true
        }
    }
}