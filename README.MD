# fking-locations

### Annotation library _loooosely_ based on the Locations feature library from Ktor.

#### Targeting Javalin Version 4.X

```xml
<dependancy>
    <groupId>io.javalin</groupId>
    <artifactId>javalin</artifactId>
    <version>4.6.4</version>
</dependancy>
```

#### Accessible via MyMavenRepo
```xml
<repository>
    <id>fking.gay</id>
    <url>https://mymavenrepo.com/repo/gfyTmeWXHIH7EF6ijloQ/</url>
</repository>
```

```xml
<dependancy>
    <groupId>gay.fking.javalin</groupId>
    <artifactId>fking-locations</artifactId>
    <version>1.3.1</version>
</dependancy>
```

### Basics

```kotlin
Javalin.create()
    .locations {
        // Routing here
    }.start(8080)
```

### HTTP Method Handlers

When using `fking-locations` route handlers, you must define a class type that will be used for hydrating the incoming
request, and passed to the handler as `this`.

```kotlin
/**
 * Without explicitly annotating class with @Hydrate all properties will be implicitly
 * hydrated, using path parameters, query parameters, and the request body.
 */
data class HelloWorld(val name: String? = null)

javalin.locations {
    /**
     * HTTP handler for GET request at index.
     */
    get<HelloWorld>("/") { ctx ->
        when {
            name.isNullOrBlank -> ctx.result("Hello world!")
            else -> ctx.result("Hello, $name!")
        }
    }

    /**
     * HTTP handler for POST request at index.
     */
    post<HelloWorld>("/") {
        when {
            name.isNullOrBlank -> ctx.result("Hello world!")
            else -> ctx.result("Hello, $name!")
        }
    }
}
```

### Hydration and Serialization

The `fking-locations` library by default implicitly hydrates properties on the provided request class.
Meaning it will use path parameters, query parameters, form parameters and even the request body content to hydrate.
To disable this feature explicitly mark class or property with the annotation `@Hydrate`.

Annotating a class with `@Hydrate` will disable all implicit hydration on its properties,
and all properties must be marked with `@Hydrate` annotation to explicitly set hydration methods.

Leaving the annotation field `@Hydrate.using` blank, or as default, will be as if you have enabled implicit hydration on
that property, or class.

#### Hydration Methods

- IGNORED _Hydration is disabled for this property._
- PATH
- QUERY
- JSON_BODY
- FORM_BODY
- BODY _(Includes any request body content; form, JSON, plain text, etc...)_

```kotlin
/**
 * Defining class with annotation @Hydrate disabled implicit hydration.
 * This request will only hydrate using the request body content.
 */
@Hydrate(using = [HydrationMethod.BODY])
data class AuthenticationRequest(val username: String? = null, val password: String? = null)
```

```kotlin
/**
 * Class properties `title` and `body` will be hydrated using form body content.
 * Class property `category` will be hydrated using Javalin path properties.
 */
@Hydrate(using = [HydrationMethod.FORM_BODY])
data class PostTopicRequest(
    @Hydrate(using = [HydrationMethod.PATH]) val category: Int = -1,
    val title: String? = null,
    val body: String? = null
)
```

### Pathing

The `fking-locations` library has several pathing methods for routing HTTP handlers, including nesting.

```kotlin
javalin.locations {
    // GET /
    get<Unit> { ctx ->
        ctx.result('Hello world')
    }

    path("/api/v1") {
        path("/authentication") {
            // POST /api/v1/authentication/login
            post<AuthenticationRequest>("/login") { ctx ->
                when {
                    username.isNullOrBlank() || password.isNullOrBlank() -> ctx.statusCode(400)
                        .result("Invalid request.")
                    else -> {
                        // etc...
                    }
                }
            }

            // POST /api/v1/authentication/refresh-token
            post<RefreshAccessTokenRequest>("/refresh-token") { ctx ->
                // etc...
            }
        }

        path("/topics") {
            // GET /api/v1/topics/{topicId}
            get<Topic>("/{topicId}") { ctx ->
                // etc
            }

            // POST /api/v1/topics
            post<CreateTopic> { ctx ->

            }
        }
    }

    path("/api/v2") {
        // HEAD /api/v2/status
        head<Unit>("status") { ctx ->
            ctx.status(200)
        }

        // /api/v2/topics
        path("/topics") {
            // etc...
        }
    }
}
```

### All-Purpose Handler

You can create a generic handler that can handle one or more HTTP method.

```kotlin
javalin.locations {
    // GET, POST /api/v1/topic/{topicId}
    handle<Topic>("/api/v1/topic/{topicId}", HandlerType.GET, HandlerType.POST) { ctx ->
        // etc...
    }

    // Expand handler types example
    // GET, POST, HEAD, PUT /api/v1/topic/{topicId}
    handle<Topic>("/api/v1/topic/{topicId}", HandlerType.GET, HandlerType.POST, HandlerType.HEAD, HandlerType.PUT) { ctx ->
        // etc...
    }
}
```

### Access Control

All handler methods supported an argument for permitted Javalin `RouteRole` implementations.

```kotlin
javalin.locations {
    handle<Topic>("/api/v1/topic/{topicId}", HandlerType.GET, HandlerType.POST, permittedRoles = arrayOf(ForumRole.GUEST, ForumRole.USER)) { ctx ->
        // etc...
    }

    // ANOTHER EXAMPLE
    post<Topic>("/api/v1/topic/{topicId}", permittedRoles = arrayOf(ForumRole.GUEST, ForumRole.USER)) { ctx ->
        // etc...
    }
}
```

### Accessing Context from Request Class

You might want to write additional properties inside your request class that uses information
from the current `Context`. You can gain access by extending the class `ContextAwareRequest`.

```kotlin
class SpecialRoute : ContextAwareRequest() {
    // context is available here as a protected property
    // IMPORTANT: context is only provided after hydration is complete
    // you will either have to use functions or val getters
    // or a lateinit exception will be thrown
    val authorizationToken
        get() = context.header("Authorization")
            .takeIf { !it.isNullOrEmpty() && it.startsWith("Bearer") }
            ?.substring(7)
}
```