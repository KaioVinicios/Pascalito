package com.pascalito.codegen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Otimizador que opera sobre a lista linear de tuplas 3AC (a IR), sem alterar o
 * comportamento lógico do programa. Aplica duas otimizações de alta confiança:
 *
 * <ol>
 *   <li><b>Constant folding</b> — operações cujos operandos são constantes conhecidas
 *       (ex.: {@code ADD t, t2, t3} com {@code t2=#2}, {@code t3=#3}) são substituídas
 *       por um carregamento direto do resultado ({@code LOAD t, #5}).</li>
 *   <li><b>Dead code elimination</b> — definições de temporárias <i>puras</i>
 *       (LOAD/aritmética/relacional/lógica) cujo destino nunca é lido são removidas.</li>
 * </ol>
 *
 * <p>Ambas se apoiam em duas propriedades da IR gerada: cada temporária recebe
 * atribuição uma única vez (single-assignment) e os operandos de uma operação são
 * sempre temporárias definidas no mesmo bloco básico. O folding é mantido <b>local
 * ao bloco básico</b> (o mapa de constantes é zerado em {@code LABEL}/{@code JUMP}/{@code JUMPF}),
 * garantindo que nunca se use uma constante proveniente de outro caminho de execução.</p>
 */
public final class Optimizer {

    /** Mnemônicos que definem uma temporária e não têm efeito colateral (candidatos a DCE). */
    private static final Set<String> PURE_DEFS = Set.of(
            "LOAD", "ADD", "SUB", "MUL", "DIV",
            "LT", "LE", "GT", "GE", "EQ", "NEQ",
            "AND", "OR", "NOT");

    private Optimizer() {}

    public static List<Instruction> optimize(List<Instruction> code) {
        List<Instruction> folded = constantFold(code);
        return deadCodeElimination(folded);
    }

    // ===== Constant folding =====

    private static List<Instruction> constantFold(List<Instruction> code) {
        List<Instruction> out = new ArrayList<>(code.size());
        Map<String, Object> consts = new HashMap<>(); // temp -> Integer | Boolean

        for (Instruction ins : code) {
            switch (ins.op()) {
                case "LABEL", "JUMP", "JUMPF" -> consts.clear(); // fronteira de bloco básico
                default -> { }
            }

            Instruction folded = tryFold(ins, consts);
            out.add(folded);

            // registra a constante produzida (após eventual folding) para uso a jusante
            if (folded.op().equals("LOAD")) {
                Object value = parseImmediate(folded.args().get(1));
                if (value != null) {
                    consts.put(folded.args().get(0), value);
                }
            }
        }
        return out;
    }

    private static Instruction tryFold(Instruction ins, Map<String, Object> consts) {
        List<String> a = ins.args();
        String op = ins.op();

        // unário: NOT dest, src
        if (op.equals("NOT") && a.size() == 2) {
            if (consts.get(a.get(1)) instanceof Boolean b) {
                return load(a.get(0), !b);
            }
            return ins;
        }

        // binário: OP dest, esq, dir
        if (a.size() != 3) return ins;
        Object l = consts.get(a.get(1));
        Object r = consts.get(a.get(2));
        if (l == null || r == null) return ins;

        if (l instanceof Integer li && r instanceof Integer ri) {
            switch (op) {
                case "ADD": return load(a.get(0), li + ri);
                case "SUB": return load(a.get(0), li - ri);
                case "MUL": return load(a.get(0), li * ri);
                case "DIV": return (ri == 0) ? ins : load(a.get(0), li / ri); // preserva erro em runtime
                case "LT":  return load(a.get(0), li <  ri);
                case "LE":  return load(a.get(0), li <= ri);
                case "GT":  return load(a.get(0), li >  ri);
                case "GE":  return load(a.get(0), li >= ri);
                case "EQ":  return load(a.get(0), li.intValue() == ri.intValue());
                case "NEQ": return load(a.get(0), li.intValue() != ri.intValue());
                default: return ins;
            }
        }
        if (l instanceof Boolean lb && r instanceof Boolean rb) {
            switch (op) {
                case "AND": return load(a.get(0), lb && rb);
                case "OR":  return load(a.get(0), lb || rb);
                case "EQ":  return load(a.get(0), lb == rb);
                case "NEQ": return load(a.get(0), lb != rb);
                default: return ins;
            }
        }
        return ins;
    }

    private static Instruction load(String dest, int value) {
        return new Instruction("LOAD", dest, "#" + value);
    }

    private static Instruction load(String dest, boolean value) {
        return new Instruction("LOAD", dest, "#" + value);
    }

    /** Converte o imediato de um LOAD em {@code Integer}/{@code Boolean}, ou {@code null} se não-constante. */
    private static Object parseImmediate(String src) {
        if (src == null || !src.startsWith("#")) return null; // carga de variável: não-constante
        String lit = src.substring(1);
        if (lit.equals("true")) return Boolean.TRUE;
        if (lit.equals("false")) return Boolean.FALSE;
        if (lit.startsWith("\"")) return null; // cadeia: não dobramos
        try {
            return Integer.parseInt(lit);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ===== Dead code elimination =====

    private static List<Instruction> deadCodeElimination(List<Instruction> code) {
        List<Instruction> current = code;
        boolean changed = true;
        while (changed) {
            Set<String> used = collectUsedTemps(current);
            List<Instruction> next = new ArrayList<>(current.size());
            changed = false;
            for (Instruction ins : current) {
                if (PURE_DEFS.contains(ins.op()) && !used.contains(ins.args().get(0))) {
                    changed = true; // destino nunca lido → instrução morta
                    continue;
                }
                next.add(ins);
            }
            current = next;
        }
        return current;
    }

    /** Conjunto de temporárias <i>lidas</i> (como operando fonte) por alguma instrução. */
    private static Set<String> collectUsedTemps(List<Instruction> code) {
        Set<String> used = new HashSet<>();
        for (Instruction ins : code) {
            List<String> a = ins.args();
            switch (ins.op()) {
                case "STORE" -> markTemp(used, a.get(1)); // STORE nome, t
                case "WRITE" -> markTemp(used, a.get(0)); // WRITE t
                case "JUMPF" -> markTemp(used, a.get(0)); // JUMPF tCond, label
                case "NOT" -> markTemp(used, a.get(1));
                case "ADD", "SUB", "MUL", "DIV",
                     "LT", "LE", "GT", "GE", "EQ", "NEQ",
                     "AND", "OR" -> { markTemp(used, a.get(1)); markTemp(used, a.get(2)); }
                default -> { } // LOAD/VAR/READ/PROG/HALT/LABEL/JUMP não leem temporárias
            }
        }
        return used;
    }

    private static void markTemp(Set<String> used, String operand) {
        if (operand != null && operand.startsWith("t_")) {
            used.add(operand);
        }
    }
}
