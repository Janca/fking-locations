package gay.fking.javalin.locations

import io.javalin.core.security.RouteRole

inline fun <reified T : Any, R : Any> LocationApiBuilder.get(path: String = "", permittedRoles: Array<RouteRole> = EMPTY_ROLES, noinline handler: LocationHandlerExtended<T, R>) = get(T::class, path, handler, *permittedRoles)
inline fun <reified T : Any, R : Any> LocationApiBuilder.head(path: String = "", permittedRoles: Array<RouteRole> = EMPTY_ROLES, noinline handler: LocationHandlerExtended<T, R>) = head(T::class, path, handler, *permittedRoles)
inline fun <reified T : Any, R : Any> LocationApiBuilder.post(path: String = "", permittedRoles: Array<RouteRole> = EMPTY_ROLES, noinline handler: LocationHandlerExtended<T, R>) = post(T::class, path, handler, *permittedRoles)
inline fun <reified T : Any, R : Any> LocationApiBuilder.put(path: String = "", permittedRoles: Array<RouteRole> = EMPTY_ROLES, noinline handler: LocationHandlerExtended<T, R>) = put(T::class, path, handler, *permittedRoles)
inline fun <reified T : Any, R : Any> LocationApiBuilder.delete(path: String = "", permittedRoles: Array<RouteRole> = EMPTY_ROLES, noinline handler: LocationHandlerExtended<T, R>) = delete(T::class, path, handler, *permittedRoles)
inline fun <reified T : Any, R : Any> LocationApiBuilder.patch(path: String = "", permittedRoles: Array<RouteRole> = EMPTY_ROLES, noinline handler: LocationHandlerExtended<T, R>) = patch(T::class, path, handler, *permittedRoles)
