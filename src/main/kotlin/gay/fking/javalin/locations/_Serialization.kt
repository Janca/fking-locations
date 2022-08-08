@file:Suppress("UNCHECKED_CAST")

package gay.fking.javalin.locations

import io.javalin.http.Context
import io.javalin.plugin.json.jsonMapper
import io.javalin.websocket.WsContext
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure

internal fun <T : Any> WsContext.hydrate(request: KClass<T>): T {
    return (this as Any).hydrate(request)
}

internal fun <T : Any> Context.hydrate(request: KClass<T>): T {
    return (this as Any).hydrate(request)
}

private fun <T : Any> Any.hydrate(request: KClass<T>): T {
    val objectInst = request.objectInstance
    if (objectInst != null) {
        return objectInst
    }

    val formParameters: Map<String, List<String>>
    val queryParameters: Map<String, List<String>>
    val pathParameters: Map<String, String>

    val requestInstance: T

    when (this) {
        is Context -> {
            formParameters = formParamMap()
            queryParameters = queryParamMap()
            pathParameters = pathParamMap()
            requestInstance = createInstance(request)
        }

        is WsContext -> {
            formParameters = emptyMap()
            queryParameters = queryParamMap()
            pathParameters = pathParamMap()
            requestInstance = request.createInstance()
        }

        else -> throw IllegalArgumentException()
    }

    val allParameters = HashMap<String, Any>()
        .apply {
            putAll(formParameters)
            putAll(queryParameters)
            putAll(pathParameters)
        }

    val requestProperties: Collection<KProperty1<Any, Any>> = request.declaredMemberProperties as Collection<KProperty1<Any, Any>>
    val requestIgnoreParamAnnot = request.findAnnotation<IgnoreParameterType>()

    requestProperties.forEach { property ->
        var hydrated = false
        val propertyName = property.name

        val propertyReturnType = property.returnType
        val propertyReturnTypeClassifier = propertyReturnType.classifier
        val isNullable = propertyReturnType.isMarkedNullable

        if (propertyReturnTypeClassifier is KClass<*>) {
            val propertyType = when {
                isNullable -> propertyReturnTypeClassifier.createType(nullable = true)
                else -> propertyReturnTypeClassifier.createType()
            }

            if (!KNOWN_TYPES.contains(propertyType)) {
                runCatching<Unit> {
                    val inst = this.hydrate(propertyReturnTypeClassifier)
                    setProperty(property, requestInstance, inst, false)
                    hydrated = true
                }
            }
        }

        property.findAnnotation<QueryParameter>()?.let { annot ->
            val hydrateKey = annot.name.takeIf { it.isNotBlank() } ?: propertyName
            queryParameters[hydrateKey]?.let {
                setProperty(property, requestInstance, it)
                hydrated = true
            }
        }

        property.findAnnotation<FormParameter>()?.let { annot ->
            val hydrateKey = annot.name.takeIf { it.isNotBlank() } ?: propertyName
            formParameters[hydrateKey]?.let {
                setProperty(property, requestInstance, it)
                hydrated = true
            }
        }

        property.findAnnotation<PathParameter>()?.let { annot ->
            val hydrateKey = annot.name.takeIf { it.isNotBlank() } ?: propertyName
            pathParameters[hydrateKey]?.let {
                setProperty(property, requestInstance, it)
                hydrated = true
            }
        }

        property.findAnnotation<PostParameter>()?.let { annot ->
            when (this) {
                is Context -> {
                    runCatching<Unit> {
                        val body = body()
                        when {
                            body.isNotBlank() -> {
                                val type = ((property.returnType.classifier!!) as KClass<*>).java
                                val inst = jsonMapper().fromJsonString(body, type)
                                setProperty(property, requestInstance, inst, false)
                                hydrated = true
                            }
                        }
                    }

                    return@let
                }
            }
        }

        if (!hydrated && request.eagerlyHydrating) {
            val propIgnoreParamAnnot = property.findAnnotation<IgnoreParameterType>()
            when {
                requestIgnoreParamAnnot == null && propIgnoreParamAnnot == null -> {
                    allParameters[propertyName]?.let {
                        setProperty(property, requestInstance, it)
                    }
                }

                else -> {
                    val pathParamHydrationAllowed = requestIgnoreParamAnnot?.types?.contains(PathParameter::class)
                        ?: propIgnoreParamAnnot?.types?.contains(PathParameter::class) ?: true

                    val queryParamHydrationAllowed = requestIgnoreParamAnnot?.types?.contains(QueryParameter::class)
                        ?: propIgnoreParamAnnot?.types?.contains(QueryParameter::class) ?: true

                    val formParamHydrationAllowed = requestIgnoreParamAnnot?.types?.contains(FormParameter::class)
                        ?: propIgnoreParamAnnot?.types?.contains(FormParameter::class) ?: true

                    if (queryParamHydrationAllowed) {
                        queryParameters[propertyName]?.let {
                            setProperty(property, requestInstance, it)
                        }
                    }

                    if (formParamHydrationAllowed) {
                        formParameters[propertyName]?.let {
                            setProperty(property, requestInstance, it)
                        }
                    }

                    if (pathParamHydrationAllowed) {
                        pathParameters[propertyName]?.let {
                            setProperty(property, requestInstance, it)
                        }
                    }
                }
            }
        }
    }

    return requestInstance
}

