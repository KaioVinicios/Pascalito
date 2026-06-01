# Pascalito — Arquitetura

Documento de entrada para entender o projeto. Cobre **o que cada pasta/arquivo faz**,
**como o processamento flui de uma ponta a outra**, e **as decisões de projeto que ditaram
essa estrutura**. Para detalhes específicos de cada fase, ver:

- [`requisites.md`](./requisites.md) — especificação da linguagem.
- [`grammar-analysis.md`](./grammar-analysis.md) — análise dos conflitos da gramática original e como foram resolvidos.
- [`codegen-target.md`](./codegen-target.md) — decisão e descrição do alvo de geração.
- [`todo.md`](./todo.md) — plano de execução por fase (checklist).

---

## 1. Visão geral

Pascalito é um compilador completo de um subconjunto Pascal-like, escrito em
**Java 21** sobre **ANTLR 4.13.2**, empacotado por **Maven**. Cobre as quatro fases
clássicas:

```
.pas ──► [LÉXICO] ──► [SINTÁTICO] ──► [SEMÂNTICO] ──► [GERAÇÃO] ──► .asm + VM ──► stdout
         tokens       árvore         tabela de tipos  3-endereços   execução
```

Não há toolchain externa: o alvo de geração é uma **máquina virtual didática própria**
(ver [`codegen-target.md`](./codegen-target.md)), então `./pascalito --run prog.pas`
executa o programa e captura stdout num único processo Java.

---

## 2. Estrutura de diretórios

```
.
├── grammar/                 # Fonte da verdade da linguagem (ANTLR .g4)
├── src/main/java/com/pascalito/
│   ├── Main.java            # Driver CLI: parse de flags + orquestração
│   ├── lex/                 # Pós-processamento de tokens + erros léxicos
│   ├── syntax/              # Coleta de erros sintáticos do ANTLR
│   ├── semantic/            # Tabela de símbolos + checagem de tipos
│   └── codegen/             # Gerador de assembly + máquina virtual
├── src/test/java/com/pascalito/   # 98 testes JUnit 5, espelhando os pacotes acima
├── src/main/resources/      # vazio (.gitkeep) — reservado p/ runtime se necessário
├── src/test/resources/      # vazio (.gitkeep) — reservado p/ fixtures
├── examples/                # programas .pas por fase (01..32)
├── docs/                    # esta pasta — especificação e decisões
├── pom.xml                  # build (ANTLR plugin + shade + surefire)
├── pascalito                # wrapper bash (build sob demanda + exec java -jar)
├── README.md                # quickstart para o usuário final
└── .gitignore               # ignora target/, out/, IDE/OS
```

### 2.1 `grammar/` — fonte da verdade

| Arquivo                | Responsabilidade |
|------------------------|------------------|
| `PascalitoLexer.g4`    | Definição de **tokens**: palavras reservadas, operadores, símbolos, `ID`, `CTE`, `CADEIA`, espaços e comentários `/* ... */`. Usa `caseInsensitive = true`. |
| `PascalitoParser.g4`   | **Gramática livre de contexto** reescrita para ser conflict-free: expressão estratificada em 7 camadas de precedência (`or < and < rel < add < mul < unário < átomo`), dangling-else resolvido com `(ELSE cmd)?` greedy. |

O `antlr4-maven-plugin` lê esses dois arquivos em `generate-sources` e produz
`target/generated-sources/antlr4/PascalitoLexer.java`, `PascalitoParser.java`,
`PascalitoParserBaseVisitor.java` etc., todos no pacote `com.pascalito.parser`
(controlado por `-package com.pascalito.parser` no `pom.xml:51-54`).

**Decisão:** manter os `.g4` **fora** de `src/main/antlr4/` (convenção default) é
deliberado — facilita encontrar a gramática para quem é novo no projeto e
documenta seu papel central.

### 2.2 `src/main/java/com/pascalito/`

#### `Main.java` — driver

Faz parse de flags, escolhe o modo de execução e cuida de exit codes:

| Flag      | Pipeline                              |
|-----------|---------------------------------------|
| *(none)*  | lex → parse → semantic                |
| `--lex`   | lex (imprime tabela de tokens)        |
| `--parse` | lex → parse                           |
| `--emit`  | lex → parse → semantic → codegen → `out/<basename>.asm` |
| `--run`   | lex → parse → semantic → codegen → VM |
| `--tree`  | sufixo: imprime a árvore sintática    |

