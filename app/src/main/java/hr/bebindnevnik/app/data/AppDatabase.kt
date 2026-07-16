package hr.bebindnevnik.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray

const val DATABASE_VERSION = 5

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
    }
}
