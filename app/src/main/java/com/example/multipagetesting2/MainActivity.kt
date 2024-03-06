package com.example.multipagetesting2

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.multipagetesting2.databinding.ActivityMainBinding
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.uk.tsl.rfid.DeviceListActivity
import com.uk.tsl.rfid.DeviceListActivity.EXTRA_DEVICE_ACTION
import com.uk.tsl.rfid.DeviceListActivity.EXTRA_DEVICE_INDEX
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander
import com.uk.tsl.rfid.asciiprotocol.commands.BatteryStatusCommand
import com.uk.tsl.rfid.asciiprotocol.device.ConnectionState
import com.uk.tsl.rfid.asciiprotocol.device.Reader
import com.uk.tsl.rfid.asciiprotocol.device.ReaderManager
import com.uk.tsl.rfid.asciiprotocol.device.TransportType
import com.uk.tsl.rfid.asciiprotocol.responders.LoggerResponder
import com.uk.tsl.utils.Observable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


class  MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var mReader: Reader? = null
    private var mIsSelectingReader = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var didOpen = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Ensure the shared instance of AsciiCommander exists
        AsciiCommander.createSharedInstance(applicationContext)
        val commander = getCommander()

        commander.clearResponders()
        commander.addResponder(LoggerResponder())
        commander.addSynchronousResponder();
        ReaderManager.create(applicationContext);
        ReaderManager.sharedInstance().getReaderList().readerAddedEvent().addObserver(mAddedObserver)
        ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().addObserver(mUpdatedObserver)
        ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().addObserver(mRemovedObserver)

        // Attempt to get the substitutions and actions from the API 5 times before giving up
        coroutineScope.launch {
            // Setup for the JSON converting
            val gson = GsonBuilder()
                .registerTypeAdapter(SubstitutionResponse::class.java, SubstitutionResponseDeserializer())
                .registerTypeAdapter(SubstitutionResponse::class.java, SubstitutionResponseSerializer())
                .registerTypeAdapter(ActionsResponse::class.java, ActionResponseDeserializer())
                .registerTypeAdapter(ActionsResponse::class.java, ActionResponseSerializer())
                .create()

            for (i in 1..5) {
                try {
                    // Get the substitutions from the API
                    if (DataRepository.substitutions == null) {
                        DataRepository.getSubstitutions()
                        if (DataRepository.substitutions != null) {

                            // Serialize the SubstitutionResponse object to JSON string
                            val substitutionsJson = gson.toJson(DataRepository.substitutions)

                            // Save the latest value to file for later use
                            val sharedPref = getSharedPreferences("${applicationContext.packageName}.AppPreferences", Context.MODE_PRIVATE)
                            with(sharedPref.edit()) {
                                putString("substitutions", substitutionsJson)
                                apply()
                            }
                        }
                    }
                    // Get the actions from the API
                    if (DataRepository.actions == null) {
                        DataRepository.getActions()
                        if (DataRepository.actions != null) {

                            // Serialize the SubstitutionResponse object to JSON string
                            val actionsJson = gson.toJson(DataRepository.actions)

                            // Save the latest value to file for later use
                            val sharedPref = getSharedPreferences("${applicationContext.packageName}.AppPreferences", Context.MODE_PRIVATE)
                            with(sharedPref.edit()) {
                                putString("actions", actionsJson)
                                apply()
                            }
                        }
                    }

                    // If both were successful stop looping
                    if (DataRepository.substitutions != null && DataRepository.actions != null) {
                        break
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("MainActivity", "Error when requesting substitutions/actions")
                }
            }

            // If the api responses failed load from file
            if (DataRepository.substitutions == null) {
                val sharedPref = getSharedPreferences("${applicationContext.packageName}.AppPreferences", Context.MODE_PRIVATE)
                val substitutionsJson = sharedPref.getString("substitutions", null)
                DataRepository.substitutions = gson.fromJson(substitutionsJson, SubstitutionResponse::class.java)
            }
            if (DataRepository.actions == null) {
                val sharedPref = getSharedPreferences("${applicationContext.packageName}.AppPreferences", Context.MODE_PRIVATE)
                val actionsJson = sharedPref.getString("actions", null)
                DataRepository.actions = gson.fromJson(actionsJson, ActionsResponse::class.java)
            }
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // This is used to show/hide the API Queue page depending on if it is empty
        super.onPrepareOptionsMenu(menu)

        // Find the menu item you want to show/hide
        val menuItem = menu.findItem(R.id.apiQueueFragment)

        // Set the item's visibility
        menuItem.isVisible = !isQueueEmpty(applicationContext)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.settingsFragment)
                true
            }
            R.id.InventoryFragment -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.inventoryFragment)
                true
            }
            R.id.TagFinderFragment -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.tagFinderFragment)
                true
            }
            R.id.TagActionFragment -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.tagActionFragment)
                true
            }
            R.id.killFragment -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.killFragment)
                true
            }
            R.id.summaryFragment -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.summaryFragment)
                true
            }
            R.id.apiQueueFragment -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.apiQueueFragment)
                true
            }
            R.id.connect_reader_menu_item -> {
                mIsSelectingReader = true
                var index = -1
                if( mReader != null )
                {
                    index = ReaderManager.sharedInstance().getReaderList().list().indexOf(mReader);
                }
                val selectIntent = Intent(this, DeviceListActivity::class.java)
                if (index >= 0) {
                    selectIntent.putExtra(EXTRA_DEVICE_INDEX, index)
                }
                startActivityForResult(selectIntent, DeviceListActivity.SELECT_DEVICE_REQUEST)
                true
            }
            R.id.disconnect_reader_menu_item -> {
                if( mReader != null )
                {
                    mReader?.disconnect();
                    mReader = null
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            DeviceListActivity.SELECT_DEVICE_REQUEST ->  // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    val readerIndex = data?.extras!!.getInt(EXTRA_DEVICE_INDEX)
                    val chosenReader = ReaderManager.sharedInstance().readerList.list()[readerIndex]
                    val action = data.extras!!.getInt(EXTRA_DEVICE_ACTION)
                    // If already connected to a different reader then disconnect it
                    if (mReader != null) {
                        if (action == DeviceListActivity.DEVICE_CHANGE || action ==
                            DeviceListActivity.DEVICE_DISCONNECT
                        ) {
                            mReader!!.disconnect()
                            if (action == DeviceListActivity.DEVICE_DISCONNECT) {
                                mReader = null
                            }
                        }
                    }
                    // Use the Reader found
                    if (action == DeviceListActivity.DEVICE_CHANGE || action ==
                        DeviceListActivity.DEVICE_CONNECT
                    ) {
                        mReader = chosenReader
                        getCommander().reader = mReader
                        saveReaderState(chosenReader.displayName)
                    }
                }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    override fun onPause() {
        super.onPause()

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver)
        if (!mIsSelectingReader && !ReaderManager.sharedInstance().didCauseOnPause() && mReader != null) {
            mReader!!.disconnect()
        }
        ReaderManager.sharedInstance().onPause()
    }

    override fun onResume() {
        super.onResume()

        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, IntentFilter(AsciiCommander.STATE_CHANGED_NOTIFICATION))
        val readerManagerDidCauseOnPause = ReaderManager.sharedInstance().didCauseOnPause()
        ReaderManager.sharedInstance().onResume()
        ReaderManager.sharedInstance().updateList()

        autoSelectReader(!readerManagerDidCauseOnPause)
        try {
            if (!getCommander().hasSynchronousResponder) {
                getCommander().addSynchronousResponder()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mIsSelectingReader = false


        // Try to automatically go to the connect reader page
        if (didOpen == false) {
            mIsSelectingReader = true
            var index = -1
            if( mReader != null )
            {
                index = ReaderManager.sharedInstance().getReaderList().list().indexOf(mReader);
            }
            val selectIntent = Intent(this, DeviceListActivity::class.java)
            if (index >= 0) {
                selectIntent.putExtra(EXTRA_DEVICE_INDEX, index)
            }
            startActivityForResult(selectIntent, DeviceListActivity.SELECT_DEVICE_REQUEST)
            didOpen = true
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove observers for changes
        ReaderManager.sharedInstance().getReaderList().readerAddedEvent().removeObserver(mAddedObserver)
        ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().removeObserver(mUpdatedObserver)
        ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().removeObserver(mRemovedObserver)

        coroutineScope.cancel()
    }

    private fun getCommander(): AsciiCommander {
        return AsciiCommander.sharedInstance()
    }

    private fun autoSelectReader(attemptReconnect: Boolean) {
        Log.d("AutoSelectReader", "With attemptReconnect: $attemptReconnect")
        val readerList = ReaderManager.sharedInstance().readerList
        var usbReader: Reader? = null

        if (readerList.list().size >= 1) {
            // Currently only support a single USB connected device so we can safely take the
            // first CONNECTED reader if there is one
            for (reader in readerList.list()) {
                if (reader.hasTransportOfType(TransportType.USB)) {
                    usbReader = reader
                    break
                }
            }
        }

        if (mReader == null) {
            if (usbReader != null) {
                // Use the Reader found, if any
                mReader = usbReader
                getCommander().setReader(mReader)
            }
        } else {
            // If already connected to a Reader by anything other than USB then
            // switch to the USB Reader
            val activeTransport = mReader?.activeTransport
            if (activeTransport != null && activeTransport.type() != TransportType.USB && usbReader != null) {
                //appendMessage("Disconnecting from: ${mReader?.displayName}\n")
                mReader?.disconnect()
                mReader = usbReader
                // Use the Reader found, if any
                getCommander().setReader(mReader)
            }
        }

        // Reconnect to the chosen Reader
        if (mReader != null &&
            !mReader!!.isConnecting &&
            (mReader!!.activeTransport == null || mReader!!.activeTransport?.connectionStatus()?.value() == ConnectionState.DISCONNECTED)
        ) {
            // Attempt to reconnect on the last used transport unless the ReaderManager is the cause of OnPause (USB device connecting)
            if (attemptReconnect) {
                Log.d("MainActivity", "Trying to reconnect to : ${mReader!!.displayName}")
                saveReaderState(mReader!!.displayName)
                if (mReader!!.allowMultipleTransports() || mReader!!.lastTransportType == null) {
                    // Reader allows multiple transports or has not yet been connected, so connect to it over any available transport
                    if (mReader!!.connect()) {
                        // Connecting to reader
                    }
                } else {
                    // Reader supports only a single active transport, so connect to it over the transport that was last in use
                    if (mReader!!.connect(mReader!!.lastTransportType)) {
                        // Connecting to reader
                    }
                }
            }
        }
    }

    val mAddedObserver: Observable.Observer<Reader> = object : Observable.Observer<Reader> {
        override fun update(observable: Observable<out Reader>?, reader: Reader) {
            // See if this newly added Reader should be used
            autoSelectReader(true)
        }
    }

    val mUpdatedObserver: Observable.Observer<Reader> = object : Observable.Observer<Reader> {
        override fun update(observable: Observable<out Reader>?, reader: Reader) {
            // This observer doesn't do anything, it's empty
        }
    }

    val mRemovedObserver: Observable.Observer<Reader> = object : Observable.Observer<Reader> {
        override fun update(observable: Observable<out Reader>?, reader: Reader) {
            // Was the current Reader removed
            if (reader == mReader) {
                mReader = null
                // Stop using the old Reader
                getCommander().setReader(mReader)
            }
        }
    }

    private val mMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connectionStateMsg = getCommander().connectionState.toString()
            Log.d("", "AsciiCommander state changed - isConnected: ${getCommander().isConnected} ($connectionStateMsg)")

            val currentReader = mReader

            if (getCommander() != null) {
                if (getCommander().isConnected) {
                    try {
                        if (!getCommander().hasSynchronousResponder) {
                            getCommander().addSynchronousResponder()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    // Report the battery level when Reader connects
                    val bCommand = BatteryStatusCommand.synchronousCommand()
                    getCommander().executeCommand(bCommand)
                    val batteryLevel = bCommand.batteryLevel
                } else if (getCommander().connectionState == ConnectionState.DISCONNECTED) {
                    // A manual disconnect will have cleared mReader
                    if (currentReader != null) {
                        // See if this is from a failed connection attempt
                        if (!currentReader.wasLastConnectSuccessful()) {
                            // Unable to connect, so have to choose the reader again
                            mReader = null
                        }
                    }
                }
            }
        }
    }

    // Function to remember what reader the user selected last
    private fun saveReaderState(readerName: String?) {
        val sharedPref = getSharedPreferences("${applicationContext.packageName}.AppPreferences", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("lastConnectedReader", readerName)
            apply()
        }
    }

    // Function to set the reader the user selected
    private fun getLastConnectedReader(): String? {
        val sharedPref = getSharedPreferences("${applicationContext.packageName}.AppPreferences", Context.MODE_PRIVATE)
        return sharedPref.getString("lastConnectedReader", null)
    }

}