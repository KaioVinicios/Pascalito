package com.pascalito.codegen;

import com.pascalito.lex.LexicalErrorListener;
import com.pascalito.parser.PascalitoLexer;
import com.pascalito.parser.PascalitoParser;
import com.pascalito.syntax.SyntaxErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VirtualMachineTest {

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

    private String exec(String source, String stdin) {
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out, true);
        VirtualMachine vm = new VirtualMachine(compile(source), new StringReader(stdin), pw);
        vm.run();
        return out.toString();
    }

    private String exec(String source) {
        return exec(source, "");
    }

    private String[] lines(String s) {
        return s.split("\\R");
    }

    // ===== Aritmética =====

    @Test
    void twoPlusTwoEqualsFour() {
        String out = exec("program p; var n: integer; begin n := 2 + 2; write(n) end.");
        assertEquals("4", lines(out)[0]);
    }

    @Test
    void operatorPrecedenceIsRespected() {
        String out = exec("program p; var n: integer; begin n := 2 + 3 * 4; write(n) end.");
        assertEquals("14", lines(out)[0]);
    }

    @Test
    void parenthesesOverrideTermPrecedence() {
        String out = exec("program p; var n: integer; begin n := (2 + 3) * 4; write(n) end.");
        assertEquals("20", lines(out)[0]);
    }

    @Test
    void integerDivisionTruncates() {
        String out = exec("program p; var n: integer; begin n := 7 / 2; write(n) end.");
        assertEquals("3", lines(out)[0]);
    }

    @Test
    void unaryMinusNegatesInteger() {
        String out = exec("program p; var n: integer; begin n := -5; write(n) end.");
        assertEquals("-5", lines(out)[0]);
    }

    @Test
    void subtractionWorks() {
        String out = exec("program p; var n: integer; begin n := 10 - 3 - 2; write(n) end.");
        assertEquals("5", lines(out)[0]);
    }

    // ===== Lógicos e relacionais =====

    @Test
    void andOrAreShortcircuitFriendly() {
        String out = exec("program p; var b: boolean; begin b := true and false; write(b) end.");
        assertEquals("false", lines(out)[0]);
    }

    @Test
    void notInvertsBoolean() {
        String out = exec("program p; var b: boolean; begin b := ~false; write(b) end.");
        assertEquals("true", lines(out)[0]);
    }

    @Test
    void greaterThanProducesBoolean() {
        String out = exec("program p; var b: boolean; n: integer; begin n := 10; b := n > 5; write(b) end.");
        assertEquals("true", lines(out)[0]);
    }

    @Test
    void equalityOnBooleansWorks() {
        String out = exec("program p; var b: boolean; begin b := true == false; write(b) end.");
        assertEquals("false", lines(out)[0]);
    }

    // ===== Controle de fluxo =====

    @Test
    void ifThenBranchTaken() {
        String out = exec("""
                program p; var n: integer;
                begin n := 10;
                  if n > 5 then write("maior") else write("menor")
                end.
                """);
        assertEquals("maior", lines(out)[0]);
    }

    @Test
    void ifElseBranchTaken() {
        String out = exec("""
                program p; var n: integer;
                begin n := 3;
                  if n > 5 then write("maior") else write("menor")
                end.
                """);
        assertEquals("menor", lines(out)[0]);
    }

    @Test
    void whileLoopRunsExpectedNumberOfIterations() {
        String out = exec("""
                program p; var i: integer;
                begin
                  i := 1;
                  while i <= 3 do
                  begin write(i); i := i + 1 end
                end.
                """);
        String[] ls = lines(out);
        assertEquals("1", ls[0]);
        assertEquals("2", ls[1]);
        assertEquals("3", ls[2]);
    }

    @Test
    void whileWithFalseConditionDoesNotEnterBody() {
        String out = exec("""
                program p; var i: integer;
                begin
                  i := 10;
                  while i < 0 do write(i);
                  write(i)
                end.
                """);
        assertEquals("10", lines(out)[0]);
    }

    @Test
    void nestedIfDanglingElseAttachesToInnerIf() {
        // else liga ao if interno (greedy)
        String out = exec("""
                program p; var n: integer;
                begin
                  n := 0;
                  if true then if false then write("A") else write("B")
                end.
                """);
        assertEquals("B", lines(out)[0]);
    }

    // ===== Write =====

    @Test
    void writeMixedStringAndNumber() {
        String out = exec("""
                program p; var n: integer;
                begin n := 42; write("n=", n) end.
                """);
        String[] ls = lines(out);
        assertEquals("n=", ls[0]);
        assertEquals("42", ls[1]);
    }

    @Test
    void writeBooleanLiteralPrintsTrueFalse() {
        String out = exec("program p; begin write(true) end.");
        assertEquals("true", lines(out)[0]);
    }

    // ===== Read =====

    @Test
    void readIntegerFromStdin() {
        String out = exec(
                "program p; var n: integer; begin read(n); write(n * 2) end.",
                "21\n");
        assertEquals("42", lines(out)[0]);
    }

    @Test
    void readStringFromStdin() {
        String out = exec(
                "program p; var s: string; begin read(s); write(s) end.",
                "pascalito\n");
        assertEquals("pascalito", lines(out)[0]);
    }

    @Test
    void readBooleanFromStdin() {
        String out = exec(
                "program p; var b: boolean; begin read(b); write(b) end.",
                "true\n");
        assertEquals("true", lines(out)[0]);
    }

    // ===== Programa completo =====

    @Test
    void fibonacciStyleProgram() {
        // Soma 1..5 = 15
        String out = exec("""
                program soma; var i, acc: integer;
                begin
                  i := 1;
                  acc := 0;
                  while i <= 5 do
                  begin acc := acc + i; i := i + 1 end;
                  write(acc)
                end.
                """);
        assertEquals("15", lines(out)[0]);
    }

    @Test
    void divisionByZeroIsReportedAsVmException() {
        assertThrows(VirtualMachine.VmException.class,
                () -> exec("program p; var n: integer; begin n := 10 / 0; write(n) end."));
    }
}
