package com.example.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MerchantSettings
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusOpenGreen
import com.example.util.BluetoothPrinterHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothPrinterScreen(
    settings: MerchantSettings,
    onSavePrinter: (name: String, address: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val customColors = LocalDelivioColors.current
    val coroutineScope = rememberCoroutineScope()

    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selectedDeviceAddress by remember(settings.bluetoothPrinterAddress) {
        mutableStateOf(settings.bluetoothPrinterAddress)
    }
    var selectedDeviceName by remember(settings.bluetoothPrinterName) {
        mutableStateOf(settings.bluetoothPrinterName)
    }

    var isPrintingTest by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorMessage by remember { mutableStateOf(false) }

    fun refreshPairedDevices() {
        if (BluetoothPrinterHelper.hasBluetoothPermissions(context)) {
            val list = BluetoothPrinterHelper.getPairedPrinters(context)
            pairedDevices = list
            if (selectedDeviceAddress.isBlank() && list.isNotEmpty()) {
                selectedDeviceAddress = list.first().address
                @SuppressLint("MissingPermission")
                selectedDeviceName = list.first().name ?: "Thermal Printer"
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) {
            refreshPairedDevices()
            statusMessage = "Bluetooth permissions granted. Select your 58mm printer."
            isErrorMessage = false
        } else {
            statusMessage = "Bluetooth permissions are required to scan and pair printers."
            isErrorMessage = true
        }
    }

    LaunchedEffect(Unit) {
        if (BluetoothPrinterHelper.hasBluetoothPermissions(context)) {
            refreshPairedDevices()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Bluetooth Thermal Printer",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("printer_screen_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize().testTag("bluetooth_printer_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Info Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedDeviceAddress.isNotBlank()) customColors.accentTint.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(customColors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = customColors.onAccent, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedDeviceAddress.isNotBlank()) "Paired 58mm Printer" else "No Printer Paired",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (selectedDeviceAddress.isNotBlank())
                                "$selectedDeviceName ($selectedDeviceAddress)"
                            else "Select a paired Bluetooth receipt printer below to print orders automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Feedback / Status alert
            statusMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isErrorMessage) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else StatusOpenGreen.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isErrorMessage) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isErrorMessage) MaterialTheme.colorScheme.error else StatusOpenGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isErrorMessage) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Scan & Search Action Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Device Discovery", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Make sure your 58 mm ESC/POS thermal printer is turned on and paired in your Android device's Bluetooth settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            if (!BluetoothPrinterHelper.hasBluetoothPermissions(context)) {
                                permissionLauncher.launch(BluetoothPrinterHelper.getRequiredPermissions())
                            } else {
                                refreshPairedDevices()
                                statusMessage = "Refreshed paired devices (${pairedDevices.size} found)."
                                isErrorMessage = false
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("scan_bluetooth_printers_btn")
                    ) {
                        Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan & Refresh Bluetooth Printers", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Paired Devices List
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Paired Devices (${pairedDevices.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (pairedDevices.isEmpty()) {
                        Text(
                            text = "No paired Bluetooth devices detected. Tap \"Scan & Refresh\" above or pair your printer in Android Settings > Bluetooth.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        pairedDevices.forEach { dev ->
                            @SuppressLint("MissingPermission")
                            val devName = dev.name ?: "Unknown Printer"
                            val isSelected = dev.address == selectedDeviceAddress

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) customColors.accentTint.copy(alpha = 0.45f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) customColors.accent else androidx.compose.ui.graphics.Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedDeviceAddress = dev.address
                                        selectedDeviceName = devName
                                        onSavePrinter(devName, dev.address)
                                        statusMessage = "Selected printer: $devName ($selectedDeviceAddress)"
                                        isErrorMessage = false
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Print,
                                        contentDescription = null,
                                        tint = if (isSelected) customColors.accent else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(devName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(dev.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = customColors.accent
                                    ) {
                                        Text(
                                            text = "Active",
                                            color = customColors.onAccent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    TextButton(onClick = {
                                        selectedDeviceAddress = dev.address
                                        selectedDeviceName = devName
                                        onSavePrinter(devName, dev.address)
                                    }) {
                                        Text("Select")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }

            // Test Print Action Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Printer Verification", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Sends a 58 mm ESC/POS alignment & test receipt to ensure wireless connection and paper feed work properly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (!BluetoothPrinterHelper.hasBluetoothPermissions(context)) {
                                permissionLauncher.launch(BluetoothPrinterHelper.getRequiredPermissions())
                                return@Button
                            }
                            if (selectedDeviceAddress.isBlank()) {
                                statusMessage = "Please select a paired printer first."
                                isErrorMessage = true
                                return@Button
                            }

                            isPrintingTest = true
                            statusMessage = "Sending test receipt to $selectedDeviceName..."
                            isErrorMessage = false

                            coroutineScope.launch {
                                val result = BluetoothPrinterHelper.printTestReceipt(
                                    context = context,
                                    deviceAddress = selectedDeviceAddress,
                                    storeName = settings.displayNameWithCode.ifBlank { settings.storeName.ifBlank { "Delivio Partner" } }
                                )
                                isPrintingTest = false
                                if (result.isSuccess) {
                                    statusMessage = "Test receipt printed successfully!"
                                    isErrorMessage = false
                                } else {
                                    statusMessage = "Printing failed: ${result.exceptionOrNull()?.localizedMessage ?: "Connection error"}"
                                    isErrorMessage = true
                                }
                            }
                        },
                        enabled = !isPrintingTest && selectedDeviceAddress.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("printer_test_print_btn")
                    ) {
                        if (isPrintingTest) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = customColors.onAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Printing Test Receipt...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send Test Print (58mm)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
