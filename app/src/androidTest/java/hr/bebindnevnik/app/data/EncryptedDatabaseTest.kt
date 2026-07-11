package hr.bebindnevnik.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var file: File

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        System.loadLibrary("sqlcipher")
        file = context.getDatabasePath("instrumented-encrypted.db")
        file.delete()
        database =
            Room
                .databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
                .openHelperFactory(SupportOpenHelperFactory("test-password".toByteArray()))
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
                .build()
    }

    @After fun tearDown() {
        database.close()
        file.delete()
    }

    @Test fun encryptedDatabaseSupportsMealCrudAndDoesNotExposeSQLiteHeader() =
        runBlocking {
            val dao = database.dao()
            dao.putSettings(SettingsEntity())
            val now = System.currentTimeMillis()
            val id = dao.insertMeal(MealEntity(date = "2026-01-02", time = "08:00", amountMl = 80, createdAt = now, updatedAt = now))
            var item = dao.allMeals().single()
            assertEquals(id, item.id)
            item = item.copy(amountMl = 120)
            dao.updateMeal(item)
            assertEquals(120, dao.allMeals().single().amountMl)
            dao.deleteMeal(item)
            assertTrue(dao.allMeals().isEmpty())
            database.openHelper.writableDatabase
            val header =
                file
                    .inputStream()
                    .use { input ->
                        ByteArray(16).also { bytes -> check(input.read(bytes) == bytes.size) }
                    }.toString(Charsets.US_ASCII)
            assertFalse(header.startsWith("SQLite format 3"))
        }

    @Test fun repositoryMaintainsTummyNoSessionInvariantAndSettingsFlow() =
        runBlocking {
            val repository = AppRepository(database)
            repository.initialize()
            val date = java.time.LocalDate.of(2026, 1, 2)
            assertTrue(repository.markNoTummy(date))
            repository.addTummy(date, java.time.LocalTime.NOON, 60, TummyInputMethod.RUCNO)
            assertFalse(repository.summary(date).noTummyTime)
            repository.setStoolCount(date, 0)
            assertEquals(0, repository.summary(date).stoolCount)
            repository.setStoolCount(date, null)
            assertEquals(null, repository.summary(date).stoolCount)
            repository.updateSettings { it.copy(theme = AppTheme.TAMNA) }
            assertEquals(AppTheme.TAMNA, repository.settings.first().theme)
        }

    @Test fun failedReplacementTransactionPreservesExistingData() =
        runBlocking {
            val dao = database.dao()
            dao.putSettings(SettingsEntity())
            val now = System.currentTimeMillis()
            dao.insertMeal(MealEntity(date = "2026-01-02", time = "08:00", amountMl = 80, createdAt = now, updatedAt = now))
            try {
                database.runInTransaction {
                    runBlocking { dao.deleteAllMeals() }
                    error("simulated import failure")
                }
            } catch (_: IllegalStateException) {
            }
            assertEquals(1, dao.allMeals().size)
        }

    @Test fun migrationFromVersionOneAddsNotificationDateWithoutLosingSettings() {
        val name = "migration-1-2.db"
        context.deleteDatabase(name)
        val v1 =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                db.execSQL(
                                    "CREATE TABLE settings (id INTEGER NOT NULL PRIMARY KEY, reminderEnabled INTEGER NOT NULL, reminderTime TEXT NOT NULL, theme TEXT NOT NULL, onboardingShown INTEGER NOT NULL)",
                                )
                                db.execSQL("INSERT INTO settings VALUES (1, 1, '18:00', 'SUSTAV', 1)")
                            }

                            override fun onUpgrade(
                                db: androidx.sqlite.db.SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        v1.writableDatabase
        v1.close()
        val v2 =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(2) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit

                            override fun onUpgrade(
                                db: androidx.sqlite.db.SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) {
                                AppDatabase.MIGRATION_1_2.migrate(db)
                            }
                        },
                    ).build(),
            )
        v2.writableDatabase.query("SELECT reminderTime, lastNotificationDate FROM settings").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("18:00", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        v2.close()
        context.deleteDatabase(name)
    }

    @Test fun migrationFromVersionTwoAddsNullableStoolCountWithoutChangingExistingDay() {
        val name = "migration-2-3.db"
        context.deleteDatabase(name)
        val v2 =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(2) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                db.execSQL(
                                    "CREATE TABLE daily_entries (date TEXT NOT NULL PRIMARY KEY, waya TEXT NOT NULL, exercise TEXT NOT NULL, noTummyTime INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                                )
                                db.execSQL("INSERT INTO daily_entries VALUES ('2026-01-02', 'DA', 'NE', 1, 100, 200)")
                            }

                            override fun onUpgrade(
                                db: androidx.sqlite.db.SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        v2.writableDatabase
        v2.close()
        val v3 =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(3) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit

                            override fun onUpgrade(
                                db: androidx.sqlite.db.SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) {
                                AppDatabase.MIGRATION_2_3.migrate(db)
                            }
                        },
                    ).build(),
            )
        v3.writableDatabase.query("SELECT waya, exercise, noTummyTime, stoolCount FROM daily_entries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("DA", cursor.getString(0))
            assertEquals("NE", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertTrue(cursor.isNull(3))
        }
        v3.close()
        context.deleteDatabase(name)
    }
}
