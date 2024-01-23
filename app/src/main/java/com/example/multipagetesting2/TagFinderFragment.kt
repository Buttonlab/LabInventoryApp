package com.example.multipagetesting2

import android.media.SoundPool
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.multipagetesting2.databinding.FragmentTagFinderBinding
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class TagFinderFragment : Fragment() {

    private var _binding: FragmentTagFinderBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // ViewModel containing code for interfacing with the reader
    private lateinit var viewModel: TagFinderViewModel

    // The controller for the sounds
    private var soundPool: SoundPool? = null
    private var soundLowest: Int? = null
    private var soundLow: Int? = null
    private var soundMedium: Int? = null
    private var soundHigh: Int? = null
    private var soundHighest: Int? = null

    // The EPC code text field and dropdown button
    private lateinit var mTargetTagEditText: AutoCompleteTextView
    private lateinit var mTargetTagClear: ImageButton
    private lateinit var mTargetTagDropdown: ImageButton

    // The signal strength display bars
    private lateinit var sBarC: View
    private lateinit var sBarR1: View
    private lateinit var sBarL1: View
    private lateinit var sBarR2: View
    private lateinit var sBarL2: View
    private lateinit var sBarR3: View
    private lateinit var sBarL3: View

    // The primary/immortal text field and dropdown button
    private lateinit var typeFilter: AutoCompleteTextView
    private lateinit var typeClear: ImageButton
    private lateinit var typeDropdown: ImageButton

    // The genotype text field and dropdown button
    private lateinit var genotypeLayout: LinearLayout
    private lateinit var genotypeFilter: AutoCompleteTextView
    private lateinit var genotypeClear: ImageButton
    private lateinit var genotypeDropdown: ImageButton

    // The dist1 text field and dropdown button
    private lateinit var distNumLayout: LinearLayout
    private lateinit var distNumFilter: AutoCompleteTextView
    private lateinit var distNumClear: ImageButton
    private lateinit var distNumDropdown: ImageButton

    // The dist2 text field and dropdown button
    private lateinit var surfaceLayout: LinearLayout
    private lateinit var surfaceFilter: AutoCompleteTextView
    private lateinit var surfaceClear: ImageButton
    private lateinit var surfaceDropdown: ImageButton

    // The cellType text field and dropdown button
    private lateinit var cellTypeLayout: LinearLayout
    private lateinit var cellTypeFilter: AutoCompleteTextView
    private lateinit var cellTypeClear: ImageButton
    private lateinit var cellTypeDropdown: ImageButton

    // The genemod text field and dropdown button
    private lateinit var genemodLayout: LinearLayout
    private lateinit var genemodFilter: AutoCompleteTextView
    private lateinit var genemodClear: ImageButton
    private lateinit var genemodDropdown: ImageButton

    // The resistance text field and dropdown button
    private lateinit var resistanceLayout: LinearLayout
    private lateinit var resistanceFilter: AutoCompleteTextView
    private lateinit var resistanceClear: ImageButton
    private lateinit var resistanceDropdown: ImageButton

    // Holding the response of the API call
    private var uniqueGenotypes: ArrayList<String> = arrayListOf()
    private var uniqueDistNums :ArrayList<String> = arrayListOf()
    private var uniqueSurfaces :ArrayList<String> = arrayListOf()
    private var uniqueCellTypes :ArrayList<String> = arrayListOf()
    private var uniqueGenemods :ArrayList<String> = arrayListOf()
    private var uniqueResistances :ArrayList<String> = arrayListOf()

    // Adapter for the main target dropdown
    private var targetAdapter: ArrayAdapter<String>? = null

    // Storing the cells list from the API
    private lateinit var cellsList: ArrayList<String>
    private lateinit var filteredList: ArrayList<String>

    // Storing the substitutions from the API
    val substitutions = DataRepository.substitutions


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        _binding = FragmentTagFinderBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(TagFinderViewModel::class.java)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()

        // Display density for finding the required height of an item
        val displayDensity = resources.displayMetrics.density

        // Setup for the viewModel
        viewModel.setCommander(getCommander()!!)
        getCommander()?.clearResponders()
        viewModel.cleanup()
        viewModel.setEnabled(true)

        // Setup for the epc text text field and dropdown
        mTargetTagEditText = binding.targetEPC
        mTargetTagEditText.addTextChangedListener(mTargetTagEditTextChangedListener)
        mTargetTagEditText.onFocusChangeListener = mTargetTagFocusChangedListener
        mTargetTagEditText.dropDownHeight = (40 * displayDensity).toInt() * 10 // 40 is the height of the dropdown element in dp, 10 is the num of them to show
        mTargetTagClear = binding.targetClear
        mTargetTagClear.setOnClickListener { mTargetTagEditText.text.clear() }
        mTargetTagDropdown = binding.epcDropdown
        mTargetTagDropdown.setOnClickListener { mTargetTagEditText.showDropDown() }


        // Setup for the signal strength display bars
        sBarC = binding.sBarC
        sBarR1 = binding.sBarR1
        sBarL1 = binding.sBarL1
        sBarR2 = binding.sBarR2
        sBarL2 = binding.sBarL2
        sBarR3 = binding.sBarR3
        sBarL3 = binding.sBarL3

        // Setup for the Primary/Immortal filter
        typeFilter = binding.typeFilter
        typeFilter.addTextChangedListener(filterTextChangedListener)
        typeClear = binding.typeClear
        typeClear.setOnClickListener { typeFilter.text.clear() }
        typeDropdown = binding.typeDropdown
        typeDropdown.setOnClickListener { typeFilter.showDropDown() }
        val possibleTypes = arrayListOf("Primary", "Immortal")
        val typeAdapter = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_item,
            possibleTypes)
        typeFilter.setAdapter(typeAdapter)
        typeAdapter.notifyDataSetChanged()

        // Setup for the genotype filter
        genotypeLayout = binding.genotypeLayout
        genotypeLayout.visibility = View.GONE
        genotypeFilter = binding.genotypeFilter
        genotypeFilter.addTextChangedListener(filterTextChangedListener)
        genotypeClear = binding.genotypeClear
        genotypeClear.setOnClickListener { genotypeFilter.text.clear() }
        genotypeDropdown = binding.genotypeDropdown
        genotypeDropdown.setOnClickListener { genotypeFilter.showDropDown() }

        // Setup for the distNum filter
        distNumLayout = binding.distNumLayout
        distNumLayout.visibility = View.GONE
        distNumFilter = binding.distNumFilter
        distNumFilter.addTextChangedListener(filterTextChangedListener)
        distNumClear = binding.distNumClear
        distNumClear.setOnClickListener { distNumFilter.text.clear() }
        distNumDropdown = binding.distNumDropdown
        distNumDropdown.setOnClickListener { distNumFilter.showDropDown() }


        // Setup for the surface filter
        surfaceLayout = binding.surfaceLayout
        surfaceLayout.visibility = View.GONE
        surfaceFilter = binding.surfaceFilter
        surfaceFilter.addTextChangedListener(filterTextChangedListener)
        surfaceClear = binding.surfaceClear
        surfaceClear.setOnClickListener { surfaceFilter.text.clear() }
        surfaceDropdown = binding.surfaceDropdown
        surfaceDropdown.setOnClickListener { surfaceFilter.showDropDown() }


        // Setup for the cellType filter
        cellTypeLayout = binding.cellTypeLayout
        cellTypeLayout.visibility = View.GONE
        cellTypeFilter = binding.cellTypeFilter
        cellTypeFilter.addTextChangedListener(filterTextChangedListener)
        cellTypeClear = binding.cellTypeClear
        cellTypeClear.setOnClickListener { cellTypeFilter.text.clear() }
        cellTypeDropdown = binding.cellTypeDropdown
        cellTypeDropdown.setOnClickListener { cellTypeFilter.showDropDown() }

        // Setup for the genemod filter
        genemodLayout = binding.genemodLayout
        genemodLayout.visibility = View.GONE
        genemodFilter = binding.genemodFilter
        genemodFilter.addTextChangedListener(filterTextChangedListener)
        genemodClear = binding.genemodClear
        genemodClear.setOnClickListener { genemodFilter.text.clear() }
        genemodDropdown = binding.genemodDropdown
        genemodDropdown.setOnClickListener { genemodFilter.showDropDown() }

        // Setup for the resistance filter
        resistanceLayout = binding.resistanceLayout
        resistanceLayout.visibility = View.GONE
        resistanceFilter = binding.resistanceFilter
        resistanceFilter.addTextChangedListener(filterTextChangedListener)
        resistanceClear = binding.resistanceClear
        resistanceClear.setOnClickListener { resistanceFilter.text.clear() }
        resistanceDropdown = binding.resistanceDropdown
        resistanceDropdown.setOnClickListener { resistanceFilter.showDropDown() }


        // Setting the responders for the signal strength values
        viewModel.setRawSignalDelegate { level ->
            var percentage = if (level != null) asPercentage(level) else 0
            if (!viewModel.isScanning()) { // Set the percentage to 0 if the user is no longer scanning to reset the display
                percentage = 0
                soundPool?.autoPause()
            }
            Log.d("TagFinder", "Signal $percentage %, level: $level")
            // Call displaySignalStrength here, ensure it is in the UI thread
            activity?.runOnUiThread {
                displaySignalStrength(percentage)
            }
        }
        viewModel.setPercentageSignalDelegate { level ->
            val percentage = if (level != null) level else 0
            // Call displaySignalStrength here, ensure it is in the UI thread
            Log.d("TagFinder", "Signal $percentage %")
            activity?.runOnUiThread {
                displaySignalStrength(percentage)
            }
        }
        viewModel.setEnabled(true)

        soundPool = SoundPool.Builder().setMaxStreams(1).build()
        soundLowest = soundPool!!.load(context, R.raw.lowest, 1)
        soundLow = soundPool!!.load(context, R.raw.low, 1)
        soundMedium = soundPool!!.load(context, R.raw.medium, 1)
        soundHigh = soundPool!!.load(context, R.raw.high, 1)
        soundHighest = soundPool!!.load(context, R.raw.highest, 1)

        // Calling the API cells function
        loadCells()
    }

    override fun onPause() {
        super.onPause()
        viewModel.setEnabled(false)
        viewModel.cleanup()
        mTargetTagEditText.removeTextChangedListener(mTargetTagEditTextChangedListener)
        soundPool?.release()
        soundPool = null
    }

    protected fun getCommander(): AsciiCommander? {
        return AsciiCommander.sharedInstance()
    }

    // Runs this if the user changes the target text in any way
    private val mTargetTagEditTextChangedListener = object: TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            // Runs this when the user stops changing the text field
            val text = s.toString()
            if (text.length in intArrayOf(14, 16, 24)) { // If the given epc code is a valid length
                viewModel.setTargetTagEpc(text)
                viewModel.updateTarget()
            }
        }

    }

    // Runs when the user leaves the target text edit box
    private val mTargetTagFocusChangedListener = View.OnFocusChangeListener { view, hasFocus ->
        if (!hasFocus) {
            if (mTargetTagEditText.text.toString().length in intArrayOf(14, 16, 24)) { // If the given epc code is a valid length
                viewModel.setTargetTagEpc(mTargetTagEditText.text.toString())
                viewModel.updateTarget()
            }
        }
    }

    // Runs this if the user changes the filter text in any way
    private val filterTextChangedListener = object: TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            // Runs this when the user stops changing the text field
            updateFilters()
        }
    }

    // Takes a db RSSI reading and converts it to percentage
    private fun asPercentage(value: Int): Int {
        // Assumed max and min from the SDK examples
        var mRangeMinimum = -70
        var mRangeMaximum = -35

        if (value < mRangeMinimum) {
            mRangeMinimum = value
        }
        if (value > mRangeMaximum) {
            mRangeMaximum = value
        }
        return (100 * (value - mRangeMinimum) / (mRangeMaximum - mRangeMinimum))
    }

    // Function to show signal strength on the display and play the sounds
    private fun displaySignalStrength(strength: Int) {
        val gray = ContextCompat.getColor(requireContext(), R.color.colorGray)
        val red = ContextCompat.getColor(requireContext(), R.color.colorRed)
        val orange = ContextCompat.getColor(requireContext(), R.color.colorOrange)
        val green = ContextCompat.getColor(requireContext(), R.color.colorGreen)
        var streamID: Int = 0
        when {
            strength >= 90 -> {
                // Make #3 red, #2 and #1 orange, and center green
                sBarR3.setBackgroundColor(red)
                sBarL3.setBackgroundColor(red)
                sBarR2.setBackgroundColor(orange)
                sBarL2.setBackgroundColor(orange)
                sBarR1.setBackgroundColor(orange)
                sBarL1.setBackgroundColor(orange)
                sBarC.setBackgroundColor(green)

                streamID = soundPool?.play(soundHighest!!, 1.0f, 1.0f, 0, 0, 1.0f)!!
            }
            strength >= 80 -> {
                // Make #3 red, #2 and #1 and center orange
                sBarR3.setBackgroundColor(red)
                sBarL3.setBackgroundColor(red)
                sBarR2.setBackgroundColor(orange)
                sBarL2.setBackgroundColor(orange)
                sBarR1.setBackgroundColor(orange)
                sBarL1.setBackgroundColor(orange)
                sBarC.setBackgroundColor(orange)

                streamID = soundPool?.play(soundHigh!!, 1.0f, 1.0f, 0, 0, 1.0f)!!
            }
            strength >= 60 -> {
                // Make #3 red, #2 and #1 orange
                sBarR3.setBackgroundColor(red)
                sBarL3.setBackgroundColor(red)
                sBarR2.setBackgroundColor(orange)
                sBarL2.setBackgroundColor(orange)
                sBarR1.setBackgroundColor(orange)
                sBarL1.setBackgroundColor(orange)
                sBarC.setBackgroundColor(gray)

                streamID = soundPool?.play(soundMedium!!, 1.0f, 1.0f, 0, 0, 1.0f)!!
            }
            strength >= 30 -> {
                // Make #3 red and #2 orange
                sBarR3.setBackgroundColor(red)
                sBarL3.setBackgroundColor(red)
                sBarR2.setBackgroundColor(orange)
                sBarL2.setBackgroundColor(orange)
                sBarR1.setBackgroundColor(gray)
                sBarL1.setBackgroundColor(gray)
                sBarC.setBackgroundColor(gray)

                streamID = soundPool?.play(soundLow!!, 1.0f, 1.0f, 0, 0, 1.0f)!!
            }
            strength >= 5 -> {
                // Make #3 red
                sBarR3.setBackgroundColor(red)
                sBarL3.setBackgroundColor(red)
                sBarR2.setBackgroundColor(gray)
                sBarL2.setBackgroundColor(gray)
                sBarR1.setBackgroundColor(gray)
                sBarL1.setBackgroundColor(gray)
                sBarC.setBackgroundColor(gray)

                streamID = soundPool?.play(soundLowest!!, 1.0f, 1.0f, 0, 0, 1.0f)!!
            }
            else -> {
                // Make all gray
                sBarR3.setBackgroundColor(gray)
                sBarL3.setBackgroundColor(gray)
                sBarR2.setBackgroundColor(gray)
                sBarL2.setBackgroundColor(gray)
                sBarR1.setBackgroundColor(gray)
                sBarL1.setBackgroundColor(gray)
                sBarC.setBackgroundColor(gray)

                soundPool?.autoPause()
            }
        }
    }

    // Function that will handle the API call to get the cells from the live database
    private fun loadCells() {
        lifecycleScope.launch {
            for (i in 1..2) {
                try {
                    // Getting the cell info from the API
                    val response = DataRepository.getLiveCells()
                    if (response.isSuccessful && response.body() != null) {
                        cellsList = response.body()!!.cells as ArrayList<String>

                        for (item in cellsList) { // Loop through all cells retrieved from the API
                            Log.d("TagFinderFragment", "Cell: $item")
                            val type = item.first().toString()
                            if (type == "1" || type == "3") { // If the cell is primary
                                val genotype = substitutions?.subs?.get("genotype")?.get(item.elementAt(1).toString()) ?: item.elementAt(1).toString()
                                if (!uniqueGenotypes.contains(genotype)) uniqueGenotypes.add(genotype)
                                val distNum = item.substring(2,4).toInt(36).toString()
                                if (!uniqueDistNums.contains(distNum)) uniqueDistNums.add(distNum)
                                val surface = substitutions?.subs?.get("surface")?.get(item.substring(8,9)) ?: item.substring(8,9)
                                if (!uniqueSurfaces.contains(surface)) uniqueSurfaces.add(surface)
                            }else if (type == "2" || type == "4") { // If the cell is immortal
                                val cellType = substitutions?.subs?.get("cellType")?.get(item.elementAt(1).toString()) ?: item.elementAt(1).toString()
                                if (!uniqueCellTypes.contains(cellType)) uniqueCellTypes.add(cellType)
                                val genemod = substitutions?.subs?.get("genemod")?.get(item.elementAt(2).toString()) ?: item.elementAt(2).toString()
                                if (!uniqueGenemods.contains(genemod)) uniqueGenemods.add(genemod)
                                val resistance = substitutions?.subs?.get("resistance")?.get(item.elementAt(5).toString()) ?: item.elementAt(5).toString()
                                if (!uniqueResistances.contains(resistance)) uniqueResistances.add(resistance)
                            } else {
                                Log.e("TagFinderFragment", "Unexpected type encountered! Cell: $item")
                            }
                        }

                        // Updating the AutoCompleteTextViews dropdown boxes
                        filteredList = ArrayList(cellsList)
                        targetAdapter = ArrayAdapter(
                            requireContext(),
                            R.layout.dropdown_item,
                            filteredList)
                        mTargetTagEditText.setAdapter(targetAdapter)
                        targetAdapter!!.notifyDataSetChanged()

                        val genotypeAdapter = ArrayAdapter(
                            requireContext(),
                            R.layout.dropdown_item,
                            uniqueGenotypes
                        )
                        genotypeFilter.setAdapter(genotypeAdapter)
                        genotypeAdapter.notifyDataSetChanged()

                        val distNumAdapter = ArrayAdapter(
                            requireContext(),
                            R.layout.dropdown_item,
                            uniqueDistNums
                        )
                        distNumFilter.setAdapter(distNumAdapter)
                        distNumAdapter.notifyDataSetChanged()

                        val surfaceAdapter = ArrayAdapter(
                            requireContext(),
                            R.layout.dropdown_item,
                            uniqueSurfaces
                        )
                        surfaceFilter.setAdapter(surfaceAdapter)
                        surfaceAdapter.notifyDataSetChanged()

                        val cellTypeAdapter = ArrayAdapter(
                            requireContext(),
                            R.layout.dropdown_item,
                            uniqueCellTypes
                        )
                        cellTypeFilter.setAdapter(cellTypeAdapter)
                        cellTypeAdapter.notifyDataSetChanged()

                        val genemodAdapter = ArrayAdapter(
                            requireContext(),
                            R.layout.dropdown_item,
                            uniqueGenemods
                        )
                        genemodFilter.setAdapter(genemodAdapter)
                        genemodAdapter.notifyDataSetChanged()

                        val resistanceAdapter = ArrayAdapter(
                            requireContext(),
                            R.layout.dropdown_item,
                            uniqueResistances
                        )
                        resistanceFilter.setAdapter(resistanceAdapter)
                        resistanceAdapter.notifyDataSetChanged()
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("SummaryFragment", "Failing to get cell info")
                    if (i < 2) delay(1000)
                }
            }
        }
    }

    // Function to update the options to select in the main tag select based off the filters
    private fun updateFilters() {
        if (targetAdapter != null) {
            if (typeFilter.text.toString() == "Primary") {
                cellTypeLayout.visibility = View.GONE
                genemodLayout.visibility = View.GONE
                resistanceLayout.visibility = View.GONE
                genotypeLayout.visibility = View.VISIBLE
                distNumLayout.visibility = View.VISIBLE
                surfaceLayout.visibility = View.VISIBLE

                // Getting the genotype filter
                var tempText = genotypeFilter.text.toString()
                val genotypeSettings = tempText.takeIf {
                    it.isNotEmpty() && uniqueGenotypes.contains(it)
                } ?: "INVALID"

                // Getting the distNum filter
                tempText = distNumFilter.text.toString()
                val distNumSettings = tempText.takeIf {
                    it.isNotEmpty() && uniqueDistNums.contains(it)
                } ?: "INVALID"

                // Getting the surface filter
                tempText = surfaceFilter.text.toString()
                val surfaceSettings = tempText.takeIf {
                    it.isNotEmpty() && uniqueSurfaces.contains(it)
                } ?: "INVALID"

                // Filtering the list of possible cells
                val newList = ArrayList<String>()
                targetAdapter!!.clear()
                targetAdapter!!.notifyDataSetChanged()
                for (item in cellsList) {
                    val typeMatch = getType(item) == "1" || getType(item) == "3"
                    val genotypeMatch = (getGenotype(item) == (getSubKey("genotype", genotypeSettings) ?: "")) || genotypeSettings == "INVALID"
                    val distNumMatch = (getDistNum(item) == (distNumSettings ?: "")) || distNumSettings == "INVALID"
                    val surfaceMatch = (getSurface(item) == (getSubKey("surface", surfaceSettings) ?: "")) || surfaceSettings == "INVALID"

                    if (typeMatch && genotypeMatch && distNumMatch && surfaceMatch) {
                        newList.add(item)
                        targetAdapter!!.add(item)
                    }
                }

                // Updating the list of tags in the dropdown box
                //targetAdapter!!.clear()
                //targetAdapter!!.addAll(newList)
                targetAdapter!!.notifyDataSetChanged()
            } else if (typeFilter.text.toString() == "Immortal") {
                genotypeLayout.visibility = View.GONE
                distNumLayout.visibility = View.GONE
                surfaceLayout.visibility = View.GONE
                cellTypeLayout.visibility = View.VISIBLE
                genemodLayout.visibility = View.VISIBLE
                resistanceLayout.visibility = View.VISIBLE

                // Getting the cellType filter
                var tempText = cellTypeFilter.text.toString()
                val cellTypeSettings = tempText.takeIf {
                    it.isNotEmpty() && uniqueCellTypes.contains(it)
                } ?: "INVALID"

                // Getting the genemod filter
                tempText = genemodFilter.text.toString()
                val genemodSettings = tempText.takeIf {
                    it.isNotEmpty() && uniqueGenemods.contains(it)
                } ?: "INVALID"

                // Getting the resistance filter
                tempText = resistanceFilter.text.toString()
                val resistanceSettings = tempText.takeIf {
                    it.isNotEmpty() && uniqueResistances.contains(it)
                } ?: "INVALID"

                // Filtering the list of possible cells
                val newList = ArrayList<String>()
                targetAdapter!!.clear()
                targetAdapter!!.notifyDataSetChanged()
                for (item in cellsList) {
                    val typeMatch = getType(item) == "2" || getType(item) == "4"
                    val cellTypeMatch = (getCellType(item) == (getSubKey("cellType", cellTypeSettings) ?: "")) || cellTypeSettings == "INVALID"
                    val genemodMatch = (getGenemod(item) == (getSubKey("genemod", genemodSettings) ?: "")) || genemodSettings == "INVALID"
                    val resistanceMatch = (getResistance(item) == (getSubKey("resistance", resistanceSettings) ?: "")) || resistanceSettings == "INVALID"

                    if (typeMatch && cellTypeMatch && genemodMatch && resistanceMatch) {
                        newList.add(item)
                        targetAdapter!!.add(item)
                    }
                }

                // Updating the list of tags in the dropdown box
                targetAdapter!!.notifyDataSetChanged()
            } else {
                targetAdapter!!.clear()
                targetAdapter!!.addAll(cellsList)
                targetAdapter!!.notifyDataSetChanged()
            }
        }
    }

    // Functions for getting various parts of an ascii representation tag
    private fun getType(ascii: String): String { return ascii.elementAt(0).toString() }
    private fun getGenotype(ascii: String): String { return ascii.elementAt(1).toString() }
    private fun getDistNum(ascii: String): String { return ascii.substring(2,4).toInt(36).toString() }
    private fun getSurface(ascii: String): String { return ascii.elementAt(8).toString() }
    private fun getCellType(ascii: String): String { return ascii.elementAt(1).toString() }
    private fun getGenemod(ascii: String): String { return ascii.elementAt(2).toString() }
    private fun getResistance(ascii: String): String { return ascii.elementAt(5).toString() }

    // Function to get the key for a value in the substitutions
    private fun getSubKey(field: String, findValue: String): String? {
        if (substitutions?.subs?.get(field) != null) {
            for ((key, value) in substitutions.subs[field]!!) {
                if (value == findValue) {
                    return key
                }
            }
        }
        return null
    }

}