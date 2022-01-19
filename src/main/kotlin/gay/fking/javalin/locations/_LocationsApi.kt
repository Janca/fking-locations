package gay.fking.javalin.locations

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder
import io.javalin.core.security.RouteRole
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HandlerType
import kotlin.reflect.KClass

typealias LocationHandler<T> = T.(Context) -> Unit

inline fun <reified T : Any> LocationApiBuilder.get(path: String = "", permittedRoles: Array<out RouteRole> = EMPTY_ROLES, noinline handler: LocationHandler<T>) = get(T::class, path, permittedRoles, handler)
inline fun <reified T : Any> LocationApiBuilder.head(path: String = "", permittedRoles: Array<out RouteRole> = EMPTY_ROLES, noinline handler: LocationHandler<T>) = head(T::class, path, permittedRoles, handler)
inline fun <reified T : Any> LocationApiBuilder.post(path: String = "", permittedRoles: Array<out RouteRole> = EMPTY_ROLES, noinline handler: LocationHandler<T>) = post(T::class, path, permittedRoles, handler)
inline fun <reified T : Any> LocationApiBuilder.put(path: String = "", permittedRoles: Array<out RouteRole> = EMPTY_ROLES, noinline handler: LocationHandler<T>) = put(T::class, path, permittedRoles, handler)
inline fun <reified T : Any> LocationApiBuilder.delete(path: String = "", permittedRoles: Array<out RouteRole> = EMPTY_ROLES, noinline handler: LocationHandler<T>) = delete(T::class, path, permittedRoles, handler)
inline fun <reified T : Any> LocationApiBuilder.patch(path: String = "", permittedRoles: Array<out RouteRole> = EMPTY_ROLES, noinline handler: LocationHandler<T>) = patch(T::class, path, permittedRoles, handler)
inline fun <reified T : Any> LocationApiBuilder.options(path: String = "", permittedRoles: Array<out RouteRole> = EMPTY_ROLES, noinline handler: LocationHandler<T>) = options(T::class, path, permittedRoles, handler)

inline fun <reified T : Any> LocationApiBuilder.path(path: String, permittedRoles: Array<out RouteRole> = EMPTY_ROLES, vararg method: HandlerType, noinline handler: LocationHandler<T>) = fkHandle(T::class, path, method, permittedRoles, handler)
inline fun <reified T : Any> LocationApiBuilder.handle(vararg method: HandlerType, permittedRoles: Array<out RouteRole> = EMPTY_ROLES, noinline handler: LocationHandler<T>) = fkHandle(T::class, "", method, permittedRoles, handler)

object LocationApiBuilder {

    @PublishedApi
    internal val EMPTY_ROLES = emptyArray<RouteRole>()

    @PublishedApi
    internal val ALL_HTTP_METHODS by lazy { HandlerType.values().filter { it.isHttpMethod() }.toTypedArray() }

    fun path(path: String, init: LocationApiBuilder.() -> Unit) {
        ApiBuilder.path(path) {
            init.invoke(LocationApiBuilder)
        }
    }

    @PublishedApi
    internal fun <T : Any> fkHandle(request: KClass<T>, path: String, methods: Array<out HandlerType>, permittedRoles: Array<out RouteRole>, handler: LocationHandler<T>) {
        val httpMethods = methods.takeIf { it.isNotEmpty() } ?: ALL_HTTP_METHODS
        httpMethods.asSequence().filter { it.isHttpMethod() }.forEach {
            when (it) {
                HandlerType.GET -> get(request, path, EMPTY_ROLES, handler)
                HandlerType.POST -> post(request, path, EMPTY_ROLES, handler)
                HandlerType.PUT -> put(request, path, EMPTY_ROLES, handler)
                HandlerType.PATCH -> patch(request, path, EMPTY_ROLES, handler)
                HandlerType.DELETE -> delete(request, path, EMPTY_ROLES, handler)
                HandlerType.HEAD -> head(request, path, EMPTY_ROLES, handler)
                else -> throw IllegalArgumentException("HandlerType '${it}' is not one of: GET, POST, PATCH, PUT, DELETE, HEAD")
            }
        }
    }

    @PublishedApi
    internal fun <T : Any> get(request: KClass<T>, path: String, permittedRoles: Array<out RouteRole>, handler: LocationHandler<T>) {
        ApiBuilder.get(path, handler.toJavalinHandler(request), *permittedRoles)
    }

    @PublishedApi
    internal fun <T : Any> head(request: KClass<T>, path: String, permittedRoles: Array<out RouteRole>, handler: LocationHandler<T>) {
        ApiBuilder.head(path, handler.toJavalinHandler(request), *permittedRoles)
    }

    @PublishedApi
    internal fun <T : Any> post(request: KClass<T>, path: String, permittedRoles: Array<out RouteRole>, handler: LocationHandler<T>) {
        ApiBuilder.post(path, handler.toJavalinHandler(request), *permittedRoles)
    }

    @PublishedApi
    internal fun <T : Any> put(request: KClass<T>, path: String, permittedRoles: Array<out RouteRole>, handler: LocationHandler<T>) {
        ApiBuilder.put(path, handler.toJavalinHandler(request), *permittedRoles)
    }

    @PublishedApi
    internal fun <T : Any> delete(request: KClass<T>, path: String, permittedRoles: Array<out RouteRole>, handler: LocationHandler<T>) {
        ApiBuilder.delete(path, handler.toJavalinHandler(request), *permittedRoles)
    }

    @PublishedApi
    internal fun <T : Any> patch(request: KClass<T>, path: String, permittedRoles: Array<out RouteRole>, handler: LocationHandler<T>) {
        ApiBuilder.patch(path, handler.toJavalinHandler(request), *permittedRoles)
    }

    @PublishedApi
    internal fun <T : Any> options(request: KClass<T>, path: String, permittedRoles: Array<out RouteRole>, handler: LocationHandler<T>) {
        ApiBuilder.staticInstance().options(ApiBuilder.prefixPath(path), handler.toJavalinHandler(request), *permittedRoles)
    }

    private fun <T : Any> LocationHandler<T>.toJavalinHandler(request: KClass<T>): Handler {
        return Handler { ctx ->
            val requestInt = ctx.hydrate(request)
            invoke(requestInt, ctx)
        }
    }
}

fun Javalin.locations(init: LocationApiBuilder.() -> Unit): Javalin = apply {
    routes {
        init.invoke(LocationApiBuilder)
    }
}