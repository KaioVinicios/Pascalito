package com.pascalito.codegen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gera assembly <b>Intel x86</b> (sintaxe Intel clássica) a partir da IR 3AC
 * (idealmente já otimizada). Modelo de geração ingênua e correta: cada variável e
 * cada temporária recebe um slot em {@code .data}; cada instrução 3AC carrega seus
 * operandos em registradores, opera e grava o resultado de volta na memória.
 *
 * <p>Mapeamento de tipos (spec V2): inteiros são WORD (2 bytes, {@code dw}) e usam
 * AX/BX (16 bits); booleanos são byte ({@code db}, 0/1) e usam AL/BL (8 bits). Cadeias
 * são literais em {@code .data}; uma temporária de cadeia guarda o offset do literal.</p>
 *
 * <p>Montar de fato (NASM/MASM) é opcional — a saída assume rotinas externas de I/O,
 * documentadas no cabeçalho do arquivo e em {@code docs/codegen-target-V2.md}:
 * {@code read_word}, {@code write_word}, {@code write_str} e {@code exit}.</p>
 */
public final class X86Generator {

    private enum Kind { INT, BOOL, STR }

    private X86Generator() {}

    public static String generate(List<Instruction> code) {
        return new Worker(code).run();
    }

    private static final class Worker {
        private final List<Instruction> code;
        private final Map<String, Kind> varKind = new LinkedHashMap<>();   // variável -> tipo
        private final Map<String, Kind> tempKind = new LinkedHashMap<>();  // temporária -> tipo
        private final Map<String, String> strLiterals = new LinkedHashMap<>(); // rótulo -> texto
        private final StringBuilder text = new StringBuilder();            // seção .code
        private String programName = "main";
        private int strCounter = 0;

        Worker(List<Instruction> code) {
            this.code = code;
        }

        String run() {
            classify();
            emitCode();
            return assemble();
        }

        // ===== Pré-análise: tipos de variáveis/temporárias e literais de cadeia =====

        private void classify() {
            for (Instruction ins : code) {
                List<String> a = ins.args();
                switch (ins.op()) {
                    case "PROG" -> programName = sanitize(a.get(0));
                    case "VAR"  -> varKind.put(a.get(0), kindOfType(a.get(1)));
                    case "LOAD" -> tempKind.put(a.get(0), kindOfLoad(a.get(1)));
                    case "ADD", "SUB", "MUL", "DIV" -> tempKind.put(a.get(0), Kind.INT);
                    case "LT", "LE", "GT", "GE", "EQ", "NEQ",
                         "AND", "OR", "NOT" -> tempKind.put(a.get(0), Kind.BOOL);
                    default -> { }
                }
            }
        }

        private Kind kindOfType(String type) {
            return switch (type) {
                case "BOOLEAN" -> Kind.BOOL;
                case "STRING"  -> Kind.STR;
                default        -> Kind.INT;
            };
        }

        private Kind kindOfLoad(String src) {
            if (src.startsWith("#\"")) return Kind.STR;
            if (src.equals("#true") || src.equals("#false")) return Kind.BOOL;
            if (src.startsWith("#")) return Kind.INT;
            // carga de variável: herda o tipo da variável
            return varKind.getOrDefault(src, Kind.INT);
        }

        // ===== Tradução das instruções 3AC para a seção .code =====

