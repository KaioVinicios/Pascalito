# Pascalito

Compilador didático de um subconjunto Pascal-like, implementado em **Java 21** com **ANTLR4**.
Cobre as quatro fases clássicas: léxico, sintático, semântico e geração de código.

O backend segue o pipeline de 3 estágios da especificação V2:

```
fonte → léxico → sintático → semântico → IR 3AC → otimização → assembly Intel x86
                                            │
                                            └─ VM interna (--run) valida a IR
```

- **Análise semântica**: tabela de símbolos baseada em hash com **escopos aninhados**
  (encadeamento ao escopo pai) e **deslocamento em bytes** por símbolo; overflow de
  constante (`−32768..32767`) é erro semântico fatal.
- **IR 3AC**: código de três endereços com temporárias `t_0, t_1, …` e rótulos `L_1, L_2, …`.
- **Otimização** (`--opt`): constant folding + dead code elimination sobre a IR.
- **Assembly x86** (`--asm`): Intel x86 (sintaxe Intel), `.data` (`dw`/`db`) + `.code`.

## Pré-requisitos

- JDK 21+
- Maven 3.9+

## Compilando

```sh
mvn package
```

Gera o fat jar executável em `target/pascalito.jar` (inclui o runtime do ANTLR).

## Executando

Use o wrapper `./pascalito` (compila sob demanda se o jar não existir):

```sh
./pascalito <flag> <arquivo.pas>
```

Equivalente direto via `java`:

```sh
java -jar target/pascalito.jar <flag> <arquivo.pas>
```

## Modos de execução (uma flag por fase)

| Flag        | Faz                                                                       |
|-------------|---------------------------------------------------------------------------|
| *(padrão)*  | Léxico + sintático + semântico. Falha com mensagem `linha, coluna` em qualquer erro. |
| `--lex`     | Só léxico. Imprime tabela `LINHA / TIPO / ATRIBUTO`.                       |
| `--parse`   | Léxico + sintático. Imprime "Análise sintática concluída".                 |
| `--tree`    | Combina com `--parse` ou padrão; gera a árvore sintática como imagem `out/<basename>.tree.png`. |
| `--emit`    | Léxico + sintático + semântico + geração da IR 3AC. Grava `out/<basename>.asm`. |
| `--run`     | Idem `--emit`, mas executa a IR na VM interna.                             |
| `--opt`     | Otimiza a IR 3AC (constant folding + dead code) antes de `--emit`/`--run`. |
| `--asm`     | Gera assembly **Intel x86** do 3AC otimizado. Grava `out/<basename>.asm`.  |
| `--help`    | Mostra esta mensagem.                                                      |

### Códigos de saída

| Código | Significado            |
|--------|------------------------|
| `0`    | sucesso                |
| `64`   | uso (`Usage`)          |
| `65`   | erro léxico            |
| `66`   | erro de I/O            |
| `67`   | erro sintático         |
| `68`   | erro semântico         |

## Demos rápidas

### 1. Léxico — tabela de tokens

```sh
./pascalito --lex examples/01_lexico_ok.pas
```

### 2. Sintático — árvore

```sh
./pascalito --parse --tree examples/10_sintatico_ok.pas
# Análise sintática concluída com sucesso.
# Árvore sintática gravada em out/10_sintatico_ok.tree.png
```

O `--tree` desenha a árvore de derivação como imagem PNG (sem dependências
externas — usa apenas AWT/`ImageIO` do JDK). Caixas **azuis** são regras
(não-terminais) e caixas **amarelas** são terminais (tokens). Funciona junto
com `--parse` ou no modo padrão (após o semântico).

### 3. Semântico — checagem de tipos

```sh
./pascalito examples/20_semantico_tipos_ok.pas
# Análise semântica concluída com sucesso.
# Símbolos declarados: 4
#   contador         integer  offset=0 (2 byte(s))
#   total            integer  offset=2 (2 byte(s))
#   feito            boolean  offset=4 (1 byte(s))
#   msg              string   offset=5 (2 byte(s))
```

A tabela de símbolos expõe o **deslocamento** (offset) de cada variável no frame, usado
depois pelo backend x86: `integer`/`string` ocupam 2 bytes, `boolean` 1 byte.

### 4. IR 3AC — escreve o código intermediário em `out/`

```sh
./pascalito --emit examples/30_codegen_aritmetica.pas
cat out/30_codegen_aritmetica.asm
```

