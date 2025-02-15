package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun onDirectorySet(dir: String) {
        val contentResolver = applicationContext.contentResolver
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // Check for the freshest data.
        val uri = Uri.parse(dir)
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            // directory picker activity call
            activityResultLauncher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                    result: ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK){
                    val resultData = result.data
                    if(resultData!=null){
                        val uri: Uri? = resultData.data
                        val dir = uri.toString()
                        val myPreference = findPreference<EditTextPreference>("directory")
                        myPreference?.text = dir
                    }
                }
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val myPreference = findPreference<Preference>("setting_directory")
            myPreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference ->
                // directory picker activity
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

                activityResultLauncher.launch(intent)
                false
            }

            myPreference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
                val dir = newValue.toString()
                val act:SettingsActivity = activity as SettingsActivity
                act.onDirectorySet(dir)
                true
            }

        }
    }
}