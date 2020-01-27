package org.decsync.sparss.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.github.paolorotolo.appintro.AppIntro2
import com.github.paolorotolo.appintro.ISlidePolicy
import kotlinx.android.synthetic.main.activity_intro_directory.*
import org.decsync.library.DecsyncPrefUtils
import org.decsync.sparss.R
import org.decsync.sparss.utils.DecsyncUtils
import org.decsync.sparss.utils.PrefUtils

@ExperimentalStdlibApi
class IntroActivity : AppIntro2() {
    private val slideDirectory = SlideDirectory()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wizardMode = true
        backButtonVisibilityWithDone = true

        addSlide(SlideWelcome())
        addSlide(slideDirectory)
    }

    @ExperimentalStdlibApi
    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)

        PrefUtils.putBoolean(PrefUtils.INTRO_DONE, true)

        val decsyncEnabled = slideDirectory.decsyncEnabled
        PrefUtils.putBoolean(PrefUtils.DECSYNC_ENABLED, decsyncEnabled)
        if (decsyncEnabled) {
            DecsyncUtils.initSync(this)
        }

        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}

class SlideWelcome : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_intro_welcome, container, false)
    }
}

@ExperimentalStdlibApi
class SlideDirectory : Fragment(), ISlidePolicy {
    var decsyncEnabled = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.activity_intro_directory, container, false)

        val directoryButton = view.findViewById<Button>(R.id.intro_directory_button)
        val decsyncDir = DecsyncPrefUtils.getDecsyncDir(activity!!)
        if (decsyncDir != null) {
            directoryButton.text = decsyncDir.name
        }
        directoryButton.setOnClickListener {
            DecsyncPrefUtils.chooseDecsyncDir(this)
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

        DecsyncPrefUtils.chooseDecsyncDirResult(activity!!, requestCode, resultCode, data) { decsyncDir ->
            intro_directory_button.text = decsyncDir.name
        }
    }

    override fun isPolicyRespected(): Boolean = !decsyncEnabled ||
            DecsyncPrefUtils.getDecsyncDir(activity!!) != null

    override fun onUserIllegallyRequestedNextPage() {
        Toast.makeText(activity!!, "Please select a DecSync directory", Toast.LENGTH_SHORT).show()
    }
}