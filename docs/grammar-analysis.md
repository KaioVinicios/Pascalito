# Análise da Gramática — Conflitos e Reescrita

Documento de apoio à **Fase 2** do projeto. Identifica os conflitos na gramática original
de [`requisites.md`](./requisites.md) e apresenta a versão reescrita usada em `grammar/PascalitoParser.g4`.

---

## 1. Gramática original

```
Prog       --> PROGRAM IDENTIFIER PVIG Decls CmdComp PONTO
Decls      --> ε | VAR ListDecl
ListDecl   --> DeclTip | DeclTip ListDecl
DeclTip    --> ListId DPONTOS Tip PVIG
ListId     --> IDENTIFIER | IDENTIFIER VIG ListId
Tip        --> INTEGER | BOOLEAN | STRING

CmdComp    --> BEGIN ListCmd END
ListCmd    --> Cmd | Cmd PVIG ListCmd
Cmd        --> CmdIf | CmdWhile | CmdRead | CmdWrite | CmdAtrib | CmdComp

CmdIf      --> IF Expr THEN Cmd
             | IF Expr THEN Cmd ELSE Cmd

CmdWhile   --> WHILE Expr DO Cmd
CmdRead    --> READ ( ListId )
CmdWrite   --> WRITE ( ListW )
ListW      --> ElemW | ElemW VIG ListW
ElemW      --> Expr | CADEIA

CmdAtrib   --> IDENTIFIER := Expr

Expr       --> Expr OPREL Expr | Expr OPAD Expr | Expr OPMULT Expr
Expr       --> IDENTIFIER | CTE | ABPAR EXPR FPAR | TRUE | FALSE | OPNEG Expr
```

---

## 2. Conflitos identificados

### 2.1 Ambiguidade do `if-then-else` (*dangling else*)

A regra
```
CmdIf --> IF Expr THEN Cmd
        | IF Expr THEN Cmd ELSE Cmd
```
permite duas derivações para `if A then if B then C1 else C2`:

- `if A then (if B then C1 else C2)` — `ELSE` liga ao IF interno (convenção C/Pascal)
- `if A then (if B then C1) else C2` — `ELSE` liga ao IF externo

**Solução:** ANTLR resolve por greedy match — o `ELSE` opcional é capturado pelo `IF` mais
interno automaticamente. Refatoramos para
```
cmdIf : IF expr THEN cmd (ELSE cmd)? ;
```

### 2.2 Ambiguidade de `Expr` (sem precedência nem associatividade)

A regra
```
Expr --> Expr OPREL Expr | Expr OPAD Expr | Expr OPMULT Expr
```
é triplamente ambígua:

- **Precedência:** `1 + 2 * 3` pode ser `(1+2)*3` ou `1+(2*3)`.
- **Associatividade:** `1 - 2 - 3` pode ser `(1-2)-3` ou `1-(2-3)`.
- **Mistura relacional/aritmético:** `a < b + c` pode ser `a < (b+c)` ou `(a<b)+c` (sem sentido para tipos).

Também falta a especificação de `OR`/`AND` — definidos como tokens `OPLOG` na descrição
léxica mas nunca usados na gramática original.

**Solução:** estratificar `expr` em camadas por precedência, da mais baixa à mais alta:

| Nível | Operadores | Associatividade |
|-------|------------|-----------------|
| 1 (mais baixo) | `OR` | esquerda |
| 2 | `AND` | esquerda |
| 3 | `< <= > >= == <>` | **não-associativa** (proíbe `a < b < c`) |
| 4 | `+ -` | esquerda |
| 5 | `* /` | esquerda |
| 6 (unário) | `~` (NEG) | prefixo |
| 7 (átomo) | `ID`, `CTE`, `TRUE`, `FALSE`, `( expr )` | — |

### 2.3 `OPNEG` aplicado a `Expr` (defeito apontado pelo enunciado)

```
Expr --> OPNEG Expr
```
permite construções como `~ (a + b)`. O enunciado pede que `OPNEG` se aplique apenas a
`IDENTIFIER | CTE | ABPAR Expr FPAR | TRUE | FALSE | OPNEG`.

**Solução:** colocar `~` na camada unária acima do átomo:
```
exprUnary : NEG exprUnary | atom ;
```
Assim `~~x` é permitido (NEG aninhado), mas `~ (a > b)` continua possível somente porque
`(a > b)` é um átomo via parênteses — o que está coerente com a lista permitida no spec.

### 2.4 Recursão à esquerda direta

