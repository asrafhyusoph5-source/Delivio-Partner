package com.example.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen

@Composable
fun DelivioAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackEmoji: String? = null
) {
    val cleanUrl = url?.trim().orEmpty()

    if (cleanUrl.isNotBlank()) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(cleanUrl)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            error = { errorState ->
                Log.e("ImageLoad", "Failed to load image from URL: $cleanUrl", errorState.result.throwable)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = "Image unavailable",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Image unavailable",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1
                        )
                    }
                }
            }
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            if (fallbackEmoji != null) {
                Text(fallbackEmoji, fontSize = 24.sp)
            } else {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun MerchantAvatarImage(
    logoUrl: String?,
    bannerUrl: String?,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    fallbackEmoji: String = "🏬"
) {
    val cleanLogo = logoUrl?.trim().orEmpty()
    val cleanBanner = bannerUrl?.trim().orEmpty()
    val cleanImage = imageUrl?.trim().orEmpty()

    val chosenUrl = when {
        cleanLogo.isNotBlank() -> cleanLogo
        cleanBanner.isNotBlank() -> cleanBanner
        cleanImage.isNotBlank() -> cleanImage
        else -> ""
    }

    DelivioAsyncImage(
        url = chosenUrl,
        contentDescription = "Merchant Avatar",
        modifier = modifier,
        fallbackEmoji = fallbackEmoji
    )
}

@Composable
fun MerchantLogoView(
    storeName: String,
    logoUrl: String?,
    bannerUrl: String? = null,
    imageUrl: String? = null,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    letterSize: androidx.compose.ui.unit.TextUnit = 16.sp
) {
    val cleanLogo = logoUrl?.trim().orEmpty()
    val cleanBanner = bannerUrl?.trim().orEmpty()
    val cleanImage = imageUrl?.trim().orEmpty()

    val chosenUrl = when {
        cleanLogo.isNotBlank() -> cleanLogo
        cleanBanner.isNotBlank() -> cleanBanner
        cleanImage.isNotBlank() -> cleanImage
        else -> ""
    }

    val customColors = LocalDelivioColors.current
    val initial = storeName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "D"

    @Composable
    fun LetterFallback() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(customColors.accentTint),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                fontWeight = FontWeight.ExtraBold,
                fontSize = letterSize,
                color = customColors.accent
            )
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(customColors.accentTint),
        contentAlignment = Alignment.Center
    ) {
        if (chosenUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(chosenUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = storeName.ifBlank { "Merchant Logo" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(customColors.accentTint),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = customColors.accent
                        )
                    }
                },
                error = { errorState ->
                    Log.e("ImageLoad", "Failed to load logo from URL: $chosenUrl", errorState.result.throwable)
                    LetterFallback()
                }
            )
        } else {
            LetterFallback()
        }
    }
}

@Composable
fun ImageUrlFieldWithLivePreview(
    label: String,
    url: String,
    onUrlChange: (String) -> Unit,
    placeholder: String = "https://example.com/photo.jpg",
    testTag: String = "image_url_input",
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    val trimmed = url.trim()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(12.dp)
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (trimmed.isNotBlank()) {
            val context = LocalContext.current
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(trimmed)
                    .crossfade(true)
                    .listener(
                        onError = { _, result ->
                            Log.e("ImageLoad", "Live preview error for URL: $trimmed", result.throwable)
                        }
                    )
                    .build()
            )
            val painterState = painter.state

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    when (painterState) {
                        is AsyncImagePainter.State.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = customColors.accent
                            )
                        }
                        is AsyncImagePainter.State.Error -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = "Failed",
                                    tint = StatusClosedRed,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text("Image unavailable", fontSize = 8.sp, color = StatusClosedRed)
                            }
                        }
                        is AsyncImagePainter.State.Success -> {
                            androidx.compose.foundation.Image(
                                painter = painter,
                                contentDescription = "Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Status: ",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        when (painterState) {
                            is AsyncImagePainter.State.Loading -> {
                                Text(
                                    text = "Loading...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = customColors.accent
                                )
                            }
                            is AsyncImagePainter.State.Success -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = StatusOpenGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "OK",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = StatusOpenGreen
                                    )
                                }
                            }
                            is AsyncImagePainter.State.Error -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = StatusClosedRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Failed",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = StatusClosedRed
                                    )
                                }
                            }
                            else -> {
                                Text("Pending", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
                            trimmed
                        else "Must start with https://",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
