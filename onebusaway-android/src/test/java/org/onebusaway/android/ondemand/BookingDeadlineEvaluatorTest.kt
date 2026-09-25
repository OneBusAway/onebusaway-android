package org.onebusaway.android.ondemand

import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.toBookingRule
import org.onebusaway.android.api.adapters.toFlexCalendar
import org.onebusaway.android.api.contract.BookingRuleDto
import org.onebusaway.android.api.contract.FlexCalendarDto
import org.onebusaway.android.models.AvailabilityRule
import org.onebusaway.android.models.BookingRule
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.FlexCalendar
import org.onebusaway.android.models.ServiceDayTime

/**
 * Runs every vector in the shared booking vectors file (mirrored verbatim from maglev's
 * `testdata/flex-booking-vectors.json`; spec §6) so Android, iOS and the server agree on one
 * deadline algorithm, then covers the §6.3 branches the vectors don't reach.
 */
class BookingDeadlineEvaluatorTest {

    @Serializable
    private data class RuleVector(val startPickupTime: String? = null, val endPickupTime: String? = null, val calendarIds: List<String> = emptyList())

    @Serializable
    private data class Expected(val state: String, val cutoffInstant: String? = null, val openInstant: String? = null, val nextBookableServiceDate: String? = null)

    @Serializable
    private data class Vector(
        val name: String,
        val timezone: String? = null,
        val bookingRule: BookingRuleDto? = null,
        val rule: RuleVector,
        val travelDate: String,
        val now: String,
        val expected: Expected
    )

    @Serializable
    private data class VectorsFile(val timezone: String, val calendars: List<FlexCalendarDto> = emptyList(), val vectors: List<Vector>)

    private val json = Json { ignoreUnknownKeys = true }

    private val file = json.decodeFromString<VectorsFile>(File("src/androidTest/res/raw/flex_booking_vectors.json").readText())

    private val calendars = file.calendars.map { it.toFlexCalendar() }.associateBy { it.id }

    private val detroit = ZoneId.of("America/Detroit")

    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    private fun state(wire: String): BookingState = when (wire) {
        "notYetOpen" -> BookingState.NOT_YET_OPEN
        "open" -> BookingState.OPEN
        "closedForDate" -> BookingState.CLOSED_FOR_DATE
        "unknown" -> BookingState.UNKNOWN
        else -> error("unknown state '$wire'")
    }

    private fun instant(iso: String?): Instant? = iso?.let { OffsetDateTime.parse(it).toInstant() }

    private fun date(iso: String): LocalDate = LocalDate.parse(iso)

    /** Null when [actual] is the same instant *and* reads with the same offset in [zone] as [expected]. */
    private fun instantMismatch(label: String, expected: String?, actual: Instant?, zone: ZoneId): String? {
        if (expected == null) return if (actual == null) null else "$label: expected null, got $actual"
        if (actual == null) return "$label: expected $expected, got null"
        val expectedOffsetTime = OffsetDateTime.parse(expected)
        val actualOffsetTime = actual.atZone(zone).toOffsetDateTime()
        return if (actualOffsetTime == expectedOffsetTime) null else "$label: expected $expected, got $actualOffsetTime"
    }

    private fun vectorFailures(vector: Vector): List<String> {
        val zone = ZoneId.of(vector.timezone ?: file.timezone)
        val rule = rule(
            start = vector.rule.startPickupTime,
            end = vector.rule.endPickupTime,
            calendarIds = vector.rule.calendarIds,
            bookingRuleId = vector.bookingRule?.id
        )
        val bookingRule = vector.bookingRule?.toBookingRule()
        val now = requireNotNull(instant(vector.now))
        val actual = BookingDeadlineEvaluator.evaluate(rule, bookingRule, date(vector.travelDate), now, zone, calendars)
        val today = now.atZone(zone).toLocalDate()
        val next = BookingDeadlineEvaluator.nextBookableServiceDate(rule, bookingRule, today, now, zone, calendars)
        val expectedNext = vector.expected.nextBookableServiceDate?.let(::date)
        return listOfNotNull(
            if (actual.state == state(vector.expected.state)) null else "state: expected ${vector.expected.state}, got ${actual.state}",
            instantMismatch("cutoffInstant", vector.expected.cutoffInstant, actual.cutoffInstant, zone),
            instantMismatch("openInstant", vector.expected.openInstant, actual.openInstant, zone),
            if (next == expectedNext) null else "nextBookableServiceDate: expected $expectedNext, got $next"
        )
    }

