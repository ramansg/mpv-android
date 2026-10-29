package `is`.xyz.mpv

import `is`.xyz.filepicker.DocumentPickerFragment
import `is`.xyz.mpv.preferences.PreferenceActivity
import `is`.xyz.mpv.databinding.MainScreenContentBinding
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import java.util.Locale
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.fragment.app.Fragment

class MainScreenFragment : Fragment(R.layout.fragment_main_screen) {
    private lateinit var binding: MainScreenContentBinding

    private lateinit var documentTreeOpener: ActivityResultLauncher<Uri?>
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var playerLauncher: ActivityResultLauncher<Intent>

    private var firstRun = false
    private var returningFromPlayer = false

    private var prev = ""
    private var prevData: String? = null
    private var lastPath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firstRun = savedInstanceState == null
        savedInstanceState?.let {
            prev = it.getString("prev", "")
            prevData = it.getString("prevData")
            lastPath = it.getString("lastPath", "")
        }

        documentTreeOpener = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            it?.let { root ->
                requireContext().contentResolver.takePersistableUriPermission(
                    root, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                saveChoice("doc", root.toString())

                val i = Intent(context, FilePickerActivity::class.java)
                i.putExtra("skip", FilePickerActivity.DOC_PICKER)
                i.putExtra("root", root.toString())
                i.putExtra("allow_dir", true)
                filePickerLauncher.launch(i)
            }
        }
        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            }
            it.data?.getStringExtra("last_path")?.let { path ->
                lastPath = path
            }
            it.data?.getStringExtra("path")?.let { path ->
                playFile(path)
                return@registerForActivityResult
            }
            it.data?.getStringArrayExtra("paths")?.let { paths ->
                // need to convert to playlist at this point
                if (paths.size == 1) {
                    playFile(paths[0])
                } else {
                    val memoryUri = Utils.makeMemoryPlaylist(paths.asIterable())
                    playFile(memoryUri)
                }
            }
        }
        playerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // we don't care about the result but remember that we've been here
            returningFromPlayer = true
            Log.v(TAG, "returned from player ($it)")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val container = view as FrameLayout

        // We draw edge-to-edge, so keep the content clear of the system bars and display cutout
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val i = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(i.left, i.top, i.right, i.bottom)
            insets
        }

        inflateContent(container)
    }

    /**
     * Inflates the home screen into [container]. The portrait and landscape variants are separate
     * layouts, and since MainActivity handles orientation changes itself (no activity restart),
     * this is also called again from [onConfigurationChanged] to switch between them.
     */
    private fun inflateContent(container: FrameLayout) {
        // carry the UI state over when re-inflating
        val old = if (::binding.isInitialized) binding else null
        val wasChecked = old?.switch1?.isChecked ?: false
        val docEnabled = old?.docBtn?.isEnabled ?: true
        val focusedId = container.findFocus()?.id ?: View.NO_ID

        container.removeAllViews()
        binding = MainScreenContentBinding.inflate(LayoutInflater.from(requireContext()), container, true)
        binding.switch1.isChecked = wasChecked
        if (!docEnabled)
            disableDocButton()
        setupViews()

        container.post {
            if (!isAdded || !::binding.isInitialized)
                return@post
            if (focusedId != View.NO_ID) {
                container.findViewById<View>(focusedId)?.requestFocus()
            } else if (isLeanback()) {
                // On TV there is no touch mode: make sure something is focused from the start
                if (!binding.docBtn.requestFocus())
                    binding.urlBtn.requestFocus()
            }
        }
    }

    private fun isLeanback() =
        requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    private fun disableDocButton() {
        binding.docBtn.isEnabled = false
        binding.docBtn.alpha = 0.38f
    }

    private fun setupViews() {
        binding.docBtn.setOnClickListener {
            try {
                documentTreeOpener.launch(null)
            } catch (e: ActivityNotFoundException) {
                // Android TV doesn't come with a document picker and certain versions just throw
                // instead of handling this gracefully
                disableDocButton()
                binding.urlBtn.requestFocus()
            }
        }
        binding.urlBtn.setOnClickListener {
            saveChoice("url")
            val helper = Utils.OpenUrlDialog(requireContext())
            with (helper) {
                builder.setPositiveButton(R.string.dialog_ok) { _, _ ->
                    playFile(helper.text)
                }
                builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
                create().show()
            }
        }
        binding.filepickerBtn.setOnClickListener {
            saveChoice("file")
            val i = Intent(context, FilePickerActivity::class.java)
            i.putExtra("skip", FilePickerActivity.FILE_PICKER)
            i.putExtra("allow_dir", true)
            if (lastPath != "")
                i.putExtra("default_path", lastPath)
            filePickerLauncher.launch(i)
        }
        binding.settingsBtn.setOnClickListener {
            saveChoice("") // will reset
            startActivity(Intent(context, PreferenceActivity::class.java))
        }

        // The whole row is the target (touch, D-pad and TV remote), the switch only shows the state
        binding.rememberRow.setOnClickListener {
            binding.switch1.toggle()
        }

        // Show the labels in Title Case, whatever the (translated) strings look like
        for (label in arrayOf(binding.docLabel, binding.urlLabel, binding.filepickerLabel, binding.settingsLabel))
            label.text = label.text.toString().toTitleCase()

        // These cards are plain layouts, tell accessibility services they behave like buttons
        for (card in arrayOf(binding.docBtn, binding.urlBtn, binding.filepickerBtn, binding.settingsBtn))
            card.exposeAsButton()

        if (BuildConfig.DEBUG) {
            binding.settingsBtn.setOnLongClickListener { showDebugMenu(); true }
        }
    }

    // first letter of every word (letters/digits and apostrophes continue a word)
    private val wordStart = Regex("(?<![\\p{L}\\p{N}'\u2019])\\p{Ll}")

    private fun String.toTitleCase() =
        wordStart.replace(this) { it.value.uppercase(Locale.getDefault()) }

    private fun View.exposeAsButton() {
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        })
    }

    private fun showDebugMenu() {
        assert(BuildConfig.DEBUG)
        val context = requireContext()
        with (AlertDialog.Builder(context)) {
            setItems(DEBUG_ACTIVITIES) { dialog, idx ->
                dialog.dismiss()
                val intent = Intent(Intent.ACTION_MAIN)
                intent.setClassName(context, "${context.packageName}.${DEBUG_ACTIVITIES[idx]}")
                startActivity(intent)
            }
            create().show()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // pick the portrait or landscape layout that fits the new configuration
        (view as? FrameLayout)?.let { inflateContent(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("prev", prev)
        outState.putString("prevData", prevData)
        outState.putString("lastPath", lastPath)
    }

    override fun onResume() {
        super.onResume()
        if (firstRun) {
            restoreChoice()
        } else if (returningFromPlayer) {
            restoreChoice(prev, prevData)
        }
        firstRun = false
        returningFromPlayer = false
    }

    private fun saveChoice(type: String, data: String? = null) {
        if (prev != type)
            lastPath = ""
        prev = type
        prevData = data

        if (!binding.switch1.isChecked)
            return
        binding.switch1.isChecked = false
        with (PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()) {
            putString("MainScreenFragment_remember", type)
            if (data == null)
                remove("MainScreenFragment_remember_data")
            else
                putString("MainScreenFragment_remember_data", data)
            commit()
        }
    }

    private fun restoreChoice() {
        with (PreferenceManager.getDefaultSharedPreferences(requireContext())) {
            restoreChoice(
                getString("MainScreenFragment_remember", "") ?: "",
                getString("MainScreenFragment_remember_data", "")
            )
        }
    }

    private fun restoreChoice(type: String, data: String?) {
        when (type) {
            "doc" -> {
                val uri = Uri.parse(data)
                // check that we can still access the folder
                if (!DocumentPickerFragment.isTreeUsable(requireContext(), uri))
                    return

                val i = Intent(context, FilePickerActivity::class.java)
                i.putExtra("skip", FilePickerActivity.DOC_PICKER)
                i.putExtra("root", uri.toString())
                i.putExtra("allow_dir", true)
                if (lastPath != "")
                    i.putExtra("default_path", lastPath)
                filePickerLauncher.launch(i)
            }
            "url" -> binding.urlBtn.callOnClick()
            "file" -> binding.filepickerBtn.callOnClick()
        }
    }

    private fun playFile(filepath: String) {
        val i: Intent
        if (filepath.startsWith("content://")) {
            i = Intent(Intent.ACTION_VIEW, Uri.parse(filepath))
        } else {
            i = Intent()
            // if we pass 'filepath' it will go directly to mpv without any processing
            i.putExtra("filepath", filepath)
        }
        i.setClass(requireContext(), MPVActivity::class.java)
        playerLauncher.launch(i)
    }

    companion object {
        private const val TAG = "mpv"

        // list of debug or testing activities that can be launched
        private val DEBUG_ACTIVITIES = arrayOf(
            "IntentTestActivity",
            "CodecInfoActivity"
        )
    }
}
