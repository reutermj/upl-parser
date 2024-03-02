package typechecker

import kotlinx.serialization.*
import parser.*

@Serializable
sealed class Environment {
    abstract fun getSpansForId(id: Long): List<Span>
}

@Serializable
data class Subordinate<out T: Environment>(val offset: Span, val environment: T)

@Serializable
data class Requirements(val requirements: Map<BindableToken, List<Type>>) {
    companion object {
        val empty = Requirements(emptyMap())
    }

    operator fun plus(other: Requirements): Requirements {
        val outRequirements = requirements.toMutableMap()
        for((token, types) in other.requirements)
            outRequirements[token] = (requirements[token] ?: emptyList()) + types

        return Requirements(outRequirements)
    }

    operator fun get(token: BindableToken) = requirements[token]

    fun addRequirement(token: BindableToken, type: Type): Requirements {
        val requirement = (requirements[token] ?: emptyList()) + type
        return copy(requirements = requirements + (token to requirement))
    }
}

@Serializable
sealed class ExpressionEnvironment: Environment() {
    abstract val type: Type
    abstract val requirements: Requirements
}

@Serializable
data class UnaryExpressionEnvironment(override val type: Type, val opType: Type, override val requirements: Requirements, val subordinate: Subordinate<ExpressionEnvironment>): ExpressionEnvironment() {
    override fun getSpansForId(id: Long) =
        when(id) {
            type.id, opType.id -> listOf(Span.zero)
            else -> subordinate.environment.getSpansForId(id).map { subordinate.offset + it }
        }
}

@Serializable
data class GroupingExpressionEnvironment(override val type: Type, override val requirements: Requirements, val subordinate: Subordinate<ExpressionEnvironment>): ExpressionEnvironment() {
    override fun getSpansForId(id: Long) = subordinate.environment.getSpansForId(id).map { subordinate.offset + it }
}

@Serializable
data class AtomicExpressionEnvironment(override val type: Type, override val requirements: Requirements): ExpressionEnvironment() {
    override fun getSpansForId(id: Long) =
        if(id == type.id) listOf(Span.zero)
        else emptyList()
}

@Serializable
data object EmptyExpressionEnvironment: ExpressionEnvironment() {
    override val type = Invalid(getNextId())
    override val requirements = Requirements.empty
    override fun getSpansForId(id: Long) = emptyList<Span>()
}