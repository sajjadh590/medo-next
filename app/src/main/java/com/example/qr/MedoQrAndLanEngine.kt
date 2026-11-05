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
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Pure ZXing QR Scanner, Y-Plane Stride/Rotation Normalizer, Private LAN Validator,
 * Desktop `medo share` HTTP Transfer Client, Wi-Fi Network Binder, and Persian Error Mapper.
 *
 * Strictly zero Google Play Services / ML Kit dependencies.
 */
object MedoQrAndLanEngine {

    const val TRANSFER_VERIFICATION_NOTE =
        "تاییدشده با سرور شبیه‌ساز پروتکل (FakeShareServer پورت 8765 بدون توکن و بدون Range) — تایید نشده در برابر سرور واقعی فیزیکی دسکتاپ (Unverified against the real server)"

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

            // Pass B: Centre crop (72% square/rect)
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
    // 5. Desktop `medo share` LAN HTTP Client (Port 8765, No Token, v2 ZIP)
    // =========================================================================

    /**
     * Sole network socket factory in the entire application.
     * Strictly validates that [urlString] resolves to a private RFC1918 LAN IP or localhost
     * before opening [HttpURLConnection].
     */
    fun openValidatedLanConnection(
        urlString: String,
        connectTimeoutMs: Int = 6000,
        readTimeoutMs: Int = 25000
    ): HttpURLConnection {
        val validated = validateLanTransferUrl(urlString)
        if (validated !is LanValidationResult.Valid) {
            val msg = (validated as LanValidationResult.Invalid).errorFa
            throw SecurityException(msg)
        }
        val conn = (URL(validated.normalizedUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/zip, application/octet-stream, application/json, text/html, */*")
        }
        return conn
    }

    /**
     * Downloads a `.medo` v2 bundle from the desktop `medo share` server (`http://<LAN_IP>:8765`):
     * - Supports direct `.medo` / ZIP URLs (`http://<LAN_IP>:8765/bank.medo` or `GET /` returning ZIP bytes)
     * - Supports `GET /` returning an HTML landing page with `<a href="/bank.medo">` (or any `*.medo` href),
     *   following the `.medo` link on the same validated private LAN host:port.
     * - Does not require a token or `Range` header (matching `medo/share.py`).
     */
    fun downloadBankFromLanServer(
        rawServerUrl: String,
        optionalPin: String? = null,
        onProgressBytes: (Long) -> Unit = {}
    ): Result<ByteArray> {
        val valRes = validateLanTransferUrl(rawServerUrl, optionalPin)
        if (valRes is LanValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException(valRes.errorFa))
        }
        val valid = valRes as LanValidationResult.Valid
        val baseHostPort = "http://${valid.host}:${valid.port}"

        val candidateUrls = linkedSetOf<String>().apply {
            add(valid.normalizedUrl)
            add("$baseHostPort/bank.medo")
            add("$baseHostPort/questions.medo")
            add("$baseHostPort/medo.zip")
            add("$baseHostPort/")
        }

        var lastException: Exception? = null
        var lastHttpStatus: Int? = null

        for (candidateUrl in candidateUrls) {
            try {
                DiagnosticsLogger.logEvent("LAN_GET", "درخواست دریافت بسته از: $candidateUrl")
                val conn = openValidatedLanConnection(candidateUrl)
                val status = conn.responseCode
                lastHttpStatus = status
                if (status in 200..299) {
                    val contentType = conn.contentType?.lowercase().orEmpty()
                    val bytes = readStreamWithProgress(conn, onProgressBytes)
                    conn.disconnect()

                    if (isMedoBundleBytes(bytes)) {
                        DiagnosticsLogger.logEvent("LAN_OK", "بسته .medo دریافت شد (${bytes.size} بایت از $candidateUrl)")
                        return Result.success(bytes)
                    }

                    // If landing page HTML was returned on GET /, extract href="...medo"
                    if (contentType.contains("text/html") || looksLikeHtml(bytes)) {
                        val html = bytes.toString(StandardCharsets.UTF_8)
                        val discoveredHref = extractMedoHrefFromHtml(html)
                        if (discoveredHref != null) {
                            val targetUrl = if (discoveredHref.startsWith("http")) {
                                discoveredHref
                            } else if (discoveredHref.startsWith("/")) {
                                "$baseHostPort$discoveredHref"
                            } else {
                                "$baseHostPort/$discoveredHref"
                            }
                            val dlConn = openValidatedLanConnection(targetUrl)
                            if (dlConn.responseCode in 200..299) {
                                val bundleBytes = readStreamWithProgress(dlConn, onProgressBytes)
                                dlConn.disconnect()
                                if (isMedoBundleBytes(bundleBytes)) {
                                    DiagnosticsLogger.logEvent("LAN_OK", "بسته .medo از لینک صفحه اصلی دریافت شد (${bundleBytes.size} بایت)")
                                    return Result.success(bundleBytes)
                                }
                            } else {
                                dlConn.disconnect()
                            }
                        }
                    }
                } else {
                    conn.disconnect()
                    if (status == 401 || status == 403) {
                        val mapped = mapTransferError(httpStatusCode = status)
                        return Result.failure(IllegalStateException(mapped.fullMessageFa))
                    }
                }
            } catch (e: Exception) {
                lastException = e
                DiagnosticsLogger.logEvent("LAN_TRY_ERR", "خطا در $candidateUrl: ${e.javaClass.simpleName}")
                if (e is ConnectException || e is NoRouteToHostException || e is SocketTimeoutException) {
                    break
                }
            }
        }

        val mapped = mapTransferError(throwable = lastException, httpStatusCode = lastHttpStatus)
        return Result.failure(IllegalStateException(mapped.fullMessageFa, lastException))
    }

    private fun readStreamWithProgress(conn: HttpURLConnection, onProgressBytes: (Long) -> Unit): ByteArray {
        val baos = ByteArrayOutputStream()
        conn.inputStream.use { input ->
            val buf = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                baos.write(buf, 0, r)
                total += r
                onProgressBytes(total)
            }
        }
        return baos.toByteArray()
    }

    private fun isMedoBundleBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        if (b0 == 0x50 && b1 == 0x4B) return true // ZIP ("PK")
        if (b0 == 0x1F && b1 == 0x8B) return true // GZIP
        val head = bytes.take(64).toByteArray().toString(StandardCharsets.UTF_8).trimStart()
        return head.startsWith("{") || head.startsWith("[")
    }

    private fun looksLikeHtml(bytes: ByteArray): Boolean {
        val head = bytes.take(128).toByteArray().toString(StandardCharsets.UTF_8).trimStart().lowercase()
        return head.startsWith("<!doctype html") || head.startsWith("<html") || head.contains("<a href=")
    }

    private fun extractMedoHrefFromHtml(html: String): String? {
        val regex = Regex("""href\s*=\s*["']([^"']+\.(?:medo|zip|json))["']""", RegexOption.IGNORE_CASE)
        return regex.find(html)?.groupValues?.getOrNull(1)
    }

    // =========================================================================
    // 6. Wi-Fi Network Binding (prevents cellular default route from breaking LAN)
    // =========================================================================

    fun bindProcessToWifiIfAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            @Suppress("DEPRECATION")
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

    fun unbindProcessFromNetwork(context: Context) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.bindProcessToNetwork(null)
        } catch (_: Exception) {
        }
    }

    // =========================================================================
    // 7. Specific Persian Error Mapper with Actionable Next Steps
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
                nextStepFa = "آدرس IP و پورت را بررسی کرده یا از دکمه «انتخاب فایل بانک (.medo) از حافظه گوشی» استفاده کنید."
            )
        }
    }
}
