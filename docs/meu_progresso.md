# Progresso e plano de aprendizagem — MarketControl

**Responsável pelo projeto:** Leonardo Alves
**Última revisão do código e dos testes:** 15 de novembro de 2026
**Princípio de colaboração:** a IA atualiza testes e documentação. Leonardo Alves é quem desenvolve, decide e altera o código de produção.

Este documento descreve o que foi observado nos arquivos do repositório e na última execução da suíte. Classes existentes não são tratadas como prova de um fluxo concluído: quando uma regra importante não passa por teste ou apresenta inconsistência, isso fica registrado como trabalho pendente.

## 1. Estado atual em poucas linhas

O MarketControl deixou de ser apenas um plano de persistência futura: a aplicação atualmente usa repositórios JDBC e MariaDB. Há modelos e serviços para categorias, produtos, lotes e movimentações. A aplicação principal ainda é um terminal simples; ainda não existe API HTTP.

O fluxo de criar lote registra a quantidade inicial no lote e, na mesma transação, registra o movimento `ENTRY` e seu item. O `updt.diff` separa esse registro inicial das entradas adicionais: somente estas últimas atualizam o saldo disponível. O teste JDBC que antes reproduzia saldo duplicado foi atualizado e passou, confirmando saldo 10 para quantidade inicial 10 e lote reconstituível.

## 2. O que existe no código

### Domínio

- `Category`: criação, edição, normalização básica de nome/descrição e estado ativo.
- `Product`: nome, código de barras, descrição, categoria e unidade; normalização e validações de criação/edição.
- `Lot`: identidade, produto, datas, quantidades inicial/disponível, reconstituição e consulta de validade. O lote é agora um retrato imutável; não executa venda ou perda diretamente.
- `StockMovement` e `StockMovementItem`: cabeçalho com tipo, data/hora, descrição e itens; validações estruturais.
- `MovementType`: entrada, saída, perda e ajuste. O enum inclui ajuste, mas não há fluxo de serviço de ajuste identificado.
- `UnitOfMeasure`: unidade, quilograma, grama, litro e mililitro.

### Serviços e terminal

- `CategoryService`: cadastro, busca, edição e listagem de categorias.
- `ProductService`: cadastro, edição, listagem e busca por código de barras.
- `LotService`: cadastro/entrada transacional, busca por ID/produto e totalização de estoque.
- `StockMovementService`: entrada, venda e perda. Venda e perda abrem transação via `TransactionManager`; entrada recebe a conexão do chamador.
- `Program`: interface de terminal para cadastro/consulta/edição de produtos, cadastro de categoria e cadastro/consulta de lotes. Não oferece ainda menu para venda, perda, ajuste ou histórico.

### Persistência e banco

- `DatabaseConnection` carrega as credenciais locais de `database.properties`.
- Repositórios JDBC para categoria, produto, lote, movimentação e item.
- `JdbcTransactionManager` abre conexão, desativa auto-commit, executa trabalho, commita e tenta rollback em falha.
- Os scripts `database/01_categories.sql` a `database/05_stock_movement_items.sql` definem as tabelas e relações.
- `LotRepository` expõe operações SQL condicionais para entrada, venda e perda; a quantidade de linhas afetadas informa se a operação ocorreu.

## 3. Execução de testes registrada

Com MariaDB local acessível, `mvn test` compilou e executou **77 testes em 15 de novembro de 2026**:

| Classe | Total | Resultado registrado |
|---|---:|---|
| `DataBaseConnectionTest` | 1 | 1 aprovado |
| `LotTest` | 22 | 22 aprovados |
| `ProductTest` | 19 | 19 aprovados |
| `StockMovementTest` | 9 | 9 aprovados |
| `StockMovementItemTest` | 5 | 5 aprovados |
| `CategoryServiceTest` | 2 | 2 aprovados |
| `ProductServiceTest` | 6 | 6 aprovados |
| `StockMovementServiceTest` | 4 | 4 aprovados; entradas adicionais usam o novo método |
| `LotServiceAtomicityTest` | 4 | 4 aprovados; usa fakes, não JDBC real |
| `LotServiceTest` | 5 | 5 aprovados; inclui integração JDBC da criação do lote |
| **Total** | **77** | **77 aprovados, 0 falhas, 0 erros de execução, 0 ignorados** |

`LotServiceTest.lotCreationPersistsOnlyInitialQuantityAndEntryMovement` foi aprovado. O caso cria um lote com quantidade inicial 10 e verifica no MariaDB: saldo disponível 10, um movimento `ENTRY`, um item desse movimento com quantidade 10 e reconstituição do lote por `findById`. Isso cobre o sintoma de duplicação descrito anteriormente.

Os testes de venda e falha de repositório usam JDBC real no `LotServiceTest`: a falha é injetada no repositório de movimentação ou no método de atualização de lote, enquanto o `JdbcTransactionManager` e a conexão MariaDB são reais. Os testes de `LotServiceAtomicityTest` e `StockMovementServiceTest` usam repositórios/transação/conexão simulados; provam delegação e identidade de conexão no serviço, mas não rollback real do MariaDB.

Essa execução é uma fotografia do estado observado; qualquer mudança no banco, código ou ambiente exige nova execução. Não foi configurada ferramenta de cobertura, então não existe percentual de cobertura a reportar.

## 4. Falhas e riscos conhecidos a discutir

### Saldo inicial duplicado na criação do lote — corrigido no diff em avaliação

