package com.example.multipagetesting2

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.multipagetesting2.databinding.FragmentTagActionBinding
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * A simple [Fragment] subclass as the third destination in the navigation.
 */
class TagActionFragment : Fragment() {

    private var _binding: FragmentTagActionBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // Creating an instance of the InventoryViewModel
    private lateinit var viewModel: InventoryViewModel

    // The text box and it's buttons
    private lateinit var actionSelect: AutoCompleteTextView
    private lateinit var actionClear: ImageButton
    private lateinit var actionDropdown: ImageButton

    // The RecyclerView that displays the list of tags
    private lateinit var rvTags: RecyclerView

    // The touchListener used to enable touch actions on the tags
    private var touchListener: RecyclerTouchListener? = null

    // Variables for the data
    private var tagList = ArrayList<String>()
    private val uniqueTags: MutableSet<String> = mutableSetOf()

    // Adapter used for the tagList display
    private lateinit var tagAdapter: RfidTagsAdapter

    // Used for the fields folder
    private lateinit var toggleFolder: Button
    private lateinit var fieldsFolder: LinearLayout

    // The textView for the tag count
    private lateinit var tagCounter: TextView

    // Holds the chosen action and its fields
    private var chosenAction: String? = null
    private var fields: MutableMap<String, String>? = null

    // Storing the substitutions from the API
    val substitutions = DataRepository.substitutions

