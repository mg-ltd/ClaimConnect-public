package app.melosome.claimconnect

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.melosome.claimconnect.data.ClaimDatabase
import app.melosome.claimconnect.data.IncidentRepository
import app.melosome.claimconnect.location.DeviceLocation
import app.melosome.claimconnect.ui.openDialler
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiShareAndPermissionsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "ai-share.db"
    private lateinit var db: ClaimDatabase
    private lateinit var repository: IncidentRepository
    private val incidents = mutableListOf<String>()
    private val authority get() = "${context.packageName}.files"
    private val aiRoot get() = File(context.cacheDir, "ai_share")

    @Before fun setup() {
        context.deleteDatabase(databaseName)
        db = Room.databaseBuilder(context, ClaimDatabase::class.java, databaseName)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).build()
        repository = IncidentRepository(context, db)
    }
    @After fun cleanup(): Unit = runBlocking {
        incidents.forEach { repository.delete(it) }
        db.close(); context.deleteDatabase(databaseName)
        aiRoot.deleteRecursively()
        context.cacheDir.listFiles()?.filter { it.name.startsWith("ai-input") }?.forEach { it.delete() }
    }

    private suspend fun incidentWithPhotos(count: Int): String {
        val id = repository.create().incident.id.also { incidents += it }
        repeat(count) { index ->
            val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(index * 20, 90, 120)) }
            val file = File(context.cacheDir, "ai-input-$index.png").apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            repository.addPhoto(id, Uri.fromFile(file), "Test import")
        }
        return id
    }
    private fun read(file: File): ByteArray =
        context.contentResolver.openInputStream(FileProvider.getUriForFile(context, authority, file))!!.use { it.readBytes() }

    @Test fun sharedUrisResolveAndAreReadableForOneAndTenPhotos() = runBlocking {
        for (count in listOf(1, 10)) {
            val id = incidentWithPhotos(count)
            val session = repository.materialiseForAi(id)
            val originals = repository.detail(id).evidence.map { repository.original(it) }
            assertEquals(count, session.files.size)
            session.files.forEachIndexed { index, file -> assertArrayEquals(originals[index], read(file)) }
        }
    }

    @Test fun fileProviderDoesNotExposeFilesOutsideSharePaths() {
        val outside = File(context.filesDir, "secret.txt").apply { writeText("secret") }
        try {
            assertThrows(IllegalArgumentException::class.java) { FileProvider.getUriForFile(context, authority, outside) }
        } finally { outside.delete() }
    }

    @Test fun sessionsAreIndependentAndExpireOnTheirOwn() = runBlocking {
        val id = incidentWithPhotos(1)
        val first = repository.materialiseForAi(id)
        val second = repository.materialiseForAi(id)
        assertTrue(first.files.single().exists() && second.files.single().exists())
        assertNotEquals(first.id, second.id)
        repository.discardAiShare(first)
        assertFalse(first.files.single().exists()); assertTrue(second.files.single().exists())
        // Within the grace period nothing is removed; afterwards the sweep removes the session.
        repository.sweepAiShares(now = second.expiresAt - 1)
        assertTrue(second.files.single().exists())
        repository.sweepAiShares(now = second.expiresAt + 1)
        assertFalse(second.files.single().exists())
    }

    @Test fun sessionsExpiredBeforeProcessRecreationAreSweptAndLiveOnesKept() = runBlocking {
        val id = incidentWithPhotos(1)
        val stale = File(aiRoot, "$id/${System.currentTimeMillis() - IncidentRepository.AI_SHARE_GRACE_MS - 1}-old")
            .apply { mkdirs(); File(this, "photo.jpg").writeText("plaintext") }
        val unreadable = File(aiRoot, "$id/not-a-session").apply { mkdirs() }
        val live = repository.materialiseForAi(id)
        // A brand-new repository instance stands in for the process being recreated after a kill.
        IncidentRepository(context, db).sweepAiShares()
        assertFalse(stale.exists()); assertFalse(unreadable.exists())
        assertTrue(live.files.single().exists())
    }

    @Test fun editingOrDeletingTheIncidentRemovesItsPlaintextOnly() = runBlocking {
        val edited = incidentWithPhotos(1); val deleted = incidentWithPhotos(1); val untouched = incidentWithPhotos(1)
        val editedSession = repository.materialiseForAi(edited)
        val deletedSession = repository.materialiseForAi(deleted)
        val untouchedSession = repository.materialiseForAi(untouched)
        repository.save(repository.detail(edited).incident.copy(notes = "changed"))
        repository.delete(deleted)
        assertFalse(editedSession.files.single().exists())
        assertFalse(deletedSession.files.single().exists())
        assertTrue(untouchedSession.files.single().exists())
        incidents -= deleted
    }

    @Test fun failedMaterialisationLeavesNoPlaintext() = runBlocking {
        try { repository.materialiseForAi("missing-incident"); fail("expected failure") } catch (_: Exception) { }
        assertTrue(aiRoot.walkTopDown().none { it.isFile })
    }

    // Revoking a granted permission kills the instrumented process, so revocation is covered by
    // hasPermission() re-checking on every call; here we cover denied and then coarse-only.
    @Test fun locationIsUnavailableWithoutPermissionAndAvailableWithCoarseOnly() {
        assumeFalse("Location already granted on this device", DeviceLocation.hasPermission(context))
        assertNull(runBlocking { DeviceLocation.currentLocationText(context) })
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        assertTrue(DeviceLocation.hasPermission(context))
    }

    @Test fun emergencyNumberUsesDialAndHandlesNoDialler() {
        var launched: Intent? = null
        assertTrue(openDialler(context, "999 (UK)") { launched = it })
        assertEquals(Intent.ACTION_DIAL, launched?.action)
        assertEquals("tel:999", launched?.data?.toString())
        assertFalse(openDialler(context, "999") { throw ActivityNotFoundException() })
    }
}
