package app.melosome.claimconnect.location

import android.location.LocationManager
import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM tests for provider eligibility; Android permission/revocation remains instrumented QA. */
class DeviceLocationTest {
    @Test fun coarseOnlySkipsGpsAndTriesActiveNetworkBeforePassive() {
        val available = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
        assertEquals(
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER),
            DeviceLocation.usableProviders(available, hasFine = false) { true },
        )
    }

    @Test fun finePermissionAllowsGps() {
        assertEquals(
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER),
            DeviceLocation.usableProviders(
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER),
                hasFine = true,
            ) { true },
        )
    }

    @Test fun disabledProvidersAreNotRequested() {
        assertEquals(
            listOf(LocationManager.NETWORK_PROVIDER),
            DeviceLocation.usableProviders(
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER),
                hasFine = false,
            ) { it == LocationManager.NETWORK_PROVIDER },
        )
    }
}
