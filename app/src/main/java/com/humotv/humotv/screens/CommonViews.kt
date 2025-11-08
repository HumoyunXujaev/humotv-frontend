package com.humotv.humotv.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.humotv.humotv.BASE_URL

@Composable
fun PosterImage(url: String?, modifier: Modifier = Modifier) {
    val fullUrl = if (url != null && !url.startsWith("http")) {
        // http://10.0.2.2:8000/api/v1/ -> http://10.0.2.2:9000/
        val minioUrl = BASE_URL.replace("8000/api/v1/", "9000")
        val bucketName = "humotv" // Из твоего .env
        "${minioUrl.removeSuffix("/")}/${bucketName}/${url.removePrefix("/")}"
    } else {
        url
    }

    Log.d("PosterImage", "Loading image from: $fullUrl")

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(fullUrl)
            .crossfade(true)
            .build(),
        contentDescription = "Poster",
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        filterQuality = FilterQuality.Medium
    )
}