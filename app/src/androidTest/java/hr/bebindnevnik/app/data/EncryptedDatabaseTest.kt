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
                .addMigrations(
                    AppDatabase.MIGRATION_1_2,
                    AppDatabase.MIGRATION_2_3,
                    AppDatabase.MIGRATION_3_4,
                    AppDatabase.MIGRATION_4_5,
                    AppDatabase.MIGRATION_5_6,
                ).build()
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
            var changed = 0
            val repository = AppRepository(database) { changed++ }
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
            assertTrue(changed >= 5)
        }

    @Test fun repositoryKeepsMultipleTimerSessionsAndRecalculatesAfterEditAndDelete() =
        runBlocking {
            val repository = AppRepository(database)
            repository.initialize()
            val date = java.time.LocalDate.of(2026, 7, 12)
            val first = repository.addTummy(date, java.time.LocalTime.of(9, 0), 260, TummyInputMethod.STOPERICA)
            val second = repository.addTummy(date, java.time.LocalTime.of(10, 0), 430, TummyInputMethod.STOPERICA)
            val third = repository.addTummy(date, java.time.LocalTime.of(11, 0), 210, TummyInputMethod.STOPERICA)

            assertEquals(3, setOf(first.id, second.id, third.id).size)
            assertEquals(3, repository.summary(date).tummyCount)
            assertEquals(900L, repository.summary(date).tummySeconds)

            repository.updateTummy(second.copy(durationSeconds = 500))
            val afterEdit = repository.tummySessions.first().filter { it.date == date.toString() }
            assertEquals(listOf(260L, 500L, 210L), afterEdit.sortedBy { it.time }.map { it.durationSeconds })
            assertEquals(970L, repository.summary(date).tummySeconds)

            repository.deleteTummy(first)
            assertEquals(2, repository.summary(date).tummyCount)
            assertEquals(710L, repository.summary(date).tummySeconds)
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
            val repository = AppRepository(database)
            val invalid =
                repository.currentSnapshot().copy(
                    complementaryFoodMeals =
                        listOf(
                            ComplementaryFoodMealEntity(
                                date = "2026-01-02",
                                time = "12:00",
                                ingredients = listOf("mrkva"),
                                amount = -1,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )
            try {
                repository.replaceAll(invalid)
            } catch (_: IllegalArgumentException) {
            }
            assertEquals(1, dao.allMeals().size)
            assertTrue(dao.allComplementaryFoodMeals().isEmpty())
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
                                    "CREATE TABLE meals (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, time TEXT NOT NULL, amountMl INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                                )
                                db.execSQL(
                                    "CREATE TABLE daily_entries (date TEXT NOT NULL PRIMARY KEY, waya TEXT NOT NULL, exercise TEXT NOT NULL, noTummyTime INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                                )
                                db.execSQL(
                                    "CREATE TABLE tummy_sessions (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, time TEXT NOT NULL, durationSeconds INTEGER NOT NULL, inputMethod TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                                )
                                db.execSQL(
                                    "CREATE TABLE settings (id INTEGER NOT NULL PRIMARY KEY, reminderEnabled INTEGER NOT NULL, reminderTime TEXT NOT NULL, theme TEXT NOT NULL, onboardingShown INTEGER NOT NULL, lastNotificationDate TEXT)",
                                )
                                db.execSQL("INSERT INTO meals VALUES (1, '2026-01-02', '08:00', 80, 100, 200)")
                                db.execSQL("INSERT INTO daily_entries VALUES ('2026-01-02', 'DA', 'NE', 1, 100, 200)")
                                db.execSQL("INSERT INTO tummy_sessions VALUES (1, '2026-01-02', '09:00', 90, 'RUCNO', 100, 200)")
                                db.execSQL("INSERT INTO settings VALUES (1, 1, '18:00', 'TAMNA', 1, '2026-01-01')")
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
        v3.writableDatabase.query("SELECT amountMl FROM meals").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(80, cursor.getInt(0))
        }
        v3.writableDatabase.query("SELECT durationSeconds FROM tummy_sessions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(90, cursor.getInt(0))
        }
        v3.writableDatabase.query("SELECT reminderTime, theme, lastNotificationDate FROM settings").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("18:00", cursor.getString(0))
            assertEquals("TAMNA", cursor.getString(1))
            assertEquals("2026-01-01", cursor.getString(2))
        }
        v3.close()
        context.deleteDatabase(name)
    }

    @Test fun migrationFromVersionThreeAddsGrowthTablesWithoutChangingDiaryData() {
        val name = "migration-3-4.db"
        context.deleteDatabase(name)
        val v3 =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(3) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                db.execSQL("CREATE TABLE meals (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, time TEXT NOT NULL, amountMl INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                                db.execSQL("CREATE TABLE daily_entries (date TEXT NOT NULL PRIMARY KEY, waya TEXT NOT NULL, exercise TEXT NOT NULL, noTummyTime INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, stoolCount INTEGER)")
                                db.execSQL("CREATE TABLE tummy_sessions (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, time TEXT NOT NULL, durationSeconds INTEGER NOT NULL, inputMethod TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                                db.execSQL("CREATE TABLE settings (id INTEGER NOT NULL PRIMARY KEY, reminderEnabled INTEGER NOT NULL, reminderTime TEXT NOT NULL, theme TEXT NOT NULL, onboardingShown INTEGER NOT NULL, lastNotificationDate TEXT)")
                                db.execSQL("INSERT INTO meals VALUES (1, '2026-01-02', '08:00', 80, 100, 200)")
                                db.execSQL("INSERT INTO settings VALUES (1, 1, '18:00', 'SUSTAV', 1, NULL)")
                            }

                            override fun onUpgrade(
                                db: androidx.sqlite.db.SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        v3.writableDatabase
        v3.close()
        val v4 =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(4) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit

                            override fun onUpgrade(
                                db: androidx.sqlite.db.SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = AppDatabase.MIGRATION_3_4.migrate(db)
                        },
                    ).build(),
            )
        v4.writableDatabase.query("SELECT amountMl FROM meals").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(80, cursor.getInt(0))
        }
        v4.writableDatabase.query("SELECT COUNT(*) FROM child_profile").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        v4.writableDatabase.query("SELECT COUNT(*) FROM growth_measurements").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        v4.close()
        context.deleteDatabase(name)
    }

    @Test fun profileAndGrowthCrudSupportsMultipleMeasurementsAndScopedTransactionalDeletion() =
        runBlocking {
            val repository = AppRepository(database)
            repository.initialize()
            val now = System.currentTimeMillis()
            repository.addMeal(java.time.LocalDate.of(2026, 1, 2), java.time.LocalTime.NOON, 80)
            val profile =
                ChildProfileEntity(
                    name = "Žana",
                    sex = ChildSex.DJEVOJCICA,
                    birthDate = "2026-01-01",
                    gestationalWeeks = 35,
                    gestationalDays = 4,
                    createdAt = now,
                    updatedAt = now,
                )
            repository.saveChildProfile(profile)
            val first = repository.addGrowthMeasurement(GrowthMeasurementEntity(date = "2026-01-02", time = "09:00", weightG = 2_100, createdAt = now, updatedAt = now))
            val second = repository.addGrowthMeasurement(GrowthMeasurementEntity(date = "2026-01-02", time = "10:00", headCircumferenceCm = 31.2, createdAt = now, updatedAt = now))
            assertEquals(2, repository.currentSnapshot().growthMeasurements.size)
            repository.updateGrowthMeasurement(first.copy(weightG = 2_150))
            assertEquals(
                2_150,
                repository
                    .currentSnapshot()
                    .growthMeasurements
                    .first { it.id == first.id }
                    .weightG,
            )
            repository.deleteGrowthMeasurement(second)
            assertEquals(1, repository.currentSnapshot().growthMeasurements.size)
            repository.deleteGrowthProfileAndMeasurements()
            val afterDelete = repository.currentSnapshot()
            assertEquals(null, afterDelete.childProfile)
            assertTrue(afterDelete.growthMeasurements.isEmpty())
            assertEquals(1, afterDelete.meals.size)
        }

    @Test fun migrationFromVersionFourAddsComplementaryFoodWithoutChangingExistingData() {
        val name = "migration-4-5.db"
        context.deleteDatabase(name)
        val v4 =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(4) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                db.execSQL("CREATE TABLE meals (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, time TEXT NOT NULL, amountMl INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                                db.execSQL("INSERT INTO meals VALUES (1, '2026-07-16', '08:00', 80, 100, 200)")
                            }

                            override fun onUpgrade(
                                db: androidx.sqlite.db.SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        v4.writableDatabase
        v4.close()
        val v5 =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(5) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit

                            override fun onUpgrade(
                                db: androidx.sqlite.db.SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = AppDatabase.MIGRATION_4_5.migrate(db)
                        },
                    ).build(),
            )
        v5.writableDatabase.query("SELECT amountMl FROM meals").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(80, cursor.getInt(0))
        }
        v5.writableDatabase.query("SELECT COUNT(*) FROM complementary_food_meals").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        v5.close()
        context.deleteDatabase(name)
    }

    @Test fun complementaryFoodCrudKeepsMultipleMealsAndScopedDeletion() =
        runBlocking {
            val repository = AppRepository(database)
            repository.initialize()
            val now = System.currentTimeMillis()
            val first =
                repository.addComplementaryFoodMeal(
                    ComplementaryFoodMealEntity(
                        date = "2026-07-16",
                        time = "09:30",
                        ingredients = listOf("mrkva"),
                        amount = 20,
                        unit = ComplementaryFoodUnit.G,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            val second =
                repository.addComplementaryFoodMeal(
                    ComplementaryFoodMealEntity(
                        date = "2026-07-16",
                        time = "13:00",
                        ingredients = listOf("mrkva", "krumpir"),
                        amount = 45,
                        unit = ComplementaryFoodUnit.ML,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            assertEquals(2, repository.currentSnapshot().complementaryFoodMeals.size)
            repository.updateComplementaryFoodMeal(first.copy(amount = 25))
            assertEquals(
                25,
                repository
                    .currentSnapshot()
                    .complementaryFoodMeals
                    .first { it.id == first.id }
                    .amount,
            )
            repository.deleteComplementaryFoodMeal(second)
            assertEquals(listOf(first.id), repository.currentSnapshot().complementaryFoodMeals.map { it.id })
            assertTrue(repository.currentSnapshot().growthMeasurements.isEmpty())
        }

    @Test fun migrationFromVersionFiveAddsGuidelineSourcesWithoutLosingProfileOrSettings() {
        val name = "migration-5-6.db"
        context.deleteDatabase(name)
        val v5 =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(5) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                db.execSQL("CREATE TABLE settings (id INTEGER NOT NULL PRIMARY KEY, reminderEnabled INTEGER NOT NULL, reminderTime TEXT NOT NULL, theme TEXT NOT NULL, onboardingShown INTEGER NOT NULL, lastNotificationDate TEXT)")
                                db.execSQL("INSERT INTO settings VALUES (1, 1, '18:00', 'TAMNA', 1, NULL)")
                                db.execSQL(
                                    "CREATE TABLE child_profile (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                                        "sex TEXT NOT NULL, birthDate TEXT NOT NULL, gestationalWeeks INTEGER NOT NULL, " +
                                        "gestationalDays INTEGER NOT NULL, birthWeightG INTEGER, birthLengthCm REAL, " +
                                        "birthHeadCircumferenceCm REAL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                                )
                                db.execSQL("INSERT INTO child_profile VALUES (1, 'Ana', 'DJEVOJCICA', '2026-01-01', 40, 0, 3200, NULL, NULL, 100, 200)")
                            }

                            override fun onUpgrade(
                                db: androidx.sqlite.db.SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        v5.writableDatabase
        v5.close()
        val v6 =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(6) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit

                            override fun onUpgrade(
                                db: androidx.sqlite.db.SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = AppDatabase.MIGRATION_5_6.migrate(db)
                        },
                    ).build(),
            )
        v6.writableDatabase.query("SELECT theme, guidelineTargetsEnabled, guidelineWizardCompleted FROM settings").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("TAMNA", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
        }
        v6.writableDatabase.query("SELECT name, birthWeightG, independentMobilityDate FROM child_profile").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Ana", cursor.getString(0))
            assertEquals(3200, cursor.getInt(1))
            assertTrue(cursor.isNull(2))
        }
        listOf(
            "milk_completeness_history",
            "expected_meal_count_history",
            "individual_feeding_targets",
            "individual_tummy_targets",
        ).forEach { table ->
            v6.writableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
        v6.close()
        context.deleteDatabase(name)
    }
}
