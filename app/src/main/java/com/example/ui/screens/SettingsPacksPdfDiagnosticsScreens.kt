package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pdf.PdfExportConfig
import com.example.pdf.PdfExportMode
import com.example.pdf.PdfTextSize
import com.example.qr.MedoQrAndLanEngine
import com.example.ui.MedoUiState
import com.example.ui.components.ClinicalOptionRow
import com.example.ui.components.GlassCard
import com.example.ui.components.MedoPill
import com.example.ui.components.MedoPrimaryButton
import com.example.ui.components.MedoSecondaryButton
import com.example.ui.components.OptionVisualState
import com.example.ui.theme.GlassEffectMode
import com.example.ui.theme.LocalMedoSettings
import com.example.ui.theme.LocalMedoTheme
import com.example.ui.theme.MedoFontChoice
import com.example.ui.theme.MedoSettingsState
import com.example.ui.theme.MedoThemeId
import com.example.ui.theme.TextSizePreset
import com.example.util.DiagnosticsLogger
import com.example.util.PersianTextUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsModalScreen(
    state: MedoUiState,
    onUpdateSettings: ((MedoSettingsState) -> MedoSettingsState) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onExportProgressJson: () -> Unit,
    onClose: () -> Unit
) {
    val tokens = LocalMedoTheme.current
    val settings = LocalMedoSettings.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.gradientStart)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FlowRow(
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("close_settings_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                            tint = tokens.textPrimary
                        )
                    }
                    Text(
                        text = "تنظیمات ظاهر، فونت و سیستم شیشه‌ای",
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                MedoPill(
                    text = "گزارش و عیب‌یابی",
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.testTag("open_diagnostics_from_settings_btn")
                )
            }
        }

        // 1. Live Theme Preview Card
        item {
            GlassCard(
                isReadingSurface = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_live_preview_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "پیش‌نمایش زنده پوسته (${tokens.themeId.nameFa})",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.accent,
                            fontWeight = FontWeight.Bold
                        )
                        MedoPill(text = "کنتراست استاندارد WCAG >= 4.5:1")
                    }

                    Text(
                        text = PersianTextUtils.formatDigits(
                            "[نمونه آزمایشی] پیش‌نمایش خوانایی متن، فونت انتخابی (${settings.fontChoice.labelFa}) و وضعیت گزینه‌ها:",
                            settings.usePersianDigits
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.textPrimary
                    )

                    ClinicalOptionRow(
                        index = 0,
                        text = "گزینه در حالت خنثی (Neutral)",
                        state = OptionVisualState.NEUTRAL,
                        onClick = {}
                    )
                    ClinicalOptionRow(
                        index = 1,
                        text = "گزینه انتخاب‌شده توسط کاربر (Selected)",
                        state = OptionVisualState.SELECTED,
                        onClick = {}
                    )
                    ClinicalOptionRow(
                        index = 2,
                        text = "گزینه صحیح با علامت تیک (Correct ✓)",
                        state = OptionVisualState.CORRECT,
                        votePercent = 72,
                        showVotes = true,
                        onClick = {}
                    )
                    ClinicalOptionRow(
                        index = 3,
                        text = "گزینه نادرست با علامت ضربدر (Wrong ✗)",
                        state = OptionVisualState.WRONG,
                        votePercent = 18,
                        showVotes = true,
                        onClick = {}
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MedoPrimaryButton(
                            text = "دکمه اصلی",
                            onClick = {},
                            modifier = Modifier.weight(1f)
                        )
                        MedoSecondaryButton(
                            text = "دکمه فرعی",
                            onClick = {},
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Font Family Selector (Persian: Vazirmatn default + Markazi/B Nazanin, Amiri + English: Inter, Roboto, JetBrains Mono)
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "انتخاب فونت فارسی و انگلیسی (پیش‌فرض: وزیرمتن، کاملاً آفلاین):",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MedoFontChoice.entries.forEach { fontChoice ->
                            val isSelected = settings.fontChoice == fontChoice
                            MedoPill(
                                text = fontChoice.labelFa,
                                containerColor = if (isSelected) tokens.accent else tokens.surfaceTint,
                                contentColor = if (isSelected) tokens.accentOn else tokens.textPrimary,
                                onClick = {
                                    onUpdateSettings { it.copy(fontChoice = fontChoice) }
                                },
                                modifier = Modifier.testTag("font_chip_${fontChoice.id}")
                            )
                        }
                    }
                }
            }
        }

        // 3. Theme Selector (11 Clinical Themes)
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "انتخاب پوسته رنگی (۱۱ پوسته استاندارد):",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MedoThemeId.entries.forEach { themeId ->
                            val isSelected = !settings.followSystemTheme && settings.selectedThemeId == themeId
                            MedoPill(
                                text = themeId.nameFa,
                                containerColor = if (isSelected) tokens.accent else tokens.surfaceTint,
                                contentColor = if (isSelected) tokens.accentOn else tokens.textPrimary,
                                onClick = {
                                    onUpdateSettings {
                                        it.copy(
                                            selectedThemeId = themeId,
                                            followSystemTheme = false
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("theme_chip_${themeId.id}")
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "هماهنگی خودکار با حالت روز/شب سیستم:",
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = settings.followSystemTheme,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(followSystemTheme = checked) }
                            }
                        )
                    }
                }
            }
        }

        // 4. Glass Effect, Text Size, Persian Digits, Daily Auto-Backup via MediaStore/SAF + Last Backup Time
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "جلوه شیشه‌ای (Glass Blur):",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassEffectMode.entries.forEach { mode ->
                            MedoPill(
                                text = mode.labelFa,
                                containerColor = if (settings.glassEffectMode == mode) tokens.accent else tokens.surfaceTint,
                                contentColor = if (settings.glassEffectMode == mode) tokens.accentOn else tokens.textPrimary,
                                onClick = { onUpdateSettings { it.copy(glassEffectMode = mode) } }
                            )
                        }
                    }

                    Text(
                        text = "اندازه نوشته‌ها (پشتیبانی تا مقیاس ۲ برابر بدون بریدگی):",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextSizePreset.entries.forEach { preset ->
                            MedoPill(
                                text = preset.labelFa,
                                containerColor = if (settings.textSizePreset == preset) tokens.accent else tokens.surfaceTint,
                                contentColor = if (settings.textSizePreset == preset) tokens.accentOn else tokens.textPrimary,
                                onClick = { onUpdateSettings { it.copy(textSizePreset = preset) } },
                                modifier = Modifier.testTag("text_size_chip_${preset.name}")
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "نمایش ارقام به صورت فارسی (۰-۹):",
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = settings.usePersianDigits,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(usePersianDigits = checked) }
                            },
                            modifier = Modifier.testTag("switch_persian_digits")
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "پشتیبان‌گیری خودکار روزانه در پوشه Documents (MediaStore):",
                                style = MaterialTheme.typography.bodyLarge,
                                color = tokens.textPrimary
                            )
                            Text(
                                text = "آخرین پشتیبان‌گیری: ${state.lastAutoBackupStatusFa}",
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.accent,
                                modifier = Modifier.testTag("last_auto_backup_status_text")
                            )
                        }
                        Switch(
                            checked = settings.dailyAutoBackupToDocuments,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.copy(dailyAutoBackupToDocuments = checked) }
                            },
                            modifier = Modifier.testTag("switch_daily_auto_backup")
                        )
                    }
                }
            }
        }

        item {
            MedoSecondaryButton(
                text = "خروجی پشتیبان پیشرفت و یادداشت‌ها (JSON)",
                onClick = onExportProgressJson,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PackManagerModalScreen(
    state: MedoUiState,
    onOpenQrScanner: () -> Unit,
    onDownloadFromLanServer: (String) -> Unit,
    onImportLocalBankUri: (Uri) -> Unit,
    onTriggerDeltaUpdate: (mirrorMode: Boolean) -> Unit,
    onDeletePack: (String) -> Unit,
    onClose: () -> Unit
) {
    val tokens = LocalMedoTheme.current
    val settings = LocalMedoSettings.current
    var lanUrl by remember(state.connectedLanServerUrl) { mutableStateOf(state.connectedLanServerUrl) }
    var mirrorMode by remember { mutableStateOf(false) }
    var selectText by remember { mutableStateOf(true) }
    var selectLessons by remember { mutableStateOf(true) }
    var selectMedia by remember { mutableStateOf(true) }
    var lanValidationError by remember { mutableStateOf<String?>(null) }

    val openMedoFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportLocalBankUri(uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.gradientStart)
            .testTag("pack_manager_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FlowRow(
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("close_pack_manager_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                            tint = tokens.textPrimary
                        )
                    }
                    Text(
                        text = "مدیریت بسته‌ها و انتقال آفلاین (.medo v2 / v3)",
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                MedoPill(
                    text = "اسکن کد QR",
                    onClick = onOpenQrScanner,
                    modifier = Modifier.testTag("open_qr_scanner_from_packs_btn")
                )
            }
        }

        // Direct Local File Import Card (supports real .medo v2 ZIP, GZIP, and JSON banks)
        item {
            GlassCard(
                isReadingSurface = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "واردسازی مستقیم فایل بانک سوالات (.medo) از حافظه گوشی:",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.accent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "پشتیبانی کامل از فایل‌های واقعی خروجی دسکتاپ (.medo v2 ZIP، bank.json، questions.json و ساختارهای فشرده) با حفظ پیشرفت قبلی شما.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                    MedoPrimaryButton(
                        text = "انتخاب فایل بانک سوالات (.medo / .zip / .json) از حافظه",
                        onClick = {
                            openMedoFileLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_local_medo_file_btn")
                    )
                }
            }
        }

        // LAN Transfer Client Card (`medo share` port 8765 + v3 Delta Sync)
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "دریافت از سرور دسکتاپ (medo share پورت 8765) و به‌روزرسانی دلتا:",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onOpenQrScanner,
                            modifier = Modifier.testTag("pack_qr_icon_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "اسکن کد QR رایانه",
                                tint = tokens.accent
                            )
                        }
                    }

                    OutlinedTextField(
                        value = lanUrl,
                        onValueChange = {
                            lanUrl = it
                            lanValidationError = null
                        },
                        label = { Text("آدرس سرور دسکتاپ در شبکه Wi-Fi محلی (مثلاً http://192.168.1.100:8765)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lan_url_input")
                    )

                    if (lanValidationError != null) {
                        Text(
                            text = lanValidationError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.wrong
                        )
                    }

                    Text(
                        text = "انتخاب سطوح محتوا برای دانلود (پیش‌فرض: همه):",
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.textPrimary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectText, onCheckedChange = { selectText = it })
                            Text("متن و کلید (۴ MB)", style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectLessons, onCheckedChange = { selectLessons = it })
                            Text("درسنامه (۸ MB)", style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectMedia, onCheckedChange = { selectMedia = it })
                            Text("مایندمپ (۱۴ MB)", style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "حالت آینه‌ای (Mirror Mode - حذف سوالات حذف‌شده در مبدا):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = mirrorMode,
                            onCheckedChange = { mirrorMode = it }
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MedoPrimaryButton(
                            text = "دریافت بانک از سرور دسکتاپ (پورت 8765)",
                            onClick = {
                                when (val res = MedoQrAndLanEngine.validateLanTransferUrl(lanUrl)) {
                                    is MedoQrAndLanEngine.LanValidationResult.Valid -> {
                                        lanValidationError = null
                                        onDownloadFromLanServer(res.normalizedUrl)
                                    }
                                    is MedoQrAndLanEngine.LanValidationResult.Invalid -> {
                                        lanValidationError = res.errorFa
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_lan_bank_btn")
                        )
                        MedoSecondaryButton(
                            text = "به‌روزرسانی دلتا v3",
                            onClick = {
                                when (val res = MedoQrAndLanEngine.validateLanTransferUrl(lanUrl)) {
                                    is MedoQrAndLanEngine.LanValidationResult.Valid -> {
                                        lanValidationError = null
                                        onTriggerDeltaUpdate(mirrorMode)
                                    }
                                    is MedoQrAndLanEngine.LanValidationResult.Invalid -> {
                                        lanValidationError = res.errorFa
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("update_all_packs_btn")
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "بسته‌های نصب‌شده روی دستگاه:",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        items(state.installedPacks, key = { it.packId }) { pack ->
            val mbSize = (pack.sizeBytes / (1024L * 1024L)).coerceAtLeast(1L)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = pack.categoryTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            if (pack.updateAvailable) {
                                MedoPill(
                                    text = "به‌روزرسانی موجود",
                                    containerColor = tokens.warning.copy(alpha = 0.2f),
                                    contentColor = tokens.warning
                                )
                            }
                        }
                        Text(
                            text = "نسخه: ${pack.version} • سطح: ${pack.level} • حدود ${PersianTextUtils.formatNumber(mbSize, settings.usePersianDigits)} مگابایت (${PersianTextUtils.formatNumber(pack.questionCount, settings.usePersianDigits)} سوال)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )
                    }
                    IconButton(
                        onClick = { onDeletePack(pack.packId) },
                        modifier = Modifier.testTag("delete_pack_${pack.packId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف بسته ${pack.categoryTitle}",
                            tint = tokens.wrong
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfExportModalScreen(
    state: MedoUiState,
    onExportPdf: (PdfExportConfig) -> Unit,
    onClose: () -> Unit
) {
    val tokens = LocalMedoTheme.current

    var selectedMode by remember { mutableStateOf(PdfExportMode.EXAM) }
    var inkSaving by remember { mutableStateOf(false) }
    var textSize by remember { mutableStateOf(PdfTextSize.MEDIUM) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.gradientStart)
            .padding(16.dp)
            .testTag("pdf_export_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("close_pdf_export_btn")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "بازگشت",
                    tint = tokens.textPrimary
                )
            }
            Text(
                text = "خروجی استاندارد PDF چاپی (A4)",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "حالت خروجی PDF:",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfExportMode.entries.forEach { mode ->
                        MedoPill(
                            text = mode.labelFa,
                            containerColor = if (selectedMode == mode) tokens.accent else tokens.surfaceTint,
                            contentColor = if (selectedMode == mode) tokens.accentOn else tokens.textPrimary,
                            onClick = { selectedMode = mode },
                            modifier = Modifier.testTag("pdf_mode_${mode.name}")
                        )
                    }
                }

                Text(
                    text = "اندازه قلم چاپی:",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfTextSize.entries.forEach { sz ->
                        MedoPill(
                            text = sz.labelFa,
                            containerColor = if (textSize == sz) tokens.accent else tokens.surfaceTint,
                            contentColor = if (textSize == sz) tokens.accentOn else tokens.textPrimary,
                            onClick = { textSize = sz }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "حالت صرفه‌جویی در مصرف جوهر (Ink-Saving):",
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = inkSaving, onCheckedChange = { inkSaving = it })
                }

                Text(
                    text = "• تضمین عدم نشت پاسخ در صفحات سوالات حالت آزمون (دایره‌های یکسان، بدون تیک و بدون رنگ)\n• جدول کلید ۵ جفت در هر سطر در صفحه مجزا پس از سوالات فصل\n• پاسخنامه تشریحی فصل در صفحات مجزا پس از جدول کلید با فشرده‌سازی خودکار سوالات فاقد تشریح («سوال‌های ۵ تا ۶: پاسخ تشریحی ثبت نشده»)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary
                )

                MedoPrimaryButton(
                    text = "ساخت و اشتراک‌گذاری فایل PDF",
                    onClick = {
                        onExportPdf(
                            PdfExportConfig(
                                mode = selectedMode,
                                inkSaving = inkSaving,
                                textSize = textSize
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_pdf_btn")
                )
            }
        }
    }
}

@Composable
fun DiagnosticsModalScreen(
    state: MedoUiState,
    onClose: () -> Unit
) {
    val tokens = LocalMedoTheme.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copiedNotice by remember { mutableStateOf(false) }

    val scanStats = MedoQrAndLanEngine.lastScanStats
    val reportText = remember(
        state.allQuestions.size,
        state.installedPacks.size,
        state.progressByQuestionId.size,
        state.lastAutoBackupStatusFa,
        scanStats
    ) {
        val baseReport = DiagnosticsLogger.buildDiagnosticsReport(
            context = context,
            appVersion = "201.0 (Medo Next)",
            totalQuestions = state.allQuestions.size,
            totalCategories = state.categoryNodes.size,
            installedPacksSummary = state.installedPacks.joinToString(", ") { "${it.packId}(${it.version})" },
            answeredCount = state.progressByQuestionId.values.count { it.lastSelectedOption != null }
        )
        buildString {
            append(baseReport)
            appendLine("--- وضعیت پروتکل انتقال و پشتیبان‌گیری ---")
            appendLine("وضعیت کلاینت انتقال: ${MedoQrAndLanEngine.TRANSFER_VERIFICATION_NOTE}")
            appendLine("آخرین پشتیبان خودکار روزانه: ${state.lastAutoBackupStatusFa}")
            appendLine("--- وضعیت آخرین نشست اسکن QR (ZXing) ---")
            appendLine("فریم‌های بررسی‌شده: ${scanStats.framesAnalysed} | تلاش‌های رمزگشایی: ${scanStats.decodeAttempts}")
            appendLine("چرخش‌های آزمایش‌شده: ${scanStats.rotationsTried} | آخرین وضعیت: ${scanStats.lastFailureReason}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.gradientStart)
            .padding(16.dp)
            .testTag("diagnostics_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("close_diagnostics_btn")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "بازگشت",
                        tint = tokens.textPrimary
                    )
                }
                Text(
                    text = "گزارش سیستم و عیب‌یابی",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            MedoPrimaryButton(
                text = if (copiedNotice) "✓ کپی شد" else "کپی گزارش",
                onClick = {
                    clipboard.setText(AnnotatedString(reportText))
                    copiedNotice = true
                },
                modifier = Modifier.testTag("copy_diagnostics_btn")
            )
        }

        GlassCard(
            isReadingSurface = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = reportText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary
                    )
                }
            }
        }
    }
}
