package com.example.qr

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.util.DiagnosticsLogger
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.InvertedLuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.ByteBuffer

/**
 * Pure ZXing QR Scanner, Y-Plane Stride/Rotation Normalizer, Private LAN Validator,
 * Wi-Fi Network Binder, and Persian Clinical Error Mapper (Section A).
 *
 * Strictly zero Google Play Services / ML Kit dependencies.
 */
object MedoQrAndLanEngine {

    private val zxingHints: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to java.lang.Boolean.TRUE,
        DecodeHintType.ALSO_INVERTED to java.lang.Boolean.TRUE,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    data class ScanSessionStats(
        val framesAnalysed: Int = 0,
        val decodeAttempts: Int = 0,
        val rotationsTried: List<Int> = emptyList(),
        val lastFailureReason: String = "NONE",
        val lastDecodedUrl: String? = null
    )

    @Volatile
    var lastScanStats: ScanSessionStats = ScanSessionStats()
        private set

    fun resetScanSessionStats() {
        lastScanStats = ScanSessionStats()
        DiagnosticsLogger.logEvent("QR_SCAN", "شروع نشست اسکن کد QR با موتور ZXing")
    }

    /**
     * Extracts a tightly packed `width x height` Y-luminance array from a CameraX Y-plane ByteBuffer,
     * honoring arbitrary `rowStride` padding and `pixelStride`.
     */
    fun extractContiguousYPlane(
        yBuffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): ByteArray {
        val out = ByteArray(width * height)
        val dup = yBuffer.duplicate()
        dup.rewind()
        val limit = dup.remaining()

        for (y in 0 until height) {
            val rowStart = y * rowStride
            val outRowOffset = y * width
            for (x in 0 until width) {
                val srcIdx = rowStart + (x * pixelStride)
                out[outRowOffset + x] = if (srcIdx in 0 until limit) dup.get(srcIdx) else 0
            }
        }
        return out
    }

