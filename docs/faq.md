# Pascalito — Perguntas Frequentes

Especulação organizada do que cada audiência (aluno usando, professor avaliando,
quem for estender o código) provavelmente vai querer saber. Para detalhes
canônicos ver [`architecture.md`](./architecture.md), [`requisites.md`](./requisites.md),
[`grammar-analysis.md`](./grammar-analysis.md) e [`codegen-target.md`](./codegen-target.md).

---

## Uso básico

### 1. Como rodo um programa?

```sh
./pascalito --run examples/30_codegen_aritmetica.pas
```

O wrapper compila o jar sob demanda na primeira chamada. Sem flags, só roda
léxico + sintático + semântico (não executa).

### 2. Como vejo a árvore sintática?

```sh
./pascalito --parse --tree examples/10_sintatico_ok.pas
```

A árvore sai em formato Lisp-like (`(prog (decls ...) (cmdComp ...))`),
usando os nomes das regras do `PascalitoParser.g4`.

### 3. Como vejo o assembly gerado sem executar?

```sh
./pascalito --emit examples/30_codegen_aritmetica.pas
# Grava em out/30_codegen_aritmetica.asm
cat out/30_codegen_aritmetica.asm
```

### 4. Como entrego dados pelo `read`?

`read` consome **uma linha de stdin por variável**. Para múltiplas:

```sh
printf '10\n20\n' | ./pascalito --run prog.pas
```

Cada linha é convertida segundo o tipo declarado da variável (`integer` → `Integer.parseInt`, `boolean` → `Boolean.parseBoolean`, `string` → linha bruta).

### 5. Como interpreto os códigos de saída?

| Código | Significado     |
|--------|-----------------|
| `0`    | sucesso         |
| `64`   | uso incorreto   |
| `65`   | erro léxico     |
| `66`   | erro de I/O     |
| `67`   | erro sintático  |
| `68`   | erro semântico  |

Convenção BSD (`sysexits.h`). Útil em scripts: `./pascalito x.pas && echo OK || echo "falhou com $?"`.

---

## A linguagem

### 6. Por que constantes são limitadas a `0..32767`?

Vem direto da especificação ([`requisites.md`](./requisites.md)) — é o range
de um inteiro de 16 bits sem sinal. Internamente, a VM usa `int` Java (32 bits),
então **não há overflow durante a execução**, só na leitura do literal.

### 7. Por que identificadores são truncados a 16 caracteres?

Também da spec. A regra é aplicada em três pontos (todos via
`SymbolTable.normalize`):
- léxico (`TokenPrinter` mostra a versão truncada),
- semântico (chaves do `SymbolTable`),
- codegen (`VAR`, `LOAD`, `STORE` usam nome normalizado).

**Efeito colateral importante**: `identificadorAAA` e `identificadorBBB`
truncam para `identificadorAAA` (16 chars iguais) e **colidem** — segundo
declarado vira "Identificador já declarado". É comportamento esperado, testado em
`SemanticAnalyzerTest.twoLongIdentifiersWithSamePrefixCollide`.

### 8. Por que igualdade é `==` em vez de `=`?

A spec define `IGUAL : '=='` (`PascalitoLexer.g4:34`). `=` sozinho não é token
válido — usar `=` gera erro léxico. Diferente do Pascal clássico, é coerente
com a notação de C/Java que o autor da spec privilegiou.

### 9. Por que negação lógica é `~` em vez de `not`?

Igual ao item anterior: a spec usa `NEG : '~'`. Não há palavra-chave `not`.
Para inverter: `b := ~b`.

### 10. Posso concatenar strings? Comparar com `<`?

**Não.** `string` é um tipo de saída: aparece em `read(s)` e em
literais `CADEIA` dentro de `write("...")`. Não há `+` para strings, e
operadores relacionais rejeitam string (testado em `SemanticAnalyzerTest`).
Apenas `==`/`<>` funcionam para igualdade, mas só entre dois operandos do mesmo tipo.

### 11. Posso atribuir uma string com `s := "oi"`?

**Não.** A spec define que strings só são introduzidas via `read`. O
`SemanticAnalyzer` bloqueia atribuição direta:

> Variáveis 'string' não podem ser atribuídas; use 'read(s)'

### 12. `n := -5` funciona? E `n := - 5`?

Sim, ambos. O `-` é tratado como **unário** em `exprUnary`, que aceita
opcionalmente whitespace entre o operador e o operando — o lexer come o espaço.

### 13. Por que `begin end.` (corpo vazio) é erro?

Porque a gramática define `listCmd : cmd (PVIG cmd)*` — exige **pelo menos um
comando**. Mude para `begin x := 0 end.` ou similar.

### 14. Posso ter `var` aninhado dentro de `begin`?

Não. Declarações só vivem no bloco `var` único do programa, antes do `begin`
principal. Escopo é único — a linguagem não tem procedures, funções ou blocos
com declarações.

