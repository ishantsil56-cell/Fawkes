# Add project specific ProGuard rules here.
# Phase 0 ships with minifyEnabled=false, so this file is a placeholder.
# Vosk talks to native code through JNA; if minification is ever enabled,
# keep org.vosk.** and com.sun.jna.** here.
-keep class org.vosk.** { *; }
