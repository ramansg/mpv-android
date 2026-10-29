package `is`.xyz.mpv

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    private var dynamicColors = false

    private fun wantsDynamicColors() =
        PreferenceManager.getDefaultSharedPreferences(this).getBoolean("material_you_theming", false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Use the same look as the settings screens: Material 3 (follows light/dark mode),
        // edge-to-edge and optionally Material You colors. This must happen before the
        // layout is inflated in super.onCreate().
        enableEdgeToEdge()
        dynamicColors = wantsDynamicColors()
        if (dynamicColors)
            DynamicColors.applyToActivityIfAvailable(this)

        super.onCreate(savedInstanceState)

        // The original plan was to have the file/doc picker live as fragments
        // under here but that requires refactoring I'm really not willing to figure out now.
        // ~sfan5, 2022-06-30

        if (savedInstanceState == null) {
            with (supportFragmentManager.beginTransaction()) {
                setReorderingAllowed(true)
                add(R.id.fragment_container_view, MainScreenFragment())
                commit()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // "Material You" may have been toggled in the settings while we were in the background
        if (wantsDynamicColors() != dynamicColors)
            recreate()
    }
}
