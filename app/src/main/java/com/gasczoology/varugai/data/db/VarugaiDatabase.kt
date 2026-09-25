package com.gasczoology.varugai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gasczoology.varugai.security.DatabaseKeyManager
import com.gasczoology.varugai.security.DatabaseKeyUnavailableException
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class DatabaseRecoveryRequiredException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

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
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE students ADD COLUMN attendanceEndDate TEXT NOT NULL DEFAULT ''")
            }
        }

        const val DATABASE_NAME = "varugai.db"

        fun create(context: Context): VarugaiDatabase {
            System.loadLibrary("sqlcipher")
            val databaseExists = context.applicationContext.getDatabasePath(DATABASE_NAME).exists()
            val passphrase = DatabaseKeyManager().passphraseFor(databaseExists)
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                VarugaiDatabase::class.java,
                DATABASE_NAME,
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                .build()
        }

        fun createAndVerify(context: Context): VarugaiDatabase {
            val database = try {
                create(context)
            } catch (e: DatabaseKeyUnavailableException) {
                throw DatabaseRecoveryRequiredException(
                    e.message ?: "Database encryption key is unavailable.",
                    e,
                )
            }
            return try {
                database.openHelper.writableDatabase
                database
            } catch (t: Throwable) {
                database.close()
                throw DatabaseRecoveryRequiredException(
                    "The encrypted attendance database cannot be opened with the key available on this device.",
                    t,
                )
            }
        }
    }
}