### 15. Por que o `write` parece imprimir cada argumento em uma linha?

Porque cada `WRITE` na VM faz `println`, e cada elemento da lista
`write(a, b, c)` vira um `WRITE` separado. Comportamento intencional para o
escopo da disciplina; se você precisar do mesmo separador, faça
`write("a=", n)` mesmo — sairão `a=` e o número em linhas separadas.

---

## Erros mais comuns

### 16. "linha X, coluna Y: extraneous input ..." — o que é?

Erro sintático do ANTLR: encontrou um token que não cabe na regra. Causas
clássicas:
- esqueceu `;` separando comandos,
- usou `=` em vez de `==`,
- escreveu `not` em vez de `~`,
- esqueceu o `.` no fim do programa.

### 17. "token recognition error at: '...'"

Erro **léxico** — caractere ou sequência não casa com nenhum token. Exemplo
clássico: `=` solto (igualdade é `==`). Aparece com `EXIT_LEX_ERROR` (65).

### 18. "Variável 'x' não declarada" mas eu declarei `x`!

Suspeitos usuais:
1. **Truncamento**: nome tem >16 chars e o declarado/usado divergem após corte.
2. **Case**: a linguagem é case-insensitive no léxico, então `X` e `x` são o mesmo. Não é isso. Verifique o item 1.
3. **Escopo**: você usou `x` fora do bloco `begin/end` principal? Bom, não dá pra usar fora dele de qualquer forma.

### 19. "Identificador 'x' já declarado na linha N" mas declarei só uma vez!

Quase sempre o item 7: colisão por truncamento. Verifique se há outro
identificador com os mesmos 16 primeiros caracteres.

### 20. Por que o erro semântico mostra a coluna 1 mesmo quando o erro está no meio da linha?

Não — a coluna vem do token. Se você está vendo coluna inesperada, provavelmente
o erro real é em outro lugar (ex.: condição de `if`/`while` reporta na **palavra-chave**, não no operador da expressão problemática).

---

## Arquitetura e decisões

### 21. Por que assembly didático e não JVM bytecode, C ou interpretador AST?

Ver [`codegen-target.md`](./codegen-target.md). Em uma linha: didática (três
endereços é o que os livros mostram) + auto-suficiência (não precisa de
toolchain externa) + testabilidade (a VM aceita streams injetáveis).

### 22. Por que ANTLR? Não seria educativo escrever o parser à mão?

Compromisso: escrever o lexer/parser à mão consumiria a maior parte do tempo
disponível e enterraria a parte que a disciplina realmente quer
avaliar — **a gramática** (análise de conflitos e reescrita, ver
[`grammar-analysis.md`](./grammar-analysis.md)), **a semântica** e o **codegen**.
ANTLR converte o `.g4` em parser LL(*) determinístico, então 100% da disciplina
fica no que importa.

### 23. Por que Java 21 especificamente?

Records, switch expressions com pattern matching, text blocks `"""..."""` e
`Files.writeString` deixam o código bem mais limpo do que em Java 17. O
projeto **não vai compilar em Java 17** sem mudanças (uso de records com
deconstrução em `record ParseOutcome(...) {}`).

### 24. Por que escopo único?

A especificação não pede procedures/funções. Adicionar escopo aninhado dobra a
complexidade da `SymbolTable` (precisa virar pilha) e do `SemanticAnalyzer`
(precisa empilhar/desempilhar nos blocos). Fora do escopo do trabalho.

### 25. Por que a VM aceita `Reader`/`PrintWriter` em vez de usar `System.in/out` direto?

Para **testes determinísticos**. `VirtualMachineTest` injeta `StringReader` e
`StringWriter` para comparar stdout contra strings literais — sem isso, os 22
testes end-to-end (incluindo o `2+2 → 4`) seriam impossíveis.

### 26. Por que o tipo `ERROR` existe no enum `Type`?

Para **propagação sem cascata**. Quando uma sub-expressão tem erro, ela
retorna `ERROR` e os operadores acima fazem short-circuit (não geram erro
novo). Sem isso, `n := q + 1` (com `q` não declarada) reportaria 2 ou 3 erros
em vez de 1.

### 27. Por que separar `CodeGeneratorTest` e `VirtualMachineTest`?

Porque pegam falhas diferentes:
- `CodeGeneratorTest` verifica que o **assembly certo foi emitido** (estrutura) — se eu trocar `ADD` por `SUB`, ele falha.
- `VirtualMachineTest` verifica que **o programa produz a saída esperada** (semântica) — se a VM somar mal, ele falha.

Um bug pode passar num e não no outro (ex.: emiti `ADD` certo mas a VM executa `ADD` errado → só VM test falha).

---

## Extensão

### 28. Como adiciono uma nova palavra-chave / token?

