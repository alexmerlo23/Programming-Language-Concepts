package plc.project.analyzer;

import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Optional;

public final class Analyzer implements Ast.Visitor<Ir, AnalyzeException> {

    private Scope scope;
    private boolean insideFunction;

    public Analyzer(Scope scope) {
        this.scope = scope;
        this.insideFunction = false;
    }

    @Override
    public Ir.Source visit(Ast.Source ast) throws AnalyzeException {
        var statements = new ArrayList<Ir.Stmt>();
        for (var statement : ast.statements()) {
            statements.add(visit(statement));
        }
        return new Ir.Source(statements);
    }

    private Ir.Stmt visit(Ast.Stmt ast) throws AnalyzeException {
        return (Ir.Stmt) visit((Ast) ast); //helper to cast visit(Ast.Stmt) to Ir.Stmt
    }

    @Override
    public Ir.Stmt.Let visit(Ast.Stmt.Let ast) throws AnalyzeException {
        Type type;
        Optional<Ir.Expr> value = Optional.empty();

        if (ast.type().isPresent()) {
            String typeName = ast.type().get();
            if (!Environment.TYPES.containsKey(typeName)) {
                throw new AnalyzeException("Undefined type: " + typeName);
            }
            type = Environment.TYPES.get(typeName);
        } else if (ast.value().isPresent()) {
            Ir.Expr expr = visit(ast.value().get());
            type = expr.type();
            value = Optional.of(expr);
        } else {
            type = Type.ANY;
        }

        if (ast.value().isPresent() && value.isEmpty()) {
            Ir.Expr expr = visit(ast.value().get());
            // Disallow NIL for Comparable unless type is NIL or ANY
            if (expr.type().equals(Type.NIL) &&
                    !type.equals(Type.NIL) &&
                    !type.equals(Type.ANY)) {
                if (type.equals(Type.COMPARABLE) || type.equals(Type.EQUATABLE)) {
                    throw new AnalyzeException("Cannot assign NIL to type " + type);
                }
            }
            requireSubtype(expr.type(), type);
            value = Optional.of(expr);
        }

        try {
            scope.define(ast.name(), type);
        } catch (IllegalStateException e) {
            throw new AnalyzeException("Variable '" + ast.name() + "' is already defined");
        }

        return new Ir.Stmt.Let(ast.name(), type, value);
    }

    @Override
    public Ir.Stmt.Def visit(Ast.Stmt.Def ast) throws AnalyzeException {
        // Determine return type
        Type returnType = ast.returnType().isPresent()
                ? Environment.TYPES.getOrDefault(ast.returnType().get(), Type.ANY)
                : Type.ANY;

        // Create a new scope for the function body
        Scope functionScope = new Scope(scope);

        // Process parameters
        var parameters = new ArrayList<Ir.Stmt.Def.Parameter>();
        for (int i = 0; i < ast.parameters().size(); i++) {
            String paramName = ast.parameters().get(i);
            Type paramType = Type.ANY;

            // If parameter type is specified, use it
            if (i < ast.parameterTypes().size() && ast.parameterTypes().get(i).isPresent()) {
                String typeName = ast.parameterTypes().get(i).get();
                if (!Environment.TYPES.containsKey(typeName)) {
                    throw new AnalyzeException("Undefined parameter type: " + typeName);
                }
                paramType = Environment.TYPES.get(typeName);
            }

            functionScope.define(paramName, paramType);
            parameters.add(new Ir.Stmt.Def.Parameter(paramName, paramType));
        }

        // Define the function in the current scope before processing the body
        Type functionType = new Type.Function(
                parameters.stream().map(p -> p.type()).toList(),
                returnType
        );
        scope.define(ast.name(), functionType);

        // Set function context and use the function scope
        boolean wasInsideFunction = this.insideFunction;
        this.insideFunction = true; // Mark that we're inside a function
        Scope parentScope = this.scope;
        this.scope = functionScope;

        var bodyStmts = new ArrayList<Ir.Stmt>();
        for (Ast.Stmt stmt : ast.body()) {
            bodyStmts.add(visit(stmt));
        }

        // Restore scope and function context
        this.scope = parentScope;
        this.insideFunction = wasInsideFunction;

        return new Ir.Stmt.Def(ast.name(), parameters, returnType, bodyStmts);
    }

