package typechecker

import kotlinx.serialization.*

@Serializable
sealed class Lookup: LookupIntermediate()
@Serializable
sealed class LookupIntermediate
@Serializable
data class Representative(val typeVar: TypeVariable): Lookup()
@Serializable
data class TypeMapping(val type: Type): Lookup()
@Serializable
data class Intermediate(val dst: UnificationTypeVariable): LookupIntermediate()
@Serializable
data class Substitution(val mapping: Map<TypeVariable, LookupIntermediate>) {
    companion object {
        val empty = Substitution(emptyMap())
    }

    operator fun plus(other: Substitution) = Substitution(mapping + other.mapping)

    fun unify(lType: Type, rType: Type): Substitution =
        when {
            lType == rType -> this
            lType is UnificationTypeVariable -> unifyUnificationTypeVariable(lType, rType)
            rType is UnificationTypeVariable -> unifyUnificationTypeVariable(rType, lType)
            lType is ArrowType && rType is ArrowType -> unify(lType.lhs, rType.lhs).unify(lType.rhs, rType.rhs)
            lType is ProductType && rType is ProductType -> {
                if(lType.types.size != rType.types.size) TODO()
                lType.types.zip(rType.types).fold(this) { acc, pair -> acc.unify(pair.first, pair.second) }
            }
            else ->
                TODO()
        }


    fun unifyUnificationTypeVariable(lType: UnificationTypeVariable, rType: Type): Substitution =
        when(val lLookup = find(lType)) {
            is TypeMapping -> unify(rType, lLookup.type)
            is Representative ->
                when(rType) {
                    is UnificationTypeVariable ->
                        when(val rLookup = find(rType)) {
                            is Representative -> pointAt(lLookup.typeVar, rLookup.typeVar)
                            is TypeMapping -> pointAt(lLookup.typeVar, rLookup.type)
                        }
                    else -> pointAt(lLookup.typeVar, rType)
                }
        }

    fun apply(type: Type, mapping: MutableMap<String, Type>): Type =
        when(type) {
            is ConcreteType -> type
            //todo should I be copying the old id or creating a new one???
            is ArrowType -> ArrowType(type.id, apply(type.lhs, mapping), apply(type.rhs, mapping))
            is ProductType -> ProductType(type.id, type.types.map { apply(it, mapping) })
            is UserDefinedTypeVariable -> type
            is UnificationTypeVariable ->
                when(val result = find(type)) {
                    is Representative -> {
                        if(type != result.typeVar) mapping[result.typeVar.id] = type
                        result.typeVar
                    }
                    is TypeMapping -> {
                        val outType = apply(result.type, mapping)
                        mapping[outType.id] = type
                        outType
                    }
                }
            is TypeScheme -> {
                //For co-contextual type inference, TypeSchemes abstract over all type variables from the function
                //Many of the variables only exist due to unresolved references to other functions
                //Once these references are resolved, the type variables can be discharged from the TypeScheme
                val typeVars = type.boundVariables.fold(emptySet<TypeVariable>()) { acc, typeVar ->
                    val applied = apply(typeVar, mapping)
                    if(applied is TypeVariable) acc + applied
                    else acc
                }
                //todo should I be copying the old id or creating a new one???
                if(typeVars.any()) TypeScheme(type.id, typeVars.toList().sortedBy { it.id }, apply(type.type, mapping))
                else apply(type.type, mapping)
            }
            else -> TODO()
        }

    fun find(typeVar: UnificationTypeVariable): Lookup {
        var tv = typeVar
        while(true) {
            when(val result = mapping[tv] ?: Representative(tv)) {
                is Lookup -> return result
                is Intermediate -> tv = result.dst
            }
        }
    }

    fun pointAt(src: TypeVariable, dst: Type) =
        when(dst) {
            src -> this
            is UnificationTypeVariable -> Substitution(mapping + (src to Intermediate(dst)))
            else -> Substitution(mapping + (src to TypeMapping(dst)))
        }
}
