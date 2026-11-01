package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AnonymousRating
import com.example.data.model.CustomerReview
import com.example.ui.theme.LocalDelivioColors
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(
    reviews: List<CustomerReview>,
    averageRating: Double,
    reviewsCount: Int,
    riderReviews: List<AnonymousRating> = emptyList(),
    riderAverageRating: Double = 5.0,
    riderReviewsCount: Int = 0,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val customColors = LocalDelivioColors.current

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Customers, 1: Riders

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ratings & Comments",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("ratings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.testTag("ratings_top_bar")
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Two tabs: Customers and Riders
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = customColors.accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ratings_tab_row")
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            text = "Customers (${reviewsCount})",
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("ratings_tab_customers")
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            text = "Riders (${riderReviewsCount})",
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("ratings_tab_riders")
                )
            }

            if (selectedTabIndex == 0) {
                val customerRatings = remember(reviews) {
                    reviews.map {
                        AnonymousRating(
                            id = it.id,
                            merchantId = it.merchantId,
                            rating = it.ratingMerchant,
                            comment = it.comment,
                            createdAt = it.createdAt
                        )
                    }
                }
                AnonymousRatingsList(
                    ratings = customerRatings,
                    averageRating = averageRating,
                    reviewsCount = reviewsCount,
                    emptySubtitle = "Customer ratings and feedback for your store will appear here.",
                    testTagPrefix = "customer"
                )
            } else {
                AnonymousRatingsList(
                    ratings = riderReviews,
                    averageRating = riderAverageRating,
                    reviewsCount = riderReviewsCount,
                    emptySubtitle = "Ratings left by delivery drivers will appear here.",
                    testTagPrefix = "rider"
                )
            }
        }
    }
}

@Composable
private fun AnonymousRatingsList(
    ratings: List<AnonymousRating>,
    averageRating: Double,
    reviewsCount: Int,
    emptySubtitle: String,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    var selectedFilter by remember { mutableStateOf("All") } // "All", "5", "4", "3", "2", "1", "With comments"

    // Calculate star count distribution
    val starCounts = remember(ratings) {
        val counts = mutableMapOf(5 to 0, 4 to 0, 3 to 0, 2 to 0, 1 to 0)
        ratings.forEach { r ->
            val star = r.rating.roundToInt().coerceIn(1, 5)
            counts[star] = (counts[star] ?: 0) + 1
        }
        counts
    }

    // Filter reviews
    val filteredRatings = remember(ratings, selectedFilter) {
        when (selectedFilter) {
            "5" -> ratings.filter { it.rating.roundToInt() == 5 }
            "4" -> ratings.filter { it.rating.roundToInt() == 4 }
            "3" -> ratings.filter { it.rating.roundToInt() == 3 }
            "2" -> ratings.filter { it.rating.roundToInt() == 2 }
            "1" -> ratings.filter { it.rating.roundToInt() == 1 }
            "With comments" -> ratings.filter { it.comment.isNotBlank() }
            else -> ratings
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("${testTagPrefix}_ratings_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Rating Summary Header Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_summary_card")
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Big Score & Stars
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (reviewsCount > 0) "%.1f".format(averageRating) else "0.0",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            StarRatingBar(rating = if (reviewsCount > 0) averageRating else 0.0)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (reviewsCount == 1) "1 rating" else "$reviewsCount ratings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Right: Star Distribution Bars (5 down to 1)
                        Column(
                            modifier = Modifier.weight(1.4f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (star in 5 downTo 1) {
                                val count = starCounts[star] ?: 0
                                val progress = if (reviewsCount > 0) count.toFloat() / reviewsCount.toFloat() else 0f

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "$star",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = Color(0xFFF59E0B),
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$count",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filterOptions = listOf(
                    "All" to "All ($reviewsCount)",
                    "5" to "5 ★ (${starCounts[5] ?: 0})",
                    "4" to "4 ★ (${starCounts[4] ?: 0})",
                    "3" to "3 ★ (${starCounts[3] ?: 0})",
                    "2" to "2 ★ (${starCounts[2] ?: 0})",
                    "1" to "1 ★ (${starCounts[1] ?: 0})",
                    "With comments" to "With comments"
                )

                filterOptions.forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = key },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = customColors.accentTint,
                            selectedLabelColor = customColors.accent
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) customColors.accent else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.testTag("${testTagPrefix}_filter_$key")
                    )
                }
            }
        }

        // 3. Ratings List or Empty State
        if (filteredRatings.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp)
                        .testTag("${testTagPrefix}_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RateReview,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No ratings yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (selectedFilter == "All")
                                emptySubtitle
                            else "No ratings match the selected filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredRatings, key = { it.id.ifBlank { "${it.createdAt}_${it.rating}" } }) { rating ->
                AnonymousRatingCard(rating = rating, testTagPrefix = testTagPrefix)
            }
        }
    }
}

/**
 * Strictly anonymous card: shows ONLY stars, comment, and date.
 * Never displays names, avatars, order numbers, items, or identities.
 */
@Composable
private fun AnonymousRatingCard(
    rating: AnonymousRating,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }
    val formattedDate = remember(rating.createdAt) {
        if (rating.createdAt > 0) dateFormat.format(Date(rating.createdAt)) else "Recent"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_card_${rating.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top: Stars pill on the left, Date on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Star Rating Tag
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "%.1f".format(rating.rating),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF92400E)
                        )
                    }
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Written Comment (or "(No written comment left)")
            if (rating.comment.isNotBlank()) {
                Text(
                    text = "\"${rating.comment.trim()}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
            } else {
                Text(
                    text = "(No written comment left)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun StarRatingBar(
    rating: Double,
    maxStars: Int = 5,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..maxStars) {
            val starValue = i.toDouble()
            val icon = when {
                rating >= starValue -> Icons.Filled.Star
                rating >= starValue - 0.5 -> Icons.Filled.StarHalf
                else -> Icons.Outlined.StarOutline
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
