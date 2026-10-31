@file:OptIn(UnsafeDuringIrConstructionAPI::class) // owner, declaration

package cats.ir

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrEnumEntrySymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.ir.util.parentAsClass


fun IrClassSymbol.getEnumEntrySymbol(name: String) = owner.declarations
        .filterIsInstance<IrEnumEntry>()
        .first { entry -> entry.name.asString() == name }
        .symbol


fun IrBuilder.irGetEnumValue(enumEntrySymbol: IrEnumEntrySymbol): IrGetEnumValue =
    IrGetEnumValueImpl(
        startOffset = startOffset,
        endOffset = endOffset,
        type = enumEntrySymbol.owner.parentAsClass.defaultType,
        symbol = enumEntrySymbol
    )


fun IrDeclaration.generateSequenceOfSelfAndParents() =
        generateSequence<IrElement>(this) { if (it is IrDeclaration) it.parent else null }


/**
 * Consider the following code.
 *
 *     class Outer {
 *         val suffix = "suffix"
 *
 *         fun outer() { // can access suffix through its dispatchReceiverParameter
 *             run {} // can access suffix through outer's (!!) dispatchReceiverParameter
 *         }
 *
 *         inner class Inner {
 *             fun inner() {} // can access suffix through Outer.thisReceiver
 *         }
 *
 *         class NotInner {
 *             fun notInner() { // cannot access suffix!
 *                 class Local {
 *                     fun local() {} // cannot access suffix!
 *                 }
 *             }
 *         }
 *     }
 *
 * While `inner()` *can* access `suffix` via `thisReceiver` (`this@Outer`), `outer()` can't.
 * Instead, `outer()` must use its `dispatchReceiverParameter`.
 * It seems that the reason for this is that `this@Outer` population
 * is explicitly addressed by `InnerClassLowering`, which doesn't happen for `outer()`.
 * Perhaps this may change in the future.
 *
 * Any local functions inside of `outer {}` must use the `dispatchReceiverParameter` of `outer`.
 *
 * Not-inner (nested) classes obviously do not have the access to the outer instance,
 * nor do any local classes inside them.
 *
 * Any classes inside of classes inside a function can't be not-inner, e.g.
 *
 *     fun function() {
 *         class Local {
 *             inner class SubLocal // must be inner!
 *         }
 *     }
 *
 * TODO figure out if there is a more robust or a simpler strategy.
 *   See also [org.jetbrains.kotlin.ir.validation.checkers.context.CheckerContext.withScopeOwner]
 *   See also [org.jetbrains.kotlin.ir.validation.checkers.context.ValueScopeUpdater]
 */
fun IrDeclaration.getThisReceiverForClassIfReachableOrNull(klass: IrClass): IrValueParameter? {
    val sequenceOfSelfAndParents = generateSequenceOfSelfAndParents()

    val classCanBeReached = sequenceOfSelfAndParents
            .filterIsInstance<IrClass>()
            .dropWhile { (it.isInner || it.isLocal) && it != klass }
            .firstOrNull() == klass

    val innermostNonlocalFunction = sequenceOfSelfAndParents
            .firstOrNull { it is IrFunction && !it.isLocal } as? IrFunction

    return when {
        !classCanBeReached                         -> null
        innermostNonlocalFunction?.parent == klass -> innermostNonlocalFunction.dispatchReceiverParameter
        else                                       -> klass.thisReceiver
    }
}
