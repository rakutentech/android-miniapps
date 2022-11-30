package com.rakuten.tech.mobile.miniapp.js

import com.google.gson.Gson
import com.rakuten.tech.mobile.miniapp.api.ApiClient
import com.rakuten.tech.mobile.miniapp.iap.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/** Check whether hostapp provides InAppPurchase dependency. */
@Suppress("EmptyCatchBlock", "SwallowedException")
private inline fun <T> whenHasInAppPurchase(callback: () -> T) {
    try {
        Class.forName("com.rakuten.tech.mobile.miniapp.iap.InAppPurchaseProvider")
        callback.invoke()
    } catch (e: ClassNotFoundException) {}
}

@Suppress("TooGenericExceptionCaught")
internal class InAppPurchaseBridgeDispatcher {
    private lateinit var bridgeExecutor: MiniAppBridgeExecutor
    private lateinit var miniAppId: String
    private var isMiniAppComponentReady = false
    private lateinit var inAppPurchaseProvider: InAppPurchaseProvider
    private lateinit var apiClient: ApiClient
    internal var scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    fun setMiniAppComponents(
        bridgeExecutor: MiniAppBridgeExecutor,
        miniAppId: String,
        apiClient: ApiClient
    ) {
        this.bridgeExecutor = bridgeExecutor
        this.miniAppId = miniAppId
        this.apiClient = apiClient
        isMiniAppComponentReady = true
    }

    fun setInAppPurchaseProvider(iapProvider: InAppPurchaseProvider) {
        this.inAppPurchaseProvider = iapProvider
    }

    private fun <T> whenReady(callbackId: String, callback: () -> T) = whenHasInAppPurchase {
        if (isMiniAppComponentReady) {
            if (this::inAppPurchaseProvider.isInitialized)
                callback.invoke()
            else
                bridgeExecutor.postError(callbackId, ErrorBridgeMessage.NO_IMPL)
        }
    }

    fun onPurchaseItem(callbackId: String, jsonStr: String) =
        whenReady(callbackId) {
            try {
                val callbackObj: PurchasedProductCallbackObj =
                    Gson().fromJson(jsonStr, PurchasedProductCallbackObj::class.java)
                val successCallback = { response: PurchasedProductResponse ->
                    if (response.status == PurchasedProductResponseStatus.PURCHASED) {
                        val purchaseRequest = MiniAppPurchaseRequest(
                            platform = PLATFORM,
                            productId = response.purchasedProduct.product.id,
                            transactionState = TransactionState.PURCHASED.state,
                            transactionId = response.purchasedProduct.transactionId,
                            transactionDate = formatTransactionDate(response.purchasedProduct.transactionDate),
                            transactionReceipt = response.purchasedProduct.transactionReceipt,
                            purchaseToken = response.purchasedProduct.purchaseToken
                        )
                        notifyMiniApp(callbackId, purchaseRequest)
                    } else {
                        bridgeExecutor.postError(callbackId, "$ERR_IN_APP_PURCHASE ${response.status}")
                    }
                }
                inAppPurchaseProvider.purchaseItem(
                    callbackObj.param.product_id,
                    successCallback,
                    createErrorCallback(callbackId)
                )
            } catch (e: Exception) {
                bridgeExecutor.postError(callbackId, "$ERR_IN_APP_PURCHASE ${e.message}")
            }
        }

    private fun createErrorCallback(callbackId: String) = { errMessage: String ->
        bridgeExecutor.postError(callbackId, "$ERR_IN_APP_PURCHASE $errMessage")
    }

    fun onGetPurchaseItems(callbackId: String) = whenReady(callbackId) {
        scope.launch {
            try {
                val purchaseItemList = apiClient.fetchPurchaseItemList(miniAppId)
                bridgeExecutor.postValue(callbackId, Gson().toJson(purchaseItemList))
            } catch (e: Exception) {
                bridgeExecutor.postError(callbackId, "$ERR_IN_APP_PURCHASE ${e.message}")
            }
        }
    }

    private fun notifyMiniApp(callbackId: String, purchaseRequest: MiniAppPurchaseRequest) {
        scope.launch {
            try {
                val miniAppPurchaseResponse = apiClient.purchaseItem(miniAppId, purchaseRequest)
                bridgeExecutor.postValue(callbackId, Gson().toJson(miniAppPurchaseResponse))
            } catch (e: Exception) {
                bridgeExecutor.postError(callbackId, "$ERR_IN_APP_PURCHASE ${e.message}")
            }
        }
    }

    private fun formatTransactionDate(time: Long): String{
        val date = Date(time)
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        return format.format(date)
    }

    companion object {
        const val ERR_IN_APP_PURCHASE = "Cannot purchase item:"
        const val PLATFORM = "ANDROID"
    }
}
