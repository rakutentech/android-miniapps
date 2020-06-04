package com.rakuten.tech.mobile.miniapp

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.rakuten.tech.mobile.miniapp.api.ApiClient
import com.rakuten.tech.mobile.miniapp.api.ManifestEntity
import com.rakuten.tech.mobile.miniapp.api.UpdatableApiClient
import com.rakuten.tech.mobile.miniapp.storage.MiniAppStatus
import com.rakuten.tech.mobile.miniapp.storage.MiniAppStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlin.system.measureTimeMillis

internal class MiniAppDownloader(
    private val storage: MiniAppStorage,
    private var apiClient: ApiClient,
    private val miniAppStatus: MiniAppStatus,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO
) : UpdatableApiClient {

    @Suppress("SwallowedException", "LongMethod")
    suspend fun getMiniApp(appId: String, versionId: String): String {
        val versionPath = storage.getMiniAppVersionPath(appId, versionId)
        try {
            return when {
                !isLatestVersion(appId, versionId) -> throw sdkExceptionForInvalidVersion()
                miniAppStatus.isVersionDownloaded(appId, versionId, versionPath) -> versionPath
                else -> startDownload(appId, versionId)
            }
        } catch (netError: MiniAppNetException) {
            // load local if possible when offline
            if (miniAppStatus.isVersionDownloaded(appId, versionId, versionPath))
                return versionPath
        }
        // cannot load miniapp from server
        throw sdkExceptionForInternalServerError()
    }

    private suspend fun isLatestVersion(appId: String, versionId: String): Boolean =
        apiClient.fetchInfo(appId).version.versionId == versionId

    @VisibleForTesting
    suspend fun startDownload(appId: String, versionId: String): String {
        val manifest = fetchManifest(appId, versionId)
        var path = ""
        Log.d("MiniApp", "time execution:${measureTimeMillis { path = downloadMiniApp(appId, versionId, manifest) }}")
        return path
    }

    @VisibleForTesting
    suspend fun fetchManifest(
        appId: String,
        versionId: String
    ) = apiClient.fetchFileList(appId, versionId)

    @SuppressWarnings("LongMethod")
    private suspend fun downloadMiniApp(
        appId: String,
        versionId: String,
        manifest: ManifestEntity
    ): String {
        val baseSavePath = storage.getMiniAppVersionPath(appId, versionId)
        when {
            isManifestValid(manifest) -> {
                for (file in manifest.files) {
                    val response = apiClient.downloadFile(file)
                    storage.saveFile(file, baseSavePath, response.byteStream())
                }
                miniAppStatus.setVersionDownloaded(appId, versionId, true)
                withContext(coroutineDispatcher) {
                    launch(Job()) { storage.removeOutdatedVersionApp(appId, versionId) }
                }
                return baseSavePath
            }
            // If backend functions correctly, this should never happen
            else -> throw sdkExceptionForInternalServerError()
        }
    }

    @Suppress("SENSELESS_COMPARISON")
    @VisibleForTesting
    internal fun isManifestValid(manifest: ManifestEntity) =
        manifest != null && manifest.files != null && manifest.files.isNotEmpty()

    override fun updateApiClient(apiClient: ApiClient) {
        this.apiClient = apiClient
    }
}
