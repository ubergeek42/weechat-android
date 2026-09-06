package cats.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension

/**
 * It seems that it's either impossible or very hard to register an extension that is not
 * defined in [org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar.AVAILABLE_EXTENSIONS].
 * This hijacks one of the existing extensions to provide the [onInvoked] method.
 * TODO find a better way to do this?
 */
abstract class SingleInvocationFirExtension(session: FirSession) : FirDeclarationGenerationExtension(session) {
    private var invoked = false

    abstract fun onInvoked()

    @ExperimentalTopLevelDeclarationsGenerationApi
    override fun getTopLevelCallableIds() = super.getTopLevelCallableIds()
            .also {
                if (!invoked) {
                    invoked = true
                    onInvoked()
                }
            }
}