private val <T : Any> KClass<T>.eagerlyHydrating: Boolean get() = !this.hasAnnotation<DisableEagerHydration>()

private val BYTE_TYPE = Byte::class.createType()
private val SHORT_TYPE = Short::class.createType()
private val INT_TYPE = Int::class.createType()
private val DOUBLE_TYPE = Double::class.createType()
private val FLOAT_TYPE = Float::class.createType()
private val LONG_TYPE = Long::class.createType()
private val BOOLEAN_TYPE = Boolean::class.createType()
private val STRING_TYPE = String::class.createType()

private val NULLABLE_BYTE_TYPE = Byte::class.createType(nullable = true)
private val NULLABLE_SHORT_TYPE = Short::class.createType(nullable = true)
private val NULLABLE_INT_TYPE = Int::class.createType(nullable = true)
private val NULLABLE_DOUBLE_TYPE = Double::class.createType(nullable = true)
private val NULLABLE_FLOAT_TYPE = Float::class.createType(nullable = true)
private val NULLABLE_LONG_TYPE = Long::class.createType(nullable = true)
private val NULLABLE_BOOLEAN_TYPE = Boolean::class.createType(nullable = true)
private val NULLABLE_STRING_TYPE = String::class.createType(nullable = true)

private val BYTE_ARRAY_TYPE = ByteArray::class.createType()
private val SHORT_ARRAY_TYPE = ShortArray::class.createType()
private val INT_ARRAY_TYPE = IntArray::class.createType()
private val DOUBLE_ARRAY_TYPE = DoubleArray::class.createType()
private val FLOAT_ARRAY_TYPE = FloatArray::class.createType()
private val LONG_ARRAY_TYPE = LongArray::class.createType()
private val BOOLEAN_ARRAY_TYPE = BooleanArray::class.createType()

private val KNOWN_TYPES = arrayOf(
    BYTE_TYPE,
    SHORT_TYPE,
    INT_TYPE,
    DOUBLE_TYPE,
    FLOAT_TYPE,
    LONG_TYPE,
    BOOLEAN_TYPE,
    STRING_TYPE,
    NULLABLE_BYTE_TYPE,
    NULLABLE_SHORT_TYPE,
    NULLABLE_INT_TYPE,
    NULLABLE_DOUBLE_TYPE,
    NULLABLE_FLOAT_TYPE,
    NULLABLE_LONG_TYPE,
    NULLABLE_BOOLEAN_TYPE,
    NULLABLE_STRING_TYPE,
    BYTE_ARRAY_TYPE,
    SHORT_ARRAY_TYPE,
    INT_ARRAY_TYPE,
    DOUBLE_ARRAY_TYPE,
    FLOAT_ARRAY_TYPE,
    LONG_ARRAY_TYPE,
    BOOLEAN_ARRAY_TYPE
)

fun <T : Any> KClass<T>.createInstance(): T {
    // RIPPED FROM KOTLIN-SDK
    val noArgsConstructor = constructors.singleOrNull { it.parameters.all(KParameter::isOptional) }
        ?: throw IllegalArgumentException("Class should have a single no-arg constructor: $this")

    return noArgsConstructor.also {
        it.isAccessible = true // make accessible
    }.callBy(emptyMap())
}


private fun <T : Any> Context.createInstance(request: KClass<T>): T {
    return when {
        request.eagerlyHydrating || request.hasAnnotation<PostBody>() -> try {
            jsonMapper().fromJsonString(body(), request.java)
        } catch (e: Exception) {
            request.createInstance()
        }

        else -> request.createInstance()
    }.also {
        if (it is ContextAwareLocation) {
            it.backingContext = this
        }
    }
}

private fun <V : Any> setProperty(property: KProperty1<Any, V>, instance: Any, value: Any, cast: Boolean = true) {
    try {
        val type = property.returnType
        val hydrated: V = when (cast) {
            true -> value.cast(type) ?: return
            else -> value as V
        }

        when (property) {
            is KMutableProperty1<Any, V> -> property.set(instance, hydrated)
            else -> {
                val backingField = property.javaField ?: return
                backingField.isAccessible = true
                backingField.set(instance, hydrated)
            }
        }
    } catch (ignore: Exception) {
    }
}

private fun <V : Any> Any.cast(type: KType, debug: Boolean = false): V? {
    return when (this) {
        is String -> this.cast(type) as V?
        is List<*> -> this.cast(type) as V?
        else -> throw IllegalArgumentException()
    }
}

