package com.example.multipagetesting2

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.multipagetesting2.databinding.FragmentInventoryBinding
import com.google.gson.Gson
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.multipagetesting2.*

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class InventoryFragment : Fragment() {

    private var _binding: FragmentInventoryBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // Creating an instance of the InventoryViewModel
    private lateinit var viewModel: InventoryViewModel

    // Variables for the data
    private var tagList = ArrayList<String>()
    private val uniqueTags: MutableSet<String> = mutableSetOf()

    // The RecyclerView that displays the list of tags
    private lateinit var rvTags: RecyclerView

    // The touchListener used to enable touch actions on the tags
    private var touchListener: RecyclerTouchListener? = null

    // Holding the detected location tag
    private val locationMap = mutableMapOf<String, Int>()
    private var location: String? = null
    private lateinit var locationText: TextView

    // Storing the substitutions from the API
    val substitutions = DataRepository.substitutions

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        _binding = FragmentInventoryBinding.inflate(inflater, container, false)


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
        viewModel.epcRssiNotification.removeObservers(viewLifecycleOwner)
        viewModel.epcRssiNotification.postValue(null)
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
        viewModel.setSendRSSI(true)
        viewModel.setUnique(false)

        // Setup the tag count
        val tagCounter = binding.tagCount

        // Setup the recyclerView
        val tagAdapter = RfidTagsAdapter(ArrayList())
        rvTags = binding.rvTags
        val possibleTypes = substitutions?.subs?.get("type")?.keys?.toList()
        rvTags.apply {
            layoutManager = LinearLayoutManager(requireActivity())

            viewModel.epcNotification.observe(viewLifecycleOwner) {message -> // This is where the RFID and BC scanned tags will show up
                if (message != null) {
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
                                if (newMsg.any { !it.isLetterOrDigit() || !it.isWhitespace() } && inDB && (newMsg.length == 14 || newMsg.length == 16)) {
                                    tagList.add("${splitMsg[0]}:$newMsg")
                                    tagCounter.setText("${tagList.size}")
                                    tagAdapter.updateData(tagList)
                                }
                            }


                        }
                    }
                }
            }
            adapter = tagAdapter
            if (touchListener == null) { // This sets the touch reactions, like how tap copies the tag and hold deletes it
                touchListener = RecyclerTouchListener(requireContext(), rvTags, object : RecyclerTouchListener.ClickListener {
                    override fun onClick(view: View, position: Int) {
                        Toast.makeText(requireContext(), "Tag value copied!", Toast.LENGTH_SHORT).show()
                    }
                    override fun onLongClick(view: View, position: Int) {
                        val removeTag = tagList[position]
                        tagList.removeAt(position)
                        tagAdapter.removeItem(removeTag)
                        viewModel.removeFromSet(removeTag)
                        tagCounter.setText((Integer.parseInt(tagCounter.text.toString())-1).toString())
                    }
                })
                addOnItemTouchListener(touchListener!!)
            }
        }

        // Used to check for location tags and save the strongest response
        locationText = binding.locationText
        viewModel.epcRssiNotification.observe(viewLifecycleOwner) { message ->
            Log.d("InventoryFragment", "epcRssiNotification: ${message}")
            if (message != null) {
                val mList = message.split(":", limit=2)
                val epc = mList[0]
                val rssi = mList[1].toIntOrNull()
                if (epc.startsWith("21") && epc.endsWith("21")) {
                    Log.d("InventoryFragment", "Location tag received: ${message}")
                    if (rssi != null) {
                        locationMap[epc] = rssi
                    }
                    location = locationMap.maxByOrNull { it.value } ?.key.toString()
                    val locationSub = substitutions?.subs?.get("location")?.get(location) ?: location
                    locationText.setText("Location: ${locationSub}")

                    Log.d("InventoryFragment", "Location map: ${locationMap}")
                    Log.d("InventoryFragment", "Max location: ${location}")
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
            tagCounter.setText("---")
            location = null
            locationMap.clear()
            locationText.setText("Location: -Scan to find location-")
        }

        // Setup the power level toggle
        val togglePwr = binding.togglePowerLevel
        val isMax = viewModel.isMaxPower()
        val colorOn = ContextCompat.getColor(requireContext(), R.color.carolinaBlue)
        val colorOff = ContextCompat.getColor(requireContext(), R.color.basinSlate)
        viewModel.setPowerLevel(true)
        togglePwr.background.setTint(colorOn)
        togglePwr.isChecked = false
//        when (isMax) { // Set the button to the current state of the reader, it will save past user settings
//            null -> {
//                Log.e("Inventory Fragment", "Could not verify device power level")
//            }
//            true -> { // When the reader is at low power set the button to the correct state
//                togglePwr.isChecked = true
//                togglePwr.background.setTint(colorOff)
//            }
//            false -> { // When the reader is at max power set the button to the correct state
//                togglePwr.isChecked = false
//                togglePwr.background.setTint(colorOn)
//            }
//        }
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
        val sendBtn = binding.sendBtn
        sendBtn.setOnClickListener {
            if (location != null) {
                if (tagList.size > 0) {
                    val formatList = ArrayList<String>(tagList.size)
                    tagList.forEach {tag -> formatList.add(tag.split(":", limit=2)[1])}
                    val request = ActionRequest(
                        target = formatList,
                        checksum = viewModel.asciiToCrc32(formatList),
                        actionName = "Inventory",
                        number = null,
                        fields = mutableMapOf("location" to location!!)
                    )
                    viewLifecycleOwner.lifecycleScope.launch {
                        // Ping API to see if it can be reached
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

                        if (canReachAPI) {  // If the API can be reached make the request
                            for (i in 1..2) {
                                try {
                                    val response = DataRepository.applyActions(request)
                                    if (response.isSuccessful) {
                                        val msg = response.body()?.success
                                        Log.d("InventoryFragment", "Action call works:\n $msg")
                                        Toast.makeText(requireContext(), "Tag(s) successfully inventoried", Toast.LENGTH_SHORT).show()
                                        withContext(Dispatchers.Main) {
                                            tagAdapter.clearData()
                                            location = null
                                            locationMap.clear()
                                            tagList.clear()
                                            viewModel.clearUniques()
                                            tagCounter.setText("---")
                                        }
                                        break
                                    } else {
                                        try {
                                            val temp = response.errorBody()?.string().toString()
                                            val msg = Gson().fromJson(temp, BasicResponse::class.java).error
                                            Log.e("InventoryFragment", "Action call failed:\n $msg")
                                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                                            break
                                        } catch (e: Exception) {
                                            val msg = "Error in parsing message!"
                                            Log.e("InventoryFragment", "Action call failed:\n $msg")
                                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                                            break
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("InventoryFragment", "Failing to apply inventory action")
                                }
                            }

                        } else {  // If the API cant be reached save the request to the queue
                            val tempRequest = QueuedApiRequest("Inventory", Gson().toJson(request))
                            saveRequestToQueue(requireContext(), tempRequest)

                            Toast.makeText(requireContext(), "Inventory Queued", Toast.LENGTH_SHORT).show()
                            withContext(Dispatchers.Main) {
                                tagAdapter.clearData()
                                location = null
                                locationMap.clear()
                                tagList.clear()
                                viewModel.clearUniques()
                                tagCounter.setText("---")
                            }
                        }

                    }
                } else {
                    Toast.makeText(requireContext(), "No items scanned!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "No location found!", Toast.LENGTH_SHORT).show()
            }
        }


        viewModel.setEnabled(true)

        // Reset all of the tag lists and counters to make sure nothing is saved between leaving and returning to the page
        tagAdapter.clearData()
        tagList.clear()
        viewModel.clearUniques()
        tagCounter.setText("---")
        location = null
        locationMap.clear()
        locationText.setText("Location: -Scan to find location-")
        Log.d("InventoryFragment", "tagList size: ${tagList.size}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.setEnabled(false)
        _binding = null
    }

    private fun getCommander(): AsciiCommander? {
        return AsciiCommander.sharedInstance()
    }

}