Internamente, `parseFile` e `analyzeFile` retornam `record`s (`ParseOutcome`,
`SemanticOutcome`) com `exitCode` + artefatos — assim cada modo `runX`
reaproveita as fases anteriores sem duplicar setup.

#### `lex/` — léxico

| Arquivo                  | Responsabilidade |
|--------------------------|------------------|
| `LexicalException.java`  | Exceção checada com `line`/`column`/`message`. |
| `LexicalErrorListener.java` | `BaseErrorListener` do ANTLR que converte `syntaxError` do lexer em `LexicalException` (interrompe na hora). |
| `TokenInfo.java`         | Record `(linha, tipo, atributo)` — saída tabular. |
| `TokenPrinter.java`      | Pós-processa a lista de tokens: trunca `ID` a 16 chars, valida `CTE` em `0..32767`, mapeia cada token para a categoria `(TIPO, ATRIBUTO)` exigida pelo spec, e formata em colunas no stdout. |

**Decisão:** truncamento silencioso de `ID` no léxico, validação de range de `CTE`
como erro léxico. Sinal de `CTE` é tratado como `OPAD` separado (`-5` são 2
tokens), facilitando a precedência no parser.

#### `syntax/` — sintático

| Arquivo                  | Responsabilidade |
|--------------------------|------------------|
| `SyntaxError.java`       | Record `(linha, coluna, mensagem)`. |
| `SyntaxErrorListener.java` | `BaseErrorListener` que **acumula** todos os erros (em vez de abortar), permitindo relatar tudo de uma vez. |

#### `semantic/` — semântico

| Arquivo                | Responsabilidade |
|------------------------|------------------|
| `Type.java`            | Enum `INTEGER, BOOLEAN, STRING, ERROR`. `ERROR` existe para **propagação** — evita cascata de mensagens derivadas. |
| `Symbol.java`          | Record `(name, type, line, column)`. |
| `SymbolTable.java`     | Escopo único (a linguagem não tem procedimentos). Chaves normalizadas a 16 chars (mesma regra do léxico) → colisões viram redeclaração. |
| `SemanticError.java`   | Record com formatação `"linha X, coluna Y: msg"`. |
| `SemanticAnalyzer.java` | `Visitor<Type>` sobre a AST. Cada `visit*` retorna o tipo inferido (ou `ERROR`). Verifica: redeclaração, uso de não declarada, atribuição compatível, aritméticos (`integer`), lógicos (`boolean`), relacionais (mesmo tipo, não-string), condições de `if`/`while` (`boolean`). |

**Decisão:** propagação `ERROR` — quando uma sub-expressão tem erro, retorna
`ERROR` em vez de um tipo concreto, e os operadores acima fazem **short-circuit**
nesse valor (não geram erro novo). Resultado: 1 erro real → 1 mensagem, não 5.

#### `codegen/` — geração + execução

| Arquivo                  | Responsabilidade |
|--------------------------|------------------|
| `Instruction.java`       | Record `(op, args, comment)` com `toString()` produzindo texto assembly indentado. |
| `CodeGenerator.java`     | `Visitor<String>` sobre a AST. Retorna o **nome do temp** que segura o resultado de cada expressão. Aloca temps `t0, t1, …` e labels `L_else_N`/`L_endif_N`/`L_while_N`/`L_endwhile_N` sob demanda. |
| `VirtualMachine.java`    | Interpreta `List<Instruction>` contra `Reader`/`PrintWriter` injetáveis. Estado: `vars`, `temps`, `varTypes`, `labels → pc`. Faz duas passadas: índice de labels + execução. |

**Decisão:** o `CodeGenerator` retorna `String` (nome do temp) em vez de objetos
opacos. Isso casa diretamente com o formato 3-endereços
(`ADD t2, t0, t1` ← `acc = right`), e a VM trabalha com `Map<String, Object>`
sem precisar dispatch por tipo de nó.

### 2.3 `src/test/java/com/pascalito/`

Espelha exatamente os pacotes de produção. 5 suítes, 98 testes:

| Suíte                  | Foco | Testes |
|------------------------|------|--------|
| `TokenPrinterTest`     | categorização, truncamento, validação de CTE | 15 |
| `ParserTest`           | construções válidas + erros + precedência + dangling-else | 24 |
| `SemanticAnalyzerTest` | tipos, redeclaração, short-circuit de ERROR | 24 |
| `CodeGeneratorTest`    | emissão correta de cada construção | 13 |
| `VirtualMachineTest`   | **end-to-end**: programa → stdout esperado | 22 |

