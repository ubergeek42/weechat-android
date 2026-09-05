package cats

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey


val processTraceAndDebugCalls = CompilerConfigurationKey<Boolean>("processTraceAndDebugCalls")


@OptIn(ExperimentalCompilerApi::class)
@Suppress("unused") // Used via reflection
class CatsCommandLineProcessor : CommandLineProcessor {
    override val pluginId = "cats"
    override val pluginOptions: Collection<CliOption> = listOf(
        CliOption(
            optionName = "processTraceAndDebugCalls",
            valueDescription = "bool <true | false>",
            description = "Process trace and debug calls",
            required = true
        )
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        return when (option.optionName) {
            "processTraceAndDebugCalls" -> configuration.put(processTraceAndDebugCalls, value.toBoolean())
            else -> throw IllegalArgumentException("Unexpected config option ${option.optionName}")
        }
    }
}
