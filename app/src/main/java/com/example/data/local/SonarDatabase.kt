package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WaypointEntity::class], version = 1, exportSchema = false)
abstract class SonarDatabase : RoomDatabase() {
    abstract fun waypointDao(): WaypointDao

    companion object {
        @Volatile
        private var instance: SonarDatabase? = null

        fun getInstance(context: Context): SonarDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SonarDatabase::class.java,
                    "bintan_sonar.db"
                ).build().also { instance = it }
            }
        }
    }
}
