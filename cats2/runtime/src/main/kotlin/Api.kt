package cats


fun trace(throwable: Throwable? = null, message: () -> String) {}
fun debug(throwable: Throwable? = null, message: () -> String) {}
fun info(throwable: Throwable? = null, message: () -> String) {}
fun warn(throwable: Throwable? = null, message: () -> String) {}
fun err(throwable: Throwable? = null, message: () -> String) {}
fun wtf(throwable: Throwable? = null, message: () -> String) {}


@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.SOURCE)
annotation class Trace

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.SOURCE)
annotation class Debug

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Name(val name: String)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Suffix

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ModuleLogger


interface Logger {
    fun log(level: Level, packageName: String, context: String, throwable: Throwable?, message: () -> String)
    fun logCall(level: Level, packageName: String, context: String, functionName: String, vararg arguments: Any?)
}


enum class Level {
    Trace,
    Debug,
    Info,
    Warn,
    Error,
    Wtf,
}
