package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.model.Order
import com.example.data.model.ReceiptDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID

object BluetoothPrinterHelper {

    // Standard Serial Port Profile (SPP) UUID for bluetooth thermal printers
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    fun hasBluetoothPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val adapter = getBluetoothAdapter(context) ?: return false
        return adapter.isEnabled
    }

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(context: Context): List<BluetoothDevice> {
        if (!hasBluetoothPermissions(context)) return emptyList()
        val adapter = getBluetoothAdapter(context) ?: return emptyList()
        return try {
            adapter.bondedDevices?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Converts formatted text to ESC/POS bytes for standard 58mm thermal printers.
     */
    fun createEscPosBytesFromText(text: String): ByteArray {
        val bytes = mutableListOf<Byte>()

        // 1. Initialize printer: ESC @ (0x1B 0x40)
        bytes.add(0x1B.toByte())
        bytes.add(0x40.toByte())

        // 2. Set default character font: ESC M 0 (Font A standard)
        bytes.add(0x1B.toByte())
        bytes.add(0x4D.toByte())
        bytes.add(0x00.toByte())

        // 3. Line spacing default: ESC 2
        bytes.add(0x1B.toByte())
        bytes.add(0x32.toByte())

        val charset = Charset.forName("CP437")
        val lines = text.split("\n")

        for (line in lines) {
            val trimmed = line.trimEnd('\r')
            // Replace Philippine Peso sign ₱ with P or string representation for compatibility with ASCII thermal printers
            val sanitized = trimmed.replace("₱", "P")
            val encoded = sanitized.toByteArray(charset)
            for (b in encoded) {
                bytes.add(b)
            }
            bytes.add(0x0A.toByte()) // Line feed LF
        }

        // Feed 4 blank lines for paper tear-off
        bytes.add(0x1B.toByte())
        bytes.add(0x64.toByte())
        bytes.add(0x04.toByte())

        // Full cut command (if printer supports cut): GS V 0 (0x1D 0x56 0x00)
        bytes.add(0x1D.toByte())
        bytes.add(0x56.toByte())
        bytes.add(0x00.toByte())

        return bytes.toByteArray()
    }

    /**
     * Prints test receipt to paired Bluetooth printer
     */
    suspend fun printTestReceipt(
        context: Context,
        deviceAddress: String,
        storeName: String = "Delivio Partner"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val testText = buildString {
            appendLine("--------------------------------")
            appendLine("       DELIVIO PARTNER")
            appendLine("      BLUETOOTH PRINTER")
            appendLine("--------------------------------")
            appendLine("Shop: $storeName")
            appendLine("Status: Connected & Ready")
            appendLine("Width: 58 mm (ESC/POS)")
            val df = java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.US)
            appendLine("Date: ${df.format(java.util.Date())}")
            appendLine("--------------------------------")
            appendLine("Ready to print order receipts!")
            appendLine("--------------------------------")
        }

        sendRawBytesToDevice(context, deviceAddress, createEscPosBytesFromText(testText))
    }

    /**
     * Prints Merchant's Copy and Customer's Copy of an order to 58mm bluetooth printer
     */
    suspend fun printOrder(
        context: Context,
        deviceAddress: String,
        order: Order,
        storeName: String,
        design: ReceiptDesign = ReceiptDesign()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // Generate formatted receipt using ReceiptPrinterHelper with 58mm column width
        val receiptText = ReceiptPrinterHelper.generateFormattedReceiptText(
            order = order,
            storeName = storeName,
            paperSize = "58mm",
            design = design
        )
        val escPosBytes = createEscPosBytesFromText(receiptText)
        sendRawBytesToDevice(context, deviceAddress, escPosBytes)
    }

    @SuppressLint("MissingPermission")
    suspend fun sendRawBytesToDevice(
        context: Context,
        deviceAddress: String,
        bytes: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions(context)) {
            return@withContext Result.failure(SecurityException("Bluetooth permission not granted"))
        }

        val adapter = getBluetoothAdapter(context)
            ?: return@withContext Result.failure(IllegalStateException("Bluetooth adapter not available"))

        if (!adapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth is disabled"))
        }

        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null

        try {
            val device = adapter.getRemoteDevice(deviceAddress)
                ?: return@withContext Result.failure(IllegalArgumentException("Device not found: $deviceAddress"))

            // Cancel discovery before connecting for stable socket
            try { adapter.cancelDiscovery() } catch (_: Exception) {}

            socket = try {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: Exception) {
                // Fallback for some thermal printers via reflection
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                method.invoke(device, 1) as BluetoothSocket
            }

            socket.connect()
            outputStream = socket.outputStream
            outputStream.write(bytes)
            outputStream.flush()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