    /**
     * Rotates a contiguous `width x height` Y-luminance array clockwise by 0, 90, 180, or 270 degrees.
     * Returns `(rotatedBytes, newWidth, newHeight)`.
     */
    fun rotateYPlane(
        yBytes: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int
    ): Triple<ByteArray, Int, Int> {
        val normRot = ((rotationDegrees % 360) + 360) % 360
        if (normRot == 0) return Triple(yBytes, width, height)

        return when (normRot) {
            90 -> {
                val out = ByteArray(width * height)
                val newW = height
                val newH = width
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val newX = height - 1 - y
                        val newY = x
                        out[newY * newW + newX] = yBytes[y * width + x]
                    }
                }
                Triple(out, newW, newH)
            }
            180 -> {
                val out = ByteArray(width * height)
                for (i in yBytes.indices) {
                    out[yBytes.lastIndex - i] = yBytes[i]
                }
                Triple(out, width, height)
            }
            270 -> {
                val out = ByteArray(width * height)
                val newW = height
                val newH = width
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val newX = y
                        val newY = width - 1 - x
                        out[newY * newW + newX] = yBytes[y * width + x]
                    }
                }
                Triple(out, newW, newH)
            }
            else -> Triple(yBytes, width, height)
        }
    }

    /**
     * Downscales a `width x height` Y-luminance array by factor 2 (averaging 2x2 blocks)
     * to recover from motion blur or high-frequency camera noise.
     */
    fun downscale2x(yBytes: ByteArray, width: Int, height: Int): Triple<ByteArray, Int, Int> {
        val newW = (width / 2).coerceAtLeast(1)
        val newH = (height / 2).coerceAtLeast(1)
        val out = ByteArray(newW * newH)
        for (y in 0 until newH) {
            for (x in 0 until newW) {
                val sx = x * 2
                val sy = y * 2
                val p00 = yBytes[sy * width + sx].toInt() and 0xFF
                val p10 = yBytes[sy * width + (sx + 1).coerceAtMost(width - 1)].toInt() and 0xFF
                val p01 = yBytes[(sy + 1).coerceAtMost(height - 1) * width + sx].toInt() and 0xFF
                val p11 = yBytes[(sy + 1).coerceAtMost(height - 1) * width + (sx + 1).coerceAtMost(width - 1)].toInt() and 0xFF
                out[y * newW + x] = ((p00 + p10 + p01 + p11) / 4).toByte()
            }
        }
        return Triple(out, newW, newH)
    }

    /**
     * Multi-pass ZXing decoder on a Y-plane frame:
     * 1. Honors `rowStride` and `pixelStride`
     * 2. Tries primary `rotationDegrees`, then fallback rotations `(rot + 90, 180, 270)`
     * 3. Tries full frame, 70% centre crop, and 2x downscaled copy (plus inverted source fallback)
     * 4. Records diagnostic metrics into `lastScanStats` and `DiagnosticsLogger`.
     */
    fun decodeYuvFrame(
        yBytesWithStride: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int = width,
        pixelStride: Int = 1,
        rotationDegrees: Int = 0
    ): String? {
        val contiguous = if (rowStride == width && pixelStride == 1 && yBytesWithStride.size == width * height) {
            yBytesWithStride
        } else {
            extractContiguousYPlane(
                ByteBuffer.wrap(yBytesWithStride),
                width,
                height,
                rowStride,
                pixelStride
            )
        }

        val candidateRotations = listOf(
            rotationDegrees,
            (rotationDegrees + 90) % 360,
            (rotationDegrees + 180) % 360,
            (rotationDegrees + 270) % 360
        ).distinct()

        val reader = MultiFormatReader().apply { setHints(zxingHints) }
        var attempts = 0
        val triedRotations = mutableListOf<Int>()

        for (rot in candidateRotations) {
            triedRotations.add(rot)
            val (rotBytes, rw, rh) = rotateYPlane(contiguous, width, height, rot)

            // Pass A: Full frame
            attempts++
            tryDecodePlanar(reader, rotBytes, rw, rh, 0, 0, rw, rh)?.let { text ->
                recordScanSuccess(attempts, triedRotations, text)
                return text
            }

            // Pass B: Centre crop (70% square/rect)
            val cropW = (rw * 0.72f).toInt().coerceAtLeast(32)
            val cropH = (rh * 0.72f).toInt().coerceAtLeast(32)
            val left = ((rw - cropW) / 2).coerceAtLeast(0)
            val top = ((rh - cropH) / 2).coerceAtLeast(0)
            if (cropW <= rw && cropH <= rh) {
                attempts++
                tryDecodePlanar(reader, rotBytes, rw, rh, left, top, cropW, cropH)?.let { text ->
                    recordScanSuccess(attempts, triedRotations, text)
                    return text
                }
            }

            // Pass C: Downscaled 2x (recovers from blur / sensor noise)
            if (rw >= 120 && rh >= 120) {
                val (downBytes, dw, dh) = downscale2x(rotBytes, rw, rh)
                attempts++
                tryDecodePlanar(reader, downBytes, dw, dh, 0, 0, dw, dh)?.let { text ->
                    recordScanSuccess(attempts, triedRotations, text)
                    return text
                }
            }
        }

        lastScanStats = lastScanStats.copy(
            framesAnalysed = lastScanStats.framesAnalysed + 1,
            decodeAttempts = lastScanStats.decodeAttempts + attempts,
            rotationsTried = triedRotations,
            lastFailureReason = "NOT_FOUND_AFTER_${attempts}_PASSES"
        )
        return null
    }

    private fun tryDecodePlanar(
        reader: MultiFormatReader,
        data: ByteArray,
        dataWidth: Int,
        dataHeight: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int
    ): String? {
        val source = PlanarYUVLuminanceSource(
            data,
            dataWidth,
            dataHeight,
            left,
            top,
            width,
            height,
            false
        )
        try {
            return reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: Exception) {
            reader.reset()
        }
        // Fallback explicit inverted check
        try {
            return reader.decodeWithState(BinaryBitmap(HybridBinarizer(InvertedLuminanceSource(source)))).text
        } catch (_: Exception) {
            reader.reset()
        }
        return null
    }

    /**
     * Decodes a QR code from a gallery Bitmap (`انتخاب عکس QR از گالری`).
     */
    fun decodeFromBitmap(bitmap: Bitmap): String? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        // Convert ARGB pixels to Y-plane luminance and use our multi-rotation/crop/downscale pipeline
        val yBytes = ByteArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            yBytes[i] = ((r * 66 + g * 129 + b * 25 + 128) shr 8).coerceIn(0, 255).toByte()
        }
        val decoded = decodeYuvFrame(yBytes, w, h, w, 1, 0)
        if (decoded != null) return decoded

        // Direct RGBLuminanceSource fallback
        val reader = MultiFormatReader().apply { setHints(zxingHints) }
        return try {
            val rgbSource = RGBLuminanceSource(w, h, pixels)
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(rgbSource))).text
        } catch (_: Exception) {
            null
        }
    }

    private fun recordScanSuccess(attempts: Int, rotations: List<Int>, decoded: String) {
        lastScanStats = lastScanStats.copy(
            framesAnalysed = lastScanStats.framesAnalysed + 1,
            decodeAttempts = lastScanStats.decodeAttempts + attempts,
            rotationsTried = rotations,
            lastFailureReason = "SUCCESS",
            lastDecodedUrl = decoded
        )
        DiagnosticsLogger.logEvent(
            "QR_DECODE",
            "کد QR در تلاش $attempts (چرخش‌ها=$rotations) رمزگشایی شد"
        )
    }

    // =========================================================================
    // 4. Private LAN URL Validator (10/8, 172.16/12, 192.168/16, localhost)
    // =========================================================================

    sealed class LanValidationResult {
        data class Valid(
            val normalizedUrl: String,
            val host: String,
            val port: Int,
            val tokenOrPin: String?
        ) : LanValidationResult()

        data class Invalid(val errorFa: String) : LanValidationResult()
    }

    fun validateLanTransferUrl(rawInput: String, optionalPin: String? = null): LanValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return LanValidationResult.Invalid("آدرس خالی است. لطفاً آدرس نمایش‌داده‌شده در برنامه دسکتاپ (مثلاً 192.168.1.100:8765) را وارد کنید.")
        }

        val withScheme = if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
            "http://$trimmed"
        } else {
            trimmed
        }

        val uri = try {
            URI(withScheme)
        } catch (_: Exception) {
            return LanValidationResult.Invalid("ساختار آدرس نامعتبر است. نمونه صحیح: http://192.168.1.100:8765")
        }

        val scheme = uri.scheme?.lowercase() ?: ""
        if (scheme != "http" && scheme != "https") {
            return LanValidationResult.Invalid("فقط پروتکل شبکه محلی (HTTP) مجاز است.")
        }

        val host = uri.host?.lowercase()
            ?: return LanValidationResult.Invalid("نام میزبان (IP) در آدرس یافت نشد.")

        if (!isPrivateLanOrLocalhost(host)) {
            DiagnosticsLogger.logEvent("LAN_REJECT", "رد آدرس غیرمحلی: $host")
            return LanValidationResult.Invalid(
                "خطای امنیتی: فقط آدرس‌های شبکه خصوصی محلی (192.168.x.x، 10.x.x.x، 172.16-31.x.x یا localhost) مجاز هستند. اتصال به اینترنت عمومی مسدود است."
            )
        }

        val port = if (uri.port > 0) uri.port else 8765
        val queryToken = extractQueryToken(uri.rawQuery) ?: optionalPin?.takeIf { it.isNotBlank() }
        val baseNormalized = "http://$host:$port" + (uri.rawPath?.takeIf { it.isNotEmpty() } ?: "")
        val finalUrl = if (!queryToken.isNullOrBlank() && uri.rawQuery.isNullOrBlank()) {
            "$baseNormalized?token=$queryToken"
        } else if (!uri.rawQuery.isNullOrBlank()) {
            "$baseNormalized?${uri.rawQuery}"
        } else {
            baseNormalized
        }

        return LanValidationResult.Valid(
            normalizedUrl = finalUrl,
            host = host,
            port = port,
            tokenOrPin = queryToken
        )
    }

    fun isPrivateLanOrLocalhost(host: String): Boolean {
        val h = host.trim().lowercase()
        if (h == "localhost" || h == "127.0.0.1" || h == "::1" || h.endsWith(".local")) {
            return true
        }
        val parts = h.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false

        val o0 = octets[0]
        val o1 = octets[1]
        return when {
            o0 == 10 -> true // 10.0.0.0/8
            o0 == 127 -> true // 127.0.0.0/8 loopback
            o0 == 192 && o1 == 168 -> true // 192.168.0.0/16
            o0 == 172 && o1 in 16..31 -> true // 172.16.0.0/12
            else -> false
        }
    }

    private fun extractQueryToken(rawQuery: String?): String? {
        if (rawQuery.isNullOrBlank()) return null
        for (pair in rawQuery.split("&")) {
            val kv = pair.split("=", limit = 2)
            if (kv.size == 2 && (kv[0] == "token" || kv[0] == "pin")) {
                return kv[1]
            }
        }
        return null
    }

    // =========================================================================
    // 5. Wi-Fi Network Binding (prevents cellular default route from breaking LAN)
    // =========================================================================

    fun bindProcessToWifiIfAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val allNetworks = cm.allNetworks
            val wifiNetwork = allNetworks.firstOrNull { net ->
                val caps = cm.getNetworkCapabilities(net)
                caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
            if (wifiNetwork != null) {
                val bound = cm.bindProcessToNetwork(wifiNetwork)
                DiagnosticsLogger.logEvent("WIFI_BIND", "اتصال پروسه به شبکه Wi-Fi محلی: $bound")
                bound
            } else {
                DiagnosticsLogger.logEvent("WIFI_BIND", "شبکه Wi-Fi فعالی یافت نشد")
                false
            }
        } catch (e: Exception) {
            DiagnosticsLogger.logEvent("WIFI_BIND_ERR", "خطا در اتصال به Wi-Fi: ${e.javaClass.simpleName}")
            false
        }
    }

    // =========================================================================
    // 6. Specific Persian Error Mapper with Actionable Next Steps
    // =========================================================================

    enum class TransferErrorKind {
        NO_ROUTE_DIFFERENT_WIFI,
        TIMEOUT,
        CONNECTION_REFUSED_FIREWALL,
        WRONG_PIN_403,
        CORRUPT_HASH_MISMATCH,
        INSUFFICIENT_STORAGE,
        WIFI_NOT_CONNECTED,
        UNKNOWN
    }

    data class MappedTransferError(
        val kind: TransferErrorKind,
        val titleFa: String,
        val nextStepFa: String
    ) {
        val fullMessageFa: String get() = "$titleFa — گام بعدی: $nextStepFa"
    }

    fun mapTransferError(
        throwable: Throwable? = null,
        httpStatusCode: Int? = null,
        hashMismatch: Boolean = false,
        insufficientSpaceBytes: Long? = null,
        wifiDisconnected: Boolean = false
    ): MappedTransferError {
        if (wifiDisconnected) {
            return MappedTransferError(
                kind = TransferErrorKind.WIFI_NOT_CONNECTED,
                titleFa = "Wi-Fi روشن نیست یا گوشی به شبکه محلی متصل نیست",
                nextStepFa = "Wi-Fi گوشی را روشن کنید و به همان مودم یا هات‌اسپات رایانه متصل شوید."
            )
        }
        if (insufficientSpaceBytes != null && insufficientSpaceBytes > 0) {
            val mb = (insufficientSpaceBytes / (1024L * 1024L)).coerceAtLeast(1L)
            return MappedTransferError(
                kind = TransferErrorKind.INSUFFICIENT_STORAGE,
                titleFa = "فضای ذخیره‌سازی داخلی کافی نیست (نیاز به حداقل $mb مگابایت فضای خالی)",
                nextStepFa = "از بخش مدیریت بسته‌ها یک بسته قدیمی را حذف کنید یا حافظه گوشی را آزاد نمایید."
            )
        }
        if (hashMismatch) {
            return MappedTransferError(
                kind = TransferErrorKind.CORRUPT_HASH_MISMATCH,
                titleFa = "فایل دریافتی ناقص است یا هش SHA-256 آن همخوانی ندارد",
                nextStepFa = "دکمه «ادامه دانلود» را بزنید تا فقط قطعه آسیب‌دیده مجدداً دریافت شود؛ بانک قبلی شما کاملاً سالم است."
            )
        }
        if (httpStatusCode == 401 || httpStatusCode == 403) {
            return MappedTransferError(
                kind = TransferErrorKind.WRONG_PIN_403,
                titleFa = "خطای ۴۰۳: کد PIN یا توکن امنیتی نادرست است",
                nextStepFa = "کد QR جدید را اسکن کنید یا پین نمایش‌داده‌شده در رایانه را دقیق وارد نمایید."
            )
        }

        return when (throwable) {
            is NoRouteToHostException -> MappedTransferError(
                kind = TransferErrorKind.NO_ROUTE_DIFFERENT_WIFI,
                titleFa = "مسیر شبکه یافت نشد (گوشی و رایانه روی یک Wi-Fi نیستند یا AP Isolation فعال است)",
                nextStepFa = "اطمینان حاصل کنید گوشی و رایانه به یک Wi-Fi واحد متصل‌اند یا هات‌اسپات مستقیم گوشی را روشن کنید."
            )
            is SocketTimeoutException -> MappedTransferError(
                kind = TransferErrorKind.TIMEOUT,
                titleFa = "مهلت زمانی اتصال به پایان رسید (Timeout)",
                nextStepFa = "بررسی کنید سرور دسکتاپ در حال اجرا باشد و فایروال ویندوز پورت را مسدود نکرده باشد."
            )
            is ConnectException -> MappedTransferError(
                kind = TransferErrorKind.CONNECTION_REFUSED_FIREWALL,
                titleFa = "اتصال رد شد (سرور دسکتاپ متوقف است یا فایروال ویندوز مانع اتصال شده است)",
                nextStepFa = "در رایانه دستور سرور را اجرا کنید و در Windows Defender Firewall دسترسی Python را روی Private Networks مجاز (Allow) نمایید."
            )
            else -> MappedTransferError(
                kind = TransferErrorKind.UNKNOWN,
                titleFa = "خطا در ارتباط شبکه محلی (${throwable?.javaClass?.simpleName ?: "نامشخص"})",
                nextStepFa = "آدرس IP و پورت را بررسی کرده یا از گزینه «انتخاب فایل از حافظه» استفاده کنید."
            )
        }
    }
}
