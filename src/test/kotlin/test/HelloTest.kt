package test

import gay.fking.javalin.locations.Location
import gay.fking.javalin.locations.get
import gay.fking.javalin.locations.handle
import gay.fking.javalin.locations.locations
import gay.fking.javalin.locations.post
import io.javalin.Javalin
import io.javalin.core.util.RouteOverviewPlugin
import io.javalin.http.HandlerType

@Location
data class TestA(val test: String = "")

fun main() {

    Javalin.create {
        it.registerPlugin(RouteOverviewPlugin("/routes"))
    }.locations {
        path("/api/v1") {
            path("/users") {
                get<TestA> {

                }

                post<TestA> {
                    println("Test string '$test'.")
                    it.result(test)
                }

                handle<TestA>(HandlerType.HEAD, HandlerType.DELETE) {
                    it.status(403)
                }
            }
        }
    }.start(8008)

}
