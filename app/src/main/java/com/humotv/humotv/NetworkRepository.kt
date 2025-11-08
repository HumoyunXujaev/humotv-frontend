package com.humotv.humotv

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

const val BASE_URL = "http://10.0.2.2:8000/api/v1/"

data class TokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String
)

data class UserResponse(
    val id: Int,
    val email: String,
    @Json(name = "is_active") val isActive: Boolean,
    @Json(name = "is_superuser") val isSuperuser: Boolean
)

data class ProfileResponse(
    val id: Int,
    val name: String,
    @Json(name = "user_id") val userId: Int,
    @Json(name = "avatar_url") val avatarUrl: String?
)

data class ProfileCreate(
    val name: String,
    @Json(name = "avatar_url") val avatarUrl: String? = null
)

data class PasswordChange(
    @Json(name = "old_password") val oldPassword: String,
    @Json(name = "new_password") val newPassword: String
)

data class MediaResponse(
    val id: Int,
    val title: String,
    @Json(name = "poster_url") val posterUrl: String?,
    @Json(name = "media_type") val mediaType: String
)

data class WatchHistoryResponse(
    @Json(name = "progress_seconds") val progressSeconds: Float,
    @Json(name = "last_watched") val lastWatched: String,
    val media: MediaResponse,
    val episode: EpisodeResponse?
)

data class WatchHistoryCreate(
    @Json(name = "media_id") val mediaId: Int,
    @Json(name = "episode_id") val episodeId: Int?,
    @Json(name = "progress_seconds") val progressSeconds: Float
)

data class CollectionResponse(
    val id: Int,
    val name: String,
    @Json(name = "media_items") val mediaItems: List<MediaResponse> = emptyList()
)

data class HomeFeedResponse(
    @Json(name = "continue_watching") val continueWatching: List<WatchHistoryResponse>,
    @Json(name = "my_list") val myList: List<CollectionResponse>,
    val collections: List<CollectionResponse>
)

data class VideoFileResponse(
    val id: Int,
    @Json(name = "file_path") val filePath: String
)

data class EpisodeResponse(
    val id: Int,
    val title: String,
    @Json(name = "episode_number") val episodeNumber: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "video_file") val videoFile: VideoFileResponse
)

data class MovieResponse(
    val id: Int,
    val title: String,
    val description: String?,
    @Json(name = "poster_url") val posterUrl: String?,
    val genres: List<GenreResponse> = emptyList(),
    @Json(name = "video_file") val videoFile: VideoFileResponse
)

data class SeriesResponse(
    val id: Int,
    val title: String,
    val description: String?,
    @Json(name = "poster_url") val posterUrl: String?,
    val genres: List<GenreResponse> = emptyList(),
    val episodes: List<EpisodeResponse> = emptyList()
)

data class GenreResponse(
    val id: Int,
    val name: String
)

data class LoginRequest(val username: String, val password: String)
data class RegisterRequest(val email: String, val password: String)

data class FavoriteCreate(
    @Json(name = "media_id") val mediaId: Int
)

interface ApiService {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): UserResponse

    @GET("auth/me")
    suspend fun getMe(@Header("Authorization") token: String): UserResponse

    @POST("auth/change-password")
    suspend fun changePassword(
        @Header("Authorization") token: String,
        @Body request: PasswordChange
    ): Response<Unit>

    @GET("profiles/")
    suspend fun getMyProfiles(
        @Header("Authorization") token: String
    ): List<ProfileResponse>

    @POST("profiles/")
    suspend fun createProfile(
        @Header("Authorization") token: String,
        @Body request: ProfileCreate
    ): ProfileResponse

    @GET("profiles/{profile_id}")
    suspend fun getProfileDetails(
        @Header("Authorization") token: String,
        @Path("profile_id") profileId: Int
    ): ProfileResponse

    @PATCH("profiles/{profile_id}")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Path("profile_id") profileId: Int,
        @Body request: ProfileCreate // Используем ту же схему
    ): ProfileResponse

    @Multipart
    @POST("profiles/{profile_id}/avatar")
    suspend fun uploadAvatar(
        @Header("Authorization") token: String,
        @Path("profile_id") profileId: Int,
        @Part file: MultipartBody.Part
    ): ProfileResponse


    @GET("home/profiles/{profile_id}/home")
    suspend fun getHomeFeed(
        @Header("Authorization") token: String,
        @Path("profile_id") profileId: Int
    ): HomeFeedResponse

    @GET("media/movies/{movie_id}")
    suspend fun getMovie(
        @Header("Authorization") token: String,
        @Path("movie_id") movieId: Int
    ): MovieResponse

    @GET("media/series/{series_id}")
    suspend fun getSeries(
        @Header("Authorization") token: String,
        @Path("series_id") seriesId: Int
    ): SeriesResponse

    @GET("search/")
    suspend fun searchMedia(
        @Header("Authorization") token: String,
        @Query("q") query: String
    ): List<MediaResponse>

    @GET("collections/profiles/{profile_id}/favorites/")
    suspend fun getFavorites(
        @Header("Authorization") token: String,
        @Path("profile_id") profileId: Int
    ): List<MediaResponse>

    @POST("collections/profiles/{profile_id}/favorites/")
    suspend fun addToFavorites(
        @Header("Authorization") token: String,
        @Path("profile_id") profileId: Int,
        @Body request: FavoriteCreate
    ): MediaResponse

    @DELETE("collections/profiles/{profile_id}/favorites/{media_id}")
    suspend fun removeFromFavorites(
        @Header("Authorization") token: String,
        @Path("profile_id") profileId: Int,
        @Path("media_id") mediaId: Int
    ): Response<Unit> // 204 No Content

    @POST("history/profiles/{profile_id}/history/")
    suspend fun logWatchProgress(
        @Header("Authorization") token: String,
        @Path("profile_id") profileId: Int,
        @Body request: WatchHistoryCreate
    ): WatchHistoryResponse
}

object RetrofitClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    // Retrofit
    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(httpClient)
            .build()
            .create(ApiService::class.java)
    }
}

val Context.dataStore by preferencesDataStore(name = "humotv_prefs")

class TokenManager(private val context: Context) {
    companion object {
        val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    }

    // Flow, который будет сообщать, есть ли у нас токен
    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACCESS_TOKEN_KEY]
    }

    // Сохранить токены
    suspend fun saveTokens(access: String, refresh: String) {
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = access
            preferences[REFRESH_TOKEN_KEY] = refresh
        }
    }

    // Очистить токены (при выходе)
    suspend fun clearTokens() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}