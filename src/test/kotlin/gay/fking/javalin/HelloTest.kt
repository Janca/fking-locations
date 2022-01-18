package gay.fking.javalin

import gay.fking.javalin.locations.get
import gay.fking.javalin.locations.locations
import gay.fking.javalin.locations.post
import io.javalin.Javalin
import io.javalin.core.util.RouteOverviewPlugin

data class TestA(val test: String = "")

fun main() {

    Javalin.create{
        it.registerPlugin(RouteOverviewPlugin("/routes"))
    }.locations {
        path("/api/v1") {
            path("/users") {
                get<TestA> {

                }

                post<TestA> {

                }
            }
        }
    }.start(8008)

}
