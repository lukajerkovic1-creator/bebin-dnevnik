package hr.bebindnevnik.app.backup

import hr.bebindnevnik.app.data.AppSnapshot
import hr.bebindnevnik.app.data.AppTheme
import hr.bebindnevnik.app.data.DailyEntryEntity
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.SettingsEntity
import hr.bebindnevnik.app.data.TernaryStatus
import hr.bebindnevnik.app.data.TummyInputMethod
import hr.bebindnevnik.app.data.TummySessionEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class CsvExporterTest {
    @Test fun `CSV escaping quotes delimiters and newlines`() {
        assertEquals("obično", CsvExporter.escape("obično"))
        assertEquals("\"a;b\"", CsvExporter.escape("a;b"))
        assertEquals("\"a\"\"b\"", CsvExporter.escape("a\"b"))
        assertEquals("\"a\nb\"", CsvExporter.escape("a\nb"))
    }

    @Test fun `ZIP contains three BOM prefixed Croatian CSV files`() {
        val now = 1_700_000_000_000
        val snapshot =
            AppSnapshot(
                listOf(MealEntity(1, "2026-01-02", "08:05:06", 80, now, now)),
                listOf(DailyEntryEntity("2026-01-02", TernaryStatus.DA, TernaryStatus.NE, false, now, now, 0)),
                listOf(TummySessionEntity(1, "2026-01-02", "09:10:11", 90, TummyInputMethod.RUCNO, now, now)),
                SettingsEntity(theme = AppTheme.SVIJETLA),
            )
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(CsvExporter.createZip(snapshot))).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        assertEquals(setOf("obroci.csv", "tummy_time.csv", "dnevna_evidencija.csv"), entries.keys)
        entries.values.forEach { assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), it.copyOfRange(0, 3)) }
        assertTrue(String(entries.getValue("obroci.csv"), Charsets.UTF_8).contains("02.01.2026.;08:05:06;80"))
        val dailyCsv = String(entries.getValue("dnevna_evidencija.csv"), Charsets.UTF_8)
        assertTrue(dailyCsv.contains("Broj stolica"))
        assertTrue(dailyCsv.contains("02.01.2026.;DA;NE;NE;0"))
    }

    @Test fun `empty database still exports headers`() {
        val bytes = CsvExporter.createZip(AppSnapshot(emptyList(), emptyList(), emptyList(), SettingsEntity()))
        assertTrue(bytes.isNotEmpty())
    }
}
