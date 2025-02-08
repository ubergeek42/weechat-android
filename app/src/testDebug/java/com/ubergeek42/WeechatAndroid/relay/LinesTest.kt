package com.ubergeek42.WeechatAndroid.relay

import androidx.test.core.app.ApplicationProvider
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.applicationContext
import org.junit.Before
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
}