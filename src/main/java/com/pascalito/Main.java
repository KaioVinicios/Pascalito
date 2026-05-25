package com.pascalito;

import com.pascalito.lex.LexicalErrorListener;
import com.pascalito.lex.LexicalException;
import com.pascalito.lex.TokenInfo;
import com.pascalito.lex.TokenPrinter;
import com.pascalito.parser.PascalitoLexer;
import com.pascalito.parser.PascalitoParser;
import com.pascalito.semantic.SemanticAnalyzer;
import com.pascalito.semantic.SemanticError;
import com.pascalito.syntax.SyntaxError;
import com.pascalito.syntax.SyntaxErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class Main {

    private static final int EXIT_OK         = 0;
    private static final int EXIT_USAGE      = 64;
    private static final int EXIT_LEX_ERROR  = 65;
    private static final int EXIT_IO_ERROR   = 66;
    private static final int EXIT_SYN_ERROR  = 67;
    private static final int EXIT_SEM_ERROR  = 68;

    private Main() {}

    public static void main(String[] args) {
        if (args.length < 1) {
            usage();
            System.exit(EXIT_USAGE);
        }

        boolean lexOnly = false;
        boolean parseOnly = false;
        boolean showTree = false;
        Path source = null;

        for (String arg : args) {
            switch (arg) {
                case "--lex"   -> lexOnly = true;
                case "--parse" -> parseOnly = true;
                case "--tree"  -> showTree = true;
                case "-h", "--help" -> { usage(); System.exit(EXIT_OK); }
                default -> {
                    if (arg.startsWith("--")) {
                        System.err.println("Flag desconhecida: " + arg);
                        usage();
                        System.exit(EXIT_USAGE);
                    }
                    source = Path.of(arg);
                }
            }
        }

        if (source == null) {
            usage();
            System.exit(EXIT_USAGE);
        }

        try {
            int code;
            if (lexOnly) {
                code = runLex(source);
            } else if (parseOnly) {
                code = runParse(source, showTree);
            } else {
                code = runSemantic(source, showTree);
            }
            System.exit(code);
        } catch (IOException e) {
            System.err.println("Erro ao ler arquivo '" + source + "': " + e.getMessage());
            System.exit(EXIT_IO_ERROR);
        }
    }

    static int runLex(Path source) throws IOException {
        CharStream input = CharStreams.fromPath(source);
        PascalitoLexer lexer = newLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        TokenPrinter printer = new TokenPrinter(lexer.getVocabulary());

        try {
            tokens.fill();
            List<TokenInfo> infos = printer.categorize(tokens.getTokens());
            printer.print(infos, System.out);
            return EXIT_OK;
        } catch (LexicalException e) {
            System.err.printf("Erro léxico em linha %d, coluna %d: %s%n",
                    e.getLine(), e.getColumn(), e.getMessage());
            return EXIT_LEX_ERROR;
        }
    }

    static int runParse(Path source, boolean showTree) throws IOException {
        ParseOutcome outcome = parseFile(source);
        if (outcome.exitCode != EXIT_OK) {
            return outcome.exitCode;
        }
        System.out.println("Análise sintática concluída com sucesso.");
        if (showTree) {
            System.out.println();
            System.out.println("Árvore sintática:");
            System.out.println(outcome.tree.toStringTree(outcome.parser));
        }
        return EXIT_OK;
    }

    static int runSemantic(Path source, boolean showTree) throws IOException {
        ParseOutcome outcome = parseFile(source);
        if (outcome.exitCode != EXIT_OK) {
            return outcome.exitCode;
        }

        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.visit(outcome.tree);

        if (analyzer.hasErrors()) {
            System.err.println("Erros semânticos encontrados:");
            for (SemanticError err : analyzer.getErrors()) {
                System.err.println("  [semântico] " + err);
            }
            return EXIT_SEM_ERROR;
        }

        System.out.println("Análise semântica concluída com sucesso.");
        System.out.println("Símbolos declarados: " + analyzer.getSymbolTable().size());
        if (showTree) {
            System.out.println();
            System.out.println("Árvore sintática:");
            System.out.println(outcome.tree.toStringTree(outcome.parser));
        }
        return EXIT_OK;
    }

    private record ParseOutcome(int exitCode, ParseTree tree, PascalitoParser parser) {}

    private static ParseOutcome parseFile(Path source) throws IOException {
        CharStream input = CharStreams.fromPath(source);
        PascalitoLexer lexer = newLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        try {
            tokens.fill();
        } catch (LexicalException e) {
            System.err.printf("Erro léxico em linha %d, coluna %d: %s%n",
                    e.getLine(), e.getColumn(), e.getMessage());
            return new ParseOutcome(EXIT_LEX_ERROR, null, null);
        }

        PascalitoParser parser = new PascalitoParser(tokens);
        parser.removeErrorListeners();
        SyntaxErrorListener errorListener = new SyntaxErrorListener();
        parser.addErrorListener(errorListener);

        ParseTree tree = parser.prog();

        if (errorListener.hasErrors()) {
            System.err.println("Erros sintáticos encontrados:");
            for (SyntaxError err : errorListener.getErrors()) {
                System.err.println("  " + err);
            }
            return new ParseOutcome(EXIT_SYN_ERROR, null, null);
        }
        return new ParseOutcome(EXIT_OK, tree, parser);
    }

    private static PascalitoLexer newLexer(CharStream input) {
        PascalitoLexer lexer = new PascalitoLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new LexicalErrorListener());
        return lexer;
    }

    private static void usage() {
        System.err.println("""
                Uso: pascalito [--lex|--parse] [--tree] <arquivo.pas>

                  --lex    executa apenas o analisador léxico
                  --parse  executa léxico + sintático (pula a análise semântica)
                  --tree   imprime a árvore sintática
                  --help   mostra esta mensagem

                Sem flags: executa léxico + sintático + semântico (padrão).""");
    }
}