A separação `CodeGeneratorTest` × `VirtualMachineTest` é proposital: o primeiro
verifica que **o assembly certo foi emitido** (estrutura), o segundo verifica
que **o resultado executado bate** (semântica). Isso pega bugs onde a estrutura
parece certa mas a execução diverge (ou vice-versa).

### 2.4 `examples/`

Programas `.pas` numerados por fase, usados como fixtures de teste e demos:

| Faixa  | Fase | Característica |
|--------|------|----------------|
| 01–04  | Léxico | OK + erro de símbolo + ID longo + CTE overflow |
| 10–13  | Sintático | OK + dangling-else + precedência + erros |
| 20–23  | Semântico | OK + não declarada + redeclaração + tipos |
| 30–32  | Codegen | aritmética + if/else + while |

### 2.5 Build e empacotamento

| Arquivo       | Papel |
|---------------|-------|
| `pom.xml`     | 3 plugins: `antlr4-maven-plugin` (gera parser), `maven-surefire-plugin` (testes), `maven-shade-plugin` (fat jar com `Main-Class`). |
| `pascalito`   | Wrapper bash: detecta jar ausente → `mvn -q package -DskipTests` → `exec java -jar`. |
| `target/`     | Gerado, ignorado pelo git. |
| `out/`        | Saída de `--emit` (`.asm`), ignorada pelo git. |

---

## 3. Pipeline de processamento

### 3.1 Fluxo de chamada (modo `--run`)

```
Main.main(args)
  └─ runProgram(source)
      ├─ analyzeFile(source)
      │   └─ parseFile(source)
      │       ├─ CharStreams.fromPath(source)
      │       ├─ PascalitoLexer + LexicalErrorListener         ── [1] LÉXICO
      │       │     └─ CommonTokenStream.fill()  ──► LexicalException? exit 65
      │       ├─ PascalitoParser + SyntaxErrorListener         ── [2] SINTÁTICO
      │       │     └─ parser.prog()  ──► lista de SyntaxError? exit 67
      │       └─ returns (tree, parser)
      │   └─ SemanticAnalyzer.visit(tree)                       ── [3] SEMÂNTICO
      │         └─ analyzer.hasErrors()  ──► exit 68
      ├─ CodeGenerator.visit(tree)                              ── [4] GERAÇÃO
      │     └─ produz List<Instruction>
      └─ VirtualMachine.run()                                   ── [5] EXECUÇÃO
            └─ interpreta List<Instruction>, escreve em stdout
```

### 3.2 Detalhe por fase

#### [1] Léxico — texto → tokens

- **Entrada**: `CharStream` (arquivo `.pas`).
- **Atores**: `PascalitoLexer` (gerado), `LexicalErrorListener`, `TokenPrinter` (só no modo `--lex`).
- **Saída**: `CommonTokenStream` consumido pelo parser, ou `LexicalException` se aparecer caractere inválido / `CTE` fora do range.
- **Decisões**: `ID` truncado a 16 chars no `TokenPrinter`; `CTE` validado em `0..32767`; sinal de negativo é token separado (`OPAD`).

#### [2] Sintático — tokens → AST

- **Entrada**: token stream.
- **Atores**: `PascalitoParser` (gerado), `SyntaxErrorListener`.
- **Saída**: `ParseTree` da regra raiz `prog`, ou lista acumulada de `SyntaxError`.
- **Decisões**: gramática reescrita para eliminar 6 conflitos da especificação original (ver [`grammar-analysis.md`](./grammar-analysis.md)); erros são **acumulados**, não abortam na primeira ocorrência.

#### [3] Semântico — AST → AST validada + tabela de símbolos

- **Entrada**: `ParseTree`.
- **Atores**: `SemanticAnalyzer` (Visitor sobre a AST).
- **Saída**: `SymbolTable` populada + lista de `SemanticError`; ou árvore validada se `!hasErrors()`.
- **Decisões**:
  - Escopo único (linguagem não tem procedimentos/funções).
  - Tipo `ERROR` propaga sem cascatear mensagens (visto em `errorTypePropagatesWithoutCascadingMessages`).
  - String só pode ser inicializada via `read` (atribuição direta é bloqueada).
  - Identificadores normalizados a 16 chars antes do `lookup`/`declare`.

#### [4] Geração — AST validada → assembly

