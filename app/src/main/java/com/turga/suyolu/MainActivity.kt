package com.turga.suyolu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turga.suyolu.data.RouteData
import com.turga.suyolu.data.RouteStorage
import com.turga.suyolu.ui.screens.ExportScreen
import com.turga.suyolu.ui.screens.RecordScreen
import com.turga.suyolu.ui.screens.ScanRepairScreen
import com.turga.suyolu.ui.theme.SuYoluTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        setContent {
            SuYoluTheme {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var activeRouteData by remember { mutableStateOf<RouteData?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Restore last active route on startup / screen rotation so data is never lost
    LaunchedEffect(Unit) {
        if (activeRouteData == null) {
            activeRouteData = RouteStorage.getLastActiveRoute(context)
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text("💧 Su Yolu: Kılavuz & Gizlilik", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("1. Projenin Amacı:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Anadolu'daki tarihi ve geleneksel köy çeşmelerine su taşıyan, zamanla toprağın altında kaybolan boru hatlarını GPS ile kayıt altına alıp QR koda dönüştürmek ve mermer/pirinç plakalarla gelecek nesillere aktarmaktır.",
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("2. Nasıl Kullanılır?", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "• Saha Kayıt: Çeşmeden başlayıp suyun gözüne kadar boru hattı boyunca yürüyün. Her 10 metrede bir konum otomatik kaydedilir. Vana ve hava bacalarını işaretleyin.\n" +
                        "• QR & Dışa Aktar: Kaydedilen hattı tek bir QR koda sıkıştırın. CNC mermer ustası için DXF, lazer plaka için SVG çıktısı alın.\n" +
                        "• Saha Rehberi: Çeşmedeki QR kodu okutarak arıza durumunda borunun tam olarak nereden geçtiğini canlı radar ile bulun.",
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("3. Gizlilik Politikası & Veri Güvenliği:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "• Konum Verisi: Yalnızca siz 'Başlat' butonuna bastığınızda su borusu güzergahını oluşturmak ve haritada konumunuzu göstermek için kullanılır.\n" +
                        "• Kamera İzni: Yalnızca çeşmelerdeki QR kodları okutmak için kullanılır.\n" +
                        "• Veri Paylaşımı: Konum ve kamera verileriniz hiçbir uzak sunucuya gönderilmez, üçüncü taraflarla paylaşılmaz. Tüm veriler %100 cihazınızda ve ürettiğiniz QR kodun içinde saklanır.",
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SU YOLU - GPS Boru & QR CNC",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Kılavuz ve Gizlilik Politikası",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D47A1),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFFE3F2FD)
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    label = { Text("Saha Kayıt") }
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                    label = { Text("QR & CNC") }
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    label = { Text("Saha Rehberi") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> RecordScreen(
                    onRouteSaved = { savedRoute ->
                        activeRouteData = savedRoute
                        selectedTab = 1
                    }
                )
                1 -> ExportScreen(
                    routeData = activeRouteData,
                    onLoadRoute = { loaded ->
                        activeRouteData = loaded
                    }
                )
                2 -> ScanRepairScreen()
            }
        }
    }
}