    @Override
    public Ir.Stmt.If visit(Ast.Stmt.If ast) throws AnalyzeException {
        Ir.Expr condition = visit(ast.condition());

        // Condition must be of type BOOLEAN
        if (!condition.type().equals(Type.BOOLEAN)) {
            throw new AnalyzeException("Condition must be a boolean expression");
        }

        // Create new scopes for then and else blocks
        Scope parentScope = this.scope;

        // Process then body
        this.scope = new Scope(parentScope);
        var thenStmts = new ArrayList<Ir.Stmt>();
        for (Ast.Stmt stmt : ast.thenBody()) {
            thenStmts.add(visit(stmt));
        }

        // Process else body
        this.scope = new Scope(parentScope);
        var elseStmts = new ArrayList<Ir.Stmt>();
        for (Ast.Stmt stmt : ast.elseBody()) {
            elseStmts.add(visit(stmt));
        }

        // Restore original scope
        this.scope = parentScope;

        return new Ir.Stmt.If(condition, thenStmts, elseStmts);
    }

    @Override
    public Ir.Stmt.For visit(Ast.Stmt.For ast) throws AnalyzeException {
        // Evaluate the iterable expression
        Ir.Expr iterable = visit(ast.expression());

        // Ensure the expression is iterable
        requireSubtype(iterable.type(), Type.ITERABLE);

        // Determine the element type (for now we'll use INTEGER as tests suggest)
        Type elementType = Type.INTEGER; // Default for range() function

        // Create a new scope for the loop body
        Scope loopScope = new Scope(scope);
        loopScope.define(ast.name(), elementType);

        // Use the loop scope to analyze the body
        Scope parentScope = this.scope;
        this.scope = loopScope;

        var bodyStmts = new ArrayList<Ir.Stmt>();
        for (Ast.Stmt stmt : ast.body()) {
            bodyStmts.add(visit(stmt));
        }

        // Restore the original scope
        this.scope = parentScope;

        return new Ir.Stmt.For(ast.name(), elementType, iterable, bodyStmts);
    }

    @Override
    public Ir.Stmt.Return visit(Ast.Stmt.Return ast) throws AnalyzeException {
        // Check if we're inside a function
        if (!insideFunction) {
            throw new AnalyzeException("Return statement outside of function");
        }

        Optional<Ir.Expr> returnValue = Optional.empty();
        Type returnType = Type.NIL; // Default for no return value

        if (ast.value().isPresent()) {
            Ir.Expr value = visit(ast.value().get());
            returnType = value.type();
            returnValue = Optional.of(value);
        }

        return new Ir.Stmt.Return(returnValue);
    }

    @Override
    public Ir.Stmt.Expression visit(Ast.Stmt.Expression ast) throws AnalyzeException {
        var expression = visit(ast.expression());
        return new Ir.Stmt.Expression(expression);
    }

    @Override
    public Ir.Stmt.Assignment visit(Ast.Stmt.Assignment ast) throws AnalyzeException {
        Ir.Expr target = visit(ast.expression());
        Ir.Expr value = visit(ast.value());

        // Check if the target is a valid assignment target
        if (target instanceof Ir.Expr.Variable variable) {
            // Special handling for NIL: only assignable to NIL or ANY
            if (value.type().equals(Type.NIL) &&
                    !variable.type().equals(Type.NIL) &&
                    !variable.type().equals(Type.ANY)) {
                throw new AnalyzeException("Cannot assign NIL to type " + variable.type());
            }
            requireSubtype(value.type(), variable.type());
            return new Ir.Stmt.Assignment.Variable(variable, value);
        }
        else if (target instanceof Ir.Expr.Property property) {
            // Apply similar NIL restriction for properties
            if (value.type().equals(Type.NIL) &&
                    !property.type().equals(Type.NIL) &&
                    !property.type().equals(Type.ANY)) {
                throw new AnalyzeException("Cannot assign NIL to type " + property.type());
            }
            requireSubtype(value.type(), property.type());
            return new Ir.Stmt.Assignment.Property(property, value);
        }
        else {
            throw new AnalyzeException("Invalid assignment target");
        }
    }

    private Ir.Expr visit(Ast.Expr ast) throws AnalyzeException {
        return (Ir.Expr) visit((Ast) ast); //helper to cast visit(Ast.Expr) to Ir.Expr
    }

    @Override
    public Ir.Expr.Literal visit(Ast.Expr.Literal ast) throws AnalyzeException {
        var type = switch (ast.value()) {
            case null -> Type.NIL;
            case Boolean _ -> Type.BOOLEAN;
            case BigInteger _ -> Type.INTEGER;
            case BigDecimal _ -> Type.DECIMAL;
            case String _ -> Type.STRING;
            //If the AST value isn't one of the above types, the Parser is
            //returning an incorrect AST - this is an implementation issue,
            //hence throw AssertionError rather than AnalyzeException.
            default -> throw new AssertionError(ast.value().getClass());
        };
        return new Ir.Expr.Literal(ast.value(), type);
    }

