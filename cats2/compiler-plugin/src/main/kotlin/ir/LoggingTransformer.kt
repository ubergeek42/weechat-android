@file:OptIn(UnsafeDuringIrConstructionAPI::class) // owner, declaration

package cats.ir

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irConcat
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrEnumEntrySymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getValueArgument
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isAnonymousObject
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name


private val traceAnnotationFqName = FqName("cats.Trace")
private val debugAnnotationFqName = FqName("cats.Debug")

private val nameAnnotationFqName = FqName("cats.Name")
private val suffixAnnotationFqName = FqName("cats.Suffix")

private val traceFqName = FqName("cats.trace")
private val debugFqName = FqName("cats.debug")
private val infoFqName = FqName("cats.info")
private val warnFqName = FqName("cats.warn")
private val errFqName = FqName("cats.err")
private val wtfFqName = FqName("cats.wtf")

private val logCallableId = CallableId(FqName("cats"), FqName("Logger"), Name.identifier("log"))
private val logCallCallableId = CallableId(FqName("cats"), FqName("Logger"), Name.identifier("logCall"))
private val levelEnumClassId = ClassId(FqName("cats"), Name.identifier("Level"))


@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") // Why call everything `declaration`?
class LoggingTransformer(val pluginContext: IrPluginContext, val moduleLoggerCallableId: CallableId, val processTraceAndDebugCalls: Boolean)
        : IrElementTransformerVoidWithContext() {

    @Suppress("PropertyName") // Allow uppercase
    inner class Symbols(file: IrFile) {
        private val finder by lazy { pluginContext.finderForSource(file) }

        val logger: IrPropertySymbol by lazy { finder.findProperties(moduleLoggerCallableId).first() }
        val log: IrSimpleFunctionSymbol by lazy { finder.findFunctions(logCallableId).first() }
        val logCall: IrSimpleFunctionSymbol by lazy { finder.findFunctions(logCallCallableId).first() }
        val Level: IrClassSymbol by lazy { finder.findClass(levelEnumClassId)!! }
        val Trace: IrEnumEntrySymbol by lazy { Level.getEnumEntrySymbol("Trace") }
        val Debug: IrEnumEntrySymbol by lazy { Level.getEnumEntrySymbol("Debug") }
        val Info: IrEnumEntrySymbol by lazy { Level.getEnumEntrySymbol("Info") }
        val Warn: IrEnumEntrySymbol by lazy { Level.getEnumEntrySymbol("Warn") }
        val Error: IrEnumEntrySymbol by lazy { Level.getEnumEntrySymbol("Error") }
        val Wtf: IrEnumEntrySymbol by lazy { Level.getEnumEntrySymbol("Wtf") }
    }

    lateinit var symbols: Symbols

    override fun visitFileNew(file: IrFile): IrFile {
        symbols = Symbols(file)
        return super.visitFileNew(file)
    }

    override fun visitFunctionNew(function: IrFunction): IrStatement {
        if (!processTraceAndDebugCalls) {
            return super.visitFunctionNew(function)
        }

        val (annotation, levelSymbol) =
                   function.getAnnotation(traceAnnotationFqName)?.let { it to symbols.Trace }
                ?: function.getAnnotation(debugAnnotationFqName)?.let { it to symbols.Debug }
                ?: return super.visitFunctionNew(function)

        // This places the injected call right at the annotation in the source code.
        // In the debugger, this creates another step just for this call.
        // If the offset is undefined, there is no extra step,
        // and the injected call runs simultaneously with the first statement of the function.
        val builder = pluginContext.irBuiltIns.createIrBuilder(function.symbol, annotation.startOffset, annotation.endOffset)

        val injectedCall = with(builder) {
            irCall(symbols.logCall).apply {
                arguments[0] = irCall(symbols.logger.owner.getter!!) // logger (dispatch receiver)
                arguments[1] = irGetEnumValue(levelSymbol) // level
                arguments[2] = irString(currentPackageFragment.packageFqName.toString()) // packageName
                arguments[3] = makeContext(function) // context
                arguments[4] = irString(function.name.asString()) // functionName
                arguments[5] = irVararg( // arguments
                    elementType = pluginContext.irBuiltIns.anyNType,
                    values = function.parameters
                            .filter { it.kind == IrParameterKind.Regular } // Skip this/extension
                            .map { irGet(it) }
                )
            }
        }

        // With K2, we shouldn't actually see `IrExpressionBody` (e.g. `fun foo() = 1`),
        // as at this stage those should be already converted to `IrBlockBody`.
        when (val body = function.body) {
            is IrBlockBody -> body.statements.add(0, injectedCall)
            is IrExpressionBody -> function.body = builder.irBlockBody {
                +injectedCall
                +irReturn(body.expression)
            }
            else -> throw Exception("Function annotated with @cats.Trace, etc must have ether a block or expression body")
        }

        return super.visitFunctionNew(function)
    }

    override fun visitCall(oldCall: IrCall): IrExpression {
        val levelSymbol = when (oldCall.symbol.owner.kotlinFqName) {
            traceFqName -> symbols.Trace
            debugFqName -> symbols.Debug
            infoFqName -> symbols.Info
            warnFqName -> symbols.Warn
            errFqName  -> symbols.Error
            wtfFqName  -> symbols.Wtf
            else -> return super.visitCall(oldCall)
        }

        if (!processTraceAndDebugCalls && (levelSymbol == symbols.Trace || levelSymbol == symbols.Debug)) {
            return super.visitCall(oldCall)
        }

        val builder = pluginContext.irBuiltIns.createIrBuilder(oldCall.symbol, oldCall.startOffset, oldCall.endOffset)

        val newCall = with(builder) {
            irCall(symbols.log).apply {
                arguments[0] = irCall(symbols.logger.owner.getter!!) // logger (dispatch receiver)
                arguments[1] = irGetEnumValue(levelSymbol) // level
                arguments[2] = irString(currentPackageFragment.packageFqName.toString()) // packageName
                arguments[3] = makeContext(currentDeclaration) // context
                arguments[4] = oldCall.arguments[0] ?: irNull() // throwable
                arguments[5] = oldCall.arguments[1] // message block
            }
        }

        return super.visitCall(newCall)
    }

    val currentDeclaration get() = allScopes.last { it.irElement is IrDeclaration }.irElement as IrDeclaration
    val currentPackageFragment get() = allScopes.last { it.irElement is IrPackageFragment }.irElement as IrPackageFragment

    /**
     * For a declaration, return a string or a concatenation in the form of either:
     *   * "Class" or "OuterClass/InnerClass/..." if the declaration is in a class, or
     *   * "File.kt" if the declaration is file-level or inside an anonymous object.
     *
     * The class label will be "Foo" if the class is annotated with `@Name("Foo")`.
     *
     * The class label will take the form of "Class:$suffix"
     * if it has a reachable property annotated with `@Suffix`.
     *
     * Implementation note:
     *   The return value in most cases is a concatenation.
     *   If there are no suffixes, it's okay to use concatenation anyway,
     *   as R8/ProGuard is smart enough to optimize it into a constant later.
     */
    @Suppress("OPT_IN_USAGE") // .properties
    private fun IrBuilderWithScope.makeContext(declaration: IrDeclaration): IrExpression {
        val concat = irConcat()

        fun prependSuffixIfPresentAndReachable(klass: IrClass) {
            klass.properties.find { it.hasAnnotation(suffixAnnotationFqName) }?.let { property ->
                declaration.getThisReceiverForClassIfReachableOrNull(klass)?.let { receiver ->
                    val getSuffix = irCall(property.getter!!.symbol).apply { arguments[0] = irGet(receiver) }
                    concat.arguments.addFirst(getSuffix)
                    concat.arguments.addFirst(irString(":"))
                }
            }
        }

        fun prependName(klass: IrClass) {
            val name = when (val annotation = klass.getAnnotation(nameAnnotationFqName)) {
                null -> klass.name.asString()
                else -> (annotation.getValueArgument(Name.identifier("name")) as IrConst).value as String
            }
            concat.arguments.addFirst(irString(name))
            concat.arguments.addFirst(irString("/"))
        }

        declaration.generateSequenceOfSelfAndParents()
                .filterIsInstance<IrClass>()
                .filterNot { it.isAnonymousObject }
                .forEach { klass ->
                    prependSuffixIfPresentAndReachable(klass)
                    prependName(klass)
                }

        return when {
            concat.arguments.isNotEmpty() -> concat.apply { arguments.removeFirst() } // Remove the leading "/"
            else -> irString(currentFile.name) // Includes ".kt"
        }
    }
}