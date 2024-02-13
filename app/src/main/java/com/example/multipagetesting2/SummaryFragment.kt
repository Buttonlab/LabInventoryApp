package com.example.multipagetesting2

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.multipagetesting2.databinding.FragmentSummaryBinding
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.full.memberProperties


class SummaryFragment : Fragment() {

    private var _binding: FragmentSummaryBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // Creating an instance of the InventoryViewModel
    private lateinit var viewModel: InventoryViewModel

    // The map that will store the given EPC and RSSI values
    private val epcRssiMap = mutableMapOf<String, Int>()

    // Storing the size of the list to know when to update
    private var mapLastSize = 0

    // Storing the tagSelect field
    private lateinit var tagSelect: AutoCompleteTextView

    // Adapter for the main target dropdown
    private var targetAdapter: ArrayAdapter<String>? = null

    // Storing the response from the API
    private var cellItem: CellItem? = null

    // Storing the substitutions from the API
    private val substitutions = DataRepository.substitutions

    // Storing the last tag that was scanned
    private var target: String? = null

    // Holding the response of the API call
    private lateinit var mCellsList: ArrayList<String>

    // All of the displayed values
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
    private lateinit var tagYear: TextView
    private lateinit var yearBox: LinearLayout
    private lateinit var tagOwner: TextView
    private lateinit var ownerBox: LinearLayout
    private lateinit var tagPassage: TextView
    private lateinit var passageBox: LinearLayout
    private lateinit var tagSurface: TextView
    private lateinit var surfaceBox: LinearLayout
    private lateinit var tagNumber: TextView
    private lateinit var numberBox: LinearLayout
    private lateinit var tagStatus: TextView
    private lateinit var statusBox: LinearLayout
    private lateinit var tagLocation: TextView
    private lateinit var locationBox: LinearLayout
    private lateinit var tagSupportQuantity: TextView
    private lateinit var supportQuantityBox: LinearLayout
    private lateinit var tagMedia: TextView
    private lateinit var mediaBox: LinearLayout
    private lateinit var tagMediaSupplements: TextView
    private lateinit var mediaSupplementsBox: LinearLayout
    private lateinit var tagAntibiotics: TextView
    private lateinit var antibioticsBox: LinearLayout
    private lateinit var tagWellCount: TextView
    private lateinit var wellCountBox: LinearLayout
    private lateinit var tagCreated: TextView
    private lateinit var createdOnBox: LinearLayout
    private lateinit var tagLastFeed: TextView
    private lateinit var lastFeedBox: LinearLayout
    private lateinit var tagLastWash: TextView
    private lateinit var lastWashBox: LinearLayout
    private lateinit var tagFeedSchedule: TextView
    private lateinit var feedScheduleBox: LinearLayout
    private lateinit var tagWashSchedule: TextView
    private lateinit var washScheduleBox: LinearLayout
    private lateinit var tagTreatmentStart: TextView
    private lateinit var treatmentStartBox: LinearLayout
    private lateinit var parentCellsBox: LinearLayout
    private lateinit var parentCells: TextView
    private lateinit var tagNotes: TextView
    private lateinit var notesBox: LinearLayout

