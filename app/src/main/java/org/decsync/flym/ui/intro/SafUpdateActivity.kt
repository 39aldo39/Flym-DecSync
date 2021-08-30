package org.decsync.flym.ui.intro

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro2
import com.github.appintro.SlidePolicy
import kotlinx.android.synthetic.main.activity_saf_update.*
import net.fred.feedex.R
import org.decsync.flym.data.utils.PrefConstants
import org.decsync.flym.ui.main.MainActivity
import org.decsync.flym.utils.putPrefBoolean
import org.decsync.library.DecsyncPrefUtils

@ExperimentalStdlibApi
class SafUpdateActivity : AppIntro2() {
    private val slideSafDirectory = SlideSafDirectory()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isSkipButtonEnabled = false
        isIndicatorEnabled = false
        showStatusBar(true)

        addSlide(slideSafDirectory)
    }

    override fun onIntroFinished() {
        super.onIntroFinished()

        putPrefBoolean(PrefConstants.UPDATE_FORCES_SAF, false)

        val decsyncEnabled = slideSafDirectory.decsyncEnabled
        putPrefBoolean(PrefConstants.DECSYNC_ENABLED, decsyncEnabled)

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}

@ExperimentalStdlibApi
class SlideSafDirectory : Fragment(), SlidePolicy {
    var decsyncEnabled = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.activity_saf_update, container, false)

        val button = view.findViewById<Button>(R.id.saf_update_button)
        val decsyncDir = DecsyncPrefUtils.getDecsyncDir(requireActivity())
        if (decsyncDir != null) {
            val name = DecsyncPrefUtils.getNameFromUri(requireActivity(), decsyncDir)
            button.text = name
        }
        button.setOnClickListener {
            DecsyncPrefUtils.chooseDecsyncDir(this)
        }

        val checkbox = view.findViewById<CheckBox>(R.id.saf_update_checkbox)
        checkbox.setOnCheckedChangeListener { _, enabled ->
            decsyncEnabled = enabled

            val alpha = (if (enabled) 1.0 else 0.75).toFloat()
            saf_update_folder.alpha = alpha
            saf_update_button.isEnabled = enabled
            saf_update_desc.alpha = alpha
        }

        return view
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        DecsyncPrefUtils.chooseDecsyncDirResult(requireActivity(), requestCode, resultCode, data) { uri ->
            val name = DecsyncPrefUtils.getNameFromUri(requireActivity(), uri)
            saf_update_button.text = name
        }
    }

    override val isPolicyRespected: Boolean
        get() {
            if (!decsyncEnabled) return true
            return DecsyncPrefUtils.getDecsyncDir(requireActivity()) != null
        }

    override fun onUserIllegallyRequestedNextPage() {
        Toast.makeText(requireActivity(), R.string.intro_directory_select, Toast.LENGTH_SHORT).show()
    }
}