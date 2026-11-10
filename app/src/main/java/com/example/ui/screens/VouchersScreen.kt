package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MerchantVoucher
import com.example.ui.theme.DelivioYellow
import com.example.util.FormatUtils

enum class VoucherTab(val title: String) {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    EXPIRED("Expired")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VouchersScreen(
    vouchers: List<MerchantVoucher>,
    onCreateVoucher: () -> Unit,
    onEditVoucher: (MerchantVoucher) -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var selectedTab by remember { mutableStateOf(VoucherTab.ACTIVE) }

    val activeVouchers = remember(vouchers) {
        vouchers.filter { it.isEffectiveActive }
    }
    val inactiveVouchers = remember(vouchers) {
        vouchers.filter { !it.active && !it.isExpired }
    }
    val expiredVouchers = remember(vouchers) {
        vouchers.filter { it.isExpired }
    }

    val displayedList = when (selectedTab) {
        VoucherTab.ACTIVE -> activeVouchers
        VoucherTab.INACTIVE -> inactiveVouchers
        VoucherTab.EXPIRED -> expiredVouchers
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Vouchers",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${vouchers.size} total promo vouchers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("vouchers_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = onCreateVoucher,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = DelivioYellow,
                            contentColor = Color(0xFF1A1A1A)
                        ),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("create_voucher_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateVoucher,
                containerColor = DelivioYellow,
                contentColor = Color(0xFF1A1A1A),
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Create voucher", fontWeight = FontWeight.Bold) },
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .testTag("create_voucher_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tabs: Active, Inactive, Expired
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = DelivioYellow,
                        height = 3.dp
                    )
                }
            ) {
                VoucherTab.values().forEach { tab ->
                    val count = when (tab) {
                        VoucherTab.ACTIVE -> activeVouchers.size
                        VoucherTab.INACTIVE -> inactiveVouchers.size
                        VoucherTab.EXPIRED -> expiredVouchers.size
                    }
                    val isSelected = selectedTab == tab
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) DelivioYellow else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$count",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }

            if (displayedList.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CardGiftcard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Text(
                            text = when (selectedTab) {
                                VoucherTab.ACTIVE -> "No active vouchers"
                                VoucherTab.INACTIVE -> "No inactive vouchers"
                                VoucherTab.EXPIRED -> "No expired vouchers"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (selectedTab) {
                                VoucherTab.ACTIVE -> "Create promotional vouchers to attract more customers and increase your order volume."
                                VoucherTab.INACTIVE -> "Vouchers you temporarily switch off will appear here for easy reactivation."
                                VoucherTab.EXPIRED -> "Past vouchers that passed their expiry date will be listed here."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (selectedTab == VoucherTab.ACTIVE) {
                            Button(
                                onClick = onCreateVoucher,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DelivioYellow,
                                    contentColor = Color(0xFF1A1A1A)
                                ),
                                modifier = Modifier.testTag("empty_state_create_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+ Create voucher", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayedList, key = { it.code }) { voucher ->
                        VoucherItemCard(
                            voucher = voucher,
                            onClick = { onEditVoucher(voucher) },
                            onToggleActive = { active -> onToggleActive(voucher.code, active) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoucherItemCard(
    voucher: MerchantVoucher,
    onClick: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("voucher_card_${voucher.code}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Code Badge, Title, and Active Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Code chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DelivioYellow.copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DelivioYellow),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = voucher.code,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = voucher.title.ifBlank { "Untitled Voucher" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = voucher.active,
                    onCheckedChange = { onToggleActive(it) },
                    enabled = !voucher.isExpired,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF1A1A1A),
                        checkedTrackColor = DelivioYellow
                    ),
                    modifier = Modifier.testTag("voucher_switch_${voucher.code}")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main details: Discount and minimum order
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = voucher.discountDisplay,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                val minOrderStr = if (voucher.minSubtotal > 0.0) {
                    "Min order ₱${FormatUtils.formatCurrency(voucher.minSubtotal)}"
                } else {
                    "No minimum spend"
                }
                Text(
                    text = minOrderStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Chips Row: Visibility, Uses, Expiry
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Visibility Chip
                if (voucher.isPublic) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE8F5E9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF81C784))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Shown to customers",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Hidden (code only)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Uses chip
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = voucher.usesDisplay,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // Expiry chip
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (voucher.isExpired) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = voucher.expiryDisplay,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (voucher.isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Stats row (if redemptions exist)
            if (voucher.redemptionsCount > 0 || voucher.totalDiscountGiven > 0.0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total Discount Given",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₱${FormatUtils.formatCurrency(voucher.totalDiscountGiven)} • ${voucher.ordersCount} orders",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
