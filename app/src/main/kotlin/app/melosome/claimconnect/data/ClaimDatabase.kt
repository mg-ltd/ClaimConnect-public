package app.melosome.claimconnect.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "incidents")
data class IncidentRow(@PrimaryKey val id: String, val updatedAt: Long, val payload: ByteArray)

@Entity(tableName = "evidence", foreignKeys = [ForeignKey(entity = IncidentRow::class,
    parentColumns = ["id"], childColumns = ["incidentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("incidentId")])
data class EvidenceRow(@PrimaryKey val id: String, val incidentId: String, val addedAt: Long, val payload: ByteArray)

@Entity(tableName = "packs", foreignKeys = [ForeignKey(entity = IncidentRow::class,
    parentColumns = ["id"], childColumns = ["incidentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("incidentId")])
data class PackRow(@PrimaryKey val id: String, val incidentId: String, val generatedAt: Long, val payload: ByteArray)

@Entity(tableName = "settings")
data class SettingsRow(@PrimaryKey val id: String = "driver", val payload: ByteArray)

@Dao
interface ClaimDao {
    @Query("SELECT * FROM incidents ORDER BY updatedAt DESC") fun observeIncidents(): Flow<List<IncidentRow>>
    @Query("SELECT * FROM incidents WHERE id = :id") suspend fun incident(id: String): IncidentRow?
    @Query("SELECT * FROM incidents") suspend fun allIncidents(): List<IncidentRow>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertIncident(row: IncidentRow)
    @Update suspend fun updateIncident(row: IncidentRow)
    @Query("DELETE FROM incidents WHERE id = :id") suspend fun deleteIncident(id: String)
    @Query("SELECT * FROM evidence WHERE incidentId = :id ORDER BY addedAt, id") suspend fun evidence(id: String): List<EvidenceRow>
    @Query("SELECT * FROM evidence WHERE id = :id") suspend fun evidenceRow(id: String): EvidenceRow?
    @Insert suspend fun insertEvidence(row: EvidenceRow)
    @Update suspend fun updateEvidence(row: EvidenceRow)
    @Query("DELETE FROM evidence WHERE id = :id") suspend fun deleteEvidence(id: String)
    @Query("SELECT * FROM packs WHERE incidentId = :id ORDER BY generatedAt DESC LIMIT 1") suspend fun pack(id: String): PackRow?
    @Query("SELECT * FROM packs WHERE id = :id") suspend fun packRow(id: String): PackRow?
    @Insert suspend fun insertPack(row: PackRow)
    @Update suspend fun updatePack(row: PackRow)
    @Query("DELETE FROM packs WHERE incidentId = :id") suspend fun deletePacks(id: String)
    @Query("SELECT * FROM settings WHERE id = 'driver'") suspend fun settings(): SettingsRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSettings(row: SettingsRow)
}

@Database(entities = [IncidentRow::class, EvidenceRow::class, PackRow::class, SettingsRow::class], version = 1, exportSchema = true)
abstract class ClaimDatabase : RoomDatabase() {
    abstract fun dao(): ClaimDao
}
