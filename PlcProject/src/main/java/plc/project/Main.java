package plc.project;

import plc.project.lexer.Lexer;
import plc.project.parser.Parser;

import java.util.Scanner;

public final class Main {

    public static void main(String[] args) {
        parser(); //edit for manual testing
    }

    private static void lexer() {
        var scanner = new Scanner(System.in);
        while (true) {
            var input = scanner.nextLine();
            try {
                var tokens = new Lexer(input).lex();
                System.out.println(tokens);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void parser() {
        var scanner = new Scanner(System.in);
        while (true) {
            var input = new StringBuilder();
            var next = scanner.nextLine();
            while (!next.isEmpty()) {
                input.append(next).append("\n");
                next = scanner.nextLine();
            }
            try {
                var tokens = new Lexer(input.toString()).lex();
                var ast = new Parser(tokens).parseSource();
                System.out.println(ast);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
