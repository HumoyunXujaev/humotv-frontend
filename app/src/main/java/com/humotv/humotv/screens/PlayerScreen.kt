package com.humotv.humotv.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.humotv.humotv.BASE_URL
import kotlinx.coroutines.delay

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun PlayerScreen(
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
    videoPath: String,
    profileId: Int,
    mediaId: Int,
    episodeId: Int?,
    startPositionMs: Long
) {
    val context = LocalContext.current
    val playerViewModel: PlayerViewModel = viewModel(factory = viewModelFactory)

    val minioUrl = BASE_URL.replace("8000/api/v1/", "9000")
    val bucketName = "humotv"
    val videoUrl = "${minioUrl}/${bucketName}/${videoPath}"

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            if (startPositionMs > 0) {
                seekTo(startPositionMs)
            }
            prepare()
            playWhenReady = true
        }
    }

    BackHandler {
        navController.popBackStack()
    }

    DisposableEffect(Unit) {
        onDispose {

            playerViewModel.logProgress(
                profileId = profileId,
                mediaId = mediaId,
                episodeId = episodeId,
                progressSeconds = exoPlayer.currentPosition / 1000f // в секундах
            )
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                controllerShowTimeoutMs = 3000
                showController()
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    )

    // Этот эффект будет "жить", пока плеер на экране
    // Он будет отправлять прогресс каждые 15 секунд
    LaunchedEffect(key1 = exoPlayer) {
        while (true) {
            // Ждем 15 секунд
            delay(15_000)

            // Отправляем прогресс, только если видео реально идет
            if (exoPlayer.isPlaying) {
                val progressSeconds = exoPlayer.currentPosition / 1000f // Конвертируем мс в секунды

                // Убедимся, что мы что-то отправляем
                if (progressSeconds > 0) {
                    playerViewModel.logProgress(
                        profileId = profileId,
                        mediaId = mediaId,
                        episodeId = episodeId,
                        progressSeconds = progressSeconds
                    )
                }
            }
        }
    }
}

// Превью для ExoPlayer, к сожалению, сделать сложно,
// так как он требует реальный Context и ресурсы.