private fun <V : Any> String.cast(type: KType): V? {
    return when (type) {
        STRING_TYPE, NULLABLE_STRING_TYPE -> this

        BYTE_TYPE -> this.toByte()
        SHORT_TYPE -> this.toShort()
        INT_TYPE -> this.toInt()
        DOUBLE_TYPE -> this.toDouble()
        FLOAT_TYPE -> this.toFloat()
        LONG_TYPE -> this.toLong()

        BOOLEAN_TYPE -> when {
            this.isEmpty() -> true
            else -> when (this.toIntOrNull()) {
                null -> this.toBoolean()
                1 -> true
                else -> false
            }
        }

        NULLABLE_BOOLEAN_TYPE -> when {
            this.isEmpty() -> null
            else -> when (this.toIntOrNull()) {
                null -> this.toBooleanStrictOrNull()
                1 -> true
                else -> false
            }
        }

        NULLABLE_BYTE_TYPE -> this.toByteOrNull()
        NULLABLE_SHORT_TYPE -> this.toShortOrNull()
        NULLABLE_INT_TYPE -> this.toIntOrNull()
        NULLABLE_DOUBLE_TYPE -> this.toDoubleOrNull()
        NULLABLE_FLOAT_TYPE -> this.toFloatOrNull()
        NULLABLE_LONG_TYPE -> this.toLongOrNull()

        else -> when {
            type.jvmErasure.java.isArray -> this.castArray(type)
            else -> throw IllegalArgumentException("Unsupported type. [${type.classifier}]")
        }
    } as V?
}

private fun <V : Any> List<*>.cast(type: KType): V? {
    val firstType = type.arguments.firstOrNull()?.type ?: type
    val casts = this.stream()

    return when {
        type.jvmErasure.isSubclassOf(List::class) -> casts.collect(Collectors.toList()) as V
        type.jvmErasure.java.isArray -> casts.castArray(type) as V
        else -> casts.map { it?.cast<V>(firstType) }.limit(1).findFirst().orElse(null)
    }
}

private fun Stream<*>.castArray(type: KType): Any {
    val values = toArray()
    val length = values.size

    fun kotlinArray(): Any {
        return when (type) {
            BYTE_ARRAY_TYPE -> ByteArray(length) { values[it].cast(BYTE_TYPE)!! }
            SHORT_ARRAY_TYPE -> ShortArray(length) { values[it].cast(SHORT_TYPE)!! }
            INT_ARRAY_TYPE -> IntArray(length) { values[it].cast(INT_TYPE)!! }
            DOUBLE_ARRAY_TYPE -> DoubleArray(length) { values[it].cast(DOUBLE_TYPE)!! }
            FLOAT_ARRAY_TYPE -> FloatArray(length) { values[it].cast(FLOAT_TYPE)!! }
            LONG_ARRAY_TYPE -> LongArray(length) { values[it].cast(LONG_TYPE)!! }
            BOOLEAN_ARRAY_TYPE -> BooleanArray(length) { values[it].cast(BOOLEAN_TYPE)!! }
            else -> throw IllegalArgumentException("Unsupported array type. [${type.classifier}]")
        }
    }

    val typeArguments = type.arguments
    return when {
        typeArguments.isNotEmpty() -> values.cast(type)
        else -> kotlinArray()
    }
}

private fun Array<*>.cast(type: KType): Any {
    val arrayType = type.arguments.first().type!!.classifier as KClass<*>
    val typedArray: Array<Any> = java.lang.reflect.Array.newInstance(arrayType.java.kotlin.java, size) as Array<Any>
    for (i in 0..lastIndex) {
        typedArray[i] = this[i]?.cast(arrayType.createType())!!
    }

    return typedArray
}

private fun String.castArray(type: KType): Any {
    fun String.kotlinArray(): Any {
        return when (type) {
            BYTE_ARRAY_TYPE -> ByteArray(1) { this.cast(BYTE_TYPE)!! }
            SHORT_ARRAY_TYPE -> ShortArray(1) { this.cast(SHORT_TYPE)!! }
            INT_ARRAY_TYPE -> IntArray(1) { this.cast(INT_TYPE)!! }
            DOUBLE_ARRAY_TYPE -> DoubleArray(1) { this.cast(DOUBLE_TYPE)!! }
            FLOAT_ARRAY_TYPE -> FloatArray(1) { this.cast(FLOAT_TYPE)!! }
            LONG_ARRAY_TYPE -> LongArray(1) { this.cast(LONG_TYPE)!! }
            BOOLEAN_ARRAY_TYPE -> BooleanArray(1) { this.cast(BOOLEAN_TYPE)!! }
            else -> throw IllegalArgumentException("Unsupported array type. [${type.classifier}]")
        }
    }

    val typeArguments = type.arguments
    return when {
        typeArguments.isNotEmpty() -> {
            val arrayType = type.arguments.first().type!!.classifier as KClass<*>
            val typedArray = java.lang.reflect.Array.newInstance(arrayType.java, 1)
            java.lang.reflect.Array.set(typedArray, 0, this.cast(arrayType.java.kotlin.createType()))
            typedArray
        }

        else -> this.kotlinArray()
    }
}