    private lateinit var scanInstructions: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSummaryBinding.inflate(inflater, container, false)


        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(InventoryViewModel::class.java)


    }

    override fun onPause() {
        super.onPause()

        viewModel.setEnabled(false)
        viewModel.resetDevice()
        tagSelect.removeTextChangedListener(mTargetTextChangedListener)
    }

    override fun onResume() {
        super.onResume()

        // Initialize the ViewModel
        viewModel.setCommander(getCommander()!!)
        viewModel.resetDevice()
        getCommander()?.clearResponders()
        viewModel.setEnabled(true)
        viewModel.setPowerLevel(false)
        viewModel.setSendRSSI(true)

        // Setup for the various cell information displays
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
        tagYear = binding.tagYear
        yearBox = binding.yearBox
        tagOwner = binding.tagOwner
        ownerBox = binding.ownerBox
        tagPassage = binding.tagPassage
        passageBox = binding.passageBox
        tagSurface = binding.tagSurface
        surfaceBox = binding.surfaceBox
        tagNumber = binding.tagNumber
        numberBox = binding.numberBox
        tagStatus = binding.tagStatus
        statusBox = binding.statusBox
        tagLocation = binding.tagLocation
        locationBox = binding.locationBox
        tagSupportQuantity = binding.tagSupportQuantity
        supportQuantityBox = binding.supportQuantityBox
        tagMedia = binding.tagMedia
        mediaBox = binding.mediaBox
        tagMediaSupplements = binding.tagMediaSupplements
        mediaSupplementsBox = binding.mediaSupplementsBox
        tagAntibiotics = binding.tagAntibiotics
        antibioticsBox = binding.anitbioticsBox
        tagWellCount = binding.tagWellCount
        wellCountBox = binding.wellCountBox
        tagCreated = binding.tagCreated
        createdOnBox = binding.createdOnBox
        tagLastFeed = binding.tagLastFeed
        lastFeedBox = binding.lastFeedBox
        tagLastWash = binding.tagLastWash
        lastWashBox = binding.lastWashBox
        tagFeedSchedule = binding.tagFeedSchedule
        feedScheduleBox = binding.feedScheduleBox
        tagWashSchedule = binding.tagWashSchedule
        washScheduleBox = binding.washScheduleBox
        tagTreatmentStart = binding.tagTreatmentStart
        treatmentStartBox = binding.treatmentStartBox
        parentCellsBox = binding.parentCellsBox
        parentCells = binding.parentCells
        tagNotes = binding.tagNotes
        notesBox = binding.notesBox

        scanInstructions = binding.scanInstructions
        resetUI()

        // Setup for the select tag field
        tagSelect = binding.tagSelect
        tagSelect.threshold = 1
        tagSelect.addTextChangedListener(mTargetTextChangedListener)

        // Setup for the x on the select tag field
        val selectClear = binding.selectClear
        selectClear.setOnClickListener {
            tagSelect.text.clear()
            resetUI()
            epcRssiMap.clear()
//            updateTagSelect()
        }

        // Setup for the dropdown button for the select field
        val selectDropdown = binding.selectDropdown
        selectDropdown.setOnClickListener {
            tagSelect.showDropDown()
        }


        // Fill in the summary fields with the correct info when a QR code is given
        // This runs when the user scans with the barcode reader
        viewModel.epcNotification.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                if (message.startsWith("BC")) {
                    val bc = message.split(":", limit=2)[1]
                    // ASSUME: barcode will always be hex same as EPC code
                    tagSelect.setText(hexToTagAscii(bc))

                    Log.d("SummaryFragment", "Calling for info for the tag $bc (converted to ${hexToTagAscii(bc)}")

                }
            }
        }

        loadCells()
    }

    override fun onDestroy() {
        super.onDestroy()

        viewModel.setEnabled(false)
    }

    private fun getCommander(): AsciiCommander? {
        return AsciiCommander.sharedInstance()
    }

    // Used to update the list of elements in the dropdown
