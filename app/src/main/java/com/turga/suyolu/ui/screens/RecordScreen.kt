package com.turga.suyolu.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.turga.suyolu.algorithm.DeltaCompressor
import com.turga.suyolu.algorithm.RouteMerger
import com.turga.suyolu.data.PointType
import com.turga.suyolu.data.RouteData
import com.turga.suyolu.data.RouteStorage
import com.turga.suyolu.data.SavedRouteRecord
import com.turga.suyolu.service.GpsTrackingService
import com.turga.suyolu.ui.components.OsmMapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordScreen(
    onRouteSaved: (RouteData) -> Unit
) {
    val context = LocalContext.current

    var fountainNameInput by remember { mutableStateOf("Dedeler Pınarı") }
    var showLocationDisclosureDialog by remember { mutableStateOf(false) }
    var showStartLocationDialog by remember { mutableStateOf(false) }
    var pendingResumeRoute by remember { mutableStateOf<RouteData?>(null) }

    // İmece & Kayıtlı Hatlar
    var savedRoutes by remember { mutableStateOf<List<SavedRouteRecord>>(emptyList()) }
    val selectedForMergeIds = remember { mutableStateListOf<String>() }
    var showImportTextDialog by remember { mutableStateOf(false) }
    var importTextValue by remember { mutableStateOf("") }
    var showMergeNameDialog by remember { mutableStateOf(false) }
    var mergedRouteNameInput by remember { mutableStateOf("Birleşik Çeşme Hattı") }

    val isTracking by GpsTrackingService.isTracking.collectAsState()
    val isPaused by GpsTrackingService.isPaused.collectAsState()
    val recordedPoints by GpsTrackingService.recordedPoints.collectAsState()
    val currentLocation by GpsTrackingService.currentLocation.collectAsState()
    val gpsAccuracy by GpsTrackingService.gpsAccuracyMeters.collectAsState()
    val totalDistance by GpsTrackingService.totalDistanceMeters.collectAsState()

    LaunchedEffect(Unit) {
        savedRoutes = RouteStorage.getSavedRoutes(context)
    }

    // Konum İzin Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineLocationGranted || coarseLocationGranted) {
            val resumeTarget = pendingResumeRoute
            if (resumeTarget != null) {
                GpsTrackingService.resumeRouteService(context, resumeTarget)
                pendingResumeRoute = null
            } else {
                showStartLocationDialog = true
            }
        } else {
            Toast.makeText(context, "Su hattı kaydı için konum izni gereklidir!", Toast.LENGTH_LONG).show()
        }
    }

    // İmece: Arkadaşın Telefonundaki QR Kodu Okuyarak Parça Ekleme Launcher
    val qrImportLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents != null) {
            try {
                val importedRoute = DeltaCompressor.decode(result.contents)
                val saved = RouteStorage.saveRoute(context, importedRoute)
                savedRoutes = RouteStorage.getSavedRoutes(context)
                if (!selectedForMergeIds.contains(saved.id)) {
                    selectedForMergeIds.add(saved.id)
                }
                Toast.makeText(
                    context,
                    "🤝 '${importedRoute.fountainName}' parçası eklendi ve birleştirmeye seçildi!",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Okunan QR kod geçerli bir Su Yolu parçası değil!", Toast.LENGTH_LONG).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Arkadaşınızın Telefonundaki Parça QR Kodunu Okutun")
                setBeepEnabled(true)
                setOrientationLocked(false)
            }
            qrImportLauncher.launch(options)
        } else {
            Toast.makeText(context, "QR ile parça almak için kamera izni gereklidir!", Toast.LENGTH_SHORT).show()
        }
    }

    // Play Store Prominent Disclosure Dialog
    if (showLocationDisclosureDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDisclosureDialog = false },
            title = {
                Text("📍 Konum İzni Bilgilendirmesi", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Su Yolu uygulaması, siz çeşmeden su gözüne (kaynağa) yürürken su borusu hattının geçtiği güzergahı 10 metre aralıklarla haritalandırmak ve QR koda dönüştürmek için ekran kapalıyken bile (ön plan servisi olarak) GPS konum verinizi kullanır.\n\nKonum verileriniz hiçbir sunucuya gönderilmez, yalnızca cihazınızda ve ürettiğiniz QR kodda saklanır."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLocationDisclosureDialog = false
                        requestLocationPermissions(permissionLauncher)
                    }
                ) {
                    Text("Anladım ve İzin Ver")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationDisclosureDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // "Nereden Başlıyorsunuz?" Başlangıç Konumu Seçim Diyaloğu
    if (showStartLocationDialog) {
        AlertDialog(
            onDismissRequest = { showStartLocationDialog = false },
            title = {
                Text("📍 Nereden Başlıyorsunuz?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "İlk noktanızın haritada doğru adlandırılması için şu an bulunduğunuz konumu seçin:",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )

                    val startOptions = listOf(
                        Triple(PointType.FOUNTAIN, "⛲ Çeşme", "Çeşmenin başından su kaynağına doğru"),
                        Triple(PointType.SOURCE, "💧 Su Gözü (Kaynak)", "Suyun çıktığı kaynaktan çeşmeye doğru"),
                        Triple(PointType.VALVE, "🚰 Vana / Maslak", "Hattın ortasındaki vana veya maslaktan"),
                        Triple(PointType.AIR_VALVE, "🌬️ Hava Alma Bacası", "Hattın üzerindeki hava tahliye bacasından"),
                        Triple(PointType.WAYPOINT, "📍 Boru Hattı (Ara Nokta)", "Boru hattının herhangi bir yerinden")
                    )

                    for ((type, title, desc) in startOptions) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showStartLocationDialog = false
                                    GpsTrackingService.startService(
                                        context,
                                        fountainNameInput.ifBlank { "Tarihi Hat" },
                                        type
                                    )
                                    Toast.makeText(
                                        context,
                                        "🟢 Kayıt '${type.displayName}' noktasından başlatıldı!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(type.symbol, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF0D47A1)
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showStartLocationDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // WhatsApp / Mesaj ile Gelen SY1 Metnini Yapıştırıp İçe Aktarma Diyaloğu
    if (showImportTextDialog) {
        AlertDialog(
            onDismissRequest = { showImportTextDialog = false },
            title = {
                Text("📲 Paylaşılan Hat Parçasını Ekle", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Başka bir köylünün / arkadaşınızın WhatsApp veya SMS ile gönderdiği 'SY1;...' ile başlayan hat kodunu buraya yapıştırın:",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    OutlinedTextField(
                        value = importTextValue,
                        onValueChange = { importTextValue = it },
                        label = { Text("SY1;... Parça Kodu") },
                        placeholder = { Text("SY1;Yukarı Hat;39.92,32.85;...") },
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clipText.isNullOrBlank()) {
                                importTextValue = clipText.trim()
                            } else {
                                Toast.makeText(context, "Panoda kopyalanmış metin bulunamadı.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Panodan Yapıştır")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            // İçinde SY1; geçen satırı ayıkla (WhatsApp mesaj açıklaması varsa temizle)
                            val raw = importTextValue.trim()
                            val syIndex = raw.indexOf("SY1;")
                            val cleanCode = if (syIndex >= 0) raw.substring(syIndex).lines().first().trim() else raw
                            val decoded = DeltaCompressor.decode(cleanCode)
                            val saved = RouteStorage.saveRoute(context, decoded)
                            savedRoutes = RouteStorage.getSavedRoutes(context)
                            if (!selectedForMergeIds.contains(saved.id)) {
                                selectedForMergeIds.add(saved.id)
                            }
                            showImportTextDialog = false
                            importTextValue = ""
                            Toast.makeText(context, "✅ '${decoded.fountainName}' parçası başarıyla eklendi!", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Hatalı kod! Lütfen 'SY1;' ile başlayan kodu eksiksiz yapıştırın.", Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text("Parçayı Ekle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportTextDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // Birleştirilecek Parçalar İçin İsim Onay Diyaloğu
    if (showMergeNameDialog) {
        AlertDialog(
            onDismissRequest = { showMergeNameDialog = false },
            title = {
                Text("🤝 İmece: Parçaları Birleştir", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Seçtiğiniz ${selectedForMergeIds.size} ayrı boru hattı parçası (biri çeşmeden, diğeri su gözünden ters yönde gelmiş olsa bile) otomatik olarak uç uca hizalanıp tek bir QR koda dönüştürülecektir.",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    OutlinedTextField(
                        value = mergedRouteNameInput,
                        onValueChange = { mergedRouteNameInput = it },
                        label = { Text("Birleşik Çeşme / Hat Adı") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedRoutes = savedRoutes
                            .filter { selectedForMergeIds.contains(it.id) }
                            .map { it.routeData }

                        if (selectedRoutes.size >= 2) {
                            val merged = RouteMerger.mergeSegments(
                                fountainTitle = mergedRouteNameInput.ifBlank { "İmece Su Hattı" },
                                routes = selectedRoutes
                            )
                            RouteStorage.saveRoute(context, merged)
                            savedRoutes = RouteStorage.getSavedRoutes(context)
                            selectedForMergeIds.clear()
                            showMergeNameDialog = false
                            Toast.makeText(
                                context,
                                "🎉 ${selectedRoutes.size} parça başarıyla birleştirildi! Toplam ${merged.points.size + 1} nokta.",
                                Toast.LENGTH_LONG
                            ).show()
                            onRouteSaved(merged)
                        }
                    }
                ) {
                    Text("Birleştir ve QR Üret")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeNameDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Fountain Name Header Input
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⛲ Çeşme & Hat Parçası Adı",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(
                        onClick = {
                            val sample = RouteStorage.createSampleRoute()
                            RouteStorage.saveRoute(context, sample)
                            savedRoutes = RouteStorage.getSavedRoutes(context)
                            Toast.makeText(context, "Örnek rota yüklendi! QR & CNC sekmesine geçiliyor.", Toast.LENGTH_SHORT).show()
                            onRouteSaved(sample)
                        }
                    ) {
                        Text("✨ Örnek Hat Yükle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = fountainNameInput,
                    onValueChange = { fountainNameInput = it },
                    label = { Text("Çeşme Adı veya Kısım (Örn: Dedeler 1. Etap)") },
                    placeholder = { Text("Örn: Dedeler Pınarı - Alt Kısım") },
                    singleLine = true,
                    enabled = !isTracking,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Section 2: Live Tracking Dashboard
        val statusBgColor = when {
            isTracking -> Color(0xFFE8F5E9)
            isPaused || recordedPoints.isNotEmpty() -> Color(0xFFFFF8E1)
            else -> MaterialTheme.colorScheme.surface
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = statusBgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isTracking -> "🟢 GPS TAKİP AKTİF"
                                isPaused || recordedPoints.isNotEmpty() -> "⏸️ DURAKLATILDI (BEKLEMEDE)"
                                else -> "🔴 TAKİP HAZIR"
                            },
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isTracking -> Color(0xFF2E7D32)
                                isPaused || recordedPoints.isNotEmpty() -> Color(0xFFE65100)
                                else -> Color.Red
                            },
                            fontSize = 14.sp
                        )
                        if (gpsAccuracy != null) {
                            Text(
                                text = String.format(Locale.getDefault(), "GPS Hassasiyeti: ±%.1f m", gpsAccuracy),
                                fontSize = 11.sp,
                                color = if ((gpsAccuracy ?: 99f) <= 15f) Color(0xFF2E7D32) else Color(0xFFE65100)
                            )
                        } else if (isPaused || recordedPoints.isNotEmpty()) {
                            Text(
                                text = "Kayıtlar korundu, devam edebilirsiniz.",
                                fontSize = 11.sp,
                                color = Color.DarkGray
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (isTracking) {
                                // Duraklat (Beklemeye Al) ve taslak olarak otomatik kaydet
                                GpsTrackingService.pauseService(context)
                                if (recordedPoints.isNotEmpty()) {
                                    val startPt = recordedPoints.first()
                                    val subList = if (recordedPoints.size > 1) recordedPoints.subList(1, recordedPoints.size) else emptyList()
                                    val draftRoute = RouteData(
                                        version = "SY1",
                                        fountainName = fountainNameInput.ifBlank { "Tarihi Su Hattı" },
                                        startLat = startPt.lat,
                                        startLng = startPt.lng,
                                        startPointType = startPt.type,
                                        points = subList
                                    )
                                    RouteStorage.saveRoute(context, draftRoute)
                                    savedRoutes = RouteStorage.getSavedRoutes(context)
                                }
                                Toast.makeText(
                                    context,
                                    "⏸️ Kayıt beklemeye alındı! Noktalarınız korundu, 'Devam Et' ile sürdürebilirsiniz.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else if (isPaused || recordedPoints.isNotEmpty()) {
                                // Beklemeden kaldığı yerden devam et (hiçbir noktayı silme!)
                                if (hasLocationPermission(context)) {
                                    GpsTrackingService.continueFromPause(context, fountainNameInput.ifBlank { "Tarihi Çeşme" })
                                } else {
                                    showLocationDisclosureDialog = true
                                }
                            } else {
                                // Sıfırdan yeni kayıt başlat: önce kullanıcıya "Nereden Başlıyorsunuz?" diye sor
                                pendingResumeRoute = null
                                if (hasLocationPermission(context)) {
                                    showStartLocationDialog = true
                                } else {
                                    showLocationDisclosureDialog = true
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                isTracking -> Color(0xFFE65100) // Turuncu Beklet/Duraklat
                                isPaused || recordedPoints.isNotEmpty() -> Color(0xFF2E7D32) // Yeşil Devam Et
                                else -> MaterialTheme.colorScheme.primary // Mavi Başlat
                            }
                        )
                    ) {
                        Icon(
                            imageVector = if (isTracking) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            when {
                                isTracking -> "Beklet"
                                isPaused || recordedPoints.isNotEmpty() -> "Devam Et"
                                else -> "Başlat"
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCard(
                        title = "Kayıtlı Nokta",
                        value = "${recordedPoints.size}",
                        unit = "adet"
                    )
                    StatCard(
                        title = "Mesafe",
                        value = String.format(Locale.getDefault(), "%.1f", totalDistance),
                        unit = "metre"
                    )
                }
            }
        }

        // Section 3: Manual Critical Point Marking Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📍 Kritik Nokta Ekle (Elle İşaretle):",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )

            if (recordedPoints.size > 1) {
                TextButton(
                    onClick = {
                        if (GpsTrackingService.undoLastPoint()) {
                            Toast.makeText(context, "Son nokta geri alındı.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.height(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Son Noktayı Sil", fontSize = 12.sp)
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        addManualPoint(context, PointType.FOUNTAIN, "Çeşme")
                    },
                    enabled = isTracking,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("⛲ Çeşme", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        addManualPoint(context, PointType.SOURCE, "Su Gözü (Kaynak)")
                    },
                    enabled = isTracking,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("💧 Su Gözü", fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        addManualPoint(context, PointType.VALVE, "Vana / Maslak")
                    },
                    enabled = isTracking,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🚰 Vana", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        addManualPoint(context, PointType.AIR_VALVE, "Hava Bacası")
                    },
                    enabled = isTracking,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🌬️ Hava Bacası", fontSize = 12.sp)
                }
            }
        }

        // Section 4: Live Map
        Text(
            text = "🗺️ Canlı Boru Hattı Haritası:",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.LightGray)
        ) {
            OsmMapView(
                modifier = Modifier.fillMaxSize(),
                routeData = null,
                currentLocation = currentLocation,
                recordedPoints = recordedPoints
            )
        }

        // Section 5: Save & Export Button
        Button(
            onClick = {
                if (recordedPoints.isEmpty()) {
                    Toast.makeText(context, "Henüz kaydedilmiş boru hattı verisi yok!", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                if (isTracking || isPaused) {
                    GpsTrackingService.stopService(context)
                }

                val startPt = recordedPoints.first()
                val subList = if (recordedPoints.size > 1) recordedPoints.subList(1, recordedPoints.size) else emptyList()

                val routeData = RouteData(
                    version = "SY1",
                    fountainName = fountainNameInput.ifBlank { "Tarihi Su Hattı" },
                    startLat = startPt.lat,
                    startLng = startPt.lng,
                    startPointType = startPt.type,
                    points = subList
                )

                RouteStorage.saveRoute(context, routeData)
                savedRoutes = RouteStorage.getSavedRoutes(context)
                GpsTrackingService.clearSessionPoints()
                onRouteSaved(routeData)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Bu Kaydı Bitir ve Kaydet", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // Section 6: İMECE MODU (Çoklu Kayıt Birleştirme & Parça Ekleme)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GroupWork, contentDescription = null, tint = Color(0xFFE65100))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🤝 İmece Modu (Parça Ekle & Birleştir)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFFE65100)
                    )
                }

                Text(
                    text = "Farklı kişilerin (örn. biri çeşmeden, biri su gözünden yürüyerek) veya farklı günlerde kaydettiği hat parçalarını tek telefonda toplayıp birleştirebilirsiniz.",
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasCam) {
                                val options = ScanOptions().apply {
                                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    setPrompt("Arkadaşınızın Telefonundaki Parça QR Kodunu Okutun")
                                    setBeepEnabled(true)
                                    setOrientationLocked(false)
                                }
                                qrImportLauncher.launch(options)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("QR ile Parça Al", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { showImportTextDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mesaj Kodu Ekle", fontSize = 11.sp)
                    }
                }

                if (selectedForMergeIds.size >= 2) {
                    Button(
                        onClick = {
                            val firstSelected = savedRoutes.firstOrNull { selectedForMergeIds.contains(it.id) }
                            mergedRouteNameInput = "${firstSelected?.routeData?.fountainName ?: "Çeşme"} (Tam Hat)"
                            showMergeNameDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                    ) {
                        Icon(Icons.Default.MergeType, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Seçilen ${selectedForMergeIds.size} Parçayı Uç Uca Birleştir",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (savedRoutes.size >= 2) {
                    Text(
                        text = "💡 Birleştirmek istediğiniz parçaların yanındaki kutucukları (☑️) işaretleyin.",
                        fontSize = 11.sp,
                        color = Color(0xFFBF360C),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Section 7: Saved Routes History (with Resume, Share Segment & Merge Checkboxes)
        if (savedRoutes.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Kayıtlı Su Hatlarım & Parçalar:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (item in savedRoutes) {
                    val isChecked = selectedForMergeIds.contains(item.id)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                RouteStorage.setActiveRoute(context, item.routeData)
                                onRouteSaved(item.routeData)
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChecked) Color(0xFFFFF3E0) else Color.White
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            selectedForMergeIds.add(item.id)
                                        } else {
                                            selectedForMergeIds.remove(item.id)
                                        }
                                    }
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "⛲ ${item.routeData.fountainName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF0D47A1)
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.getDefault(),
                                            "%d Nokta • %.1f m • %s",
                                            item.routeData.points.size + 1,
                                            item.routeData.calculateTotalDistanceMeters(),
                                            dateFormat.format(Date(item.timestampMs))
                                        ),
                                        fontSize = 11.sp,
                                        color = Color.DarkGray
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        selectedForMergeIds.remove(item.id)
                                        RouteStorage.deleteRoute(context, item.id)
                                        savedRoutes = RouteStorage.getSavedRoutes(context)
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.Red)
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Alt Aksiyon Satırı: Kaldığın Yerden Devam Et & Parçayı Arkadaşına Gönder
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TextButton(
                                    onClick = {
                                        fountainNameInput = item.routeData.fountainName
                                        if (hasLocationPermission(context)) {
                                            GpsTrackingService.resumeRouteService(context, item.routeData)
                                            Toast.makeText(
                                                context,
                                                "▶️ '${item.routeData.fountainName}' kaldığı yerden devam ediyor! Yürümeye başlayabilirsiniz.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            pendingResumeRoute = item.routeData
                                            showLocationDisclosureDialog = true
                                        }
                                    },
                                    enabled = !isTracking
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.height(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Kaldığım Yerden Devam Et", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                TextButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                item.encodedCompact
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Hat Parçasını Arkadaşına Gönder"))
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.height(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Parçayı Gönder", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, unit: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.width(130.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 11.sp, color = Color.Gray)
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(text = unit, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun requestLocationPermissions(
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    val permissionsNeeded = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    launcher.launch(permissionsNeeded.toTypedArray())
}

private fun addManualPoint(context: Context, type: PointType, label: String) {
    val success = GpsTrackingService.addCriticalPoint(type, label)
    if (success) {
        Toast.makeText(context, "${type.symbol} ${type.displayName} kaydedildi!", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "GPS konumu bekleniyor, lütfen açık alanda olduğunuzdan emin olun.", Toast.LENGTH_SHORT).show()
    }
}
