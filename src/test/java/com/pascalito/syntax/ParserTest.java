package com.pascalito.syntax;

import com.pascalito.lex.LexicalErrorListener;
import com.pascalito.parser.PascalitoLexer;
import com.pascalito.parser.PascalitoParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    private record ParseResult(ParseTree tree, SyntaxErrorListener errors, PascalitoParser parser) {
        String treeString() { return tree.toStringTree(parser); }
    }

    private ParseResult parse(String source) {
        PascalitoLexer lexer = new PascalitoLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new LexicalErrorListener());
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PascalitoParser parser = new PascalitoParser(tokens);
        parser.removeErrorListeners();
        SyntaxErrorListener listener = new SyntaxErrorListener();
        parser.addErrorListener(listener);
        ParseTree tree = parser.prog();
        return new ParseResult(tree, listener, parser);
    }

    private void assertOk(String source) {
        ParseResult r = parse(source);
        assertFalse(r.errors().hasErrors(),
                () -> "Expected no syntax errors, got: " + r.errors().getErrors());
    }

    private void assertSyntaxError(String source) {
        ParseResult r = parse(source);
        assertTrue(r.errors().hasErrors(), "Expected a syntax error");
    }

    // ===== Programas válidos =====

    @Test
    void minimalProgramWithOneCommand() {
        // Spec exige ListCmd com pelo menos um Cmd — body vazio é erro sintático
        assertOk("program p; var x: integer; begin x := 0 end.");
    }

    @Test
    void emptyBodyIsSyntaxError() {
        assertSyntaxError("program p; begin end.");
    }

    @Test
    void programWithSingleVarDecl() {
        assertOk("program p; var x: integer; begin x := 0 end.");
    }

    @Test
    void programWithAllTypes() {
        assertOk("""
                program p;
                var
                  a, b: integer;
                  flag: boolean;
                  s: string;
                begin
                  a := 1;
                  flag := true;
                  read(s)
                end.
                """);
    }

    @Test
    void multipleVarDeclarations() {
        assertOk("""
                program p;
                var
                  a: integer;
                  b: boolean;
                  c: string;
                begin
                  a := 0
                end.
                """);
    }

    // ===== Comandos =====

    @Test
    void ifWithoutElse() {
        assertOk("program p; var x: integer; begin if x > 0 then x := 1 end.");
    }

    @Test
    void ifWithElse() {
        assertOk("program p; var x: integer; begin if x > 0 then x := 1 else x := 2 end.");
    }

    @Test
    void whileLoop() {
        assertOk("program p; var x: integer; begin while x <> 0 do x := x - 1 end.");
    }

    @Test
    void readAndWrite() {
        assertOk("""
                program p;
                var x: integer; s: string;
                begin
                  read(x, s);
                  write("hello", x, s)
                end.
                """);
    }

    @Test
    void nestedBlocks() {
        assertOk("""
                program p;
                var x: integer;
                begin
                  begin
                    x := 1;
                    begin x := 2 end
                  end
                end.
                """);
    }

    // ===== Dangling else =====

    @Test
    void danglingElseBindsToInnerIf() {
        ParseResult r = parse("""
                program p;
                var a, b: integer;
                begin
                  if a > 0 then if b > 0 then a := 1 else a := 2
                end.
                """);
        assertFalse(r.errors().hasErrors());
        String tree = r.treeString();
        // O ELSE deve aparecer dentro do cmdIf interno, não do externo
        int outerIfIdx = tree.indexOf("cmdIf");
        int innerIfIdx = tree.indexOf("cmdIf", outerIfIdx + 1);
        int elseIdx = tree.indexOf("else");
        assertTrue(outerIfIdx >= 0 && innerIfIdx > outerIfIdx && elseIdx > innerIfIdx,
                "ELSE deve estar associado ao IF interno");
    }

    // ===== Precedência de operadores =====

    @Test
    void multiplicationBindsTighterThanAddition() {
        ParseResult r = parse("program p; var x: integer; begin x := 1 + 2 * 3 end.");
        String t = r.treeString();
        // exprAdd contém um exprMul que por sua vez tem VEZES → '* 3' está aninhado
        assertTrue(t.contains("(exprMul (exprUnary (atom 2)) * (exprUnary (atom 3)))"),
                "2 * 3 deve formar exprMul próprio; árvore: " + t);
    }

    @Test
    void unaryNegOnAtom() {
        assertOk("program p; var b: boolean; begin b := ~true end.");
    }

    @Test
    void unaryMinusOnConstant() {
        assertOk("program p; var x: integer; begin x := -5 end.");
    }

    @Test
    void parenthesizedExpressionCanContainAnyExpr() {
        assertOk("program p; var x: integer; b: boolean; begin b := ~(x > 0) end.");
    }

    @Test
    void andOrLogicalChaining() {
        assertOk("""
                program p;
                var a, b: boolean;
                begin
                  a := true and false or ~true
                end.
                """);
    }

    @Test
    void relationalNotAssociative() {
        // a < b < c deve ser erro sintático (relacional não associativo)
        assertSyntaxError("""
                program p;
                var a, b, c: integer;
                begin
                  if a < b < c then a := 0
                end.
                """);
    }

    // ===== Restrição de string =====

    @Test
    void cadeiaCannotBeAssigned() {
        // CADEIA não está em expr — atribuir literal de string é erro sintático
        assertSyntaxError("program p; var s: string; begin s := \"foo\" end.");
    }

    @Test
    void cadeiaAllowedInWriteArguments() {
        assertOk("program p; begin write(\"hello\", \"world\") end.");
    }

    // ===== Erros sintáticos =====

    @Test
    void missingSemicolonBetweenStatements() {
        assertSyntaxError("""
                program p;
                var x: integer;
                begin
                  x := 1
                  x := 2
                end.
                """);
    }

    @Test
    void missingDotAtEnd() {
        assertSyntaxError("program p; begin end");
    }

    @Test
    void missingProgramKeyword() {
        assertSyntaxError("p; begin end.");
    }

    @Test
    void unbalancedParentheses() {
        assertSyntaxError("program p; var x: integer; begin x := (1 + 2 end.");
    }

    @Test
    void emptyVarBlockIsAllowed() {
        // 'var' sem declarações deve falhar (declTip+ exige ao menos uma)
        assertSyntaxError("program p; var begin end.");
    }
}
