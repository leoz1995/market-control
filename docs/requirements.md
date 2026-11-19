# MarketControl — requisitos e estado funcional

> Revisado em 18/11/2026 após inspeção do repositório, bootstrap descartável, migrations 07/08 aplicadas no MariaDB local e suíte executada com a conta restrita da aplicação. Requisitos planejados não são funcionalidades implementadas.

## 1. Objetivo e nível atual

MarketControl é um projeto educacional de controle de estoque, feito em Java 25, JDBC e MariaDB. O núcleo cobre categorias, produtos, fornecedores, lotes, entradas, vendas e perdas. A arquitetura separa domínio, serviços, repositórios, infraestrutura JDBC e transações, sem Spring ou ORM.

**Nível do projeto:** intermediário em maturidade técnica de um backend educacional/pré-MVP. Já há persistência relacional, atomicidade observável e testes concorrentes reais. Ainda não é produto operacional: falta API, segurança, consulta de histórico, configuração por ambiente e CI.

## 2. Regras de domínio

### Produto, categoria e unidade

- Produto pertence a uma categoria e tem nome, código de barras, descrição opcional e unidade.
- Código de barras é normalizado no fluxo de serviço/repositório e tem unicidade no banco.
- Unidades persistidas: `UNIT`, `KILOGRAM`, `GRAM`, `LITER`, `MILLILITER`; conversões entre unidades não estão definidas.
- Categoria tem nome, descrição e estado ativo. O schema rejeita descrição vazia.

### Fornecedor

- Fornecedor tem UUID, documento empresarial de 14 dígitos, e-mail, dados de contato e estado ativo/inativo.
- Serviço e repositório JDBC suportam criação, busca, atualização e inativação.
- ENTRY exige fornecedor ativo; fornecedor inativo segue disponível para reconstruir histórico.
- EXIT e LOSS não exigem fornecedor. FK impede excluir fornecedor ainda referenciado por movimentos.

### Lotes e movimentos

- Lote contém produto, datas e saldo atual (`availableQuantity`). Não há `totalReceivedQuantity`.
- A primeira aquisição cria lote, movimento `ENTRY` e item na mesma transação.
- Cada nova aquisição do produto cria outro lote. Não há reposição no lote existente.
- `EXIT` vende estoque; `LOSS` registra perda. Venda não consome lote vencido; perda pode consumir lote vencido com saldo.
- `ADJUSTMENT` existe no enum/schema, mas não tem caso de uso.
- Não há distribuição de uma venda entre lotes nem política FEFO.

### Custo histórico

- `StockMovementItem.unitCost` é custo unitário de aquisição; não significa preço de venda.
- EXIT e LOSS recuperam o custo da ENTRY original do lote.
- Migration 07 adiciona `initial_entry_lot_id`, `UNIQUE`, FK, CHECK e triggers. Migration 08 cria a procedure transacional, revoga INSERT direto de `marketcontrol_app` em `lots` e restringe a exceção de limpeza da ENTRY à sessão root.
- O fluxo normal marca a origem explicitamente e entradas posteriores ficam sem marcador. Triggers protegem a origem marcada contra update, mudança do tipo de movimento e exclusão normal.
- MariaDB não pode exigir por FK imediata que todo lote tenha um filho no commit; a garantia é fechada para a conta `marketcontrol_app@localhost` pelos grants da migration 08. Essa conta não pode inserir `lots` diretamente e recebe EXECUTE na procedure `SQL SECURITY DEFINER`, que grava lote + ENTRY/item atomicamente. Root/DBA permanece privilegiado.

## 3. Matriz de funcionalidades

