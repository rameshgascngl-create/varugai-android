package com.gasczoology.varugai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gasczoology.varugai.security.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        RegisterEntity::class,
        StudentEntity::class,
        TeachingDayEntity::class,
        AttendanceMarkEntity::class,
        AuditEventEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class VarugaiDatabase : RoomDatabase() {
    abstract fun registerDao(): RegisterDao
    abstract fun studentDao(): StudentDao
    abstract fun teachingDayDao(): TeachingDayDao
    abstract fun attendanceMarkDao(): AttendanceMarkDao
    abstract fun auditEventDao(): AuditEventDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE students ADD COLUMN attendanceEndDate TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context): VarugaiDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKeyManager().getOrCreatePassphrase()
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                VarugaiDatabase::class.java,
                "varugai.db",
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
