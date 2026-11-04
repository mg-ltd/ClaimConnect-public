package app.melosome.claimconnect.ai

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Thin Play Billing wrapper for the one-time AI unlock. Query and launch only;
 * entitlement is decided server-side by the proxy (never by this client), and
 * purchases are acknowledged only after the proxy confirms them.
 */
class BillingManager(context: Context, private val onResult: (BillingResult, List<Purchase>?) -> Unit) {
    private val client = BillingClient.newBuilder(context)
        .setListener { result, purchases -> onResult(result, purchases) }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    suspend fun connect(): Boolean = suspendCancellableCoroutine { continuation ->
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (continuation.isActive) continuation.resume(
                    result.responseCode == BillingClient.BillingResponseCode.OK)
            }
            override fun onBillingServiceDisconnected() {
                if (continuation.isActive) continuation.resume(false)
            }
        })
    }

    suspend fun product(productId: String): ProductDetails? = suspendCancellableCoroutine { continuation ->
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build())).build()) { _, result ->
            if (continuation.isActive) continuation.resume(result.productDetailsList.firstOrNull())
        }
    }

    fun launch(activity: Activity, product: ProductDetails): Int {
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product).build())).build()
        return client.launchBillingFlow(activity, params).responseCode
    }

    suspend fun acknowledge(token: String): Boolean = suspendCancellableCoroutine { continuation ->
        client.acknowledgePurchase(
            com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(token).build()) { result ->
            if (continuation.isActive) continuation.resume(
                result.responseCode == BillingClient.BillingResponseCode.OK)
        }
    }

    fun endConnection() {
        runCatching { client.endConnection() }
    }
}
