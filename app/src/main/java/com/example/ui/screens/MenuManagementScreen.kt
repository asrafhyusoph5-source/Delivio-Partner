package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.example.data.model.MenuItem
import com.example.data.model.MenuSchedule
import com.example.data.model.ShopMenu
import com.example.data.model.Subcategory
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuManagementScreen(
    menus: List<ShopMenu>,
    subcategories: List<Subcategory>,
    menuItems: List<MenuItem>,
    currentTime: Long,
    timeZoneId: String,
    onBack: () -> Unit,
    onAddMenu: () -> Unit,
    onEditMenu: (ShopMenu) -> Unit,
    onToggleMenuActive: (String, Boolean) -> Unit,
    onDuplicateMenu: (String) -> Unit,
    onDeleteMenu: (String) -> Unit,
    onReorderSubcategories: (List<Subcategory>) -> Unit,
    onAddSubcategory: (String, (Boolean, Subcategory?, String?) -> Unit) -> Unit,
    onRenameSubcategory: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onDeleteSubcategory: (String, (Boolean, String?) -> Unit) -> Unit,
    onBulkMarkStock: (Set<String>, Boolean, (Boolean, Int, String?) -> Unit) -> Unit,
    onBulkMoveToMenu: (Set<String>, String, (Boolean, Int, String?) -> Unit) -> Unit,
    onBulkSetCategoryClass: (Set<String>, String?, String?, (Boolean, Int, String?) -> Unit) -> Unit,
    onBulkAdjustPrices: (Set<String>, Double, Double, (Boolean, Int, String?) -> Unit) -> Unit,
    onBulkDeleteItems: (Set<String>, (Boolean, Int, String?) -> Unit) -> Unit,
    isMultiOutlet: Boolean = false,
    onPushMenu: () -> Unit = {},
    onPullMenu: () -> Unit = {},
    onSyncMenu: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val customColors = LocalDelivioColors.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Menus, 1: Category Classes, 2: Bulk edit

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("menu_management_screen"),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("menu_mgmt_back_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Menu Management",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = customColors.accent
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Menus", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.testTag("tab_menus")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Category Classes", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.testTag("tab_category_classes")
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Bulk edit", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.testTag("tab_bulk_edit")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> MenusTabContent(
                    menus = menus,
                    menuItems = menuItems,
                    currentTime = currentTime,
                    timeZoneId = timeZoneId,
                    isMultiOutlet = isMultiOutlet,
                    onPushMenu = onPushMenu,
                    onPullMenu = onPullMenu,
                    onSyncMenu = onSyncMenu,
                    onAddMenu = onAddMenu,
                    onEditMenu = onEditMenu,
                    onToggleMenuActive = onToggleMenuActive,
                    onDuplicateMenu = onDuplicateMenu,
                    onDeleteMenu = onDeleteMenu
                )
                1 -> CategoryClassesTabContent(
                    subcategories = subcategories,
                    menuItems = menuItems,
                    onReorder = onReorderSubcategories,
                    onAdd = onAddSubcategory,
                    onRename = onRenameSubcategory,
                    onDelete = onDeleteSubcategory
                )
                2 -> BulkEditTabContent(
                    menus = menus,
                    subcategories = subcategories,
                    menuItems = menuItems,
                    onBulkMarkStock = onBulkMarkStock,
                    onBulkMoveToMenu = onBulkMoveToMenu,
                    onBulkSetCategoryClass = onBulkSetCategoryClass,
                    onBulkAdjustPrices = onBulkAdjustPrices,
                    onBulkDeleteItems = onBulkDeleteItems
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: MENUS
// -------------------------------------------------------------
@Composable
private fun MenusTabContent(
    menus: List<ShopMenu>,
    menuItems: List<MenuItem>,
    currentTime: Long,
    timeZoneId: String,
    isMultiOutlet: Boolean,
    onPushMenu: () -> Unit,
    onPullMenu: () -> Unit,
    onSyncMenu: () -> Unit,
    onAddMenu: () -> Unit,
    onEditMenu: (ShopMenu) -> Unit,
    onToggleMenuActive: (String, Boolean) -> Unit,
    onDuplicateMenu: (String) -> Unit,
    onDeleteMenu: (String) -> Unit
) {
    val customColors = LocalDelivioColors.current
    var menuToDelete by remember { mutableStateOf<ShopMenu?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isMultiOutlet) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().testTag("multi_outlet_menu_sync_bar")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = customColors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Outlets Menu Actions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onPushMenu,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 44.dp)
                                    .testTag("btn_push_menu")
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Push Menu", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = onPullMenu,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 44.dp)
                                    .testTag("btn_pull_menu")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pull Menu", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            Button(
                                onClick = onSyncMenu,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = customColors.accent,
                                    contentColor = customColors.onAccent
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 44.dp)
                                    .testTag("btn_sync_menu")
                            ) {
                                Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync Menu", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Explanation Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = customColors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "A menu is a group of products customers can order at set times, for example a Breakfast Menu from 6:00 AM to 10:00 AM. Products in no menu belong to your Main Menu, which is always available unless you schedule it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section header and Add button
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Menus (${menus.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onAddMenu,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.accent,
                        contentColor = customColors.onAccent
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_menu_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Add Menu", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        items(menus, key = { it.id }) { menu ->
            val productCount = remember(menu.id, menuItems) {
                menuItems.count { it.getEffectiveMenuIds().contains(menu.id) }
            }
            val isAvailableNow = menu.isAvailableNow(currentTime, timeZoneId)
            val statusChipText = menu.nextOpenStatusDescription(currentTime, timeZoneId)
            var showOptionsDropdown by remember { mutableStateOf(false) }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (!menu.active) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditMenu(menu) }
                    .testTag("menu_card_${menu.id}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = menu.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (menu.isMainMenu) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = customColors.accent.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Default",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = customColors.accent,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = menu.scheduleSummary(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = menu.active,
                                onCheckedChange = { onToggleMenuActive(menu.id, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = customColors.accent,
                                    checkedTrackColor = customColors.accentTint
                                ),
                                modifier = Modifier.testTag("menu_active_switch_${menu.id}")
                            )

                            Box {
                                IconButton(onClick = { showOptionsDropdown = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Menu options")
                                }
                                DropdownMenu(
                                    expanded = showOptionsDropdown,
                                    onDismissRequest = { showOptionsDropdown = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showOptionsDropdown = false
                                            onEditMenu(menu)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Duplicate") },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showOptionsDropdown = false
                                            onDuplicateMenu(menu.id)
                                        }
                                    )
                                    if (!menu.isMainMenu) {
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                showOptionsDropdown = false
                                                menuToDelete = menu
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$productCount ${if (productCount == 1) "product" else "products"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (!menu.active) MaterialTheme.colorScheme.surfaceVariant
                            else if (isAvailableNow) StatusOpenGreen.copy(alpha = 0.15f)
                            else StatusWarningAmber.copy(alpha = 0.20f)
                        ) {
                            Text(
                                text = statusChipText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (!menu.active) MaterialTheme.colorScheme.onSurfaceVariant
                                else if (isAvailableNow) StatusOpenGreen
                                else Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    menuToDelete?.let { targetMenu ->
        AlertDialog(
            onDismissRequest = { menuToDelete = null },
            title = { Text("Delete Menu", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("Are you sure you want to delete \"${targetMenu.name}\"? Its products will move to Main Menu.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMenu(targetMenu.id)
                        menuToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { menuToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// TAB 2: CATEGORY CLASSES (REORDER, RENAME, DELETE, ADD)
// -------------------------------------------------------------
@Composable
private fun CategoryClassesTabContent(
    subcategories: List<Subcategory>,
    menuItems: List<MenuItem>,
    onReorder: (List<Subcategory>) -> Unit,
    onAdd: (String, (Boolean, Subcategory?, String?) -> Unit) -> Unit,
    onRename: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onDelete: (String, (Boolean, String?) -> Unit) -> Unit
) {
    val customColors = LocalDelivioColors.current
    var showAddDialog by remember { mutableStateOf(false) }
    var addInput by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var isSubmittingAdd by remember { mutableStateOf(false) }

    var subToRename by remember { mutableStateOf<Subcategory?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }
    var isSubmittingRename by remember { mutableStateOf(false) }

    var subToDelete by remember { mutableStateOf<Subcategory?>(null) }
    var isSubmittingDelete by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Category Classes (${subcategories.size}/30)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Drag or use arrows to reorder how sections appear to customers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        addInput = ""
                        addError = null
                        showAddDialog = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.accent,
                        contentColor = customColors.onAccent
                    ),
                    modifier = Modifier.testTag("add_subcategory_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        if (subcategories.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No custom category classes yet.\nTap + Add to create your first class.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            items(subcategories.size) { index ->
                val sub = subcategories[index]
                val productCount = remember(sub.id, sub.name, menuItems) {
                    menuItems.count {
                        it.subcategoryId == sub.id || (it.subcategoryId == null && it.subcategoryName?.equals(sub.name, ignoreCase = true) == true)
                    }
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("subcat_item_${sub.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Drag / Reorder handle buttons
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val mutable = subcategories.toMutableList()
                                        val item = mutable.removeAt(index)
                                        mutable.add(index - 1, item)
                                        onReorder(mutable)
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", modifier = Modifier.size(20.dp))
                            }
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "Reorder handle",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                            IconButton(
                                onClick = {
                                    if (index < subcategories.size - 1) {
                                        val mutable = subcategories.toMutableList()
                                        val item = mutable.removeAt(index)
                                        mutable.add(index + 1, item)
                                        onReorder(mutable)
                                    }
                                },
                                enabled = index < subcategories.size - 1,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sub.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$productCount ${if (productCount == 1) "product" else "products"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                subToRename = sub
                                renameInput = sub.name
                                renameError = null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { subToDelete = sub },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmittingAdd) showAddDialog = false },
            title = { Text("Type your Category", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = addInput,
                        onValueChange = {
                            addInput = it
                            addError = null
                        },
                        label = { Text("Category Class Name *") },
                        placeholder = { Text("e.g. Rice Meals, Drinks") },
                        singleLine = true,
                        isError = addError != null,
                        supportingText = addError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_subcat_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = addInput.trim()
                        if (trimmed.isBlank()) {
                            addError = "Category name cannot be empty"
                            return@Button
                        }
                        if (trimmed.length > 40) {
                            addError = "Maximum 40 characters allowed"
                            return@Button
                        }
                        if (subcategories.any { it.name.trim().equals(trimmed, ignoreCase = true) }) {
                            addError = "Category \"$trimmed\" already exists"
                            return@Button
                        }
                        if (subcategories.size >= 30) {
                            addError = "Maximum 30 categories reached"
                            return@Button
                        }
                        isSubmittingAdd = true
                        onAdd(trimmed) { success, _, err ->
                            isSubmittingAdd = false
                            if (success) {
                                showAddDialog = false
                            } else {
                                addError = err ?: "Failed to add category"
                            }
                        }
                    },
                    enabled = !isSubmittingAdd,
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.accent, contentColor = customColors.onAccent)
                ) {
                    Text(if (isSubmittingAdd) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }, enabled = !isSubmittingAdd) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename dialog
    subToRename?.let { sub ->
        AlertDialog(
            onDismissRequest = { if (!isSubmittingRename) subToRename = null },
            title = { Text("Rename Category", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = {
                            renameInput = it
                            renameError = null
                        },
                        label = { Text("Category Class Name *") },
                        singleLine = true,
                        isError = renameError != null,
                        supportingText = renameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameInput.trim()
                        if (trimmed.isBlank()) {
                            renameError = "Name cannot be empty"
                            return@Button
                        }
                        if (trimmed.length > 40) {
                            renameError = "Maximum 40 characters"
                            return@Button
                        }
                        if (subcategories.any { it.id != sub.id && it.name.trim().equals(trimmed, ignoreCase = true) }) {
                            renameError = "Category \"$trimmed\" already exists"
                            return@Button
                        }
                        isSubmittingRename = true
                        onRename(sub.id, trimmed) { success, err ->
                            isSubmittingRename = false
                            if (success) {
                                subToRename = null
                            } else {
                                renameError = err ?: "Failed to rename"
                            }
                        }
                    },
                    enabled = !isSubmittingRename,
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.accent, contentColor = customColors.onAccent)
                ) {
                    Text(if (isSubmittingRename) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { subToRename = null }, enabled = !isSubmittingRename) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete dialog
    subToDelete?.let { sub ->
        val count = remember(sub.id, sub.name, menuItems) {
            menuItems.count {
                it.subcategoryId == sub.id || (it.subcategoryId == null && it.subcategoryName?.equals(sub.name, ignoreCase = true) == true)
            }
        }
        AlertDialog(
            onDismissRequest = { if (!isSubmittingDelete) subToDelete = null },
            title = { Text("Delete Category Class", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("$count ${if (count == 1) "product uses" else "products use"} this category. They will move to Other.") },
            confirmButton = {
                Button(
                    onClick = {
                        isSubmittingDelete = true
                        onDelete(sub.id) { _, _ ->
                            isSubmittingDelete = false
                            subToDelete = null
                        }
                    },
                    enabled = !isSubmittingDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { subToDelete = null }, enabled = !isSubmittingDelete) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// TAB 3: BULK EDIT
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkEditTabContent(
    menus: List<ShopMenu>,
    subcategories: List<Subcategory>,
    menuItems: List<MenuItem>,
    onBulkMarkStock: (Set<String>, Boolean, (Boolean, Int, String?) -> Unit) -> Unit,
    onBulkMoveToMenu: (Set<String>, String, (Boolean, Int, String?) -> Unit) -> Unit,
    onBulkSetCategoryClass: (Set<String>, String?, String?, (Boolean, Int, String?) -> Unit) -> Unit,
    onBulkAdjustPrices: (Set<String>, Double, Double, (Boolean, Int, String?) -> Unit) -> Unit,
    onBulkDeleteItems: (Set<String>, (Boolean, Int, String?) -> Unit) -> Unit
) {
    val customColors = LocalDelivioColors.current
    var searchQuery by remember { mutableStateOf("") }
    val selectedItemIds = remember { mutableStateListOf<String>() }

    // Dialogs
    var confirmActionType by remember { mutableStateOf<String?>(null) } // "stock_in", "stock_out", "move_menu", "set_class", "price", "delete"
    var targetMenuId by remember { mutableStateOf("main") }
    var targetSubcategoryId by remember { mutableStateOf<String?>(null) }
    var priceAdjustmentPercentText by remember { mutableStateOf("10") }
    var isPriceFixedMode by remember { mutableStateOf(false) }
    var priceAdjustmentFixedText by remember { mutableStateOf("10") }

    var resultSummaryText by remember { mutableStateOf<String?>(null) }
    var isOperating by remember { mutableStateOf(false) }

    val filteredItems = remember(menuItems, searchQuery) {
        if (searchQuery.isBlank()) menuItems
        else menuItems.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.categoryName.contains(searchQuery, ignoreCase = true) ||
                    (it.subcategoryName?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val isAllSelected = filteredItems.isNotEmpty() && filteredItems.all { selectedItemIds.contains(it.id) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sticky action toolbar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search products to select...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isAllSelected,
                            onCheckedChange = { check ->
                                if (check) {
                                    filteredItems.forEach {
                                        if (!selectedItemIds.contains(it.id)) selectedItemIds.add(it.id)
                                    }
                                } else {
                                    val filteredSet = filteredItems.map { it.id }.toSet()
                                    selectedItemIds.removeAll { it in filteredSet }
                                }
                            }
                        )
                        Text(
                            text = "Select all (${selectedItemIds.size}/${filteredItems.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (selectedItemIds.isNotEmpty()) {
                        TextButton(onClick = { selectedItemIds.clear() }) {
                            Text("Deselect all")
                        }
                    }
                }

                // Action buttons row
                val hasSelection = selectedItemIds.isNotEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { confirmActionType = "stock" },
                        enabled = hasSelection && !isOperating,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Stock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = { confirmActionType = "menu" },
                        enabled = hasSelection && !isOperating,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Menu", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = { confirmActionType = "class" },
                        enabled = hasSelection && !isOperating,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Class", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = { confirmActionType = "price" },
                        enabled = hasSelection && !isOperating,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Price", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = { confirmActionType = "delete" },
                        enabled = hasSelection && !isOperating,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Bulk Delete", tint = if (hasSelection) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        // Product checklist
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredItems, key = { it.id }) { item ->
                val isSelected = selectedItemIds.contains(item.id)
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) customColors.accentTint.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, if (isSelected) customColors.accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (isSelected) selectedItemIds.remove(item.id) else selectedItemIds.add(item.id)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { check ->
                                if (check) selectedItemIds.add(item.id) else selectedItemIds.remove(item.id)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${FormatUtils.formatCurrency(item.price)} • ${item.subcategoryName ?: "Other"} • ${if (item.inStock) "In stock" else "Sold out"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // 1. Stock Dialog
    if (confirmActionType == "stock") {
        AlertDialog(
            onDismissRequest = { confirmActionType = null },
            title = { Text("Update Stock Status", fontWeight = FontWeight.Bold) },
            text = { Text("Change stock status for ${selectedItemIds.size} selected products:") },
            confirmButton = {
                Button(
                    onClick = {
                        isOperating = true
                        val ids = selectedItemIds.toSet()
                        onBulkMarkStock(ids, true) { success, count, err ->
                            isOperating = false
                            confirmActionType = null
                            resultSummaryText = if (success) "Successfully marked $count products In Stock" else "Error: $err"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusOpenGreen)
                ) {
                    Text("Mark In Stock")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        isOperating = true
                        val ids = selectedItemIds.toSet()
                        onBulkMarkStock(ids, false) { success, count, err ->
                            isOperating = false
                            confirmActionType = null
                            resultSummaryText = if (success) "Successfully marked $count products Sold Out" else "Error: $err"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusClosedRed)
                ) {
                    Text("Mark Sold Out")
                }
            }
        )
    }

    // 2. Move to Menu Dialog
    if (confirmActionType == "menu") {
        AlertDialog(
            onDismissRequest = { confirmActionType = null },
            title = { Text("Move to Menu", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select which menu will include these ${selectedItemIds.size} products:")
                    menus.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { targetMenuId = m.id }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = targetMenuId == m.id,
                                onClick = { targetMenuId = m.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(m.name, fontWeight = FontWeight.Bold)
                                Text(m.scheduleSummary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isOperating = true
                        val ids = selectedItemIds.toSet()
                        val chosenName = menus.find { it.id == targetMenuId }?.name ?: "Menu"
                        onBulkMoveToMenu(ids, targetMenuId) { success, count, err ->
                            isOperating = false
                            confirmActionType = null
                            resultSummaryText = if (success) "Moved $count products to $chosenName" else "Error: $err"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.accent, contentColor = customColors.onAccent)
                ) {
                    Text("Move Products")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmActionType = null }) { Text("Cancel") }
            }
        )
    }

    // 3. Set Category Class Dialog
    if (confirmActionType == "class") {
        AlertDialog(
            onDismissRequest = { confirmActionType = null },
            title = { Text("Set Category Class", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set Category Class for ${selectedItemIds.size} products:")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { targetSubcategoryId = null }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = targetSubcategoryId == null,
                            onClick = { targetSubcategoryId = null }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Other (None)", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                    subcategories.forEach { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { targetSubcategoryId = sub.id }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = targetSubcategoryId == sub.id,
                                onClick = { targetSubcategoryId = sub.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(sub.name, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isOperating = true
                        val ids = selectedItemIds.toSet()
                        val subName = subcategories.find { it.id == targetSubcategoryId }?.name
                        onBulkSetCategoryClass(ids, targetSubcategoryId, subName) { success, count, err ->
                            isOperating = false
                            confirmActionType = null
                            resultSummaryText = if (success) "Updated category class for $count products" else "Error: $err"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.accent, contentColor = customColors.onAccent)
                ) {
                    Text("Apply Class")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmActionType = null }) { Text("Cancel") }
            }
        )
    }

    // 4. Price Adjustment Dialog
    if (confirmActionType == "price") {
        AlertDialog(
            onDismissRequest = { confirmActionType = null },
            title = { Text("Change Price", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Adjust prices for ${selectedItemIds.size} selected products:")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = !isPriceFixedMode,
                            onClick = { isPriceFixedMode = false },
                            label = { Text("Percentage (%)") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = isPriceFixedMode,
                            onClick = { isPriceFixedMode = true },
                            label = { Text("Fixed Amount (₱)") }
                        )
                    }

                    if (!isPriceFixedMode) {
                        OutlinedTextField(
                            value = priceAdjustmentPercentText,
                            onValueChange = { priceAdjustmentPercentText = it },
                            label = { Text("Percent adjustment (e.g. 10 for +10%, -5 for -5%)") },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = priceAdjustmentFixedText,
                            onValueChange = { priceAdjustmentFixedText = it },
                            label = { Text("Amount adjustment in ₱ (e.g. 20 for +₱20, -10 for -₱10)") },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isOperating = true
                        val ids = selectedItemIds.toSet()
                        val pct = if (!isPriceFixedMode) priceAdjustmentPercentText.toDoubleOrNull() ?: 0.0 else 0.0
                        val fix = if (isPriceFixedMode) priceAdjustmentFixedText.toDoubleOrNull() ?: 0.0 else 0.0
                        onBulkAdjustPrices(ids, pct, fix) { success, count, err ->
                            isOperating = false
                            confirmActionType = null
                            resultSummaryText = if (success) "Successfully updated prices for $count products" else "Error: $err"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.accent, contentColor = customColors.onAccent)
                ) {
                    Text("Apply Adjustment")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmActionType = null }) { Text("Cancel") }
            }
        )
    }

    // 5. Delete Dialog
    if (confirmActionType == "delete") {
        AlertDialog(
            onDismissRequest = { confirmActionType = null },
            title = { Text("Delete Products", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("Are you sure you want to delete ${selectedItemIds.size} products? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        isOperating = true
                        val ids = selectedItemIds.toSet()
                        onBulkDeleteItems(ids) { success, count, err ->
                            isOperating = false
                            confirmActionType = null
                            selectedItemIds.clear()
                            resultSummaryText = if (success) "Deleted $count products" else "Error: $err"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmActionType = null }) { Text("Cancel") }
            }
        )
    }

    // Result summary dialog
    resultSummaryText?.let { summary ->
        AlertDialog(
            onDismissRequest = { resultSummaryText = null },
            title = { Text("Bulk Operation Result", fontWeight = FontWeight.Bold) },
            text = { Text(summary) },
            confirmButton = {
                Button(
                    onClick = { resultSummaryText = null },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.accent, contentColor = customColors.onAccent)
                ) {
                    Text("OK")
                }
            }
        )
    }
}
