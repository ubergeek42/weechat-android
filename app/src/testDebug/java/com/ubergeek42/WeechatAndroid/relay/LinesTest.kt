package com.ubergeek42.WeechatAndroid.relay

import androidx.test.core.app.ApplicationProvider
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.applicationContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.function.Predicate


fun makeLine(pointer: Long, message: String = pointer.toString(), visible: Boolean = true, timestamp: Long = 0) =
    Line(
        pointer = pointer,
        type = LineSpec.Type.IncomingMessage,
        timestamp = timestamp,
        rawPrefix = "prefix",
        rawMessage = message,
        nick = null,
        isVisible = visible,
        isHighlighted = false,
        displayAs = LineSpec.DisplayAs.Say,
        notifyLevel = LineSpec.NotifyLevel.Message,
    )

fun Lines.getCopyWithoutHeader() = getCopy().drop(1)

fun hasPointer(pointer: Long) = Predicate<Line> { it.pointer == pointer }

fun isSquiggle() = Predicate<Line> { it is SquiggleLine }

fun isReadMarker() = Predicate<Line> { it is MarkerLine }

fun startOf(date: LocalDate) = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

val JAN_1_2026 = startOf(LocalDate.of(2026, 1, 1))
val JAN_2_2026 = startOf(LocalDate.of(2026, 1, 2))
val JAN_3_2026 = startOf(LocalDate.of(2026, 1, 3))

fun isDayChange(day: Long) = Predicate<Line> { it.type == LineSpec.Type.Other && it.timestamp == day }

fun Lines.assertConformsTo(vararg predicates: Predicate<Line>) {
    val lines = getCopyWithoutHeader()
    if (lines.size != predicates.size) throw AssertionError("Expected ${predicates.size} lines. Lines: ${lines.asString()}")
    lines.zip(predicates).forEachIndexed { index, (line, predicate) ->
        if (!predicate.test(line)) throw AssertionError("Failed at index $index. Lines: ${lines.asString()}")
    }
}

fun Lines.assertUnfilteredConformsTo(vararg predicates: Predicate<Line>) {
    P.filterLines = false
    assertConformsTo(*predicates)
}

fun Lines.assertFilteredConformsTo(vararg predicates: Predicate<Line>) {
    P.filterLines = true
    assertConformsTo(*predicates)
}

fun Collection<Line>.asString() =
    joinToString(", ") { line ->
        val visible = if (line.isVisible) "visible" else "invisible"
        val name = if (line is SquiggleLine) "squiggle" else line.pointer.toString()
        "$name ($visible)"
    }


