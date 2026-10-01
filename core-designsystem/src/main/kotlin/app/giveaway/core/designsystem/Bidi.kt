package app.giveaway.core.designsystem

/** LEFT-TO-RIGHT ISOLATE and POP DIRECTIONAL ISOLATE, built from code points so no bidi control sits in the source. */
private val LRI = Char(0x2066)
private val PDI = Char(0x2069)

/**
 * Wraps text in a Unicode left-to-right isolate. Usernames, hashes and codes stay left to right inside Arabic text
 * (spec: Localization) without changing the direction or alignment of the surrounding paragraph; without it, "@shop"
 * shows as "shop@" in a right-to-left layout.
 */
fun String.ltrIsolated(): String = "$LRI$this$PDI"

/** An Instagram handle with its "@", kept left to right in any language. */
fun handle(username: String): String = "@$username".ltrIsolated()
