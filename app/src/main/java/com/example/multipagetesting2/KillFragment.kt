package com.example.multipagetesting2

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.multipagetesting2.databinding.FragmentKillBinding
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.full.memberProperties

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class KillFragment : Fragment() {

    private var _binding: FragmentKillBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // Creating an instance of the KillViewModel
    private lateinit var viewModel: KillViewModel

    // Initialize the basic ViewModel
    private val basicModel: BasicViewModel by activityViewModels()

    // Storing the substitutions from the API
    val substitutions = DataRepository.substitutions

    // Storing the response from the API
    private var cellItem: CellItem? = null

    // The displayed fields of the selected cell
    private lateinit var tagHex: TextView
    private lateinit var hexBox: LinearLayout
    private lateinit var tagAscii: TextView
    private lateinit var asciiBox: LinearLayout
    private lateinit var tagType: TextView
    private lateinit var typeBox: LinearLayout
    private lateinit var tagGenotype: TextView
    private lateinit var genotypeBox: LinearLayout
    private lateinit var tagDistNum: TextView
    private lateinit var distNumBox: LinearLayout
    private lateinit var tagCellType: TextView
    private lateinit var cellTypeBox: LinearLayout
    private lateinit var tagSource: TextView
    private lateinit var sourceBox: LinearLayout
    private lateinit var tagGenemod: TextView
    private lateinit var genemodBox: LinearLayout
    private lateinit var tagGene1: TextView
    private lateinit var gene1Box: LinearLayout
    private lateinit var tagGene2: TextView
    private lateinit var gene2Box: LinearLayout
    private lateinit var tagResistance: TextView
    private lateinit var resistanceBox: LinearLayout
    private lateinit var tagClone: TextView
    private lateinit var cloneBox: LinearLayout
    private lateinit var tagName: TextView
    private lateinit var nameBox: LinearLayout

    private lateinit var scanInstructions: TextView
    private lateinit var failedLookupWarning: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        _binding = FragmentKillBinding.inflate(inflater, container, false)


        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(KillViewModel::class.java)
    }

    override fun onPause() {
        super.onPause()

        viewModel.setEnabled(false)
        viewModel.resetDevice()
        viewModel.mTagBC.removeObservers(viewLifecycleOwner)
        viewModel.mTagBC.postValue(null)
    }

    override fun onResume() {
        super.onResume()

        viewModel.setCommander(getCommander()!!)
        viewModel.resetDevice()
        getCommander()?.clearResponders()
        viewModel.setEnabled(true)

        // Setup for the tag information displays
        tagHex = binding.tagHex
        hexBox = binding.hexBox
        tagAscii = binding.tagAscii
        asciiBox = binding.asciiBox
        tagType = binding.tagType
        typeBox = binding.typeBox
        tagGenotype = binding.tagGenotype
        genotypeBox = binding.genotypeBox
        tagDistNum = binding.tagDistNum
        distNumBox = binding.distNumBox
        tagSource = binding.tagSource
        sourceBox = binding.sourceBox
        tagCellType = binding.tagCellType
        cellTypeBox = binding.cellTypeBox
        tagGenemod = binding.tagGenemod
        genemodBox = binding.genemodBox
        tagGene1 = binding.tagGene1
        gene1Box = binding.gene1Box
        tagGene2 = binding.tagGene2
        gene2Box = binding.gene2Box
        tagResistance = binding.tagResistance
        resistanceBox = binding.resistanceBox
        tagClone = binding.tagClone
        cloneBox = binding.cloneBox
        tagName = binding.tagName
        nameBox = binding.nameBox

        scanInstructions = binding.scanInstructions
        failedLookupWarning = binding.failedLookupWarning
        resetUI()

        // Setup for the kill tag button
        val killTag = binding.killBtn
        killTag.setOnClickListener {
            if (tagHex.text != ContextCompat.getString(requireContext(), R.string.tag_hex_value)) {
                val hexStr = tagHex.text.toString()
                Log.d("KillFragment", "Hex: ${tagHex.text} Ascii: ${tagAscii.text} toString: $hexStr crc32: ${hexToCrc32(hexStr)}")
                // Killing the rfid tag with this command
                viewModel.killTarget(hexStr)

                if (viewModel.checkTag(hexStr)) {// TODO: This doesnt appear to work
                    resetUI()
                    Toast.makeText(requireContext(), "Kill succeeded!", Toast.LENGTH_SHORT).show()

                    viewLifecycleOwner.lifecycleScope.launch {
                        var canReachAPI = false
                        for (j in 1..2) {
                            try {
                                canReachAPI = DataRepository.pingAPI()
                                if (canReachAPI) {
                                    break
                                }
                            } catch (e: Exception) {
                                canReachAPI = false
                            }

                        }

                        if (canReachAPI) {  // If the API can be reached send the kill command
                            for (i in 1..2) {
                                try {
                                    // Call the kill cell endpoint for the API
                                    val toKill = hexToTagAscii(hexStr)
                                    val response = DataRepository.killCellByID(toKill, asciiToCrc32(toKill))
                                    if (response.isSuccessful) {
                                        break
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Log.e("SummaryFragment", "Failing to get cell info")
                                    if (i < 2) delay(1000)
                                }
                            }
                        } else {  // If the API cant be reached save the request to the queue
                            val request = QueuedApiRequest("Inventory", hexToTagAscii(hexStr))
                            saveRequestToQueue(requireContext(), request)

                            Toast.makeText(requireContext(), "Kill request queued!", Toast.LENGTH_SHORT).show()
                        }

                    }
                }
            }
        }

        // This receives the data when scanning a tag's QR code
        viewModel.mTagBC.observe(viewLifecycleOwner) { message ->
            setTargetItem(message)
        }

        // Checking the saved target from inventory page
        if (basicModel.getSelectedTag().isNotEmpty()) {
            setTargetItem(basicModel.getSelectedTag())
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        viewModel.setEnabled(false)
    }

    fun getCommander(): AsciiCommander? {
        return AsciiCommander.sharedInstance()
    }

    // This is used to get the scanned item's information and set the kill target
    private fun setTargetItem(item: String?) {
        if ( item != null ) {
            var cellID = ""
            if (item.startsWith("http")) {
                tagHex.setText(tagAsciiToHex(item.split("/").last()))
                tagAscii.setText(item.split("/").last())
                cellID = item.split("/").last()
            } else if (item.length == 24) {
                tagHex.setText(item)
                tagAscii.setText(hexToTagAscii(item))
                cellID = hexToTagAscii(item)
            } else if (isValidLength(item)) {
                tagHex.setText(tagAsciiToHex(item))
                tagAscii.setText(item)
                cellID = item
            }

            if (cellID != "") {
                // Add the info from the database
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // Getting the cell info from the API
                        val response = DataRepository.getCellByID(cellID)
                        if (response.isSuccessful) {
                            cellItem = response.body()!!
                        } else {
                            cellItem = null
                        }
                        withContext(Dispatchers.Main) {
                            changeUI(cellItem)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun resetUI() {
        tagHex.text = ContextCompat.getString(requireContext(), R.string.tag_hex_value)
        hexBox.visibility = View.GONE
        tagAscii.text = ContextCompat.getString(requireContext(), R.string.tag_ascii_value)
        asciiBox.visibility = View.GONE
        tagType.text = ContextCompat.getString(requireContext(), R.string.tag_cell_type_value)
        typeBox.visibility = View.GONE
        tagGenotype.text = ContextCompat.getString(requireContext(), R.string.tag_genotype_value)
        genotypeBox.visibility = View.GONE
        tagDistNum.text = ContextCompat.getString(requireContext(), R.string.tag_dist1_value)
        distNumBox.visibility = View.GONE
        tagCellType.text = ContextCompat.getString(requireContext(), R.string.tag_cell_type_value)
        cellTypeBox.visibility = View.GONE
        tagSource.text = ContextCompat.getString(requireContext(), R.string.tag_source_value)
        sourceBox.visibility = View.GONE
        tagGenemod.text = ContextCompat.getString(requireContext(), R.string.tag_genemod_value)
        genemodBox.visibility = View.GONE
        tagGene1.text = ContextCompat.getString(requireContext(), R.string.tag_gene1_value)
        gene1Box.visibility = View.GONE
        tagGene2.text = ContextCompat.getString(requireContext(), R.string.tag_gene2_value)
        gene2Box.visibility = View.GONE
        tagResistance.text = ContextCompat.getString(requireContext(), R.string.tag_resistance_value)
        resistanceBox.visibility = View.GONE
        tagClone.text = ContextCompat.getString(requireContext(), R.string.tag_clone_value)
        cloneBox.visibility = View.GONE
        tagName.text = ContextCompat.getString(requireContext(), R.string.tag_name_value)
        nameBox.visibility = View.GONE
        scanInstructions.visibility = View.VISIBLE
        failedLookupWarning.visibility = View.GONE

    }

    // This is used to change all of the UI text elements for the cell item
    private fun changeUI(cellItem: CellItem?) {
        scanInstructions.visibility = View.GONE
        if (substitutions != null && substitutions.subs != null) {
            // This contains all of the text elements used in the summary page and the field to look under for substitutions
            // NONE is used fore datetime fields that are sent over formatted by the API already
            val summaryItems = listOf(
                Triple(typeBox, tagType, "type"),
                Triple(genotypeBox,tagGenotype, "genotype"),
                Triple(distNumBox, tagDistNum, "distNum"),
                Triple(cellTypeBox, tagCellType, "cellType"),
                Triple(sourceBox, tagSource, "source"),
                Triple(genemodBox, tagGenemod, "genemod"),
                Triple(gene1Box, tagGene1, "gene1"),
                Triple(gene2Box, tagGene2, "gene2"),
                Triple(resistanceBox, tagResistance, "resistance"),
                Triple(cloneBox, tagClone, "clone"),
                Triple(nameBox, tagName, "name"),
            )
            val regex = "---(.*?)---".toRegex()

            if (cellItem != null) {
                failedLookupWarning.visibility = View.GONE
                // Loop through all the pairs described above
                for ((box, text, field) in summaryItems) {
                    Log.d("Substitution", "Checking for $field")
                    // This will run if there exists a substitution for the value
                    if (substitutions.subs.containsKey(field)) {
                        // Get the value current field is set to eg. cellItem.type will be 1, 2, etc...
                        val givenText = cellItem::class.memberProperties
                            .firstOrNull {it.name.equals(field, ignoreCase = true)}
                            ?.call(cellItem)
                            ?.toString()

                        Log.d("Substitution", "$field contains the text $givenText")
                        // If the value was given by the API (meaning it wasnt empty or null in the database)
                        if (!givenText.isNullOrEmpty()) {
                            // Make the text item visible as it has a valid thing to show
                            box.visibility = View.VISIBLE

                            // Loop through the substitution options for the field
                            val possibleSubs = substitutions.subs[field]
                            for ((key, value) in possibleSubs!!) {
                                // If the given text from the API equals a substitution then substitute to the correct value like "N" turns to "None"
                                if (givenText == key) {
                                    Log.d("Substitution", "Replacing $givenText with $value")
                                    text.setText(value)
                                    break
                                }
                            }
                        } else {
                            // This means that the API sent over an empty or null value
                            box.visibility = View.GONE
                            text.setText("UNSET")
                        }
                    } else { // This will run if there is no substitution for a value
                        Log.d("Substitution", "No change to make for $field")
                        // Get the value current field is set to eg. cellItem.type will be 1, 2, etc...
                        val givenText = cellItem::class.memberProperties
                            .firstOrNull {it.name.equals(field, ignoreCase = true)}
                            ?.call(cellItem)
                            ?.toString()
                        Log.d("Substitution", "$field is $givenText ${givenText != null}")
                        // If the value was given by the API (meaning it wasnt empty or null in the database)
                        if (!givenText.isNullOrEmpty()) {
                            // Make the text item visible as it has a valid thing to show
                            box.visibility = View.VISIBLE
                            text.setText(regex.replace(text.text, givenText))
                        }
                    }
                }
            } else { // The given item was null so display the warning
                failedLookupWarning.visibility = View.VISIBLE
                for ((box, text, field) in summaryItems) {
                    box.visibility = View.GONE
                    text.setText("UNSET")
                }
            }


        } else {
            Log.e("SummaryFragment", "Substitutions is null!")
        }


    }

}