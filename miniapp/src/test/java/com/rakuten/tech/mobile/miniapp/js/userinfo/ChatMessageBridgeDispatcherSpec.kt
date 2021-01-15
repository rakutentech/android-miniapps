package com.rakuten.tech.mobile.miniapp.js.userinfo

import com.google.gson.Gson
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.rakuten.tech.mobile.miniapp.MiniAppInfo
import com.rakuten.tech.mobile.miniapp.Version
import com.rakuten.tech.mobile.miniapp.TestActivity
import com.rakuten.tech.mobile.miniapp.TEST_CALLBACK_ID
import com.rakuten.tech.mobile.miniapp.TEST_CALLBACK_VALUE
import com.rakuten.tech.mobile.miniapp.TEST_CONTACT_ID
import com.rakuten.tech.mobile.miniapp.TEST_ERROR_MSG
import com.rakuten.tech.mobile.miniapp.TEST_MA
import com.rakuten.tech.mobile.miniapp.TEST_MA_DISPLAY_NAME
import com.rakuten.tech.mobile.miniapp.TEST_MA_ICON
import com.rakuten.tech.mobile.miniapp.TEST_MA_ID
import com.rakuten.tech.mobile.miniapp.TEST_MA_VERSION_ID
import com.rakuten.tech.mobile.miniapp.TEST_MA_VERSION_TAG
import com.rakuten.tech.mobile.miniapp.display.WebViewListener
import com.rakuten.tech.mobile.miniapp.js.ActionType
import com.rakuten.tech.mobile.miniapp.js.CallbackObj
import com.rakuten.tech.mobile.miniapp.js.ChatMessageBridgeDispatcher
import com.rakuten.tech.mobile.miniapp.js.ChatMessageBridgeDispatcher.Companion.ERR_CONTACT_NO_PERMISSION
import com.rakuten.tech.mobile.miniapp.js.ChatMessageBridgeDispatcher.Companion.ERR_SEND_MESSAGE
import com.rakuten.tech.mobile.miniapp.js.MessageToContact
import com.rakuten.tech.mobile.miniapp.js.MiniAppMessageBridge
import com.rakuten.tech.mobile.miniapp.js.MiniAppBridgeExecutor
import com.rakuten.tech.mobile.miniapp.js.SendContactCallbackObj
import com.rakuten.tech.mobile.miniapp.permission.MiniAppCustomPermissionCache
import com.rakuten.tech.mobile.miniapp.permission.MiniAppCustomPermissionType
import org.amshove.kluent.When
import org.amshove.kluent.calling
import org.amshove.kluent.itReturns
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class ChatMessageBridgeDispatcherSpec {

    private lateinit var miniAppBridge: MiniAppMessageBridge
    private val sendMessageCallbackObj = CallbackObj(
        action = ActionType.SEND_MESSAGE_TO_CONTACT.action,
        param = null,
        id = TEST_CALLBACK_ID
    )
    private val customPermissionCache: MiniAppCustomPermissionCache = mock()
    private val miniAppInfo = MiniAppInfo(
        id = TEST_MA_ID,
        displayName = TEST_MA_DISPLAY_NAME,
        icon = TEST_MA_ICON,
        version = Version(TEST_MA_VERSION_TAG, TEST_MA_VERSION_ID)
    )
    private val webViewListener: WebViewListener = mock()
    private val bridgeExecutor = Mockito.spy(MiniAppBridgeExecutor(webViewListener))

    @Before
    fun setup() {
        miniAppBridge = Mockito.spy(createMessageBridge())
        When calling miniAppBridge.createBridgeExecutor(webViewListener) itReturns bridgeExecutor
        miniAppBridge.init(
            activity = TestActivity(),
            webViewListener = webViewListener,
            customPermissionCache = customPermissionCache,
            miniAppId = TEST_MA.id
        )

        whenever(
            customPermissionCache.hasPermission(
                miniAppInfo.id, MiniAppCustomPermissionType.CONTACT_LIST
            )
        ).thenReturn(true)
    }

    private fun createChatMessageBridgeDispatcher(
        hasContactListPermission: Boolean,
        canSendMessage: Boolean
    ): ChatMessageBridgeDispatcher {
        if (hasContactListPermission)
            return object : ChatMessageBridgeDispatcher() {

                override fun sendMessageToContact(
                    message: MessageToContact,
                    onSuccess: (contactId: String?) -> Unit,
                    onError: (message: String) -> Unit
                ) {
                    if (canSendMessage)
                        onSuccess.invoke(TEST_CONTACT_ID)
                    else
                        onError.invoke(TEST_ERROR_MSG)
                }
            }
        else return object : ChatMessageBridgeDispatcher() {

            override fun sendMessageToContact(
                message: MessageToContact,
                onSuccess: (contactId: String?) -> Unit,
                onError: (message: String) -> Unit
            ) {
                onError.invoke(TEST_ERROR_MSG)
            }
        }
    }

    private fun createMessageBridge(): MiniAppMessageBridge =
        object : MiniAppMessageBridge() {
            override fun getUniqueId() = TEST_CALLBACK_VALUE
        }

    private val messageToContact = MessageToContact("", "", "", "", "")
    private val sendingMessageJsonStr = Gson().toJson(
        CallbackObj(
            action = ActionType.SEND_MESSAGE_TO_CONTACT.action,
            param = SendContactCallbackObj.MessageParam(messageToContact),
            id = TEST_CALLBACK_ID
        )
    )

    @Test
    fun `postError should be called when contact list permission hasn't been allowed`() {
        val chatMessageBridgeDispatcher =
            Mockito.spy(createChatMessageBridgeDispatcher(false, false))
        chatMessageBridgeDispatcher.init(bridgeExecutor, customPermissionCache, TEST_MA_ID)
        val errMsg = "$ERR_SEND_MESSAGE $ERR_CONTACT_NO_PERMISSION"
        whenever(
            customPermissionCache.hasPermission(
                miniAppInfo.id, MiniAppCustomPermissionType.CONTACT_LIST
            )
        ).thenReturn(false)

        chatMessageBridgeDispatcher.onSendMessageToContact(
            sendMessageCallbackObj.id,
            sendingMessageJsonStr
        )

        verify(bridgeExecutor).postError(sendMessageCallbackObj.id, errMsg)
    }

    @Test
    fun `postError should be called when contact list permission has been allowed but can't send message`() {
        val chatMessageBridgeDispatcher =
            Mockito.spy(createChatMessageBridgeDispatcher(true, false))
        chatMessageBridgeDispatcher.init(bridgeExecutor, customPermissionCache, TEST_MA_ID)
        val errMsg = "$ERR_SEND_MESSAGE $TEST_ERROR_MSG"

        chatMessageBridgeDispatcher.onSendMessageToContact(
            sendMessageCallbackObj.id,
            sendingMessageJsonStr
        )

        verify(bridgeExecutor).postError(sendMessageCallbackObj.id, errMsg)
    }

    @Test
    fun `postValue should be called when contact list permission has been allowed and can send message`() {
        val chatMessageBridgeDispatcher =
            Mockito.spy(createChatMessageBridgeDispatcher(true, true))
        chatMessageBridgeDispatcher.init(bridgeExecutor, customPermissionCache, TEST_MA_ID)

        chatMessageBridgeDispatcher.onSendMessageToContact(
            sendMessageCallbackObj.id,
            sendingMessageJsonStr
        )

        verify(bridgeExecutor).postValue(sendMessageCallbackObj.id, TEST_CONTACT_ID)
    }
}
