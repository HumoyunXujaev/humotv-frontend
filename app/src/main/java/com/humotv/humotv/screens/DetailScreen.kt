package com.humotv.humotv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.humotv.humotv.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLEncoder

class DetailViewModel(private val tokenManager: TokenManager) : ViewModel() {

    var detailUiState by mutableStateOf<DetailUiState>(DetailUiState.Loading)
        private set

    var isFavorite by mutableStateOf(false)
        private set

    fun loadMediaDetail(profileId: Int, mediaId: Int, mediaType: String) {
        viewModelScope.launch {
            detailUiState = DetailUiState.Loading
            isFavorite = false
            try {
                val token = "Bearer ${tokenManager.accessTokenFlow.first()}"

                val favorites = RetrofitClient.instance.getFavorites(token, profileId)
                isFavorite = favorites.any { it.id == mediaId } 

                if (mediaType == "movie") {
                    val movie = RetrofitClient.instance.getMovie(token, mediaId)
                    detailUiState = DetailUiState.MovieSuccess(movie)
                } else {
                    val series = RetrofitClient.instance.getSeries(token, mediaId)
                    detailUiState = DetailUiState.SeriesSuccess(series)
                }
            } catch (e: Exception) {
                detailUiState = DetailUiState.Error(e.message ?: "Ошибка")
            }
        }
    }

    fun toggleFavorite(profileId: Int, mediaId: Int) {
        viewModelScope.launch {
            val token = "Bearer ${tokenManager.accessTokenFlow.first()}"
            try {
                if (isFavorite) {
                    RetrofitClient.instance.removeFromFavorites(token, profileId, mediaId)
                    isFavorite = false
                } else {
                    RetrofitClient.instance.addToFavorites(token, profileId, FavoriteCreate(mediaId))
                    isFavorite = true
                }
            } catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() == 409) {
                    isFavorite = true
                } else {
                    // TODO: Показать snackbar с ошибкой
                    e.printStackTrace()
                }
            }
        }
    }
}

sealed interface DetailUiState {
    object Loading : DetailUiState
    data class Error(val message: String) : DetailUiState
    data class MovieSuccess(val movie: MovieResponse) : DetailUiState
    data class SeriesSuccess(val series: SeriesResponse) : DetailUiState
}

@Composable
fun DetailScreen(
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
    profileId: Int,
    mediaId: Int,
    mediaType: String,
    startPositionMs: Long
) {
    val detailViewModel: DetailViewModel = viewModel(factory = viewModelFactory)
    val uiState by rememberUpdatedState(detailViewModel.detailUiState)
    val isFavorite by rememberUpdatedState(detailViewModel.isFavorite) 

    LaunchedEffect(key1 = mediaId, key2 = profileId) {
        detailViewModel.loadMediaDetail(profileId, mediaId, mediaType)
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxSize().padding(64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is DetailUiState.Error -> {
                item {
                    Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            }
            is DetailUiState.MovieSuccess -> {
                val movie = state.movie
                item { PosterHeader(movie.posterUrl) }
                item {
                    InfoBlock(
                        title = movie.title,
                        description = movie.description ?: "",
                        isFavorite = isFavorite,
                        onPlayClick = {
                            val path = URLEncoder.encode(movie.videoFile.filePath, "UTF-8")
                            navController.navigate(
                                AppRoutes.playerRoute(
                                    profileId = profileId,
                                    mediaId = movie.id,
                                    episodeId = 0,
                                    videoPath = path,
                                    startPositionMs = startPositionMs
                                )
                            )
                        },
                        onFavoriteClick = {
                            detailViewModel.toggleFavorite(profileId, movie.id)
                        }
                    )
                }
            }
            is DetailUiState.SeriesSuccess -> {
                val series = state.series
                item { PosterHeader(series.posterUrl) }
                item {
                    InfoBlock(
                        title = series.title,
                        description = series.description ?: "",
                        isFavorite = isFavorite,
                        onPlayClick = {
                            series.episodes.firstOrNull()?.let {
                                val path = URLEncoder.encode(it.videoFile.filePath, "UTF-8")
                                navController.navigate(
                                    AppRoutes.playerRoute(
                                        profileId = profileId,
                                        mediaId = series.id,
                                        episodeId = it.id,
                                        videoPath = path,
                                        startPositionMs = startPositionMs
                                    )
                                )
                            }
                        },
                        onFavoriteClick = {
                            detailViewModel.toggleFavorite(profileId, series.id)
                        }
                    )
                }
                item {
                    Text(
                        "Эпизоды",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp)
                    )
                }
                items(series.episodes) { episode ->
                    EpisodeItem(
                        episode = episode,
                        onPlayClick = {
                            val path = URLEncoder.encode(episode.videoFile.filePath, "UTF-8")
                            navController.navigate(
                                AppRoutes.playerRoute(
                                    profileId = profileId,
                                    mediaId = series.id,
                                    episodeId = episode.id,
                                    videoPath = path,
                                    startPositionMs = 0L
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PosterHeader(posterUrl: String?) {
    Box(modifier = Modifier
        .fillMaxWidth()
        .height(400.dp)) {
        PosterImage(
            url = posterUrl,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                    startY = 300f
                )
            ))
    }
}

@Composable
fun InfoBlock(
    title: String,
    description: String,
    isFavorite: Boolean,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onPlayClick,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Смотреть")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Смотреть")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onFavoriteClick,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            val icon = if (isFavorite) Icons.Default.Check else Icons.Default.Add
            val text = if (isFavorite) "В моем списке" else "Мой список"
            Icon(icon, contentDescription = text)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(description, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun EpisodeItem(episode: EpisodeResponse, onPlayClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onPlayClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier
            .width(120.dp)
            .height(70.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "S${episode.seasonNumber} E${episode.episodeNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Text(episode.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DetailScreenPreview() {
    HumoTVTheme {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { PosterHeader(null) }
            item {
                InfoBlock(
                    title = "Название Фильма",
                    description = "Длинное описание фильма, которое занимает несколько строк и рассказывает о сюжете.",
                    isFavorite = true,
                    onPlayClick = {},
                    onFavoriteClick = {}
                )
            }
            item {
                Text(
                    "Эпизоды",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp)
                )
            }
            item {
                EpisodeItem(
                    episode = EpisodeResponse(1, "Начало", 1, 1, VideoFileResponse(1, "")),
                    onPlayClick = {}
                )
            }
            item {
                EpisodeItem(
                    episode = EpisodeResponse(2, "Конец", 2, 1, VideoFileResponse(2, "")),
                    onPlayClick = {}
                )
            }
        }
    }
}