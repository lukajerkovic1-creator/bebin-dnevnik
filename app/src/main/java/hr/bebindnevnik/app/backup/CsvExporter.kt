package hr.bebindnevnik.app.backup

import hr.bebindnevnik.app.data.AppSnapshot
import hr.bebindnevnik.app.domain.growth.GrowthAgeBasis
import hr.bebindnevnik.app.domain.growth.GrowthCalculator
import hr.bebindnevnik.app.domain.growth.GrowthIndicator
import hr.bebindnevnik.app.domain.growth.formatGrowthAge
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CsvExporter {
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy.")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val timestampFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy. HH:mm:ss")

    fun createZip(
        snapshot: AppSnapshot,
        growthCalculator: GrowthCalculator? = null,
    ): ByteArray =
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
                zip.addCsv(
                    "profil_djeteta.csv",
                    listOf(
                        listOf<Any>(
                            "Ime",
                            "Spol",
                            "Datum rođenja",
                            "Gestacijska dob",
                            "Porođajna težina (g)",
                            "Porođajna duljina (cm)",
                            "Porođajni opseg glave (cm)",
                        ),
                    ) +
                        listOfNotNull(
                            snapshot.childProfile?.let {
                                listOf<Any>(
                                    it.name,
                                    it.sex.name,
                                    date(it.birthDate),
                                    "${it.gestationalWeeks}+${it.gestationalDays}",
                                    it.birthWeightG ?: "",
                                    it.birthLengthCm ?: "",
                                    it.birthHeadCircumferenceCm ?: "",
                                )
                            },
                        ),
                )
                zip.addCsv(
                    "mjerenja_rasta.csv",
                    growthRows(snapshot, growthCalculator),
                )
                zip.addCsv(
                    "dohrana.csv",
                    listOf(
                        listOf<Any>(
                            "Datum",
                            "Vrijeme",
                            "Namirnice",
                            "Količina",
                            "Jedinica",
                            "Vrijeme stvaranja",
                            "Vrijeme posljednje izmjene",
                        ),
                    ) +
                        snapshot.complementaryFoodMeals.map {
                            listOf<Any>(
                                date(it.date),
                                time(it.time),
                                it.ingredients.joinToString(" | "),
                                it.amount,
                                it.unit.name.lowercase(),
                                timestamp(it.createdAt),
                                timestamp(it.updatedAt),
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

    private fun timestamp(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(timestampFormat)

    private fun growthRows(
        snapshot: AppSnapshot,
        calculator: GrowthCalculator?,
    ): List<List<Any>> {
        val header =
            listOf<Any>(
                "Datum",
                "Vrijeme",
                "Kronološka dob",
                "Korigirana dob",
                "Postmenstrualna dob",
                "Težina (g)",
                "Duljina/visina (cm)",
                "Vrsta mjerenja",
                "Opseg glave (cm)",
                "Percentil težine/dobi",
                "Z težine/dobi",
                "Percentil duljine/visine",
                "Z duljine/visine",
                "Percentil opsega glave",
                "Z opsega glave",
                "Percentil težine/duljine-visine",
                "Z težine/duljine-visine",
                "Referentni sustav",
                "Vrsta dobi",
                "Korekcija duljine/visine (cm)",
            )
        val profile = snapshot.childProfile ?: return listOf(header)
        val birthDate = LocalDate.parse(profile.birthDate)
        val rows =
            snapshot.growthMeasurements.map { item ->
                val measurementDate = LocalDate.parse(item.date)
                val assessment = calculator?.assess(profile, item)
                val weight = assessment?.metric(GrowthIndicator.WEIGHT_FOR_AGE)
                val length = assessment?.metric(GrowthIndicator.LENGTH_HEIGHT_FOR_AGE)
                val head = assessment?.metric(GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE)
                val weightLength = assessment?.metric(GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT)
                val ages = assessment?.ages
                listOf<Any>(
                    date(item.date),
                    time(item.time),
                    formatGrowthAge(birthDate, measurementDate),
                    ages?.correctedDays?.let { formatGrowthAge(birthDate, measurementDate, it) } ?: "",
                    ages?.postmenstrualDays?.let { "${it / 7}+${it % 7}" } ?: "",
                    item.weightG ?: "",
                    item.lengthHeightCm ?: "",
                    item.lengthMeasurementType.name,
                    item.headCircumferenceCm ?: "",
                    weight?.percentile ?: "",
                    weight?.zScore ?: "",
                    length?.percentile ?: "",
                    length?.zScore ?: "",
                    head?.percentile ?: "",
                    head?.zScore ?: "",
                    weightLength?.percentile ?: "",
                    weightLength?.zScore ?: "",
                    assessment?.referenceSystem?.name ?: "",
                    assessment?.ageBasis?.name ?: GrowthAgeBasis.CHRONOLOGICAL.name,
                    length?.lengthCorrectionCm?.takeIf { it != 0.0 } ?: weightLength?.lengthCorrectionCm?.takeIf { it != 0.0 } ?: "",
                )
            }
        return listOf(header) + rows
    }
}