    @Override
    public Ir.Expr.Group visit(Ast.Expr.Group ast) throws AnalyzeException {
        Ir.Expr expr = visit(ast.expression());
        return new Ir.Expr.Group(expr);
    }

    @Override
    public Ir.Expr.Binary visit(Ast.Expr.Binary ast) throws AnalyzeException {
        Ir.Expr left = visit(ast.left());
        Ir.Expr right = visit(ast.right());

        Type resultType;

        switch (ast.operator()) {
            case "+" -> {
                if (left.type().equals(Type.STRING) || right.type().equals(Type.STRING)) {
                    // Allow concatenation if either operand is STRING
                    resultType = Type.STRING;
                } else if (left.type().equals(Type.INTEGER) && right.type().equals(Type.INTEGER)) {
                    resultType = Type.INTEGER;
                } else if (left.type().equals(Type.DECIMAL) && right.type().equals(Type.DECIMAL)) {
                    resultType = Type.DECIMAL;
                } else {
                    throw new AnalyzeException("Invalid operand types for operator '+': " +
                            left.type() + " and " + right.type());
                }
            }
            case "-", "*" -> {
                if (left.type().equals(Type.INTEGER) && right.type().equals(Type.INTEGER)) {
                    resultType = Type.INTEGER;
                } else if ((left.type().equals(Type.INTEGER) || left.type().equals(Type.DECIMAL)) &&
                        (right.type().equals(Type.INTEGER) || right.type().equals(Type.DECIMAL))) {
                    resultType = Type.DECIMAL;
                } else {
                    throw new AnalyzeException("Invalid operand types for arithmetic operator");
                }
            }
            case "/" -> {
                if ((left.type().equals(Type.INTEGER) || left.type().equals(Type.DECIMAL)) &&
                        (right.type().equals(Type.INTEGER) || right.type().equals(Type.DECIMAL))) {
                    resultType = Type.DECIMAL;
                } else {
                    throw new AnalyzeException("Invalid operand types for division operator");
                }
            }
            case "<", ">", "<=", ">=" -> {
                requireSubtype(left.type(), Type.COMPARABLE);
                requireSubtype(right.type(), Type.COMPARABLE);
                if (!left.type().equals(right.type()) &&
                        !(left.type().equals(Type.INTEGER) && right.type().equals(Type.DECIMAL)) &&
                        !(left.type().equals(Type.DECIMAL) && right.type().equals(Type.INTEGER))) {
                    throw new AnalyzeException("Comparison operator requires compatible types");
                }
                resultType = Type.BOOLEAN;
            }
            case "==", "!=" -> {
                requireSubtype(left.type(), Type.EQUATABLE);
                requireSubtype(right.type(), Type.EQUATABLE);
                if (!left.type().equals(right.type()) &&
                        !(left.type().equals(Type.INTEGER) && right.type().equals(Type.DECIMAL)) &&
                        !(left.type().equals(Type.DECIMAL) && right.type().equals(Type.INTEGER))) {
                    throw new AnalyzeException("Equality operator requires compatible types");
                }
                resultType = Type.BOOLEAN;
            }
            case "AND", "OR" -> {
                if (left.type().equals(Type.BOOLEAN) && right.type().equals(Type.BOOLEAN)) {
                    resultType = Type.BOOLEAN;
                } else {
                    throw new AnalyzeException("Logical operators require boolean operands");
                }
            }
            default -> throw new AnalyzeException("Unknown operator: " + ast.operator());
        }

        return new Ir.Expr.Binary(ast.operator(), left, right, resultType);
    }

    @Override
    public Ir.Expr.Variable visit(Ast.Expr.Variable ast) throws AnalyzeException {
        var variable = scope.get(ast.name(), false);
        if (variable.isEmpty()) {
            throw new AnalyzeException("Undefined variable: " + ast.name());
        }
        return new Ir.Expr.Variable(ast.name(), variable.get());
    }

    @Override
    public Ir.Expr.Property visit(Ast.Expr.Property ast) throws AnalyzeException {
        Ir.Expr receiver = visit(ast.receiver());

        // Only Object types can have properties
        if (!(receiver.type() instanceof Type.Object)) {
            throw new AnalyzeException("Cannot access property of non-object type");
        }

        Type.Object objectType = (Type.Object) receiver.type();
        var property = objectType.scope().get(ast.name(), false);

        if (property.isEmpty()) {
            throw new AnalyzeException("Undefined property: " + ast.name());
        }

        return new Ir.Expr.Property(receiver, ast.name(), property.get());
    }

