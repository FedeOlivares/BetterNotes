# ML Kit brings in WorkManager, whose Room database is created by reflection under its generated name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# ML Kit finds these by the names in its manifest entries and creates them by reflection.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
