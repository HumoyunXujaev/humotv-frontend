package com.humotv.humotv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.humotv.humotv.screens.*
import java.net.URLDecoder
import java.net.URLEncoder

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE50914),
    onPrimary = Color.White,
    secondary = Color(0xFFB00020),
    background = Color(0xFF141414),
    surface = Color(0xFF1C1C1C),
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun HumoTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HumoTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val tokenManager = TokenManager(applicationContext)
                    val viewModelFactory = AppViewModelFactory(tokenManager)
                    AppNavigation(viewModelFactory)
                }
            }
        }
    }
}

class AppViewModelFactory(private val tokenManager: TokenManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                AuthViewModel(tokenManager) as T
            modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
                ProfileViewModel(tokenManager) as T
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(tokenManager) as T
            modelClass.isAssignableFrom(DetailViewModel::class.java) ->
                DetailViewModel(tokenManager) as T
            modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                SearchViewModel(tokenManager) as T
            modelClass.isAssignableFrom(MyListViewModel::class.java) ->
                MyListViewModel(tokenManager) as T
            modelClass.isAssignableFrom(PlayerViewModel::class.java) ->
                PlayerViewModel(tokenManager) as T
            modelClass.isAssignableFrom(AccountViewModel::class.java) ->
                AccountViewModel(tokenManager) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

object AuthRoutes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val PROFILE_SELECT = "profile_select"
}

object AppRoutes {
    // Каркас
    const val MAIN = "main/{profileId}"
    fun mainRoute(profileId: Int) = "main/$profileId"

    // Вкладки
    const val HOME = "home"
    const val SEARCH = "search"
    const val MY_LIST = "my_list"
    const val PROFILE = "profile"

    const val DETAIL = "detail/{profileId}/{mediaId}/{mediaType}?start={startPositionMs}"
    fun detailRoute(profileId: Int, mediaId: Int, mediaType: String, startPositionMs: Long = 0L): String {
        val route = "detail/$profileId/$mediaId/$mediaType"
        return if (startPositionMs > 0) "$route?start=$startPositionMs" else route
    }
    const val PLAYER = "player/{profileId}/{mediaId}/{episodeId}/{videoPath}?start={startPositionMs}"
    fun playerRoute(profileId: Int, mediaId: Int, episodeId: Int, videoPath: String, startPositionMs: Long = 0L): String {
        val route = "player/$profileId/$mediaId/$episodeId/$videoPath"
        return if (startPositionMs > 0) "$route?start=$startPositionMs" else route
    }
}

@Composable
fun AppNavigation(viewModelFactory: AppViewModelFactory) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel(factory = viewModelFactory)

    NavHost(
        navController = navController,
        startDestination = AuthRoutes.SPLASH,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
    ) {
        composable(AuthRoutes.SPLASH) {
            SplashScreen(navController = navController, authViewModel = authViewModel)
        }
        composable(AuthRoutes.WELCOME) {
            WelcomeScreen(navController = navController)
        }
        composable(AuthRoutes.LOGIN) {
            LoginScreen(navController = navController, authViewModel = authViewModel)
        }
        composable(AuthRoutes.REGISTER) {
            RegisterScreen(navController = navController, authViewModel = authViewModel)
        }
        composable(AuthRoutes.PROFILE_SELECT) {
            ProfileSelectScreen(
                navController=navController,
                viewModelFactory = viewModelFactory,
                onProfileSelected = { profileId ->
                    navController.navigate(AppRoutes.mainRoute(profileId)) {
                        popUpTo(AuthRoutes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = AppRoutes.MAIN, // "main/{profileId}"
            arguments = listOf(navArgument("profileId") { type = NavType.IntType })
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getInt("profileId") ?: 0
            MainScreen(
                mainNavController = navController,
                viewModelFactory = viewModelFactory,
                profileId = profileId
            )
        }

        composable(
            route = AppRoutes.DETAIL, // "detail/{...}?start={startPositionMs}"
            arguments = listOf(
                navArgument("profileId") { type = NavType.IntType },
                navArgument("mediaId") { type = NavType.IntType },
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("startPositionMs") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getInt("profileId") ?: 0
            val mediaId = backStackEntry.arguments?.getInt("mediaId") ?: 0
            val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "movie"
            val startPositionMs = backStackEntry.arguments?.getLong("startPositionMs") ?: 0L 

            DetailScreen(
                navController = navController,
                viewModelFactory = viewModelFactory,
                profileId = profileId,
                mediaId = mediaId,
                mediaType = mediaType,
                startPositionMs = startPositionMs
            )
        }
        composable(
            route = AppRoutes.PLAYER, // "player/{...}?start={startPositionMs}"
            arguments = listOf(
                navArgument("profileId") { type = NavType.IntType },
                navArgument("mediaId") { type = NavType.IntType },
                navArgument("episodeId") { type = NavType.IntType },
                navArgument("videoPath") { type = NavType.StringType },
                navArgument("startPositionMs") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getInt("profileId") ?: 0
            val mediaId = backStackEntry.arguments?.getInt("mediaId") ?: 0
            val episodeId = backStackEntry.arguments?.getInt("episodeId") ?: 0
            val videoPath = backStackEntry.arguments?.getString("videoPath") ?: ""
            val startPositionMs = backStackEntry.arguments?.getLong("startPositionMs") ?: 0L 

            PlayerScreen(
                navController = navController,
                viewModelFactory = viewModelFactory,
                videoPath = URLDecoder.decode(videoPath, "UTF-8"),
                profileId = profileId,
                mediaId = mediaId,
                episodeId = if (episodeId == 0) null else episodeId,
                startPositionMs = startPositionMs
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainNavController: NavHostController,
    viewModelFactory: AppViewModelFactory,
    profileId: Int
) {
    val internalNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomNavBar(navController = internalNavController)
        }
    ) { innerPadding ->
        NavHost(
            navController = internalNavController,
            startDestination = AppRoutes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppRoutes.HOME) {
                HomeScreen(
                    navController = mainNavController,
                    viewModelFactory = viewModelFactory,
                    profileId = profileId
                )
            }
            composable(AppRoutes.SEARCH) {
                SearchScreen(
                    navController = mainNavController,
                    viewModelFactory = viewModelFactory,
                    profileId = profileId
                )
            }
            composable(AppRoutes.MY_LIST) {
                MyListScreen(
                    navController = mainNavController,
                    viewModelFactory = viewModelFactory,
                    profileId = profileId
                )
            }
            composable(AppRoutes.PROFILE) {
                AccountScreen(
                    navController = mainNavController,
                    viewModelFactory = viewModelFactory,
                    profileId = profileId
                )
            }
        }
    }
}

@Composable
fun BottomNavBar(navController: NavHostController) {
    val items = listOf(
        BottomNavItem("Главная", Icons.Default.Home, AppRoutes.HOME),
        BottomNavItem("Поиск", Icons.Default.Search, AppRoutes.SEARCH),
        BottomNavItem("Мой список", Icons.Default.List, AppRoutes.MY_LIST),
        BottomNavItem("Аккаунт", Icons.Default.Person, AppRoutes.PROFILE) 
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
        }
    }
}

data class BottomNavItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String
)