    private fun rule(start: String? = "05:30:00", end: String? = "18:00:00", calendarIds: List<String> = listOf("wk"), bookingRuleId: String? = "br") = AvailabilityRule(
        fromIds = emptyList(),
        toIds = emptyList(),
        startPickupTime = start?.let(ServiceDayTime::parse),
        endPickupTime = end?.let(ServiceDayTime::parse),
        endDropOffTime = null,
        calendarIds = calendarIds,
        pickupType = 2,
        dropOffType = 2,
        pickupBookingRuleId = bookingRuleId,
        dropOffBookingRuleId = null,
        safeDurationFactor = null,
        safeDurationOffset = null
    )

    private fun bookingRule(
        type: BookingType,
        durationMin: Int? = null,
        durationMax: Int? = null,
        lastDay: Int? = null,
        lastTime: String? = null,
        startDay: Int? = null,
        startTime: String? = null,
        calendarId: String? = null
    ) = BookingRule(
        id = "br",
        bookingType = type,
        priorNoticeDurationMin = durationMin,
        priorNoticeDurationMax = durationMax,
        priorNoticeLastDay = lastDay,
        priorNoticeLastTime = lastTime?.let(ServiceDayTime::parse),
        priorNoticeStartDay = startDay,
        priorNoticeStartTime = startTime?.let(ServiceDayTime::parse),
        priorNoticeCalendarId = calendarId,
        message = null,
        pickupMessage = null,
        dropOffMessage = null,
        phoneNumber = null,
        infoUrl = null,
        bookingUrl = null
    )

    private fun calendar(
        id: String = "wk",
        days: Set<DayOfWeek> = weekdays,
        startDate: String = "2024-01-01",
        endDate: String = "2027-12-31",
        exceptedDates: Set<LocalDate> = emptySet()
    ) = FlexCalendar(id, days, date(startDate), date(endDate), exceptedDates)

    @Test
    fun `the vectors file has the spec's minimum coverage`() {
        assertTrue("expected at least 21 vectors, found ${file.vectors.size}", file.vectors.size >= 21)
        val reached = file.vectors.map { state(it.expected.state) }.toSet()
        assertEquals(BookingState.entries.toSet(), reached)
    }

    @Test
    fun `every vector evaluates as expected`() {
        val failures = file.vectors.mapNotNull { vector ->
            vectorFailures(vector).takeIf { it.isNotEmpty() }?.let { "${vector.name}: ${it.joinToString("; ")}" }
        }
        val passed = file.vectors.size - failures.size
        assertEquals("$passed/${file.vectors.size} vectors passed\n" + failures.joinToString("\n"), file.vectors.size, passed)
    }

    @Test
    fun `noon anchor survives a DST transition`() {
        // 2026-03-08 springs forward at 02:00 EST. Local noon is 12:00 EDT (16:00Z); twelve hours
        // earlier is 04:00Z, which is 23:00 EST on the 7th — the GTFS anchor, not local midnight.
        val anchor = BookingDeadlineEvaluator.serviceDayAnchor(date("2026-03-08"), detroit)
        assertEquals(OffsetDateTime.parse("2026-03-07T23:00:00-05:00").toInstant(), anchor)
        // So 18:00:00 is 18:00 EDT, and 25:00:00 is 05:00Z on the 9th, 01:00 EDT.
        assertEquals(OffsetDateTime.parse("2026-03-08T18:00:00-04:00").toInstant(), BookingDeadlineEvaluator.instantOf(date("2026-03-08"), ServiceDayTime.parse("18:00:00"), detroit))
        assertEquals(OffsetDateTime.parse("2026-03-09T01:00:00-04:00").toInstant(), BookingDeadlineEvaluator.instantOf(date("2026-03-08"), ServiceDayTime.parse("25:00:00"), detroit))
        // And across fall-back, 2026-11-01.
        assertEquals(OffsetDateTime.parse("2026-11-01T18:00:00-05:00").toInstant(), BookingDeadlineEvaluator.instantOf(date("2026-11-01"), ServiceDayTime.parse("18:00:00"), detroit))
    }

    @Test
    fun `countBack counts civil days without a calendar and service days with one`() {
        val monday = date("2026-03-16")
        val weekdaysWithoutFriday13 = calendar(exceptedDates = setOf(date("2026-03-13")))
        assertEquals(date("2026-03-15"), BookingDeadlineEvaluator.countBack(monday, 1, null))
        assertEquals(date("2026-03-12"), BookingDeadlineEvaluator.countBack(monday, 1, weekdaysWithoutFriday13))
    }

    @Test
    fun `countBack of zero days is the date itself, without validating the calendar`() {
        val noActiveDays = calendar(days = emptySet(), startDate = "2026-03-16")
        assertEquals(date("2026-03-16"), BookingDeadlineEvaluator.countBack(date("2026-03-16"), 0, noActiveDays))
    }

