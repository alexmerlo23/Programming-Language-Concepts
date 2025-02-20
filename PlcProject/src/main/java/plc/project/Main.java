package plc.project;

import plc.project.lexer.LexException;
import plc.project.lexer.Lexer;

import java.util.Scanner;

public final class Main {

    public static void main(String[] args) {
        var scanner = new Scanner(System.in);
        while (true) {
            var input = scanner.nextLine();
            try {
                var tokens = new Lexer(input).lex();
                System.out.println(tokens);
            } catch (LexException e) {
                System.out.println(e.getMessage());
            }
        }
    }

}
