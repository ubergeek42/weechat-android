package com.ubergeek42.cats;

import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.runners.MockitoJUnitRunner;

import static android.util.Log.DEBUG;
import static android.util.Log.ERROR;
import static org.mockito.Mockito.*;

import static android.util.Log.VERBOSE;
import static com.ubergeek42.cats.Cats.TAG;

@SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
@RunWith(MockitoJUnitRunner.class)
public class CatTest {

    private static Kitty kitty;
    private Kitty boop;
    private Printer printer;
    private InOrder inOrder;

    static class SystemOutPrinter extends Printer {
        @Override void println(int priority, String tag, String msg) {
            System.out.println(String.format("%s %s %s", priority, tag, msg));
        }
    }

    @Before
    public void setup() {
        kitty = Kitty.make("Test");
        boop = kitty.kid("Boop");
        Kitty.printer = printer = spy(SystemOutPrinter.class);
        inOrder = inOrder(printer);
    }

    private void check(int level, String tag, String message) {
        inOrder.verify(printer).println(eq(level), eq(tag), argThat(new IgnoringDelayMatcher(message)));
    }

    private static class IgnoringDelayMatcher extends ArgumentMatcher<String> {
        final String expected;

        IgnoringDelayMatcher(String expected) {
            this.expected = normalize(expected);
        }

        @Override public boolean matches(Object argument) {
            return expected.equals(normalize((String) argument));
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("\"" + expected + "\"");
        }

        private static String normalize(String message) {
            return message.replaceFirst(" \\[\\d+ms]", " [*ms]");
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test public void kittyTrace() {
        kitty.setPrefix("hello");
        kitty.trace("foo %s", "bar");
        check(VERBOSE, TAG, "main : hello : Test: foo \"bar\"");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test public void kittyTraceLinger() {
        kitty.tracel("first");
        kitty.trace("second");
        check(VERBOSE, TAG, "main : Test: first » second");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test public void kittyDebugKid() {
        boop.debug("first");
        check(DEBUG, TAG, "main : Test/Boop: first");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test public void catMethod() {
        add(1, 2);
        check(VERBOSE, TAG, "main : Test: → add(x=1, y=2)");
    }

    @Cat private int add(int x, int y) {
        return x + y;
    }

    @Test public void catConstructor() {
        new Constructing();
        check(VERBOSE, TAG, "main : Test: → Constructing() » constructing!");
    }

    class Constructing {
        @Cat(exit=true) public Constructing() {
            kitty.trace("constructing!");
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test public void catStaticMethodExit() {
        addStaticExit(1, 2);
        check(VERBOSE, TAG, "main : Test: → addStaticExit(x=1, y=2) ← [1ms] ⇒ 3");
    }

    @Cat(exit = true)
    private static int addStaticExit(int x, int y) {
        return x + y;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test public void catStaticLinger() {
        StaticClass.staticMethod();
        check(DEBUG, TAG, "main : Test: → staticMethod() » first ×");
        check(ERROR, TAG, "main : Test: last");
        inOrder.verifyNoMoreInteractions();
    }

    private static class StaticClass {
        @CatD(linger=true)
        static void staticMethod() {
            kitty.debugl("first");
            kitty.error("last");
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test public void catNonStaticExit() {
        new NonStatic("one").replaceFirstArrayValue(new int[] {0, 1}, 2);
        check(VERBOSE, TAG, "main : one  : NonStatic: → replaceFirstArrayValue(arr=[0, 1], i=2) ← [0ms]");
        inOrder.verifyNoMoreInteractions();
    }

    @Test public void catNonStaticInnerKidExit() {
        new NonStatic("two").new Inner().returnArray();
        check(DEBUG, TAG, "main : two  : NonStatic/Kid: → returnArray() » first ← [0ms] ⇒ [-1]");
        inOrder.verifyNoMoreInteractions();
    }

    public class NonStatic {
        Kitty kitty = Kitty.make("NonStatic");
        Kitty kid = kitty.kid("Kid");

        NonStatic(String name) {
            kitty.setPrefix(name);
        }

        @Cat(exit=true)
        void replaceFirstArrayValue(int[] arr, int i) {
            arr[0] = i;
        }

        class Inner {
            @CatD(value = "Kid", exit=true)
            int[] returnArray() {
                kid.debugl("first");
                return new int[]{-1};
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test public void catStaticWithKittyPrefixInnerExit() {
        new StaticWithKitty("three").new Lyx().test();
        check(VERBOSE, TAG, "main : three : StaticWithKitty: → test() ← [0ms]");
        inOrder.verifyNoMoreInteractions();
    }

    public static class StaticWithKitty {
        Kitty kitty = Kitty.make("StaticWithKitty");

        StaticWithKitty(String name) {
            kitty.setPrefix(name);
        }

        class Lyx {
            @Cat(exit=true)
            void test() {}
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test public void catComplexExtends() {
        new Static().new NonStaticOuter().new NonStaticInner().new NonStaticInnerInnerWithKitty().one();
        check(VERBOSE, TAG, "main : NonStaticInnerInnerAbstract: → one() » first ×");
        check(VERBOSE, TAG, "main : NonStaticInnerInnerAbstract: → two()");
        check(VERBOSE, TAG, "main : NonStaticInnerInnerAbstract: second ← [0ms] ⇒ 100");
        inOrder.verifyNoMoreInteractions();
    }

    @Test public void catComplex() {
        new Static().new NonStaticOuter().new NonStaticInner().new NonStaticInnerInner().one();
        check(VERBOSE, TAG, "main : NonStaticOuter: → one()");
        inOrder.verifyNoMoreInteractions();
    }

    @SuppressWarnings("unused")
    static class Static {                        // CatTest.this = null (can't reference from static context)
        class NonStaticOuter {                   // Static = this$0
            Kitty kitty = Kitty.make("NonStaticOuter");

            class NonStaticInner {               // NonStaticOuter = this$1
                class NonStaticInnerInnerWithKitty extends NonStaticInnerInnerAbstract {      // NonStaticInnerInnerWithKitty = this$2
                    @Cat(exit=true) @Override int one() {
                        kitty.tracel("first");
                        two();
                        kitty.tracel("second");
                        return 100;
                    }
                }

                abstract class NonStaticInnerInnerAbstract {
                    Kitty kitty = Kitty.make("NonStaticInnerInnerAbstract");
                    abstract int one();
                    @Cat void two() {}
                }

                class NonStaticInnerInner {
                    @Cat void one() {}
                }
            }
        }
    }
}
