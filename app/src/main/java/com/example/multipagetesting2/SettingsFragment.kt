package com.example.multipagetesting2

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.multipagetesting2.databinding.FragmentSettingsBinding
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander
import java.util.Locale

/**
 * A simple [Fragment] subclass as the third destination in the navigation.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // Creating an instance of the InventoryViewModel
    private lateinit var viewModel: SettingsViewModel

    // The displayed values
    private lateinit var readerState: TextView
    private lateinit var connectionDisplay : ImageView
    private lateinit var batteryLevel: TextView
    private lateinit var ipInput: EditText
    private lateinit var ipWarning: TextView

    // Button to save the given url
    private lateinit var saveIP: Button

    // Handler and the code used to run the state checks
    private val handler = Handler(Looper.getMainLooper())
    var waitTime = 500
    private val updateState = object : Runnable {
        override fun run() {

            // Setup for the reader state displays
            val readerStateText = viewModel.connectionState().lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            readerState.text = readerStateText
            if (readerStateText.equals("disconnected", true)) {
                connectionDisplay.setImageResource(R.drawable.baseline_close_24)
            } else if (readerStateText.equals("connecting", true)) {
                Glide.with(requireContext()).asGif().load(R.drawable.waiting_gif).into(connectionDisplay)
            } else if (readerStateText.equals("connected", true)) {
                connectionDisplay.setImageResource(R.drawable.baseline_check_24)
            } else {
                connectionDisplay.setImageResource(R.drawable.ic_cross)
            }


            if (viewModel.isConnected()) {
                batteryLevel.text = "${viewModel.getBatteryLevel()}%"
                waitTime = 2000 // After a successful connection only ping the reader every 2 seconds
            } else {
                batteryLevel.setText(R.string.unknown)
            }

            // Schedule the task to run again
            handler.postDelayed(this, waitTime.toLong())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)


        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)
    }

    override fun onResume() {
        super.onResume()

        // Initialize the ViewModel
        viewModel.setCommander(getCommander()!!)

        // Binding and resetting the values
        readerState = binding.readerState
        readerState.setText(R.string.unknown)
        connectionDisplay = binding.connectionDisplay
        batteryLevel = binding.batteryLevel
        batteryLevel.setText(R.string.unknown)
        ipInput = binding.apiIP
        ipWarning = binding.ipWarning

        // Getting the saved url and displaying it
        val sharedPref = requireContext().getSharedPreferences("${requireContext().packageName}.AppPreferences", Context.MODE_PRIVATE)
        val displayIP = sharedPref.getString("apiBaseIP", "No URL found!").toString()
        DataRepository.setApiIP(displayIP)
        ipInput.setText(displayIP)
        ipInput.setHint(displayIP)
        if (!isValidIP(displayIP)) { ipWarning.setText(R.string.invalid_ip_port_address) } else { ipWarning.setText("") }

        // Saving the given url when the user clicks save
        saveIP = binding.saveIP
        saveIP.setOnClickListener {
            if (ipInput.text.toString().isNotEmpty()) {
                val ip = ipInput.text.toString()
                if (isValidIP(ip)) {
                    with(sharedPref.edit()) {
                        putString("apiBaseIP", ip)
                        apply()
                    }
                    DataRepository.setApiIP(ip)
                    ipWarning.setText("")
                } else {
                    ipWarning.setText(R.string.invalid_ip_port_address)
                }
            }
        }

        // Starting the checks for the reader and battery state
        handler.post(updateState)

    }

    override fun onPause() {
        super.onPause()

        viewModel.resetDevice()
        handler.removeCallbacks(updateState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getCommander(): AsciiCommander? {
        return AsciiCommander.sharedInstance()
    }

    private fun isValidIP(ip: String): Boolean {
        // Extract the various parts of the given address
        val parts = ip.split(":", limit=2)
        if (parts.size != 2) { return false }
        val ipParts = parts[0].split(".")
        val port = parts[1]

        // Checks to see if IP is valid
        if (ipParts.size != 4) { return false }
        if (!ipParts.all { it.toIntOrNull() in 0..255 }) { return false }

        // Checks to see if port is valid
        if (port.toIntOrNull() !in 0..65535) { return false }

        return true
    }
}