        private void emitCode() {
            label(programName);
            for (Instruction ins : code) {
                List<String> a = ins.args();
                switch (ins.op()) {
                    case "PROG", "VAR", "NOP" -> { } // tratados em .data / cabeçalho
                    case "LOAD"  -> emitLoad(a.get(0), a.get(1));
                    case "STORE" -> emitMove(a.get(0), a.get(1));
                    case "ADD" -> emitBinInt("add",  a);
                    case "SUB" -> emitBinInt("sub",  a);
                    case "MUL" -> emitMul(a);
                    case "DIV" -> emitDiv(a);
                    case "LT"  -> emitRel("setl",  a);
                    case "LE"  -> emitRel("setle", a);
                    case "GT"  -> emitRel("setg",  a);
                    case "GE"  -> emitRel("setge", a);
                    case "EQ"  -> emitRel("sete",  a);
                    case "NEQ" -> emitRel("setne", a);
                    case "AND" -> emitBinBool("and", a);
                    case "OR"  -> emitBinBool("or",  a);
                    case "NOT" -> emitNot(a);
                    case "LABEL" -> label(a.get(0));
                    case "JUMP"  -> ins("jmp " + a.get(0));
                    case "JUMPF" -> emitJumpFalse(a.get(0), a.get(1));
                    case "READ"  -> emitRead(a.get(0));
                    case "WRITE" -> emitWrite(a.get(0));
                    case "HALT"  -> ins("call exit");
                    default -> throw new IllegalStateException("Mnemônico 3AC sem tradução x86: " + ins.op());
                }
            }
        }

        /** LOAD dest, src — imediato, cadeia ou carga de variável. */
        private void emitLoad(String dest, String src) {
            if (src.startsWith("#\"")) {
                String lit = src.substring(2, src.length() - 1); // remove #" e "
                String lbl = "str_" + (strCounter++);
                strLiterals.put(lbl, lit);
                ins("mov ax, offset " + lbl, "endereço da cadeia");
                ins("mov " + mem(dest) + ", ax");
                return;
            }
            if (src.equals("#true"))  { ins("mov ax, 1");  ins("mov " + mem(dest) + ", ax"); return; }
            if (src.equals("#false")) { ins("mov ax, 0");  ins("mov " + mem(dest) + ", ax"); return; }
            if (src.startsWith("#"))  { ins("mov ax, " + src.substring(1)); ins("mov " + mem(dest) + ", ax"); return; }
            // carga de variável
            emitMove(dest, src);
        }

        /** Copia src -> dest passando por um registrador do tamanho adequado. */
        private void emitMove(String dest, String src) {
            String reg = kindOf(dest) == Kind.BOOL && kindOf(src) == Kind.BOOL ? "al" : "ax";
            ins("mov " + reg + ", " + mem(src));
            ins("mov " + mem(dest) + ", " + reg);
        }

        private void emitBinInt(String op, List<String> a) {
            ins("mov ax, " + mem(a.get(1)));
            ins("mov bx, " + mem(a.get(2)));
            ins(op + " ax, bx");
            ins("mov " + mem(a.get(0)) + ", ax");
        }

        private void emitMul(List<String> a) {
            ins("mov ax, " + mem(a.get(1)));
            ins("mov bx, " + mem(a.get(2)));
            ins("imul bx", "AX <- AX * BX");
            ins("mov " + mem(a.get(0)) + ", ax");
        }

        private void emitDiv(List<String> a) {
            ins("mov ax, " + mem(a.get(1)));
            ins("mov bx, " + mem(a.get(2)));
            ins("cwd", "estende sinal de AX em DX:AX");
            ins("idiv bx", "AX <- DX:AX / BX");
            ins("mov " + mem(a.get(0)) + ", ax");
        }

        /** Relacional: compara inteiros (16 bits) e materializa o booleano via setcc (8 bits). */
        private void emitRel(String setcc, List<String> a) {
            ins("mov ax, " + mem(a.get(1)));
            ins("mov bx, " + mem(a.get(2)));
            ins("cmp ax, bx");
            ins(setcc + " al");
            ins("mov " + mem(a.get(0)) + ", al");
        }

        private void emitBinBool(String op, List<String> a) {
            ins("mov al, " + mem(a.get(1)));
            ins("mov bl, " + mem(a.get(2)));
            ins(op + " al, bl");
            ins("mov " + mem(a.get(0)) + ", al");
        }

        /** NOT lógico de booleano: x86 'not' é bit a bit, então usamos 'xor al, 1' (0<->1). */
        private void emitNot(List<String> a) {
            ins("mov al, " + mem(a.get(1)));
            ins("xor al, 1", "NOT lógico (booleano 0/1)");
            ins("mov " + mem(a.get(0)) + ", al");
        }