| ID | Requisito | Estado observado |
|---|---|---|
| RF01–02 | Cadastro/consulta/edição de categorias | Domínio, serviço e JDBC; CLI parcial. |
| RF03–05 | Cadastro/consulta/edição de produtos | Domínio, serviço e JDBC; CLI parcial; barcode único. |
| RF06 | Ativar/desativar produto | Não implementado; produto não tem estado ativo. |
| RF07–09 | Cadastro, consulta, atualização e inativação de fornecedor | Serviço e JDBC implementados; sem menu CLI. Integrações cobrem persistência, histórico e fornecedor inativo. |
| RF10–11 | Entrada inicial e nova aquisição | Implementado; aquisição cria lote próprio e grava ENTRY/item atomicamente. |
| RF12 | Consultar lotes e saldo | Serviço/repositório implementados; total considera validade hoje ou futura. |
| RF13–14 | Venda e bloqueio por validade/saldo | Venda por lote em serviço; update condicional e transacional; concorrência exercitada. Sem CLI ou venda multi-lote. |
| RF15–16 | Perda e perda de lote vencido | Serviço por lote, causa, custo histórico e transação; perda vencida permitida com saldo. Sem CLI. |
| RF17 | Recuperar custo histórico | Implementado pelo marcador ENTRY; unicidade validada por constraint e origem criada atomicamente pela procedure. A conta da aplicação não pode inserir lotes diretamente. |
| RF18 | Consultar histórico de movimentos | Não implementado; movimentos são gravados, sem consultas/paginação/serviço de leitura. |
| RF19–20 | Alertas de validade e estoque mínimo | Não implementados; não há regra de janela ou nível mínimo. |
| RF21 | Ajuste de estoque | Não implementado; existe apenas o tipo `ADJUSTMENT`. |
| RF22 | Saída entre lotes/FEFO | Não implementado. |
| RF23 | API HTTP | Não implementada. |
| RF24 | Frontend | Não implementado. |
| RF25 | Autenticação/autorização | Não implementadas. |
| RF26 | Spring Boot | Não adotado; aplicação atual é Java puro/JDBC. |

## 4. Transações e concorrência

`JdbcTransactionManager` abre conexão, desativa auto-commit, comita ao final e executa rollback em falha. Entrada grava lote + ENTRY/item na mesma conexão. Venda/perda debitam saldo e gravam movimento/item na mesma transação.

O saldo é atualizado por uma única instrução InnoDB, sem leitura e gravação separadas:

```sql
UPDATE lots
SET available_quantity = available_quantity - ?
WHERE id = ? AND available_quantity >= ?
```

Venda também exige validade não vencida. A atualização bloqueia a linha; transações concorrentes esperam e a condição de saldo é reavaliada. Não foi necessário lock Java nem `SELECT ... FOR UPDATE`. Se não atualizar uma linha, o serviço falha antes de salvar movimento. Se o insert do movimento falhar depois do débito, a mesma transação faz rollback.

Testes MariaDB usam `CountDownLatch`, duas threads e duas conexões identificadas por `CONNECTION_ID()`. Quatro cenários são repetidos dez vezes: duas vendas de 7 sobre saldo 10; venda 7 contra perda 7; venda 4 com perda 5; e rollback forçado de uma operação simultânea à operação bem-sucedida. Verificam saldo, ausência de negativos, quantidade/tipo de movimentos e itens e custo histórico. Resultado observado: 40 execuções, todas aprovadas.

Essa evidência demonstra os cenários testados no MariaDB 10.11 local. Não prova comportamento sob falhas de rede durante commit, alta contenção de produção, retries/deadlocks, múltiplos nós ou outro nível de isolamento/configuração.

## 5. Arquitetura observada

```text
Program (CLI/composição manual)
  → services
  → domain models + repository contracts
  → JDBC repositories + JdbcTransactionManager
  → MariaDB
```

Os contratos transacionais recebem `java.sql.Connection`, então o núcleo conhece JDBC em parte. `ProductDTO` é para edição interna, não DTO HTTP. `DatabaseConnection` carrega `database.properties` do classpath e usa `DriverManager`; não há profiles nem pool. `src/main/java/marketcontrol/Test.java` é um exercício de streams compilado junto com produção.

## 6. Banco e setup

Scripts de criação e dependências:

