package hr.bebindnevnik.app.backup

import hr.bebindnevnik.app.data.AppSnapshot
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CsvExporter {
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy.")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun createZip(snapshot: AppSnapshot): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output, Charsets.UTF_8).use { zip ->
                zip.addCsv(
                    "obroci.csv",
                    listOf(listOf<Any>("ID", "Datum", "Vrijeme", "Količina (ml)")) +
                        snapshot.meals.map {
                            listOf<Any>(it.id, date(it.date), time(it.time), it.amountMl)
                        },
                )
                zip.addCsv(
                    "tummy_time.csv",
                    listOf(listOf<Any>("ID", "Datum", "Vrijeme", "Trajanje (sekunde)", "Način unosa")) +
                        snapshot.tummySessions.map {
                            listOf<Any>(it.id, date(it.date), time(it.time), it.durationSeconds, it.inputMethod.name)
                        },
                )
                zip.addCsv(
                    "dnevna_evidencija.csv",
                    listOf(listOf<Any>("Datum", "Waya kapi", "Vježbanje", "Nije bilo tummy timea", "Broj stolica")) +
                        snapshot.dailyEntries.map {
                            listOf<Any>(
                                date(it.date),
                                it.waya.name,
                                it.exercise.name,
                                if (it.noTummyTime) "DA" else "NE",
                                it.stoolCount ?: "Nije evidentirano",
                            )
                        },
                )
            }
            output.toByteArray()
        }

    fun escape(value: Any?): String {
        val text = value?.toString().orEmpty()
        return if (text.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"${text.replace("\"", "\"\"")}\"" else text
    }

    private fun ZipOutputStream.addCsv(
        name: String,
        rows: List<List<Any>>,
    ) {
        putNextEntry(ZipEntry(name))
        write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        rows.forEach { row -> write((row.joinToString(";") { escape(it) } + "\r\n").toByteArray(Charsets.UTF_8)) }
        closeEntry()
    }

    private fun date(value: String) = LocalDate.parse(value).format(dateFormat)

    private fun time(value: String) = LocalTime.parse(value).format(timeFormat)
}
