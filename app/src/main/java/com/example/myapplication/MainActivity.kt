package com.example.myapplication

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.io.File
import java.util.Timer
import java.util.TimerTask
import kotlin.io.path.Path
import kotlin.io.path.extension

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private val PREFERENCE_KEY = "PREF_KEY"
    private val SAVE_DIRECTORY = "directory"
    private val SAVE_FILE = "file"
    private val SAVE_POS = "pos"
    private val PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 100
    private var mediaPlayer : MediaPlayer? = null
    private lateinit var radioList: ArrayList<RadioData>
    private var currentPath = ""
    private lateinit var timer: Timer

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        radioList = ArrayList()
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setOnCompletionListener { nextPlay() }
        timer = Timer()

        // 親クラス
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        /*
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
        */

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
            loadDir()
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
        timer.scheduleAtFixedRate(task, 1000L, 1000L)

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
        loadDir()
    }

    override fun onPause() {
        mediaPlayer?.let{
            if(it.isPlaying){
                saveString(SAVE_POS, it.currentPosition.toString())
            }
        }
        super.onPause()
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

    private fun loadDir() {
        val dir = loadSetting(SAVE_DIRECTORY, "")
        if(dir!=""){
            val list = listDir(dir)
            val saveFile = loadString(SAVE_FILE, "")
            val savePos = loadString(SAVE_POS, "0").toInt()
            list.forEach {
                if( it.documentFile.uri.path == saveFile ){
                    val idx = list.indexOf(it)
                    radioPlay(it, savePos)
                    val adapter:MyRecyclerViewAdapter = binding.recyclerview.adapter as MyRecyclerViewAdapter
                    adapter.changeSelection(idx)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    loadDir()
                }
                return
            }
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
        val regex = "(\\d{4}\\d{2}\\d{2}\\d{2}\\d{2}\\d{2})".toRegex()
        list.sortBy{
            val txt = it.documentFile.uri.path
            var ret = ""
            if(txt!=null){
                val result:MatchResult? = regex.find(txt)
                if(result!=null) {
                    if(result.groupValues.isNotEmpty()){
                        ret = result.groupValues[0]
                    }
                }
            }
            ret
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
        radioList = list
        return list
    }

    private fun setCurrent(radioData:RadioData){
        var idx = -1
        for((i,elem) in radioList.withIndex()){
            if(radioData.documentFile.uri.path.toString() == elem.documentFile.uri.path.toString()){
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
            it.setDataSource(this, radioData.documentFile.uri)
            it.prepare()
            binding.seekBar.max = it.duration
            currentPath = radioData.documentFile.uri.path.toString()
            saveString(SAVE_FILE, currentPath)
            if(pos==0){
                binding.seekBar.progress = 0
                saveString(SAVE_POS, pos.toString())
            }else{
                it.seekTo(pos)
            }
            it.start()
            binding.buttonPlay.setText(R.string.button_pause)
        }
    }

    private fun nextPlay() {
        mediaPlayer?.let{
            it.stop()
            it.reset()
        }
        var idx=-1
        for((i,elem)in radioList.withIndex()){
            if( elem.documentFile.uri.path.toString()==currentPath ){
                idx = i;
                break
            }
        }
        if(idx!=-1){
            idx = idx + 1
            if(radioList.size>idx){
                radioPlay(radioList[idx], 0)
                val adapter:MyRecyclerViewAdapter = binding.recyclerview.adapter as MyRecyclerViewAdapter
                adapter.changeSelection(idx)
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}