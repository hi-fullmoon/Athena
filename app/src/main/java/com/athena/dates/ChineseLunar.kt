package com.athena.dates

import com.nlf.calendar.Lunar
import com.nlf.calendar.LunarYear
import com.nlf.calendar.Solar
import java.time.LocalDate

const val LUNAR_MIN_YEAR = 1900
const val LUNAR_MAX_YEAR = 2100
val LUNAR_SUPPORTED_YEARS = LUNAR_MIN_YEAR..LUNAR_MAX_YEAR

data class LunarMonthOption(
    val month: Int,
    val isLeapMonth: Boolean,
    val dayCount: Int,
) {
    val label: String get() = (if (isLeapMonth) "闰" else "") + LUNAR_MONTH_NAMES.getValue(month)
    val signedMonth: Int get() = if (isLeapMonth) -month else month
}

fun LunarDateSpec.toSolarDate(): LocalDate {
    require(year in LUNAR_SUPPORTED_YEARS) { "农历年份仅支持 $LUNAR_MIN_YEAR–$LUNAR_MAX_YEAR" }
    val lunar = Lunar.fromYmd(year, signedMonth, day)
    val solar = lunar.solar
    return LocalDate.of(solar.year, solar.month, solar.day)
}

fun LocalDate.toLunarDateSpec(): LunarDateSpec {
    val lunar = Solar.fromYmd(year, monthValue, dayOfMonth).lunar
    require(lunar.year in LUNAR_SUPPORTED_YEARS) {
        "农历年份仅支持 $LUNAR_MIN_YEAR–$LUNAR_MAX_YEAR"
    }
    return LunarDateSpec(
        year = lunar.year,
        month = kotlin.math.abs(lunar.month),
        day = lunar.day,
        isLeapMonth = lunar.month < 0,
    )
}

fun lunarMonthsInYear(year: Int): List<LunarMonthOption> {
    require(year in LUNAR_SUPPORTED_YEARS) { "农历年份仅支持 $LUNAR_MIN_YEAR–$LUNAR_MAX_YEAR" }
    return LunarYear.fromYear(year).monthsInYear.map { month ->
        LunarMonthOption(kotlin.math.abs(month.month), month.month < 0, month.dayCount)
    }
}

fun LunarDateSpec.isValidLunarDate(): Boolean = runCatching {
    val monthOption = lunarMonthsInYear(year).first { it.signedMonth == signedMonth }
    require(day <= monthOption.dayCount)
    toSolarDate()
}.isSuccess

fun LunarDateSpec.displayLabel(includeYear: Boolean = true): String = buildString {
    if (includeYear) append(year).append("年")
    if (isLeapMonth) append("闰")
    append(LUNAR_MONTH_NAMES.getValue(month))
    append(LUNAR_DAY_NAMES.getOrElse(day) { "${day}日" })
}

fun LocalDate.lunarDisplayLabel(): String? = runCatching {
    toLunarDateSpec().displayLabel(includeYear = false)
}.getOrNull()

fun LocalDate.lunarCalendarCellLabel(): String? = runCatching {
    val lunar = toLunarDateSpec()
    if (lunar.day == 1) {
        (if (lunar.isLeapMonth) "闰" else "") + LUNAR_MONTH_NAMES.getValue(lunar.month)
    } else {
        LUNAR_DAY_NAMES[lunar.day]
    }
}.getOrNull()

internal fun lunarOccurrenceInYear(anchor: LunarDateSpec, targetLunarYear: Int): LocalDate? {
    if (targetLunarYear !in LUNAR_SUPPORTED_YEARS) return null
    val month = lunarMonthsInYear(targetLunarYear).firstOrNull { it.signedMonth == anchor.signedMonth } ?: return null
    val clampedDay = minOf(anchor.day, month.dayCount)
    return LunarDateSpec(targetLunarYear, anchor.month, clampedDay, anchor.isLeapMonth).toSolarDate()
}

private val LUNAR_MONTH_NAMES = mapOf(
    1 to "正月",
    2 to "二月",
    3 to "三月",
    4 to "四月",
    5 to "五月",
    6 to "六月",
    7 to "七月",
    8 to "八月",
    9 to "九月",
    10 to "十月",
    11 to "冬月",
    12 to "腊月",
)

private val LUNAR_DAY_NAMES = listOf(
    "",
    "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
    "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
    "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十",
)
