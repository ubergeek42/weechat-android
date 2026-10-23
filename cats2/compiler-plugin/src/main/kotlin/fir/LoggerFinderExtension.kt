package cats.fir

import cats.moduleLoggerCallableIdKey
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.name.FqName


private val moduleLoggerAnnotationFqName = FqName("cats.ModuleLogger")

private val moduleLoggerPredicate = LookupPredicate.create { annotated(moduleLoggerAnnotationFqName) }


/**
 * By registering a predicate, we instruct the backend to keep track of the annotation.
 * It is then available when FirExtension is invoked, even during incremental compilation.
 */
class LoggerFinderExtension(session: FirSession, private val configuration: CompilerConfiguration) : SingleInvocationFirExtension(session) {
    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(moduleLoggerPredicate)
    }

    override fun onInvoked() {
        val moduleLoggerSymbol = session.predicateBasedProvider
                .getSymbolsByPredicate(moduleLoggerPredicate)
                .filterIsInstance<FirPropertySymbol>()
                .firstOrNull()
                ?: throw Exception("Couldn't find property annotated with @cats.ModuleLogger")
        configuration.put(moduleLoggerCallableIdKey, moduleLoggerSymbol.callableId!!)
    }
}
