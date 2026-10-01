package app.giveaway

import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint

/** Empty Hilt-enabled activity that UI tests set their content on (debug builds only). A FragmentActivity, like
 * MainActivity, so screens can show BiometricPrompt. */
@AndroidEntryPoint
class HiltTestActivity : FragmentActivity()
