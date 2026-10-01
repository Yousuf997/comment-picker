package app.giveaway.core.instagram.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands the App Link that Instagram redirects to from the callback activity to the screen waiting for it.
 * It holds the latest callback until consumed, so it survives the screen being recreated in between.
 */
@Singleton
class AuthCallbacks @Inject constructor() {
    private val pending = MutableStateFlow<String?>(null)

    val latest: StateFlow<String?> = pending.asStateFlow()

    fun deliver(callbackUri: String) {
        pending.value = callbackUri
    }

    /** Takes the pending callback, if any, so it is handled once. */
    fun consume(): String? = pending.getAndUpdate { null }
}
