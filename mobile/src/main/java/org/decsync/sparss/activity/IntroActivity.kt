package org.decsync.sparss.activity

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro2
import com.github.appintro.SlidePolicy
import com.nononsenseapps.filepicker.FilePickerActivity
import com.nononsenseapps.filepicker.Utils
import kotlinx.android.synthetic.main.activity_intro_directory.*
import org.decsync.library.DecsyncPrefUtils
import org.decsync.library.checkDecsyncInfo
import org.decsync.sparss.R
import org.decsync.sparss.utils.DecsyncUtils
import org.decsync.sparss.utils.PrefUtils
import org.decsync.sparss.utils.defaultDecsyncDir
import java.io.File

const val CHOOSE_DECSYNC_FILE = 0
const val PERMISSIONS_REQUEST_INTRO_DONE = 20

@ExperimentalStdlibApi
class IntroActivity : AppIntro2() {
    private val slideDirectory = SlideDirectory()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isWizardMode = true
        showStatusBar(true)

        addSlide(SlideWelcome())
        addSlide(slideDirectory)
    }

    override fun onIntroFinished() {
        super.onIntroFinished()

        PrefUtils.putBoolean(PrefUtils.INTRO_DONE, true)

        val decsyncEnabled = slideDirectory.decsyncEnabled
        PrefUtils.putBoolean(PrefUtils.DECSYNC_ENABLED, decsyncEnabled)
        if (decsyncEnabled) {
            DecsyncUtils.initSync(this)
        }

        PrefUtils.updateAutomaticRefresh(this, null, null, null)

        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSIONS_REQUEST_INTRO_DONE -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onIntroFinished()
            }
        }
    }
}

class SlideWelcome : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_intro_welcome, container, false)
    }
}

@ExperimentalStdlibApi
class SlideDirectory : Fragment(), SlidePolicy {
    var decsyncEnabled = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.activity_intro_directory, container, false)

        val directoryButton = view.findViewById<Button>(R.id.intro_directory_button)
        if (PrefUtils.getBoolean(PrefUtils.DECSYNC_USE_SAF, false)) {
            val decsyncDir = DecsyncPrefUtils.getDecsyncDir(requireActivity())
            if (decsyncDir != null) {
                val name = DecsyncPrefUtils.getNameFromUri(requireActivity(), decsyncDir)
                directoryButton.text = name
            }
            directoryButton.setOnClickListener {
                DecsyncPrefUtils.chooseDecsyncDir(this)
            }
        } else {
            val decsyncDir = File(PrefUtils.getString(PrefUtils.DECSYNC_FILE, defaultDecsyncDir))
            val name = decsyncDir.path
            directoryButton.text = name
            directoryButton.setOnClickListener {
                val intent = Intent(requireActivity(), FilePickerActivity::class.java)
                intent.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true)
                intent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR)
                intent.putExtra(FilePickerActivity.EXTRA_START_PATH, decsyncDir.path)
                startActivityForResult(intent, CHOOSE_DECSYNC_FILE)
            }
        }

        val decsyncCheckbox = view.findViewById<CheckBox>(R.id.intro_directory_checkbox)
        decsyncCheckbox.setOnCheckedChangeListener { _, enabled ->
            decsyncEnabled = enabled

            val alpha = (if (enabled) 1.0 else 0.75).toFloat()
            intro_directory_folder.alpha = alpha
            intro_directory_button.isEnabled = enabled
            intro_directory_desc.alpha = alpha
        }

        return view
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (PrefUtils.getBoolean(PrefUtils.DECSYNC_USE_SAF, false)) {
            DecsyncPrefUtils.chooseDecsyncDirResult(requireActivity(), requestCode, resultCode, data) { uri ->
                val name = DecsyncPrefUtils.getNameFromUri(requireActivity(), uri)
                intro_directory_button.text = name
            }
        } else {
            if (requestCode == CHOOSE_DECSYNC_FILE) {
                val uri = data?.data
                if (resultCode == Activity.RESULT_OK && uri != null) {
                    val oldDir = PrefUtils.getString(PrefUtils.DECSYNC_FILE, defaultDecsyncDir)
                    val newDir = Utils.getFileForUri(uri).path
                    if (oldDir != newDir) {
                        checkDecsyncInfo(File(newDir))
                        PrefUtils.putString(PrefUtils.DECSYNC_FILE, newDir)
                        intro_directory_button.text = newDir
                    }
                }
            }
        }
    }

    override val isPolicyRespected: Boolean
        get() {
            if (!decsyncEnabled) return true
            return if (PrefUtils.getBoolean(PrefUtils.DECSYNC_USE_SAF, false)) {
                DecsyncPrefUtils.getDecsyncDir(requireActivity()) != null
            } else {
                ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }

    override fun onUserIllegallyRequestedNextPage() {
        if (PrefUtils.getBoolean(PrefUtils.DECSYNC_USE_SAF, false)) {
            Toast.makeText(requireActivity(), R.string.intro_directory_select, Toast.LENGTH_SHORT).show()
        } else {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSIONS_REQUEST_INTRO_DONE)
        }
    }
}