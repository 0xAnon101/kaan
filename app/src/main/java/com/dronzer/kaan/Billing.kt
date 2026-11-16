package com.dronzer.kaan

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play Billing with no backend. Play is the source of truth for the subscription.
 * The app caches the last answer in Store.proCached so Pro keeps working offline.
 *
 * Create these two subscription product IDs in Play Console before you publish.
 */
class Billing(ctx: Context, private val onChange: () -> Unit) : PurchasesUpdatedListener {

    private val store = Store(ctx)
    private val main = Handler(Looper.getMainLooper())

    var products: List<ProductDetails> = emptyList()
        private set

    private val client: BillingClient = BillingClient.newBuilder(ctx.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadProducts()
                    refresh()
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun end() {
        client.endConnection()
    }

    private fun loadProducts() {
        val wanted = listOf(MONTHLY, YEARLY).map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(wanted).build()
        // Billing Library 8 returns a QueryProductDetailsResult instead of a plain list.
        client.queryProductDetailsAsync(params) { result, response ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                products = response.productDetailsList.sortedBy { if (it.productId == MONTHLY) 0 else 1 }
                notifyChange()
            }
        }
    }

    /** Asks Play what the user owns. Also serves as "Restore purchase". */
    fun refresh() {
        if (!client.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handle(purchases, authoritative = true)
            }
        }
    }

    fun buy(activity: Activity, product: ProductDetails) {
        val offer = product.subscriptionOfferDetails?.firstOrNull() ?: return
        val line = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(offer.offerToken)
            .build()
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(line))
            .build()
        client.launchBillingFlow(activity, flow)
    }

    /** The recurring price of the first offer, already formatted by Play for the user's country. */
    fun price(product: ProductDetails): String =
        product.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.lastOrNull()
            ?.formattedPrice ?: ""

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handle(purchases, authoritative = false)
        }
    }

    private fun handle(purchases: List<Purchase>, authoritative: Boolean) {
        val active = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        // Play refunds a subscription that stays unacknowledged for 3 days.
        active.filter { !it.isAcknowledged }.forEach { purchase ->
            val ack = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(ack) { }
        }
        if (authoritative || active.isNotEmpty()) {
            store.proCached = active.isNotEmpty()
        }
        notifyChange()
    }

    private fun notifyChange() {
        main.post(onChange)
    }

    companion object {
        const val MONTHLY = "kaan_pro_monthly"
        const val YEARLY = "kaan_pro_yearly"
    }
}
