package com.programmingtask.hospitalroutingappk

import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var fuseLocationClient: FusedLocationProviderClient
    private lateinit var tvLocation: TextView
    private lateinit var map: GoogleMap
    private lateinit var myPosition:  LatLng
    private lateinit var btnCalculate: Button
    var poly: Polyline?=null

    // List of InkaPharmacy locations
    private var inkaPharmaLocations = listOf(
        LatLng(-12.004366670552542, -76.84398622278493),
        LatLng(-12.006299719825705, -76.85390711704667),
        LatLng(-11.997196472800125, -76.83620349986236),
        LatLng(-12.010688716451163, -76.85105782520363),
        LatLng(-12.011336604138467, -76.82178851368194),
        LatLng(-12.015507343888109, -76.81756578419936)
    )

    // Name of InkaPharma locations
    private var inkaPharmaName = listOf(
        "Sin, Santa Cruz expansion, Ca. 1 Lt - 42, Lima",
        "MZA. E LOT. 04, II",
        "NICOLAS AYLLON Avenue MZA. M LOT. 2-B, Lima fence",
        "X4QW+FR4, Gloria Avenue, Lima 15476",
        "25, Ate 15479",
        "X5MJ+M38, José Carlos Mariátegui Avenue, Ate 15483"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        btnCalculate = findViewById(R.id.btnCalculateRoute)
        btnCalculate.setOnClickListener {
            // Clean the routes
            poly?.remove()
            poly = null
            // Start calculating the nearest route
            findNearestInkaPharmaRoute()
        }
        fuseLocationClient = LocationServices.getFusedLocationProviderClient(this)
        tvLocation = findViewById(R.id.tvLocation)
        getCurrentLocation()
        createMapFragment()
    }

    private fun getCurrentLocation() {
        // Check permissions
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // If the permits have not been granted upon request
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        // We want to get the location
        fuseLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                // Show the location in a Toast or in a control
                val latitude = location.latitude
                val longitude = location.longitude
                val message = "Latitude: $latitude \n Longitude: $longitude"
                tvLocation.text = message
                this.myPosition = LatLng(latitude, longitude)
                createMarker()
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            }else{
                // Could not get location
                Toast.makeText(applicationContext, "Could not get location", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Handle the permission request response
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode==1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            // If permission have been granted, call again to obtain the location
            getCurrentLocation()
        }
    }

    override fun onMapReady(p0: GoogleMap) {
        this.map = p0

        // Add listener for clicks on markers
        this.map.setOnMarkerClickListener { marker ->
            // Search the InkaFarma marker index by clicking
            for (i in inkaPharmaLocations.indices) {
                if (marker.position == inkaPharmaLocations[i]) {
                    // We found the InkaFarma marker by clicking
                    showRouteToInkaFarma(i)
                    return@setOnMarkerClickListener true
                }
            }
            false // If your are not an InkaFarma marker, allow default behavior
        }
    }

    private fun createMapFragment(){
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun createMarker(){
        val myPos = this.myPosition

        this.map.addMarker(MarkerOptions().position(myPos).title("YO").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))

        for((index, inkaPharmaLng) in inkaPharmaLocations.withIndex()){
            this.map.addMarker(MarkerOptions().position(inkaPharmaLng).title(inkaPharmaName[index]))
        }

        this.map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(myPos, 12f), 4000, null
        )
    }

    // Function to display route to specific InkaFarma
    private fun showRouteToInkaFarma(position: Int) {
        if(!::myPosition.isInitialized){
            Toast.makeText(applicationContext, "Your location is not available yet, please try again.", Toast.LENGTH_LONG).show()
            return
        }

        // Clear previous route
        poly?.remove()
        poly = null

        CoroutineScope(Dispatchers.IO).launch {
            val inkaPharmaLng = inkaPharmaLocations[position]
            val inkaPharmaNameSelected = inkaPharmaName[position]
            val apiService = getRetrofit().create(ApiService::class.java)

            val currentStart = "${myPosition.longitude}, ${myPosition.latitude}"
            val currentEnd = "${inkaPharmaLng.longitude}, ${inkaPharmaLng.latitude}"

            try{
                val call = apiService.getRoute(getString(R.string.google_maps_key), currentStart, currentEnd)
                if(call.isSuccessful){
                    val routeResponse = call.body()
                    val routeDistance = routeResponse?.features?.firstOrNull()?.properties?.summary?.distance?:0.0
                    val routeCoordinates = routeResponse?.features?.firstOrNull()?.geometry?.coordinates

                    if(routeCoordinates != null){
                        drawRoute(routeCoordinates)
                        runOnUiThread{
                            val message = "Ruta a: $inkaPharmaNameSelected (${String.format("%.2f",routeDistance/1000)} km)"
                            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }else{
                    val errorBody = call.errorBody()?.string()
                    Log.e("MainActivity", "Error getting path to $inkaPharmaNameSelected: $errorBody")
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Error al obtener la ruta: $errorBody", Toast.LENGTH_LONG).show()
                    }
                }
            }catch (e: Exception){
                Log.e("MainActivity", "Exception getting path to $inkaPharmaNameSelected")
            }
        }
    }

    private fun findNearestInkaPharmaRoute(){
        if(!::myPosition.isInitialized){
            Toast.makeText(applicationContext, "Your location is not available yet, please try again.", Toast.LENGTH_LONG).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            var shortDistance: Double = Double.MAX_VALUE
            var bestRouteCoordinates : List<List<Double>>? = null
            var nearestInkaPharmaName : String? = null
            val apiService = getRetrofit().create(ApiService::class.java)

            // Find the closest route from my position to an inkaPharma point
            for((index, inkaPharmaLng) in inkaPharmaLocations.withIndex()){
                val currentStart = "${myPosition.longitude}, ${myPosition.latitude}"
                val currentEnd = "${inkaPharmaLng.longitude},${inkaPharmaLng.latitude}"
                try{
                    val call = apiService.getRoute(getString(R.string.google_maps_key), currentStart, currentEnd)
                    if(call.isSuccessful){
                        val routeResponse = call.body()
                        val routeDistance = routeResponse?.features?.firstOrNull()?.properties?.summary?.distance?:0.0
                        if(routeDistance<shortDistance){
                            shortDistance = routeDistance
                            bestRouteCoordinates = routeResponse?.features?.firstOrNull()?.geometry?.coordinates
                            nearestInkaPharmaName = inkaPharmaName[index]
                        }
                    }else{
                        Log.e("MainActivity", "Error getting path to")
                    }
                }catch (e: Exception){
                    Log.e("MainActivity", "Exception getting path to")
                }
            }

            if(bestRouteCoordinates!= null){
                drawRoute(bestRouteCoordinates)
                runOnUiThread{
                    val message = "Route to the nearest inkaPharma: $nearestInkaPharmaName (${String.format("%.2f",shortDistance/1000)} km)"
                    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getRetrofit(): Retrofit{
        return Retrofit.Builder().baseUrl("https://api.openrouteservice.org/")
            .addConverterFactory(GsonConverterFactory.create()).build()
    }

    // We modified drawRoute to accept the list of coordinates directly
    private fun drawRoute(coordinates: List<List<Double>>?){
        val polyLineOption = PolylineOptions()
            .width(10f) // line width
            .color(Color.BLUE) // line color
            .jointType(JointType.ROUND) // the joints are going to be rounded
            .startCap(RoundCap()) // rounded start cap
            .endCap(RoundCap()) // rounded end cap

        coordinates?.forEach{
            polyLineOption.add(LatLng(it[1],it[0]))
        }

        runOnUiThread {
            poly = map.addPolyline(polyLineOption)
        }
    }
}