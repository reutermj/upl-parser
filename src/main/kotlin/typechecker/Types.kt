package typechecker

sealed interface Kind
object TypeKind: Kind
object EntityKind: Kind
object ComponentKind: Kind
object EntityOrComponentKind: Kind
object UndeterminedKind: Kind

sealed interface Type {
    val freeTypeVariables: Set<TypeVariable>
    val kind: Kind
}

object IntType: Type {
    override val freeTypeVariables: Set<TypeVariable>
        get() = setOf()
    override val kind: Kind
        get() = TypeKind

    override fun toString() = "Int"
}
object FloatType: Type {
    override val freeTypeVariables: Set<TypeVariable>
        get() = setOf()
    override val kind: Kind
        get() = TypeKind

    override fun toString() = "Float"
}
object StringType: Type {
    override val freeTypeVariables: Set<TypeVariable>
        get() = setOf()
    override val kind: Kind
        get() = TypeKind

    override fun toString() = "String"
}
object CharType: Type {
    override val freeTypeVariables: Set<TypeVariable>
        get() = setOf()
    override val kind: Kind
        get() = TypeKind

    override fun toString() = "Char"
}
object UnitType: Type {
    override val freeTypeVariables: Set<TypeVariable>
        get() = setOf()
    override val kind: Kind
        get() = TypeKind

    override fun toString() = "Unit"
}
data class ComponentType(val name: String): Type {
    override val freeTypeVariables: Set<TypeVariable>
        get() = setOf()
    override val kind: Kind
        get() = ComponentKind

    override fun toString() = name
}

data class FunctionType(val lhs: Type, val rhs: Type): Type {
    override val freeTypeVariables: Set<TypeVariable>
        get() = lhs.freeTypeVariables + rhs.freeTypeVariables
    override val kind: Kind
        get() = TypeKind

    override fun toString() = "($lhs -> $rhs)"
}
data class SumType(val lhs: Type, val rhs: Type): Type {
    override val freeTypeVariables: Set<TypeVariable>
        get() = lhs.freeTypeVariables + rhs.freeTypeVariables
    override val kind: Kind
        get() = TypeKind

    override fun toString() = "($lhs x $rhs)"
}


data class TypeVariable(val n: Int, override val kind: Kind): Type {
    override val freeTypeVariables: Set<TypeVariable>
        get() = setOf(this)

    override fun toString() =
        when(kind) {
            is UndeterminedKind -> "u$n"
            is TypeKind -> "t$n"
            is EntityOrComponentKind -> "rc$n"
            is EntityKind -> "r$n"
            is ComponentKind -> "c$n"
        }
}
data class TypeScheme(val boundVariable: TypeVariable, val body: Type): Type {
    override val freeTypeVariables: Set<TypeVariable>
        get() = body.freeTypeVariables - boundVariable

    override val kind: Kind
        get() = TypeKind

    override fun toString() = "$boundVariable.$body"
}

data class OpenEntityType(val components: Set<Type>, val row: TypeVariable): Type {
    override val freeTypeVariables: Set<TypeVariable>
        get() = components.fold(row.freeTypeVariables) { acc, type -> acc + type.freeTypeVariables }

    override val kind: Kind
        get() = EntityKind

    override fun toString() =
        when(components.size) {
            0 -> "{row=$row}"
            1 -> {
                val type = components.first()
                "{$type, row=$row}"
            }
            else -> components.fold("{") { acc, type -> "$acc$type, " } + "row=$row}"
        }
}

data class ClosedEntityType(val components: Set<Type>): Type {
    override val freeTypeVariables: Set<TypeVariable>
        get() = components.fold(setOf()) { acc, type -> acc + type.freeTypeVariables }

    override val kind: Kind
        get() = EntityKind

    override fun toString() =
        when(components.size) {
            0 -> "{}"
            1 -> {
                val type = components.first()
                "{$type}"
            }
            else -> components.fold("{") { acc, type -> "$acc$type, " }.dropLast(2) + "}"
        }
}

data class OpenRecordType(val labels: Map<String, Type>, val row: TypeVariable): Type {
    override val freeTypeVariables: Set<TypeVariable>
        get() = labels.values.fold(setOf(row)) { acc, type -> acc + type.freeTypeVariables }

    override val kind: Kind
        get() = ComponentKind

    override fun toString() =
        when(labels.size) {
            0 -> "{row=$row}"
            1 -> {
                val (name, type) = labels.toList().first()
                "{$name: $type, row=$row}"
            }
            else -> labels.toList().fold("{") { acc, pair -> acc + "${pair.first}: ${pair.second}, " }.dropLast(2) + ", row=$row}"
        }
}
data class ClosedRecordType(val name: String, val labels: Map<String, Type>): Type {
    override val freeTypeVariables: Set<TypeVariable>
        get() = labels.values.fold(setOf()) { acc, type -> acc + type.freeTypeVariables }

    override val kind: Kind
        get() = ComponentKind

    override fun toString() = name
}