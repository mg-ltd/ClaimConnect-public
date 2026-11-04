package app.melosome.claimconnect

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import java.io.File
import org.junit.Rule
import org.junit.Test
import app.melosome.claimconnect.ui.AppViewModel

class CaptureFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun guidedCaptureAutosavesAndGeneratesPack() {
        val registration = "QA${System.currentTimeMillis().toString().takeLast(6)}"
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("New incident").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Skip").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("New incident").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { compose.onAllNodesWithText("New incident").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Skip").assertDoesNotExist()
        screenshot("home.png")
        compose.onNodeWithText("New incident").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Safety comes first").fetchSemanticsNodes().isNotEmpty() }
        screenshot("safety.png")
        compose.onNodeWithText("I'm safe — continue").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("registration").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("registration").performTextInput(registration)
        compose.onNodeWithTag("driver").performScrollTo().performTextInput("Pilot Driver")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithTag("location").performTextInput("Simulated test road")
        compose.onNodeWithTag("notes").performScrollTo().performTextInput("A simulated incident for verification.")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        val photo = File(compose.activity.cacheDir, "flow-test-photo.png")
        val bitmap = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(0, 106, 96)) }
        try { photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
        compose.runOnUiThread {
            ViewModelProvider(compose.activity)[AppViewModel::class.java].importPhotos(listOf(Uri.fromFile(photo)), "Simulated test import")
        }
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Photo 1: Incident evidence").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Photo 1: Incident evidence").performScrollTo().assertIsDisplayed()
        screenshot("photo.png")
        photo.delete()
        compose.onNodeWithText("Review incident").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Generate evidence pack").fetchSemanticsNodes().any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() } }
        screenshot("review.png")
        compose.onNodeWithText("Generate evidence pack").performScrollTo().performClick()
        compose.waitUntil(20000) { compose.onAllNodesWithText("Evidence pack ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("completionPreview").assertIsDisplayed()
        compose.onNodeWithTag("completionSave").assertIsDisplayed()
        compose.onNodeWithTag("completionShare").assertIsDisplayed()
        screenshot("pack.png")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Evidence pack ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("completionShare").performClick()
        compose.onNodeWithText("Share this evidence pack?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Evidence pack ready").assertIsDisplayed()
        compose.onNodeWithTag("completionHome").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("New incident").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Reports").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("archiveReport:$registration").fetchSemanticsNodes().any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() } }
        compose.onNodeWithTag("archiveReport:$registration").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("archivedReportsToggle").fetchSemanticsNodes().any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() } }
        compose.onNodeWithText(registration).assertDoesNotExist()
        compose.onNodeWithTag("archivedReportsToggle").performClick()
        compose.onNodeWithText(registration).assertIsDisplayed()
        compose.onNodeWithTag("restoreReport:$registration").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("archiveReport:$registration").fetchSemanticsNodes().any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() } }
        compose.onNodeWithText("Incidents").performClick()
        compose.onNodeWithText(registration).performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Delete incident").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Delete incident").performScrollTo().performClick()
        compose.onNodeWithText("Delete this incident?").assertIsDisplayed()
        compose.onNodeWithTag("confirmDelete").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("New incident").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Replay tour").performScrollTo().performClick()
        compose.onNodeWithText("Safety first").assertIsDisplayed()
        compose.onNodeWithText("Skip").performClick()
        compose.onNodeWithText("Replay tour").assertExists()
    }
    /**
     * Real Play-store screenshots of the running app (synthetic data only).
     * App-scoped external storage needs no permission and is pullable with
     * `adb pull` without root, unlike cacheDir. CI pulls this directory after
     * the suite and uploads it as the play-screenshots artifact.
     */
    private fun screenshot(name: String) {
        val directory = File(compose.activity.getExternalFilesDir(null), "play-shots").apply { mkdirs() }
        File(directory, name).outputStream().use {
            compose.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
