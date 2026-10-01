package app.giveaway

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser
import java.time.LocalDate

/** F-13 acceptance: backup, cleartext and pinning settings from the spec's Security section stay in place. */
@RunWith(RobolectricTestRunner::class)
class SecurityConfigTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val flags = app.packageManager.getApplicationInfo(app.packageName, 0).flags

    @Test
    fun backupIsOff() {
        assertEquals("allowBackup must be false", 0, flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun cleartextTrafficIsOff() {
        assertEquals(0, flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)
        val baseConfig = elements(R.xml.network_security_config).first { it.name == "base-config" }
        assertEquals("false", baseConfig.attributes["cleartextTrafficPermitted"])
    }

    @Test
    fun instagramApiIsPinnedWithBackupsAndAFreshExpiration() {
        val elements = elements(R.xml.network_security_config)
        val domain = elements.first { it.name == "domain" }
        assertEquals("graph.instagram.com", domain.text)
        val pinSet = elements.first { it.name == "pin-set" }
        val pins = elements.filter { it.name == "pin" }
        assertTrue("Need the current pin and at least one backup", pins.size >= 2)
        pins.forEach { assertEquals("SHA-256", it.attributes["digest"]) }
        // Fails a month before the pins lapse, as a reminder to refresh them in the next release (plan R7).
        val expiration = LocalDate.parse(pinSet.attributes["expiration"])
        assertTrue("Pins expire $expiration: refresh them", expiration.isAfter(LocalDate.now().plusDays(30)))
    }

    @Test
    fun crashReportsAreOffInDebugAndAnalyticsNeverRun() {
        val meta = app.packageManager.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA).metaData
        assertEquals("false", meta.get("firebase_crashlytics_collection_enabled").toString())
        assertEquals("true", meta.get("firebase_analytics_collection_deactivated").toString())
        assertEquals("false", meta.get("google_analytics_adid_collection_enabled").toString())
    }

    @Test
    fun cloudBackupAndDeviceTransferExcludeEverything() {
        val elements = elements(R.xml.data_extraction_rules)
        val domains = setOf("root", "file", "database", "sharedpref", "external")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val excluded = elements
                .filter { it.parent == section && it.name == "exclude" }
                .map { it.attributes["domain"] }
            assertEquals(section, domains, excluded.toSet())
            assertFalse(section, elements.any { it.parent == section && it.name == "include" })
        }
    }

    private data class Element(
        val name: String,
        val parent: String?,
        val attributes: Map<String, String>,
        var text: String = "",
    )

    /** Flattens an XML resource into elements with their parent's name and attributes. */
    private fun elements(resId: Int): List<Element> {
        val parser = app.resources.getXml(resId)
        val result = mutableListOf<Element>()
        val stack = ArrayDeque<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val attributes = (0 until parser.attributeCount)
                        .associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
                    result += Element(parser.name, stack.lastOrNull(), attributes)
                    stack.addLast(parser.name)
                }
                XmlPullParser.TEXT -> result.lastOrNull()?.let { it.text += parser.text.trim() }
                XmlPullParser.END_TAG -> stack.removeLast()
            }
        }
        return result
    }
}
