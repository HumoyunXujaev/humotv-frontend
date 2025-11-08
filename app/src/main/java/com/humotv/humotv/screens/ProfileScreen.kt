package com.humotv.humotv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.humotv.humotv.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProfileViewModel(private val tokenManager: TokenManager) : ViewModel() {

    var profilesUiState by mutableStateOf<ProfilesUiState>(ProfilesUiState.Loading)
        private set

    var newProfileName by mutableStateOf("")
    var createProfileError by mutableStateOf<String?>(null)

    fun onNewProfileNameChange(name: String) {
        newProfileName = name
    }

    fun loadProfiles(onAuthFailure: () -> Unit) {
        viewModelScope.launch {
            profilesUiState = ProfilesUiState.Loading
            try {
                val token = "Bearer ${tokenManager.accessTokenFlow.first()}"
                val profiles = RetrofitClient.instance.getMyProfiles(token)
                profilesUiState = ProfilesUiState.Success(profiles)
            } catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() == 401) {
                    tokenManager.clearTokens()
                    onAuthFailure()
                } else {
                    profilesUiState = ProfilesUiState.Error(e.message ?: "Ошибка")
                }

            }
        }
    }

    fun createProfile(onSuccess: (Int) -> Unit) {
        if (newProfileName.isBlank()) {
            createProfileError = "Имя не может быть пустым"
            return
        }
        viewModelScope.launch {
            profilesUiState = ProfilesUiState.Loading
            try {
                val token = "Bearer ${tokenManager.accessTokenFlow.first()}"
                val request = ProfileCreate(name = newProfileName)
                val newProfile = RetrofitClient.instance.createProfile(token, request)
                onSuccess(newProfile.id)
            } catch (e: Exception) {
                createProfileError = e.message ?: "Ошибка"
                profilesUiState = ProfilesUiState.Success(emptyList())
            }
        }
    }
}

sealed interface ProfilesUiState {
    object Loading : ProfilesUiState
    data class Error(val message: String) : ProfilesUiState
    data class Success(val profiles: List<ProfileResponse>) : ProfilesUiState
}

@Composable
fun ProfileSelectScreen(
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
    onProfileSelected: (Int) -> Unit
) {
    val profileViewModel: ProfileViewModel = viewModel(factory = viewModelFactory)
    val uiState by rememberUpdatedState(profileViewModel.profilesUiState)

    LaunchedEffect(key1 = true) {
        profileViewModel.loadProfiles(
            onAuthFailure = {
                navController.navigate(AuthRoutes.WELCOME) {
                    popUpTo(AuthRoutes.SPLASH) { inclusive = true }
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is ProfilesUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ProfilesUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is ProfilesUiState.Success -> {
                if (state.profiles.isEmpty()) {
                    CreateProfileScreen(
                        profileViewModel = profileViewModel,
                        onProfileCreated = onProfileSelected
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Кто смотрит?", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(32.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            items(state.profiles) { profile ->
                                ProfileItem(profile = profile, onClick = {
                                    onProfileSelected(profile.id)
                                })
                            }
                            // TODO: Добавить кнопку "Создать еще"
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileItem(profile: ProfileResponse, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            if (profile.avatarUrl != null) {
                PosterImage(
                    url = profile.avatarUrl,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    profile.name.take(1).uppercase(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(profile.name, fontSize = 18.sp, maxLines = 1)
    }
}

@Composable
fun CreateProfileScreen(
    profileViewModel: ProfileViewModel,
    onProfileCreated: (Int) -> Unit
) {
    val newName = profileViewModel.newProfileName
    val error = profileViewModel.createProfileError

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Создайте свой профиль",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "Это позволит вам сохранять свой список и историю просмотра.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = newName,
            onValueChange = profileViewModel::onNewProfileNameChange,
            label = { Text("Имя профиля") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            isError = error != null
        )

        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { profileViewModel.createProfile(onProfileCreated) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Продолжить", fontSize = 18.sp)
        }
    }
}


@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun ProfileSelectScreenPreview() {
    HumoTVTheme {
        val profiles = listOf(
            ProfileResponse(1, "Взрослый", 1, null),
            ProfileResponse(2, "Ребенок", 1, "avatars/fake.jpg") // Пример с аватаркой
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Кто смотрит?", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(profiles) { profile ->
                    ProfileItem(profile = profile, onClick = {})
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun CreateProfileScreenPreview() {
    HumoTVTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Создайте свой профиль",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Это позволит вам сохранять свой список и историю просмотра.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = "Джон",
                onValueChange = { },
                label = { Text("Имя профиля") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                isError = true
            )
            Text(
                "Имя не может быть пустым",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Продолжить", fontSize = 18.sp)
            }
        }
    }
}