        private void emitJumpFalse(String cond, String target) {
            String reg = kindOf(cond) == Kind.BOOL ? "al" : "ax";
            ins("mov " + reg + ", " + mem(cond));
            ins("cmp " + reg + ", 0");
            ins("je " + target, "salta se falso");
        }

        private void emitRead(String var) {
            String routine = switch (varKind.getOrDefault(var, Kind.INT)) {
                case BOOL -> "read_bool";
                case STR  -> "read_str";
                default   -> "read_word";
            };
            ins("call " + routine, "lê de stdin -> AX");
            ins("mov " + mem(var) + ", " + (varKind.get(var) == Kind.BOOL ? "al" : "ax"));
        }

        private void emitWrite(String src) {
            switch (kindOf(src)) {
                case STR -> { ins("mov dx, " + mem(src), "offset da cadeia"); ins("call write_str"); }
                case BOOL -> {
                    ins("mov al, " + mem(src));
                    ins("mov ah, 0", "zera byte alto");
                    ins("call write_word", "imprime 0/1");
                }
                default -> { ins("mov ax, " + mem(src)); ins("call write_word"); }
            }
        }

        // ===== Montagem do arquivo final =====

        private String assemble() {
            StringBuilder sb = new StringBuilder();
            sb.append(header());
            sb.append(".data\n");
            // variáveis da Tabela de Símbolos
            for (var e : varKind.entrySet()) {
                sb.append(dataLine(e.getKey(), e.getValue(), true));
            }
            // temporárias virtuais (scratch)
            if (!tempKind.isEmpty()) {
                sb.append("    ; --- temporárias da IR 3AC ---\n");
                for (var e : tempKind.entrySet()) {
                    sb.append(dataLine(e.getKey(), e.getValue(), false));
                }
            }
            // literais de cadeia
            if (!strLiterals.isEmpty()) {
                sb.append("    ; --- literais de cadeia ---\n");
                for (var e : strLiterals.entrySet()) {
                    sb.append("    %-8s db \"%s\", 0\n".formatted(e.getKey(), e.getValue()));
                }
            }
            sb.append('\n').append(".code\n");
            sb.append(text);
            return sb.toString();
        }

        private String dataLine(String name, Kind kind, boolean fromSymbolTable) {
            return switch (kind) {
                case BOOL -> "    %-8s db 0%s\n".formatted(name, comment("BOOLEAN (0=false,1=true)", fromSymbolTable));
                case STR  -> "    %-8s dw 0%s\n".formatted(name, comment("STRING (offset)", fromSymbolTable));
                default   -> "    %-8s dw 0%s\n".formatted(name, comment("INTEGER (WORD)", fromSymbolTable));
            };
        }

        private String comment(String txt, boolean show) {
            return show ? "    ; " + txt : "";
        }

        private String header() {
            return """
                    ; ============================================================
                    ;  Pascalito -> Intel x86 (sintaxe Intel; WORD = inteiro 2 bytes)
                    ;  Registradores: INTEGER -> AX/BX/CX/DX ; BOOLEAN -> AL/BL/CL/DL
                    ;  Convenção de I/O (rotinas externas):
                    ;    read_word  : lê inteiro de stdin -> AX
                    ;    write_word : imprime AX (inteiro)
                    ;    write_str  : imprime a cadeia apontada por DX
                    ;    exit       : encerra o programa
                    ; ============================================================

                    """;
        }

        // ===== Utilidades =====

        private Kind kindOf(String operand) {
            if (operand.startsWith("t_")) return tempKind.getOrDefault(operand, Kind.INT);
            return varKind.getOrDefault(operand, Kind.INT);
        }

        private String mem(String operand) {
            return "[" + operand + "]";
        }

        private void ins(String s) {
            text.append("    ").append(s).append('\n');
        }

        private void ins(String s, String comment) {
            text.append("    ").append(String.format("%-22s", s)).append("; ").append(comment).append('\n');
        }

        private void label(String name) {
            text.append(name).append(":\n");
        }

        private String sanitize(String name) {
            return name.replaceAll("[^A-Za-z0-9_]", "_");
        }
    }
}
