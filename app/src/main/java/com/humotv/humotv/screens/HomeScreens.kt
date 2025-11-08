package com.humotv.humotv.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.material.placeholder
import com.google.accompanist.placeholder.material.shimmer
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.humotv.humotv.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLEncoder
class HomeViewModel(private val tokenManager: TokenManager) : ViewModel() {

    var homeUiState by mutableStateOf<HomeUiState>(HomeUiState.Loading)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    fun loadHomeFeed(profileId: Int, forceReload: Boolean = false) {
        viewModelScope.launch {
            if (forceReload) {
                isRefreshing = true
            } else {
                homeUiState = HomeUiState.Loading
            }

            try {
                val token = "Bearer ${tokenManager.accessTokenFlow.first()}"
                val feed = RetrofitClient.instance.getHomeFeed(token, profileId)
                homeUiState = HomeUiState.Success(feed)
            } catch (e: Exception) {
                homeUiState = HomeUiState.Error(e.message ?: "Ошибка")
            } finally {
                isRefreshing = false
            }
        }
    }
}

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Success(val feed: HomeFeedResponse) : HomeUiState
}

@Composable
fun HomeScreen(
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
    profileId: Int
) {
    val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
    val uiState by rememberUpdatedState(homeViewModel.homeUiState)
    val isRefreshing by rememberUpdatedState(homeViewModel.isRefreshing)

    LaunchedEffect(key1 = profileId) {
        homeViewModel.loadHomeFeed(profileId)
    }

    SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing),
        onRefresh = { homeViewModel.loadHomeFeed(profileId, forceReload = true) }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text(
                    "HUMO TV",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            when (val state = uiState) {
                is HomeUiState.Loading -> {
                    item { HomeScreenSkeleton() }
                }

                is HomeUiState.Error -> {
                    item {
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is HomeUiState.Success -> {
                    if (state.feed.continueWatching.isNotEmpty()) {
                        item {
                            ContinueWatchingRow(
                                title = "Продолжить просмотр",
                                historyList = state.feed.continueWatching,
                                navController = navController,
                                profileId = profileId
                            )
                        }
                    }
                    state.feed.myList.firstOrNull()?.let {
                        if (it.mediaItems.isNotEmpty()) {
                            item {
                                MediaRow(
                                    title = it.name,
                                    mediaList = it.mediaItems,
                                    navController = navController,
                                    profileId = profileId
                                )
                            }
                        }
                    }
                    items(state.feed.collections) { collection ->
                        MediaRow(
                            title = collection.name,
                            mediaList = collection.mediaItems,
                            navController = navController,
                            profileId = profileId
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaRow(
    title: String,
    mediaList: List<MediaResponse>,
    navController: NavController,
    profileId: Int,
    isContinueWatching: Boolean = false,
    isLoading: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .placeholder(
                    visible = isLoading,
                    highlight = PlaceholderHighlight.shimmer(),
                    shape = RoundedCornerShape(4.dp)
                )
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(if (isLoading) 5 else mediaList.size) { index ->
                val media = if (isLoading) null else mediaList[index]
                MediaPosterItem(
                    media = media,
                    onClick = {
                        media?.let {
                            navController.navigate(AppRoutes.detailRoute(profileId, it.id, it.mediaType))
                        }
                    },
                    modifier = if (isContinueWatching) Modifier
                        .width(180.dp)
                        .height(100.dp)
                    else Modifier
                        .width(120.dp)
                        .height(180.dp),
                    isLoading = isLoading
                )
            }
        }
    }
}



@Composable
fun ContinueWatchingRow(
    title: String,
    historyList: List<WatchHistoryResponse>,
    navController: NavController,
    profileId: Int,
    isLoading: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .placeholder(
                    visible = isLoading,
                    highlight = PlaceholderHighlight.shimmer(),
                    shape = RoundedCornerShape(4.dp)
                )
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(if (isLoading) 5 else historyList.size) { index ->
                val historyItem = if (isLoading) null else historyList[index]
                ContinueWatchingPosterItem(
                    historyItem = historyItem,
                    onClick = {
                        historyItem?.let { item ->
                            val media = item.media
                            val episode = item.episode
                            val startMs = (item.progressSeconds * 1000).toLong()

                            if (episode != null) {
                                // СЕРИАЛ: У нас есть `videoPath` -> Идем прямо в Плеер
                                val path = URLEncoder.encode(episode.videoFile.filePath, "UTF-8")
                                navController.navigate(
                                    AppRoutes.playerRoute(
                                        profileId = profileId,
                                        mediaId = media.id,
                                        episodeId = episode.id,
                                        videoPath = path,
                                        startPositionMs = startMs
                                    )
                                )
                            } else {
                                // ФИЛЬМ: У нас нет `videoPath` -> Идем в Detail,
                                // передавая `startMs`, который Detail применит
                                // к своей кнопке "Play".
                                navController.navigate(
                                    AppRoutes.detailRoute(
                                        profileId = profileId,
                                        mediaId = media.id,
                                        mediaType = media.mediaType,
                                        startPositionMs = startMs
                                    )
                                )
                            }
                        }
                    },
                    isLoading = isLoading
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingPosterItem(
    historyItem: WatchHistoryResponse?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .width(180.dp) 
            .height(100.dp)
            .clickable(onClick = onClick, enabled = !isLoading)
            .placeholder(
                visible = isLoading,
                highlight = PlaceholderHighlight.shimmer()
            )
    ) {
        if (!isLoading) {
            // TODO: Добавить оверлей с прогресс-баром
            PosterImage(url = historyItem?.media?.posterUrl, modifier = Modifier.fillMaxSize())
        }
    }
}


@Composable
fun MediaPosterItem(
    media: MediaResponse?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .clickable(onClick = onClick, enabled = !isLoading)
            .placeholder(
                visible = isLoading,
                highlight = PlaceholderHighlight.shimmer()
            )
    ) {
        if (!isLoading) {
            PosterImage(url = media?.posterUrl, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun HomeScreenSkeleton() {
    Column(Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(16.dp))
        ContinueWatchingRow(
            title = "Продолжить просмотр",
            historyList = emptyList(),
            navController = rememberNavController(),
            profileId = 0,
            isLoading = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        MediaRow(
            title = "Мой список",
            mediaList = emptyList(),
            navController = rememberNavController(),
            profileId = 0,
            isLoading = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        MediaRow(
            title = "Подборки",
            mediaList = emptyList(),
            navController = rememberNavController(),
            profileId = 0,
            isLoading = true
        )
    }
}


@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun MediaRowPreview() {
    HumoTVTheme {
        val mockMedia = MediaResponse(1, "Mock Movie", "poster.jpg", "movie")
        MediaRow(
            title = "Популярное",
            mediaList = listOf(mockMedia, mockMedia, mockMedia),
            navController = rememberNavController(),
            profileId = 1
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun HomeScreenSkeletonPreview() {
    HumoTVTheme {
        HomeScreenSkeleton()
    }
}