@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class LinesTest {
    @Before fun setUp() {
        applicationContext = ApplicationProvider.getApplicationContext()
        P.init(applicationContext)
        P.loadConnectionPreferences()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `Day change markers appear before the first line of each day`() {
        val lines = Lines().apply { displayDayChange = true }
        lines.addLast(makeLine(0, timestamp = JAN_1_2026))
        lines.addLast(makeLine(1, timestamp = JAN_1_2026))
        lines.addLast(makeLine(2, timestamp = JAN_2_2026))

        lines.assertFilteredConformsTo(
            isDayChange(JAN_1_2026),
            hasPointer(0),
            hasPointer(1),
            isDayChange(JAN_2_2026),
            hasPointer(2),
        )
    }

    @Test fun `Day changes for invisible lines are not shown when filtered`() {
        val lines = Lines().apply { displayDayChange = true }
        lines.addLast(makeLine(0, timestamp = JAN_1_2026))
        lines.addLast(makeLine(1, timestamp = JAN_2_2026, visible = false))
        lines.addLast(makeLine(2, timestamp = JAN_3_2026))

        lines.assertUnfilteredConformsTo(
            isDayChange(JAN_1_2026),
            hasPointer(0),
            isDayChange(JAN_2_2026),
            hasPointer(1),
            isDayChange(JAN_3_2026),
            hasPointer(2),
        )

        lines.assertFilteredConformsTo(
            isDayChange(JAN_1_2026),
            hasPointer(0),
            isDayChange(JAN_3_2026),
            hasPointer(2),
        )
    }

    @Test fun `Day changes can be turned on and off`() {
        val lines = Lines()
        lines.addLast(makeLine(0, timestamp = JAN_1_2026))
        lines.addLast(makeLine(1, timestamp = JAN_2_2026))

        lines.assertUnfilteredConformsTo(
            hasPointer(0),
            hasPointer(1),
        )

        lines.displayDayChange = true

        lines.assertUnfilteredConformsTo(
            isDayChange(JAN_1_2026),
            hasPointer(0),
            isDayChange(JAN_2_2026),
            hasPointer(1),
        )

        lines.displayDayChange = false

        lines.assertUnfilteredConformsTo(
            hasPointer(0),
            hasPointer(1),
        )
    }

    @Test fun `Day changes are updated when a line is added`() {
        val lines = Lines().apply { displayDayChange = true }
        lines.addLast(makeLine(0, timestamp = JAN_1_2026))

        lines.assertUnfilteredConformsTo(
            isDayChange(JAN_1_2026),
            hasPointer(0),
        )

        lines.addLast(makeLine(1, timestamp = JAN_2_2026))

        lines.assertUnfilteredConformsTo(
            isDayChange(JAN_1_2026),
            hasPointer(0),
            isDayChange(JAN_2_2026),
            hasPointer(1),
        )
    }

    @Test fun `Day changes are updated when a line is replaced`() {
        val lines = Lines().apply { displayDayChange = true }
        lines.addLast(makeLine(0, timestamp = JAN_1_2026))
        lines.addLast(makeLine(1, timestamp = JAN_2_2026))

        lines.assertFilteredConformsTo(
            isDayChange(JAN_1_2026),
            hasPointer(0),
            isDayChange(JAN_2_2026),
            hasPointer(1),
        )

        lines.replaceLine(makeLine(1, timestamp = JAN_2_2026, visible = false))

        lines.assertFilteredConformsTo(
            isDayChange(JAN_1_2026),
            hasPointer(0)
        )
    }

    @Test fun `Read marker comes before the day change of the next line`() {
        val lines = Lines().apply { displayDayChange = true }
        lines.addLast(makeLine(0, timestamp = JAN_1_2026))
        lines.moveReadMarkerToEnd()
        lines.addLast(makeLine(1, timestamp = JAN_2_2026))

        lines.assertFilteredConformsTo(
            isDayChange(JAN_1_2026),
            hasPointer(0),
            isReadMarker(),
            isDayChange(JAN_2_2026),
            hasPointer(1),
        )
    }

    @Test fun `Read marker comes after the last line`() {
        val lines = Lines().apply { displayDayChange = true }
        lines.addLast(makeLine(0, timestamp = JAN_1_2026))
        lines.addLast(makeLine(1, timestamp = JAN_2_2026))
        lines.moveReadMarkerToEnd()

        lines.assertFilteredConformsTo(
            isDayChange(JAN_1_2026),
            hasPointer(0),
            isDayChange(JAN_2_2026),
            hasPointer(1),
            isReadMarker(),
        )
    }

    @Test fun `Squiggle line comes before the day change of the next line`() {
        val lines = Lines().apply { displayDayChange = true }
        lines.addLast(makeLine(0, timestamp = JAN_1_2026))
        lines.updateLastLineInfo(lastPointerServer = 1, lastVisiblePointerServer = 1)
        lines.addLast(makeLine(2, timestamp = JAN_2_2026))

        lines.assertFilteredConformsTo(
            isDayChange(JAN_1_2026),
            hasPointer(0),
            isSquiggle(),
            isDayChange(JAN_2_2026),
            hasPointer(2),
        )
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `Squiggle line appears in both unfiltered and filtered modes`() {
        val lines = Lines()
        lines.addLast(makeLine(0))
        lines.updateLastLineInfo(lastPointerServer = 1, lastVisiblePointerServer = 1)
        lines.addLast(makeLine(2))

        lines.assertUnfilteredConformsTo(
            hasPointer(0),
            isSquiggle(),
            hasPointer(2)
        )

        lines.assertFilteredConformsTo(
            hasPointer(0),
            isSquiggle(),
            hasPointer(2)
        )
    }

    @Test fun `Squiggle line appears only in unfiltered mode`() {
        val lines = Lines()
        lines.addLast(makeLine(0))
        lines.updateLastLineInfo(lastPointerServer = 1, lastVisiblePointerServer = 0)
        lines.addLast(makeLine(2))

        lines.assertUnfilteredConformsTo(
            hasPointer(0),
            isSquiggle(),
            hasPointer(2)
        )

        lines.assertFilteredConformsTo(
            hasPointer(0),
            hasPointer(2),
        )
    }

    @Test fun `Squiggle line never appears doubled around visible lines`() {
        val lines = Lines()
        lines.addLast(makeLine(0))
        lines.updateLastLineInfo(lastPointerServer = 1, lastVisiblePointerServer = 1)
        lines.addLast(makeLine(3))
        lines.updateLastLineInfo(lastPointerServer = 4, lastVisiblePointerServer = 4)
        lines.addLast(makeLine(5))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            isSquiggle(),
            hasPointer(3),
            isSquiggle(),
            hasPointer(5),
        )

        lines.replaceLine(makeLine(3, visible = false))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            isSquiggle(),
            hasPointer(5),
        )
    }

    @Test fun `Squiggle line never appears doubled around invisible lines`() {
        val lines = Lines()
        lines.addLast(makeLine(0))
        lines.updateLastLineInfo(lastPointerServer = 1, lastVisiblePointerServer = 1)
        lines.updateLastLineInfo(lastPointerServer = 2, lastVisiblePointerServer = 2)
        lines.addLast(makeLine(3, visible = false))
        lines.updateLastLineInfo(lastPointerServer = 4, lastVisiblePointerServer = 2)
        lines.addLast(makeLine(5))

        lines.assertUnfilteredConformsTo(
            hasPointer(0),
            isSquiggle(),
            hasPointer(3),
            isSquiggle(),
            hasPointer(5)
        )

        lines.assertFilteredConformsTo(
            hasPointer(0),
            isSquiggle(),
            hasPointer(5),
        )

        lines.replaceLine(makeLine(3, visible = true))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            isSquiggle(),
            hasPointer(3),
            isSquiggle(),
            hasPointer(5)
        )
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `replaceLine() works for lines before unfiltered & filtered squiggle`() {
        val lines = Lines()
        lines.addLast(makeLine(0))
        lines.addLast(makeLine(1, visible = false))
        lines.updateLastLineInfo(lastPointerServer = 2, lastVisiblePointerServer = 2)
        lines.addLast(makeLine(3))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            isSquiggle(),
            hasPointer(3)
        )

        lines.replaceLine(makeLine(1, visible = true))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            hasPointer(1),
            isSquiggle(),
            hasPointer(3)
        )

        lines.replaceLine(makeLine(1, visible = false))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            isSquiggle(),
            hasPointer(3)
        )
    }

    // Not considering here the scenario where
    // updateLastLineInfo is called with lastVisiblePointerServer = 1.
    @Test fun `replaceLine() works for lines before unfiltered-only squiggle`() {
        val lines = Lines()
        lines.addLast(makeLine(0))
        lines.addLast(makeLine(1, visible = false))
        lines.updateLastLineInfo(lastPointerServer = 2, lastVisiblePointerServer = 0)
        lines.addLast(makeLine(3))

        lines.assertUnfilteredConformsTo(
            hasPointer(0),
            hasPointer(1),
            isSquiggle(),
            hasPointer(3)
        )

        lines.assertFilteredConformsTo(
            hasPointer(0),
            hasPointer(3)
        )

        lines.replaceLine(makeLine(1, visible = true))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            hasPointer(1),
            hasPointer(3)
        )

        lines.replaceLine(makeLine(1, visible = false))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            hasPointer(3)
        )
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `replaceLine() works for lines after unfiltered & filtered squiggle`() {
        val lines = Lines()
        lines.addLast(makeLine(0))
        lines.addLast(makeLine(1))
        lines.updateLastLineInfo(lastPointerServer = 2, lastVisiblePointerServer = 2)
        lines.addLast(makeLine(3, visible = false))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            hasPointer(1),
        )

        lines.replaceLine(makeLine(3, visible = true))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            hasPointer(1),
            isSquiggle(),
            hasPointer(3)
        )

        lines.replaceLine(makeLine(3, visible = false))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            hasPointer(1),
        )
    }

    @Test fun `replaceLine() works for lines after unfiltered-only squiggle`() {
        val lines = Lines()
        lines.addLast(makeLine(0))
        lines.addLast(makeLine(1))
        lines.updateLastLineInfo(lastPointerServer = 2, lastVisiblePointerServer = 1)
        lines.addLast(makeLine(3, visible = false))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            hasPointer(1),
        )

        lines.replaceLine(makeLine(3, visible = true))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            hasPointer(1),
            hasPointer(3)
        )

        lines.replaceLine(makeLine(3, visible = false))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            hasPointer(1),
        )
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////


    @Test fun `replaceLine() works sensibly with read marker`() {
        val lines = Lines()
        lines.addLast(makeLine(0))
        lines.addLast(makeLine(1, visible = false))

        lines.assertUnfilteredConformsTo(
            hasPointer(0),
            hasPointer(1),
        )

        lines.moveReadMarkerToEnd()

        lines.assertUnfilteredConformsTo(
            hasPointer(0),
            hasPointer(1),
            isReadMarker(),
        )

        lines.assertFilteredConformsTo(
            hasPointer(0),
            isReadMarker(),
        )

        lines.addLast(makeLine(2, visible = false))
        lines.addLast(makeLine(3, visible = true))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            isReadMarker(),
            hasPointer(3),
        )

        lines.replaceLine(makeLine(1, visible = true))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            hasPointer(1),
            isReadMarker(),
            hasPointer(3),
        )

        lines.replaceLine(makeLine(2, visible = true))

        lines.assertFilteredConformsTo(
            hasPointer(0),
            hasPointer(1),
            isReadMarker(),
            hasPointer(2),
            hasPointer(3),
        )
    }
}
