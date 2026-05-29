package com.pascalito.codegen;

import com.pascalito.lex.LexicalErrorListener;
import com.pascalito.parser.PascalitoLexer;
import com.pascalito.parser.PascalitoParser;
import com.pascalito.syntax.SyntaxErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeGeneratorTest {

    private List<Instruction> codeOf(String source) {
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

    private List<String> ops(List<Instruction> code) {
        return code.stream().map(Instruction::op).toList();
    }

    @Test
    void emitsVarDeclarationsForEachIdentifier() {
        List<Instruction> code = codeOf("""
                program p; var a, b: integer; c: boolean;
                begin a := 0 end.
                """);
        long vars = code.stream().filter(i -> i.op().equals("VAR")).count();
        assertEquals(3, vars);
        assertTrue(code.stream().anyMatch(i ->
                i.op().equals("VAR") && i.args().get(0).equals("a") && i.args().get(1).equals("INTEGER")));
        assertTrue(code.stream().anyMatch(i ->
                i.op().equals("VAR") && i.args().get(0).equals("c") && i.args().get(1).equals("BOOLEAN")));
    }

    @Test
    void assignmentEmitsLoadImmediateAndStore() {
        List<Instruction> code = codeOf("program p; var n: integer; begin n := 42 end.");
        assertTrue(code.stream().anyMatch(i ->
                i.op().equals("LOAD") && i.args().get(1).equals("#42")));
        assertTrue(code.stream().anyMatch(i ->
                i.op().equals("STORE") && i.args().get(0).equals("n")));
    }

    @Test
    void arithmeticUsesThreeAddressTemporaries() {
        List<Instruction> code = codeOf("program p; var n: integer; begin n := 2 + 3 * 4 end.");
        // Precisa de pelo menos um ADD e um MUL, com a precedência: MUL primeiro
        int idxMul = -1, idxAdd = -1;
        for (int i = 0; i < code.size(); i++) {
            if (code.get(i).op().equals("MUL")) idxMul = i;
            if (code.get(i).op().equals("ADD")) idxAdd = i;
        }
        assertTrue(idxMul >= 0 && idxAdd >= 0);
        assertTrue(idxMul < idxAdd, "MUL deve preceder ADD (precedência)");
        // Cada op aritmética é 3-endereços: dest, esq, dir
        Instruction add = code.get(idxAdd);
        assertEquals(3, add.args().size());
        assertTrue(add.args().get(0).startsWith("t"));
    }

    @Test
    void relationalEmitsCorrectMnemonic() {
        List<Instruction> code = codeOf(
                "program p; var n: integer; b: boolean; begin b := n >= 5 end.");
        assertTrue(ops(code).contains("GE"));
    }

    @Test
    void allRelationalMnemonicsCovered() {
        assertTrue(ops(codeOf("program p; var b: boolean; n: integer; begin b := n <  1 end.")).contains("LT"));
        assertTrue(ops(codeOf("program p; var b: boolean; n: integer; begin b := n <= 1 end.")).contains("LE"));
        assertTrue(ops(codeOf("program p; var b: boolean; n: integer; begin b := n >  1 end.")).contains("GT"));
        assertTrue(ops(codeOf("program p; var b: boolean; n: integer; begin b := n >= 1 end.")).contains("GE"));
        assertTrue(ops(codeOf("program p; var b: boolean; n: integer; begin b := n == 1 end.")).contains("EQ"));
        assertTrue(ops(codeOf("program p; var b: boolean; n: integer; begin b := n <> 1 end.")).contains("NEQ"));
    }

    @Test
    void ifEmitsJumpfAndLabels() {
        List<Instruction> code = codeOf("""
                program p; var n: integer;
                begin if n > 0 then n := 1 else n := 2 end.
                """);
        List<String> opsList = ops(code);
        assertTrue(opsList.contains("JUMPF"));
        assertTrue(opsList.contains("JUMP"));
        assertEquals(2, opsList.stream().filter(o -> o.equals("LABEL")).count());
    }

    @Test
    void whileEmitsStartAndEndLabels() {
        List<Instruction> code = codeOf("""
                program p; var n: integer;
                begin while n < 10 do n := n + 1 end.
                """);
        List<String> opsList = ops(code);
        assertEquals(2, opsList.stream().filter(o -> o.equals("LABEL")).count(),
                "WHILE precisa de label de início e fim");
        assertTrue(opsList.contains("JUMPF"));
        assertTrue(opsList.contains("JUMP"));
    }

    @Test
    void writeOfCadeiaLoadsStringImmediateThenWrites() {
        List<Instruction> code = codeOf("""
                program p; begin write("oi") end.
                """);
        boolean hasStrLoad = code.stream().anyMatch(i ->
                i.op().equals("LOAD") && i.args().get(1).equals("#\"oi\""));
        assertTrue(hasStrLoad);
        assertTrue(ops(code).contains("WRITE"));
    }

    @Test
    void readEmitsOnePerVariable() {
        List<Instruction> code = codeOf("""
                program p; var a, b: integer; begin read(a, b) end.
                """);
        assertEquals(2, code.stream().filter(i -> i.op().equals("READ")).count());
    }

    @Test
    void unaryMinusEmitsSubFromZero() {
        List<Instruction> code = codeOf("program p; var n: integer; begin n := -5 end.");
        assertTrue(ops(code).contains("SUB"));
    }

    @Test
    void unaryNegEmitsNot() {
        List<Instruction> code = codeOf("program p; var b: boolean; begin b := ~true end.");
        assertTrue(ops(code).contains("NOT"));
    }

    @Test
    void programEndsWithHalt() {
        List<Instruction> code = codeOf("program p; begin write(1) end.");
        assertEquals("HALT", code.get(code.size() - 1).op());
    }

    @Test
    void identifierIsTruncatedTo16Chars() {
        List<Instruction> code = codeOf("""
                program p;
                var identificadorMuitoGrande: integer;
                begin identificadorMuitoGrande := 1 end.
                """);
        assertTrue(code.stream().anyMatch(i ->
                i.op().equals("VAR") && i.args().get(0).equals("identificadorMui")));
        assertTrue(code.stream().anyMatch(i ->
                i.op().equals("STORE") && i.args().get(0).equals("identificadorMui")));
    }
}
