package com.example.data.local

import kotlinx.coroutines.flow.Flow

class WaypointRepository(private val waypointDao: WaypointDao) {
    val allWaypoints: Flow<List<WaypointEntity>> = waypointDao.getAllWaypoints()

    suspend fun saveWaypoint(waypoint: WaypointEntity): Long {
        return waypointDao.insertWaypoint(waypoint)
    }

    suspend fun deleteWaypoint(id: Long) {
        waypointDao.deleteWaypointById(id)
    }
}
