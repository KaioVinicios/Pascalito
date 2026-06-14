package com.pascalito;

import com.pascalito.codegen.CodeGenerator;
import com.pascalito.codegen.Instruction;
import com.pascalito.codegen.Optimizer;
import com.pascalito.codegen.VirtualMachine;
import com.pascalito.codegen.X86Generator;
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
import com.pascalito.syntax.TreeImageRenderer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
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
        boolean emit = false;
        boolean run = false;
        boolean asm = false;
        boolean showTree = false;
        boolean opt = false;
        Path source = null;

        for (String arg : args) {
            switch (arg) {
                case "--lex"   -> lexOnly = true;
                case "--parse" -> parseOnly = true;
                case "--emit"  -> emit = true;
                case "--run"   -> run = true;
                case "--asm"   -> asm = true;
                case "--opt"   -> opt = true;
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
            } else if (asm) {
                code = runAsm(source);
            } else if (emit) {
                code = runEmit(source, opt);
            } else if (run) {
                code = runProgram(source, opt);
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
            writeTreeImage(source, outcome.tree, outcome.parser);
        }
        return EXIT_OK;
    }

    static int runSemantic(Path source, boolean showTree) throws IOException {
        SemanticOutcome outcome = analyzeFile(source);
        if (outcome.exitCode != EXIT_OK) {
            return outcome.exitCode;
        }

        System.out.println("Análise semântica concluída com sucesso.");
        var table = outcome.analyzer.getSymbolTable();
        System.out.println("Símbolos declarados: " + table.size());
        for (var sym : table.all()) {
            System.out.printf("  %-16s %-8s offset=%d (%d byte(s))%n",
                    sym.name(), sym.type().displayName(), sym.offset(), sym.type().sizeInBytes());
        }
        if (showTree) {
            writeTreeImage(source, outcome.tree, outcome.parser);
        }
        return EXIT_OK;
    }

    private static void writeTreeImage(Path source, ParseTree tree, PascalitoParser parser) {
        try {
            Path outDir = Path.of("out");
            Files.createDirectories(outDir);
            Path outFile = outDir.resolve(
                    stripExtension(source.getFileName().toString()) + ".tree.png");
            TreeImageRenderer.render(tree, parser.getRuleNames(), outFile);
            System.out.println();
            System.out.println("Árvore sintática gravada em " + outFile);
        } catch (IOException e) {
            System.err.println("Não foi possível gerar a imagem da árvore: " + e.getMessage());
        }
    }

    static int runEmit(Path source, boolean opt) throws IOException {
        SemanticOutcome outcome = analyzeFile(source);
        if (outcome.exitCode != EXIT_OK) {
            return outcome.exitCode;
        }
        CodeGenerator gen = new CodeGenerator();
        gen.visit(outcome.tree);
        List<Instruction> code = gen.getCode();
        if (opt) {
            int before = code.size();
            code = Optimizer.optimize(code);
            System.out.println("Otimização: %d → %d instruções".formatted(before, code.size()));
        }

        Path outDir = Path.of("out");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve(stripExtension(source.getFileName().toString()) + ".asm");

        StringBuilder sb = new StringBuilder();
        sb.append("=== CODIGO ASSEMBLY GERADO ===\n");
        for (Instruction ins : code) {
            sb.append(ins).append('\n');
        }
        Files.writeString(outFile, sb.toString());
        System.out.println("Assembly escrito em " + outFile);
        return EXIT_OK;
    }

    /** Gera assembly x86 a partir do 3AC otimizado e grava em out/&lt;programa&gt;.asm. */
    static int runAsm(Path source) throws IOException {
        SemanticOutcome outcome = analyzeFile(source);
        if (outcome.exitCode != EXIT_OK) {
            return outcome.exitCode;
        }
        CodeGenerator gen = new CodeGenerator();
        gen.visit(outcome.tree);
        List<Instruction> code = Optimizer.optimize(gen.getCode());
        String x86 = X86Generator.generate(code);

        Path outDir = Path.of("out");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve(stripExtension(source.getFileName().toString()) + ".asm");
        Files.writeString(outFile, x86);
        System.out.println("Assembly x86 escrito em " + outFile);
        return EXIT_OK;
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? filename : filename.substring(0, dot);
    }

    static int runProgram(Path source, boolean opt) throws IOException {
        SemanticOutcome outcome = analyzeFile(source);
        if (outcome.exitCode != EXIT_OK) {
            return outcome.exitCode;
        }
        CodeGenerator gen = new CodeGenerator();
        gen.visit(outcome.tree);
        List<Instruction> code = opt ? Optimizer.optimize(gen.getCode()) : gen.getCode();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out), true);
        VirtualMachine vm = new VirtualMachine(code, new InputStreamReader(System.in), out);
        try {
            vm.run();
        } catch (VirtualMachine.VmException e) {
            System.err.println("Erro de execução: " + e.getMessage());
            return EXIT_IO_ERROR;
        }
        return EXIT_OK;
    }

    private record ParseOutcome(int exitCode, ParseTree tree, PascalitoParser parser) {}
    private record SemanticOutcome(int exitCode, ParseTree tree, PascalitoParser parser, SemanticAnalyzer analyzer) {}

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

    private static SemanticOutcome analyzeFile(Path source) throws IOException {
        ParseOutcome parse = parseFile(source);
        if (parse.exitCode != EXIT_OK) {
            return new SemanticOutcome(parse.exitCode, null, null, null);
        }
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.visit(parse.tree);
        for (String warning : analyzer.getWarnings()) {
            System.err.println("  [aviso] " + warning);
        }
        if (analyzer.hasErrors()) {
            System.err.println("Erros semânticos encontrados:");
            for (SemanticError err : analyzer.getErrors()) {
                System.err.println("  [semântico] " + err);
            }
            return new SemanticOutcome(EXIT_SEM_ERROR, null, null, null);
        }
        return new SemanticOutcome(EXIT_OK, parse.tree, parse.parser, analyzer);
    }

    private static PascalitoLexer newLexer(CharStream input) {
        PascalitoLexer lexer = new PascalitoLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new LexicalErrorListener());
        return lexer;
    }

    private static void usage() {
        System.err.println("""
                Uso: pascalito [--lex|--parse|--emit|--run|--asm] [--opt] [--tree] <arquivo.pas>

                  --lex    executa apenas o analisador léxico
                  --parse  executa léxico + sintático (pula a análise semântica)
                  --emit   compila e imprime o código intermediário 3AC didático
                  --run    compila e executa o programa (gera 3AC + VM)
                  --asm    gera assembly Intel x86 do 3AC otimizado em out/<programa>.asm
                  --opt    otimiza a IR 3AC (constant folding + dead code) antes de --emit/--run
                  --tree   imprime a árvore sintática
                  --help   mostra esta mensagem

                Sem flags: executa léxico + sintático + semântico (padrão).""");
    }
}
