import cats.Level
import cats.Logger
import cats.ModuleLogger


fun String.has(pattern: String) = pattern
        .split("*")
        .joinToString(separator = ".*", prefix = "^", postfix = "$") { Regex.escape(it) }
        .toRegex()
        .matches(this)

fun List<String>.hasLine(pattern: String) = any { it.has(pattern) }


@ModuleLogger lateinit var logger: Logger


fun withTestLogger(block: OutputHolder.() -> Unit) {
    val output = mutableListOf<String>()

    logger = object : Logger {
        override fun log(level: Level, packageName: String, context: String, throwable: Throwable?, message: () -> String) {
            if (throwable == null) {
                output.add("($level) $context: ${message()}")
            } else {
                output.add("($level) $context: ${message()} ($throwable)")
            }
        }

        override fun logCall(level: Level, packageName: String, context: String, functionName: String, vararg arguments: Any?) {
            output.add("($level) $context: $functionName(${arguments.joinToString()})")
        }
    }

    val outputHolder = object : OutputHolder {
        override val output = output
    }

    outputHolder.block()
}


interface OutputHolder {
    val output: List<String>
}
