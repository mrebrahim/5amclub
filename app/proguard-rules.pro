# Room entities and ML Kit ship their own consumer rules; keep receivers/services referenced from the manifest.
-keep class com.fiveam.club.**.*Receiver { *; }
-keep class com.fiveam.club.**.*Service { *; }
