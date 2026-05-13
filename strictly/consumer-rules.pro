# Strictly is debug-only. Consumer apps wiring releaseImplementation(strictly-noop)
# get a stub class with all methods no-op'd, so nothing here ships to release.
# These rules keep the public API (and the auto-init provider) intact in case
# someone debugImplementation's it into a custom build type.
-keep class com.vwap.strictly.Strictly { *; }
-keep class com.vwap.strictly.StrictlyConfig { *; }
-keep class com.vwap.strictly.internal.StrictlyAutoInitProvider { *; }
