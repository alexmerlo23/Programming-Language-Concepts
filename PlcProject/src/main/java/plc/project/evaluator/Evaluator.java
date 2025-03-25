package plc.project.evaluator;

import plc.project.parser.Ast;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.ArrayList;


public final class Evaluator implements Ast.Visitor<RuntimeValue, EvaluateException> {

    private Scope scope;

    public Evaluator(Scope scope) {
        this.scope = scope;
    }

    @Override
    public RuntimeValue visit(Ast.Source ast) throws EvaluateException {
        RuntimeValue value = new RuntimeValue.Primitive(null);
        for (var stmt : ast.statements()) {
            value = visit(stmt);
        }
        //TODO: Handle the possibility of RETURN being called outside of a function.
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Let ast) {
        // Implement logic for evaluating Let statements
        return new RuntimeValue.Primitive(ast.value().orElse(null));
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Def ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.If ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.For ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Return ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Expression ast) throws EvaluateException {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Assignment ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Literal ast) throws EvaluateException {
        return new RuntimeValue.Primitive(ast.value());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Group ast) throws EvaluateException {
        // Evaluate the expression inside the group
        return visit(ast.expression());
    }


    @Override
    public RuntimeValue visit(Ast.Expr.Binary ast) throws EvaluateException {
        RuntimeValue left = visit(ast.left());
        RuntimeValue right = visit(ast.right());

        // Extract literal values from the RuntimeValue
        Object leftValue = ((RuntimeValue.Primitive) left).value();
        Object rightValue = ((RuntimeValue.Primitive) right).value();

        switch (ast.operator()) {
            case "+":
                if (leftValue instanceof BigInteger && rightValue instanceof BigInteger) {
                    return new RuntimeValue.Primitive(((BigInteger) leftValue).add((BigInteger) rightValue));
                }
                if (leftValue instanceof BigDecimal && rightValue instanceof BigDecimal) {
                    return new RuntimeValue.Primitive(((BigDecimal) leftValue).add((BigDecimal) rightValue));
                }
                if (leftValue instanceof String && rightValue instanceof String) {
                    return new RuntimeValue.Primitive(leftValue.toString() + rightValue.toString());
                }
                throw new EvaluateException("Invalid operands for + operation");

            case "-":
                return new RuntimeValue.Primitive(requireType(left, BigDecimal.class).subtract(requireType(right, BigDecimal.class)));

            case "*":
                return new RuntimeValue.Primitive(requireType(left, BigDecimal.class).multiply(requireType(right, BigDecimal.class)));

            case "/":
                BigDecimal leftDecimal = requireType(left, BigDecimal.class);
                BigDecimal rightDecimal = requireType(right, BigDecimal.class);
                if (rightDecimal.equals(BigDecimal.ZERO)) {
                    throw new EvaluateException("Division by zero");
                }
                return new RuntimeValue.Primitive(leftDecimal.divide(rightDecimal, RoundingMode.FLOOR));

            case "<":
                return new RuntimeValue.Primitive(requireType(left, BigDecimal.class).compareTo(requireType(right, BigDecimal.class)) < 0);

            case "==":
                return new RuntimeValue.Primitive(leftValue.equals(rightValue));

            case "AND":
                return new RuntimeValue.Primitive(requireType(left, Boolean.class) && requireType(right, Boolean.class));

            case "OR":
                return new RuntimeValue.Primitive(requireType(left, Boolean.class) || requireType(right, Boolean.class));

            default:
                throw new EvaluateException("Unsupported binary operator: " + ast.operator());
        }
    }




    @Override
    public RuntimeValue visit(Ast.Expr.Variable ast) throws EvaluateException {
        // Simply return the variable's name as a primitive value
        return new RuntimeValue.Primitive(ast.name());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Property ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Function ast) throws EvaluateException {
        // Check if the function is predefined or available in the environment
        if (functionExists(ast.name())) {
            // Evaluate the arguments of the function
            List<RuntimeValue> evaluatedArguments = new ArrayList<>();
            for (Ast.Expr expr : ast.arguments()) {
                evaluatedArguments.add(visit(expr));
            }

            // Return the result (for now, returning arguments as they are)
            return new RuntimeValue.Primitive(evaluatedArguments);
        } else {
            // Function is undefined, throw an exception
            throw new EvaluateException("Undefined function: " + ast.name());
        }
    }

    // Helper function to check if the function is available in the environment
    private boolean functionExists(String name) {
        // This is a simple check; you may want to expand it to support your environment's functions.
        return name.equals("function") || name.equals("log");
    }


    @Override
    public RuntimeValue visit(Ast.Expr.Method ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Expr.ObjectExpr ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    /**
     * Helper function for extracting RuntimeValues of specific types. If the
     * type is subclass of {@link RuntimeValue} the check applies to the value
     * itself, otherwise the value is expected to be a {@link RuntimeValue.Primitive}
     * and the check applies to the primitive value.
     */
    private static <T> T requireType(RuntimeValue value, Class<T> type) throws EvaluateException {
        //To be discussed in lecture 3/5.
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
