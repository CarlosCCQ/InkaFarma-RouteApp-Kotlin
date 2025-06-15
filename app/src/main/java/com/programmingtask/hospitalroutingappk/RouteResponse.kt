package com.programmingtask.hospitalroutingappk

import com.google.gson.annotations.SerializedName

data class RouteResponse(@SerializedName("feature") val features: List<Feature>)
data class Feature(
    @SerializedName("geometry") val geometry: Geometry,
    @SerializedName("properties") val properties: Properties
)

data class Geometry(@SerializedName("coordinates") val coordinates: List<List<Double>>)
data class Properties(@SerializedName("summary") val summary: Summary)
data class Summary(@SerializedName("distance") val distance: Double)