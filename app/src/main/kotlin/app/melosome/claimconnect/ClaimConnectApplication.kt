package app.melosome.claimconnect

import android.app.Application
import androidx.room.Room
import app.melosome.claimconnect.data.ClaimDatabase
import app.melosome.claimconnect.data.IncidentRepository

class ClaimConnectApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Keyless init for the map snapshotter; tile fetches happen only on explicit user action.
        runCatching { org.maplibre.android.MapLibre.getInstance(this) }
    }
    val database: ClaimDatabase by lazy {
        Room.databaseBuilder(this, ClaimDatabase::class.java, "claimconnect.db")
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).build()
    }
    val repository: IncidentRepository by lazy { IncidentRepository(this, database) }
}
