package com.internetspeed.meterlite.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.internetspeed.meterlite.data.dao.UsageDao
import com.internetspeed.meterlite.data.entity.DailyUsage

@Database(entities = [DailyUsage::class], version = 1, exportSchema = false)
abstract class UsageDatabase : RoomDatabase() {

    abstract fun usageDao(): UsageDao

    companion object {
        @Volatile
        private var INSTANCE: UsageDatabase? = null

        fun getDatabase(context: Context): UsageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UsageDatabase::class.java,
                    "usage_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
