package com.turga.suyolu.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turga.suyolu.algorithm.DeltaCompressor
import com.turga.suyolu.data.RouteData
import com.turga.suyolu.data.RouteStorage
import com.turga.suyolu.export.ExportManager
import com.turga.suyolu.qr.QrManager
import java.util.Locale

@Composable
fun ExportScreen(
    routeData: RouteData?,
    onLoadRoute: ((RouteData) -> Unit)? = null
) {
    val context = LocalContext.current

    if (routeData == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "⚠️ Sıkıştırılacak boru hattı verisi bulunamadı!\nLütfen önce 'Saha Kayıt' sekmesinden hat kaydediniz veya örnek bir hat yükleyiniz.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val sample = RouteStorage.createSampleRoute()
                        RouteStorage.saveRoute(context, sample)
                        onLoadRoute?.invoke(sample)
                    }
                ) {
                    Text("✨ Örnek Çeşme Hattı Yükle")
                }
            }
        }
        return
    }

    // 1. Delta Compression (Boru Hattı SY1 Verisi)
    val compressedString = remember(routeData) {
        DeltaCompressor.encode(routeData)
    }

    val stats = remember(routeData, compressedString) {
        DeltaCompressor.calculateStats(routeData, compressedString)
    }

    // 2. Sol QR: Boru Hattı QR'ı
    val pipeQrBitmap = remember(compressedString) {
        QrManager.generateQrBitmap(compressedString, sizePx = 600)
    }
    val pipeQrMatrix = remember(compressedString) {
        QrManager.generateBooleanMatrix(compressedString)
    }

    // 3. Sağ QR: Uygulama İndir QR'ı (Google Play Store Linki)
    val appQrBitmap = remember {
        QrManager.generateQrBitmap(QrManager.APP_PLAY_STORE_URL, sizePx = 600)
    }
    val appQrMatrix = remember {
        QrManager.generateBooleanMatrix(QrManager.APP_PLAY_STORE_URL)
    }

    // 4. Mermer / Pirinç Plaka Baskı Görseli (Tabela Tasarımı)
    val plateBitmap = remember(routeData, pipeQrBitmap, appQrBitmap) {
        ExportManager.generatePlateBitmap(routeData, pipeQrBitmap, appQrBitmap)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Section 1: Komple Çift QR'lı Mermer / Pirinç Plaka Tabela Kartı
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⛲ ${routeData.fountainName}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Mermer & Pirinç Plaka Şablonu (Çift QR Kodlu)",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Yan Yana İki QR Kodu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    // Sol QR: Boru Hattı
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "1. BORU HATTI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF0D47A1)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (pipeQrBitmap != null) {
                            Image(
                                bitmap = pipeQrBitmap.asImageBitmap(),
                                contentDescription = "Boru Hattı QR Kodu",
                                modifier = Modifier
                                    .size(130.dp)
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
                                    .padding(4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Su Yolu ile Okutun",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(140.dp)
                            .background(Color(0xFFE0E0E0))
                    )

                    // Sağ QR: Uygulamayı İndir
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "2. UYGULAMAYI İNDİR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (appQrBitmap != null) {
                            Image(
                                bitmap = appQrBitmap.asImageBitmap(),
                                contentDescription = "Uygulama İndir QR Kodu",
                                modifier = Modifier
                                    .size(130.dp)
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
                                    .padding(4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Normal Kamerayla Okutun",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Plaka Altındaki Açıklama & Talimat Kutusu
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "📌 ÇEŞMEDEKİ KULLANIM TALİMATI (Plakaya İşlenir):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFFB71C1C)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. Adım: Gelecekte arıza olduğunda köylü veya usta önce sağdaki QR kodu telefonunun normal kamerasıyla okutup ücretsiz 'Su Yolu' uygulamasını indirir.\n" +
                                   "2. Adım: Uygulamayı açıp soldaki Boru Hattı QR kodunu tarar. Boru hattı, vana ve su kaynağı anında haritada canlı radarla açılır.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFF263238)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Plaka Görseli İndir / Paylaş Butonu
                Button(
                    onClick = {
                        ExportManager.saveImage(context, "${routeData.fountainName}_Tabela_Plaka.png", plateBitmap)
                        Toast.makeText(context, "🖼️ Çift QR'lı Mermer Tabela Görseli Galeriye (Pictures/SuYolu) Kaydedildi!", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🖼️ Plaka Tabela Görselini İndir (PNG / Baskı Şablonu)")
                }
            }
        }

        // Section 2: Delta Sıkıştırma Analizi
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡ Delta Sıkıştırma Analizi",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0),
                        fontSize = 14.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2E7D32))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%%%.1f Sıkıştırıldı", stats.compressionRatioPercentage),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ham JSON Veri Boyutu:", fontSize = 12.sp, color = Color.DarkGray)
                    Text("${stats.originalSizeBytes} Bayt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("QR Sıkıştırılmış Veri:", fontSize = 12.sp, color = Color.DarkGray)
                    Text("${stats.compressedSizeBytes} Bayt", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }

                if (stats.originalPointCount != stats.simplifiedPointCount) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Akıllı Köşe Optimizasyonu:", fontSize = 12.sp, color = Color.DarkGray)
                        Text("${stats.originalPointCount} -> ${stats.simplifiedPointCount} Nokta", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text("Kodlanmış QR Metni (SY1 Protokolü):", fontSize = 11.sp, color = Color.Gray)
                Text(
                    text = compressedString,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 3,
                    color = Color(0xFF0D47A1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .padding(6.dp)
                )
            }
        }

        // Section 3: CNC & Lazer Dışa Aktarma Formatları
        Text(
            text = "🛠️ CNC & Lazer Dışa Aktarma (Çift QR + Açıklamalı):",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )

        // DXF Button (CNC Freze)
        Button(
            onClick = {
                val dxfContent = ExportManager.generateDxf(routeData, pipeQrMatrix, appQrMatrix)
                ExportManager.saveFileToDownloads(
                    context,
                    "${routeData.fountainName}_CNC.dxf",
                    "image/vnd.dxf",
                    dxfContent
                )
                Toast.makeText(context, "DXF (Çift QR'lı CNC Freze) dosyası 'Downloads/SuYolu' klasörüne kaydedildi.", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Code, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("📐 DXF Formatında İndir (Çift QR'lı CNC & AutoCAD)")
        }

        // SVG Button (Lazer Kazıma)
        Button(
            onClick = {
                val svgContent = ExportManager.generateSvg(routeData, pipeQrMatrix, appQrMatrix)
                ExportManager.saveFileToDownloads(
                    context,
                    "${routeData.fountainName}_Lazer.svg",
                    "image/svg+xml",
                    svgContent
                )
                Toast.makeText(context, "SVG (Çift QR'lı Lazer Tabela) dosyası 'Downloads/SuYolu' klasörüne kaydedildi.", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
        ) {
            Icon(Icons.Default.QrCode, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("⚡ SVG Formatında İndir (Çift QR'lı Lazer Plaka)")
        }

        // KML Button (Google Earth)
        Button(
            onClick = {
                val kmlContent = ExportManager.generateKml(routeData)
                ExportManager.saveFileToDownloads(
                    context,
                    "${routeData.fountainName}_Earth.kml",
                    "application/vnd.google-earth.kml+xml",
                    kmlContent
                )
                Toast.makeText(context, "KML (Google Earth 3D) dosyası 'Downloads/SuYolu' klasörüne kaydedildi.", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
        ) {
            Icon(Icons.Default.Map, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("🌍 KML Formatında İndir (Google Earth 3D)")
        }

        // GPX Button (El Tipi GPS Cihazları)
        Button(
            onClick = {
                val gpxContent = ExportManager.generateGpx(routeData)
                ExportManager.saveFileToDownloads(
                    context,
                    "${routeData.fountainName}_GPS.gpx",
                    "application/gpx+xml",
                    gpxContent
                )
                Toast.makeText(context, "GPX (El GPS Cihazı) dosyası 'Downloads/SuYolu' klasörüne kaydedildi.", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A148C))
        ) {
            Icon(Icons.Default.Explore, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("🧭 GPX Formatında İndir (Garmin / El GPS)")
        }

        // Paylaşım Butonları
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    ExportManager.shareImage(context, "${routeData.fountainName}_Tabela.png", plateBitmap)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Plakayı Paylaş", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    val dxfContent = ExportManager.generateDxf(routeData, pipeQrMatrix, appQrMatrix)
                    ExportManager.shareContent(
                        context,
                        "${routeData.fountainName}_CNC.dxf",
                        dxfContent,
                        "application/octet-stream"
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("DXF Paylaş", fontSize = 12.sp)
            }
        }
    }
}
