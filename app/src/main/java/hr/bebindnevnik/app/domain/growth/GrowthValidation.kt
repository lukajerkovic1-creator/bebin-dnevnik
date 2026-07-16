package hr.bebindnevnik.app.domain.growth

import hr.bebindnevnik.app.data.ChildProfileEntity
import hr.bebindnevnik.app.data.GrowthMeasurementEntity
import java.time.LocalDate
import java.time.LocalTime

data class GrowthValidationResult(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val valid: Boolean get() = errors.isEmpty()
}

object GrowthValidation {
    const val MIN_GESTATIONAL_WEEKS = 22
    const val MAX_GESTATIONAL_WEEKS = 42

    fun profile(
        profile: ChildProfileEntity,
        today: LocalDate = LocalDate.now(),
    ): GrowthValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val birthDate = runCatching { LocalDate.parse(profile.birthDate) }.getOrNull()
        if (profile.name.trim().isEmpty()) errors += "Unesite ime djeteta."
        if (birthDate == null) errors += "Odaberite ispravan datum rođenja."
        if (birthDate?.isAfter(today) == true) errors += "Datum rođenja ne može biti u budućnosti."
        if (profile.gestationalWeeks !in MIN_GESTATIONAL_WEEKS..MAX_GESTATIONAL_WEEKS) {
            errors += "Gestacijski tjedni moraju biti između $MIN_GESTATIONAL_WEEKS i $MAX_GESTATIONAL_WEEKS."
        }
        if (profile.gestationalDays !in 0..6) errors += "Dodatni gestacijski dani moraju biti između 0 i 6."
        validateWeight(profile.birthWeightG, errors, warnings)
        validateLength(profile.birthLengthCm, errors, warnings)
        validateHead(profile.birthHeadCircumferenceCm, errors, warnings)
        return GrowthValidationResult(errors, warnings)
    }

    fun measurement(
        profile: ChildProfileEntity,
        measurement: GrowthMeasurementEntity,
        nowDate: LocalDate = LocalDate.now(),
        nowTime: LocalTime = LocalTime.now(),
    ): GrowthValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val date = runCatching { LocalDate.parse(measurement.date) }.getOrNull()
        val time = runCatching { LocalTime.parse(measurement.time) }.getOrNull()
        val birthDate = LocalDate.parse(profile.birthDate)
        if (date == null) errors += "Odaberite ispravan datum mjerenja."
        if (time == null) errors += "Odaberite ispravno vrijeme mjerenja."
        if (date != null && date.isBefore(birthDate)) errors += "Mjerenje ne može biti prije datuma rođenja."
        if (date != null && date.isAfter(nowDate)) errors += "Mjerenje ne može biti u budućnosti."
        if (date != null && !date.isBefore(birthDate.plusYears(5))) errors += "Modul podržava mjerenja prije navršene 5. godine."
        if (date == nowDate && time != null && time.isAfter(nowTime)) errors += "Vrijeme mjerenja ne može biti u budućnosti."
        if (measurement.weightG == null && measurement.lengthHeightCm == null && measurement.headCircumferenceCm == null) {
            errors += "Unesite najmanje jednu mjeru."
        }
        validateWeight(measurement.weightG, errors, warnings)
        validateLength(measurement.lengthHeightCm, errors, warnings)
        validateHead(measurement.headCircumferenceCm, errors, warnings)
        return GrowthValidationResult(errors, warnings)
    }

    private fun validateWeight(
        value: Int?,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        if (value == null) return
        if (value !in 150..50_000) {
            errors += "Težina mora biti između 150 g i 50.000 g."
        } else if (value < 500 || value > 30_000) {
            warnings += "Težina je vrlo neuobičajena. Provjerite unos prije spremanja."
        }
    }

    private fun validateLength(
        value: Double?,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        if (value == null) return
        if (!value.isFinite() || value !in 20.0..140.0) {
            errors += "Duljina/visina mora biti između 20,0 cm i 140,0 cm."
        } else if (value < 35.0 || value > 130.0) {
            warnings += "Duljina/visina je vrlo neuobičajena. Provjerite unos prije spremanja."
        }
    }

    private fun validateHead(
        value: Double?,
        errors: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        if (value == null) return
        if (!value.isFinite() || value !in 15.0..65.0) {
            errors += "Opseg glave mora biti između 15,0 cm i 65,0 cm."
        } else if (value < 25.0 || value > 60.0) {
            warnings += "Opseg glave je vrlo neuobičajen. Provjerite unos prije spremanja."
        }
    }
}
