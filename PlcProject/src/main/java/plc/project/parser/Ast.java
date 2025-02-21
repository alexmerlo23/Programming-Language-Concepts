package plc.project.parser;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.Optional;

public sealed interface Ast {

    record Source(
        List<Stmt> statements
    ) implements Ast {}

    sealed interface Stmt extends Ast {

        record Let(
            String name,
            Optional<Expr> value
        ) implements Stmt {}

        record Def(
            String name,
            List<String> parameters,
            List<Stmt> body
        ) implements Stmt {}

        record If(
            Expr condition,
            List<Stmt> thenBody,
            List<Stmt> elseBody
        ) implements Stmt {}

        record For(
            String name,
            Expr expression,
            List<Stmt> body
        ) implements Stmt {}

        record Return(
            Optional<Expr> value
        ) implements Stmt {}

        record Expression(
            Expr expression
        ) implements Stmt {}

        record Assignment(
            Expr expression,
            Expr value
        ) implements Stmt {}

    }

    sealed interface Expr extends Ast {

        record Literal(
            @Nullable Object value
        ) implements Expr {}

        record Group(
            Expr expression
        ) implements Expr {}

        record Binary(
            String operator,
            Expr left,
            Expr right
        ) implements Expr {}

        record Variable(
            String name
        ) implements Expr {}

        record Property(
            Expr receiver,
            String name
        ) implements Expr {}

        record Function(
            String name,
            List<Expr> arguments
        ) implements Expr {}

        record Method(
            Expr receiver,
            String name,
            List<Expr> arguments
        ) implements Expr {}

        //Using "ObjectExpr" to avoid confusion with Java's "Object"
        record ObjectExpr(
            Optional<String> name,
            List<Stmt.Let> fields,
            List<Stmt.Def> methods
        ) implements Expr {}

    }

}
