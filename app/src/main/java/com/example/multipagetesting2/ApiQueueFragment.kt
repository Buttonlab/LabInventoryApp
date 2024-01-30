package com.example.multipagetesting2

import android.content.Context
import android.content.res.ColorStateList

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.multipagetesting2.databinding.FragmentApiQueueBinding
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class ApiQueueFragment : Fragment() {

    private var _binding: FragmentApiQueueBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // The UI components that show the request information
    private lateinit var requestTypeLayout : LinearLayout
    private lateinit var requestType : TextView
    private lateinit var requestTargetLayout: LinearLayout
    private lateinit var requestTargetList : LinearLayout
    private lateinit var requestChangesLayout : LinearLayout
    private lateinit var requestChangesList : LinearLayout
    private lateinit var requestTimeLayout : LinearLayout
    private lateinit var requestTime : TextView

    //The text used to display the result
    private lateinit var requestResult: TextView

    // The warning and button group layout
    private lateinit var connectionWarning : TextView
    private lateinit var actionButtons : LinearLayout

    // The buttons in the page
    private lateinit var nextRequestBtn: Button
    private lateinit var denyBtn : Button
    private lateinit var approveBtn : Button

    // Storing the default color of the text for later use
    private lateinit var defaultTextColor: ColorStateList

    // Storing the substitutions from the API
    val substitutions = DataRepository.substitutions

    // The request queue
    private lateinit var queue: MutableList<QueuedApiRequest>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        _binding = FragmentApiQueueBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        // Binding the UI elements
        requestTypeLayout = binding.requestTypeLayout
        requestType = binding.requestType
        requestTargetLayout = binding.requestTargetLayout
        requestTargetList = binding.requestTargetList
        requestChangesLayout = binding.requestChangesLayout
        requestChangesList = binding.requestChangesList
        requestTimeLayout = binding.requestTimeLayout
        requestTime = binding.requestTime
        requestResult = binding.requestResult
        connectionWarning = binding.connectionWarning
        actionButtons = binding.actionButtons
        nextRequestBtn = binding.nextRequestBtn
        denyBtn = binding.denyBtn
        approveBtn = binding.approveBtn
        defaultTextColor = requestTime.textColors
        resetUI()

        // Getting the queue
        val sharedPreferences = requireContext().getSharedPreferences("${requireContext().packageName}.ApiQueue", Context.MODE_PRIVATE)
        queue = getQueueFromPreferences(sharedPreferences)
        queue.sortBy { it.time ?: LocalDateTime.MAX } // Sort from oldest to newest


        viewLifecycleOwner.lifecycleScope.launch {
            var canReachAPI = false
            // Ping API to see if it can be reached
            for (i in 1..2) {
                canReachAPI = DataRepository.pingAPI()
                if (canReachAPI) {
                    break
                }
            }

            if (canReachAPI) {  // If the API can be reached then continue normally

                // Call the function to reset the UI before displaying anything
                withContext(Dispatchers.Main) { resetUI() }

                // Getting the first request and adding it's information to the UI
                var request = advanceQueue()

                // If the user taps approve
                approveBtn.setOnClickListener {
                    if (request != null) {
                        // Sending the request
                        if (request!!.type.equals("kill", true)) {
                            sendKill(request!!) { (success, result) ->
                                if (success) {  // If the request was successful
                                    // Remove the request from the queue
                                    queue.removeAt(0)

                                    // Update the queue in file
                                    replaceRequestQueue(requireContext(), queue)

                                    // Not needed but to ensure matching data get the queue again
                                    queue = getQueueFromPreferences(sharedPreferences)
                                    queue.sortBy { it.time ?: LocalDateTime.MAX } // Sort from oldest to newest

                                    // Clear the UI
                                    resetUI()
                                    // Set the response text
                                    requestResult.setText(result)
                                    // Make button to see next request visible if there are any more
                                    if (queue.isNotEmpty()) {nextRequestBtn.visibility = View.VISIBLE}
                                } else {
                                    // Set the response text to show the error
                                    requestResult.setText("ERROR: $result")
                                }
                            }
                        } else if (request!!.type.equals("inventory", true) || request!!.type.equals("action", true)) {
                            sendAction(request!!) { (success, result) ->
                                if (success) {  // If the request was successful
                                    // Remove the request from the queue
                                    queue.removeAt(0)

                                    // Update the queue in file
                                    replaceRequestQueue(requireContext(), queue)

                                    // Not needed but to ensure matching data get the queue again
                                    queue = getQueueFromPreferences(sharedPreferences)
                                    queue.sortBy { it.time ?: LocalDateTime.MAX } // Sort from oldest to newest

                                    // Clear the UI
                                    resetUI()
                                    // Set the response text
                                    requestResult.setText(result)
                                    // Make button to see next request visible if there are any more
                                    if (queue.isNotEmpty()) {nextRequestBtn.visibility = View.VISIBLE}
                                } else {
                                    // Set the response text to show the error
                                    requestResult.setText("ERROR: $result")
                                }
                            }
                        }
                    }
                }

                // If the user taps deny
                denyBtn.setOnClickListener {
                    // Remove the request from the queue
                    queue.removeAt(0)

                    // Update the queue in file
                    replaceRequestQueue(requireContext(), queue)

                    // Not needed but to ensure matching data get the queue again
                    queue = getQueueFromPreferences(sharedPreferences)
                    queue.sortBy { it.time ?: LocalDateTime.MAX } // Sort from oldest to newest

                    // Display the next request
                    request = advanceQueue()
                }

                // If the user taps the next request button
                nextRequestBtn.setOnClickListener {
                    // Not needed but to ensure matching data get the queue again
                    queue = getQueueFromPreferences(sharedPreferences)
                    queue.sortBy { it.time ?: LocalDateTime.MAX } // Sort from oldest to newest
                    if (queue.isNotEmpty()) {
                        // Display the next request
                        request = advanceQueue()
                    } else {
                        resetUI()
                    }
                }


            } else {

                withContext(Dispatchers.Main) {
                    resetUI()  // Call the function to reset the UI to clear all elements

                    // Showing the warning and hiding the buttons
                    connectionWarning.visibility = View.VISIBLE
                    actionButtons.visibility = View.GONE
                }


            }
        }
    }

    // Function to advance the queue
    private fun advanceQueue(): QueuedApiRequest? {
        resetUI()
        if (queue.isNotEmpty()) {
            val request = queue.first()

            // Displaying the first(oldest) request
            setUI(request)

            return request
        }
        return null
    }



    // Function to send a kill request
    private fun sendKill(request: QueuedApiRequest, callback: (result: Pair<Boolean, String?>) -> Unit) {
        val cellID = request.param
        val checksum = asciiToCrc32(cellID)
        var success = false
        var result: String? = null
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Call the kill cell endpoint for the API
                val response = DataRepository.killCellByID(cellID, checksum)
                if (response.isSuccessful) {
                    success = true
                    result = response.body()?.success
                } else {
                    try {
                        val temp = response.errorBody()?.string().toString()
                        result = Gson().fromJson(temp, BasicResponse::class.java).error
                        Log.e("ApiQueueFragment", "Error when applying action, $result")
                    } catch (e: Exception) {
                        Log.e("ApiQueueFragment", "Error when applying action")
                        e.printStackTrace()
                        result = "Error parsing message returned by API"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                result = "Unknown error when trying to send request"
            }
            // Call the callback with the result
            callback.invoke(Pair(success, result))
        }
    }

    private fun sendAction(request: QueuedApiRequest, callback: (result: Pair<Boolean, String?>) -> Unit) {
        Log.d("ApiQueueFragment", "Sending action")
        val actionRequest = Gson().fromJson(request.param, ActionRequest::class.java)
        var success = false
        var result: String? = null
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Call the kill cell endpoint for the API
                val response = DataRepository.applyActions(actionRequest)
                if (response.isSuccessful) {
                    success = true
                    result = response.body()?.success
                } else {
                    try {
                        val temp = response.errorBody()?.string().toString()
                        result = Gson().fromJson(temp, BasicResponse::class.java).error
                        Log.e("ApiQueueFragment", "Error when applying action, $result")
                    } catch (e: Exception) {
                        Log.e("ApiQueueFragment", "Error when applying action")
                        e.printStackTrace()
                        result = "Error parsing message returned by API"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                result = "Unknown error when trying to send request"
            }

            // Call the callback with the result
            callback.invoke(Pair(success, result))
        }
    }


    // Function to reset the UI displaying the request information
    private fun resetUI() {
        requestTypeLayout.visibility = View.GONE
        requestType.setText("")
        requestTargetLayout.visibility = View.GONE
        requestTargetList.removeAllViews()
        requestChangesLayout.visibility = View.GONE
        requestChangesList.removeAllViews()
        requestTimeLayout.visibility = View.GONE
        requestTime.setText("")
        nextRequestBtn.visibility = View.GONE
        requestResult.setText("")
    }

    // Function to set the UI display for a request
    private fun setUI(request : QueuedApiRequest) {
        // Setting the type
        requestType.setText(request.type)
        requestTypeLayout.visibility = View.VISIBLE

        // Setting the target display
        if (request.type.equals("kill", true)) {
            // Creating the text view for the target
            requestTargetList.addView(createTargetTextView(request.param))

        } else if (request.type.equals("inventory", true) || request.type.equals("action", true)) {
            val actionRequest = Gson().fromJson(request.param, ActionRequest::class.java)
            for (tag in actionRequest.target) {
                // Create a textview for each target
                requestTargetList.addView(createTargetTextView(tag))
            }
        }
        requestTargetLayout.visibility = View.VISIBLE

        // Setting changes if action
        if (request.type.equals("inventory", true) || request.type.equals("action", true)) {
            val actionRequest = Gson().fromJson(request.param, ActionRequest::class.java)
            for ((key, value) in actionRequest.fields) {
                requestChangesList.addView(createChangeTextView(key, value))
            }
            if (actionRequest.number != null) {
                requestChangesList.addView(createChangeTextView("number", actionRequest.number.toString()))
            }

            requestChangesList.visibility = View.VISIBLE
        } else {
            requestChangesLayout.visibility = View.GONE
            requestChangesList.removeAllViews()
        }

        // Setting the time display
        var formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm")
        requestTime.setText(if (request.time != null) formatter.format(request.time) else "NULL")
        requestTimeLayout.visibility = View.VISIBLE

        // Checking if time is more than a day old and if so making text red
        val currentCol = requestTime.currentTextColor
        if (request.time.isBefore(LocalDateTime.now().minusDays(1))) {
            requestTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning))
        } else {
            requestTime.setTextColor(defaultTextColor)
        }
    }


    // Function used to create a textview to display a target
    private fun createTargetTextView(tag: String): TextView {
        // Creating the textview and settings for it
        val textView = TextView(requireContext())
        textView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        textView.setText(displayTag(tag))
        textView.textSize = 26f
        textView.gravity = Gravity.CENTER
        textView.setBackgroundResource(R.drawable.inv_item)

        return textView
    }

    // Function used to create a textview to display a change for actions
    private fun createChangeTextView(key: String, value: String): TextView {
        // Creating the textview and settings for it
        val textView = TextView(requireContext())
        textView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val newValue = if (value.equals("unchanged", true) || key.equals("number", true)) value else "set to $value"
        textView.setText("$key is $newValue")
        textView.textSize = 26f
        textView.gravity = Gravity.CENTER
        textView.setBackgroundResource(R.drawable.inv_item)

        return textView
    }

    // Function used to turn a tag to a display format
    private fun displayTag(tag: String): String {
        try {
            if (tag.startsWith("1") || tag.startsWith("3")) {
                val genotype = substitutions?.subs?.get("genotype")?.get(tag.substring(1,2)) ?: tag.substring(1,2)
                val distNum = tag.substring(2,4).toInt(36)
                val year = tag.substring(4,6)
                val owner = substitutions?.subs?.get("owner")?.get(tag.substring(6,7)) ?: tag.substring(6,7)
                val passage = tag.substring(7,8).toInt(36)
                val surface = substitutions?.subs?.get("surface")?.get(tag.substring(8,9)) ?: tag.substring(8,9)
                val number = tag.substring(9,10).toInt(36)
                return "$genotype$distNum$year  $owner\nOn:$surface   Psg#$passage   #$number".replace("Unset", "")
            } else if (tag.startsWith("2") || tag.startsWith("4")) {
                val cellType = substitutions?.subs?.get("cellType")?.get(tag.substring(1,2)) ?: tag.substring(1,2)
                val genemod = substitutions?.subs?.get("genemod")?.get(tag.substring(2,3)) ?: tag.substring(2,3)
                val gene1 = substitutions?.subs?.get("gene1")?.get(tag.substring(3,4)) ?: tag.substring(3,4)
                val gene2 = substitutions?.subs?.get("gene2")?.get(tag.substring(4,5)) ?: tag.substring(4,5)
                val resistance = substitutions?.subs?.get("resistance")?.get(tag.substring(5,6)) ?: tag.substring(5,6)
                val clone = tag.substring(6,8).toInt(16)
                val passage = tag.substring(8,10).toInt(16)
                val number = tag.substring(11,12).toInt(36)
                return "$cellType    $genemod   $gene1   $gene2   \n$resistance   Clone#$clone   Psg#${passage}   #${number}".replace("Unset", "")
            } else {
                return ""
            }
        } catch (e: Exception) {
            return ""
        }

    }

}