1. Definir no `PascalitoLexer.g4` (`MOD : 'mod' ;`).
2. Usar onde fizer sentido no `PascalitoParser.g4`.
3. Tratar no `SemanticAnalyzer` (se for operador, ajustar `foldArithmetic` etc.).
4. Tratar no `CodeGenerator` (mapear pra mnemônico).
5. Tratar na `VirtualMachine` (implementar o mnemônico).
6. Adicionar testes em todas as camadas afetadas.

Receita curta em [`architecture.md`](./architecture.md) §5.

### 29. Como adiciono um novo tipo (ex.: `real`)?

Mais trabalho. Resumido:
1. Token `REAL : 'real' ;` no lexer + literal de ponto flutuante.
2. `Type.REAL` no enum.
3. Regras de coerção `INTEGER → REAL` no semântico.
4. Tipo `Double` na VM.

Não é trivial — vai mexer em `foldArithmetic`, `checkLogical`, `relOp` (precisa permitir comparação cruzada `int < real`?). Vale planejar antes.

### 30. Como adiciono uma nova flag CLI?

1. `case` no `Main.main` setando um boolean.
2. Novo método `runX(...)`.
3. Atualizar `usage()`.
4. Atualizar README + `docs/architecture.md` §2.2.

---

## Build, testes, distribuição

### 31. Como rodo só um teste?

```sh
mvn test -Dtest='VirtualMachineTest#twoPlusTwoEqualsFour'
```

Pra uma suíte inteira:

```sh
mvn test -Dtest='VirtualMachineTest'
```

### 32. Funciona no Windows?

O **jar funciona** (`java -jar target/pascalito.jar prog.pas`).
O wrapper `./pascalito` é bash — no Windows, use Git Bash/WSL ou rode o jar
diretamente. Não há `pascalito.cmd` equivalente.

### 33. Posso rodar sem Maven instalado?

Depois de gerado, sim — `target/pascalito.jar` é auto-suficiente (inclui o
runtime do ANTLR via shade). Distribua só o jar e use `java -jar`. Para
**construir** o jar, Maven é obrigatório (precisa rodar o `antlr4-maven-plugin`).

### 34. Onde fica o jar? Por que `pascalito.jar` e não `pascalito-1.0-SNAPSHOT.jar`?

`target/pascalito.jar`. O nome curto vem do `<finalName>pascalito</finalName>`
no `pom.xml` (config do `maven-shade-plugin`). Simplifica o wrapper.

### 35. Por que `mvn verify` em vez de `mvn test`?

`verify` roda o ciclo completo (incluindo `package`, que gera o fat jar).
`test` para após os testes. Para sanity-check de PR, `verify` é mais completo.

---

## Sobre a VM

### 36. E se eu escrever um loop infinito?

A VM **não tem timeout** — ela vai rodar até bater Ctrl-C, OOM (improvável
sem alocação de strings grandes), ou a JVM ser morta. Para programas
suspeitos, use `timeout 5s ./pascalito --run prog.pas`.

### 37. Quão grande pode ser um programa?

Não há limite explícito. Na prática:
- A VM usa `Map<String, Object>` para vars e temps — sem teto, mas cada temp ocupa memória até o programa terminar.
- O `CodeGenerator` aloca **temps infinitos** (`t0, t1, …`) — não há reciclagem. Programas grandes podem gerar dezenas de milhares de temps. Não causa problema funcional, só consome RAM linear no tamanho do programa.

### 38. Posso ler dois inteiros na mesma linha de stdin?

**Não.** `read(a, b)` consome **duas linhas** de stdin. É consequência de a VM
usar `BufferedReader.readLine()` por leitura. Se você precisar, mude a
`VirtualMachine.readInto` para tokenizar.

### 39. O que acontece se eu tentar `read(s)` mas o stdin acabou?

`VmException` com mensagem "EOF inesperado no READ". Exit code 66 (`EXIT_IO_ERROR`).

---

## Sobre o projeto

### 40. Posso reutilizar isso em outro lugar?

Não há `LICENSE` definida — por padrão isso significa "todos os direitos reservados".
Se quiser tornar reutilizável, adicione uma licença (MIT/Apache-2.0 são opções
comuns para projetos acadêmicos).

### 41. Existe roadmap pra mais funcionalidades?

Ver [`todo.md`](./todo.md). Fases 0–5 estão **concluídas**. Extensões plausíveis
mas não planejadas: escopo aninhado, procedures, tipo `real`, arrays, otimização
do codegen (reciclar temps, constant folding).

### 42. Como contribuo?

Padrões adotados no projeto:
- Conventional Commits em inglês (`feat(...)`, `docs(...)`, `build(...)`).
- Mensagens da CLI e comentários em código: português (combina com a spec).
- Mantenha `mvn verify` verde antes de commitar.
- Não inclua `docs/Especificação do projeto.pdf` em diff de código.

### 43. Onde reportar bugs?

Como é projeto de disciplina, não há issue tracker público. Se for um pull
request interno, abra uma issue com:
- programa `.pas` que reproduz,
- saída esperada vs. observada,
- versão do JDK (`java -version`).
