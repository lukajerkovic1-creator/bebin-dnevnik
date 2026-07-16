package hr.bebindnevnik.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray

const val DATABASE_VERSION = 6

class EnumConverters {
    @TypeConverter fun ternaryToString(value: TernaryStatus): String = value.name

    @TypeConverter fun stringToTernary(value: String): TernaryStatus = TernaryStatus.valueOf(value)

    @TypeConverter fun methodToString(value: TummyInputMethod): String = value.name

    @TypeConverter fun stringToMethod(value: String): TummyInputMethod = TummyInputMethod.valueOf(value)

    @TypeConverter fun themeToString(value: AppTheme): String = value.name

    @TypeConverter fun stringToTheme(value: String): AppTheme = AppTheme.valueOf(value)

    @TypeConverter fun childSexToString(value: ChildSex): String = value.name

    @TypeConverter fun stringToChildSex(value: String): ChildSex = ChildSex.valueOf(value)

    @TypeConverter fun lengthTypeToString(value: LengthMeasurementType): String = value.name

    @TypeConverter fun stringToLengthType(value: String): LengthMeasurementType = LengthMeasurementType.valueOf(value)

    @TypeConverter fun complementaryUnitToString(value: ComplementaryFoodUnit): String = value.name

    @TypeConverter fun stringToComplementaryUnit(value: String): ComplementaryFoodUnit = ComplementaryFoodUnit.valueOf(value)

    @TypeConverter fun ingredientsToString(value: List<String>): String = JSONArray(value).toString()

    @TypeConverter
    fun stringToIngredients(value: String): List<String> {
        val array = JSONArray(value)
        return List(array.length()) { array.getString(it) }
    }
}

@Database(
    entities = [
        MealEntity::class,
        DailyEntryEntity::class,
        TummySessionEntity::class,
        SettingsEntity::class,
        ChildProfileEntity::class,
        GrowthMeasurementEntity::class,
        ComplementaryFoodMealEntity::class,
        MilkCompletenessEntity::class,
        ExpectedMealCountEntity::class,
        IndividualFeedingTargetEntity::class,
        IndividualTummyTargetEntity::class,
    ],
    version = DATABASE_VERSION,
    exportSchema = true,
)
@TypeConverters(EnumConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE settings ADD COLUMN lastNotificationDate TEXT")
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE daily_entries ADD COLUMN stoolCount INTEGER")
                }
            }

        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS child_profile (
                            id INTEGER NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            sex TEXT NOT NULL,
                            birthDate TEXT NOT NULL,
                            gestationalWeeks INTEGER NOT NULL,
                            gestationalDays INTEGER NOT NULL,
                            birthWeightG INTEGER,
                            birthLengthCm REAL,
                            birthHeadCircumferenceCm REAL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS growth_measurements (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            date TEXT NOT NULL,
                            time TEXT NOT NULL,
                            weightG INTEGER,
                            lengthHeightCm REAL,
                            lengthMeasurementType TEXT NOT NULL,
                            headCircumferenceCm REAL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_growth_measurements_date_time ON growth_measurements(date, time)",
                    )
                }
            }

        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS complementary_food_meals (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            date TEXT NOT NULL,
                            time TEXT NOT NULL,
                            ingredients TEXT NOT NULL,
                            amount INTEGER NOT NULL,
                            unit TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_complementary_food_meals_date_time " +
                            "ON complementary_food_meals(date, time)",
                    )
                }
            }

        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE settings ADD COLUMN guidelineTargetsEnabled INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE settings ADD COLUMN guidelineWizardCompleted INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE settings ADD COLUMN guidelineWizardDismissed INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE child_profile ADD COLUMN independentMobilityDate TEXT")
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS milk_completeness_history (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            startDate TEXT NOT NULL,
                            endDate TEXT,
                            complete INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_milk_completeness_start_date " +
                            "ON milk_completeness_history(startDate)",
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS expected_meal_count_history (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            startDate TEXT NOT NULL,
                            endDate TEXT,
                            mealCount INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_expected_meal_count_start_date " +
                            "ON expected_meal_count_history(startDate)",
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS individual_feeding_targets (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            lowerMlPerDay INTEGER NOT NULL,
                            upperMlPerDay INTEGER NOT NULL,
                            startDate TEXT NOT NULL,
                            endDate TEXT,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_feeding_target_start_date " +
                            "ON individual_feeding_targets(startDate)",
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS individual_tummy_targets (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            minutesPerDay INTEGER NOT NULL,
                            startDate TEXT NOT NULL,
                            endDate TEXT,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_tummy_target_start_date " +
                            "ON individual_tummy_targets(startDate)",
                    )
                }
            }
    }
}
