package com.tthhfugapiyo.turnplayer

import PodcastEpisode
import RssParser
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.SeekBar
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tthhfugapiyo.turnplayer.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import kotlin.io.path.Path
import kotlin.io.path.extension

const val PREFERENCE_KEY = "PREF_KEY"
const val USE_DND = "use_dnd"
const val SKIP_TIME = "skip_time"
const val SAVE_DIRECTORY = "directory"
const val SAVE_URL = "url"
const val SAVE_FILE = "file"
const val SAVE_TIME = "time"
const val SAVE_POS = "pos"

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private var playbackService: PlaybackService? = null
    private var bound = false
    private lateinit var radioDataList: ArrayList<RadioData>
    private var currentPath = ""
    private lateinit var timer: Timer
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private var errCount = 0

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as PlaybackService.LocalBinder
            playbackService = binder.getService()
            bound = true
            setupServiceCallbacks()
            loadSetting()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
            playbackService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        radioDataList = ArrayList()

        // progressBar
        binding.progressBar.max = 100
        binding.progressBar.visibility = View.GONE

        timer = Timer()
        enableEdgeToEdge()
        setContentView(binding.root)

        // insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Bind PlaybackService
        Intent(this, PlaybackService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        // 設定終了時
        val getContent = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            setButtons()
            loadSetting()
        }

        // button SettingActivity
        binding.buttonSetting.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            getContent.launch(intent)
        }

        // button Play
        binding.buttonPlay.setOnClickListener {
            playbackService?.let {
                if (it.isPlaying) {
                    it.pause()
                    saveString(SAVE_POS, it.currentPosition.toString())
                    binding.buttonPlay.setText(R.string.button_play)
                    binding.buttonPlay.icon = ContextCompat.getDrawable(this, R.drawable.baseline_play_arrow_24)
                } else {
                    unMute()
                    it.start()
                    binding.buttonPlay.setText(R.string.button_pause)
                    binding.buttonPlay.icon = ContextCompat.getDrawable(this, R.drawable.baseline_pause_24)
                }
            }
        }

        binding.buttonForward.setOnClickListener {
            playbackService?.let {
                if (it.isPlaying) {
                    val t = loadSettingInt(SKIP_TIME, 5)
                    it.seekTo(it.currentPosition + t * 1000)
                }
            }
        }

        binding.buttonBackward.setOnClickListener {
            playbackService?.let {
                if (it.isPlaying) {
                    val t = loadSettingInt(SKIP_TIME, 5)
                    it.seekTo(it.currentPosition - t * 1000)
                }
            }
        }

        // button Mute
        setButtons()
        binding.buttonMute.setOnClickListener {
            if (getMuteState()) {
                unMute()
            } else {
                mute()
            }
        }

        // 初回un-mute
        unMute()

        // timer
        val task = object : TimerTask() {
            override fun run() {
                playbackService?.let {
                    if (it.isPlaying) {
                        val pos = it.currentPosition
                        var sec = pos / 1000
                        var min = sec / 60
                        sec %= 60
                        val hour = min / 60
                        min %= 60
                        val posStr = String.format(Locale.US, "%02d:%02d:%02d", hour, min, sec)
                        lifecycleScope.launch(Dispatchers.Main) {
                            binding.seekBar.progress = pos
                            binding.textPos.text = posStr
                        }
                    }
                }
            }
        }
        timer.schedule(task, 1000L, 1000L)

        // seekbar
        binding.seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        playbackService?.seekTo(progress)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )
    }

    private fun setupServiceCallbacks() {
        playbackService?.onPreparedCallback = {
            prepareCompleted()
        }
        playbackService?.onCompletionCallback = {
            errCount = 0
            nextPlay()
        }
        playbackService?.onErrorCallback = { what, extra ->
            var ret = false
            if (errCount < 5) {
                playbackService?.let {
                    saveString(SAVE_POS, it.currentPosition.toString())
                    it.stop()
                }
                resume()
                ret = true
                errCount += 1
            }
            val errorMessage = "Media error: $what ($extra)"
            showErrorMessage("$errorMessage:$errCount:$ret")
            ret
        }
        playbackService?.onBufferingUpdateCallback = { percent ->
            if (percent < 100) {
                binding.progressBar.progress = percent
                binding.progressBar.visibility = View.VISIBLE
            } else {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playbackService?.apply {
            onPreparedCallback = null
            onCompletionCallback = null
            onErrorCallback = null
            onBufferingUpdateCallback = null
        }
        if (bound) {
            unbindService(connection)
            bound = false
        }
        handler.removeCallbacksAndMessages(null)
    }

    override fun onPause() {
        playbackService?.let {
            if (it.isPlaying) {
                saveString(SAVE_POS, it.currentPosition.toString())
            }
        }
        super.onPause()
    }

    private fun checkDoNotDisturbPermission() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun showPermissionExplanationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permission required")
            .setMessage("Grant permission to change Do Not Disturb mode")
            .setPositiveButton("setting") { _, _ ->
                checkDoNotDisturbPermission()
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun getMuteState(): Boolean {
        if (!loadSettingBoolean(USE_DND, false)) {
            return false
        }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        var isMute = false
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            showPermissionExplanationDialog()
        } else {
            try {
                if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                    isMute = true
                }
            } catch (e: SecurityException) {
                showPermissionExplanationDialog()
            }
            try {
                if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                    isMute = true
                }
                if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != maxVolume) {
                    isMute = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return isMute
    }

    @SuppressLint("SetTextI18n")
    private fun setButtons() {
        val setTime = loadSettingInt(SKIP_TIME, 5)
        binding.buttonForward.text = "${setTime}s"
        binding.buttonBackward.text = "${setTime}s"

        if (!loadSettingBoolean(USE_DND, false)) {
            binding.buttonMute.setText(R.string.button_mute_disabled)
            binding.buttonMute.icon = ContextCompat.getDrawable(this, R.drawable.baseline_volume_off_24)
            binding.buttonMute.isEnabled = false
        } else {
            binding.buttonMute.isEnabled = true
            if (getMuteState()) {
                binding.buttonMute.setText(R.string.button_unmute)
                binding.buttonMute.icon = ContextCompat.getDrawable(this, R.drawable.baseline_volume_up_24)
            } else {
                binding.buttonMute.setText(R.string.button_mute)
                binding.buttonMute.icon = ContextCompat.getDrawable(this, R.drawable.baseline_volume_off_24)
            }
        }
    }

    private fun unMute() {
        if (!loadSettingBoolean(USE_DND, false)) {
            binding.buttonMute.setText(R.string.button_mute_disabled)
            binding.buttonMute.icon = ContextCompat.getDrawable(this, R.drawable.baseline_volume_off_24)
            return
        }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            showPermissionExplanationDialog()
        } else {
            try {
                if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                    notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                }
            } catch (e: SecurityException) {
                showPermissionExplanationDialog()
            }
            try {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, AudioManager.FLAG_SHOW_UI)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        binding.buttonMute.setText(R.string.button_mute)
        binding.buttonMute.icon = ContextCompat.getDrawable(this, R.drawable.baseline_volume_off_24)
    }

    private fun mute() {
        if (!loadSettingBoolean(USE_DND, false)) {
            return
        }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            showPermissionExplanationDialog()
        } else {
            try {
                if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE) {
                    notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_NONE
                    )
                }
            } catch (e: SecurityException) {
                showPermissionExplanationDialog()
            }
            try {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        binding.buttonMute.setText(R.string.button_unmute)
        binding.buttonMute.icon = ContextCompat.getDrawable(this, R.drawable.baseline_volume_up_24)
    }

    private var alertDialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private fun showErrorMessage(message: String) {
        alertDialog?.dismiss()
        alertDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }.create()

        alertDialog?.show()
        handler.postDelayed({
            alertDialog?.dismiss()
            alertDialog = null
        }, 5000)
    }

    private fun loadSetting() {
        lifecycleScope.launch {
            binding.loadingSpinner.visibility = View.VISIBLE

            val dir = loadSetting(SAVE_DIRECTORY, "")
            if (dir != "") {
                val list = withContext(Dispatchers.IO) {
                    listDir(dir)
                }
                updateList(list)
            }

            val url = loadSetting(SAVE_URL, "")
            if (url != "") {
                try {
                    val podcasts = withContext(Dispatchers.IO) {
                        fetchRssFeed(url)
                    }
                    val list = radioDataList
                    for (item in podcasts) {
                        val radioData = RadioData(item)
                        list.add(radioData)
                    }
                    updateList(list)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            binding.loadingSpinner.visibility = View.GONE
            resume()
        }
    }

    private suspend fun fetchRssFeed(url: String): List<PodcastEpisode> {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            val rssContent = response.body()?.string() ?: ""
            val parser = RssParser()
            return parser.parse(rssContent)
        } catch (e: Exception) {
            throw e
        }
    }

    private fun saveString(key: String, value: String) {
        val sharedPref = getSharedPreferences(PREFERENCE_KEY, Context.MODE_PRIVATE)
        sharedPref.edit().putString(key, value).apply()
    }

    private fun loadString(key: String, defValue: String): String {
        val sharedPref = getSharedPreferences(PREFERENCE_KEY, Context.MODE_PRIVATE)
        var value: String? = null
        try {
            value = sharedPref.getString(key, defValue)
        } catch (_: Exception) {
        }
        return value ?: defValue
    }

    @Suppress("SameParameterValue")
    private fun loadSetting(key: String, defValue: String): String {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        var value: String? = null
        try {
            value = sharedPref.getString(key, defValue)
        } catch (_: Exception) {
        }
        return value ?: defValue
    }

    @Suppress("SameParameterValue")
    private fun loadSettingBoolean(key: String, defValue: Boolean): Boolean {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        var value: Boolean? = null
        try {
            value = sharedPref.getBoolean(key, defValue)
        } catch (_: Exception) {
        }
        return value ?: defValue
    }

    @Suppress("SameParameterValue")
    private fun loadSettingInt(key: String, defValue: Int): Int {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        var value: Int? = null
        try {
            value = sharedPref.getInt(key, defValue)
        } catch (_: Exception) {
        }
        return value ?: defValue
    }

    private fun dirFiles(dir: DocumentFile): ArrayList<RadioData> {
        val ary: ArrayList<RadioData> = arrayListOf()
        dir.listFiles().iterator().forEach {
            if (it.isFile) {
                if (Path(it.name!!).extension == "m4a") {
                    val dat = RadioData(it)
                    ary.add(dat)
                }
            }
            if (it.isDirectory) {
                ary.addAll(dirFiles(it))
            }
        }
        return ary
    }

    private fun listDir(pathStr: String): ArrayList<RadioData> {
        val uri = Uri.parse(pathStr)
        val dir = DocumentFile.fromTreeUri(this, uri)
        return dir?.let { dirFiles(it) } ?: arrayListOf()
    }

    private fun updateList(list: ArrayList<RadioData>) {
        list.sortBy {
            it.time()
        }
        val adapter = MyRecyclerViewAdapter(list)
        adapter.setOnCellClickListener(
            object : MyRecyclerViewAdapter.OnCellClickListener {
                override fun onItemClick(radioData: RadioData) {
                    radioPlay(radioData, 0)
                }
            }
        )
        binding.recyclerview.adapter = adapter
        binding.recyclerview.layoutManager = LinearLayoutManager(this)
        radioDataList = list
        resumeFile()
    }

    private fun resumeFile(): Int {
        var idx = -1
        var idx2 = -1
        val list = radioDataList
        val saveFile = loadString(SAVE_FILE, "")
        val saveTime = loadString(SAVE_TIME, "0000/00/00 00:00:00")
        val p = binding.recyclerview.adapter
        if (p != null) {
            val adapter: MyRecyclerViewAdapter = p as MyRecyclerViewAdapter
            list.forEach {
                if (it.isSaveFile(saveFile)) {
                    idx = list.indexOf(it)
                    adapter.changeSelection(idx)
                    return idx
                }
                if (idx2 == -1 && it.getTime() > saveTime) {
                    idx2 = list.indexOf(it)
                }
            }
            if (idx2 != -1) {
                adapter.changeSelection(idx)
            }
        }
        return idx2
    }

    private fun resume(): Int { // wait, return type Int or Unit
        val idx = resumeFile()
        if (idx != -1) {
            val saveFile = loadString(SAVE_FILE, "")
            var savePos = loadString(SAVE_POS, "0").toInt()
            val radioData = radioDataList[idx]
            if (radioData.getSaveFile() != saveFile) {
                savePos = 0
            }
            radioPlay(radioData, savePos)
        }
        return idx
    }

    private fun scrollTo(radioData: RadioData) {
        var idx = -1
        for ((i, elem) in radioDataList.withIndex()) {
            if (radioData.getSaveFile() == elem.getSaveFile()) {
                idx = i
                break
            }
        }
        if (idx >= 0) {
            binding.recyclerview.smoothScrollToPosition(idx)
        }
    }

    private fun radioPlay(radioData: RadioData, pos: Int) {
        scrollTo(radioData)
        playbackService?.let {
            try {
                it.stop()
            } catch (_: Exception) {}

            val intent = Intent(this, PlaybackService::class.java)
            startForegroundService(intent)

            currentPath = radioData.getSaveFile()
            saveString(SAVE_FILE, currentPath)
            saveString(SAVE_TIME, radioData.getTime())
            if (pos == 0) {
                binding.seekBar.progress = 0
                saveString(SAVE_POS, "0")
            }
            binding.progressBar.visibility = View.GONE
            it.play(radioData.uri(), radioData.url())
        }
    }

    private fun prepareCompleted() {
        playbackService?.let {
            binding.seekBar.max = it.duration
            val savePos = loadString(SAVE_POS, "0").toInt()
            if (savePos != 0) {
                it.seekTo(savePos)
            }
            it.start()
            binding.buttonPlay.setText(R.string.button_pause)
            binding.buttonPlay.icon = ContextCompat.getDrawable(this, R.drawable.baseline_pause_24)
        }
    }

    private fun nextPlay() {
        playbackService?.stop()
        var idx = -1
        for ((i, elem) in radioDataList.withIndex()) {
            if (elem.getSaveFile() == currentPath) {
                idx = i
                break
            }
        }
        if (idx != -1) {
            idx += 1
            if (radioDataList.size > idx) {
                radioPlay(radioDataList[idx], 0)
                val adapter: MyRecyclerViewAdapter = binding.recyclerview.adapter as MyRecyclerViewAdapter
                adapter.changeSelection(idx)
            }
        }
    }
}
