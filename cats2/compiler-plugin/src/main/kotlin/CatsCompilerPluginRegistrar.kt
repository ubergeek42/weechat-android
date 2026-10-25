package cats

import cats.fir.LoggerFinderExtension
import cats.ir.LoggingTransformer
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment


@OptIn(ExperimentalCompilerApi::class)
class CatsCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId = "cats"
    override val supportsK2 = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val processTraceAndDebugCalls = configuration[processTraceAndDebugCalls]!!

        FirExtensionRegistrarAdapter.registerExtension(object : FirExtensionRegistrar() {
            override fun ExtensionRegistrarContext.configurePlugin() {
                +::LoggerFinderExtension.bind(configuration)
            }
        })

        IrGenerationExtension.registerExtension(object : IrGenerationExtension {
            override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
                val moduleLoggerCallableId = configuration[moduleLoggerCallableIdKey]!!
                moduleFragment.transform(LoggingTransformer(pluginContext, moduleLoggerCallableId, processTraceAndDebugCalls), null)
            }
        })
    }
}