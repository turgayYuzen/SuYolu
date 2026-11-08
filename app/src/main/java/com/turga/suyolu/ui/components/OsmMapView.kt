package com.turga.suyolu.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.location.Location
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.turga.suyolu.data.PointType
import com.turga.suyolu.data.RouteData
import com.turga.suyolu.data.RoutePoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import java.io.File
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@SuppressLint("ClickableViewAccessibility")
@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    routeData: RouteData?,
    currentLocation: Location? = null,
    recordedPoints: List<RoutePoint> = emptyList(),
    allowFullscreen: Boolean = true
) {
    var isFullscreen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Normal Map View
        MapContent(
            modifier = Modifier.fillMaxSize(),
            routeData = routeData,
            currentLocation = currentLocation,
            recordedPoints = recordedPoints,
            onToggleFullscreen = if (allowFullscreen) { { isFullscreen = true } } else null,
            isFullscreen = false
        )

        // Fullscreen Modal Dialog
        if (isFullscreen) {
            Dialog(
                onDismissRequest = { isFullscreen = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black)
                ) {
                    MapContent(
                        modifier = Modifier.fillMaxSize(),
                        routeData = routeData,
                        currentLocation = currentLocation,
                        recordedPoints = recordedPoints,
                        onToggleFullscreen = { isFullscreen = false },
                        isFullscreen = true
                    )
                }
            }
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun MapContent(
    modifier: Modifier = Modifier,
    routeData: RouteData?,
    currentLocation: Location?,
    recordedPoints: List<RoutePoint>,
    onToggleFullscreen: (() -> Unit)?,
    isFullscreen: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize OSMDroid configuration with strict internal cache directories
    remember {
        try {
            val osmCacheDir = File(context.cacheDir, "osmdroid")
            if (!osmCacheDir.exists()) osmCacheDir.mkdirs()
            val osmTileCache = File(osmCacheDir, "tiles")
            if (!osmTileCache.exists()) osmTileCache.mkdirs()

            Configuration.getInstance().osmdroidBasePath = osmCacheDir
            Configuration.getInstance().osmdroidTileCache = osmTileCache
            Configuration.getInstance().userAgentValue = context.packageName
        } catch (e: Exception) {
            e.printStackTrace()
        }
        true
    }

    var hasAutoCentered by remember { mutableStateOf(false) }

    val mapView = remember {
        try {
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.ALWAYS)
                controller.setZoom(17.0)
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            MapView(context)
        }
    }

    // Connect MapView to Lifecycle to prevent background and transition crashes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    try { mapView.onResume() } catch (_: Exception) {}
                }
                Lifecycle.Event.ON_PAUSE -> {
                    try { mapView.onPause() } catch (_: Exception) {}
                }
                Lifecycle.Event.ON_DESTROY -> {
                    try { mapView.onDetach() } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try { mapView.onDetach() } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlays.clear()

                val geoPoints = mutableListOf<GeoPoint>()

                // 1. Draw Route from RouteData object
                if (routeData != null) {
                    val startGeo = GeoPoint(routeData.startLat, routeData.startLng)
                    geoPoints.add(startGeo)

                    // Start Point Marker
                    val startMarker = Marker(map).apply {
                        position = startGeo
                        title = "${routeData.startPointType.symbol} ${routeData.fountainName} (${routeData.startPointType.displayName})"
                        snippet = "Başlangıç Koordinatı: ${routeData.startLat}, ${routeData.startLng}"
                        icon = MapIconHelper.createPointTypeDrawable(context, routeData.startPointType)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    map.overlays.add(startMarker)

                    for (pt in routeData.points) {
                        val ptGeo = GeoPoint(pt.lat, pt.lng)
                        geoPoints.add(ptGeo)

                        if (pt.type != PointType.WAYPOINT) {
                            val marker = Marker(map).apply {
                                position = ptGeo
                                title = "${pt.type.symbol} ${pt.type.displayName}"
                                snippet = pt.label ?: pt.type.displayName
                                icon = MapIconHelper.createPointTypeDrawable(context, pt.type)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            map.overlays.add(marker)
                        }
                    }
                } else if (recordedPoints.isNotEmpty()) {
                    // 2. Draw live recorded points during active tracking
                    for ((index, pt) in recordedPoints.withIndex()) {
                        val ptGeo = GeoPoint(pt.lat, pt.lng)
                        geoPoints.add(ptGeo)

                        if (pt.type != PointType.WAYPOINT) {
                            val marker = Marker(map).apply {
                                position = ptGeo
                                title = "${pt.type.symbol} ${pt.type.displayName}"
                                snippet = pt.label ?: "Kritik Nokta"
                                icon = MapIconHelper.createPointTypeDrawable(context, pt.type)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            map.overlays.add(marker)
                        } else if (index == 0) {
                            val marker = Marker(map).apply {
                                position = ptGeo
                                title = "${pt.type.symbol} ${pt.label ?: "Boru Hattı Başlangıcı"}"
                                snippet = "${pt.lat}, ${pt.lng}"
                                icon = MapIconHelper.createPointTypeDrawable(context, pt.type)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            map.overlays.add(marker)
                        }
                    }
                }

                // Draw Blue Polyline for the water pipe
                if (geoPoints.size >= 2) {
                    val polyline = Polyline(map).apply {
                        setPoints(geoPoints)
                        outlinePaint.color = Color.parseColor("#0D47A1")
                        outlinePaint.strokeWidth = 12f
                    }
                    map.overlays.add(polyline)
                }

                // Draw Current User Position Marker ("📍 Siz Buradasınız" - Blue Dot)
                if (currentLocation != null) {
                    val userGeo = GeoPoint(currentLocation.latitude, currentLocation.longitude)
                    val userMarker = Marker(map).apply {
                        position = userGeo
                        title = "📍 SİZ BURADASINIZ"
                        snippet = "Anlık GPS Konumunuz"
                        icon = MapIconHelper.createUserLocationDrawable(context)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    map.overlays.add(userMarker)
                }

                // Kullanıcı gezinirken haritayı zorla merkeze çekme; sadece ilk açılışta odakla
                if (!hasAutoCentered) {
                    val allBoundsPoints = mutableListOf<GeoPoint>()
                    allBoundsPoints.addAll(geoPoints)
                    if (currentLocation != null) {
                        allBoundsPoints.add(GeoPoint(currentLocation.latitude, currentLocation.longitude))
                    }

                    if (allBoundsPoints.size > 1) {
                        try {
                            val bbox = BoundingBox.fromGeoPoints(allBoundsPoints)
                            map.zoomToBoundingBox(bbox.increaseByScale(1.3f), true)
                            hasAutoCentered = true
                        } catch (_: Exception) {
                            map.controller.setCenter(allBoundsPoints.first())
                            hasAutoCentered = true
                        }
                    } else if (allBoundsPoints.size == 1) {
                        map.controller.setCenter(allBoundsPoints.first())
                        hasAutoCentered = true
                    }
                }

                map.invalidate()
            }
        )

        // Sağ Üst Kontrol Butonları: Tam Ekran & Kapat
        if (onToggleFullscreen != null) {
            SmallFloatingActionButton(
                onClick = onToggleFullscreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                containerColor = androidx.compose.ui.graphics.Color.White,
                contentColor = androidx.compose.ui.graphics.Color(0xFF0D47A1),
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen) "Tam Ekrandan Çık" else "Tam Ekran Yap"
                )
            }
        }

        // Sağ Alt Gezinme Butonları: "Konumuma Git" & "Hattı Ekrana Sığdır"
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = if (isFullscreen) 36.dp else 12.dp),
            horizontalAlignment = Alignment.End
        ) {
            // 1. Tüm Boru Hattını Ekrana Sığdır Butonu
            SmallFloatingActionButton(
                onClick = {
                    val allPoints = mutableListOf<GeoPoint>()
                    if (routeData != null) {
                        allPoints.add(GeoPoint(routeData.startLat, routeData.startLng))
                        allPoints.addAll(routeData.points.map { GeoPoint(it.lat, it.lng) })
                    } else if (recordedPoints.isNotEmpty()) {
                        allPoints.addAll(recordedPoints.map { GeoPoint(it.lat, it.lng) })
                    }
                    if (currentLocation != null) {
                        allPoints.add(GeoPoint(currentLocation.latitude, currentLocation.longitude))
                    }

                    if (allPoints.size > 1) {
                        try {
                            val bbox = BoundingBox.fromGeoPoints(allPoints)
                            mapView.zoomToBoundingBox(bbox.increaseByScale(1.35f), true)
                        } catch (_: Exception) {
                            mapView.controller.animateTo(allPoints.first())
                        }
                    } else if (allPoints.isNotEmpty()) {
                        mapView.controller.animateTo(allPoints.first())
                        mapView.controller.setZoom(18.0)
                    }
                },
                containerColor = androidx.compose.ui.graphics.Color.White,
                contentColor = androidx.compose.ui.graphics.Color(0xFF1B5E20),
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Default.CropFree, contentDescription = "Boru Hattını Sığdır")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Anlık Konumuma Git Butonu
            if (currentLocation != null) {
                SmallFloatingActionButton(
                    onClick = {
                        val userGeo = GeoPoint(currentLocation.latitude, currentLocation.longitude)
                        mapView.controller.animateTo(userGeo)
                        mapView.controller.setZoom(18.5)
                    },
                    containerColor = androidx.compose.ui.graphics.Color(0xFF0D47A1),
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Konumuma Git")
                }
            }
        }
    }
}
