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
    private lateinit var mSocket: Socket

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

       
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L  
        ).setMinUpdateIntervalMillis(3000L)
            .build()

        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    // Emit the location to the server via Socket.io
                    emitLocationToServer(location.latitude, location.longitude)
                    Log.d("Location Update", "Lat: ${location.latitude}, Lon: ${location.longitude}")
                }
            }
        }

        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationPermissionRequestCode
            )
        } else {
            startLocationUpdates()
        }

        try {
            
            mSocket = IO.socket("PUT IN YOUR BACKEND IP")
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
            
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {

            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationPermissionRequestCode)
        }
    }

    // Emit 
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
