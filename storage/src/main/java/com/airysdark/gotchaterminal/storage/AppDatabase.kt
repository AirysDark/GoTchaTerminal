package com.airysdark.gotchaterminal.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.airysdark.gotchaterminal.storage.dao.CaptureDao
import com.airysdark.gotchaterminal.storage.dao.DeviceDao
import com.airysdark.gotchaterminal.storage.entities.*

@Database(
    entities = [
        AdvertisementCapture::class,
        PacketCapture::class,
        SessionCapture::class,
        ChallengeCapture::class,
        DeviceEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao
    abstract fun deviceDao(): DeviceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gotcha_terminal_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
