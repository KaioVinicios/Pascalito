# Pascalito

Compilador didático de um subconjunto Pascal-like, implementado em **Java 21** com **ANTLR4**.
Cobre as quatro fases clássicas: léxico, sintático, semântico e geração de código (assembly
didático de três endereços + VM própria).

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
| `--emit`    | Léxico + sintático + semântico + geração. Grava `out/<basename>.asm`.      |
| `--run`     | Idem `--emit`, mas executa o assembly na VM interna.                       |
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
```

### 4. Geração — escreve o `.asm` em `out/`

```sh
./pascalito --emit examples/30_codegen_aritmetica.pas
cat out/30_codegen_aritmetica.asm
```

Trecho do `.asm`:

```
=== CODIGO ASSEMBLY GERADO ===
    PROG aritmetica
    VAR n, INTEGER
    LOAD t0, #2
    LOAD t1, #2
    ADD t2, t0, t1
    STORE n, t2
    LOAD t3, #"resultado: "
    WRITE t3
    LOAD t4, n
    WRITE t4
    HALT
```

### 5. Execução — `2 + 2 → 4`

```sh
./pascalito --run examples/30_codegen_aritmetica.pas
# resultado:
# 4
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

- **Tipos**: `integer` (0..32767), `boolean`, `string` (só via `read` ou literal `CADEIA`).
- **Operadores**: aritméticos `+ - * /`, relacionais `< <= > >= == <>`, lógicos `and or ~`
  (negação lógica), unário `+ -`.
- **Comandos**: `read(...)`, `write(...)`, atribuição, `if/else`, `while/do`, `begin/end`.
- **Identificadores**: até 16 caracteres significativos (case insensitive).
- **Constantes inteiras**: 0 a 32767.
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
│   ├── semantic/                       # tabela de símbolos + checagem de tipos
│   └── codegen/                        # gerador de assembly + máquina virtual
├── src/test/java/com/pascalito/        # 98 testes JUnit 5
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

| Suíte                    | Testes |
|--------------------------|--------|
| `TokenPrinterTest`       | 15     |
| `ParserTest`             | 24     |
| `SemanticAnalyzerTest`   | 24     |
| `CodeGeneratorTest`      | 13     |
| `VirtualMachineTest`     | 22     |
| **Total**                | **98** |

## Documentação adicional

- [`docs/requisites.md`](docs/requisites.md) — especificação do projeto.
- [`docs/grammar-analysis.md`](docs/grammar-analysis.md) — análise dos 6 conflitos da gramática original e a reescrita.
- [`docs/codegen-target.md`](docs/codegen-target.md) — decisão e descrição do alvo de geração.
- [`docs/todo.md`](docs/todo.md) — plano de execução por fase.
