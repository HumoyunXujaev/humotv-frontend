package com.humotv.humotv.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.humotv.humotv.RetrofitClient
import com.humotv.humotv.TokenManager
import com.humotv.humotv.WatchHistoryCreate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlayerViewModel(private val tokenManager: TokenManager) : ViewModel() {

    fun logProgress(
        profileId: Int,
        mediaId: Int,
        episodeId: Int?,
        progressSeconds: Float
    ) {
        viewModelScope.launch {
            try {
                val token = "Bearer ${tokenManager.accessTokenFlow.first()}"
                val request = WatchHistoryCreate(
                    mediaId = mediaId,
                    episodeId = episodeId,
                    progressSeconds = progressSeconds
                )
                RetrofitClient.instance.logWatchProgress(token, profileId, request)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}