    @Override
    public Ir.Expr.Function visit(Ast.Expr.Function ast) throws AnalyzeException {
        var function = scope.get(ast.name(), false);
        if (function.isEmpty()) {
            throw new AnalyzeException("Undefined function: " + ast.name());
        }

        // Validate function type
        if (!(function.get() instanceof Type.Function)) {
            throw new AnalyzeException("'" + ast.name() + "' is not a function");
        }

        Type.Function functionType = (Type.Function) function.get();

        // Process arguments
        var arguments = new ArrayList<Ir.Expr>();
        for (int i = 0; i < ast.arguments().size(); i++) {
            Ir.Expr arg = visit(ast.arguments().get(i));
            if (i < functionType.parameters().size()) {
                Type paramType = functionType.parameters().get(i);
                requireSubtype(arg.type(), paramType);
            }
            arguments.add(arg);
        }

        // Check argument count
        if (arguments.size() != functionType.parameters().size()) {
            throw new AnalyzeException("Expected " + functionType.parameters().size() +
                    " arguments, got " + arguments.size());
        }

        // Function call itself returns the function's return type
        return new Ir.Expr.Function(ast.name(), arguments, functionType.returns());
    }

    @Override
    public Ir.Expr.Method visit(Ast.Expr.Method ast) throws AnalyzeException {
        Ir.Expr receiver = visit(ast.receiver());

        // Only Object types can have methods
        if (!(receiver.type() instanceof Type.Object)) {
            throw new AnalyzeException("Cannot call method on non-object type");
        }

        Type.Object objectType = (Type.Object) receiver.type();
        var method = objectType.scope().get(ast.name(), false);

        if (method.isEmpty()) {
            throw new AnalyzeException("Undefined method: " + ast.name());
        }

        // Validate method type
        if (!(method.get() instanceof Type.Function)) {
            throw new AnalyzeException("'" + ast.name() + "' is not a method");
        }

        Type.Function methodType = (Type.Function) method.get();

        // Process arguments
        var arguments = new ArrayList<Ir.Expr>();
        for (int i = 0; i < ast.arguments().size(); i++) {
            Ir.Expr arg = visit(ast.arguments().get(i));

            // Check argument type if parameter types are available
            if (i < methodType.parameters().size()) {
                Type paramType = methodType.parameters().get(i);
                requireSubtype(arg.type(), paramType);
            }

            arguments.add(arg);
        }

        // Check argument count
        if (arguments.size() != methodType.parameters().size()) {
            throw new AnalyzeException("Expected " + methodType.parameters().size() +
                    " arguments, got " + arguments.size());
        }

        return new Ir.Expr.Method(receiver, ast.name(), arguments, methodType.returns());
    }

    @Override
    public Ir.Expr.ObjectExpr visit(Ast.Expr.ObjectExpr ast) throws AnalyzeException {
        // Create scope for the object
        Scope objectScope = new Scope(null);

        // Process fields
        var irFields = new ArrayList<Ir.Stmt.Let>();
        for (Ast.Stmt.Let field : ast.fields()) {
            // Process field in the object scope
            Scope parentScope = this.scope;
            this.scope = objectScope;

            Ir.Stmt.Let irField = visit(field);
            irFields.add(irField);

            // Restore original scope
            this.scope = parentScope;
        }

        // Process methods
        var irMethods = new ArrayList<Ir.Stmt.Def>();
        for (Ast.Stmt.Def method : ast.methods()) {
            // Process method in the object scope
            Scope parentScope = this.scope;
            this.scope = objectScope;

            Ir.Stmt.Def irMethod = visit(method);
            irMethods.add(irMethod);

            // Restore original scope
            this.scope = parentScope;
        }

        // Create the object type
        Type.Object objectType = new Type.Object(objectScope);

        return new Ir.Expr.ObjectExpr(ast.name(), irFields, irMethods, objectType);
    }

    public static void requireSubtype(Type type, Type other) throws AnalyzeException {
        // Equal types are subtypes of each other
        if (type.equals(other)) {
            return;
        }

        // Any is a supertype of all types
        if (other.equals(Type.ANY)) {
            return;
        }

        // Equatable relationships
        if (other.equals(Type.EQUATABLE)) {
            if (type.equals(Type.BOOLEAN) || type.equals(Type.INTEGER) ||
                    type.equals(Type.DECIMAL) || type.equals(Type.STRING)) {
                return;
            }
        }

        // Comparable relationships
        if (other.equals(Type.COMPARABLE)) {
            if (type.equals(Type.INTEGER) || type.equals(Type.DECIMAL) ||
                    type.equals(Type.STRING)) {
                return;
            }
        }

        // Check if type is a subtype of other
        throw new AnalyzeException("Expected " + other + ", received " + type);
    }
}