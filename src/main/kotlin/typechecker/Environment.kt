package typechecker

import kotlinx.serialization.*
import parser.*

@Serializable
sealed class Environment {
    abstract fun getSpansForId(id: Long): List<Span>
}

@Serializable
data class Subordinate<out T : Environment>(val offset: Span, val environment: T)

@Serializable
data class Requirements(val requirements: Map<BindableToken, List<Type>>) {
    companion object {
        val empty = Requirements(emptyMap())
    }

    operator fun plus(other: Requirements): Requirements {
        val outRequirements = requirements.toMutableMap()
        for ((token, types) in other.requirements)
            outRequirements[token] = (requirements[token] ?: emptyList()) + types

        return Requirements(outRequirements)
    }

    operator fun get(token: BindableToken) = requirements[token]

    fun addRequirement(token: BindableToken, type: Type): Requirements {
        val requirement = (requirements[token] ?: emptyList()) + type
        return copy(requirements = requirements + (token to requirement))
    }

    fun applySubstitution(substitution: Substitution, mapping: MutableMap<Long, Type>) = Requirements(requirements.mapValues { kv -> kv.value.map { substitution.apply(it, mapping) }} )
}

@Serializable
sealed class ExpressionEnvironment : Environment() {
    abstract val expressionType: Type
    abstract val requirements: Requirements
}

@Serializable
data class FunctionCallExpressionEnvironment(
    override val expressionType: Type,
    val functionType: Type,
    override val requirements: Requirements,
    val functionSubordinate: Subordinate<ExpressionEnvironment>,
    val argumentSubordinates: List<Subordinate<ExpressionEnvironment>>
) : ExpressionEnvironment() {
    override fun getSpansForId(id: Long) =
        when (id) {
            functionType.id -> {
                val environment = functionSubordinate.environment
                environment.getSpansForId(environment.expressionType.id).map { functionSubordinate.offset + it }
            }
            else -> functionSubordinate.environment.getSpansForId(id).map { functionSubordinate.offset + it } +
                    argumentSubordinates.flatMap { subordinate ->
                        subordinate.environment.getSpansForId(id).map { subordinate.offset + it }
                    }
        }
}

@Serializable
data class BinaryExpressionEnvironment(
    override val expressionType: Type,
    val opType: Type,
    val opSpan: Span,
    override val requirements: Requirements,
    val lhsSubordinate: Subordinate<ExpressionEnvironment>,
    val rhsSubordinate: Subordinate<ExpressionEnvironment>
) : ExpressionEnvironment() {
    override fun getSpansForId(id: Long) =
        when (id) {
            //todo do these ids need to be different?
            expressionType.id, opType.id -> listOf(opSpan)
            else -> lhsSubordinate.environment.getSpansForId(id).map { lhsSubordinate.offset + it } +
                    rhsSubordinate.environment.getSpansForId(id).map { rhsSubordinate.offset + it }
        }
}

@Serializable
data class UnaryExpressionEnvironment(
    override val expressionType: Type,
    val opType: Type,
    override val requirements: Requirements,
    val subordinate: Subordinate<ExpressionEnvironment>
) : ExpressionEnvironment() {
    override fun getSpansForId(id: Long) =
        when (id) {
            //todo do these ids need to be different?
            expressionType.id, opType.id -> listOf(Span.zero)
            else -> subordinate.environment.getSpansForId(id).map { subordinate.offset + it }
        }
}

@Serializable
data class GroupingExpressionEnvironment(
    override val expressionType: Type,
    override val requirements: Requirements,
    val subordinate: Subordinate<ExpressionEnvironment>
) : ExpressionEnvironment() {
    override fun getSpansForId(id: Long) = subordinate.environment.getSpansForId(id).map { subordinate.offset + it }
}

@Serializable
data class AtomicExpressionEnvironment(override val expressionType: Type, override val requirements: Requirements) :
    ExpressionEnvironment() {
    override fun getSpansForId(id: Long) =
        if (id == expressionType.id) listOf(Span.zero)
        else emptyList()
}

@Serializable
data object EmptyExpressionEnvironment : ExpressionEnvironment() {
    override val expressionType = Invalid(getNextId())
    override val requirements = Requirements.empty
    override fun getSpansForId(id: Long) = emptyList<Span>()
}