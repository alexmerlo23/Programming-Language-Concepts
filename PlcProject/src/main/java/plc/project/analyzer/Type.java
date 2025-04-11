package plc.project.analyzer;

import java.util.List;

/**
 * IMPORTANT: DO NOT CHANGE! This file is part of the Analyzer API and should
 * not be modified by your solution.
 */
public sealed interface Type {

    Primitive NIL = new Primitive("Nil");
    Primitive BOOLEAN = new Primitive("Boolean");
    Primitive INTEGER = new Primitive("Integer");
    Primitive DECIMAL = new Primitive("Decimal");
    Primitive STRING = new Primitive("String");

    Primitive ANY = new Primitive("Any");
    Primitive EQUATABLE = new Primitive("Equatable");
    Primitive COMPARABLE = new Primitive("Comparable");
    Primitive ITERABLE = new Primitive("Iterable");

    record Primitive(
        String name
    ) implements Type {}

    record Function(
        List<Type> parameters,
        Type returns
    ) implements Type {}

    record Object(
        Scope scope
    ) implements Type {}

}
