# Add project specific ProGuard rules here.
# For more details, see http://developer.android.com/guide/developing/tools/proguard.html

# The below rules allow for the calls such as `trace {}`
# to be translated to the platform calls with nearly no overhead.
# This works as some of the arguments for these methods are static at compile time
# and the resulting logic can be removed completely.

-alwaysinline class cats.AndroidLoggerKt {
    public static void log(...);
}

-alwaysinline class * implements cats.Logger {
    <methods>;
}