package com.rakuten.tech.mobile.miniapp.js.iap

import com.google.gson.Gson
import com.rakuten.tech.mobile.miniapp.TEST_CALLBACK_ID
import com.rakuten.tech.mobile.miniapp.TestActivity
import com.rakuten.tech.mobile.miniapp.TEST_MA
import com.rakuten.tech.mobile.miniapp.MiniAppSdkException
import com.rakuten.tech.mobile.miniapp.RobolectricBaseSpec
import com.rakuten.tech.mobile.miniapp.TEST_MA_ID
import com.rakuten.tech.mobile.miniapp.TEST_CALLBACK_VALUE
import com.rakuten.tech.mobile.miniapp.api.ApiClient
import com.rakuten.tech.mobile.miniapp.display.WebViewListener
import com.rakuten.tech.mobile.miniapp.errors.MiniAppBridgeErrorModel
import com.rakuten.tech.mobile.miniapp.iap.ProductPrice
import com.rakuten.tech.mobile.miniapp.iap.ProductInfo
import com.rakuten.tech.mobile.miniapp.iap.PurchasedProductInfo
import com.rakuten.tech.mobile.miniapp.iap.PurchaseData
import com.rakuten.tech.mobile.miniapp.iap.InAppPurchaseProvider
import com.rakuten.tech.mobile.miniapp.iap.MiniAppInAppPurchaseErrorType
import com.rakuten.tech.mobile.miniapp.js.CallbackObj
import com.rakuten.tech.mobile.miniapp.js.MiniAppMessageBridge
import com.rakuten.tech.mobile.miniapp.js.ActionType
import com.rakuten.tech.mobile.miniapp.js.MiniAppBridgeExecutor
import com.rakuten.tech.mobile.miniapp.js.PurchasedProductCallbackObj
import com.rakuten.tech.mobile.miniapp.js.ErrorBridgeMessage
import com.rakuten.tech.mobile.miniapp.permission.MiniAppDevicePermissionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import org.amshove.kluent.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.text.SimpleDateFormat
import java.util.*

@Suppress("LargeClass")
class InAppPurchaseBridgeDispatcherSpec : RobolectricBaseSpec() {
    private lateinit var miniAppBridge: MiniAppMessageBridge
    private val callbackObj = CallbackObj(
        action = ActionType.PURCHASE_ITEM.action,
        param = null,
        id = TEST_CALLBACK_ID
    )
    private val webViewListener: WebViewListener = mock()
    private val bridgeExecutor = Mockito.spy(MiniAppBridgeExecutor(webViewListener))
    private val productPrice = ProductPrice(
        "JPY", "1234"
    )
    private val productInfo = ProductInfo(
        "itemId", "title", "description", productPrice
    )
    private val purchasedProductInfo = PurchasedProductInfo(
        productInfo, "dummy_transactionId", 0
    )
    private val samplePurchaseItem = PurchaseItem(
        "123", "1234"
    )
    private val purchaseData =
        PurchaseData(
            1,
            purchasedProductInfo,
            "",
            ""
        )
    private val purchaseJsonStr: String = Gson().toJson(
        CallbackObj(
            action = ActionType.PURCHASE_ITEM.action,
            param = PurchasedProductCallbackObj.ProductItem("itemId"),
            id = TEST_CALLBACK_ID
        )
    )

    private val apiClient: ApiClient = mock()
    private val miniAppIAPVerifier: MiniAppIAPVerifier = mock()
    private val listOfProductFromGooglePlay = listOf(productInfo)

    private fun createPurchaseRequest() = MiniAppPurchaseRecord(
        platform = InAppPurchaseBridgeDispatcher.PLATFORM,
        productId = purchaseData.purchasedProductInfo.productInfo.id,
        transactionState = TransactionState.PURCHASED.state,
        transactionId = purchaseData.purchasedProductInfo.transactionId,
        transactionDate = formatTransactionDate(purchaseData.purchasedProductInfo.transactionDate),
        transactionReceipt = purchaseData.transactionReceipt,
        purchaseToken = purchaseData.purchaseToken
    )

