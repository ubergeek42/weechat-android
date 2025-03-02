package com.ubergeek42.WeechatAndroid.relay

import androidx.test.core.app.ApplicationProvider
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.applicationContext
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.function.Predicate


fun makeLine(pointer: Long, message: String = pointer.toString(), visible: Boolean = true) =
    Line(
        pointer = pointer,
        type = LineSpec.Type.IncomingMessage,
        timestamp = 0,
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

fun Lines.assertConformsTo(vararg predicates: Predicate<Line>) {
    getCopyWithoutHeader().zip(predicates).forEachIndexed { index, (line, predicate) ->
        if (!predicate.test(line)) throw AssertionError("Failed at index $index. Lines: ${getCopyWithoutHeader().asString()}")
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