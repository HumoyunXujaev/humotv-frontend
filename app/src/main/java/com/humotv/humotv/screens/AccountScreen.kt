package com.humotv.humotv.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.humotv.humotv.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
class AccountViewModel(private val tokenManager: TokenManager) : ViewModel() {

    var uiState by mutableStateOf<AccountUiState>(AccountUiState.Loading)
        private set

    var showEditNameDialog by mutableStateOf(false)
    var showChangePasswordDialog by mutableStateOf(false)

    var newProfileName by mutableStateOf("")
    var oldPassword by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var dialogError by mutableStateOf<String?>(null)
    var dialogIsLoading by mutableStateOf(false)


    fun loadAccountData(profileId: Int, onAuthFailure: () -> Unit) {
        viewModelScope.launch {
            uiState = AccountUiState.Loading
            try {
                val token = "Bearer ${tokenManager.accessTokenFlow.first()}"
                val userAsync = viewModelScope.launch {
                    val user = RetrofitClient.instance.getMe(token)
                    uiState = (uiState as? AccountUiState.Success)?.copy(user = user)
                        ?: AccountUiState.Success(user = user, profile = null)
                }
                val profileAsync = viewModelScope.launch {
                    val profile = RetrofitClient.instance.getProfileDetails(token, profileId)
                    uiState = (uiState as? AccountUiState.Success)?.copy(profile = profile)
                        ?: AccountUiState.Success(user = null, profile = profile)

                    newProfileName = profile.name
                }
                userAsync.join()
                profileAsync.join()
            } catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() == 401) {
                    tokenManager.clearTokens()
                    onAuthFailure()
                } else {
                    uiState = AccountUiState.Error(e.message ?: "Ошибка")
                }
            }
        }
    }

    fun uploadAvatar(profileId: Int, uri: Uri, context: Context, onAuthFailure: () -> Unit) {
        viewModelScope.launch {
            uiState = (uiState as? AccountUiState.Success)?.copy(isAvatarLoading = true) ?: uiState

            try {
                val token = "Bearer ${tokenManager.accessTokenFlow.first()}"

                val inputStream = context.contentResolver.openInputStream(uri)
                val fileBytes = inputStream?.readBytes()
                inputStream?.close()

                if (fileBytes == null) {
                    uiState = (uiState as? AccountUiState.Success)?.copy(isAvatarLoading = false) ?: uiState
                    Toast.makeText(context, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val requestFile = fileBytes.toRequestBody(
                    context.contentResolver.getType(uri)?.toMediaTypeOrNull()
                )
                val body = MultipartBody.Part.createFormData("file", "avatar.jpg", requestFile)

                val updatedProfile = RetrofitClient.instance.uploadAvatar(token, profileId, body)

                uiState = (uiState as? AccountUiState.Success)?.copy(
                    profile = updatedProfile,
                    isAvatarLoading = false
                ) ?: uiState

            } catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() == 401) {
                    tokenManager.clearTokens()
                    onAuthFailure()
                } else {
                    uiState = (uiState as? AccountUiState.Success)?.copy(isAvatarLoading = false) ?: uiState
                    Toast.makeText(context, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateProfileName(profileId: Int, onAuthFailure: () -> Unit) {
        if (newProfileName.isBlank()) {
            dialogError = "Имя не может быть пустым"
            return
        }

        viewModelScope.launch {
            dialogIsLoading = true
            dialogError = null
            try {
                val token = "Bearer ${tokenManager.accessTokenFlow.first()}"
                val request = ProfileCreate(name = newProfileName)
                val updatedProfile = RetrofitClient.instance.updateProfile(token, profileId, request)

                uiState = (uiState as? AccountUiState.Success)?.copy(profile = updatedProfile) ?: uiState
                showEditNameDialog = false

            } catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() == 401) {
                    tokenManager.clearTokens()
                    onAuthFailure()
                } else {
                    dialogError = e.message ?: "Ошибка"
                }
            } finally {
                dialogIsLoading = false
            }
        }
    }

    fun changePassword(context: Context, onAuthFailure: () -> Unit) {
        if (oldPassword.isBlank() || newPassword.isBlank()) {
            dialogError = "Все поля обязательны"
            return
        }
        if (newPassword.length < 4) {
            dialogError = "Новый пароль слишком короткий"
            return
        }

        viewModelScope.launch {
            dialogIsLoading = true
            dialogError = null
            try {
                val token = "Bearer ${tokenManager.accessTokenFlow.first()}"
                val request = PasswordChange(oldPassword = oldPassword, newPassword = newPassword)
                RetrofitClient.instance.changePassword(token, request)

                // Успех
                showChangePasswordDialog = false
                Toast.makeText(context, "Пароль успешно изменен", Toast.LENGTH_SHORT).show()
                oldPassword = ""
                newPassword = ""

            } catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() == 401) {
                    tokenManager.clearTokens()
                    onAuthFailure()
                } else if (e is retrofit2.HttpException && e.code() == 400) {
                    dialogError = "Неверный старый пароль"
                }
                else {
                    dialogError = e.message ?: "Ошибка"
                }
            } finally {
                dialogIsLoading = false
            }
        }
    }

    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            tokenManager.clearTokens()
            onLogoutComplete()
        }
    }
}

