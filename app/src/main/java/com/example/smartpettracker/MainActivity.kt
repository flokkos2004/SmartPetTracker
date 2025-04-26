package com.example.smartpettracker

import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import android.os.Bundle
import android.os.Looper
import android.Manifest
import androidx.compose.material.icons.filled.Pets
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import android.content.pm.PackageManager
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.provider.Settings
import android.bluetooth.BluetoothGatt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import android.location.LocationManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.navigation.compose.*
import androidx.compose.material.icons.Icons
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.core.net.toUri
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.google.android.gms.location.Priority
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.navigation.NavController
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import com.example.smartpettracker.ui.theme.SmartPetTrackerTheme
import com.google.firebase.Firebase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.database
import java.util.Locale


val LocalMuteAlerts = compositionLocalOf { mutableStateOf(false) }
val LocalGeofenceRadius = compositionLocalOf { mutableDoubleStateOf(100.0) } // default 100m

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NotificationUtils.createNotificationChannel(this)
            SmartPetTrackerTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val muteAlerts = remember { mutableStateOf(false) }
    val geofenceRadius = remember { mutableDoubleStateOf(100.0) }

    LaunchedEffect(Unit) {
        SettingsDataStore.getMuteAlerts(context).collect {
            muteAlerts.value = it
        }
    }
    LaunchedEffect(Unit) {
        SettingsDataStore.getRadius(context).collect {
            geofenceRadius.doubleValue = it
        }
    }

    CompositionLocalProvider(
        LocalMuteAlerts provides muteAlerts,
        LocalGeofenceRadius provides geofenceRadius
    ) {
        AppNavigation()
    }
}


@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val items = listOf("home", "map", "settings")

    val pathPoints = remember { mutableStateListOf<Pair<LatLng, String>>() }
    val today = LocalDate.now(ZoneId.of("Europe/Athens")).toString()
    val pathRef = Firebase.database.getReference("paths/${Build.MODEL}/$today")
    val selectedDate = remember { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Athens"))) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            when (screen) {
                                "home" -> Icon(Icons.Filled.Home, contentDescription = "Home")
                                "map" -> Icon(Icons.Filled.Place, contentDescription = "Map")
                                "settings" -> Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "Settings"
                                )
                            }
                        },
                        label = { Text(screen.replaceFirstChar { it.uppercase() }) },
                        selected = navController.currentBackStackEntryAsState().value?.destination?.route == screen,
                        onClick = {
                            navController.navigate(screen) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") { HomeScreen() }
            composable("map") {
                MapScreen(
                    pathPoints = pathPoints,
                    navController = navController,
                    selectedDate = selectedDate
                )
            }
            composable("settings") { SettingsScreen(navController = navController) }
            composable("path_history") {
                PathHistoryScreen(pathPoints = pathPoints, originalPathRef = pathRef)
            }
            composable("pick_home_location") { PickHomeLocationScreen(navController) }


        }
    }
}


@Composable
fun HomeScreen() {
    val context = LocalContext.current

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val isBluetoothEnabled = bluetoothManager.adapter?.isEnabled == true

    val showPrompt = !isLocationEnabled || !isBluetoothEnabled

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { /* no-op */ }
    val bluetoothSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { /* no-op */ }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFDFBF5), Color(0xFFE0F2F1))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Pets,
                contentDescription = "Pet Logo",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = "Welcome to SmartPet Tracker!",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                color = Color(0xFF333333)
            )

            if (showPrompt) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F6E9)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "To use SmartPet Tracker, please enable:",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        if (!isBluetoothEnabled) {
                            Text("• Bluetooth", color = Color(0xFF4CAF50))
                        }
                        if (!isLocationEnabled) {
                            Text("• Location (GPS)", color = Color(0xFF4CAF50))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (!isBluetoothEnabled) {
                                Button(
                                    onClick = {
                                        bluetoothSettingsLauncher.launch(
                                            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(
                                            0xFFC8E6C9
                                        )
                                    ),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text("Bluetooth", color = Color(0xFF1B5E20))
                                }
                            }
                            if (!isLocationEnabled) {
                                Button(
                                    onClick = {
                                        locationSettingsLauncher.launch(
                                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(
                                            0xFFC8E6C9
                                        )
                                    ),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text("Location", color = Color(0xFF1B5E20))
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !showPrompt,
                enter = fadeIn()
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9E7)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.catsanddogs),
                        contentDescription = "Happy Pets",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                    )
                }
            }

            // 5️⃣ SmartTag note
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Note",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = "Important note ℹ️! SmartTag's location is based on your phone's location.",
                        color = Color(0xFF6D4C41),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