1. `Lot.create` inicializa `availableQuantity` com `initialQuantity`.
2. `LotService.save` persiste o lote e chama `registerInitialEntry(conn, lot)` na mesma transação.
3. `registerInitialEntry` grava apenas o movimento e o item `ENTRY`; não soma novamente o saldo.
4. `registerAdditionalEntry` mantém o caminho separado para entradas posteriores e usa `increaseAvailableQuantityForAdditionalEntry`.
5. A integração JDBC confirmou que o lote permanece consistente e pode ser reconstituído.

Evidência atual: `mvn -Dtest=LotServiceTest test` — 5 testes aprovados — e `mvn test` — 77 aprovados, sem falhas/erros/ignorados, em 15/11/2026. O diff de produção foi fornecido pelo responsável; nesta colaboração foram alterados somente testes e documentação. A conclusão cobre o caminho de criação inicial testado; entrada adicional ainda não possui integração JDBC dedicada.

### Testes e atomicidade

- O teste de rollback de venda verifica com leitura SQL posterior que o saldo persistido volta ao valor imediatamente anterior e não fica saída/itens de saída parciais.
- O teste de falha no update confirma que o movimento de saída não é persistido.
- Não há teste determinístico de falha durante `commit()` ou durante `rollback()`, nem de exceção suprimida do rollback.
- Não há teste de concorrência para duas vendas simultâneas do mesmo lote.
- O rollback da entrada inicial quando a gravação da movimentação falha ainda pode ser coberto melhor em integração.

### Regras abertas

- A perda aceita uma causa textual, mas a política que define quais lotes podem sofrer perda deve ser decidida e testada (por exemplo, somente lote vencido ou também avaria).
- `ADJUSTMENT` existe no enum, mas falta serviço, regras, autorização e auditoria da operação.
- A venda opera sobre um lote por chamada. A seleção/distribuição entre lotes e política FEFO ainda não estão implementadas.

## 5. Etapas do projeto e percentuais estimados

Percentuais são estimativas de avanço contra critérios de conclusão, definidas por Leonardo Alves e revisadas com base no estado do repositório. Não são métricas automáticas, cobertura de testes nem promessa de prazo. Etapas futuras ficam em 0% enquanto não houver implementação observável.

| Etapa | Avanço | Critério usado e o que falta |
|---|---:|---|
| 1. Domínio e serviços em Java puro | **65%** | Há categorias, produtos, lotes e movimentos, com serviços. Falta fechar regras, corrigir o saldo de entrada, implementar seleção multi-lote/FEFO, ajustes, fornecedores e decisões de validade. |
| 2. Persistência MariaDB/JDBC | **70%** | Esquema, repositórios JDBC e gerenciador de transações existem. Ainda é necessário garantir consistência ponta a ponta e cobrir erro SQL/commit/rollback, entrada adicional e rollback da entrada inicial. |
| 3. Terminal operacional | **45%** | Cadastro e consultas essenciais estão no menu. Ainda não há fluxos de venda, perda, ajuste, histórico ou operação completa para o mercado. |
| 4. Qualidade e testes | **65%** | Há 77 testes e integração real; a execução registrada está verde, mas permanecem lacunas de concorrência e de falhas de commit/rollback. |
| 5. API HTTP em Java puro | **0%** | Não há servidor HTTP, rotas, DTOs de transporte, validação da fronteira ou testes HTTP. É a próxima grande etapa após consolidar regras e JDBC. |
| 6. Front-end Angular com IA | **0%** | Ainda não existe aplicação Angular. Planejado depois de uma API estável; a integração e as decisões de produto continuam sob responsabilidade de Leonardo. |
| 7. Spring Boot v2 | **0%** | É uma migração futura para estudo comparativo, depois da v1 Java puro + HTTP e da experiência com Angular. Não antecipar framework na etapa atual. |

Os números não são somados nem convertidos em uma média geral: HTTP, Angular e Spring Boot são etapas separadas ainda não iniciadas.

## 6. Critérios para considerar cada etapa concluída

### Java puro, domínio, serviços, terminal e JDBC

- Regras acordadas estão no domínio/casos de uso apropriados e têm testes observáveis.
- Criação de lote e entradas não duplicam saldo; saídas/perdas mantêm saldo e movimento consistentes.
- Operações multi-registro são transacionais e têm evidência de commit e rollback com MariaDB.
- O terminal permite percorrer os casos de uso acordados e apresenta erros recuperáveis.
- Dados persistem após reiniciar a aplicação; constraints e testes protegem relações e regras.
- Leonardo consegue explicar responsabilidades, fluxos, SQL, conexão, transação, exceções e decisões de teste.

### HTTP em Java puro

- Casos de uso são acessíveis por rotas e contratos HTTP documentados, sem Spring.
- Status, validação, erros e serialização estão definidos e testados na fronteira.
- Regras não ficam acopladas ao servidor HTTP; MariaDB e transações continuam verificáveis.

### Angular e Spring Boot v2

- O front-end Angular consome contratos HTTP estáveis e tem fluxos de uso testados; apoio/geração por IA é uma direção planejada, não implementação atual.
- A migração Spring Boot ocorre apenas na v2, com testes de equivalência de comportamento para comparar o Java puro e as abstrações do framework.

## 7. Próximos passos de aprendizagem

1. Adicionar teste JDBC de entrada adicional e rollback da entrada inicial quando a gravação do movimento falha.
2. Testar perdas, erros SQL, commit/rollback, isolamento e concorrência com estratégia determinística.
3. Completar fluxos de terminal e histórico conforme os requisitos aceitos.
4. Só então definir e implementar a API HTTP em Java puro.
5. Integrar Angular depois que contratos e fluxos HTTP estiverem estáveis.
6. Planejar a migração Spring Boot v2 como estudo separado, preservando testes de comportamento.

Detalhamento funcional e decisões em aberto estão em [requirements.md](requirements.md); a visão geral está no [README](../README.md).
