package com.pascalito.syntax;

import com.pascalito.lex.LexicalErrorListener;
import com.pascalito.parser.PascalitoLexer;
import com.pascalito.parser.PascalitoParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TreeImageRendererTest {

    private ParseTree parse(String source) {
        PascalitoLexer lexer = new PascalitoLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new LexicalErrorListener());
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PascalitoParser parser = new PascalitoParser(tokens);
        parser.removeErrorListeners();
        SyntaxErrorListener listener = new SyntaxErrorListener();
        parser.addErrorListener(listener);
        ParseTree tree = parser.prog();
        assertFalse(listener.hasErrors(),
                () -> "fonte de teste deveria ser válida, erros: " + listener.getErrors());
        return tree;
    }

    private String[] ruleNames() {
        return new PascalitoParser(null).getRuleNames();
    }

    @Test
    void generatesValidPngFile(@TempDir Path dir) throws IOException {
        ParseTree tree = parse("program p; var x: integer; begin x := 1 + 2 * 3 end.");
        Path out = dir.resolve("tree.png");

        TreeImageRenderer.render(tree, ruleNames(), out);

        assertTrue(Files.exists(out), "arquivo PNG deveria ter sido criado");
        assertTrue(Files.size(out) > 0, "arquivo PNG não deveria estar vazio");

        // Header PNG: 0x89 'P' 'N' 'G'
        byte[] head = Files.readAllBytes(out);
        assertEquals((byte) 0x89, head[0]);
        assertEquals('P', head[1]);
        assertEquals('N', head[2]);
        assertEquals('G', head[3]);

        BufferedImage img = ImageIO.read(out.toFile());
        assertNotNull(img, "ImageIO deveria conseguir decodificar o PNG");
        assertTrue(img.getWidth() > 0 && img.getHeight() > 0,
                "dimensões deveriam ser positivas");
    }

    @Test
    void widerProgramProducesWiderImage(@TempDir Path dir) throws IOException {
        // Mais folhas ⇒ árvore mais larga (empacotamento justo por subárvore).
        ParseTree small = parse("program p; var x: integer; begin x := 1 end.");
        ParseTree big = parse(
                "program p; var x: integer; begin x := 1 + 2 + 3 + 4 + 5 + 6 + 7 end.");

        Path smallPng = dir.resolve("small.png");
        Path bigPng = dir.resolve("big.png");
        TreeImageRenderer.render(small, ruleNames(), smallPng);
        TreeImageRenderer.render(big, ruleNames(), bigPng);

        int smallW = ImageIO.read(smallPng.toFile()).getWidth();
        int bigW = ImageIO.read(bigPng.toFile()).getWidth();
        assertTrue(bigW > smallW,
                () -> "imagem maior (%d) deveria ser mais larga que a menor (%d)"
                        .formatted(bigW, smallW));
    }
}
