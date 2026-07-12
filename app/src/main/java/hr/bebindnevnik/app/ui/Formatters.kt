package hr.bebindnevnik.app.ui

import hr.bebindnevnik.app.data.TernaryStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val CroatianDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy.")
val CroatianTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun LocalDate.hrDate(): String = format(CroatianDateFormatter)

fun LocalTime.hrTime(): String = format(CroatianTimeFormatter)

fun String.hrStoredTime(): String = LocalTime.parse(this).format(CroatianTimeFormatter)

fun Long.durationText(): String = "%d:%02d".format(this / 60, this % 60)

fun tummySessionCountText(count: Int): String {
    val noun =
        if (count % 100 in 11..14) {
            "sesija"
        } else {
            when (count % 10) {
                1 -> "sesija"
                in 2..4 -> "sesije"
                else -> "sesija"
            }
        }
    return "$count $noun"
}

fun TernaryStatus.label(): String =
    when (this) {
        TernaryStatus.NIJE_EVIDENTIRANO -> "Nije evidentirano"
        TernaryStatus.DA -> "Da"
        TernaryStatus.NE -> "Ne"
    }
