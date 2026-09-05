package cats.ir

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName


private val moduleLoggerAnnotationFqName = FqName("cats.ModuleLogger")


private class LoggerFoundException(val loggerCallableId: CallableId): Exception()


fun IrModuleFragment.findModuleLogger() =
        try {
            accept(LoggerFinder(), null)
            throw Exception("Couldn't find property annotated with @cats.ModuleLogger")
        } catch (e: LoggerFoundException) {
            e.loggerCallableId
        }

// Only visit files, looking for top-level declarations.
//
// Return early if a logger has been found.
// Hopefully, there's only one annotated logger.
//
// Note: we could check the type of logger, but the error at runtime is good enough:
//   java.lang.ClassCastException: class java.lang.Integer cannot be cast to class cats.Logger
//   (java.lang.Integer is in module java.base of loader 'bootstrap';
//   cats.Logger is in unnamed module of loader 'app')
//	   at Outer$Inner.<init>(Tests.kt:42)
@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") // Why call everything `declaration`?
private class LoggerFinder : IrVisitorVoid() {
    override fun visitElement(element: IrElement) {
        if (element is IrModuleFragment || element is IrFile) {
            element.acceptChildrenVoid(this)
        }
    }

    override fun visitProperty(property: IrProperty) {
        if (property.hasAnnotation(moduleLoggerAnnotationFqName)) {
            val propertyParent = property.parent as IrPackageFragment
            val loggerCallableId = CallableId(propertyParent.packageFqName, property.name)
            throw LoggerFoundException(loggerCallableId)
        }
        super.visitProperty(property)
    }
}