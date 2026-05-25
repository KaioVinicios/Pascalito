package com.pascalito;

import com.pascalito.lex.LexicalErrorListener;
import com.pascalito.lex.LexicalException;
import com.pascalito.lex.TokenInfo;
import com.pascalito.lex.TokenPrinter;
import com.pascalito.parser.PascalitoLexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class Main {

    private Main() {}

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Uso: pascalito <arquivo.pas>");
            System.exit(64);
        }

        Path source = Path.of(args[0]);
        try {
            int exitCode = run(source);
            System.exit(exitCode);
        } catch (IOException e) {
            System.err.println("Erro ao ler arquivo '" + source + "': " + e.getMessage());
            System.exit(66);
        }
    }

    static int run(Path source) throws IOException {
        CharStream input = CharStreams.fromPath(source);
        PascalitoLexer lexer = new PascalitoLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new LexicalErrorListener());

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        TokenPrinter printer = new TokenPrinter(lexer.getVocabulary());

        try {
            tokens.fill();
            List<TokenInfo> infos = printer.categorize(tokens.getTokens());
            printer.print(infos, System.out);
            return 0;
        } catch (LexicalException e) {
            System.err.printf("Erro léxico em linha %d, coluna %d: %s%n",
                    e.getLine(), e.getColumn(), e.getMessage());
            return 65;
        }
    }
}