1. `database/01_categories.sql`
2. `database/02_products.sql`
3. `database/03_lots.sql`
4. `database/04_suppliers.sql`
5. `database/05_stock_movements.sql`
6. `database/06_stock_movement_items.sql`

Migrations para base existente: `database/07_lot_initial_entry_integrity.sql` e `database/08_atomic_lot_creation_and_app_grants.sql`. A 07 faz preflight de histórico e interrompe se lote não tiver exatamente uma ENTRY candidata ou se houver mais de uma, sem corrigir/apagar dados silenciosamente. A 08 verifica as origens, cria a procedure atômica e substitui os grants de `marketcontrol_app@localhost`; esse usuário precisa existir previamente.

A sequência 01–06 foi executada em `marketcontrol_bootstrap_test`, schema descartável inicialmente sem tabelas. As seis tabelas, FKs, índices e constraints foram inspecionados; depois o schema temporário foi removido. Migrations 07/08 foram aplicadas com root ao MariaDB local `marketcontrol`; procedure, grants e triggers foram inspecionados. Testes com `marketcontrol_app` confirmaram INSERT direto negado, criação pela procedure e rejeição da tentativa de apagar origem mesmo definindo a variável de limpeza. A suíte remove fixtures e o banco termina sem lotes, movimentos ou itens.

Scripts não criam database ou usuário. A migration 08 revoga INSERT no nível do schema da conta `marketcontrol_app`, concede INSERT apenas nas demais tabelas necessárias e EXECUTE na procedure de criação. Configuração local também fornece credenciais administrativas separadas para limpar fixtures protegidas; não as versione.

## 7. Testes e evidências desta revisão

Comando final executado: `mvn clean test -Dstyle.color=never -Ddb.admin.user=root -Ddb.admin.password=root`.

| Grupo | Testes | Resultado |
|---|---:|---|
| Suíte total | 164 | 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS. |
| Unitários/domínio/serviços | 65 | Aprovados. |
| Integração JDBC/MariaDB tradicional | 59 | Aprovados. |
| Integração concorrente MariaDB | 40 | Aprovados; quatro cenários × dez repetições. |
| Bootstrap SQL descartável | 6 scripts | Sucesso; schema, constraints, FKs e índices verificados e schema removido. |

O primeiro commit que registra a auditoria anterior está no histórico como `ddccbda` (`test: document current inventory audit and integration coverage`). A mensagem é diferente da sugestão original, mas o commit contém README, requirements e ajustes de fixtures/testes. Trabalho posterior das tarefas de fornecedor e ENTRY aparece no working tree e ainda não está commitado.

## 8. Roadmap e prontidão para HTTP

Antes de integrar HTTP ao MarketControl:

1. Preservar os grants mínimos e executar a aplicação como `marketcontrol_app`, nunca como root.
2. Separar configuração de desenvolvimento/teste e validar o banco-alvo antes de operações que gravam.
3. Estabilizar limites de serviço e mapeamento de erros para adaptadores, sem expor JDBC/SQL ao contrato HTTP.
4. Iniciar HTTP como adaptador didático de escopo limitado e testar os primeiros endpoints; autenticação/autorização são obrigatórias antes de uso externo.

**Decisão atual:** Tarefa 4 está fechada para o papel `marketcontrol_app` por procedure/grants e Tarefa 5 está demonstrada em 40 disputas concorrentes repetidas. O núcleo está pronto para iniciar HTTP como próximo passo educacional, não para uso em produção.

## 9. Maturidade do projeto

**Classificação:** projeto educacional de nível intermediário, com núcleo de domínio/JDBC funcional e transações testadas, incluindo disputa concorrente real. Está em estágio pré-MVP de backend, não em nível de produção. O número de testes e a classificação não equivalem a cobertura, confiabilidade operacional ou certificação de segurança.

Faltam, entre outros: consulta de histórico; interface além do CLI parcial; API e segurança; configuração por ambiente; CI e cobertura quantitativa; logs, backup, monitoramento e deploy.
