package cats

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption


@Suppress("unused") // Used with reflection
class CatsCompilerPlugin : KotlinCompilerPluginSupportPlugin {
    override fun getCompilerPluginId() = "cats"

    // TODO Apply to tests conditionally
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>) = !kotlinCompilation.isTest

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(groupId = "cats", artifactId = "compiler-plugin", version = "unspecified")

    // TODO Use an extension to fetch `processTraceAndDebugCalls`
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        project.dependencies.add("implementation", "cats:runtime")
        return project.provider { listOf(SubpluginOption(key="processTraceAndDebugCalls", value = kotlinCompilation.isDebug.toString())) }
    }
}


val KotlinCompilation<*>.isTest get() = name.contains("test", ignoreCase = true)
val KotlinCompilation<*>.isDebug get() = name.contains("debug", ignoreCase = true) || isTest
