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

fun TernaryStatus.label(): String =
    when (this) {
        TernaryStatus.NIJE_EVIDENTIRANO -> "Nije evidentirano"
        TernaryStatus.DA -> "Da"
        TernaryStatus.NE -> "Ne"
    }
