package com.humotv.humotv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.humotv.humotv.AuthRoutes
import com.humotv.humotv.BASE_URL
import com.humotv.humotv.HumoTVTheme
import com.humotv.humotv.RegisterRequest
import com.humotv.humotv.RetrofitClient
import com.humotv.humotv.TokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first

class AuthViewModel(private val tokenManager: TokenManager) : ViewModel() {

    var uiState by mutableStateOf(AuthUiState())
        private set

    val accessTokenFlow: Flow<String?> = tokenManager.accessTokenFlow

    fun onEmailChange(email: String) {
        uiState = uiState.copy(email = email)
    }

    fun onPasswordChange(password: String) {
        uiState = uiState.copy(password = password)
    }

    fun login(onLoginSuccess: () -> Unit) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val client = OkHttpClient()
                val requestBody = FormBody.Builder()
                    .add("username", uiState.email)
                    .add("password", uiState.password)
                    .build()

                val request = Request.Builder()
                    .url(BASE_URL + "auth/login")
                    .post(requestBody)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        uiState = uiState.copy(isLoading = false, error = e.message)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.body?.string()?.let { responseBody ->
                            if (response.isSuccessful) {
                                val json = JSONObject(responseBody)
                                val access = json.getString("access_token")
                                val refresh = json.getString("refresh_token")
                                viewModelScope.launch {
                                    tokenManager.saveTokens(access, refresh)
                                    uiState = uiState.copy(isLoading = false)
                                    onLoginSuccess()
                                }
                            } else {
                                val json = JSONObject(responseBody)
                                val detail = json.getString("detail")
                                uiState = uiState.copy(isLoading = false, error = detail)
                            }
                        }
                    }
                })

            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun register(onRegisterSuccess: () -> Unit) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val request = RegisterRequest(uiState.email, uiState.password)
                RetrofitClient.instance.register(request)
                login(onRegisterSuccess)
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = "Ошибка регистрации: ${e.message}")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            tokenManager.clearTokens()
        }
    }
}


data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@Composable
fun SplashScreen(navController: NavController, authViewModel: AuthViewModel) {

    LaunchedEffect(key1 = true) {

        val accessToken = authViewModel.accessTokenFlow.first()

        val destination = if (accessToken.isNullOrEmpty()) {
            AuthRoutes.WELCOME
        } else {
            AuthRoutes.PROFILE_SELECT
        }

        navController.navigate(destination) {
            popUpTo(AuthRoutes.SPLASH) { inclusive = true }
        }
    }

    // Этот Box будет отображаться, ПОКА LaunchedEffect загружает данные
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text("HUMO TV", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun WelcomeScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
    ) {
        Text("HUMO TV", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { navController.navigate(AuthRoutes.LOGIN) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Войти", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { navController.navigate(AuthRoutes.REGISTER) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Регистрация", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun AuthScreen(
    title: String,
    buttonText: String,
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onButtonClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))

        if (uiState.error != null) {
            Text(uiState.error, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text("Пароль") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = onButtonClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(buttonText, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun LoginScreen(navController: NavController, authViewModel: AuthViewModel) {
    val uiState = authViewModel.uiState
    AuthScreen(
        title = "Вход",
        buttonText = "Войти",
        uiState = uiState,
        onEmailChange = authViewModel::onEmailChange,
        onPasswordChange = authViewModel::onPasswordChange,
        onButtonClick = {
            authViewModel.login {
                navController.navigate(AuthRoutes.PROFILE_SELECT) {
                    popUpTo(AuthRoutes.WELCOME) { inclusive = true }
                }
            }
        }
    )
}

@Composable
fun RegisterScreen(navController: NavController, authViewModel: AuthViewModel) {
    val uiState = authViewModel.uiState
    AuthScreen(
        title = "Регистрация",
        buttonText = "Создать аккаунт",
        uiState = uiState,
        onEmailChange = authViewModel::onEmailChange,
        onPasswordChange = authViewModel::onPasswordChange,
        onButtonClick = {
            authViewModel.register {
                navController.navigate(AuthRoutes.PROFILE_SELECT) {
                    popUpTo(AuthRoutes.WELCOME) { inclusive = true }
                }
            }
        }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun WelcomeScreenPreview() {
    HumoTVTheme {
        WelcomeScreen(navController = rememberNavController())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
fun LoginScreenPreview() {
    HumoTVTheme {
        AuthScreen(
            title = "Вход",
            buttonText = "Войти",
            uiState = AuthUiState(error = "Неверный пароль"),
            onEmailChange = {},
            onPasswordChange = {},
            onButtonClick = {}
        )
    }
}