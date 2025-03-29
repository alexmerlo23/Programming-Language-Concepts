package plc.project.evaluator;

import plc.project.parser.Ast;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

public final class Evaluator implements Ast.Visitor<RuntimeValue, EvaluateException> {

    private Scope scope;
    private List<RuntimeValue> log;

    public Evaluator(Scope scope) {
        this.scope = scope;
        this.log = new ArrayList<>();
    }

    public List<RuntimeValue> getLog() {
        return log;
    }

    @Override
    public RuntimeValue visit(Ast.Source ast) throws EvaluateException {
        RuntimeValue value = new RuntimeValue.Primitive(null);
        for (var stmt : ast.statements()) {
            value = visit(stmt);
        }
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Let ast) throws EvaluateException {
        if (scope.get(ast.name(), true).isPresent()) {
            throw new EvaluateException("Variable " + ast.name() + " is already defined in the current scope.");
        }
        RuntimeValue value = ast.value().isPresent() ? visit(ast.value().get()) : new RuntimeValue.Primitive(null);
        scope.define(ast.name(), value);
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Def ast) throws EvaluateException {
        RuntimeValue.Function.Definition methodDefinition = (arguments) -> {
            Scope methodScope = new Scope(scope);
            for (int i = 0; i < ast.parameters().size(); i++) {
                if (i < arguments.size()) {
                    methodScope.define(ast.parameters().get(i), arguments.get(i));
                }
            }
            Scope originalScope = this.scope;
            this.scope = methodScope;

            RuntimeValue result = new RuntimeValue.Primitive(null);
            for (Ast.Stmt stmt : ast.body()) {
                if (stmt instanceof Ast.Stmt.Return returnStmt) {
                    if (returnStmt.value().isPresent()) {
                        result = visit(returnStmt.value().get());
                    }
                    break;
                } else if (stmt instanceof Ast.Stmt.Expression) {
                    visit(stmt);
                } else {
                    result = visit(stmt);
                }
            }

            this.scope = originalScope;
            return result;
        };

        scope.define(ast.name(), new RuntimeValue.Function(ast.name(), methodDefinition));
        return new RuntimeValue.Primitive(null);
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.If ast) throws EvaluateException {
        RuntimeValue condition = visit(ast.condition());
        Boolean conditionValue = requireType(condition, Boolean.class);
        RuntimeValue result = new RuntimeValue.Primitive(null);
        Scope originalScope = this.scope;
        if (conditionValue) {
            this.scope = new Scope(scope);
            for (Ast.Stmt stmt : ast.thenBody()) {
                result = visit(stmt);
            }
            this.scope = originalScope;
        } else {
            this.scope = new Scope(scope);
            for (Ast.Stmt stmt : ast.elseBody()) {
                result = visit(stmt);
            }
            this.scope = originalScope;
        }
        return result;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.For ast) throws EvaluateException {
        RuntimeValue expressionValue = visit(ast.expression());
        if (!(expressionValue instanceof RuntimeValue.Primitive) ||
                !(((RuntimeValue.Primitive) expressionValue).value() instanceof List)) {
            throw new EvaluateException("For statement requires an iterable list.");
        }

        @SuppressWarnings("unchecked")
        List<RuntimeValue> iterableList = (List<RuntimeValue>) ((RuntimeValue.Primitive) expressionValue).value();

        for (RuntimeValue element : iterableList) {
            Scope iterationScope = new Scope(scope);
            Scope originalScope = this.scope;
            this.scope = iterationScope;
            iterationScope.define(ast.name(), element);
            for (Ast.Stmt stmt : ast.body()) {
                visit(stmt);
            }
            this.scope = originalScope;
        }
        return new RuntimeValue.Primitive(null);
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Return ast) throws EvaluateException {
        throw new EvaluateException("Return statement outside of a function is not allowed.");
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Expression ast) throws EvaluateException {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Assignment ast) throws EvaluateException {
        if (ast.expression() instanceof Ast.Expr.Variable) {
            String varName = ((Ast.Expr.Variable) ast.expression()).name();
            if (!scope.get(varName, false).isPresent()) {
                throw new EvaluateException("Variable '" + varName + "' is not defined.");
            }
            RuntimeValue value = visit(ast.value());
            scope.set(varName, value);
            return value;
        } else if (ast.expression() instanceof Ast.Expr.Property) {
            Ast.Expr.Property property = (Ast.Expr.Property) ast.expression();
            RuntimeValue receiver = visit(property.receiver());
            if (!(receiver instanceof RuntimeValue.ObjectValue object)) {
                throw new EvaluateException("Property assignment requires an object receiver.");
            }
            if (!object.scope().get(property.name(), true).isPresent()) {
                throw new EvaluateException("Property '" + property.name() + "' is not defined.");
            }
            RuntimeValue value = visit(ast.value());
            object.scope().set(property.name(), value);
            return value;
        }
        throw new EvaluateException("Assignment to non-variable or non-property targets is not supported.");
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Literal ast) throws EvaluateException {
        return new RuntimeValue.Primitive(ast.value());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Group ast) throws EvaluateException {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Binary ast) throws EvaluateException {
        switch (ast.operator()) {
            case "AND": {
                RuntimeValue left = visit(ast.left());
                Boolean leftBool = requireType(left, Boolean.class);
                if (!leftBool) {
                    return new RuntimeValue.Primitive(false);
                }
                RuntimeValue right = visit(ast.right()); // Evaluate right only if needed
                return new RuntimeValue.Primitive(requireType(right, Boolean.class));
            }
            case "OR": {
                RuntimeValue left = visit(ast.left());
                Boolean leftBool = requireType(left, Boolean.class);
                if (leftBool) {
                    return new RuntimeValue.Primitive(true);
                }
                RuntimeValue right = visit(ast.right()); // Evaluate right only if needed
                return new RuntimeValue.Primitive(requireType(right, Boolean.class));
            }
            default: {
                // For other operators, evaluate both operands as before
                RuntimeValue left = visit(ast.left());
                RuntimeValue right = visit(ast.right());
                if (!(left instanceof RuntimeValue.Primitive) || !(right instanceof RuntimeValue.Primitive)) {
                    throw new EvaluateException("Binary operation requires primitive operands.");
                }
                Object leftValue = ((RuntimeValue.Primitive) left).value();
                Object rightValue = ((RuntimeValue.Primitive) right).value();

                switch (ast.operator()) {
                    case "+":
                        if (leftValue instanceof String || rightValue instanceof String ||
                                leftValue == null || rightValue == null) {
                            return new RuntimeValue.Primitive(
                                    (leftValue == null ? "NIL" : leftValue.toString()) +
                                            (rightValue == null ? "NIL" : rightValue.toString())
                            );
                        }
                        if (leftValue instanceof BigInteger && rightValue instanceof BigInteger) {
                            return new RuntimeValue.Primitive(
                                    ((BigInteger) leftValue).add((BigInteger) rightValue)
                            );
                        }
                        if (leftValue instanceof BigDecimal && rightValue instanceof BigDecimal) {
                            return new RuntimeValue.Primitive(
                                    ((BigDecimal) leftValue).add((BigDecimal) rightValue)
                            );
                        }
                        throw new EvaluateException("Invalid operands for + operation");

                    case "-":
                        log.add(left); // Log left operand for evaluation order
                        return new RuntimeValue.Primitive(toBigDecimal(left).subtract(toBigDecimal(right)));

                    case "*":
                        return new RuntimeValue.Primitive(toBigDecimal(left).multiply(toBigDecimal(right)));

                    case "/":
                        BigDecimal leftDecimal = toBigDecimal(left);
                        BigDecimal rightDecimal = toBigDecimal(right);
                        if (rightDecimal.equals(BigDecimal.ZERO)) {
                            throw new EvaluateException("Division by zero");
                        }
                        return new RuntimeValue.Primitive(leftDecimal.divideToIntegralValue(rightDecimal));

                    case "<":
                        return new RuntimeValue.Primitive(toBigDecimal(left).compareTo(toBigDecimal(right)) < 0);

                    case ">":
                        return new RuntimeValue.Primitive(toBigDecimal(left).compareTo(toBigDecimal(right)) > 0);

                    case ">=":
                        return new RuntimeValue.Primitive(toBigDecimal(left).compareTo(toBigDecimal(right)) >= 0);

                    case "<=":
                        return new RuntimeValue.Primitive(toBigDecimal(left).compareTo(toBigDecimal(right)) <= 0);

                    case "==":
                        return new RuntimeValue.Primitive(
                                leftValue == null ? rightValue == null : leftValue.equals(rightValue)
                        );

                    case "!=":
                        return new RuntimeValue.Primitive(
                                leftValue == null ? rightValue != null : !leftValue.equals(rightValue)
                        );

                    default:
                        throw new EvaluateException("Unsupported binary operator: " + ast.operator());
                }
            }
        }
    }