ANTLR4 trata recursão à esquerda *direta*, então `Expr --> Expr OPAD Expr` não é
intrinsecamente ilegal. Porém, sem precedência, gera warnings de ambiguidade.
A estratificação acima elimina toda recursão à esquerda.

### 2.5 Omissão de `OPLOG` (OR/AND) na sintaxe

A spec léxica define `OR` e `AND`, mas a gramática original nunca os usa. A versão
reescrita os incorpora nas camadas 1 e 2 de `expr`.

### 2.6 Recursão à direita em `ListDecl`, `ListId`, `ListW`, `ListCmd`

Funciona em ANTLR, mas é mais idiomático usar fechos:
```
listId : ID (VIG ID)* ;
listW  : elemW (VIG elemW)* ;
...
```
Equivalente em poder, mais legível, gera melhores árvores de parse.

---

## 3. Gramática reescrita (sem conflitos)

A implementação canônica está em [`../grammar/PascalitoParser.g4`](../grammar/PascalitoParser.g4).
Forma BNF abaixo apenas para documentação:

```
prog       : PROGRAM ID PVIG decls cmdComp PONTO EOF ;

decls      : (VAR declTip+)? ;
declTip    : listId DPONTOS tip PVIG ;
listId     : ID (VIG ID)* ;
tip        : INTEGER | BOOLEAN | STRING ;

cmdComp    : BEGIN listCmd END ;
listCmd    : cmd (PVIG cmd)* ;
cmd        : cmdIf
           | cmdWhile
           | cmdRead
           | cmdWrite
           | cmdAtrib
           | cmdComp
           ;

cmdIf      : IF expr THEN cmd (ELSE cmd)? ;
cmdWhile   : WHILE expr DO cmd ;
cmdRead    : READ ABPAR listId FPAR ;
cmdWrite   : WRITE ABPAR listW FPAR ;
listW      : elemW (VIG elemW)* ;
elemW      : expr | CADEIA ;
cmdAtrib   : ID ATRIB expr ;

// Expressões — camadas de precedência (baixa → alta):
expr       : exprOr ;
exprOr     : exprAnd  (OR  exprAnd)* ;
exprAnd    : exprRel  (AND exprRel)* ;
exprRel    : exprAdd  (relOp exprAdd)? ;
exprAdd    : exprMul  ((MAIS | MENOS) exprMul)* ;
exprMul    : exprUnary ((VEZES | DIV) exprUnary)* ;
exprUnary  : NEG exprUnary
           | atom
           ;
atom       : ID
           | CTE
           | TRUE
           | FALSE
           | ABPAR expr FPAR
           ;
relOp      : MENOR | MENIG | MAIOR | MAIG | IGUAL | DIFER ;
```

### Garantias

| Conflito original | Como ficou resolvido |
|-------------------|---------------------|
| 2.1 dangling else  | greedy `(ELSE cmd)?` |
| 2.2 ambiguidade de `Expr` | 7 camadas com precedência fixa |
| 2.3 NEG sobre `Expr` | NEG só sobre `exprUnary`/`atom` |
| 2.4 recursão à esquerda | eliminada via fechos `*`/`?` |
| 2.5 OPLOG ausente | OR e AND adicionados nas camadas 1 e 2 |
| 2.6 recursão à direita em listas | substituída por `(SEP item)*` |

### Decisões adicionais

- **`;` é separador, não terminador** de comandos: `c1 ; c2 ; c3` (igual à gramática original).
- **Relacional não associativo**: `a < b < c` é erro sintático (faz sentido — `<` retorna `boolean` e não pode ser comparado de novo).
- **`prog` exige `EOF`**: garante que o parser consome o programa inteiro e não para depois do `.` final ignorando lixo.
- **Unário `+` e `-` na camada `exprUnary`**: o spec léxico declara que `CTE` pode ter sinal (positivo ou negativo), mas o lexer trata o sinal como token `OPAD` separado, e a gramática sintática original só dá unário ao `~`. Para que `-5` ou `+x` sejam reconhecíveis, `exprUnary` aceita também `MAIS`/`MENOS` como prefixo:
  ```
  exprUnary : (NEG | MAIS | MENOS) exprUnary | atom ;
  ```
  Não há conflito com `exprAdd` — este sempre consome um `exprMul` antes de ver o operador, então o `MENOS` de `1 - 2` é binário, e o de `-2` é unário.
- **`CADEIA` não pertence a `expr`**: segue a gramática original — strings literais só aparecem em `elemW` (lista do `WRITE`). Variáveis `string` portanto só podem ser populadas via `READ` e consumidas via `WRITE`. Atribuir `s := "foo"` é erro sintático intencional.
