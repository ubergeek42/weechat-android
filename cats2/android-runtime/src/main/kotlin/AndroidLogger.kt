package cats

import android.util.Log


/**
 * Creates a logger that writes directly to LogCat.
 * The resulting Java bytecode is minimal and ready for production.
 *
 * The provided block allows for configuring the tag based on the package name, e.g.
 *
 *     @ModuleLogger
 *     val logger = androidLogger { packageName ->
 *         when {
 *             packageName.startsWith("my.app") -> "App"
 *             else -> ...
 *         }
 *     }
 *
 *  In this block, make sure to only use logic that can be optimized with R8.
 *  The block uses `java.lang.String` as Kotlin's `startsWith` is not optimized by R8.
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
inline fun androidLogger(crossinline block: (packageName: java.lang.String) -> String) =
    // Both methods inlined with an R8 rule
    object : Logger {
        override fun log(level: Level, packageName: String, context: String, throwable: Throwable?, message: () -> String) {
            log(level, block(packageName as java.lang.String), throwable, "$context: ${message()}")
        }

        override fun logCall(level: Level, packageName: String, context: String, functionName: String, vararg arguments: Any?) {
            log(level, block(packageName as java.lang.String), null, "$context: → $functionName(${arguments.joinToString()})")
        }
    }


// Inlined with an R8 rule
fun log(level: Level, tag: String, throwable: Throwable?, message: String) {
    if (throwable == null) {
        when (level) {
            Level.Trace -> Log.v(tag, message)
            Level.Debug -> Log.d(tag, message)
            Level.Info  -> Log.i(tag, message)
            Level.Warn  -> Log.w(tag, message)
            Level.Error -> Log.e(tag, message)
            Level.Wtf   -> Log.wtf(tag, message)
        }
    } else {
        when (level) {
            Level.Trace -> Log.v(tag, message, throwable)
            Level.Debug -> Log.d(tag, message, throwable)
            Level.Info  -> Log.i(tag, message, throwable)
            Level.Warn  -> Log.w(tag, message, throwable)
            Level.Error -> Log.e(tag, message, throwable)
            Level.Wtf   -> Log.wtf(tag, message, throwable)
        }
    }
}
