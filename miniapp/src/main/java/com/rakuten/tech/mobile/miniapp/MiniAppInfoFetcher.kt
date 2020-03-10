package com.rakuten.tech.mobile.miniapp

import com.rakuten.tech.mobile.miniapp.api.ApiClient
import com.rakuten.tech.mobile.miniapp.api.MiniAppApiImpl
import java.lang.Exception

internal class MiniAppInfoFetcher(private var apiClient: ApiClient): MiniAppApiImpl {

    suspend fun fetchMiniAppList() = apiClient.list()

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun getInfo(appId: String) = run {
        try {
            apiClient.fetchInfo(appId)
        } catch (error: Exception) {
            // If backend functions correctly, this should never happen
            throw sdkExceptionForInternalServerError()
        }
    }

    override fun updateApiClient(apiClient: ApiClient) {
        this.apiClient = apiClient
    }
}
