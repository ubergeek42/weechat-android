@file:Suppress("RedundantInnerClassModifier", "RedundantSuspendModifier", "unused")

import cats.Debug
import cats.Name
import cats.Suffix
import cats.Trace
import cats.info
import cats.wtf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test


@Trace fun blockBodyFunction(int: Int) {
    info { "Function called" }
}

@Trace fun expressionBodyFunction(int: Int) = 0.also {
    wtf(Exception("Bad value!")) { "Error while setting value" }
}

@Trace suspend fun suspendedFunction(int: Int) = 0

class Outer {
    @Suffix val suffix = "suffix"

    inner class Inner @Trace constructor() {
        @Debug fun inner() {}
    }

    class NotInner {
        @Trace fun notInner() {
            class Local { @Trace fun local() {} }
            Local().local()
        }
    }

    @Name("Renamed")
    inner class Named {
        var property = 42
            @Trace set
    }

    val property = info { "Outside of a function" }

    val anotherProperty = 42.also @Trace {
        info { "Inside an anonymous function" }
    }

    fun adjacentToSuffix() {
        info { "Inside a method adjacent to suffix" }
    }

    fun adjacentToSuffixWithLocal() = run {
        info { "Inside a local function in a method adjacent to suffix" }
    }
}


class Tests {
    @Test fun `Block body function is traced`() = withTestLogger {
        blockBodyFunction(42)
        assert(output[0] == "(Trace) Tests.kt: blockBodyFunction(42)")
    }

    @Test fun `Expression body function is traced`() = withTestLogger {
        expressionBodyFunction(42)
        assert(output[0] == "(Trace) Tests.kt: expressionBodyFunction(42)")
    }

    @Test fun `Suspended function is traced`() = withTestLogger {
        runBlocking { suspendedFunction(42) }
        assert(output.hasLine("(Trace) Tests.kt: suspendedFunction(42)"))
    }

    @Test fun `Constructor is traced`() = withTestLogger {
        Outer().Inner()
        assert(output.hasLine("*<init>()"))
    }

    @Test fun `A function in a local class in a not inner class is traced`() = withTestLogger {
        Outer.NotInner().notInner()
        assert(output.hasLine("(Trace) Outer/NotInner/Local: local()"))
    }

    @Test fun `Property setter is traced`() = withTestLogger {
        Outer().Named().property = 69
        assert(output.hasLine("*<set-property>(69)"))
    }

    @Test fun `Anonymous functions are traced`() = withTestLogger {
        Outer()
        assert(output.hasLine("(Trace) Outer:suffix: <anonymous>(42)"))
    }


    @Test fun `Log calls are transformed`() = withTestLogger {
        blockBodyFunction(42)
        assert(output.hasLine("(Info) Tests.kt: Function called"))
    }

    @Test fun `Log calls with throwables are transformed`() = withTestLogger {
        expressionBodyFunction(42)
        assert(output.hasLine("(Wtf) Tests.kt: Error while setting value (*Exception: Bad value!)"))
    }

    @Test fun `Log calls work inside a method adjacent to suffix`() = withTestLogger {
        Outer().adjacentToSuffix()
        assert(output.hasLine("(Info) Outer:suffix: Inside a method adjacent to suffix"))
    }

    @Test fun `Log calls work inside a local function in a method adjacent to suffix`() = withTestLogger {
        Outer().adjacentToSuffixWithLocal()
        assert(output.hasLine("(Info) Outer:suffix: Inside a local function in a method adjacent to suffix"))
    }

    @Test fun `Log calls work outside of a function`() = withTestLogger {
        Outer()
        assert(output.hasLine("(Info) Outer:suffix: Outside of a function"))
    }

    @Test fun `Log calls work inside anonymous functions`() = withTestLogger {
        Outer()
        assert(output.hasLine("(Info) Outer:suffix: Inside an anonymous function"))
    }


    @Test fun `Inner class uses the suffix of the outer`() = withTestLogger {
        Outer().Inner().inner()
        assert(output.hasLine("*Outer:suffix/Inner: <init>()"))
        assert(output.hasLine("*Outer:suffix/Inner: inner()"))
    }

    @Test fun `Not inner class does not use the suffix of the outer`() = withTestLogger {
        Outer.NotInner().notInner()
        assert(output.hasLine("*Outer/NotInner: notInner()*"))
    }

    @Test fun `Classes can be renamed for the purpose of logging`() = withTestLogger {
        Outer().Named().property = 69
        assert(output.hasLine("*Outer:suffix/Renamed*"))
    }
}
