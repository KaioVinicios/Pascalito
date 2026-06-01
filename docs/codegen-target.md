# Alvo de Geração de Código

## Decisão

**Assembly didático** — não é JVM bytecode, não é C, não é interpretador AST puro,
não é uma ISA real (x86/ARM/MIPS). É um código intermediário de **três endereços**
sobre uma máquina virtual abstrata inventada para fins de ensino.

A execução ocorre via VM própria (`com.pascalito.codegen.VirtualMachine`), que
interpreta diretamente a `List<Instruction>` emitida pelo `CodeGenerator`. Isso
permite validar saída (`write(2+2) → 4`) sem depender de toolchain externa (`as`,
`ld`, `gcc`, `nasm`, MARS, SPIM, etc.).

## Características

1. **Registradores virtuais ilimitados** — temporários `t0, t1, t2, …` gerados
   sob demanda. Não há alocação de registradores físicos, nem spill.
2. **Formato three-address** — toda operação binária é `OP dest, esq, dir`.
   Exemplo: `ADD t2, t0, t1`.
3. **Imediatos com prefixo `#`** — `LOAD t0, #42`, `LOAD t1, #true`,
   `LOAD t2, #"hello"`. O tipo do imediato é inferido pela sintaxe do literal.
4. **Memória nomeada** — variáveis são acessadas por nome (truncado a 16 chars,
   mesma regra do `SymbolTable`), não por endereço.
5. **Controle de fluxo por labels** — `LABEL`, `JUMP`, `JUMPF` (jump if false).
   Sem offsets numéricos.
6. **Comentários em assembly** — prefixo `;` (gerados quando útil pelo emissor).

## Conjunto de instruções

| Categoria   | Mnemônicos                              | Forma                                   |
|-------------|-----------------------------------------|-----------------------------------------|
| Programa    | `PROG`, `HALT`                          | `PROG nome` · `HALT`                    |
| Memória     | `VAR`, `LOAD`, `STORE`                  | `VAR nome, TIPO` · `LOAD t, src` · `STORE nome, t` |
| Aritmética  | `ADD`, `SUB`, `MUL`, `DIV`              | `OP dest, esq, dir` (operandos integer) |
| Relacional  | `LT`, `LE`, `GT`, `GE`, `EQ`, `NEQ`     | `OP dest, esq, dir` (resultado boolean) |
| Lógica      | `AND`, `OR`, `NOT`                      | `OP dest, esq, dir` · `NOT dest, src`   |
| Controle    | `LABEL`, `JUMP`, `JUMPF`                | `LABEL nome` · `JUMP label` · `JUMPF tCond, label` |
| E/S         | `READ`, `WRITE`                         | `READ nome` · `WRITE t`                 |

## Por que essa escolha

- **Didática**: três endereços é o que aparece nos livros (Aho/Sethi/Ullman cap. 8).
- **Auto-suficiente**: a VM embute toda a runtime — nada de `as`/`ld`, nada de
  Mach-O, nada de containers para rodar ELF no macOS.
- **Testável**: `VirtualMachine` aceita `Reader`/`PrintWriter` injetáveis, então
  os testes JUnit comparam stdout do programa contra strings literais.
- **Pequena**: 22 mnemônicos cobrem toda a linguagem Pascalito.

## Como rodar

```sh
# Emite o .asm em out/<basename>.asm
mvn -q exec:java -Dexec.mainClass=com.pascalito.Main -Dexec.args="--emit examples/30_codegen_aritmetica.pas"

# Compila e executa o programa
mvn -q exec:java -Dexec.mainClass=com.pascalito.Main -Dexec.args="--run examples/30_codegen_aritmetica.pas"
```

## Alternativas descartadas (e por quê)

| Alvo                          | Por que não                                                |
|-------------------------------|------------------------------------------------------------|
| Interpretador AST puro        | Não exercita "geração" de código nenhuma — só percorre AST. |
| JVM bytecode (ASM)            | Boilerplate alto (frames, stack maps, descritores).         |
| C (transpilação)              | Exige toolchain externa pra rodar os testes e2e.            |
| x86_64 / ARM64 nativo         | ABI macOS é verbosa (Mach-O, `_main`, libc/SVC).            |
| MIPS via MARS/SPIM            | Dependência externa pesada para rodar `.asm`.               |