    private BigDecimal toBigDecimal(RuntimeValue value) throws EvaluateException {
        Object val = ((RuntimeValue.Primitive) value).value();
        if (val instanceof Number) {
            return val instanceof BigDecimal ? (BigDecimal) val : new BigDecimal(val.toString());
        }
        throw new EvaluateException("Expected numeric value, received " + (val != null ? val.getClass() : "null"));
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Variable ast) throws EvaluateException {
        Optional<RuntimeValue> value = scope.get(ast.name(), false);
        if (value.isPresent()) {
            return value.get();
        } else {
            throw new EvaluateException("Variable '" + ast.name() + "' is not defined.");
        }
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Property ast) throws EvaluateException {
        RuntimeValue receiver = visit(ast.receiver());
        if (!(receiver instanceof RuntimeValue.ObjectValue)) {
            throw new EvaluateException("Property access requires an object receiver.");
        }
        RuntimeValue.ObjectValue object = (RuntimeValue.ObjectValue) receiver;
        Optional<RuntimeValue> value = object.scope().get(ast.name(), true);
        if (value.isPresent()) {
            return value.get();
        } else {
            throw new EvaluateException("Undefined property: " + ast.name());
        }
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Function ast) throws EvaluateException {
        Optional<RuntimeValue> functionValue = scope.get(ast.name(), false);
        if (functionValue.isEmpty() || !(functionValue.get() instanceof RuntimeValue.Function)) {
            throw new EvaluateException("Undefined function: " + ast.name());
        }

        List<RuntimeValue> evaluatedArguments = new ArrayList<>();
        for (Ast.Expr expr : ast.arguments()) {
            evaluatedArguments.add(visit(expr));
        }

        RuntimeValue.Function function = (RuntimeValue.Function) functionValue.get();
        return function.definition().invoke(evaluatedArguments);
    }

