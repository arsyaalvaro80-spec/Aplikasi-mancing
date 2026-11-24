package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waypoints")
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val depthMeters: Double,
    val targetFish: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
