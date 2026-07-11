package hr.bebindnevnik.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class EnumConverters {
    @TypeConverter fun ternaryToString(value: TernaryStatus): String = value.name

    @TypeConverter fun stringToTernary(value: String): TernaryStatus = TernaryStatus.valueOf(value)

    @TypeConverter fun methodToString(value: TummyInputMethod): String = value.name

    @TypeConverter fun stringToMethod(value: String): TummyInputMethod = TummyInputMethod.valueOf(value)

    @TypeConverter fun themeToString(value: AppTheme): String = value.name

    @TypeConverter fun stringToTheme(value: String): AppTheme = AppTheme.valueOf(value)
}

@Database(
    entities = [MealEntity::class, DailyEntryEntity::class, TummySessionEntity::class, SettingsEntity::class],
    version = 3,
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
    }
}