sealed interface AccountUiState {
    object Loading : AccountUiState
    data class Error(val message: String) : AccountUiState
    data class Success(
        val user: UserResponse?,
        val profile: ProfileResponse?,
        val isAvatarLoading: Boolean = false
    ) : AccountUiState
}

@Composable
fun AccountScreen(
    navController: NavController,
    viewModelFactory: ViewModelProvider.Factory,
    profileId: Int
) {
    val accountViewModel: AccountViewModel = viewModel(factory = viewModelFactory)
    val uiState by rememberUpdatedState(accountViewModel.uiState)
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            accountViewModel.uploadAvatar(profileId, it, context) {
                // onAuthFailure
                navController.navigate(AuthRoutes.WELCOME) {
                    popUpTo(AuthRoutes.SPLASH) { inclusive = true }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            imagePickerLauncher.launch("image/*")
        } else {
            Toast.makeText(context, "Доступ к галерее необходим", Toast.LENGTH_SHORT).show()
        }
    }

    val onAuthFailure: () -> Unit = {
        navController.navigate(AuthRoutes.WELCOME) {
            popUpTo(AuthRoutes.SPLASH) { inclusive = true }
        }
    }

    LaunchedEffect(key1 = profileId) {
        accountViewModel.loadAccountData(profileId, onAuthFailure)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Аккаунт",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        when (val state = uiState) {
            is AccountUiState.Loading -> {
                CircularProgressIndicator()
            }
            is AccountUiState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            is AccountUiState.Success -> {
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        PosterImage(
                            url = state.profile?.avatarUrl,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                            .align(Alignment.BottomEnd)
                            .clickable {
                                val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    Manifest.permission.READ_MEDIA_IMAGES
                                } else {
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                }

                                when (PackageManager.PERMISSION_GRANTED) {
                                    ContextCompat.checkSelfPermission(context, permission) -> {
                                        imagePickerLauncher.launch("image/*")
                                    }
                                    else -> {
                                        permissionLauncher.launch(permission)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Сменить аватар",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (state.isAvatarLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                AccountInfoRow(
                    title = "Имя профиля",
                    value = state.profile?.name ?: "...",
                    onClick = { accountViewModel.showEditNameDialog = true }
                )

                AccountInfoRow(
                    title = "Email аккаунта",
                    value = state.user?.email ?: "...",
                    onClick = null // Email не редактируем
                )

                AccountInfoRow(
                    title = "Пароль",
                    value = "••••••••",
                    icon = Icons.Default.Key,
                    onClick = { accountViewModel.showChangePasswordDialog = true }
                )

                Spacer(modifier = Modifier.weight(1f))

                OutlinedButton(
                    onClick = {
                        accountViewModel.logout {
                            navController.navigate(AuthRoutes.WELCOME) {
                                popUpTo(AuthRoutes.SPLASH) { inclusive = true }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = "Выйти")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выйти из аккаунта")
                }
            }
        }
    }

    if (accountViewModel.showEditNameDialog) {
        EditNameDialog(
            viewModel = accountViewModel,
            profileId = profileId,
            onAuthFailure = onAuthFailure,
            onDismiss = {
                accountViewModel.showEditNameDialog = false
                accountViewModel.dialogError = null
            }
        )
    }

    if (accountViewModel.showChangePasswordDialog) {
        ChangePasswordDialog(
            viewModel = accountViewModel,
            context = context,
            onAuthFailure = onAuthFailure,
            onDismiss = {
                accountViewModel.showChangePasswordDialog = false
                accountViewModel.dialogError = null
            }
        )
    }
}

@Composable
fun AccountInfoRow(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Edit,
    onClick: (() -> Unit)?
) {
    val modifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (onClick != null) {
            Icon(
                icon,
                contentDescription = "Редактировать",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
    Divider()
}

@Composable
fun EditNameDialog(
    viewModel: AccountViewModel,
    profileId: Int,
    onAuthFailure: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сменить имя профиля") },
        text = {
            Column {
                OutlinedTextField(
                    value = viewModel.newProfileName,
                    onValueChange = { viewModel.newProfileName = it },
                    label = { Text("Новое имя") },
                    isError = viewModel.dialogError != null,
                    singleLine = true
                )
                if (viewModel.dialogError != null) {
                    Text(
                        viewModel.dialogError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.updateProfileName(profileId, onAuthFailure) },
                enabled = !viewModel.dialogIsLoading
            ) {
                if (viewModel.dialogIsLoading) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    Text("Сохранить")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun ChangePasswordDialog(
    viewModel: AccountViewModel,
    context: Context,
    onAuthFailure: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сменить пароль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = viewModel.oldPassword,
                    onValueChange = { viewModel.oldPassword = it },
                    label = { Text("Старый пароль") },
                    isError = viewModel.dialogError != null,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = viewModel.newPassword,
                    onValueChange = { viewModel.newPassword = it },
                    label = { Text("Новый пароль") },
                    isError = viewModel.dialogError != null,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                if (viewModel.dialogError != null) {
                    Text(
                        viewModel.dialogError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.changePassword(context, onAuthFailure) },
                enabled = !viewModel.dialogIsLoading
            ) {
                if (viewModel.dialogIsLoading) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    Text("Сохранить")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}