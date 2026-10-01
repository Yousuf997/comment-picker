package app.giveaway.core.security

/**
 * Each kind of secret is wrapped by its own Android Keystore key (spec: Security and privacy), so one key can be
 * deleted or rotated without touching the others. Aliases are stable: changing one orphans existing data.
 */
enum class KeyPurpose(internal val alias: String) {
    /** Wraps the random SQLCipher passphrase. */
    DATABASE("giveaway.kek.database"),

    /** Encrypts the Instagram access token. */
    TOKEN("giveaway.kek.token"),

    /** Encrypts a giveaway's committed draw seed until the real draw. */
    SEED("giveaway.kek.seed"),

    /** Encrypts the app-lock PIN verifier. */
    PIN("giveaway.kek.pin"),
}
