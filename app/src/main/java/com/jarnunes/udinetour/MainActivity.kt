package com.jarnunes.udinetour

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.jarnunes.udinetour.adapter.MessageAdapter
import com.jarnunes.udinetour.databinding.ActivityMainBinding
import com.jarnunes.udinetour.helper.DeviceHelper
import com.jarnunes.udinetour.helper.FileHelper
import com.jarnunes.udinetour.maps.PlacesApiServiceImpl
import com.jarnunes.udinetour.maps.SearchPlacesQuery
import com.jarnunes.udinetour.maps.location.ActivityResultProvider
import com.jarnunes.udinetour.maps.location.UserLocationService
import com.jarnunes.udinetour.message.ChatSessionInfo
import com.jarnunes.udinetour.message.Message
import com.jarnunes.udinetour.message.MessageType
import com.jarnunes.udinetour.message.UserLocation
import com.jarnunes.udinetour.recorder.AndroidAudioPlayer
import com.jarnunes.udinetour.recorder.AndroidAudioRecorder
import com.jarnunes.udinetour.recorder.AudioService
import java.io.File

class MainActivity : AppCompatActivity(), ActivityResultProvider {

    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageList: ArrayList<Message>
    private lateinit var deviceHelper: DeviceHelper
    private lateinit var binding: ActivityMainBinding
    private lateinit var chatSessionInfo: ChatSessionInfo
    private lateinit var currentLocation: UserLocation
    private lateinit var locationService: UserLocationService
    private lateinit var audioService: AudioService;

    private var fileHelper = FileHelper()
    private var currentImagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.chatToolbar)
        initialize()
        configureMessages()
        configureSessionChatInfo()
        configureMainView()

        loadStoredMessages()
        configureListenerForSendMessages()
        configureListenerForAudioRecorder()

        addWatcherToShowHideSendButton(binding.chatInputMessage, binding.chatSendMessageIcon)
        configureCurrentLocationCallback()
    }

    private fun configureCurrentLocationCallback() {
        locationService.getUserLocation { lastLocation ->
            currentLocation.latitude = -19.918892780804857
            currentLocation.longitude = -43.93867202055777
            println(lastLocation)
            // currentLocation.latitude = lastLocation?.latitude
            // currentLocation.longitude = lastLocation?.longitude
        }
    }

    private fun configureMessages() {
        this.messageList = ArrayList()
        this.messageAdapter = MessageAdapter(this, messageList, supportFragmentManager)
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun configureListenerForAudioRecorder() {
        binding.audioRecorder.setOnClickListener {
            audioService.record(
                afterStopRecordCallback = { audioFile ->
                    binding.audioRecorder.setImageResource(R.drawable.baseline_mic_24)

                    val messageObject = Message(null, chatSessionInfo.getSenderUID(), audioFile?.absolutePath)
                    messageObject.messageType = MessageType.AUDIO
                    messageList.add(messageObject)
                    messageAdapter.notifyDataSetChanged()
                },
                afterStartRecordCallback = {
                    binding.audioRecorder.setImageResource(R.drawable.sharp_mic_off_24)
                }
            )
        }
    }

    private fun configureListenerForSendMessages() {
        // add the message to database
        binding.chatSendMessageIcon.setOnClickListener {
            val message = binding.chatInputMessage.text.toString()

            if (currentImagePath != null) {
                val messageObject = Message(null, chatSessionInfo.getSenderUID(), currentImagePath)
                messageObject.messageType = MessageType.IMAGE
                messageList.add(messageObject)
            }

            val messageObject = Message()
            messageObject.message = message
            messageObject.messageType = MessageType.TEXT
            messageObject.sentId = chatSessionInfo.getSenderUID()
            messageObject.setUserLocation(currentLocation)
            messageList.add(messageObject)
            fileHelper.writeMessages(messageList, applicationContext)

            // Integração com o UDINE
            val mapMessage = Message()
            mapMessage.messageType = MessageType.MAP
            mapMessage.sentId = "SYSTEM"
            mapMessage.setUserLocation(messageObject.getUserLocation())
            messageList.add(mapMessage)
            fileHelper.writeMessages(messageList, applicationContext)

            PlacesApiServiceImpl(applicationContext).getNearbyPlaces(createSearchPlacesQuery()) { placesResult ->
                // Manipule a lista `placesResult` aqui, por exemplo:
                if (placesResult.isNotEmpty()) {
                    println(placesResult.size)
                } else {
                    println(placesResult.size)
                }
            }

            currentImagePath = null
            messageAdapter.notifyDataSetChanged()

            binding.chatInputMessage.setText("")
            binding.chatRecycler.scrollToPosition(messageList.size - 1)
        }
    }

    private fun createSearchPlacesQuery(): SearchPlacesQuery {
        return SearchPlacesQuery(
            userLocation = currentLocation,
            type = "tourist_attraction",
            radius = 1000
        )
    }

    private fun loadStoredMessages() {
        val storedMessageList = fileHelper.readMessages(applicationContext)
        messageList.clear()
        messageList.addAll(storedMessageList)
    }

    private fun configureMainView() {
        binding.chatRecycler.layoutManager = LinearLayoutManager(this)
        binding.chatRecycler.adapter = messageAdapter
        binding.chatRecycler.scrollToPosition(messageList.size - 1)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding.chatRecycler.layoutManager = layoutManager
    }

    private fun initialize() {
        this.currentLocation = UserLocation()
        this.locationService = UserLocationService(this, this);
        this.audioService = AudioService(this, this);
    }

    private fun configureSessionChatInfo() {
        this.deviceHelper = DeviceHelper()
        this.chatSessionInfo = ChatSessionInfo()
        this.chatSessionInfo.setSenderName("DeviceName")
        this.chatSessionInfo.setSenderUID(deviceHelper.getDeviceId(applicationContext))
        this.chatSessionInfo.setReceiverName(getString(R.string.app_name))
        this.chatSessionInfo.setReceiverUID(getReceiverUID())
        this.chatSessionInfo.setSenderRoom(chatSessionInfo.getReceiverUID() + chatSessionInfo.getSenderUID())
        this.chatSessionInfo.setReceiverRoom(chatSessionInfo.getSenderUID() + chatSessionInfo.getReceiverUID())
    }


    private fun addWatcherToShowHideSendButton(messageText: EditText, sendButton: ImageView) {
        messageText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrBlank()) {
                    sendButton.visibility = View.GONE
                } else {
                    sendButton.visibility = View.VISIBLE
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun getReceiverUID(): String {
        return getString(R.string.app_name) + "UID"
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_bar_delete -> {
                val alert = AlertDialog.Builder(this)
                alert.setTitle("Deletar")
                alert.setMessage("Confirma exclusão das mensagens?")
                alert.setCancelable(false)
                alert.setNegativeButton(
                    "Não"
                ) { dialogInterface, i ->
                    dialogInterface.cancel()
                }

                alert.setPositiveButton(
                    "Sim"
                ) { dialogInterface, i ->
                    // Limpa todas as mensagens
                    messageList.clear()
                    messageAdapter.notifyDataSetChanged()

                    // Limpa o armazenamento de mensagens (caso esteja salvando)
                    fileHelper.writeMessages(messageList, applicationContext)
                }

                alert.create().show()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun <I, O> getActivityResultLauncher(
        contract: ActivityResultContract<I, O>,
        callback: ActivityResultCallback<O>
    ): ActivityResultLauncher<I> {
        return registerForActivityResult(contract, callback)
    }

}