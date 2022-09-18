@file:Suppress("UNCHECKED_CAST")

package gay.fking.javalin.locations

import io.javalin.http.Context
import io.javalin.plugin.json.JSON_MAPPER_KEY
import io.javalin.plugin.json.JsonMapper
import io.javalin.plugin.json.jsonMapper
import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
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
    if (request.java == Void.TYPE) {
        return Unit as T
    }

    val objectInst = request.objectInstance
    if (objectInst != null) {
        return objectInst
    }

    val globalHydration = request.findAnnotation<Hydrate>()
    val globalExplicit = globalHydration?.explicit ?: false

    val body: String?
    val formParameters: Map<String, List<String>>
    val queryParameters: Map<String, List<String>>
    val pathParameters: Map<String, String>

    val jsonMapper: JsonMapper

    val requestInstance: T

    when (this) {
        is Context -> {
            body = body()

            formParameters = formParamMap()
            queryParameters = queryParamMap()
            pathParameters = pathParamMap()

            jsonMapper = jsonMapper()
            requestInstance = createInst(request)
        }

        is WsContext -> {
            formParameters = emptyMap()
            queryParameters = queryParamMap()
            pathParameters = pathParamMap()

            body = when (this) {
                is WsMessageContext -> message()
                else -> null
            }

            jsonMapper = attribute<JsonMapper>(JSON_MAPPER_KEY)!!
            requestInstance = request.createInstance()
        }

        else -> throw IllegalArgumentException()
    }

    val requestProperties: Collection<KProperty1<Any, Any>> = request.declaredMemberProperties as Collection<KProperty1<Any, Any>>

    @Suppress("KotlinConstantConditions") // is this a static inspector error or am i stupid?
    requestProperties.forEach { property ->
        val propertyName = property.name

        val propertyReturnType = property.returnType
        val propertyReturnTypeClassifier = propertyReturnType.classifier
        val isNullable = propertyReturnType.isMarkedNullable

        var explicitlyHydrated = false
        val hydration = property.findAnnotation<Hydrate>()
        val isExplicit = hydration?.explicit ?: DEFAULT_EXPLICIT

        val methods = when {
            globalExplicit -> hydration?.using ?: HydrationMethod.VALUES
            else -> hydration?.using ?: emptyArray()
        }

        val keyAs = hydration?.keyAs.takeIf { !it.isNullOrBlank() } ?: propertyName

        if (propertyReturnTypeClassifier is KClass<*>) {
            val propertyType = when {
                isNullable -> propertyReturnTypeClassifier.createType(nullable = true)
                else -> propertyReturnTypeClassifier.createType()
            }

            if (!KNOWN_TYPES.contains(propertyType)) {
                runCatching<Unit> {
                    val inst = this.hydrate(propertyReturnTypeClassifier)
                    setProperty(property, requestInstance, inst, false)
                    explicitlyHydrated = isExplicit
                }
            }
        }

        val isPathProperty = methods.contains(HydrationMethod.PATH)
        val isQueryProperty = methods.contains(HydrationMethod.QUERY)
        val isBodyProperty = methods.contains(HydrationMethod.BODY)
        val isJSONProperty = methods.contains(HydrationMethod.JSON_BODY) || isBodyProperty
        val isFormProperty = methods.contains(HydrationMethod.FORM_BODY) || isBodyProperty

        if (isPathProperty || (!isExplicit && !explicitlyHydrated)) {
            pathParameters[keyAs]?.let {
                setProperty(property, requestInstance, it)
                explicitlyHydrated = isExplicit
            }
        }

        if (isQueryProperty || (!isExplicit && !explicitlyHydrated)) {
            queryParameters[keyAs]?.let {
                setProperty(property, requestInstance, it)
                explicitlyHydrated = isExplicit
            }
        }

        if (isJSONProperty) {
            body?.let {
                val jsonInst = jsonMapper.fromJsonString(it, propertyReturnType.javaClass)
                setProperty(property, requestInstance, jsonInst)
                explicitlyHydrated = isExplicit
            }
        } else if (isFormProperty) {
            formParameters[keyAs]?.let {
                setProperty(property, requestInstance, it)
                explicitlyHydrated = isExplicit
            }
        } else if (isBodyProperty || (!isExplicit && !explicitlyHydrated)) {
            when (val form = formParameters[keyAs]) {
                null -> {
                    try {
                        body?.let {
                            val jsonInst = jsonMapper.fromJsonString(it, propertyReturnType.javaClass)
                            setProperty(property, requestInstance, jsonInst)
                            explicitlyHydrated = isExplicit
                        }
                    } catch (ignore: Exception) {
                        // NOP
                    }
                }

                else -> {
                    setProperty(property, requestInstance, form)
                    explicitlyHydrated = isExplicit
                }
            }
        }
    }

    return requestInstance
}

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

private fun <T : Any> Context.createInst(request: KClass<T>): T {
    val hydration = request.findAnnotation<Hydrate>()
    val isExplicit = hydration?.explicit ?: DEFAULT_EXPLICIT
    val isBody = hydration?.using?.contains(HydrationMethod.BODY) ?: !isExplicit

    val inst = when {
        isBody -> runCatching {
            jsonMapper().fromJsonString(body(), request.java)
        }.getOrElse { request.createInstance() }

        else -> request.createInstance()
    }

    when (inst) {
        is ContextAwareRequest -> {
            inst.backingContext = this
        }
    }

    return inst
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