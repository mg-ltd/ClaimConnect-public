package app.melosome.claimconnect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.rules.Timeout
import java.io.File
import app.melosome.claimconnect.data.*
import app.melosome.claimconnect.model.*
import app.melosome.claimconnect.security.CryptoBox

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    @get:Rule val timeout: Timeout = Timeout.seconds(120)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: ClaimDatabase
    private lateinit var repository: IncidentRepository
    private var incidentId: String? = null
    private val databaseName = "verification.db"
    @Before fun setup() { context.deleteDatabase(databaseName); open() }
    private fun open() {
        db = Room.databaseBuilder(context, ClaimDatabase::class.java, databaseName)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).build()
        repository = IncidentRepository(context, db)
    }
    @After fun cleanup(): Unit = runBlocking {
        incidentId?.let { repository.delete(it) }
        db.close(); context.deleteDatabase(databaseName)
        context.cacheDir.listFiles()?.filter { it.name.startsWith("test-input") }?.forEach { it.delete() }
        File(context.cacheDir, "test-export.pdf").delete()
    }
    @Test fun incidentAndEncryptedDefaultsSurviveDatabaseReopen() = runBlocking {
        repository.saveSettings(DriverSettings(driver = "Private Driver", registration = "PRIVATE123",
            emergencyContacts = listOf(EmergencyContact(label = "AA Recovery", number = "0800123456"))))
        val detail = repository.create(); incidentId = detail.incident.id
        repository.save(detail.incident.copy(location = "Confidential location", notes = "Confidential narrative"))
        db.close(); open()
        assertEquals("Private Driver", repository.settings().driver)
        assertEquals(listOf(EmergencyContact(label = "AA Recovery", number = "0800123456")).map { it.label to it.number },
            repository.settings().emergencyContacts.map { it.label to it.number })
        assertEquals("Confidential narrative", repository.detail(detail.incident.id).incident.notes)
        db.close()
        val raw = context.getDatabasePath(databaseName).readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(raw.contains("Confidential narrative")); assertFalse(raw.contains("PRIVATE123"))
        assertFalse(raw.contains("AA Recovery")); assertFalse(raw.contains("0800123456"))
        open()
    }
    @Test fun settingsWithoutEmergencyContactsFieldDecodeAsEmptyList() = runBlocking {
        // Backward compatibility: rows saved before this field existed must still decode cleanly.
        repository.saveSettings(DriverSettings(driver = "Legacy Driver"))
        assertTrue(repository.settings().emergencyContacts.isEmpty())
    }
    @Test fun archivingAReportKeepsItsPackValidAndRestoringReversesIt() = runBlocking {
        val detail = repository.create(); val id = detail.incident.id; incidentId = id
        repository.save(detail.incident.copy(driver = "Pilot", registration = "AB12 CDE", location = "Test road", notes = "Simulated incident"))
        val generated = repository.generate(id)
        val pack = requireNotNull(generated.currentPack)
        val archived = repository.setArchived(id, true)
        assertTrue(archived.incident.archived)
        assertEquals("Archived", archived.status)
        assertEquals(pack.id, archived.currentPack?.id) // archiving must not invalidate the pack
        val restored = repository.setArchived(id, false)
        assertFalse(restored.incident.archived)
        assertEquals(pack.id, restored.currentPack?.id)
    }
    @Test fun backupMergeRestoresADeletedIncidentWithItsEvidenceAndPack() = runBlocking {
        val detail = repository.create(); val id = detail.incident.id; incidentId = id
        repository.save(detail.incident.copy(driver = "Pilot", registration = "AB12 CDE", location = "Test road", notes = "Simulated incident"))
        val input = photo()
        repository.addPhoto(id, Uri.fromFile(input), "Test import")
        val generated = repository.generate(id)
        val originalPhotoBytes = repository.original(generated.evidence.first())
        val backupFile = File(context.cacheDir, "test-backup.zip")
        repository.backup(Uri.fromFile(backupFile))
        assertTrue(backupFile.length() > 0)
        // Re-running backup/restore while the incident still exists must be a no-op merge (skip, not duplicate).
        val noOp = repository.restoreBackup(Uri.fromFile(backupFile))
        assertEquals(0, noOp.incidentsAdded); assertEquals(1, noOp.incidentsSkipped)
        repository.delete(id) // simulate data loss: the incident is gone from this device entirely
        val summary = repository.restoreBackup(Uri.fromFile(backupFile))
        assertEquals(1, summary.incidentsAdded); assertEquals(1, summary.evidenceAdded); assertEquals(1, summary.packsAdded)
        val restoredDetail = repository.detail(id)
        assertEquals("Pilot", restoredDetail.incident.driver)
        assertEquals("AB12 CDE", restoredDetail.incident.registration)
        assertArrayEquals(originalPhotoBytes, repository.original(restoredDetail.evidence.first()))
        assertEquals(generated.currentPack?.sha256, restoredDetail.currentPack?.sha256)
        backupFile.delete(); Unit
    }
    @Test fun tenPhotosProduceACompleteRenderablePackAndEditsRequireRegeneration() = runBlocking {
        val detail = repository.create(); val id = detail.incident.id; incidentId = id
        repository.save(detail.incident.copy(driver = "Pilot", registration = "AB12 CDE", location = "Test road", notes = "Simulated incident\n".repeat(80)))
        val originals = (0 until 10).associate { index ->
            val input = photo(index)
            val bytes = input.readBytes()
            repository.addPhoto(id, Uri.fromFile(input), "Test import")
            sha256(bytes) to bytes
        }
        val captured = repository.detail(id)
        assertEquals(10, captured.evidence.size)
        assertEquals(10, originals.size)
        assertEquals(originals.keys, captured.evidence.map { it.sha256 }.toSet())
        captured.evidence.forEach { assertArrayEquals(originals.getValue(it.sha256), repository.original(it)) }
        try { repository.addPhoto(id, Uri.fromFile(photo()), "Test import"); fail("11th photo must be rejected") } catch (_: IllegalArgumentException) { }
        val result = repository.generate(id)
        val pack = requireNotNull(result.currentPack)
        val pdf = repository.materialise(pack)
        File(context.cacheDir, "qa").apply { mkdirs() }.let { pdf.copyTo(File(it, "incident-pack.pdf"), overwrite = true) }
        assertEquals(pack.sha256, sha256(pdf.readBytes()))
        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertTrue(renderer.pageCount >= 11)
            // Render every page: the manifest tables, photo pages and caption boxes must all draw cleanly.
            repeat(renderer.pageCount) { index ->
                renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    try { page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                    finally { bitmap.recycle() }
                }
            }
        }
        val destination = File(context.cacheDir, "test-export.pdf")
        val saved = repository.saveCopy(pack, Uri.fromFile(destination))
        assertEquals("Saved copy", saved.status)
        assertEquals(pack.sha256, sha256(destination.readBytes()))
        repository.caption(id, captured.evidence.first().id, "Updated caption")
        assertNull(repository.detail(id).currentPack)
        assertFalse(pdf.exists())
        try { repository.materialise(pack); fail("Old revision must not be shared") } catch (_: IllegalArgumentException) { }
        val regenerated = repository.generate(id)
        assertNotEquals(pack.sha256, regenerated.currentPack?.sha256)
        repository.delete(id)
        assertNull(db.dao().incident(id)); assertTrue(db.dao().evidence(id).isEmpty()); assertNull(db.dao().pack(id))
        assertFalse(File(context.noBackupFilesDir, "records/$id").exists())
        incidentId = null
    }
    @Test fun unauthenticatedAndRelocatedCiphertextIsRejected() {
        val crypto = CryptoBox("claimconnect.test.v1")
        val bytes = crypto.encrypt("private data".toByteArray(), "record-a")
        assertEquals("private data", crypto.decrypt(bytes, "record-a").toString(Charsets.UTF_8))
        try { crypto.decrypt(bytes, "record-b"); fail("Record substitution must fail") } catch (_: java.security.GeneralSecurityException) { }
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        try { crypto.decrypt(bytes, "record-a"); fail("Tampering must fail") } catch (_: java.security.GeneralSecurityException) { }
    }
    @Test fun invalidImageIsRejectedWithoutAddingEvidence() = runBlocking {
        val detail = repository.create(); incidentId = detail.incident.id
        val input = File(context.cacheDir, "test-input.png").apply { writeText("not a photograph") }
        try { repository.addPhoto(detail.incident.id, Uri.fromFile(input), "Test import"); fail("Invalid image must fail") } catch (_: IllegalArgumentException) { }
        assertTrue(repository.detail(detail.incident.id).evidence.isEmpty())
    }
    @Test fun retryingAnInterruptedCameraImportDoesNotDuplicateThePhoto() = runBlocking {
        val detail = repository.create(); incidentId = detail.incident.id
        val id = java.util.UUID.randomUUID().toString()
        val uri = Uri.fromFile(photo())
        repository.addPhoto(detail.incident.id, uri, "Camera", captureId = id)
        val retried = repository.addPhoto(detail.incident.id, uri, "Camera", captureId = id)
        assertEquals(1, retried.evidence.size)
        assertEquals(id, retried.evidence.single().id)
    }
    private fun photo(index: Int = 0): File {
        val bitmap = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(index * 24, 106, 96)) }
        return File(context.cacheDir, "test-input-$index.png").apply {
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
}
