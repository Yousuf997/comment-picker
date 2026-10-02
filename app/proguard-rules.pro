# Project-specific R8 rules. Library rules for Hilt, Compose and AndroidX ship with the libraries.

# No logging in release (spec: no sensitive data in logs): calls to android.util.Log, ours or a library's, are removed.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}
