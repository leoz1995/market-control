# Progresso e plano de aprendizagem — MarketControl

**Responsável pelo projeto:** Leonardo Alves
**Última revisão do código e dos testes:** 14 de novembro de 2026
**Princípio de colaboração:** a IA atualiza testes e documentação. Leonardo Alves é quem desenvolve, decide e altera o código de produção.

Este documento descreve o que foi observado nos arquivos do repositório e na última execução da suíte. Classes existentes não são tratadas como prova de um fluxo concluído: quando uma regra importante não passa por teste ou apresenta inconsistência, isso fica registrado como trabalho pendente.

## 1. Estado atual em poucas linhas

O MarketControl deixou de ser apenas um plano de persistência futura: a aplicação atualmente usa repositórios JDBC e MariaDB. Há modelos e serviços para categorias, produtos, lotes e movimentações. A aplicação principal ainda é um terminal simples; ainda não existe API HTTP.

O fluxo de criar lote tenta registrar uma entrada inicial na mesma transação. Um teste JDBC recém-atualizado encontrou uma falha: a quantidade inicial já é gravada em `available_quantity` quando o lote é salvo, e em seguida `StockMovementService.registerEntry` soma a mesma quantidade novamente. O saldo persistido fica acima de `initial_quantity`, e a reconstituição do lote rejeita o registro. A falha permanece aberta para Leonardo analisar e corrigir.

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

Com MariaDB de testes acessível, `mvn test` compilou e executou **77 testes**:

| Classe | Total | Resultado registrado |
|---|---:|---|
| `DataBaseConnectionTest` | 1 | 1 aprovado |
| `LotTest` | 22 | 22 aprovados |
| `ProductTest` | 19 | 19 aprovados |
| `StockMovementTest` | 9 | 9 aprovados |
| `StockMovementItemTest` | 5 | 5 aprovados |
| `CategoryServiceTest` | 2 | 2 aprovados |
| `ProductServiceTest` | 6 | 6 aprovados |
| `StockMovementServiceTest` | 4 | 4 aprovados |
| `LotServiceAtomicityTest` | 4 | 4 aprovados; usa fakes, não JDBC real |
| `LotServiceTest` | 5 | 4 aprovados, 1 falhou |
| **Total** | **77** | **76 aprovados, 1 falhou, 0 erros de execução, 0 ignorados** |

O caso que falhou foi `LotServiceTest.lotCreationPersistsOnlyInitialQuantityAndEntryMovement`. A expectativa é que criar um lote com quantidade 10 deixe 10 disponíveis. O banco guardou 20, porque o fluxo grava a quantidade inicial e depois a soma uma segunda vez como entrada. `Lot.reconstitute` não aceita esse estado pois `available_quantity` supera `initial_quantity`.

Os testes de venda e falha de repositório usam JDBC real no `LotServiceTest`: a falha é injetada no repositório de movimentação ou no método de atualização de lote, enquanto o `JdbcTransactionManager` e a conexão MariaDB são reais. Os testes de `LotServiceAtomicityTest` e `StockMovementServiceTest` usam repositórios/transação/conexão simulados; provam delegação e identidade de conexão no serviço, mas não rollback real do MariaDB.

Essa execução é uma fotografia do estado observado; qualquer mudança no banco, código ou ambiente exige nova execução. Não foi configurada ferramenta de cobertura, então não existe percentual de cobertura a reportar.

## 4. Falhas e riscos conhecidos a discutir

### Saldo inicial duplicado na criação do lote

1. `Lot.create` inicializa `availableQuantity` com `initialQuantity`.
2. `LotService.save` chama `lotRepository.save(conn, lot)`, que persiste o saldo inicial.
3. Ainda na transação, `LotService.save` chama `stockMovementService.registerEntry` com `initialQuantity`.
4. A operação SQL de entrada adiciona esse valor ao saldo já gravado.
5. O banco fica inconsistente com o invariante aplicado em `Lot.reconstitute`.

Evidências: falha acima e asserção de que a reconstituição rejeita o registro salvo. Nenhuma correção foi feita nesta auditoria.

### Testes e atomicidade

- O teste de rollback de venda verifica com leitura SQL posterior que o saldo persistido volta ao valor imediatamente anterior e não fica saída/itens de saída parciais.
- O teste de falha no update confirma que o movimento de saída não é persistido.
- Não há teste determinístico de falha durante `commit()` ou durante `rollback()`, nem de exceção suprimida do rollback.
- Não há teste de concorrência para duas vendas simultâneas do mesmo lote.
- O rollback da entrada inicial quando a gravação da movimentação falha ainda pode ser coberto melhor em integração; o fluxo de criação atualmente já falha pelo saldo dobrado.

### Regras abertas

- A perda aceita uma causa textual, mas a política que define quais lotes podem sofrer perda deve ser decidida e testada (por exemplo, somente lote vencido ou também avaria).
- `ADJUSTMENT` existe no enum, mas falta serviço, regras, autorização e auditoria da operação.
- A venda opera sobre um lote por chamada. A seleção/distribuição entre lotes e política FEFO ainda não estão implementadas.

## 5. Etapas do projeto e percentuais estimados

Percentuais são estimativas de avanço contra critérios de conclusão, definidas por Leonardo Alves e revisadas com base no estado do repositório. Não são métricas automáticas, cobertura de testes nem promessa de prazo. Etapas futuras ficam em 0% enquanto não houver implementação observável.

| Etapa | Avanço | Critério usado e o que falta |
|---|---:|---|
| 1. Domínio e serviços em Java puro | **65%** | Há categorias, produtos, lotes e movimentos, com serviços. Falta fechar regras, corrigir o saldo de entrada, implementar seleção multi-lote/FEFO, ajustes, fornecedores e decisões de validade. |
| 2. Persistência MariaDB/JDBC | **70%** | Esquema, repositórios JDBC e gerenciador de transações existem. Ainda é necessário garantir consistência ponta a ponta, cobrir erro SQL/commit/rollback e tratar a entrada duplicada. |
| 3. Terminal operacional | **45%** | Cadastro e consultas essenciais estão no menu. Ainda não há fluxos de venda, perda, ajuste, histórico ou operação completa para o mercado. |
| 4. Qualidade e testes | **60%** | Há 77 testes e integração real; a execução registrada tem 1 falha, e permanecem lacunas de concorrência e de falhas de commit/rollback. |
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

1. Leonardo analisa a falha de duplicação na entrada e decide a regra correta de criação do lote.
2. Atualizar a expectativa de integração após a decisão e comprovar saldo inicial, movimento `ENTRY` e item correspondentes.
3. Adicionar testes JDBC para entrada com falha e perdas conforme a política escolhida.
4. Testar erros SQL, commit/rollback, isolamento e concorrência com estratégia determinística.
5. Completar fluxos de terminal e histórico conforme os requisitos aceitos.
6. Só então definir e implementar a API HTTP em Java puro.
7. Integrar Angular depois que contratos e fluxos HTTP estiverem estáveis.
8. Planejar a migração Spring Boot v2 como estudo separado, preservando testes de comportamento.

Detalhamento funcional e decisões em aberto estão em [requirements.md](requirements.md); a visão geral está no [README](../README.md).
