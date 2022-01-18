package gay.fking.javalin.locations

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder
import io.javalin.core.security.RouteRole
import io.javalin.http.Context
import kotlin.reflect.KClass

typealias LocationHandler<T> = LocationHandlerExtended<T, Unit>
typealias LocationHandlerExtended<T, R> = T.(Context) -> R

inline fun <reified T : Any> LocationApiBuilder.get(path: String = "", permittedRoles: Array<RouteRole> = EMPTY_ROLES, noinline handler: LocationHandler<T>) = get(T::class, path, handler, *permittedRoles)
inline fun <reified T : Any> LocationApiBuilder.head(path: String = "", permittedRoles: Array<RouteRole> = EMPTY_ROLES, noinline handler: LocationHandler<T>) = head(T::class, path, handler, *permittedRoles)
inline fun <reified T : Any> LocationApiBuilder.post(path: String = "", permittedRoles: Array<RouteRole> = EMPTY_ROLES, noinline handler: LocationHandler<T>) = post(T::class, path, handler, *permittedRoles)
inline fun <reified T : Any> LocationApiBuilder.put(path: String = "", permittedRoles: Array<RouteRole> = EMPTY_ROLES, noinline handler: LocationHandler<T>) = put(T::class, path, handler, *permittedRoles)
inline fun <reified T : Any> LocationApiBuilder.delete(path: String = "", permittedRoles: Array<RouteRole> = EMPTY_ROLES, noinline handler: LocationHandler<T>) = delete(T::class, path, handler, *permittedRoles)
inline fun <reified T : Any> LocationApiBuilder.patch(path: String = "", permittedRoles: Array<RouteRole> = EMPTY_ROLES, noinline handler: LocationHandler<T>) = patch(T::class, path, handler, *permittedRoles)

object LocationApiBuilder {

    @PublishedApi
    internal val EMPTY_ROLES = emptyArray<RouteRole>()

    fun path(path: String, init: LocationApiBuilder.() -> Unit) {
        ApiBuilder.path(path) {
            init.invoke(LocationApiBuilder)
        }
    }

    @PublishedApi
    internal fun <T : Any, R : Any> get(request: KClass<T>, path: String, handler: LocationHandlerExtended<T, R>, vararg permittedRoles: RouteRole) {
        ApiBuilder.get(path, { ctx ->
            val requestInst = ctx.hydrate(request)
            handler.invoke(requestInst, ctx)
        }, *permittedRoles)
    }

    @PublishedApi
    internal fun <T : Any, R : Any> head(request: KClass<T>, path: String, handler: LocationHandlerExtended<T, R>, vararg permittedRoles: RouteRole) {
        ApiBuilder.head(path, { ctx ->
            val requestInst = ctx.hydrate(request)
            handler.invoke(requestInst, ctx)
        }, *permittedRoles)
    }

    @PublishedApi
    internal fun <T : Any, R : Any> post(request: KClass<T>, path: String, handler: LocationHandlerExtended<T, R>, vararg permittedRoles: RouteRole) {
        ApiBuilder.post(path, { ctx ->
            val requestInst = ctx.hydrate(request)
            handler.invoke(requestInst, ctx)
        }, *permittedRoles)
    }

    @PublishedApi
    internal fun <T : Any, R : Any> put(request: KClass<T>, path: String, handler: LocationHandlerExtended<T, R>, vararg permittedRoles: RouteRole) {
        ApiBuilder.put(path, { ctx ->
            val requestInst = ctx.hydrate(request)
            handler.invoke(requestInst, ctx)
        }, *permittedRoles)
    }

    @PublishedApi
    internal fun <T : Any, R : Any> delete(request: KClass<T>, path: String, handler: LocationHandlerExtended<T, R>, vararg permittedRoles: RouteRole) {
        ApiBuilder.delete(path, { ctx ->
            val requestInst = ctx.hydrate(request)
            handler.invoke(requestInst, ctx)
        }, *permittedRoles)
    }

    @PublishedApi
    internal fun <T : Any, R : Any> patch(request: KClass<T>, path: String, handler: LocationHandlerExtended<T, R>, vararg permittedRoles: RouteRole) {
        ApiBuilder.patch(path, { ctx ->
            val requestInst = ctx.hydrate(request)
            handler.invoke(requestInst, ctx)
        }, *permittedRoles)
    }

}

fun Javalin.locations(init: LocationApiBuilder.() -> Unit): Javalin = apply {
    routes {
        init.invoke(LocationApiBuilder)
    }
}