    @Test
    fun `countBack fails when it reaches the calendar start date first`() {
        val startsThursday = calendar(startDate = "2026-03-12")
        assertNull(BookingDeadlineEvaluator.countBack(date("2026-03-16"), 3, startsThursday))
        assertEquals(date("2026-03-12"), BookingDeadlineEvaluator.countBack(date("2026-03-16"), 2, startsThursday))
    }

    @Test
    fun `countBack fails for a calendar with no active days`() {
        assertNull(BookingDeadlineEvaluator.countBack(date("2026-03-16"), 1, calendar(days = emptySet())))
    }

    @Test
    fun `countBack walks at most 400 calendar days`() {
        val travelDate = date("2026-03-16")
        fun onlyActiveOn(daysBack: Long) = calendar(
            days = DayOfWeek.entries.toSet(),
            startDate = "2020-01-01",
            exceptedDates = (1L until daysBack).mapTo(mutableSetOf()) { travelDate.minusDays(it) }
        )
        assertEquals(travelDate.minusDays(400), BookingDeadlineEvaluator.countBack(travelDate, 1, onlyActiveOn(400)))
        assertNull(BookingDeadlineEvaluator.countBack(travelDate, 1, onlyActiveOn(401)))
    }

    @Test
    fun `a prior-notice calendar with no active days makes the evaluation unknown`() {
        val calendars = mapOf("wk" to calendar(), "none" to calendar(id = "none", days = emptySet()))
        val prior = bookingRule(BookingType.PRIOR_DAYS, lastDay = 1, lastTime = "15:00:00", calendarId = "none")
        val evaluation = BookingDeadlineEvaluator.evaluate(rule(), prior, date("2026-03-16"), Instant.parse("2026-03-13T12:00:00Z"), detroit, calendars)
        assertEquals(BookingEvaluation(BookingState.UNKNOWN, cutoffInstant = null, openInstant = null), evaluation)
    }

    @Test
    fun `a prior-notice calendar missing from the references counts civil days`() {
        val prior = bookingRule(BookingType.PRIOR_DAYS, lastDay = 1, lastTime = "15:00:00", calendarId = "absent")
        val evaluation = BookingDeadlineEvaluator.evaluate(rule(), prior, date("2026-03-16"), Instant.parse("2026-03-15T13:00:00Z"), detroit, mapOf("wk" to calendar()))
        assertEquals(BookingEvaluation(BookingState.OPEN, instant("2026-03-15T15:00:00-04:00"), openInstant = null), evaluation)
    }

    @Test
    fun `same-day durationMax opens that many minutes before the earliest pickup`() {
        val sameDay = bookingRule(BookingType.SAME_DAY, durationMin = 60, durationMax = 120)
        val evaluation = BookingDeadlineEvaluator.evaluate(rule(), sameDay, date("2026-03-11"), instant("2026-03-11T03:00:00-04:00")!!, detroit, emptyMap())
        assertEquals(BookingEvaluation(BookingState.NOT_YET_OPEN, instant("2026-03-11T17:00:00-04:00"), instant("2026-03-11T03:30:00-04:00")), evaluation)
    }

    @Test
    fun `a missing end pickup time means the end of the service day`() {
        val realTime = bookingRule(BookingType.REAL_TIME)
        val evaluation = BookingDeadlineEvaluator.evaluate(rule(start = null, end = null), realTime, date("2026-03-11"), instant("2026-03-11T23:30:00-04:00")!!, detroit, emptyMap())
        assertEquals(BookingEvaluation(BookingState.OPEN, instant("2026-03-12T00:00:00-04:00"), openInstant = null), evaluation)
    }

    @Test
    fun `nextBookableServiceDate skips dates whose count-back cannot complete`() {
        // The prior-notice calendar starts on Monday the 16th, so the 16th has no prior service day
        // to count back to (unknown); the 17th's last day is the 16th, still open at 08:00.
        val calendars = mapOf("wk" to calendar(), "late" to calendar(id = "late", startDate = "2026-03-16"))
        val prior = bookingRule(BookingType.PRIOR_DAYS, lastDay = 1, lastTime = "15:00:00", calendarId = "late")
        val now = instant("2026-03-16T08:00:00-04:00")!!
        assertEquals(BookingState.UNKNOWN, BookingDeadlineEvaluator.evaluate(rule(), prior, date("2026-03-16"), now, detroit, calendars).state)
        assertEquals(date("2026-03-17"), BookingDeadlineEvaluator.nextBookableServiceDate(rule(), prior, date("2026-03-16"), now, detroit, calendars))
    }

    @Test
    fun `nextBookableServiceDate is null when no referenced calendar is known`() {
        val realTime = bookingRule(BookingType.REAL_TIME)
        assertNull(BookingDeadlineEvaluator.nextBookableServiceDate(rule(calendarIds = listOf("absent")), realTime, date("2026-03-11"), instant("2026-03-11T12:00:00-04:00")!!, detroit, emptyMap()))
    }
}
