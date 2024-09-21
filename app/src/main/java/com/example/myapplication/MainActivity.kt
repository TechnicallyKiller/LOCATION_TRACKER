package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private val locationPermissionRequestCode = 1001

    // Initialize the Socket.io client
    private lateinit var mSocket: Socket

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize FusedLocationProviderClient
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // Create location request
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, // Use the correct priority
            5000L  // Interval in milliseconds
        ).setMinUpdateIntervalMillis(3000L)  // Fastest interval
            .build()

        // Create location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    // Emit the location to the server via Socket.io
                    emitLocationToServer(location.latitude, location.longitude)
                    Log.d("Location Update", "Lat: ${location.latitude}, Lon: ${location.longitude}")
                }
            }
        }

        // Check for location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // Request location permissions if not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationPermissionRequestCode
            )
        } else {
            // Permissions already granted - start location updates
            startLocationUpdates()
        }

        // Connect to the Socket.io server
        try {
            // Replace with your backend server URL
            mSocket = IO.socket("http://122.173.31.20") // Replace with actual IP or domain
            mSocket.connect()
            Log.d("SocketIO", "Connected to server")
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    // Start requesting location updates
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            // Permission is granted, start location updates
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {
            // Permission is not granted, request permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationPermissionRequestCode)
        }
    }

    // Emit location data to the Socket.io server
    private fun emitLocationToServer(latitude: Double, longitude: Double) {
        if (this::mSocket.isInitialized && mSocket.connected()) {
            val locationData = JSONObject()
            locationData.put("latitude", latitude)
            locationData.put("longitude", longitude)

            mSocket.emit("locationUpdate", locationData)
            Log.d("SocketIO", "Location data emitted: Lat=$latitude, Lon=$longitude")
        } else {
            Log.e("SocketIO", "Socket not connected, unable to emit location")
        }
    }

    // Handle permission request result
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted - start location updates
                startLocationUpdates()
            } else {
                // Permission denied - Show a message to the user
                Toast.makeText(this, "Location permission is required to get updates", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Optional: Stop location updates to save battery
    override fun onPause() {
        super.onPause()
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Close socket connection when the activity is destroyed
        if (this::mSocket.isInitialized && mSocket.connected()) {
            mSocket.disconnect()
        }
    }
}
