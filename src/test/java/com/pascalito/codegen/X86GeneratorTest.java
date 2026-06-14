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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class X86GeneratorTest {

    private String asm(String source) {
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
        return X86Generator.generate(Optimizer.optimize(gen.getCode()));
    }

    private boolean matches(String text, String regex) {
        return Pattern.compile(regex, Pattern.MULTILINE).matcher(text).find();
    }

    // ===== Seções =====

    @Test
    void hasDataAndCodeSections() {
        String out = asm("program p; var n: integer; begin n := 1 end.");
        assertTrue(out.contains(".data"), () -> out);
        assertTrue(out.contains(".code") || out.contains(".text"), () -> out);
    }

    @Test
    void endsWithExitConvention() {
        String out = asm("program p; var n: integer; begin n := 1 end.");
        assertTrue(out.contains("exit"), () -> "deve encerrar via convenção de saída: " + out);
    }

    // ===== Seção .data: db/dw a partir da tabela de símbolos =====

    @Test
    void integerVariableDeclaredAsDw() {
        String out = asm("program p; var n: integer; begin n := 1 end.");
        assertTrue(matches(out, "^\\s*n\\s+dw\\b"), () -> "n deve ser 'dw': " + out);
    }

    @Test
    void booleanVariableDeclaredAsDb() {
        String out = asm("program p; var flag: boolean; begin flag := true end.");
        assertTrue(matches(out, "^\\s*flag\\s+db\\b"), () -> "flag deve ser 'db': " + out);
    }

    // ===== Aritmética =====

    @Test
    void arithmeticEmitsAddAndImul() {
        // n := a + a * a  (com variável, para não dobrar tudo em constante)
        String out = asm("program p; var a, n: integer; begin read(a); n := a + a * a end.");
        assertTrue(matches(out, "\\badd\\b"), () -> "esperava 'add': " + out);
        assertTrue(matches(out, "\\bimul\\b"), () -> "esperava 'imul': " + out);
    }

    @Test
    void divisionEmitsIdiv() {
        String out = asm("program p; var a, n: integer; begin read(a); n := a / a end.");
        assertTrue(matches(out, "\\bidiv\\b"), () -> "esperava 'idiv': " + out);
    }

    // ===== Relacional: cmp + setcc =====

    @Test
    void relationalEmitsCmpAndSetcc() {
        String out = asm("program p; var a: integer; b: boolean; begin read(a); b := a > 0 end.");
        assertTrue(matches(out, "\\bcmp\\b"), () -> "esperava 'cmp': " + out);
        assertTrue(matches(out, "\\bset(g|l|e|ge|le|ne)\\b"), () -> "esperava setcc: " + out);
    }

    // ===== Lógico em registradores de 1 byte =====

    @Test
    void logicalEmitsByteRegisterOps() {
        String out = asm("program p; var b: boolean; begin read(b); b := b and (b or b) end.");
        assertTrue(matches(out, "\\band\\b"), () -> "esperava 'and': " + out);
        assertTrue(matches(out, "\\bor\\b"), () -> "esperava 'or': " + out);
        assertTrue(matches(out, "\\bal\\b") || matches(out, "\\bbl\\b"),
                () -> "lógicos devem usar registradores de 1 byte (al/bl): " + out);
    }

    // ===== Controle: if/else e while =====

    @Test
    void ifEmitsConditionalJumpAndLabels() {
        String out = asm("""
                program p; var a: integer;
                begin read(a); if a > 0 then a := 1 else a := 2 end.
                """);
        assertTrue(matches(out, "\\bjmp\\b"), () -> "esperava 'jmp': " + out);
        assertTrue(matches(out, "\\bj(e|z)\\b"), () -> "esperava salto condicional je/jz: " + out);
        assertTrue(matches(out, "^L_\\d+:"), () -> "esperava rótulos L_n:: " + out);
    }

    @Test
    void whileEmitsBackEdgeJump() {
        String out = asm("""
                program p; var a: integer;
                begin a := 0; while a < 3 do a := a + 1 end.
                """);
        long labels = out.lines().filter(l -> l.matches("^L_\\d+:")).count();
        assertTrue(labels >= 2, () -> "while precisa de rótulos de início e fim: " + out);
        assertTrue(matches(out, "\\bjmp\\b"), () -> "esperava salto de retorno do laço: " + out);
    }
}
