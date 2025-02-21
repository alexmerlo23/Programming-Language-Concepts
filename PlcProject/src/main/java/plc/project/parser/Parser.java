package plc.project.parser;

import plc.project.lexer.Token;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

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
        if (tokens.peek("LET")) {
            return parseLetStmt();
        } else if (tokens.peek("DEF")) {
            return parseDefStmt();
        } else if (tokens.peek("IF")) {
            return parseIfStmt();
        } else if (tokens.peek("FOR")) {
            return parseForStmt();
        } else if (tokens.peek("RETURN")) {
            return parseReturnStmt();
        } else {
            return parseExpressionOrAssignmentStmt();
        }
    }

    private Ast.Stmt.Let parseLetStmt() throws ParseException {
        tokens.match("LET");
        String variable = tokens.get(0).literal(); // Access the identifier
        tokens.match(Token.Type.IDENTIFIER);
        Optional<Ast.Expr> expression = Optional.empty(); // Default value for expression is empty

        if (tokens.match("=")) {
            expression = Optional.of(parseExpr()); // Only parse the expression if "=" is present
        }

        // Check if the semicolon is present, if not, throw a ParseException
        if (!tokens.match(";")) {
            throw new ParseException("Expected semicolon after LET statement.");
        }

        return new Ast.Stmt.Let(variable, expression); // Return the Let statement with the optional expression
    }



    private Ast.Stmt.Def parseDefStmt() throws ParseException {
        tokens.match("def");
        String functionName = tokens.get(0).literal();
        tokens.match(Token.Type.IDENTIFIER);
        tokens.match("(");
        List<String> parameters = new ArrayList<>();
        if (!tokens.peek(")")) {
            do {
                parameters.add(tokens.get(0).literal());
                tokens.match(Token.Type.IDENTIFIER);
            } while (tokens.match(","));
        }
        tokens.match(")");
        tokens.match("{");
        List<Ast.Stmt> body = new ArrayList<>();
        while (!tokens.peek("}")) {
            body.add(parseStmt());
        }
        tokens.match("}");
        return new Ast.Stmt.Def(functionName, parameters, body);
    }

    private Ast.Stmt.If parseIfStmt() throws ParseException {
        tokens.match("if");
        Ast.Expr condition = parseExpr();
        tokens.match("{");
        List<Ast.Stmt> thenBody = new ArrayList<>();
        while (!tokens.peek("}")) {
            thenBody.add(parseStmt());
        }
        tokens.match("}");
        List<Ast.Stmt> elseBody = new ArrayList<>();
        if (tokens.peek("else")) {
            tokens.match("else");
            tokens.match("{");
            while (!tokens.peek("}")) {
                elseBody.add(parseStmt());
            }
            tokens.match("}");
        }
        return new Ast.Stmt.If(condition, thenBody, elseBody);
    }

    private Ast.Stmt.For parseForStmt() throws ParseException {
        tokens.match("FOR"); // Match "FOR"

        String variable = tokens.get(0).literal(); // Access the variable name
        tokens.match(Token.Type.IDENTIFIER); // Match the identifier

        // Check for the "IN" keyword, and if missing, throw a ParseException
        if (!tokens.match("IN")) {
            throw new ParseException("Expected 'IN' after the variable in FOR loop.");
        }

        Ast.Expr range = parseExpr(); // Parse the range expression

        tokens.match("DO"); // Match "DO" before the body of the loop

        List<Ast.Stmt> body = new ArrayList<>();
        while (!tokens.peek("END")) {
            body.add(parseStmt()); // Parse statements inside the loop body
        }

        tokens.match("END"); // Match the "END" keyword for the loop

        return new Ast.Stmt.For(variable, range, body); // Return the parsed FOR statement
    }





    private Ast.Stmt.Return parseReturnStmt() throws ParseException {
        tokens.match("RETURN"); // Match the "RETURN" keyword

        Optional<Ast.Expr> expression = Optional.empty(); // Default value if no expression is provided

        // Check if there's an expression (if the next token is not a semicolon)
        if (!tokens.peek(";")) {
            expression = Optional.of(parseExpr()); // Parse the expression if available
        }

        // Ensure there is a semicolon at the end
        tokens.match(";");

        return new Ast.Stmt.Return(expression); // Return the return statement with the optional expression
    }


    private Ast.Stmt parseExpressionOrAssignmentStmt() throws ParseException {
        Ast.Expr expression = parseExpr();  // Parse the expression
        if (tokens.peek("=")) {  // Check if the next token is an assignment operator
            tokens.match("=");  // Match the assignment token
            Ast.Expr value = parseExpr();  // Parse the value being assigned
            return new Ast.Stmt.Assignment(expression, value);  // Return the assignment statement
        }
        // Ensure a semicolon follows the expression
        if (!tokens.peek(";")) {  // Use peek instead of match here to check the next token
            throw new ParseException("Expected semicolon after expression.");
        }
        tokens.match(";");  // If a semicolon is present, match it
        return new Ast.Stmt.Expression(expression);  // Return the expression statement
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
        } else if (tokens.peek(Token.Type.DECIMAL)) {
            return parseLiteralExpr();  // Handle DECIMAL literals
        } else if (tokens.peek(Token.Type.STRING)) {
            return parseLiteralExpr();  // Handle STRING literals
        } else if (tokens.peek(Token.Type.CHARACTER)) {
            return parseLiteralExpr();  // Handle CHARACTER literals
        } else if (tokens.peek(Token.Type.IDENTIFIER)) {
            Token token = tokens.get(0);
            if (token.literal().equals("NIL")) {
                return parseLiteralExpr();  // Handle NIL as a literal
            } else if (token.literal().equals("TRUE")) {
                return parseLiteralExpr();  // Handle TRUE as a literal
            } else if (token.literal().equals("FALSE")) {
                return parseLiteralExpr();  // Handle FALSE as a literal
            }
            return parseVariableOrFunctionExpr();
        } else if (tokens.peek("(")) {
            return parseGroupExpr();
        } else {
            throw new ParseException("Unexpected token: " + tokens.get(0));
        }
    }




    private Ast.Expr.Literal parseLiteralExpr() throws ParseException {
        Token token = tokens.get(0);  // Peek at the current token

        // Handle NIL
        if (token.literal().equals("NIL")) {
            tokens.match(Token.Type.IDENTIFIER);
            return new Ast.Expr.Literal(null);
        }
        // Handle TRUE
        else if (token.literal().equals("TRUE")) {
            tokens.match(Token.Type.IDENTIFIER);
            return new Ast.Expr.Literal(true);
        }
        // Handle FALSE
        else if (token.literal().equals("FALSE")) {
            tokens.match(Token.Type.IDENTIFIER);
            return new Ast.Expr.Literal(false);
        }
        // Handle Integer literals
        else if (tokens.peek(Token.Type.INTEGER)) {
            BigInteger value = new BigInteger(token.literal());
            tokens.match(Token.Type.INTEGER);
            return new Ast.Expr.Literal(value);
        }
        // Handle Decimal literals
        else if (tokens.peek(Token.Type.DECIMAL)) {
            BigDecimal value = new BigDecimal(token.literal());
            tokens.match(Token.Type.DECIMAL);
            return new Ast.Expr.Literal(value);
        }
        // Handle Character literals (with escape handling)
        else if (tokens.peek(Token.Type.CHARACTER)) {
            String literal = token.literal();
            // No escapes
            if (literal.length() < 4) {
                Character c = literal.charAt(1);  // Extract the character
                tokens.match(Token.Type.CHARACTER);
                return new Ast.Expr.Literal(c);
            }
            // Escape sequences
            else {
                String temp = literal;
                temp = temp.replace("\\b", "\b");
                temp = temp.replace("\\n", "\n");
                temp = temp.replace("\\r", "\r");
                temp = temp.replace("\\t", "\t");
                if (temp.equals("'\\\"'")) temp = "'\"'";
                if (temp.equals("'\\\\'")) temp = "'\\'";
                if (temp.equals("'\\\''")) temp = "'\''";
                Character c = temp.charAt(1);  // Extract the character
                tokens.match(Token.Type.CHARACTER);
                return new Ast.Expr.Literal(c);
            }
        }
        // Handle String literals (with escape handling)
        else if (tokens.peek(Token.Type.STRING)) {
            String temp = token.literal();
            temp = temp.replace("\\b", "\b");
            temp = temp.replace("\\n", "\n");
            temp = temp.replace("\\r", "\r");
            temp = temp.replace("\\t", "\t");
            temp = temp.replace("\\\"", "\"");
            temp = temp.replace("\\\\", "\\");
            temp = temp.replace("\\\'", "\'");
            // Remove the surrounding quotes
            temp = temp.substring(1, temp.length() - 1);
            tokens.match(Token.Type.STRING);
            return new Ast.Expr.Literal(temp);
        }
        // If none of the above matched, throw an unexpected token exception
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

        // If it's a method call (object.method(args)) or property access (object.property)
        if (tokens.peek(".")) {
            tokens.match("."); // Match the dot
            String name = tokens.get(0).literal(); // Get method/property name
            tokens.match(Token.Type.IDENTIFIER);

            // Check if it's a method call (has parentheses) or property access
            if (tokens.peek("(")) {
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
                return new Ast.Expr.Method(receiver, name, arguments);
            } else {
                // Create a Property expression
                Ast.Expr receiver = new Ast.Expr.Variable(literal);
                return new Ast.Expr.Property(receiver, name);
            }
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
