package app.giveaway.core.security.lock

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Whether strong biometrics (fingerprint or face) can be used (spec: S3 states). */
fun interface BiometricAvailability {
    fun canUseBiometrics(): Boolean
}

class DefaultBiometricAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
) : BiometricAvailability {
    override fun canUseBiometrics(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
}

enum class BiometricResult { SUCCEEDED, CANCELLED, FAILED }

/**
 * Shows BiometricPrompt with strong biometrics (spec: App access). On Android 11+ the device's own screen lock is
 * accepted as a fallback, so re-enrolling fingers can't lock someone out of their giveaways; below that a cancel
 * button is shown instead.
 */
object BiometricGate {
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        cancelLabel: String,
        onResult: (BiometricResult) -> Unit,
    ) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                } else {
                    setAllowedAuthenticators(BIOMETRIC_STRONG)
                    setNegativeButtonText(cancelLabel)
                }
            }
            .build()
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                onResult(BiometricResult.SUCCEEDED)

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED,
                    -> BiometricResult.CANCELLED
                    else -> BiometricResult.FAILED
                },
            )
        }
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}