    private fun createMiniAppPurchaseResponse() = MiniAppPurchaseResponse(
        "",
        "",
        ""
    )

    private fun formatTransactionDate(time: Long): String {
        val date = Date(time)
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        return format.format(date)
    }
    @Before
    fun setup() {
        miniAppBridge = Mockito.spy(createMessageBridge())
        miniAppBridge.apiClient = apiClient
        When calling miniAppBridge.createBridgeExecutor(webViewListener) itReturns bridgeExecutor
        miniAppBridge.init(
            activity = TestActivity(),
            webViewListener = webViewListener,
            customPermissionCache = mock(),
            downloadedManifestCache = mock(),
            miniAppId = TEST_MA.id,
            ratDispatcher = mock(),
            secureStorageDispatcher = mock(),
            miniAppIAPVerifier
        )
    }

    @Test
    fun `postError should be called when there is no InAppPurchaseBridgeDispatcher`() {
        val errMsg = ErrorBridgeMessage.NO_IMPL
        miniAppBridge.postMessage(Gson().toJson(callbackObj))
        val miniAppBridge = Mockito.spy(createMessageBridge())
        miniAppBridge.postMessage(Gson().toJson(callbackObj))

        verify(bridgeExecutor).postError(callbackObj.id, errMsg)
    }

    @Test(expected = MiniAppSdkException::class)
    fun `postError should be called when there is no purchaseItem implementation`() {
        val provider = Mockito.spy(
            createPurchaseProvider(canGetItems = true)
        )
        miniAppBridge.setInAppPurchaseProvider(provider)
    }

