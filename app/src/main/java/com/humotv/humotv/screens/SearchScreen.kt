package com.humotv.humotv.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.humotv.humotv.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchViewModel(private val tokenManager: TokenManager) : ViewModel() {

    var searchQuery by mutableStateOf("")
        private set

    var searchUiState by mutableStateOf<SearchUiState>(SearchUiState.Idle)
        private set

    fun onSearchQueryChange(query: String) {
        searchQuery = query // Это обновляет состояние в ViewModel

        // Не ищем, пока юзер не ввел хотя бы 3 символа
        if (query.length < 3) {
            searchUiState = SearchUiState.Idle
            return
        }

        viewModelScope.launch {
            searchUiState = SearchUiState.Loading
            try {
                val token = "Bearer ${tokenManager.accessTokenFlow.first()}"
                val results = RetrofitClient.instance.searchMedia(token, query)
                searchUiState = SearchUiState.Success(results)
            } catch (e: Exception) {
                searchUiState = SearchUiState.Error(e.message ?: "Ошибка")
            }
        }
    }
}

sealed interface SearchUiState {
    object Idle : SearchUiState
    object Loading : SearchUiState
    data class Error(val message: String) : SearchUiState
    data class Success(val results: List<MediaResponse>) : SearchUiState
}

@Composable
fun SearchScreen(
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
    profileId: Int
) {
    val searchViewModel: SearchViewModel = viewModel(factory = viewModelFactory)
    val query = searchViewModel.searchQuery
    val uiState by rememberUpdatedState(searchViewModel.searchUiState)

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        TextField(
            value = query,
            onValueChange = { searchViewModel.onSearchQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Поиск фильмов и сериалов") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Поиск") },
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )

        // Results
        when (val state = uiState) {
            is SearchUiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Введите запрос для поиска...")
                }
            }
            is SearchUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is SearchUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is SearchUiState.Success -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3), // 3 постера в ряд
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.results) { media ->
                        MediaPosterItem(
                            media = media,
                            onClick = {
                                navController.navigate(AppRoutes.detailRoute(profileId, media.id, media.mediaType))
                            },
                            modifier = Modifier.height(180.dp) // Фикс. высота
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun SearchScreenPreview() {
    HumoTVTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            TextField(
                value = "Запрос",
                onValueChange = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Поиск фильмов и сериалов") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Поиск") },
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }
    }
}