```
=== CODIGO ASSEMBLY GERADO ===
    PROG aritmetica
    VAR n, INTEGER
    LOAD t_0, #2
    LOAD t_1, #2
    ADD t_2, t_0, t_1
    STORE n, t_2
    LOAD t_3, #"resultado: "
    WRITE t_3
    LOAD t_4, n
    WRITE t_4
    HALT
```

### 5. Otimização — `--opt` dobra constantes e elimina código morto

```sh
./pascalito --emit --opt examples/30_codegen_aritmetica.pas
# Otimização: 11 → 9 instruções
```

O `2 + 2` é dobrado em `LOAD t_2, #4` e as cargas mortas são removidas:

```
    LOAD t_2, #4
    STORE n, t_2
    ...
```

### 6. Assembly x86 — `--asm` traduz o 3AC otimizado

```sh
./pascalito --asm examples/31_codegen_if.pas
cat out/31_codegen_if.asm
```

Trecho (controle de fluxo com `cmp`/`setcc`/`je`/rótulos):

```asm
.data
    n        dw 0    ; INTEGER (WORD)
    ...
.code
controle:
    ...
    cmp ax, bx
    setg al
    mov [t_3], al
    mov al, [t_3]
    cmp al, 0
    je L_1                ; salta se falso
    ...
    jmp L_2
L_1:
    ...
L_2:
    call exit
```

### 7. Execução na VM — `2 + 2 → 4`

```sh
./pascalito --run examples/30_codegen_aritmetica.pas
# resultado:
# 4

./pascalito --run --opt examples/30_codegen_aritmetica.pas   # mesma saída, IR otimizada
```

## A linguagem (resumo)

```pascal
program nome;
var
  x, y: integer;
  ok: boolean;
  s: string;
begin
  read(x);
  if x > 0 then
    y := x * 2
  else
    y := -x;

  while y > 0 do
  begin
    write(y);
    y := y - 1
  end
end.
```

- **Tipos**: `integer` (2 bytes com sinal, `−32768..32767`), `boolean`, `string` (só via `read` ou literal `CADEIA`).
- **Operadores**: aritméticos `+ - * /`, relacionais `< <= > >= == <>`, lógicos `and or ~`
  (negação lógica), unário `+ -`.
- **Comandos**: `read(...)`, `write(...)`, atribuição, `if/else`, `while/do`, `begin/end`.
- **Identificadores**: até 16 caracteres significativos (case insensitive); excedente é
  truncado com **aviso** no console.
- **Constantes inteiras**: `−32768..32767`; fora da faixa é **erro semântico fatal** (overflow).
- **Strings**: literais entre `"..."`, só usadas em `write`.

## Estrutura do projeto

```
.
├── grammar/                            # gramáticas ANTLR (.g4)
│   ├── PascalitoLexer.g4
│   └── PascalitoParser.g4
├── src/main/java/com/pascalito/
│   ├── Main.java                       # driver CLI
│   ├── lex/                            # post-processing de tokens, erros léxicos
│   ├── syntax/                         # error listeners para o parser
│   ├── semantic/                       # tabela de símbolos (escopos + offset) + checagem de tipos
│   └── codegen/                        # IR 3AC, otimizador, VM e backend x86
├── src/test/java/com/pascalito/        # 138 testes JUnit 5
├── examples/                           # programas .pas por fase (01..32)
├── docs/                               # especificação, análise de gramática, decisões
└── pascalito                           # wrapper bash
```

## Testes

```sh
mvn test       # roda só os JUnit
mvn verify     # roda JUnit + ciclo completo do package
```

Cobertura por fase:

| Suíte                    | Testes  |
|--------------------------|---------|
| `TokenPrinterTest`       | 15      |
| `ParserTest`             | 24      |
| `SymbolTableTest`        | 8       |
| `SemanticAnalyzerTest`   | 34      |
| `CodeGeneratorTest`      | 16      |
| `OptimizerTest`          | 7       |
| `X86GeneratorTest`       | 10      |
| `VirtualMachineTest`     | 22      |
| `TreeImageRendererTest`  | 2       |
| **Total**                | **138** |

## Documentação adicional

- [`docs/requisites-V2.md`](docs/requisites-V2.md) — especificação do projeto (V2).
- [`docs/grammar-analysis.md`](docs/grammar-analysis.md) — análise dos 6 conflitos da gramática original e a reescrita.
- [`docs/codegen-target-V2.md`](docs/codegen-target-V2.md) — mapeamento gramática → 3AC, otimizações e backend x86.
- [`docs/codegen-target.md`](docs/codegen-target.md) — decisão e descrição do alvo de geração (IR/VM).
- [`docs/todo-V2.md`](docs/todo-V2.md) — plano de execução V2 por fase, com decisões registradas.
