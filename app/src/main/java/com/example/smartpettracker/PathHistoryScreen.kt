package com.example.smartpettracker

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.snapshots.SnapshotStateList
import android.app.DatePickerDialog
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import com.google.firebase.Firebase
import com.google.firebase.database.database
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt


@Composable
fun PathHistoryScreen(
    pathPoints: SnapshotStateList<Pair<LatLng, String>>,
    originalPathRef: DatabaseReference
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val selectedDate = remember { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Athens"))) }
    val currentPathRef = remember(selectedDate.value) {
        Firebase.database.getReference("paths/${Build.MODEL}/${selectedDate.value}")
    }
    val emptyPathState = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        val datePickerDialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                selectedDate.value = LocalDate.of(year, month + 1, dayOfMonth)
                emptyPathState.value = false // reset
            },
            selectedDate.value.year,
            selectedDate.value.monthValue - 1,
            selectedDate.value.dayOfMonth
        )

        Button(
            onClick = { datePickerDialog.show() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF59D))
        ) {
            Text("📅 Select Date: ${selectedDate.value}", color = Color.Black)
        }

        Text("📌 Path History", style = MaterialTheme.typography.headlineSmall, color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))

        if (emptyPathState.value) {
            Text(
                text = "❗ No path points for selected date!",
                color = Color.Red,
                fontSize = 16.sp,
                modifier = Modifier.padding(16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            val distances = remember(pathPoints, emptyPathState.value) {
                pathPoints.zipWithNext { a, b ->
                    calculateDistanceInMeters(
                        a.first.latitude, a.first.longitude,
                        b.first.latitude, b.first.longitude
                    )
                }
            }
            val totalDistance = distances.sum()

            Text(
                text = "🏁 Total Distance: %.2f meters".format(totalDistance),
                color = Color.Black,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            pathPoints.forEachIndexed { index, (location, timestamp) ->
                val distance = if (index == 0) 0.0 else distances.getOrNull(index - 1) ?: 0.0

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        "📍 %.5f, %.5f".format(location.latitude, location.longitude),
                        color = Color.Black
                    )
                    Text("🕒 $timestamp", fontSize = 12.sp, color = Color.DarkGray)
                    Text(
                        "📏 %.2f m from previous".format(distance),
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ☁️ Upload Path
        Button(
            onClick = {
                coroutineScope.launch {
                    val data = pathPoints.map {
                        mapOf(
                            "lat" to it.first.latitude,
                            "lon" to it.first.longitude,
                            "time" to it.second
                        )
                    }
                    currentPathRef.setValue(data)
                    Toast.makeText(context, "✅ Uploaded to Firebase", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC8E6C9))
        ) {
            Text("☁️ Upload Path to Cloud", color = Color.Black)
        }

        // 📥 Load and Save Path
        Button(
            onClick = {
                coroutineScope.launch {
                    currentPathRef.get()
                        .addOnSuccessListener { snapshot ->
                            if (!snapshot.exists() || !snapshot.hasChildren()) {
                                emptyPathState.value = true
                                pathPoints.clear()
                                Toast.makeText(
                                    context,
                                    "⚠️ No path data for ${selectedDate.value}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@addOnSuccessListener
                            }

                            val downloaded = snapshot.children.mapNotNull { child ->
                                val lat = child.child("lat").getValue(Double::class.java)
                                val lon = child.child("lon").getValue(Double::class.java)
                                val time = child.child("time").getValue(String::class.java)
                                if (lat != null && lon != null && time != null) {
                                    LatLng(lat, lon) to time
                                } else null
                            }

                            pathPoints.clear()
                            pathPoints.addAll(downloaded)

                            val downloadsDir = context.getExternalFilesDir(null)
                            if (downloadsDir != null) {
                                val fileName = "SmartPetPath_${selectedDate.value}.csv"
                                val file = java.io.File(downloadsDir, fileName)

                                file.bufferedWriter().use { writer ->
                                    writer.write("Latitude,Longitude,Timestamp\n")
                                    downloaded.forEach { (loc, time) ->
                                        writer.write("${loc.latitude},${loc.longitude},$time\n")
                                    }
                                }
                                Toast.makeText(
                                    context,
                                    "✅ Downloaded & Saved: ${file.absolutePath}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "⚠️ Could not access storage",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "❌ Download failed", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1F5FE))
        ) {
            Text("📥 Load and Save Path", color = Color.Black)
        }

        // 📤 Export Path to CSV
        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        val downloadsDir = context.getExternalFilesDir(null) ?: return@launch
                        val fileName = "SmartPetPath_${selectedDate.value}.csv"
                        val file = java.io.File(downloadsDir, fileName)

                        val distances = pathPoints.zipWithNext { a, b ->
                            calculateDistanceInMeters(
                                a.first.latitude, a.first.longitude,
                                b.first.latitude, b.first.longitude
                            )
                        }

                        file.bufferedWriter().use { writer ->
                            writer.write("Latitude,Longitude,Timestamp,DistanceFromPrevious\n")
                            pathPoints.forEachIndexed { index, (location, timestamp) ->
                                val distance =
                                    if (index == 0) 0.0 else distances.getOrNull(index - 1) ?: 0.0
                                writer.write(
                                    "${location.latitude},${location.longitude},$timestamp,${
                                        "%.2f".format(
                                            distance
                                        )
                                    }\n"
                                )
                            }
                        }
                        Toast.makeText(
                            context,
                            "✅ Exported to ${file.absolutePath}",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "❌ Export failed: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB2EBF2))
        ) {
            Text("📤 Export Path to CSV", color = Color.Black)
        }

        // 🧹 Clear Path
        Button(
            onClick = { pathPoints.clear() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCDD2))
        ) {
            Text("🧹 Clear Path", color = Color.Black)
        }
    }
}


fun calculateDistanceInMeters(
    lat1: Double, lon1: Double,
    lat2: Double, lon2: Double
): Double {
    val earthRadius = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val rLat1 = Math.toRadians(lat1)
    val rLat2 = Math.toRadians(lat2)

    val a = sin(dLat / 2).pow(2.0) + cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2.0)
    val c = 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))

    return earthRadius * c
}
