package com.example.myapplication

import PodcastEpisode
import RssParser
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Timer
import java.util.TimerTask
import kotlin.io.path.Path
import kotlin.io.path.extension

const val PREFERENCE_KEY = "PREF_KEY"
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

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        radioDataList = ArrayList()
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setOnCompletionListener { nextPlay() }
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
                }else{
                    it.start()
                    binding.buttonPlay.setText(R.string.button_pause)
                }
            }
        }

        // button Stop
        binding.buttonStop.setOnClickListener {
            mediaPlayer?.let{
                saveString(SAVE_POS, it.currentPosition.toString())
                it.stop()
            }
        }

        // timer
        val task = object : TimerTask() {
            @SuppressLint("DefaultLocale")
            override fun run() {
                mediaPlayer?.let{
                    if(it.isPlaying){
                        val pos = it.currentPosition
                        var sec = pos / 1000
                        var min = sec / 60
                        sec %= 60
                        val hour = min / 60
                        min %= 60
                        val posStr = String.format("%02d:%02d:%02d", hour, min, sec)
                        binding.seekBar.progress = pos
                        binding.textPos.text = posStr
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
        mediaPlayer?.let{
            if(it.isPlaying){
                saveString(SAVE_POS, it.currentPosition.toString())
                it.stop()
            }
            it.release()
            mediaPlayer = null
        }
        super.onDestroy()
    }

    override fun onPause() {
        mediaPlayer?.let{
            if(it.isPlaying){
                saveString(SAVE_POS, it.currentPosition.toString())
            }
        }
        super.onPause()
    }

    private fun loadSetting() {
        loadDir()
        val url = loadSetting(SAVE_URL, "")
        if(url!=""){
            fetchPodcasts(url)
            // getUrl("http://abehiroshi.la.coocan.jp/menu.htm")
            // https://feeds.megaphone.fm/TBS4550274867
        }else{
            resume()
        }
    }

    private fun fetchPodcasts(url:String) {
        lifecycleScope.launch {
            try {
                val podcasts = withContext(Dispatchers.IO) {
                    fetchRssFeed(url)
                }
                setPodcasts(podcasts)
            }catch (e:Exception){
                e.printStackTrace()
            }
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

    private fun setPodcasts(podcasts:List<PodcastEpisode>){
        val list = radioDataList
        for (item in podcasts) {
            val radioData = RadioData(item)
            list.add(radioData)
        }
        updateList(list)
    }

    private fun loadDir() {
        val dir = loadSetting(SAVE_DIRECTORY, "")
        if(dir!=""){
            val list = listDir(dir)
            updateList(list)
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
        val adapter:MyRecyclerViewAdapter = binding.recyclerview.adapter as MyRecyclerViewAdapter
        list.forEach {
            // SAVE_FILEが見つかったらそれ
            if( it.isSaveFile(saveFile) ){
                idx = list.indexOf(it)
                adapter.changeSelection(idx)
                return idx
            }
            // SAVE_FILEが見つからなかったらその次の日付のファイルにする
            if( idx2==-1 && it.getTime() > saveTime ){
                idx2 = list.indexOf(it)
            }
        }
        if(idx2!=-1){
            adapter.changeSelection(idx)
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

    private fun setCurrent(radioData:RadioData){
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
        setCurrent(radioData)
        mediaPlayer?.let{
            if(it.isPlaying){
                it.stop()
                it.reset()
            }
            try {
                val uri = radioData.uri()
                val url = radioData.url()
                if(uri!=null){
                    it.setDataSource(this, uri)
                }else if(url!=null){
                    it.setDataSource(url)
                }
                it.prepare()
                binding.seekBar.max = it.duration
                currentPath = radioData.getSaveFile()
                saveString(SAVE_FILE, currentPath)
                saveString(SAVE_TIME, radioData.getTime())
                if (pos == 0) {
                    binding.seekBar.progress = 0
                    saveString(SAVE_POS, pos.toString())
                } else {
                    it.seekTo(pos)
                }
                it.start()
                binding.buttonPlay.setText(R.string.button_pause)
            }catch(_: Exception){
            }
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
