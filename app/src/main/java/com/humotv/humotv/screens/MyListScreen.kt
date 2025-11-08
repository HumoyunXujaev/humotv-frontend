package com.humotv.humotv.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.humotv.humotv.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class MyListViewModel(private val tokenManager: TokenManager) : ViewModel() {

    var myListUiState by mutableStateOf<MyListUiState>(MyListUiState.Loading)
        private set

    fun loadMyList(profileId: Int) {
        viewModelScope.launch {
            myListUiState = MyListUiState.Loading
            try {
                val token = "Bearer ${tokenManager.accessTokenFlow.first()}"
                val results = RetrofitClient.instance.getFavorites(token, profileId)
                myListUiState = MyListUiState.Success(results)
            } catch (e: Exception) {
                myListUiState = MyListUiState.Error(e.message ?: "Ошибка")
            }
        }
    }
}

sealed interface MyListUiState {
    object Loading : MyListUiState
    data class Error(val message: String) : MyListUiState
    data class Success(val results: List<MediaResponse>) : MyListUiState
}

@Composable
fun MyListScreen(
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
    profileId: Int
) {
    val myListViewModel: MyListViewModel = viewModel(factory = viewModelFactory)
    val uiState by rememberUpdatedState(myListViewModel.myListUiState)

    LaunchedEffect(key1 = profileId) {
        myListViewModel.loadMyList(profileId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Мой список",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        when (val state = uiState) {
            is MyListUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is MyListUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is MyListUiState.Success -> {
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
                            modifier = Modifier.height(180.dp)
                        )
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun MyListScreenPreview() {
    HumoTVTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "Мой список",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}