    private boolean functionExists(String name) {
        return name.equals("function") || name.equals("log");
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Method ast) throws EvaluateException {
        RuntimeValue receiver = visit(ast.receiver());
        if (!(receiver instanceof RuntimeValue.ObjectValue objectValue)) {
            throw new EvaluateException("Method call requires an object receiver.");
        }

        List<RuntimeValue> evaluatedArgs = new ArrayList<>();
        for (Ast.Expr expr : ast.arguments()) {
            evaluatedArgs.add(visit(expr));
        }

        Optional<RuntimeValue> methodValue = objectValue.scope().get(ast.name(), true);
        if (methodValue.isEmpty() || !(methodValue.get() instanceof RuntimeValue.Function method)) {
            throw new EvaluateException("Method '" + ast.name() + "' is not defined or is not a function.");
        }

        List<RuntimeValue> fullArgs = new ArrayList<>();
        fullArgs.add(receiver); // Add 'this'
        fullArgs.addAll(evaluatedArgs);
        return method.definition().invoke(fullArgs); // Return the method's result
    }

    @Override
    public RuntimeValue visit(Ast.Expr.ObjectExpr ast) throws EvaluateException {
        Scope objectScope = new Scope(scope);
        RuntimeValue.ObjectValue object = new RuntimeValue.ObjectValue(ast.name(), objectScope);

        // Handle fields
        for (Ast.Stmt.Let field : ast.fields()) {
            Scope originalScope = this.scope;
            this.scope = objectScope;
            visit(field);
            this.scope = originalScope;
        }

        // Handle methods
        for (Ast.Stmt.Def method : ast.methods()) {
            Scope originalScope = this.scope;
            this.scope = objectScope;
            RuntimeValue.Function.Definition methodDefinition = (arguments) -> {
                Scope methodScope = new Scope(objectScope);
                methodScope.define("this", object); // Define 'this'
                // Adjust index: parameters start at arguments[1], since arguments[0] is 'this'
                for (int i = 0; i < method.parameters().size(); i++) {
                    if (i + 1 < arguments.size()) { // Offset by 1
                        methodScope.define(method.parameters().get(i), arguments.get(i + 1));
                    }
                }
                Scope origScope = this.scope;
                this.scope = methodScope;
                RuntimeValue result = new RuntimeValue.Primitive(null);
                for (Ast.Stmt stmt : method.body()) {
                    if (stmt instanceof Ast.Stmt.Return returnStmt) {
                        if (returnStmt.value().isPresent()) {
                            result = visit(returnStmt.value().get());
                        }
                        break;
                    } else if (stmt instanceof Ast.Stmt.Expression) {
                        visit(stmt);
                    } else {
                        result = visit(stmt);
                    }
                }
                this.scope = origScope;
                return result;
            };
            objectScope.define(method.name(), new RuntimeValue.Function(method.name(), methodDefinition));
            this.scope = originalScope;
        }

        return object;
    }

    private static <T> T requireType(RuntimeValue value, Class<T> type) throws EvaluateException {
        if (RuntimeValue.class.isAssignableFrom(type)) {
            if (!type.isInstance(value)) {
                throw new EvaluateException("Expected value to be of type " + type + ", received " + value.getClass() + ".");
            }
            return (T) value;
        } else {
            var primitive = requireType(value, RuntimeValue.Primitive.class);
            if (!type.isInstance(primitive.value())) {
                var received = primitive.value() != null ? primitive.value().getClass() : null;
                throw new EvaluateException("Expected value to be of type " + type + ", received " + received + ".");
            }
            return (T) primitive.value();
        }
    }
}