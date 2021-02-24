package com.ubergeek42.cats;

import androidx.annotation.NonNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;

import static android.util.Log.DEBUG;
import static android.util.Log.ERROR;
import static com.ubergeek42.cats.Cats.disabled;
import static org.mockito.Mockito.*;

import static android.util.Log.VERBOSE;

@SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CatTest {
    private static @Root Kitty test;
    private Kitty kid;
    private Printer printer;
    private InOrder inOrder;

    // mockable printer that also prints to stdout
    static class SystemOutPrinter extends Printer {
        @Override void println(int priority, String tag, String msg) {
            System.out.format("%s %s %s%n", priority, tag, msg);
        }
    }

    @BeforeEach
    public void setup() {
        disabled.add("*/?");
        test = Kitty.make("Test");
        kid = test.kid("Kid");
        Kitty.printer = printer = spy(SystemOutPrinter.class);
        inOrder = inOrder(printer);
    }

    // checks that the printer printed what it was supposed to print
    // ignores the calculated duration of method in case of exit=true
    private void check(int level, String message) {
        inOrder.verify(printer).println(eq(level), eq("🐱"), argThat(new IgnoringDelayMatcher(message)));
    }

    private void end() {
        inOrder.verifyNoMoreInteractions();
    }

    private static class IgnoringDelayMatcher implements ArgumentMatcher<String> {
        final String expected;

        IgnoringDelayMatcher(String expected) {
            this.expected = normalize(expected);
        }

        @Override public boolean matches(String argument) {
            return expected.equals(normalize(argument));
        }

        @NonNull public String toString() {
            return "\"" + expected + "\"";
        }

        private static String normalize(String message) {
            return message.replaceFirst("^Test :", "main :")
                          .replaceFirst(" \\[\\d+ms]", " [*ms]");
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    static class Static {
        static @Root Kitty static_root = Kitty.make("StaticRoot");
        @Root Kitty root = Kitty.make("Root");
        
        Static() {}
        
        @Cat(exit=true) Static(char z) {
            static_root.tracel("one " + z);
        }

        @Cat static void staticMethod() {}

        @Cat(linger=true) static void staticMethodExit() {
            static_root.trace("one");
        }

        @Cat(exit=true) static void staticMethodError() {
            static_root.tracel("one");
            static_root.errorl("two");
            static_root.tracel("three");
        }

        @CatD(exit=true) int virtualMethod(int x, int y) {
            return x + y;
        }

        class Inner {
            @Root Kitty inner = Kitty.make("Inner");

            Inner() {
                inner.setPrefix("zoo");
            }

            @Cat(exit=true) void replaceFirstArrayValue(int[] arr, int i) {
                arr[0] = i;
            }
            
            @SuppressWarnings("unused")
            @Cat(exit=true) Inner(byte b) {
                // this uses the per-class kitty!
                Cats.getKitty(Inner.class).tracel("one");
            }

            class InnerInnerExtends extends InnerInnerAbstract {
                @Cat(exit=true) @Override int one() {
                    kitty.tracel("one");
                    two();
                    return 100;
                }
            }

            abstract class InnerInnerAbstract {
                @Root Kitty kitty = Kitty.make("InnerInnerAbstract");
                @SuppressWarnings("unused") abstract int one();
                @Cat void two() {}
            }

            class InnerInner {
                @Root Kitty hello = Kitty.make("InnerInner").kid("Kid");

                @CatD(value="Kid", exit=true) int[] returnArray() {
                    hello.debugl("first");
                    return new int[]{-1};
                }
            }

            class InnerInnerSlow {
                @Root Kitty pupper = Inner.this.inner;

                @Cat(exit=true) void slow() {
                    for (int x = 0; x < 1000; x++) one("hello ", new int[] {x, 1, 2});
                }

                @Cat("?") private String one(String foo, int[] z) {
                    return foo + z[0];
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test public void kittyTrace() {
        test.setPrefix("hello");
        test.trace("foo %s", "bar");
        check(VERBOSE,"main : hell : Test: foo \"bar\"");
    }

    @Test public void kittyTraceLinger() {
        test.tracel("first");
        test.trace("second");
        check(VERBOSE, "main : Test: first » second");
    }

    @Test public void kittyDebugKid() {
        kid.debug("first");
        check(DEBUG, "main : Test/Kid: first");
    }

    @Test public void catConstructor() {
        new Static('f');
        check(VERBOSE, "main : StaticRoot: → Static(z=f) » one f ← [0ms]");
    }

    @Test public void catConstructorInner() {
        new Static().new Inner((byte) 12);
        check(VERBOSE, "main : Inner: → Inner(b=0x0C) » one ← [0ms]");
    }
    
    @Test public void catMethod() {
        new Static().virtualMethod(1, 2);
        check(DEBUG, "main : Root: → virtualMethod(x=1, y=2) ← [0ms] ⇒ 3");
    }

    @Test public void catStaticMethod() {
        Static.staticMethod();
        check(VERBOSE, "main : StaticRoot: → staticMethod()");
    }

    @SuppressWarnings("AccessStaticViaInstance")
    @Test public void catStaticMethodExit() {
        new Static().staticMethodExit();
        check(VERBOSE, "main : StaticRoot: → staticMethodExit() » one");
    }

    @Test public void catStaticMethodError() {
        Static.staticMethodError();
        check(VERBOSE, "main : StaticRoot: → staticMethodError() » one ×");
        check(ERROR, "main : StaticRoot: two ×");
        check(VERBOSE, "main : StaticRoot: three ← [0ms]");
        end();
    }

    @Test public void catInnerExit() {
        new Static().new Inner().replaceFirstArrayValue(new int[] {0, 1}, 2);
        check(VERBOSE, "main : zoo  : Inner: → replaceFirstArrayValue(arr=[0, 1], i=2) ← [0ms]");
        end();
    }

    @Test public void catInnerInnerKidExit() {
        new Static().new Inner().new InnerInner().returnArray();
        check(DEBUG, "main : InnerInner/Kid: → returnArray() » first ← [0ms] ⇒ [-1]");
        end();
    }

    @Test public void catExtends() {
        new Static().new Inner().new InnerInnerExtends().one();
        check(VERBOSE, "main : InnerInnerAbstract: → one() » one ×");
        check(VERBOSE, "main : InnerInnerAbstract: → two()");
        check(VERBOSE, "main : InnerInnerAbstract: ← one [0ms] ⇒ 100");
        inOrder.verifyNoMoreInteractions();
    }

    @Test public void catSlow() {
        new Static().new Inner().new InnerInnerSlow().slow();
        check(VERBOSE, "main : zoo  : Inner: → slow() ← [0ms]");
        inOrder.verifyNoMoreInteractions();
    }

}
