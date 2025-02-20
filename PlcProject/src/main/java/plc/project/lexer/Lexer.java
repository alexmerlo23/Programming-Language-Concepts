package plc.project.lexer;

import java.util.ArrayList;
import java.util.List;

/**
 * The lexer works through a combination of {@link #lex()}, which repeatedly
 * calls {@link #lexToken()} and skips over whitespace/comments, and
 * {@link #lexToken()}, which determines the type of the next token and
 * delegates to the corresponding lex method.
 *
 * <p>Additionally, {@link CharStream} manages the lexer state and contains
 * {@link CharStream#peek} and {@link CharStream#match}. These are helpful
 * utilities for working with character state and building tokens.
 */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    public List<Token> lex() throws LexException {
        List<Token> tokens = new ArrayList<>();

        while (chars.has(0)) { // Ensure we don't go out of bounds
            if (chars.peek("[ \b\n\r\t]")) {
                chars.match("[ \b\n\r\t]");
                chars.length=0; // Match and skip over whitespace
            } else if (chars.peek("/","/")) { // Check for "//" directly
                lexComment();
            } else {
                tokens.add(lexToken()); // Process valid tokens
            }
        }
        return tokens;
    }



    private void lexComment() {
        if (chars.match("/", "/")) { // Ensure it starts with //
            while (chars.has(0) && !chars.peek("\n", "\r")) { // Consume until newline or end of input
                chars.match("."); // Consume any character in the comment
                chars.index++; // Advance the index after consuming each character
            }

            // After consuming the comment, we should check and consume the newline (for double comment test)
            if (chars.peek("\n") || chars.peek("\r")) {
                chars.match(".");
                chars.index++; // Advance the index after consuming the newline
            }

            // Reset length since comments are ignored
            chars.length = 0;
        }
    }



    private Token lexToken() throws LexException {
        // Identifier
        if (chars.peek("[A-Za-z_]")) {
            return lexIdentifier();
        }

        // Number
        else if (chars.peek("[+\\-]", "[0-9]") ||chars.peek("[0-9]")){
            return lexNumber();
        }

        // Character
        else if (chars.peek("\'")){
            return lexCharacter();
        }

        // String
        else if (chars.peek("\"")){
            return lexString();
        }

        // Operator
        else {
            return lexOperator();
        }
    }


    private Token lexIdentifier() {
        while (chars.match("[A-Za-z0-9_-]"));       //  Provided by lecture
        return new Token(Token.Type.IDENTIFIER, chars.emit());
    }

    private Token lexNumber() throws LexException {
        if (chars.peek("[\\+-]")) {     // Handle sign
            chars.match("[\\+-]");
        }

        if (chars.peek("[0-9]")) {
            while (chars.match("[0-9]")); // Match initial number


            if (chars.peek("e")) {      // Int exponent case
                chars.match("e");
                if (chars.peek("[0-9]")) {  // Check if there are digits after exponent
                    while (chars.match("[0-9]")); // Match exponent digits
                    return new Token(Token.Type.INTEGER, chars.emit()); // Return as INTEGER with exponent
                } else {    // No digits after exponent, backtrack
                    chars.index--;
                    chars.length--;
                    return new Token(Token.Type.INTEGER, chars.emit()); // Return as INTEGER without exponent
                }
            }

            // If we peek a decimal point
            if (chars.peek("\\.")) {
                chars.match("\\."); // Match decimal point
                // Check if there are digits after the decimal point
                if (chars.peek("[0-9]")) {
                    while (chars.match("[0-9]")); // Match decimal digits if present
                    if (chars.peek("e")) {  // Handle exponent part after decimal
                        chars.match("e");
                        if (chars.peek("[0-9]")) {
                            while (chars.match("[0-9]")); // Match exponent digits
                            return new Token(Token.Type.DECIMAL, chars.emit()); // Return as DECIMAL with exponent
                        } else {
                            // No digits after exponent, backtrack
                            chars.index--;
                            chars.length--;
                            return new Token(Token.Type.DECIMAL, chars.emit()); // Return as DECIMAL without exponent
                        }
                    }
                    return new Token(Token.Type.DECIMAL, chars.emit()); // Return as DECIMAL
                } else {
                    // No digits after decimal, backtrack
                    chars.index--;
                    chars.length--;
                    return new Token(Token.Type.INTEGER, chars.emit()); // Return integer part first
                }
            }
            return new Token(Token.Type.INTEGER, chars.emit()); // No decimal point, just return integer part
        }

        throw new LexException("Invalid number format");
    }




    private Token lexCharacter() throws LexException {
        chars.match("'"); // Match opening single quote
        String charRegex = "([^'\\n\\r])";
        
        if (chars.peek("\\\\") || chars.peek(charRegex)) { // Check for escape sequence or valid character
            if (chars.peek("\\\\"))
                lexEscape(); // Handle escape sequences
            else
                chars.match(charRegex); // Match a regular character

            // Ensure the character is properly closed with a single quote
            if (chars.peek("'")) {
                chars.match("'");
                return new Token(Token.Type.CHARACTER, chars.emit());
            } else {
                throw new LexException("Missing end quote"); // Error if closing quote is missing
            }
        }
        throw new LexException("Invalid Character"); // Error if no valid character was found
    }


    private Token lexString() throws LexException{
        String stringRegex = "[^\\\\\"\\n\\r]";

        chars.match("\""); // Start of String
        while (!chars.peek("\"")) {
            if (chars.peek("\\\\")) { // escape sequence started
                lexEscape();
            }
            else if (chars.peek(stringRegex)) { // regular characters
                chars.match(stringRegex);
            }
            else {
                throw new LexException("Invalid String");
            }
        }
        chars.match("\""); // End of String found

        return new Token(Token.Type.STRING, chars.emit());
    }

    private void lexEscape() throws LexException {
        if (!chars.match("\\\\")) {
            throw new LexException("Invalid Escape Sequence");
        } else {
            if (!chars.match("[bnrt'\"\\\\]"))
                throw new LexException("Invalid Escape Character");
        }
    }

    public Token lexOperator() throws LexException {
        if(chars.match("<", "=") || chars.match(">", "=") || chars.match("!", "=") || chars.match("=", "=")) {
            return new Token(Token.Type.OPERATOR, chars.emit());
        } else if(chars.match("([<>!=] '='?|(.))")) {
            return new Token(Token.Type.OPERATOR, chars.emit());
        }
        throw new LexException("Invalid Operator");
    }

    /**
     * A helper class for maintaining the state of the character stream (input)
     * and methods for building up token literals.
     */
    private static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        /**
         * Returns true if the next characters match their corresponding
         * pattern. Each pattern is a regex matching only ONE character!
         */
        public boolean peek(String... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var character = input.charAt(index + offset);
                if (!String.valueOf(character).matches(patterns[offset])) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the character stream.
         */
        public boolean match(String... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
                length += patterns.length;
            }
            return peek;
        }

        /**
         * Returns the literal built by all characters matched since the last
         * call to emit(); also resetting the length for subsequent tokens.
         */
        public String emit() {
            var literal = input.substring(index - length, index);
            length = 0;
            return literal;
        }

    }

}