    // Storing the substitutions from the API
    val actions = DataRepository.actions

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTagActionBinding.inflate(inflater, container, false)
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
        viewModel.epcNotification.removeObservers(viewLifecycleOwner)
        viewModel.epcNotification.postValue(null)
        actionSelect.removeTextChangedListener(mActionChangedListener)
        if (touchListener != null) {
            rvTags.removeOnItemTouchListener(touchListener!!)
            touchListener = null
        }
    }

    override fun onResume() {
        super.onResume()

        // Initialize the ViewModel
        viewModel.setCommander(getCommander()!!)
        viewModel.resetDevice()
        getCommander()?.clearResponders()
        viewModel.setEnabled(true)
        viewModel.setUnique(false)

        // Setup the tag count
        tagCounter = binding.tagCount

        // Setup the recyclerView
        tagAdapter = RfidTagsAdapter(ArrayList())
        rvTags = binding.rvTags
        rvTags.layoutManager = LinearLayoutManager(requireActivity())
        rvTags.adapter = tagAdapter
        if (touchListener == null) {
            touchListener = RecyclerTouchListener(requireContext(), rvTags, object : RecyclerTouchListener.ClickListener {
                    override fun onClick(view: View, position: Int) {
                        Toast.makeText(requireContext(), "Tag value copied!", Toast.LENGTH_SHORT).show()
                    }
                    override fun onLongClick(view: View, position: Int) {
                        val removeTag = tagList[position]
                        tagList.removeAt(position)
                        tagAdapter.removeItem(removeTag)
                        viewModel.removeFromSet(removeTag)
                        tagCounter.setText((Integer.parseInt(tagCounter.text.toString()) - 1).toString())
                    }
                })
            rvTags.addOnItemTouchListener(touchListener!!)
        }

        // Setup the action select
        actionSelect = binding.actionSelect
        actionClear = binding.actionClear
        actionDropdown = binding.actionDropdown
        actionClear.setOnClickListener {
            actionSelect.text.clear()
            resetFields()
        }
        actionDropdown.setOnClickListener {
            actionSelect.showDropDown()
        }
        actionSelect.addTextChangedListener(mActionChangedListener)
        val actionsList = actions?.acts?.keys?.toList()?.filter { !it.equals("Inventory", true) }
        if (actionsList != null) {
            val actionAdapter = ArrayAdapter(
                requireContext(),
                R.layout.dropdown_item,
                actionsList)
            actionSelect.setAdapter(actionAdapter)
        }

        // Setup for the folder to contain the field inputs
        toggleFolder = binding.toggleFolder
        fieldsFolder = binding.customFieldsFolder
        toggleFolder.visibility = View.GONE
        fieldsFolder.visibility = View.GONE
        toggleFolder.setText(R.string.tap_to_show_fields)
        toggleFolder.setOnClickListener {
            if (fieldsFolder.visibility == View.GONE) {
                fieldsFolder.visibility = View.VISIBLE
                toggleFolder.setText(R.string.tap_to_hide_fields)
            } else {
                fieldsFolder.visibility = View.GONE
                toggleFolder.setText(R.string.tap_to_show_fields)
            }
        }

        // Update the values on screen when the user scans
        val possibleTypes = substitutions?.subs?.get("type")?.keys?.toList()
        viewModel.epcNotification.observe(viewLifecycleOwner) { message ->
            if (message != null) {

                when {
                    message.startsWith("BC") -> {
                        val splitMsg = message.split(":", limit = 2)

                        var newMsg = ""
                        try { // Convert to ascii if possible/necessary
                            newMsg = if (actions?.acts?.containsKey(splitMsg[1]) == true) { splitMsg[1] } else { viewModel.hexToTagAscii(splitMsg[1]) }
                        } catch (e: Exception) {
                            newMsg = splitMsg[1]
                        }

                        if (actionsList?.contains(newMsg) == true) { // If the scanned barcode is an action update the action text box
                            actionSelect.setText(newMsg)

                        } else if (!(splitMsg[1].startsWith("21") && splitMsg[1].endsWith("21"))) { // This runs if the code is not an action or location
                            if (!uniqueTags.contains(message)) {
                                uniqueTags.add(message)

                                if (newMsg.all { (it.isLetterOrDigit() || it.isWhitespace()) } && newMsg.isNotEmpty()) { // Don't display a tag if it does not contain valid characters

                                    var inDB = true

                                    if ((possibleTypes != null) && (!possibleTypes.contains(newMsg.first().toString()))) {
                                        inDB = false // Check if the first letter is a valid type in the database to ensure it is a valid tag
                                    }
                                    if ((newMsg.length == 14 || newMsg.length == 16) && inDB) {
                                        tagList.add("BC:$newMsg")
                                        tagCounter.setText("${tagList.size}")
                                        Log.d("TagActionFragment", "Tag count: ${tagList.size}")
                                        tagAdapter.updateData(tagList)
                                    }
                                }
                            }
                        }
                    }
                    message.startsWith("EPC") -> {
                        if (!uniqueTags.contains(message)) {
                            uniqueTags.add(message)

                            val splitMsg = message.split(":", limit = 2)
                            if (!(splitMsg[1].startsWith("21") && splitMsg[1].endsWith("21"))) { // Don't display location tags and stop if empty
                                val newMsg = viewModel.hexToTagAscii(splitMsg[1])
                                if (newMsg.all { (it.isLetterOrDigit() || it.isWhitespace()) } && newMsg.isNotEmpty()) { // Don't display a tag if it does not contain valid characters

                                    var inDB = true

                                    if ((possibleTypes != null) && (!possibleTypes.contains(newMsg.first().toString()))) {
                                        inDB = false // Check if the first letter is a valid type in the database to ensure it is a valid tag
                                    }
                                    if ((newMsg.length == 14 || newMsg.length == 16) && inDB) {
                                        tagList.add("EPC:$newMsg")
                                        tagCounter.setText("${tagList.size}")
                                        Log.d("TagActionFragment", "Tag count: ${tagList.size}")
                                        tagAdapter.updateData(tagList)
                                    }

                                }

                            }
                        }
                    }
                }
            }
        }

        // Setup the clear inventory button
        val clearInv = binding.clearInv
        clearInv.setOnClickListener {
            tagAdapter.clearData()
            viewModel.clearUniques()
            tagList.clear()
            uniqueTags.clear()
            tagCounter.text = "---"
        }

        // Setup the power level toggle
        val togglePwr = binding.togglePowerLevel
        val colorOn = ContextCompat.getColor(requireContext(), R.color.carolinaBlue)
        val colorOff = ContextCompat.getColor(requireContext(), R.color.basinSlate)
        viewModel.setPowerLevel(true)
        togglePwr.background.setTint(colorOn)
        togglePwr.isChecked = false
        togglePwr.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // If on High go to Low
                if (getCommander() != null){
                    viewModel.setPowerLevel(false)
                    togglePwr.background.setTint(colorOff)
                }
            } else {
                // If on Low go to High
                if (getCommander() != null) {
                    viewModel.setPowerLevel(true)
                    togglePwr.background.setTint(colorOn)
                }
            }
        }

        // Setup for the send button
        val sendBtn = binding.send
        sendBtn.setOnClickListener {
            sendAction()
        }

        viewModel.setEnabled(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        viewModel.setEnabled(false)
        _binding = null
    }

    private fun getCommander(): AsciiCommander? {
        return AsciiCommander.sharedInstance()
    }

    // Function to add a field input to the page
    private fun addField(fieldName: String, default: String) {
        // Make the button to show fields visible
        toggleFolder.visibility = View.VISIBLE

        // Creating the inflated layout of the template
        val inflater = LayoutInflater.from(requireContext())
        val fieldView = inflater.inflate(R.layout.action_field_template, fieldsFolder, false)

        // Setup for the template elements
        val label = fieldView.findViewById<TextView>(R.id.xLabel)
        val textinput = fieldView.findViewById<AutoCompleteTextView>(R.id.xSelect)
        val clearInput = fieldView.findViewById<ImageButton>(R.id.xClear)
        val showInputDropdown = fieldView.findViewById<ImageButton>(R.id.xDropdown)
        clearInput.setOnClickListener { textinput.text.clear() }
        showInputDropdown.setOnClickListener { textinput.showDropDown() }

        // If this is a number field make it only accept numbers of the correct size
        if (fieldName.equals("number", true) ||
            fieldName.equals("passage", true) ||
            fieldName.equals("clone", true) ||
            fieldName.equals("distNum", true)) {

            // Get the maximum number the field can be set to. Passage can only be 35 for Primary cells but there is no realistic way to know what cells the action is applied to
            var maxNum = 35
            if (fieldName.equals("passage", true) || fieldName.equals("clone", true)) { maxNum = 255 }
            else if (fieldName.equals("distNum", true)) { maxNum = 1295 }

            // Make the input type a number
            textinput.inputType = InputType.TYPE_CLASS_NUMBER
            // Add the text watcher to enforce a min & max number
            textinput.addTextChangedListener(createTextWatcher(maxNum))

            // Hide the dropdown button as it is not needed
            showInputDropdown.visibility = View.GONE
        }

        // Assign tags and text for the elements relevant to the fieldName
        val capitalizedField = fieldName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        label.tag = capitalizedField+"Label"
        label.text = capitalizedField
        textinput.tag = capitalizedField+"Select"
        textinput.setText(default)
        textinput.hint = default

        // Get an assign the valid options for this field from the substitutions
        val possibleValues = ArrayList<String>()
        if (default == "unchanged") { possibleValues.add("unchanged") }
        substitutions?.subs?.get(fieldName)?.values?.let { possibleValues.addAll(it) }

        Log.d("TagActionFragment", "Possible Values: $possibleValues")
        if (possibleValues.isNotEmpty()) {
            val adapter = ArrayAdapter(
                requireContext(),
                R.layout.dropdown_item,
                possibleValues)
            textinput.setAdapter(adapter)
        }

        // Add the finished text field to the folder
        fieldsFolder.addView(fieldView)
    }

    // Helper function used to create fields
    private fun createTextWatcher(maxNum: Int): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s?.isNotEmpty() == true) {
                    val number = s.toString().toIntOrNull() ?: return
                    if (number > maxNum) {
                        s.replace(0, s.length, maxNum.toString())
                    } else if (number < 0) {
                        s.replace(0, s.length, "0")
                    }
                }
            }
        }
    }

    // Used to reset the fields being displayed
    private fun resetFields() {
        fieldsFolder.removeAllViews()
        fieldsFolder.visibility = View.GONE
        toggleFolder.visibility = View.GONE
        toggleFolder.setText(R.string.tap_to_show_fields)
        Log.d("TagActionFragment", "Trying to delete the fields")
    }

    // This will trigger when the user types or scans in an action
    private fun setupAction(newAction: String) {
        Log.d("TagActionFragment", "setupAction($newAction)")
        if (chosenAction != newAction && newAction.isNotEmpty()) { // continue only if there is a change
            if (actions != null && actions.acts != null) { // continue only if the actions were retrieved from the API
                if (actions.acts.containsKey(newAction)) { // continue only if it is a valid action
                    // Reset the fields to remove all past settings or changes
                    resetFields()

                    // Get the fields for the actions and create them with the correct default
                    chosenAction = newAction
                    fields = actions.acts.getValue(chosenAction!!).toMutableMap()
                    fields?.forEach { (key, value) -> // Only show fields that are unchanged or set
                        if (value.equals("unchanged", true)){ // Keep the last value
                            addField(key, value)
                        } else if (value.startsWith("set")){ // Set it to a specific value
                            if (substitutions?.subs?.containsKey(key) == true) {
                                val subsVal = substitutions.subs.get(key)?.get(value.split(":", limit=2)[1]) ?: value.split(":", limit=2)[1]
                                addField(key, subsVal)
                            } else {
                                addField(key, value.split(":", limit=2)[1])
                            }

                        }
                    }
                }
            }
        } else {
            Log.d("TagActionFragment", "nulling chosen action")
            chosenAction = null
        }

    }

    // Runs this if the user changes the action text in any way or a barcode is scanned
    private val mActionChangedListener = object: TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            // Runs this when the user stops changing the text field
            val text = s.toString()
            Log.d("TagActionFragment", "Text changed to $text")
            setupAction(text)
        }

    }

    // This function will send the action request to the API
    private fun sendAction() {
        var number: Int? = null
        if (chosenAction != null && fields != null) { // Only continue if the user has selected an action
            if (tagList.size > 0) {
                // Get the list of tags in the correct format
                val formatList = ArrayList<String>(tagList.size)
                tagList.forEach {it -> formatList.add(it.split(":", limit=2)[1])}

                // Creating the map to store the fields and the changes the user made for the API call
                var finalFields: MutableMap<String, String>? = mutableMapOf()

                // This code goes through all the fields of the action and adds them to the finalFields variable to send as an action
                val noChangesList = listOf("now","increment", "clear")
                fields?.forEach { (key, value) ->

                    if (key.equals("number", true)) { // Set the number if it exists
                        // By default set it to the given value in action
                        number = if (value.startsWith("set")) { Integer.parseInt(value.split(":")[1]) } else { Integer.parseInt(value) }

                        // Set number to the text the user inputted if it is valid
                        val capitalizedFieldName = key.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() } + "Select"
                        val textField = fieldsFolder.findViewWithTag<AutoCompleteTextView>(capitalizedFieldName)
                        val userText = textField.text.toString()
                        try {
                            number = Integer.parseInt(userText)
                        } catch (e: Exception) {
                            Log.e("TagActionFragment", "Could not convert the number input $userText")
                        }
                    } else {
                        if (value !in noChangesList) { // Only make a change to the field value if it isn't "now", "increment", or "clear"
                            // Get the text the user inputted
                            val capitalizedFieldName = key.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() } + "Select"
                            val textField = fieldsFolder.findViewWithTag<AutoCompleteTextView>(capitalizedFieldName)
                            val userText = textField.text.toString()

                            // Check if it is a valid option
                            val dropdownItems = getDropdownItems(textField)
                            if ((userText in dropdownItems || dropdownItems.isEmpty()) && userText.isNotEmpty()) { // Only continue if the user selected a valid item
                                Log.d("TagActionFragment", "User selected a valid item")
                                if (substitutions?.subs != null) {
                                    if (userText.equals("unchanged", true)) {
                                        finalFields?.put(key, userText) // Setting the value to the unchanged value
                                    } else if (!substitutions.subs.containsKey(key)) { // If the field is not in substitutions
                                        finalFields?.put(key, userText) // Setting the value to the user inputted text
                                    } else {
                                        finalFields?.put(key, getSubstitutionKey(key, userText)!!) // Setting the value to the user input with the shortened substitution
                                    }
                                }
                            } else {
                                Log.d("TagActionFragment", "User selected an invalid item")
                                Toast.makeText(requireContext(), "The input '$userText' is not valid!", Toast.LENGTH_SHORT).show()
                            }
                            Log.d("TagActionFragment", "========================")
                        } else { // Simply add the value as-is if it isn't in the noChangesList
                            finalFields?.put(key, value)
                        }
                    }
                }
                Log.d("TagActionFragment", "finalFields has been made: $finalFields")

                // Only make and send the action if finalFields has all the required fields
                val fieldKeys = fields?.keys?.toMutableSet() ?: mutableSetOf()
                fieldKeys.remove("number")
                val finalFieldKeys = finalFields!!.keys
                Log.d("TagActionFragment", "fields: $fieldKeys")
                Log.d("TagActionFragment", "finalFields: $finalFieldKeys")
                if (fieldKeys == finalFieldKeys) {
                    // Making the request json body object
                    val request = ActionRequest(
                        target = formatList,
                        checksum = viewModel.asciiToCrc32(formatList),
                        actionName = chosenAction!!,
                        number = number,
                        fields = finalFields
                    )

                    // Sending the action with the API, trying twice to protect against network issues
                    viewLifecycleOwner.lifecycleScope.launch {
                        for (i in 1..2) {
                            try {
                                val response = DataRepository.applyActions(request)
                                if (response.isSuccessful) {
                                    val msg = response.body()?.success
                                    Log.d("InventoryFragment", "Action call works:\n $msg")
                                    Toast.makeText(requireContext(), "Action '$chosenAction' applied.", Toast.LENGTH_SHORT).show()
                                    break
                                } else {
                                    try {
                                        val msg = response.body()?.error
                                        Log.e("InventoryFragment", "Action call failed:\n $msg")
                                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                                        break
                                    } catch (e: Exception) {
                                        val msg = response.errorBody()?.string()
                                        Log.e("InventoryFragment", "Action call failed:\n $msg")
                                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                                        break
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("InventoryFragment", "Failing to apply inventory action")
                            }
                        }
                    }


                } else {
                    Log.e("InventoryFragment", "Fields were not copied properly!")
                    Log.e("InventoryFragment", "Fields: $fields")
                    Log.e("InventoryFragment", "finalFields: $finalFields")
                }
            } else {
                Toast.makeText(requireContext(), "No tags selected!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Please select an action", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to get the items in the dropdown of an AutoCompleteTextView
    private fun getDropdownItems(textField: AutoCompleteTextView): List<String> {
        val adapter = textField.adapter as ArrayAdapter<String>?
        val retrievedItems = ArrayList<String>()
        if (adapter is ArrayAdapter<*>) {
            for (i in 0 until adapter.count) {
                val item = adapter.getItem(i)
                Log.d("TagActionFragment", "Got item: $item")
                // Add the item to the list after checking and casting it to String
                if (item is String) {
                    retrievedItems.add(item)
                }
            }
        }
        return retrievedItems
    }

    // Function to find the substitution for a value
    private fun getSubstitutionKey(outerKey: String, innerValue: String): String? {
        val innerSubs = substitutions?.subs?.get(outerKey)
        if (innerSubs != null) {
            for ((key, value) in innerSubs) {
                if (value == innerValue) {
                    return key
                }
            }
        }
        return null
    }

}