package com.rakuten.tech.mobile.miniapp.js

import android.app.Activity
import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import com.rakuten.tech.mobile.miniapp.errors.MiniAppSecureStorageError
import com.rakuten.tech.mobile.miniapp.storage.MiniAppSecureStorage

internal const val DB_NAME_PREFIX = "rakuten-"

@Suppress("TooManyFunctions", "LargeClass")
internal class MiniAppSecureStorageDispatcher(
    private val storageMaxSizeKB: Int
) {
    private val databaseVersion = 1
    private lateinit var miniAppId: String
    private lateinit var activity: Activity
    private lateinit var bridgeExecutor: MiniAppBridgeExecutor

    @VisibleForTesting
    internal lateinit var onSuccess: () -> Unit

    @VisibleForTesting
    internal lateinit var onFailed: (MiniAppSecureStorageError) -> Unit

    @VisibleForTesting
    internal lateinit var onSuccessGetItem: (String) -> Unit

    @VisibleForTesting
    internal lateinit var onSuccessDBSize: (Long) -> Unit

    private lateinit var miniAppSecureStorage: MiniAppSecureStorage

    fun setBridgeExecutor(activity: Activity, bridgeExecutor: MiniAppBridgeExecutor) {
        this.activity = activity
        this.bridgeExecutor = bridgeExecutor
    }

    fun setMiniAppComponents(miniAppId: String) {
        this.miniAppId = miniAppId
        this.miniAppSecureStorage = MiniAppSecureStorage(
            activity,
            databaseVersion,
            storageMaxSizeKB
        )
    }

    @Suppress("ComplexCondition")
    private fun <T> whenReady(callback: () -> T) {
        if (this::bridgeExecutor.isInitialized &&
            this::activity.isInitialized &&
            this::miniAppId.isInitialized &&
            this::miniAppSecureStorage.isInitialized
        ) {
            callback.invoke()
        }
    }

    fun onLoad() = whenReady {
        onSuccess = {
            bridgeExecutor.dispatchEvent(eventType = NativeEventType.MINIAPP_SECURE_STORAGE_READY.value)
        }
        onFailed = { errorSecure: MiniAppSecureStorageError ->
            bridgeExecutor.dispatchEvent(
                eventType = NativeEventType.MINIAPP_SECURE_STORAGE_LOAD_ERROR.value,
                value = Gson().toJson(errorSecure)
            )
        }
        miniAppSecureStorage.load(miniAppId, onSuccess, onFailed)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException", "ComplexMethod", "LongMethod")
    fun onSetItems(callbackId: String, jsonStr: String) = whenReady {
        try {
            val callbackObj: SecureStorageCallbackObj? =
                Gson().fromJson(jsonStr, SecureStorageCallbackObj::class.java)
            if (callbackObj != null) {
                onSuccess = {
                    bridgeExecutor.postValue(callbackId, SAVE_SUCCESS_SECURE_STORAGE)
                }
                onFailed = { errorSecure: MiniAppSecureStorageError ->
                    bridgeExecutor.postError(callbackId, Gson().toJson(errorSecure))
                }
                miniAppSecureStorage.insertItems(
                    callbackObj.param.secureStorageItems,
                    onSuccess,
                    onFailed
                )
            } else {
                bridgeExecutor.postError(callbackId, ERR_WRONG_JSON_FORMAT)
            }
        } catch (e: Exception) {
            bridgeExecutor.postError(callbackId, ERR_WRONG_JSON_FORMAT)
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun onGetItem(callbackId: String, jsonStr: String) = whenReady {
        try {
            val callbackObj: GetItemCallbackObj? =
                Gson().fromJson(jsonStr, GetItemCallbackObj::class.java)
            if (callbackObj != null) {
                onSuccessGetItem = { itemValue: String ->
                    bridgeExecutor.postValue(callbackId, itemValue)
                }
                onFailed = { errorSecure: MiniAppSecureStorageError ->
                    bridgeExecutor.postError(callbackId, Gson().toJson(errorSecure))
                }
                miniAppSecureStorage.getItem(
                    callbackObj.param.secureStorageKey,
                    onSuccessGetItem,
                    onFailed
                )
            } else {
                bridgeExecutor.postError(callbackId, ERR_WRONG_JSON_FORMAT)
            }
        } catch (e: Exception) {
            bridgeExecutor.postError(callbackId, ERR_WRONG_JSON_FORMAT)
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun onRemoveItems(callbackId: String, jsonStr: String) = whenReady {
        try {
            val callbackObj: DeleteItemsCallbackObj? =
                Gson().fromJson(jsonStr, DeleteItemsCallbackObj::class.java)
            if (callbackObj != null) {
                onSuccess = {
                    bridgeExecutor.postValue(callbackId, REMOVE_ITEMS_SUCCESS_SECURE_STORAGE)
                }
                onFailed = { errorSecure: MiniAppSecureStorageError ->
                    bridgeExecutor.postError(callbackId, Gson().toJson(errorSecure))
                }
                miniAppSecureStorage.deleteItems(
                    callbackObj.param.secureStorageKeyList,
                    onSuccess,
                    onFailed
                )
            } else {
                bridgeExecutor.postError(callbackId, ERR_WRONG_JSON_FORMAT)
            }
        } catch (e: Exception) {
            bridgeExecutor.postError(callbackId, ERR_WRONG_JSON_FORMAT)
        }
    }

    fun onClearAll(callbackId: String) = whenReady {
        onSuccess = {
            bridgeExecutor.postValue(callbackId, REMOVE_SUCCESS_SECURE_STORAGE)
        }
        onFailed = { errorSecure: MiniAppSecureStorageError ->
            bridgeExecutor.postError(callbackId, Gson().toJson(errorSecure))
        }
        miniAppSecureStorage.delete(onSuccess, onFailed)
    }

    @Suppress("MagicNumber")
    @Deprecated("No Longer Needed")
    fun onSize(callbackId: String) = whenReady {
        onSuccessDBSize = { fileSize: Long ->
            val maxSizeInBytes = storageMaxSizeKB * 1024
            val storageSize =
                Gson().toJson(MiniAppSecureStorageSize(fileSize, maxSizeInBytes.toLong()))
            bridgeExecutor.postValue(callbackId, storageSize)
        }
        miniAppSecureStorage.getDatabaseUsedSize(onSuccessDBSize)
    }

    fun cleanupSecureStorage() {}

    /**
     * Will be invoked by MiniApp.clearSecureStorage(miniAppId: String).
     * @param miniAppId will be used to find the storage to be deleted.
     */
    fun clearSecureStorage(miniAppId: String) = whenReady {
        clearSecureDatabase(miniAppId)
    }

    /**
     * Will be invoked by MiniApp.clearSecureStorage.
     */
    fun clearSecureStorage() = whenReady {
        clearAllSecureDatabases()
    }

    /**
     * It will delete all the records as well as the whole DB related to the given mini app id
     * Will be invoked with MiniApp.clearSecureStorage(miniAppId: String).
     * @param miniAppId will be used to find the file to be deleted.
     */
    private fun clearSecureDatabase(miniAppId: String) {
        try {
            val dbName = DB_NAME_PREFIX + miniAppId
            activity.deleteDatabase(dbName)
        } catch (e: Exception) {
            // No callback needed. So Ignoring.
        }
    }

    /**
     * Will be invoked by MiniApp.clearSecureStorage.
     */
    private fun clearAllSecureDatabases() {
        try {
            activity.databaseList().forEach {
                if (it.startsWith(DB_NAME_PREFIX)) {
                    activity.deleteDatabase(it)
                }
            }
        } catch (e: Exception) {
            // No callback needed. So Ignoring.
        }
    }

    internal companion object {
        const val SAVE_SUCCESS_SECURE_STORAGE = "Items saved successfully."
        const val REMOVE_SUCCESS_SECURE_STORAGE = "Storage removed successfully."
        const val REMOVE_ITEMS_SUCCESS_SECURE_STORAGE = "Items removed successfully."
        const val ERR_WRONG_JSON_FORMAT = "Can not parse secure storage json object."
    }
}