suspend fun getCurrentLocation(context: Context): Location? {
    val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    return suspendCancellableCoroutine { cont ->
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                cont.resume(location)
            }
            .addOnFailureListener {
                cont.resume(null)
            }
    }
}

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val muteAlertsState = LocalMuteAlerts.current
    val geofenceRadiusState = LocalGeofenceRadius.current

    var lastKnownUserLocation by remember { mutableStateOf<LatLng?>(null) }

    val permissionGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val homeLocationState = produceState<LatLng?>(initialValue = null, context) {
        SettingsDataStore.getHomeLocation(context).collect { value = it }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    lastKnownUserLocation = LatLng(location.latitude, location.longitude)
                }
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Adjust the application preferences.")
        Spacer(modifier = Modifier.height(16.dp))

        // Mute toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mute alerts")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = muteAlertsState.value,
                onCheckedChange = {
                    muteAlertsState.value = it
                    coroutineScope.launch {
                        SettingsDataStore.setMuteAlerts(context, it)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Geofence radius
        Text("Geofence Radius: ${geofenceRadiusState.doubleValue.toInt()} meters")
        Slider(
            value = geofenceRadiusState.doubleValue.toFloat(),
            onValueChange = {
                geofenceRadiusState.doubleValue = it.toDouble()
                coroutineScope.launch {
                    SettingsDataStore.setRadius(context, it.toDouble())
                }
            },
            valueRange = 50f..500f,
            steps = 8
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = if (homeLocationState.value != null) "🏡 Home Location is set ✅" else "🏡 Home Location not set ⚠️",
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )

        homeLocationState.value?.let { home ->
            Text(
                text = "Latitude: %.5f\nLongitude: %.5f".format(home.latitude, home.longitude),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 📍 Set Current Location as Home
        Button(
            onClick = {
                if (lastKnownUserLocation != null) {
                    coroutineScope.launch {
                        SettingsDataStore.setHomeLocation(context, lastKnownUserLocation!!)
                        Toast.makeText(
                            context,
                            "✅ Home Location set successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        context,
                        "⚠️ Current location not available.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBBDEFB))
        ) {
            Text("📍 Set Current Location as Home")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 🗑️ Clear Home Location
        Button(
            onClick = {
                coroutineScope.launch {
                    SettingsDataStore.clearHomeLocation(context)
                    Toast.makeText(context, "✅ Home Location cleared!", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCDD2))
        ) {
            Text("🗑️ Clear Home Location")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 🗺️ Pick Home Location manually
        Button(
            onClick = {
                navController.navigate("pick_home_location")
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC8E6C9))
        ) {
            Text("🗺️ Pick Home Location on Map")
        }
    }
}

fun estimateDistance(rssi: Int, txPower: Int = -59): Double {
    //val ratio = rssi.toDouble() / txPower
    return 10.0.pow((txPower - rssi) / 20.0)
}

@Composable
fun MapScreen(
    pathPoints: SnapshotStateList<Pair<LatLng, String>>,
    navController: NavController,
    selectedDate: State<LocalDate>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val cameraPositionState = rememberCameraPositionState()
    val geofenceCenter = remember { mutableStateOf(LatLng(35.1856, 33.3823)) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var permissionGranted by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val bleDevices = remember { mutableStateListOf<ScanResult>() }
    val lastKnownLocation = remember { mutableStateOf<LatLng?>(null) }
    val muteAlertsState = LocalMuteAlerts.current
    val radius = LocalGeofenceRadius.current.doubleValue

    val homeLocation = produceState<LatLng?>(initialValue = null, context) {
        SettingsDataStore.getHomeLocation(context).collect {
            value = it
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }
    // Request permission
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        else permissionGranted = true
    }

    // Load geofence
    LaunchedEffect(Unit) {
        SettingsDataStore.getGeofenceCenter(context).collect {
            geofenceCenter.value = it
        }
    }

    // Load last known SmartTag location
    LaunchedEffect(Unit) {
        SettingsDataStore.getLastKnownLocation(context).collect {
            lastKnownLocation.value = it
        }
    }

    // Location updates
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            val locationRequest = LocationRequest.Builder(3000L)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY).build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    val latLng = LatLng(loc.latitude, loc.longitude)
                    userLocation = latLng

                    if (connected) {
                        val lastPoint = pathPoints.lastOrNull()?.first
                        if (lastPoint == null || calculateDistanceInMeters(
                                lastPoint.latitude, lastPoint.longitude,
                                latLng.latitude, latLng.longitude
                            ) > 2.0
                        ) {
                            val zoneId = ZoneId.of("Europe/Athens")
                            val formatter =
                                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(zoneId)
                            val time = formatter.format(Instant.now())
                            pathPoints.add(latLng to time)
                        }
                    }
                    userLocation = latLng
                }
            }


            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    // BLE scan logic
    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val rssi = result.rssi
                val name = try {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                        result.device.name ?: "Unknown"
                    else "Unknown"
                } catch (e: SecurityException) {
                    "Unknown"
                }

                val index = bleDevices.indexOfFirst { it.device.address == result.device.address }
                if (index == -1) bleDevices.add(result) else bleDevices[index] = result

                if (name == "Unknown" && rssi > -30 && !connected) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        connected = true // block other devices from connecting
                        result.device.connectGatt(context, false, object : BluetoothGattCallback() {
                            override fun onConnectionStateChange(
                                gatt: BluetoothGatt,
                                status: Int,
                                newState: Int
                            ) {
                                if (newState == BluetoothProfile.STATE_CONNECTED) {
                                    Toast.makeText(
                                        context,
                                        "✅ SmartTag connected!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.BLUETOOTH_CONNECT
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        gatt.discoverServices()
                                    }
                                    userLocation?.let { currentLoc ->
                                        val zoneId = ZoneId.of("Europe/Athens")
                                        val formatter =
                                            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                                                .withZone(zoneId)
                                        val time = formatter.format(Instant.now())
                                        pathPoints.clear()
                                        pathPoints.add(currentLoc to time)
                                    }
                                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                    connected = false
                                    Toast.makeText(
                                        context,
                                        "❌ SmartTag disconnected",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    gatt.close()
                                    userLocation?.let {
                                        coroutineScope.launch {
                                            SettingsDataStore.saveLastKnownLocation(context, it)
                                        }
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }
    }

    // Start BLE scan
    LaunchedEffect(permissionGranted) {
        if (permissionGranted && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            val bleScanner = bluetoothAdapter.bluetoothLeScanner
            while (true) {
                bleScanner.startScan(scanCallback)
                delay(4000L)
                bleScanner.stopScan(scanCallback)
                delay(6000L)
            }
        }
    }

    val isOutsideFence = userLocation?.let {
        calculateDistanceInMeters(
            geofenceCenter.value.latitude,
            geofenceCenter.value.longitude,
            it.latitude,
            it.longitude
        ) > radius
    } == true


    var wasOutsideFence by remember { mutableStateOf(false) }
    var firstCheckDone by remember { mutableStateOf(false) }

    LaunchedEffect(userLocation) {
        if (userLocation != null) {
            val distance = calculateDistanceInMeters(
                geofenceCenter.value.latitude,
                geofenceCenter.value.longitude,
                userLocation!!.latitude,
                userLocation!!.longitude
            )
            val outsideFenceNow = distance > radius

            if (!firstCheckDone) {
                wasOutsideFence = outsideFenceNow
                firstCheckDone = true
            } else {
                if (outsideFenceNow && !wasOutsideFence && !muteAlertsState.value) {
                    NotificationUtils.showGeofenceExitNotification(context, "Pet exited the zone!")
                    showDialog = true
                    wasOutsideFence = true
                } else if (!outsideFenceNow && wasOutsideFence) {
                    NotificationUtils.showGeofenceExitNotification(
                        context,
                        "Pet re-entered the safe zone!"
                    )
                    showDialog = false
                    wasOutsideFence = false
                }
            }
        }
    }

    // UI
    Column {
        if (connected) {
            Text(
                text = "🟢 SmartTag connected",
                color = Color(0xFF1B5E20),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFD7FAD9))
                    .padding(vertical = 8.dp)
            )
        }
        GoogleMap(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = permissionGranted),
            onMapLongClick = {
                geofenceCenter.value = it
                coroutineScope.launch { SettingsDataStore.setGeofenceCenter(context, it) }
            }
        ) {
            if (pathPoints.isNotEmpty()) {
                Marker(
                    state = MarkerState(pathPoints.first().first),
                    title = "Start Point",
                    snippet = pathPoints.first().second
                )
                Marker(
                    state = MarkerState(pathPoints.last().first),
                    title = "End Point",
                    snippet = pathPoints.last().second
                )
                if (pathPoints.size > 2) {
                    val middle = pathPoints.size / 2
                    Marker(
                        state = MarkerState(pathPoints[middle].first),
                        title = "Mid Point",
                        snippet = pathPoints[middle].second
                    )
                }
            }

            userLocation?.let {
                Marker(state = MarkerState(it), title = "Pet")
            }
            Circle(
                center = geofenceCenter.value,
                radius = radius,
                fillColor = Color(0x33FF0000),
                strokeColor = Color.Red,
                strokeWidth = 3f
            )
            val today = LocalDate.now(ZoneId.of("Europe/Athens"))
            val polylineColor = if (selectedDate.value == today) Color.Blue else Color.Green

            val smoothedPoints = smoothPath(pathPoints)


            if (pathPoints.size > 1) {
                Polyline(
                    points = smoothedPoints.map { it.first },
                    color = polylineColor,
                    width = 5f
                )
            }
        }
        Button(
            onClick = { navController.navigate("path_history") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF8BBD0))
        ) {
            Text("📂 View Path History")
        }
        Button(
            onClick = {
                homeLocation.value?.let { home ->
                    geofenceCenter.value = home
                    coroutineScope.launch {
                        SettingsDataStore.setGeofenceCenter(context, home)
                    }
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(home, 16f))
                    Toast.makeText(context, "🏡 Moved Geofence to Home", Toast.LENGTH_SHORT).show()
                } ?: Toast.makeText(context, "⚠️ No Home Location set", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC8E6C9))
        ) {
            Text("🏡 Move Geofence to Home")
        }


        homeLocation.value?.let { home ->
            Text(
                text = "🏡 Home Location: %.5f, %.5f".format(home.latitude, home.longitude),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        } ?: Text(
            text = "🏡 Home Location: Not Set",
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )



        if (!connected && lastKnownLocation.value != null) {
            Button(
                onClick = {
                    val lat = lastKnownLocation.value!!.latitude
                    val lon = lastKnownLocation.value!!.longitude
                    val uri = "google.navigation:q=$lat,$lon".toUri()
                    val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    context.startActivity(mapIntent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBBDEFB)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("📍 Navigate to last location")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 300.dp)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()) // ✅ Scroll active
        ) {
            bleDevices.sortedByDescending { it.rssi }.forEach { device ->
                val name = device.device.name ?: "Unknown"
                val signal = when {
                    device.rssi >= -60 -> "📶📶📶📶📶"
                    device.rssi >= -70 -> "📶📶📶📶"
                    device.rssi >= -80 -> "📶📶📶"
                    device.rssi >= -90 -> "📶📶"
                    else -> "📶"
                }
                val dist = "~%.2f m".format(estimateDistance(device.rssi))
                val isCandidate = name == "Unknown" && device.rssi > -30

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(if (isCandidate) Color(0xFFE8F5E9) else Color.Transparent)
                        .clip(RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (isCandidate) "🐾 Possible SmartTag2 – $name" else "🐾 $name",
                        fontWeight = if (isCandidate) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    Text("$signal – $dist")
                }
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Alert") },
                text = { Text("Your pet has exited the geofence zone.") },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) { Text("OK") }
                }
            )
        }
    }

}


fun downloadPathFromFirebase(
    context: Context,
    pathRef: DatabaseReference,
    onPathLoaded: (List<Pair<LatLng, String>>) -> Unit
) {
    pathRef.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (!snapshot.exists()) {
                Toast.makeText(context, "⚠️ No path data found", Toast.LENGTH_SHORT).show()
                return
            }

            val list = snapshot.children.mapNotNull { child ->
                val lat = child.child("lat").getValue(Double::class.java)
                val lon = child.child("lon").getValue(Double::class.java)
                val time = child.child("time").getValue(String::class.java)
                if (lat != null && lon != null && time != null) {
                    LatLng(lat, lon) to time
                } else null
            }

            onPathLoaded(list)
        }

        override fun onCancelled(error: DatabaseError) {
            Toast.makeText(context, "❌ Failed to load path", Toast.LENGTH_SHORT).show()
        }
    })
}


fun smoothPath(
    points: List<Pair<LatLng, String>>,
    windowSize: Int = 5
): List<Pair<LatLng, String>> {
    if (points.size <= windowSize) return points

    val smoothed = mutableListOf<Pair<LatLng, String>>()
    for (i in points.indices) {
        val start = maxOf(0, i - windowSize / 2)
        val end = minOf(points.size - 1, i + windowSize / 2)

        val latAvg = points.subList(start, end + 1).map { it.first.latitude }.average()
        val lonAvg = points.subList(start, end + 1).map { it.first.longitude }.average()
        val timestamp = points[i].second

        smoothed.add(LatLng(latAvg, lonAvg) to timestamp)
    }
    return smoothed
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickHomeLocationScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedLocation by remember { mutableStateOf<LatLng?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val cameraPositionState = rememberCameraPositionState()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pick Home Location 📍") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val location = getCurrentLocation(context)
                            if (location != null) {
                                val latLng = LatLng(location.latitude, location.longitude)
                                selectedLocation = latLng
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        latLng,
                                        16f
                                    )
                                )
                                Toast.makeText(
                                    context,
                                    "📍 Current location found!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "⚠️ Unable to get current location",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Place, contentDescription = "Find my location")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search address...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        coroutineScope.launch {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            try {
                                val addresses = geocoder.getFromLocationName(searchQuery, 1)
                                if (!addresses.isNullOrEmpty()) {
                                    val address = addresses[0]
                                    val latLng = LatLng(address.latitude, address.longitude)
                                    selectedLocation = latLng
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(latLng, 16f)
                                    )
                                } else {
                                    Toast.makeText(
                                        context,
                                        "⚠️ Address not found",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "❌ Error searching address",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            // Google Map
            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                cameraPositionState = cameraPositionState,
                onMapClick = { latLng ->
                    selectedLocation = latLng
                }
            ) {
                selectedLocation?.let { loc ->
                    Marker(
                        state = MarkerState(position = loc),
                        title = "Selected Location"
                    )
                }
            }

            // Save Button
            Button(
                onClick = {
                    if (selectedLocation != null) {
                        coroutineScope.launch {
                            SettingsDataStore.setHomeLocation(context, selectedLocation!!)
                            Toast.makeText(context, "✅ Home location saved!", Toast.LENGTH_SHORT)
                                .show()
                            navController.popBackStack()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "⚠️ Please select a location first.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("✅ Save Home Location")
            }
        }
    }
}
