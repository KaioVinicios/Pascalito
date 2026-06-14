package com.pascalito.codegen;

import com.pascalito.lex.LexicalErrorListener;
import com.pascalito.parser.PascalitoLexer;
import com.pascalito.parser.PascalitoParser;
import com.pascalito.syntax.SyntaxErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptimizerTest {

    private List<Instruction> compile(String source) {
        PascalitoLexer lexer = new PascalitoLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new LexicalErrorListener());
        PascalitoParser parser = new PascalitoParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        SyntaxErrorListener syn = new SyntaxErrorListener();
        parser.addErrorListener(syn);
        ParseTree tree = parser.prog();
        assertFalse(syn.hasErrors(), () -> "Fixture inválido: " + syn.getErrors());
        CodeGenerator gen = new CodeGenerator();
        gen.visit(tree);
        return gen.getCode();
    }

    private String run(List<Instruction> code, String stdin) {
        StringWriter out = new StringWriter();
        VirtualMachine vm = new VirtualMachine(code, new StringReader(stdin), new PrintWriter(out, true));
        vm.run();
        return out.toString();
    }

    private List<String> ops(List<Instruction> code) {
        return code.stream().map(Instruction::op).toList();
    }

    // ===== Constant folding =====

    @Test
    void foldsArithmeticConstants() {
        List<Instruction> opt = Optimizer.optimize(
                compile("program p; var n: integer; begin n := 2 + 3 * 4 end."));
        assertFalse(ops(opt).contains("ADD"), "ADD constante deve ser dobrado");
        assertFalse(ops(opt).contains("MUL"), "MUL constante deve ser dobrado");
        assertTrue(opt.stream().anyMatch(i ->
                        i.op().equals("LOAD") && i.args().get(1).equals("#14")),
                () -> "esperava LOAD ..., #14: " + opt);
    }

    @Test
    void foldsRelationalConstantsToBoolean() {
        List<Instruction> opt = Optimizer.optimize(
                compile("program p; var b: boolean; begin b := 3 > 5 end."));
        assertFalse(ops(opt).contains("GT"));
        assertTrue(opt.stream().anyMatch(i ->
                i.op().equals("LOAD") && i.args().get(1).equals("#false")));
    }

    @Test
    void foldsLogicalConstants() {
        List<Instruction> opt = Optimizer.optimize(
                compile("program p; var b: boolean; begin b := true and false end."));
        assertFalse(ops(opt).contains("AND"));
        assertTrue(opt.stream().anyMatch(i ->
                i.op().equals("LOAD") && i.args().get(1).equals("#false")));
    }

    @Test
    void doesNotFoldDivisionByZero() {
        // dobrar 1/0 mudaria o comportamento (a VM lança em tempo de execução)
        List<Instruction> opt = Optimizer.optimize(
                compile("program p; var n: integer; begin n := 1 / 0 end."));
        assertTrue(ops(opt).contains("DIV"), "divisão por zero não pode ser dobrada");
    }

    // ===== Dead code elimination =====

    @Test
    void eliminatesDeadConstantLoads() {
        List<Instruction> original = compile("program p; var n: integer; begin n := 2 + 3 * 4 end.");
        List<Instruction> opt = Optimizer.optimize(original);
        assertTrue(opt.size() < original.size(),
                () -> "otimização deve reduzir instruções: %d -> %d".formatted(original.size(), opt.size()));
        long loads = opt.stream().filter(i -> i.op().equals("LOAD")).count();
        assertEquals(1, loads, () -> "deve restar só o LOAD do resultado dobrado: " + opt);
    }

    // ===== Preservação de comportamento (VM antes == depois) =====

    @Test
    void preservesBehaviorAcrossPrograms() {
        List<String> programs = List.of(
                "program p; var n: integer; begin n := 2 + 3 * 4; write(n) end.",
                "program p; var n: integer; begin n := (2 + 3) * 4; write(n) end.",
                "program p; var b: boolean; begin b := (3 > 5) or (2 == 2); write(b) end.",
                "program p; var n: integer; begin if 3 > 5 then write(1) else write(2) end.",
                "program p; var n: integer; begin n := 0; while n < 3 do n := n + 1; write(n) end.",
                "program p; var n: integer; begin n := -5 + 10; write(n) end.");
        for (String src : programs) {
            List<Instruction> original = compile(src);
            String before = run(original, "");
            String after = run(Optimizer.optimize(original), "");
            assertEquals(before, after, () -> "saída mudou após otimização para: " + src);
        }
    }

    @Test
    void doesNotGrowAlreadyMinimalCode() {
        List<Instruction> original = compile("program p; var n: integer; begin read(n); write(n) end.");
        List<Instruction> opt = Optimizer.optimize(original);
        assertTrue(opt.size() <= original.size());
    }
}
