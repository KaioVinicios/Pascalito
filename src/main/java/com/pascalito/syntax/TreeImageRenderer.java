package com.pascalito.syntax;

import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renderiza uma {@link ParseTree} do ANTLR como imagem PNG, sem dependências
 * externas (apenas AWT/ImageIO). O layout é o algoritmo clássico de Knuth:
 * cada folha ocupa uma coluna; cada nó interno é centralizado sobre seus filhos.
 */
public final class TreeImageRenderer {

    private static final int PAD_X  = 8;   // espaço horizontal dentro da caixa
    private static final int PAD_Y  = 5;   // espaço vertical dentro da caixa
    private static final int H_GAP  = 24;  // folga horizontal entre colunas
    private static final int V_GAP  = 40;  // folga vertical entre níveis
    private static final int MARGIN = 24;  // moldura ao redor da imagem

    private static final Color BG          = Color.WHITE;
    private static final Color RULE_FILL    = new Color(0xDC, 0xE6, 0xF5);
    private static final Color TERMINAL_FILL = new Color(0xFF, 0xF2, 0xCC);
    private static final Color BORDER       = new Color(0x33, 0x33, 0x33);
    private static final Color EDGE         = new Color(0x88, 0x88, 0x88);
    private static final Color TEXT         = new Color(0x11, 0x11, 0x11);

    private TreeImageRenderer() {}

    /** Modelo intermediário de nó (independente do ANTLR) usado para o layout. */
    private static final class Node {
        final String label;
        final boolean terminal;
        final List<Node> children = new ArrayList<>();
        int depth;
        int boxW;
        int subtreeW; // largura reservada para esta subárvore
        double cx;    // centro horizontal em pixels
        int top;      // topo da caixa em pixels

        Node(String label, boolean terminal) {
            this.label = label;
            this.terminal = terminal;
        }
    }

    public static void render(ParseTree tree, String[] ruleNames, Path out) throws IOException {
        Node root = build(tree, ruleNames);

        // FontMetrics a partir de um Graphics2D descartável, para medir o texto.
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gm = scratch.createGraphics();
        Font font = new Font("SansSerif", Font.PLAIN, 13);
        gm.setFont(font);
        FontMetrics fm = gm.getFontMetrics();
        final int boxH = fm.getHeight() + 2 * PAD_Y;

        measure(root, fm);
        gm.dispose();

        final int rowH = boxH + V_GAP;

        int maxDepth = maxDepth(root, 0);

        place(root, 0, MARGIN, rowH, boxH);

        int width  = 2 * MARGIN + root.subtreeW;
        int height = 2 * MARGIN + (maxDepth + 1) * boxH + maxDepth * V_GAP;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, width, height);
        g.setFont(font);

        drawEdges(g, root, boxH);
        drawNodes(g, root, fm, boxH);

        g.dispose();
        ImageIO.write(img, "png", out.toFile());
    }

    private static Node build(ParseTree t, String[] ruleNames) {
        Node n;
        if (t instanceof TerminalNode) {
            n = new Node(t.getText(), true);
        } else {
            RuleContext rc = (RuleContext) t;
            n = new Node(ruleNames[rc.getRuleIndex()], false);
        }
        for (int i = 0; i < t.getChildCount(); i++) {
            n.children.add(build(t.getChild(i), ruleNames));
        }
        return n;
    }

    /** Mede cada caixa e calcula a largura reservada por subárvore (empacotamento justo). */
    private static void measure(Node n, FontMetrics fm) {
        n.boxW = fm.stringWidth(n.label) + 2 * PAD_X;
        if (n.children.isEmpty()) {
            n.subtreeW = n.boxW;
            return;
        }
        int childrenW = 0;
        for (Node c : n.children) {
            measure(c, fm);
            childrenW += c.subtreeW;
        }
        childrenW += (n.children.size() - 1) * H_GAP;
        n.subtreeW = Math.max(n.boxW, childrenW);
    }

    private static int maxDepth(Node n, int depth) {
        int max = depth;
        for (Node c : n.children) {
            max = Math.max(max, maxDepth(c, depth + 1));
        }
        return max;
    }

    /** Posiciona cada nó dentro da faixa horizontal [leftX, leftX + subtreeW]. */
    private static void place(Node n, int depth, int leftX, int rowH, int boxH) {
        n.depth = depth;
        n.top = MARGIN + depth * rowH;
        if (n.children.isEmpty()) {
            n.cx = leftX + n.subtreeW / 2.0;
            return;
        }
        int childrenW = (n.children.size() - 1) * H_GAP;
        for (Node c : n.children) {
            childrenW += c.subtreeW;
        }
        int x = leftX + (n.subtreeW - childrenW) / 2; // centraliza o bloco de filhos
        for (Node c : n.children) {
            place(c, depth + 1, x, rowH, boxH);
            x += c.subtreeW + H_GAP;
        }
        n.cx = (n.children.get(0).cx + n.children.get(n.children.size() - 1).cx) / 2.0;
    }

    private static void drawEdges(Graphics2D g, Node n, int boxH) {
        g.setColor(EDGE);
        g.setStroke(new BasicStroke(1.2f));
        for (Node c : n.children) {
            g.drawLine((int) Math.round(n.cx), n.top + boxH,
                       (int) Math.round(c.cx), c.top);
            drawEdges(g, c, boxH);
        }
    }

    private static void drawNodes(Graphics2D g, Node n, FontMetrics fm, int boxH) {
        int x = (int) Math.round(n.cx - n.boxW / 2.0);
        g.setColor(n.terminal ? TERMINAL_FILL : RULE_FILL);
        g.fillRoundRect(x, n.top, n.boxW, boxH, 10, 10);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1.0f));
        g.drawRoundRect(x, n.top, n.boxW, boxH, 10, 10);

        g.setColor(TEXT);
        int textX = (int) Math.round(n.cx - fm.stringWidth(n.label) / 2.0);
        int textY = n.top + PAD_Y + fm.getAscent();
        g.drawString(n.label, textX, textY);

        for (Node c : n.children) {
            drawNodes(g, c, fm, boxH);
        }
    }
}