- **Entrada**: `ParseTree` validado.
- **Atores**: `CodeGenerator` (Visitor `<String>`).
- **Saída**: `List<Instruction>` no formato 3-endereços com temps `t0..tN` e labels nomeadas.
- **Decisões**: ver [`codegen-target.md`](./codegen-target.md). Em resumo: VM didática própria, registradores virtuais ilimitados, controle via labels (sem offsets), unário `-` é açúcar para `SUB t', #0, t`, unário `~` mapeia direto pra `NOT`.

#### [5] Execução — assembly → stdout

- **Entrada**: `List<Instruction>` + `Reader` (stdin) + `PrintWriter` (stdout).
- **Atores**: `VirtualMachine`.
- **Saída**: stdout do programa, ou `VmException` em erro de runtime (divisão por zero, EOF inesperado).
- **Decisões**: streams injetáveis (em vez de fixar em `System.in/out`) — viabiliza os 22 testes end-to-end do `VirtualMachineTest` que comparam stdout contra strings literais. Defaults de variáveis: `0` / `false` / `""`.

---

## 4. Decisões transversais

| Decisão | Onde aparece | Por quê |
|---------|--------------|---------|
| Mensagens em PT, código em EN | CLI + comentários × identificadores | Spec do trabalho é em PT; código segue convenção Java. |
| Conventional Commits em EN | `git log` | Padrão da indústria, facilita ferramentas (semantic-release etc.). |
| Identificador truncado a 16 chars | `SymbolTable.normalize` + `TokenPrinter` + `CodeGenerator` | Exigência da especificação. Centralizado em `SymbolTable.normalize()`. |
| Erros acumulados, não abortam | `SyntaxErrorListener`, `SemanticAnalyzer.errors` | Spec pede relatório completo na primeira execução. |
| Exit codes BSD-like (`64..68`) | `Main` | Convenção `sysexits.h`. Permite scripts/CI distinguirem o tipo de falha. |
| Streams injetáveis na VM | `VirtualMachine` recebe `Reader`/`PrintWriter` | Necessário para testes end-to-end deterministicos. |
| `target/` e `out/` no `.gitignore` | `.gitignore` | Artefatos derivados — não pertencem ao histórico. |
| Plugin ANTLR aponta para `grammar/` (não `src/main/antlr4/`) | `pom.xml:47` | Coloca a gramática no nível de `src/`, sinalizando que ela é o input primário do projeto. |

---

## 5. Como adicionar coisas novas

Pequeno guia caso o projeto cresça.

### Novo token (ex.: `mod` para resto da divisão)

1. Adicionar `MOD : 'mod' ;` em `PascalitoLexer.g4`.
2. Adicionar `MOD` em `exprMul` no `PascalitoParser.g4`.
3. Tratar no `SemanticAnalyzer.foldArithmetic` (provavelmente já passa sem mudança se reutilizar `MAIS/MENOS/VEZES/DIV`).
4. Tratar no `CodeGenerator.foldAddMul` (`case "mod" -> "MOD"`).
5. Adicionar mnemônico `MOD` na `VirtualMachine` (similar a `DIV`).
6. Testes em `ParserTest` + `CodeGeneratorTest` + `VirtualMachineTest`.

### Novo tipo (ex.: `real`)

1. Adicionar `REAL : 'real' ;` no Lexer + nó `REAL` em `tip`.
2. Adicionar `REAL` em `Type.java`.
3. Tipar literais (atualmente `CTE` é só `INTEGER`; precisaria token `CTE_REAL`).
4. Atualizar `SemanticAnalyzer` para regras de promoção `INTEGER → REAL`.
5. Adicionar tipo `REAL` na `VirtualMachine` (`Double` em vez de `Integer`).

### Nova flag CLI

1. Adicionar `case` no `Main.main`.
2. Adicionar método `runX`.
3. Atualizar `usage()` e o README.

---

## 6. Limites conhecidos

- **Sem escopo aninhado**: declarar uma var dentro de um `begin/end` não cria escopo novo.
- **Sem procedures/functions**: a linguagem é puramente sequencial (loops + atribuições).
- **Sem strings em expressões**: `string` só vive em variáveis lidas via `read` ou literais `CADEIA` em `write`. Não há concatenação.
- **Inteiros são `int` Java (32 bits)** durante a execução, embora o léxico force `0..32767` no literal.
- **A VM não tem stack frames** — é uma máquina de registradores virtuais ilimitados, próxima de TAC (three-address code), não de uma máquina de pilha tipo JVM.
