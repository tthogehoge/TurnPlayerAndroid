package com.example.myapplication

import PodcastEpisode
import RssParser
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
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
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
const val SAVE_DIRECTORY = "directory"
const val SAVE_URL = "url"
const val SAVE_FILE = "file"
const val SAVE_TIME = "time"
const val SAVE_POS = "pos"

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private var mediaPlayer : MediaPlayer? = null
    private lateinit var radioDataList: ArrayList<RadioData>
    private var currentPath = ""
    private lateinit var timer: Timer
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private var errCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        radioDataList = ArrayList()
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setOnPreparedListener {
            prepareCompleted()
        }
        // progressBar
        binding.progressBar.max = 100
        binding.progressBar.visibility = View.GONE
        mediaPlayer?.setOnBufferingUpdateListener { _, percent ->
            if(percent<100){
                binding.progressBar.progress = percent
                binding.progressBar.visibility = View.VISIBLE
            }else {
                binding.progressBar.visibility = View.GONE
            }
        }
        mediaPlayer?.setOnCompletionListener {
            // 正常に終了したらエラーカウントリセット
            errCount=0
            nextPlay()
        }
        mediaPlayer?.setOnErrorListener { mp, what, extra ->
            var ret = false
            // エラーだったらretry 5回
            if(errCount < 5) {
                // 今の位置を覚えてresume
                saveString(SAVE_POS, mp.currentPosition.toString())
                mp.stop()
                mp.reset()
                resume()
                ret = true
                errCount+=1
            }

            val errorMessage = when (what) {
                MediaPlayer.MEDIA_ERROR_UNKNOWN -> {
                    when (extra) {
                        MediaPlayer.MEDIA_ERROR_IO -> "Media error: IO error"
                        MediaPlayer.MEDIA_ERROR_MALFORMED -> "Media error: Malformed"
                        MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "Media error: Unsupported"
                        MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "Media error: Timed out"
                        else -> "An unknown media error occurred"
                    }
                }
                MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "Media server died"
                else -> "An unknown error occurred"
            }

            // Display the error message to the user
            showErrorMessage("$errorMessage:$errCount:$ret")

            ret
        }
        timer = Timer()

        // 親クラス
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // アクティビティ
        setContentView(binding.root)

        // insets ?
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 設定終了時
        val getContent = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            setMuteButton()
            loadSetting()
        }

        // button SettingActivity
        binding.buttonSetting.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            getContent.launch(intent)
        }

        // button Play
        binding.buttonPlay.setOnClickListener {
            mediaPlayer?.let{
                if(it.isPlaying){
                    it.pause()
                    saveString(SAVE_POS, it.currentPosition.toString())
                    binding.buttonPlay.setText(R.string.button_play)
                    binding.buttonPlay.icon = ContextCompat.getDrawable(this, R.drawable.baseline_play_arrow_24)
                }else{
                    unMute()
                    it.start()
                    binding.buttonPlay.setText(R.string.button_pause)
                    binding.buttonPlay.icon = ContextCompat.getDrawable(this, R.drawable.baseline_pause_24)
                }
            }
        }

        // button Mute
        setMuteButton()
        binding.buttonMute.setOnClickListener {
            if(getMuteState()){
                unMute()
            }else{
                mute()
            }
        }

        // 初回un-mute
        unMute()

        // timer
        val task = object : TimerTask() {
            override fun run() {
                mediaPlayer?.let{
                    if(it.isPlaying){
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
                    if(fromUser){
                        mediaPlayer?.seekTo(progress)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                }
            }
        )

        // 設定ロード
        loadSetting()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.let{
            if(it.isPlaying){
                saveString(SAVE_POS, it.currentPosition.toString())
                it.stop()
            }
            it.release()
            mediaPlayer = null
        }
        handler.removeCallbacksAndMessages(null)
    }

    override fun onPause() {
        mediaPlayer?.let{
            if(it.isPlaying){
                saveString(SAVE_POS, it.currentPosition.toString())
            }
        }
        super.onPause()
    }

    private fun checkDoNotDisturbPermission() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 権限チェック
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            // 権限がない場合、設定画面を開く
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

    private fun getMuteState() : Boolean {
        if(!loadSettingBoolean(USE_DND, false)){
            return false
        }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        var isMute = false
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            showPermissionExplanationDialog()
        } else {
            // マナーモード設定の変更処理
            try {
                if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL ){
                    isMute = true
                }
            } catch (e: SecurityException) {
                showPermissionExplanationDialog()
            }
            try{
                if(audioManager.ringerMode!=AudioManager.RINGER_MODE_NORMAL){
                    isMute = true
                }
                if(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != maxVolume){
                    isMute = true
                }
            }catch(e:Exception){
                e.printStackTrace()
            }
        }
        return isMute
    }

    private fun setMuteButton() {
        if(!loadSettingBoolean(USE_DND, false)){
            binding.buttonMute.setText(R.string.button_mute_disabled)
            binding.buttonMute.icon = ContextCompat.getDrawable(this, R.drawable.baseline_volume_off_24)
            binding.buttonMute.isEnabled = false
            return
        }
        binding.buttonMute.isEnabled = true
        if(getMuteState()){
            binding.buttonMute.setText(R.string.button_unmute)
            binding.buttonMute.icon = ContextCompat.getDrawable(this, R.drawable.baseline_volume_up_24)
        }else{
            binding.buttonMute.setText(R.string.button_mute)
            binding.buttonMute.icon = ContextCompat.getDrawable(this, R.drawable.baseline_volume_off_24)
        }
    }

    private fun unMute() {
        if(!loadSettingBoolean(USE_DND, false)){
            binding.buttonMute.setText(R.string.button_mute_disabled)
            binding.buttonMute.icon = ContextCompat.getDrawable(this, R.drawable.baseline_volume_off_24)
            return
        }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            showPermissionExplanationDialog()
        } else {
            // マナーモード設定の変更処理
            try {
                if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL ){
                    notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                }
            } catch (e: SecurityException) {
                showPermissionExplanationDialog()
            }
            try{
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, AudioManager.FLAG_SHOW_UI)
            }catch(e:Exception){
                e.printStackTrace()
            }
        }
        binding.buttonMute.setText(R.string.button_mute)
        binding.buttonMute.icon = ContextCompat.getDrawable(this, R.drawable.baseline_volume_off_24)
    }

    private fun mute() {
        if(!loadSettingBoolean(USE_DND, false)){
            return
        }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            showPermissionExplanationDialog()
        } else {
            // マナーモード設定の変更処理
            try {
                if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE ){
                    notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_NONE
                    )
                }
            } catch (e: SecurityException) {
                showPermissionExplanationDialog()
            }
            try{
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            }catch(e:Exception){
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
                alertDialog=null
        }, 5000)
    }

    private fun loadSetting() {
        loadSettingLoadDir()
        val url = loadSetting(SAVE_URL, "")
        if(url!=""){
            loadSettingFetchPodcasts(url)
            // getUrl("http://abehiroshi.la.coocan.jp/menu.htm")
            // https://feeds.megaphone.fm/TBS4550274867
        }else{
            binding.loadingSpinner.visibility = View.GONE
            resume()
        }
    }

    private fun loadSettingLoadDir() {
        val dir = loadSetting(SAVE_DIRECTORY, "")
        if(dir!=""){
            binding.loadingSpinner.visibility = View.VISIBLE
            val list = listDir(dir)
            updateList(list)
        }
    }

    private fun loadSettingFetchPodcasts(url:String) {
        lifecycleScope.launch {
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
            }catch (e:Exception){
                e.printStackTrace()
            }
            binding.loadingSpinner.visibility = View.GONE
            resume()
        }

    }

    // ネットワークリクエストの実装
    private suspend fun fetchRssFeed(url:String) : List<PodcastEpisode> {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                //.url("https://feeds.megaphone.fm/TBS4550274867")
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            val rssContent = response.body()?.string() ?: ""
            val parser = RssParser()
            val podcasts = parser.parse(rssContent)

            /*
            // パースした結果を使用
            podcasts.forEach { episode ->
                println("Title: ${episode.title}")
                println("Description: ${episode.description}")
                println("Published: ${episode.pubDate}")
                println("Duration: ${episode.duration}")
                println("URL: ${episode.url}")
                println("-------------------")
            }
             */
            return podcasts

        } catch (e: Exception) {
            throw e
        }
    }

    // save
    private fun saveString(key: String, value: String) {
        val sharedPref = getSharedPreferences(PREFERENCE_KEY, Context.MODE_PRIVATE)
        sharedPref.edit().putString(key, value).apply()
    }

    // load
    private fun loadString(key: String, defValue: String): String {
        val sharedPref = getSharedPreferences(PREFERENCE_KEY, Context.MODE_PRIVATE)
        var value:String? = null
        try {
            value = sharedPref.getString(key,defValue)
        } catch(_: Exception) {
        }
        if(value != null){
            return value
        }
        return defValue
    }

    // loadSetting
    @Suppress("SameParameterValue")
    private fun loadSetting(key: String, defValue: String): String {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        var value:String? = null
        try {
            value = sharedPref.getString(key,defValue)
        } catch(_: Exception) {
        }
        if(value != null){
            return value
        }
        return defValue
    }

    // loadSettingBoolean
    @Suppress("SameParameterValue")
    private fun loadSettingBoolean(key: String, defValue: Boolean): Boolean {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        var value:Boolean? = null
        try {
            value = sharedPref.getBoolean(key,defValue)
        } catch(_: Exception) {
        }
        if(value != null){
            return value
        }
        return defValue
    }

    private fun dirFiles(dir: DocumentFile): ArrayList<RadioData> {
        val ary: ArrayList<RadioData> = arrayListOf()
        dir.listFiles().iterator().forEach {
            if(it.isFile){
                if( Path(it.name!!).extension == "m4a" ) {
                    val dat = RadioData(it)
                    ary.add(dat)
                }
            }
            if(it.isDirectory){
                ary.addAll(dirFiles(it))
            }
        }
        return ary
    }

    private fun listDir(pathStr: String):ArrayList<RadioData> {
        val uri = Uri.parse(pathStr)
        /*
        contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
         */
        val dir = DocumentFile.fromTreeUri(this, uri)
        val list = dirFiles(dir!!)
        return list
    }

    private fun updateList(list: ArrayList<RadioData>){
        list.sortBy{
            it.time()
        }
        val adapter = MyRecyclerViewAdapter(list)
        adapter.setOnCellClickListener(
            object: MyRecyclerViewAdapter.OnCellClickListener {
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

    // saveされたファイルを探して選択する
    private fun resumeFile():Int{
        var idx = -1
        var idx2 = -1
        val list = radioDataList
        val saveFile = loadString(SAVE_FILE, "")
        val saveTime = loadString(SAVE_TIME, "0000/00/00 00:00:00")
        val p = binding.recyclerview.adapter
        if(p!=null) {
            val adapter: MyRecyclerViewAdapter = p as MyRecyclerViewAdapter
            list.forEach {
                // SAVE_FILEが見つかったらそれ
                if (it.isSaveFile(saveFile)) {
                    idx = list.indexOf(it)
                    adapter.changeSelection(idx)
                    return idx
                }
                // SAVE_FILEが見つからなかったらその次の日付のファイルにする
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

    private fun resume(){
        val idx = resumeFile()
        if(idx!=-1){
            val saveFile = loadString(SAVE_FILE, "")
            var savePos = loadString(SAVE_POS, "0").toInt()
            val radioData = radioDataList[idx]
            if(radioData.getSaveFile()!=saveFile){
                savePos = 0
            }
            radioPlay(radioData, savePos)
        }
    }

    private fun scrollTo(radioData:RadioData){
        var idx = -1
        for((i,elem) in radioDataList.withIndex()){
            if(radioData.getSaveFile() == elem.getSaveFile()){
                idx = i
                break
            }
        }
        if(idx>=0){
            binding.recyclerview.smoothScrollToPosition(idx)
        }
    }

    private fun radioPlay(radioData: RadioData, pos:Int) {
        scrollTo(radioData)
        mediaPlayer?.let{
            var resetPlayer = false
            if(it.isPlaying){
                resetPlayer = true
            }else if(currentPath != radioData.getSaveFile()){
                // pauseだとisPlayingに引っかからない
                // stop/resetしないままだとplayできないので、
                // とりあえずクリックした番組が変わったらいったん止める
                resetPlayer = true
            }
            if(resetPlayer){
                try {
                    it.stop()
                    it.reset()
                }catch(_: Exception) {
                }
            }
            try {
                val uri = radioData.uri()
                val url = radioData.url()
                if(uri!=null){
                    it.setDataSource(this, uri)
                }else if(url!=null){
                    it.setDataSource(url)
                }
                currentPath = radioData.getSaveFile()
                saveString(SAVE_FILE, currentPath)
                saveString(SAVE_TIME, radioData.getTime())
                if (pos == 0) {
                    binding.seekBar.progress = 0
                    saveString(SAVE_POS, 0.toString())
                }
                binding.progressBar.visibility = View.GONE
                it.prepareAsync() // -> prepareCompleted
            }catch(e: Exception){
                e.printStackTrace()
                e.message?.let{msg->
                    showErrorMessage(msg)
                }
            }
        }
    }

    private fun prepareCompleted() {
        mediaPlayer?.let {
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
        mediaPlayer?.let{
            it.stop()
            it.reset()
        }
        var idx=-1
        for((i,elem)in radioDataList.withIndex()){
            if( elem.getSaveFile()==currentPath ){
                idx = i
                break
            }
        }
        if(idx!=-1){
            idx += 1
            if(radioDataList.size>idx){
                radioPlay(radioDataList[idx], 0)
                val adapter:MyRecyclerViewAdapter = binding.recyclerview.adapter as MyRecyclerViewAdapter
                adapter.changeSelection(idx)
            }
        }
    }
}
