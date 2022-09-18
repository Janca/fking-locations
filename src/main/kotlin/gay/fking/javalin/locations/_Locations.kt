package gay.fking.javalin.locations

import io.javalin.http.Context

internal const val DEFAULT_EXPLICIT = true

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Hydrate(
    val keyAs: String = "",
    val explicit: Boolean = DEFAULT_EXPLICIT,
    vararg val using: HydrationMethod = [HydrationMethod.QUERY, HydrationMethod.PATH, HydrationMethod.BODY]
)

enum class HydrationMethod {
    IGNORED,
    QUERY,
    PATH,
    JSON_BODY,
    FORM_BODY,
    BODY;

    companion object {
        internal val VALUES = arrayOf(QUERY, PATH, BODY)
    }
}

@DslMarker
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class LocationDsl

abstract class ContextAwareRequest {
    internal lateinit var backingContext: Context
    protected val context: Context get() = backingContext
}