    @Test
    fun `postError should be called when can't purchase item`() {
        val provider = Mockito.spy(
            createPurchaseProvider(shouldCreate = true, canGetItems = true)
        )
        val wrapper = Mockito.spy(createIAPBridgeWrapper(provider))
        When calling miniAppIAPVerifier.getStoreIdByProductId(TEST_MA_ID, "itemId") itReturns ""
        wrapper.onPurchaseItem(callbackObj.id, purchaseJsonStr)
        val errorMsg = "InApp Purchase Error: Invalid Product Id."
        verify(bridgeExecutor).postError(
            callbackObj.id,
            errorMsg
        )
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `postValue should be called when purchasing item successfully`() {
        val provider = Mockito.spy(
            createPurchaseProvider(shouldCreate = true, canPurchase = true)
        )
        TestCoroutineScope().launch {
            When calling apiClient.recordPurchase(
                TEST_MA_ID,
                createPurchaseRequest()
            ) itReturns createMiniAppPurchaseResponse()
            val wrapper = Mockito.spy(createIAPBridgeWrapper(provider))
            wrapper.onPurchaseItem(callbackObj.id, purchaseJsonStr)

            verify(bridgeExecutor).postValue(
                callbackObj.id,
                Gson().toJson(createMiniAppPurchaseResponse())
            )
        }
    }
    /** region: get all items */
    @ExperimentalCoroutinesApi
    @Test
    fun `postError should be called when fetch empty items`() {
        val provider = Mockito.spy(
            createPurchaseProvider(shouldCreate = true, canGetItems = true)
        )
        TestCoroutineScope().launch {
            When calling apiClient.fetchPurchaseItemList(TEST_MA_ID) itReturns emptyList()
            val wrapper = Mockito.spy(createIAPBridgeWrapper(provider))
            wrapper.onGetPurchaseItems(callbackObj.id)

            val errorMsg = "InApp Purchase Error: Empty product list."
            verify(bridgeExecutor).postError(
                callbackObj.id,
                errorMsg
            )
        }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `postError should be called when throw exception`() {
        val provider = Mockito.spy(
            createPurchaseProvider(shouldCreate = true, canGetItems = true)
        )
        TestCoroutineScope().launch {
            When calling apiClient.fetchPurchaseItemList(TEST_MA_ID) itThrows Exception("message")
            val wrapper = Mockito.spy(createIAPBridgeWrapper(provider))
            wrapper.onGetPurchaseItems(callbackObj.id)

            val errorMsg = "InApp Purchase Error: message"
            verify(bridgeExecutor).postError(
                callbackObj.id,
                errorMsg
            )
        }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `postValue should be called when successfully fetch items from google play console`() {
        val provider = Mockito.spy(
            createPurchaseProvider(shouldCreate = true, canGetItems = true)
        )
        TestCoroutineScope().launch {
            When calling apiClient.fetchPurchaseItemList(TEST_MA_ID) itReturns listOf(
                samplePurchaseItem
            )
            val wrapper = Mockito.spy(createIAPBridgeWrapper(provider))
            wrapper.onGetPurchaseItems(callbackObj.id)

            verify(bridgeExecutor).postValue(
                callbackObj.id,
                Gson().toJson(listOfProductFromGooglePlay)
            )
        }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `postError should be called when failed to fetch items from google play console`() {
        val provider = Mockito.spy(
            createPurchaseProvider(shouldCreate = true)
        )
        TestCoroutineScope().launch {
            When calling apiClient.fetchPurchaseItemList(TEST_MA_ID) itReturns listOf(
                samplePurchaseItem
            )
            val wrapper = Mockito.spy(createIAPBridgeWrapper(provider))
            wrapper.onGetPurchaseItems(callbackObj.id)

            val error =
                MiniAppBridgeErrorModel(MiniAppInAppPurchaseErrorType.productNotFoundError.type)
            verify(bridgeExecutor).postError(
                callbackObj.id,
                Gson().toJson(error)
            )
        }
    }

    /** end region */

    /** region: purchase item */
    @ExperimentalCoroutinesApi
    @Test
    fun `onPurchaseItem postError should be if item is not available in cache`() {
        val provider = Mockito.spy(
            createPurchaseProvider(shouldCreate = true)
        )
        When calling miniAppIAPVerifier.getStoreIdByProductId(TEST_MA_ID, "itemId") itReturns ""
        val wrapper = Mockito.spy(createIAPBridgeWrapper(provider))
        wrapper.onPurchaseItem(callbackObj.id, purchaseJsonStr)

        val error = "InApp Purchase Error: Invalid Product Id."
        verify(bridgeExecutor).postError(
            callbackObj.id,
            error
        )
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `onPurchaseItem postError should be if throw any exception`() {
        val provider = Mockito.spy(
            createPurchaseProvider(shouldCreate = true)
        )
        val wrapper = Mockito.spy(createIAPBridgeWrapper(provider))
        wrapper.onPurchaseItem(callbackObj.id, "")
        verify(bridgeExecutor).postError(any(), any())
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `onPurchaseItem postError should be if failed to purchase using google billing`() {
        val provider = Mockito.spy(
            createPurchaseProvider(shouldCreate = true)
        )
        When calling miniAppIAPVerifier.getStoreIdByProductId(TEST_MA_ID, "itemId") itReturns "123"
        val wrapper = Mockito.spy(createIAPBridgeWrapper(provider))
        wrapper.onPurchaseItem(callbackObj.id, purchaseJsonStr)
        val error = MiniAppBridgeErrorModel(MiniAppInAppPurchaseErrorType.purchaseFailedError.type)

        verify(bridgeExecutor).postError(
            callbackObj.id,
            Gson().toJson(error)

        )
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `onPurchaseItem postValue should be if successfully purchase using google billing`() {
        val provider = Mockito.spy(
            createPurchaseProvider(shouldCreate = true, canPurchase = true)
        )
        When calling miniAppIAPVerifier.getStoreIdByProductId(TEST_MA_ID, "itemId") itReturns "123"
        When calling miniAppIAPVerifier.getProductIdByStoreId(TEST_MA_ID, "itemId") itReturns "itemId"
        TestCoroutineScope().launch {
            val wrapper = Mockito.spy(createIAPBridgeWrapper(provider))
            wrapper.onPurchaseItem(callbackObj.id, purchaseJsonStr)

            verify(bridgeExecutor).postValue(
                callbackObj.id,
                Gson().toJson(purchaseData.purchasedProductInfo)
            )
        }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `recordPurchase should be called any case`() {
        val provider = Mockito.spy(
            createPurchaseProvider(shouldCreate = true, canPurchase = true)
        )
        When calling miniAppIAPVerifier.getStoreIdByProductId(TEST_MA_ID, "itemId") itReturns "123"
        When calling miniAppIAPVerifier.getProductIdByStoreId(TEST_MA_ID, "itemId") itReturns "itemId"
        val wrapper = Mockito.spy(createIAPBridgeWrapper(provider))
        wrapper.onPurchaseItem(callbackObj.id, purchaseJsonStr)
        verify(wrapper).recordPurchase(any(), any(), any())
    }

    /** end region */

    @Suppress("EmptyFunctionBlock")
    private fun createPurchaseProvider(
        shouldCreate: Boolean = false,
        canGetItems: Boolean = false,
        canPurchase: Boolean = false,
        canConsume: Boolean = false
    ): InAppPurchaseProvider {
        return if (shouldCreate) object :
            InAppPurchaseProvider {
            override fun getAllProducts(
                androidStoreIds: List<String>,
                onSuccess: (productInfos: List<ProductInfo>) -> Unit,
                onError: (errorType: MiniAppInAppPurchaseErrorType) -> Unit
            ) {
                if (canGetItems) onSuccess(listOfProductFromGooglePlay)
                else onError(MiniAppInAppPurchaseErrorType.productNotFoundError)
            }

            override fun purchaseProductWith(
                androidStoreId: String,
                onSuccess: (purchaseData: PurchaseData) -> Unit,
                onError: (errorType: MiniAppInAppPurchaseErrorType) -> Unit
            ) {
                if (canPurchase) onSuccess(purchaseData)
                else onError(MiniAppInAppPurchaseErrorType.purchaseFailedError)
            }

            override fun consumePurchaseWIth(
                purhcaseToken: String,
                onSuccess: (title: String, description: String) -> Unit,
                onError: (errorType: MiniAppInAppPurchaseErrorType) -> Unit
            ) {
                if (canConsume) onSuccess("", "")
                else onError(MiniAppInAppPurchaseErrorType.consumeFailedError)
            }

            override fun onEndConnection() {
                // for disconnecting the  billing client.
            }
        } else {
            throw MiniAppSdkException("No method has been implemented.")
        }
    }

    private fun createIAPBridgeWrapper(provider: InAppPurchaseProvider): InAppPurchaseBridgeDispatcher {
        val wrapper = InAppPurchaseBridgeDispatcher()
        wrapper.setMiniAppComponents(
            bridgeExecutor,
            TEST_MA.id,
            apiClient,
            miniAppIAPVerifier
        )
        wrapper.setInAppPurchaseProvider(provider)
        return wrapper
    }

    private fun createMessageBridge(): MiniAppMessageBridge =
        object : MiniAppMessageBridge() {

            override fun getUniqueId(
                onSuccess: (uniqueId: String) -> Unit,
                onError: (message: String) -> Unit
            ) {
                onSuccess(TEST_CALLBACK_VALUE)
            }

            override fun requestDevicePermission(
                miniAppPermissionType: MiniAppDevicePermissionType,
                callback: (isGranted: Boolean) -> Unit
            ) {
                onRequestDevicePermissionsResult(TEST_CALLBACK_ID, false)
            }
        }
}
