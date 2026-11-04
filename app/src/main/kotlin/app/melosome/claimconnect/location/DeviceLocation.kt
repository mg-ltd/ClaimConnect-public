package app.melosome.claimconnect.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads the device's current location and turns it into a readable address, using only
 * platform APIs (no Google Play Services dependency). Returns null if location cannot be
 * determined so callers can fall back to manual entry; this never throws on missing
 * permission or disabled providers.
 */
object DeviceLocation {
    fun hasFinePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Enabled providers the app may use, best first. GPS needs fine permission, so a coarse-only grant skips it
     * rather than failing on it; passive never produces a fix by itself and is tried last. */
    internal fun usableProviders(all: List<String>, hasFine: Boolean, isEnabled: (String) -> Boolean): List<String> =
        all.filter { (hasFine || it != LocationManager.GPS_PROVIDER) && isEnabled(it) }
            .sortedBy { if (it == LocationManager.PASSIVE_PROVIDER) 1 else 0 }

    fun hasPermission(context: Context): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    suspend fun currentLocationText(context: Context): String? = withContext(Dispatchers.IO) {
        val location = currentFix(context) ?: return@withContext null
        describe(context, location)
    }

    /** Raw coordinates for the map snapshot; null when no fix is available. Never throws. */
    suspend fun currentCoordinates(context: Context): MapPoint? = withContext(Dispatchers.IO) {
        currentFix(context)?.let { MapPoint(it.latitude, it.longitude, it.accuracy.takeIf { accuracy -> accuracy > 0 }) }
    }

    private suspend fun currentFix(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return lastKnown(manager) ?: freshFix(manager, hasFinePermission(context))
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? =
        runCatching { manager.allProviders }.getOrNull()
            ?.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            ?.maxByOrNull { it.time }

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(manager: LocationManager, hasFine: Boolean): Location? {
        val providers = runCatching { manager.allProviders }.getOrNull()
            ?.let { usableProviders(it, hasFine) { name -> runCatching { manager.isProviderEnabled(name) }.getOrDefault(false) } }
            .orEmpty()
        for (provider in providers) fixFrom(manager, provider)?.let { return it }
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun fixFrom(manager: LocationManager, provider: String): Location? {
        return withTimeoutOrNull(8000) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { manager.removeUpdates(this) }
                        if (continuation.isActive) continuation.resume(location)
                    }
                }
                continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                runCatching { manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }
                    .onFailure { if (continuation.isActive) continuation.resume(null) }
            }
        }
    }

    private fun describe(context: Context, location: Location): String {
        val coordinates = "%.5f, %.5f".format(location.latitude, location.longitude)
        return runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()?.getAddressLine(0)
        }.getOrNull() ?: coordinates
    }
}
