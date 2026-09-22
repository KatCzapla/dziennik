package pl.usagi.edziennik.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ClassificationPeriodDto(
    @SerialName("numerOkresu")
    val periodNumber: Int,
    @SerialName("dataOd")
    val dateFrom: String,
    @SerialName("dataDo")
    val dateTo: String,
    @SerialName("id")
    val id: Int,
)

@Serializable
data class GradesResponseDto(
    @SerialName("ocenyPrzedmioty")
    val subjects: List<SubjectGradesDto> = emptyList(),
    @SerialName("ustawienia")
    val settings: GradesSettingsDto = GradesSettingsDto(),
)

@Serializable
data class SubjectGradesDto(
    @SerialName("przedmiotNazwa")
    val subjectName: String,
    @SerialName("pozycja")
    val position: Int,
    @SerialName("uwzglednijWageOcen")
    val includeGradeWeight: Boolean = false,
    @SerialName("nauczyciele")
    val teachers: List<String> = emptyList(),
    @SerialName("kolumnyOcenyCzastkowe")
    val columns: List<GradeColumnDto> = emptyList(),
    @SerialName("egzaminFormaPraktyczna")
    val practicalExamGrade: String? = null,
    @SerialName("egzaminFormaUstna")
    val oralExamGrade: String? = null,
    @SerialName("egzaminOcenaProponowana")
    val proposedExamGrade: String? = null,
    @SerialName("egzaminOcenaLaczna")
    val finalExamGrade: String? = null,
    @SerialName("sumaPunktow")
    val pointsSum: String? = null,
    @SerialName("sumaPunktowWszystkieSemestry")
    val pointsSumAllTerms: String? = null,
    @SerialName("srednia")
    val average: JsonElement? = null,
    @SerialName("sredniaWszystkieSemestry")
    val averageAllTerms: JsonElement? = null,
    @SerialName("proponowanaOcenaOkresowa")
    val proposedTermGrade: String? = null,
    @SerialName("proponowanaOcenaOkresowaPunkty")
    val proposedTermPoints: String? = null,
    @SerialName("ocenaOkresowa")
    val termGrade: String? = null,
    @SerialName("ocenaOkresowaPunkty")
    val termPoints: String? = null,
    @SerialName("podsumowanieOcen")
    val gradesSummary: JsonElement? = null,
)

@Serializable
data class GradeColumnDto(
    @SerialName("idKolumny")
    val id: Int,
    @SerialName("kategoriaKolumny")
    val category: String,
    @SerialName("nazwaKolumny")
    val name: String,
    @SerialName("oceny")
    val grades: List<GradeDto> = emptyList(),
)

@Serializable
data class GradeDto(
    @SerialName("wpis")
    val value: String,
    @SerialName("dataOceny")
    val date: String,
    @SerialName("idKolumny")
    val columnId: Int,
    @SerialName("kategoriaKolumny")
    val category: String,
    @SerialName("nazwaKolumny")
    val columnName: String,
    @SerialName("waga")
    val weight: JsonElement? = null,
    @SerialName("kolorOceny")
    val color: Int? = null,
    @SerialName("nauczyciel")
    val teacher: String? = null,
    @SerialName("zmienionaOdOstatniegoLogowania")
    val changedSinceLastLogin: Boolean = false,
    @SerialName("idOcenaPoprawiona")
    val correctedGradeId: Int? = null,
)

@Serializable
data class GradesSettingsDto(
    @SerialName("isSredniaAndPunkty")
    val showAverageAndPoints: Boolean = false,
    @SerialName("isDorosli")
    val adultStudent: Boolean = false,
    @SerialName("isOcenaOpisowa")
    val descriptiveGrade: Boolean = false,
    @SerialName("isOstatniOkresKlasyfikacyjny")
    val lastClassificationPeriod: Boolean = false,
)
