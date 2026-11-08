package com.turga.suyolu.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.turga.suyolu.algorithm.DeltaCompressor
import com.turga.suyolu.data.PointType
import com.turga.suyolu.data.RouteData
import com.turga.suyolu.data.RouteStorage
import com.turga.suyolu.service.GpsTrackingService
import com.turga.suyolu.ui.components.OsmMapView
import java.util.Locale

@SuppressLint("MissingPermission")
@Composable
fun ScanRepairScreen() {
    val context = LocalContext.current
    var scannedRouteData by remember { mutableStateOf<RouteData?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }

    // Live user location for field navigation
    var liveUserLocation by remember { mutableStateOf<Location?>(null) }
    val serviceLocation by GpsTrackingService.currentLocation.collectAsState()

    val effectiveUserLocation = liveUserLocation ?: serviceLocation

    // Start active GPS updates for field navigation when viewing this screen
    DisposableEffect(Unit) {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val callback = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                res.lastLocation?.let {
                    liveUserLocation = it
                }
            }
        }

        if (hasFine || hasCoarse) {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
                .setMinUpdateIntervalMillis(800L)
                .build()
            fusedClient.requestLocationUpdates(req, callback, Looper.getMainLooper())
        }

        onDispose {
            if (hasFine || hasCoarse) {
                fusedClient.removeLocationUpdates(callback)
            }
        }
    }

    // ZXing Scanner Launcher
    val qrScanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents != null) {
            val qrContent = result.contents
            try {
                val decoded = DeltaCompressor.decode(qrContent)
                scannedRouteData = decoded
                RouteStorage.saveRoute(context, decoded)
                scanError = null
                Toast.makeText(context, "Çeşme QR Başarıyla Çözüldü: ${decoded.fountainName}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                scanError = "Okunan QR Kod Su Yolu formatında değil!"
                Toast.makeText(context, scanError, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Camera Permission Launcher before launching scanner
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchQrScanner(qrScanLauncher)
        } else {
            Toast.makeText(context, "QR Kod okutmak için kamera izni gereklidir!", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Scan Button Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔍 Saha Rehberi ve Tamir Modülü",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Çeşmedeki mermer/pirinç plakada kazınmış QR kodu tarayarak boru hattının tam yerini haritada ve pusula kılavuzunda görüntüleyin.",
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (hasCam) {
                            launchQrScanner(qrScanLauncher)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Çeşmedeki QR Kodu Tara", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val lastRoute = remember { RouteStorage.getLastActiveRoute(context) }
                    if (lastRoute != null) {
                        OutlinedButton(
                            onClick = { scannedRouteData = lastRoute },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Son Kaydı Aç", fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { scannedRouteData = RouteStorage.createSampleRoute() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Örnek Hattı Dene", fontSize = 12.sp)
                    }
                }
            }
        }

        if (scannedRouteData != null) {
            val route = scannedRouteData!!
            val totalDistM = route.calculateTotalDistanceMeters()

            // Section 1.5: LIVE PIPE PROXIMITY RADAR (Kazı & Boru Bulucu Kılavuz)
            if (effectiveUserLocation != null) {
                val proximity = route.findNearestPipeProximity(
                    effectiveUserLocation.latitude,
                    effectiveUserLocation.longitude
                )
                val isRightOnPipe = proximity.distanceMeters <= 2.5
                val isCloseToPipe = proximity.distanceMeters <= 10.0

                val cardBg = when {
                    isRightOnPipe -> Color(0xFF1B5E20)
                    isCloseToPipe -> Color(0xFFE65100)
                    else -> Color(0xFF0D47A1)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Explore, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CANLI BORU HATTI BULUCU (RADAR)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = String.format(Locale.getDefault(), "%.1f Metre", proximity.distanceMeters),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = when {
                                isRightOnPipe -> "✅ TAM BORU HATTININ ÜZERİNDESİNİZ! (Kazı Noktası)"
                                isCloseToPipe -> "⚠️ Boruya Çok Yakınsınız! Yön: ${proximity.directionLabel}"
                                else -> "🧭 Boru Hattına Ulaşmak İçin Yön: ${proximity.directionLabel}"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Section 2: Scanned Route Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⛲ ${route.fountainName}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Çeşme Konumu:", fontSize = 12.sp, color = Color.Gray)
                        Text("${route.startLat}, ${route.startLng}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Toplam Boru Mesafesi:", fontSize = 12.sp, color = Color.Gray)
                        Text(String.format(Locale.getDefault(), "%.1f Metre", totalDistM), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Hat Üzerindeki Nokta Sayısı:", fontSize = 12.sp, color = Color.Gray)
                        Text("${route.points.size + 1} Nokta", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (effectiveUserLocation != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("📍 Mevcut Konumunuz:", fontSize = 12.sp, color = Color(0xFF0288D1), fontWeight = FontWeight.Bold)
                            Text(
                                String.format(Locale.getDefault(), "%.5f, %.5f", effectiveUserLocation.latitude, effectiveUserLocation.longitude),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0288D1)
                            )
                        }
                    }
                }
            }

            // Section 3: Interactive Route Map with User Location Frame
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🗺️ Canlı Harita & Boru Hattı:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                if (effectiveUserLocation != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFF0288D1), modifier = Modifier.height(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Konumunuz Aktif", fontSize = 11.sp, color = Color(0xFF0288D1), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                    .background(Color.LightGray)
            ) {
                OsmMapView(
                    modifier = Modifier.fillMaxSize(),
                    routeData = route,
                    currentLocation = effectiveUserLocation
                )
            }

            // Section 4: Critical Points Breakdown List with Live Distance
            Text(
                text = "🔧 Kritik Noktalar & Mevcut Konuma Uzaklık:",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // First Point: Route Start Point
                PointDetailCard(
                    title = "${route.startPointType.symbol} ${route.startPointType.displayName} (Başlangıç Noktası)",
                    lat = route.startLat,
                    lng = route.startLng,
                    currentLocation = effectiveUserLocation
                )

                // Sub Points
                for (pt in route.points) {
                    if (pt.type != PointType.WAYPOINT) {
                        PointDetailCard(
                            title = "${pt.type.symbol} ${pt.type.displayName}",
                            lat = pt.lat,
                            lng = pt.lng,
                            currentLocation = effectiveUserLocation
                        )
                    }
                }
            }
        } else {
            // Default placeholder state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.height(48.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Henüz bir QR Kod taranmadı.",
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun launchQrScanner(
    launcher: androidx.activity.result.ActivityResultLauncher<ScanOptions>
) {
    val options = ScanOptions().apply {
        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        setPrompt("Çeşmedeki QR Kodu Kareye Hizalayın")
        setCameraId(0)
        setBeepEnabled(true)
        setBarcodeImageEnabled(false)
        setOrientationLocked(false)
    }
    launcher.launch(options)
}

@Composable
private fun PointDetailCard(
    title: String,
    lat: Double,
    lng: Double,
    currentLocation: Location?
) {
    val distToUser = if (currentLocation != null) {
        RouteData.haversineDistance(
            currentLocation.latitude, currentLocation.longitude,
            lat, lng
        )
    } else null

    val directionStr = if (currentLocation != null) {
        val brg = RouteData.bearingDegrees(currentLocation.latitude, currentLocation.longitude, lat, lng)
        RouteData.compassDirectionTr(brg)
    } else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                if (distToUser != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE1F5FE))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.getDefault(), "📍 %.1f m (%s)", distToUser, directionStr ?: ""),
                            fontSize = 11.sp,
                            color = Color(0xFF0288D1),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "GPS Koordinatı: $lat, $lng",
                fontSize = 11.sp,
                color = Color.DarkGray
            )
        }
    }
}