//    private fun updateTagSelect() {
//        val epcList = epcRssiMap.keys.toList()
//        targetAdapter = ArrayAdapter(
//            requireContext(),
//            R.layout.dropdown_item,
//            epcList
//        )
//        tagSelect.setAdapter(targetAdapter)
//        targetAdapter!!.notifyDataSetChanged()
//    }

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
        tagYear.text = ContextCompat.getString(requireContext(), R.string.tag_year_value)
        yearBox.visibility = View.GONE
        tagOwner.text = ContextCompat.getString(requireContext(), R.string.tag_owner_value)
        ownerBox.visibility = View.GONE
        tagPassage.text = ContextCompat.getString(requireContext(), R.string.tag_cell_passage_value)
        passageBox.visibility = View.GONE
        tagSurface.text = ContextCompat.getString(requireContext(), R.string.tag_surface_value)
        surfaceBox.visibility = View.GONE
        tagNumber.text = ContextCompat.getString(requireContext(), R.string.tag_number_value_in_batch)
        numberBox.visibility = View.GONE
        tagStatus.text = ContextCompat.getString(requireContext(), R.string.tag_status_value)
        statusBox.visibility = View.GONE
        tagLocation.text = ContextCompat.getString(requireContext(), R.string.tag_location_value)
        locationBox.visibility = View.GONE
        tagSupportQuantity.text = ContextCompat.getString(requireContext(), R.string.tag_support_quantity_value)
        supportQuantityBox.visibility = View.GONE
        tagMedia.text = ContextCompat.getString(requireContext(), R.string.tag_media_value)
        mediaBox.visibility = View.GONE
        tagMediaSupplements.text = ContextCompat.getString(requireContext(), R.string.tag_media_supl_value)
        mediaSupplementsBox.visibility = View.GONE
        tagAntibiotics.text = ContextCompat.getString(requireContext(), R.string.tag_antibiotics_value)
        antibioticsBox.visibility = View.GONE
        tagWellCount.text = ContextCompat.getString(requireContext(), R.string.tag_well_count)
        wellCountBox.visibility = View.GONE
        tagCreated.text = ContextCompat.getString(requireContext(), R.string.tag_created_value)
        createdOnBox.visibility = View.GONE
        tagLastFeed.text = ContextCompat.getString(requireContext(), R.string.tag_last_feeding_value)
        lastFeedBox.visibility = View.GONE
        tagLastWash.text = ContextCompat.getString(requireContext(), R.string.tag_last_washing_value)
        lastWashBox.visibility = View.GONE
        tagFeedSchedule.text = ContextCompat.getString(requireContext(), R.string.tag_feed_schedule_value)
        feedScheduleBox.visibility = View.GONE
        tagWashSchedule.text = ContextCompat.getString(requireContext(), R.string.tag_wash_schedule_value)
        washScheduleBox.visibility = View.GONE
        tagTreatmentStart.text = ContextCompat.getString(requireContext(), R.string.tag_treatment_start_value)
        treatmentStartBox.visibility = View.GONE
        parentCellsBox.visibility = View.GONE
        parentCells.text = ContextCompat.getString(requireContext(), R.string.tag_parent_cells_value)
        tagNotes.text = ContextCompat.getString(requireContext(), R.string.tag_notes_value)
        notesBox.visibility = View.GONE

        scanInstructions.visibility = View.VISIBLE
    }

    // Function that will handle the API call to get the cells from the live database
    private fun loadCells() {
        Log.d("SummaryFragment", "Load cells called")
        lifecycleScope.launch {
            for (i in 1..2) {
                try {
                    Log.d("SummaryFragment", "Trying the get cells from api")
                    // Getting the cell info from the API
                    val response = DataRepository.getLiveCells()
                    if (response.isSuccessful && response.body() != null) {
                        mCellsList = response.body()!!.cells as ArrayList<String>
                        targetAdapter = ArrayAdapter(
                            requireContext(),
                            R.layout.dropdown_item,
                            mCellsList
                        )
                        tagSelect.setAdapter(targetAdapter)
                        targetAdapter!!.notifyDataSetChanged()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("SummaryFragment", "Failing to get cell info")
                    if (i < 2) delay(1000)
                }
            }
        }
    }

    // This gets the cell from the API by the given cellID string
    private fun getCell(cellID: String) {
        // Add the info from the database
        viewLifecycleOwner.lifecycleScope.launch {
            for (i in 1..2) {
                try {
                    Log.e("SummaryFragment", "Making the response")
                    // Getting the cell info from the API
                    val response = DataRepository.getCellByID(cellID)
                    if (response.isSuccessful) {
                        Log.e("SummaryFragment", "Got it: ${response.body()!!}")
                        cellItem = response.body()!!
                        withContext(Dispatchers.Main) {
                            changeUI(cellItem!!)
                        }
                        break
                    } else {
                        cellItem = null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("SummaryFragment", "Failing to get cell info")
                    if (i < 2) delay(1000)
                }
            }
        }
    }

    // This is used to change all of the UI text elements for the cell item
    private fun changeUI(cellItem: CellItem) {
        scanInstructions.visibility = View.GONE
        if (substitutions != null && substitutions.subs != null) {
            // This contains all of the text elements used in the summary page and the field to look under for substitutions
            // NONE is used fore datetime fields that are sent over formatted by the API already
            val summaryItems = listOf(
                Triple(typeBox, tagType, "type"),
                Triple(genotypeBox,tagGenotype, "genotype"),
                Triple(distNumBox, tagDistNum, "distNum"),
                Triple(cellTypeBox, tagCellType, "cellType"),
                Triple(genemodBox, tagGenemod, "genemod"),
                Triple(gene1Box, tagGene1, "gene1"),
                Triple(gene2Box, tagGene2, "gene2"),
                Triple(resistanceBox, tagResistance, "resistance"),
                Triple(cloneBox, tagClone, "clone"),
                Triple(yearBox, tagYear, "year"),
                Triple(ownerBox, tagOwner, "owner"),
                Triple(passageBox, tagPassage, "passage"),
                Triple(surfaceBox, tagSurface, "surface"),
                Triple(numberBox, tagNumber, "number"),
                Triple(statusBox, tagStatus, "status"),
                Triple(locationBox, tagLocation, "location"),
                Triple(supportQuantityBox, tagSupportQuantity, "supportQuantity"),
                Triple(mediaBox, tagMedia, "media"),
                Triple(mediaSupplementsBox, tagMediaSupplements, "mediaSupplements"),
                Triple(antibioticsBox, tagAntibiotics, "antibiotics"),
                Triple(wellCountBox, tagWellCount, "wellCount"),
                Triple(createdOnBox, tagCreated, "creationDate"),
                Triple(lastFeedBox, tagLastFeed, "lastWash"),
                Triple(lastWashBox, tagLastWash, "lastFeed"),
                Triple(feedScheduleBox, tagFeedSchedule, "feedingSchedule"),
                Triple(washScheduleBox, tagWashSchedule, "washSchedule"),
                Triple(treatmentStartBox, tagTreatmentStart, "treatmentStart"),
                Triple(parentCellsBox, parentCells, "parentCells"),
                Triple(notesBox, tagNotes, "notes"))
            val regex = "---(.*?)---".toRegex()

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
                    if (givenText != null) {
                        // Make the text item visible as it has a valid thing to show
                        box.visibility = View.VISIBLE

                        // Loop through the substitution options for the field
                        val possibleSubs = substitutions.subs[field]
                        var showText = givenText
                        for ((key, value) in possibleSubs!!) {
                            // If the given text from the API equals a substitution then substitute to the correct value like "N" turns to "None"
                            if (givenText == key) {
                                Log.d("Substitution", "Replacing $givenText with $value")
                                showText = value
                                break
                            }
                        }
                        text.setText(showText.toString())
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
                    // If the value was given by the API (meaning it wasnt empty or null in the database)
                    if (givenText != null) {
                        // Make the text item visible as it has a valid thing to show
                        box.visibility = View.VISIBLE
                        text.setText(regex.replace(text.text, givenText.toString()))
                    }
                }
            }
        } else {
            Log.e("SummaryFragment", "Substitutions is null!")
        }
    }

    // Runs this if the user changes the owner/type/surface filter text in any way
    private val mTargetTextChangedListener = object: TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            // Runs this when the user stops changing the text field
            val text = s.toString()
            if (text.length == 14 || text.length == 16) {
                resetUI()
                tagHex.setText(tagAsciiToHex(text))
                tagAscii.setText(text)
                getCell(text)
            } else if (text.length == 24) {
                resetUI()
                tagHex.setText(text)
                tagAscii.setText(hexToTagAscii(text))
                getCell(hexToTagAscii(text))
            } else {
                Log.e("SummaryFragment", "Was given an invalid cellID! $text")
            }
        }
    }
}