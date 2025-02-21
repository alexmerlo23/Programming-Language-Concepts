package plc.project.parser;

import plc.project.lexer.Token;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

import static com.google.common.base.Preconditions.checkState;

/**
 * This style of parser is called <em>recursive descent</em>. Each rule in our
 * grammar has dedicated function, and references to other rules correspond to
 * calling that function. Recursive rules are therefore supported by actual
 * recursive calls, while operator precedence is encoded via the grammar.
 *
 * <p>The parser has a similar architecture to the lexer, just with
 * {@link Token}s instead of characters. As before, {@link TokenStream#peek} and
 * {@link TokenStream#match} help with traversing the token stream. Instead of
 * emitting tokens, you will instead need to extract the literal value via
 * {@link TokenStream#get} to be added to the relevant AST.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    public Ast.Source parseSource() throws ParseException {
        List<Ast.Stmt> statements = new ArrayList<>();
        while (tokens.has(0)) {
            statements.add(parseStmt());
        }
        return new Ast.Source(statements);
    }

    public Ast.Stmt parseStmt() throws ParseException {
        /*if (tokens.peek("let")) {
            return parseLetStmt();
        } else if (tokens.peek("def")) {
            return parseDefStmt();
        } else if (tokens.peek("if")) {
            return parseIfStmt();
        } else if (tokens.peek("for")) {
            return parseForStmt();
        } else if (tokens.peek("return")) {
            return parseReturnStmt();
        } else {*/
            return parseExpressionOrAssignmentStmt();
        //}
    }

    private Ast.Stmt.Let parseLetStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt.Def parseDefStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt.If parseIfStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt.For parseForStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt.Return parseReturnStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt parseExpressionOrAssignmentStmt() throws ParseException {
        Ast.Expr expression = parseExpr();
       /* if (tokens.peek("=")) {
            tokens.match("=");
            Ast.Expr value = parseExpr();
            return new Ast.Stmt.Assignment(expression, value);
        }*/
        return new Ast.Stmt.Expression(expression);
    }

    public Ast.Expr parseExpr() throws ParseException {
        return parseLogicalExpr();
    }

    private Ast.Expr parseLogicalExpr() throws ParseException {
        Ast.Expr left = parseComparisonExpr();
        while (tokens.peek("AND") || tokens.peek("OR")) {
            String operator = tokens.get(0).literal();
            tokens.match(Token.Type.IDENTIFIER);
            Ast.Expr right = parseComparisonExpr();
            left = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
    }

    private Ast.Expr parseComparisonExpr() throws ParseException {
        Ast.Expr left = parseAdditiveExpr();
        while (tokens.peek("==") || tokens.peek("!=") || tokens.peek("<") || tokens.peek("<=") || tokens.peek(">") || tokens.peek(">=")) {
            String operator = tokens.get(0).literal();
            tokens.match(Token.Type.OPERATOR);
            Ast.Expr right = parseAdditiveExpr();
            left = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
    }

    private Ast.Expr parseAdditiveExpr() throws ParseException {
        Ast.Expr left = parseMultiplicativeExpr();
        while (tokens.peek("+") || tokens.peek("-")) {
            String operator = tokens.get(0).literal();
            tokens.match(Token.Type.OPERATOR);
            Ast.Expr right = parseMultiplicativeExpr();
            left = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
    }

    private Ast.Expr parseMultiplicativeExpr() throws ParseException {
        Ast.Expr left = parseSecondaryExpr();
        while (tokens.peek("*") || tokens.peek("/")) {
            String operator = tokens.get(0).literal();
            tokens.match(Token.Type.OPERATOR);
            Ast.Expr right = parseSecondaryExpr();
            left = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
    }

    private Ast.Expr parseSecondaryExpr() throws ParseException {
        return parsePrimaryExpr();
    }

    private Ast.Expr parsePrimaryExpr() throws ParseException {
        if (tokens.peek(Token.Type.INTEGER)) {
            return parseLiteralExpr();
        } else if (tokens.peek(Token.Type.IDENTIFIER)) {
            return parseVariableOrFunctionExpr();
        } else if (tokens.peek("(")) {
            return parseGroupExpr();
        } else {
            throw new ParseException("Unexpected token: " + tokens.get(0));
        }
    }

    private Ast.Expr.Literal parseLiteralExpr() throws ParseException {
        Token token = tokens.get(0);  // Peek at the token

        // Handle Boolean literals
        if (tokens.peek(Token.Type.IDENTIFIER)) {
            String literal = token.literal();
            if (literal.equalsIgnoreCase("TRUE") || literal.equalsIgnoreCase("FALSE")) {
                tokens.match(Token.Type.IDENTIFIER);
                return new Ast.Expr.Literal(Boolean.parseBoolean(literal.toLowerCase()));
            }
            if (literal.equalsIgnoreCase("NIL")) {
                tokens.match(Token.Type.IDENTIFIER);
                return new Ast.Expr.Literal(null);
            }
        }

        // Handle Integer literals (use BigInteger)
        if (tokens.peek(Token.Type.INTEGER)) {
            tokens.match(Token.Type.INTEGER);
            return new Ast.Expr.Literal(new java.math.BigInteger(token.literal()));
        }

        // Handle Decimal literals (use BigDecimal)
        if (tokens.peek(Token.Type.DECIMAL)) {
            tokens.match(Token.Type.DECIMAL);
            return new Ast.Expr.Literal(new java.math.BigDecimal(token.literal()));
        }

        // Handle Character literals
        if (tokens.peek(Token.Type.CHARACTER)) {
            tokens.match(Token.Type.CHARACTER);
            String literal = token.literal();
            if (literal.length() == 3 && literal.startsWith("'") && literal.endsWith("'")) {
                return new Ast.Expr.Literal(literal.charAt(1));
            } else {
                throw new ParseException("Invalid character literal: " + literal);
            }
        }

        // Handle String literals
        if (tokens.peek(Token.Type.STRING)) {
            tokens.match(Token.Type.STRING);
            String literal = token.literal();
            return new Ast.Expr.Literal(literal.substring(1, literal.length() - 1).replace("\\n", "\n"));
        }

        throw new ParseException("Unexpected token: " + token.literal());
    }



    private Ast.Expr.Group parseGroupExpr() throws ParseException {
        tokens.match("(");
        Ast.Expr expression = parseExpr();
        tokens.match(")");
        return new Ast.Expr.Group(expression);
    }

    private Ast.Expr.ObjectExpr parseObjectExpr() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseVariableOrFunctionExpr() throws ParseException {
        Token identifier = tokens.get(0);
        tokens.match(Token.Type.IDENTIFIER);
        String literal = identifier.literal();

        // If it's a function call
        if (tokens.peek("(")) {
            tokens.match("(");  // Match the opening parenthesis
            List<Ast.Expr> arguments = new ArrayList<>();
            if (!tokens.peek(")")) {
                do {
                    arguments.add(parseExpr()); // Parse function arguments
                } while (tokens.match(",")); // Continue if there are multiple arguments
            }
            tokens.match(")"); // Match the closing parenthesis
            return new Ast.Expr.Function(literal, arguments); // Create function expression
        }

        // If it's a method call (object.method(args))
        if (tokens.peek(".")) {
            tokens.match("."); // Match the dot
            String methodName = tokens.get(0).literal(); // Get method name
            tokens.match(Token.Type.IDENTIFIER);
            tokens.match("("); // Ensure method call parentheses
            List<Ast.Expr> arguments = new ArrayList<>();
            if (!tokens.peek(")")) {
                do {
                    arguments.add(parseExpr()); // Parse arguments
                } while (tokens.match(","));
            }
            tokens.match(")");

            // Create the Method expression
            Ast.Expr receiver = new Ast.Expr.Variable(literal); // The object calling the method
            return new Ast.Expr.Method(receiver, methodName, arguments);
        }

        // If it's just a variable
        return new Ast.Expr.Variable(literal);
    }


    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at (index + offset).
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Returns the token at (index + offset).
         */
        public Token get(int offset) {
            checkState(has(offset));
            return tokens.get(index + offset);
        }

        /**
         * Returns true if the next characters match their corresponding
         * pattern. Each pattern is either a {@link Token.Type}, matching tokens
         * of that type, or a {@link String}, matching tokens with that literal.
         * In effect, {@code new Token(Token.Type.IDENTIFIER, "literal")} is
         * matched by both {@code peek(Token.Type.IDENTIFIER)} and
         * {@code peek("literal")}.
         */
        public boolean peek(Object... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var token = tokens.get(index + offset);
                var pattern = patterns[offset];
                checkState(pattern instanceof Token.Type || pattern instanceof String, pattern);
                if (!token.type().equals(pattern) && !token.literal().equals(pattern)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the token stream.
         */
        public boolean match(Object... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
            }
            return peek;
        }

    }

}
