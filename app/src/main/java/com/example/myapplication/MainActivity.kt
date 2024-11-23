package com.example.myapplication

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.widget.Button
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.extension

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding;
    private val PREFERENCE_KEY = "PREF_KEY"
    private val SAVE_PATH = "path"
    private val PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 100
    private val PERMISSIONS_REQUEST_READ_MEDIA_AUDIO = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                // 許可されている
                loadDir()
            } else {
                // 許可されていないので許可ダイアログを表示する
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
            }
        }

        // directory picker activity
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        // directory picker activity call
        val getContent = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
                result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK){
                val resultData = result.data
                if(resultData!=null){
                    val uri: Uri? = resultData.data
                    val dir = uri.toString()

                    // show uri
                    binding.textView.text = dir
                    // save path
                    saveString(SAVE_PATH, dir)

                    val contentResolver = applicationContext.contentResolver
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    // Check for the freshest data.
                    if (uri != null) {
                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                    }
                    listDir(dir)
                }
            }
        }

        // button
        binding.button.setOnClickListener {
            binding.textView.text = getString(R.string.test_text)
            getContent.launch(intent)
        }

    }

    private fun loadDir() {
        val dir = loadString(SAVE_PATH, "")
        binding.textView.text = dir;
        if(dir!=""){
            listDir(dir)
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
        binding.recyclerview.adapter = MyRecyclerViewAdapter(list)
        binding.recyclerview.layoutManager = LinearLayoutManager(this)
        return list
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