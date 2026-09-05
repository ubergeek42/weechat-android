package cats

import cats.ir.LoggingTransformer
import cats.ir.findModuleLogger
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment


@OptIn(ExperimentalCompilerApi::class)
class CatsCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId = "cats"
    override val supportsK2 = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(object : IrGenerationExtension {
            override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
                val processTraceAndDebugCalls = configuration[processTraceAndDebugCalls]!!

                val moduleLoggerCallableId = moduleFragment.findModuleLogger()
                moduleFragment.transform(LoggingTransformer(pluginContext, moduleLoggerCallableId, processTraceAndDebugCalls), null)
            }
        })
    }
}