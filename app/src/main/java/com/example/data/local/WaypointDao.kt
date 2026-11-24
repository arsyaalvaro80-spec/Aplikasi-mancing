package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WaypointDao {
    @Query("SELECT * FROM waypoints ORDER BY createdAt DESC")
    fun getAllWaypoints(): Flow<List<WaypointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoint(waypoint: WaypointEntity): Long

    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun deleteWaypointById(id: Long)
}
