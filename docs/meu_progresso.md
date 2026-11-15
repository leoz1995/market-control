# Progresso e plano de aprendizagem — MarketControl

**Responsável pelo projeto:** Leonardo Alves
**Auditoria de testes e documentação:** 15 de novembro de 2026
**Princípio de colaboração:** a IA atualiza testes e documentação. Leonardo Alves é quem desenvolve, decide e altera o código de produção.

Este documento descreve o que foi observado nos arquivos do repositório e na última execução da suíte. Classes existentes não são tratadas como prova de um fluxo concluído: quando uma regra importante não passa por teste ou apresenta inconsistência, isso fica registrado como trabalho pendente.

## 1. Estado atual em poucas linhas

O MarketControl deixou de ser apenas um plano de persistência futura: a aplicação atualmente usa repositórios JDBC e MariaDB. Há modelos e serviços para categorias, produtos, lotes e movimentações. A aplicação principal ainda é um terminal simples; ainda não existe API HTTP.

O estado atual permite criar lote com entrada inicial e registrar entradas adicionais. A entrada inicial grava o total e o saldo no lote uma vez e registra o movimento/item `ENTRY`; entradas adicionais atualizam `total_received_quantity` e `available_quantity` e registram seu próprio movimento na mesma transação. A auditoria confirmou esse comportamento com MariaDB.

## 2. O que existe no código

### Domínio

- `Category`: criação, edição, normalização básica de nome/descrição e estado ativo.
- `Product`: nome, código de barras, descrição, categoria e unidade; normalização e validações de criação/edição.
- `Lot`: identidade, produto, datas, quantidades total recebida/disponível, reconstituição e consulta de validade. O lote é um retrato imutável; não executa venda ou perda diretamente.
- `StockMovement` e `StockMovementItem`: cabeçalho com tipo, data/hora, descrição e itens; validações estruturais.
- `MovementType`: entrada, saída, perda e ajuste. O enum inclui ajuste, mas não há fluxo de serviço de ajuste identificado.
- `UnitOfMeasure`: unidade, quilograma, grama, litro e mililitro.

### Serviços e terminal

- `CategoryService`: cadastro, busca, edição e listagem de categorias.
- `ProductService`: cadastro, edição, listagem e busca por código de barras.
- `LotService`: cadastro com entrada inicial, entrada adicional (`updateStockLot`), busca por ID/produto e totalização de estoque.
- `StockMovementService`: entrada inicial, entrada adicional, venda e perda. Usa `execute` para operações sem retorno e `executeAndReturn` para entrada adicional; a criação do lote compartilha a conexão entre lote e entrada inicial.
- `Program`: interface de terminal para cadastro/consulta/edição de produtos, cadastro de categoria e cadastro/consulta de lotes. Não oferece ainda menu para venda, perda, ajuste ou histórico.

### Persistência e banco

- `DatabaseConnection` carrega as credenciais locais de `database.properties`.
- Repositórios JDBC para categoria, produto, lote, movimentação e item.
- `JdbcTransactionManager` centraliza abertura da conexão, desativação de auto-commit, execução, commit e rollback. `execute` recebe `TransactionWork` sem retorno; `executeAndReturn` recebe `TransactionAction<T>` e só retorna depois do commit.
- Os scripts `database/01_categories.sql` a `database/05_stock_movement_items.sql` definem as tabelas e relações.
- `LotRepository` expõe operações SQL condicionais para entrada, venda e perda; a quantidade de linhas afetadas informa se a operação ocorreu.

## 3. Execução de testes registrada

Diagnóstico inicial: uma execução incremental (`mvn test`) usou classes antigas em `target` e registrou 77 casos com 20 erros: referências compiladas a nomes/signaturas de produção anteriores e bloqueio do socket MariaDB pelo sandbox. A auditoria alinhou somente os testes à API atualmente presente e usou `mvn clean test` com acesso ao MariaDB para forçar recompilação integral.

Com MariaDB local acessível, `mvn clean test` compilou do zero e executou **121 testes em 15 de novembro de 2026**:

| Classe | Total | Resultado registrado |
|---|---:|---|
| `DataBaseConnectionIntegrationTest` | 1 | 1 aprovado; MariaDB real |
| `JdbcProductRepositoryIntegrationTest` | 4 | 4 aprovados; busca independente/fornecida, mapeamento e ausência |
| `JdbcLotRepositoryIntegrationTest` | 5 | 5 aprovados; consultas, mapeamento, constraints e propriedade da conexão |
| `JdbcTransactionManagerIntegrationTest` | 8 | 8 aprovados; commits, rollbacks e exceções suprimidas |
| `CategoryTest` | 7 | 7 aprovados |
| `MovementTypeTest` | 2 | 2 aprovados |
| `UnitOfMeasureTest` | 2 | 2 aprovados |
| `LotTest` | 23 | 23 aprovados |
| `ProductTest` | 19 | 19 aprovados |
| `StockMovementTest` | 9 | 9 aprovados |
| `StockMovementItemTest` | 5 | 5 aprovados |
| `CategoryServiceIntegrationTest` | 2 | 2 aprovados; JDBC real |
| `ProductServiceIntegrationTest` | 6 | 6 aprovados; JDBC real |
| `StockMovementServiceTest` | 4 | 4 aprovados; testes com fakes |
| `LotServiceAtomicityTest` | 4 | 4 aprovados; usa fakes, não JDBC real |
| `LotServiceIntegrationTest` | 20 | 20 aprovados; MariaDB real, atomicidade e concorrência |
| **Total** | **121** | **121 aprovados, 0 falhas, 0 erros, 0 ignorados; 75 unitários e 46 integração** |

`LotServiceIntegrationTest` verifica saldo inicial único, movimento e item `ENTRY`, rollback da criação inicial quando o movimento falha, aumento do total recebido e saldo disponível na entrada adicional, retorno atualizado, rollback da entrada adicional, venda e rollback, venda igual ao saldo, rejeição acima do saldo, perda e rollback, causa informada, e falha ao gravar item posterior. Quatro corridas sincronizadas por `CountDownLatch` cobrem vendas 7+7 contra 10, vendas 4+6 contra 10, duas entradas concorrentes e entrada contra venda.

Os testes com sufixo `IntegrationTest` usam conexão MariaDB real. `StockMovementServiceTest` e `LotServiceAtomicityTest` usam fakes para validar delegação e identidade de conexão, não rollback JDBC. Há testes JDBC dedicados para produto, lote e gerenciador transacional; os demais repositories são cobertos sobretudo pelo fluxo composto dos services. A divisão é de 75 unitários (67 domínio e 8 serviços com fakes) e 46 integrações MariaDB.

O banco `marketcontrol` é o banco local de testes do desenvolvedor. A configuração JDBC não seleciona automaticamente um banco de teste: a URL vem de `database.properties`. Os testes usam identificadores próprios e removem os registros associados a cada execução.

Essa execução é uma fotografia do estado observado; qualquer mudança no banco, código ou ambiente exige nova execução. Não foi configurada ferramenta de cobertura, então não existe percentual de cobertura a reportar.

O conjunto direcionado `mvn -Dtest=JdbcTransactionManagerIntegrationTest,LotServiceIntegrationTest,JdbcLotRepositoryIntegrationTest,JdbcProductRepositoryIntegrationTest,LotTest,StockMovementServiceTest,LotServiceAtomicityTest test` executou **68 testes, todos aprovados**. A primeira tentativa sem permissão de socket falhou por bloqueio do sandbox ao acessar `localhost`; a repetição autorizada com acesso ao banco e a execução limpa completa passaram.

## 4. Falhas e riscos conhecidos a discutir

### Entrada inicial e adicional — comportamento validado

1. `Lot.create` inicializa `totalReceivedQuantity` e `availableQuantity` com a quantidade recebida inicialmente.
2. `LotService.save` persiste o lote e chama `registerInitialEntry(conn, lot)` na mesma transação.
3. `registerInitialEntry` grava apenas o movimento e o item `ENTRY`; não soma novamente as quantidades do lote.
4. `registerAdditionalEntry` chama `increaseAvailableQuantityForAdditionalEntry`, que aumenta total recebido e saldo disponível.
5. Testes JDBC confirmaram valores, movimento/item e a possibilidade de continuar com saldo menor que total recebido após venda/perda.

`registerAdditionalEntry` retorna o lote atualizado consultado por `findById(conn, lotId)` dentro do callback. `registerLoss` registra a causa informada. Evidência: `mvn clean test` — 121 aprovados, sem falhas/erros/ignorados. O core já tinha alterações no workspace antes desta auditoria; nenhuma foi feita nesta tarefa.

### Testes e atomicidade

- Commit de criação, entrada adicional, venda e perda foi observado por leitura do estado persistido.
- Falha após gravar cabeçalho/item de entrada adicional reverte total recebido, saldo, cabeçalho e item.
- Falha ao inserir o segundo item reverte o cabeçalho e o item anteriormente inserido.
- A concorrência de duas vendas de 7 contra saldo 10 foi validada no banco; uma foi aceita e outra recusada.
- `JdbcProductRepositoryIntegrationTest` e `JdbcLotRepositoryIntegrationTest` verificam que métodos com `Connection` deixam a conexão do chamador aberta e utilizável.
- `JdbcTransactionManagerIntegrationTest` prova commit e rollback em MariaDB. Fechar a conexão dentro do callback provoca falha JDBC no commit ou rollback; o teste confirma preservação da falha original e SQL suprimido ao falhar rollback. Não é uma simulação de interrupção de rede depois de o servidor confirmar commit.
- Abertura da conexão e `setAutoCommit(false)` não podem ser simulados deterministicamente sem injetar a conexão/fábrica, que exigiria mudança de produção. Falha de `close()` após commit concluído também não foi simulada; esse cenário não significa rollback e pode deixar resultado ambíguo.
- A criação inicial, entrada adicional, venda e perda tiveram rollback verificado em MariaDB quando a escrita do movimento/item falha.
- Os testes JDBC confirmam que conexões fornecidas pelo chamador continuam abertas e utilizáveis. Fechamento de conexões internas não é observado diretamente sem interceptar DriverManager.

### Achados de implementação fora do escopo de alteração

- **Médio — falhas na fronteira JDBC.** `JdbcTransactionManager` chama `DatabaseConnection` estaticamente, o que impede simular abertura, configuração e fechamento pós-commit sem alterar produção. Uma fábrica substituível permitiria testar esses casos, mas tem custo arquitetural; decidir em etapa aprovada.
- **Médio — commit confirmado seguido de falha em close.** No `try-with-resources`, se `commit()` retornar normalmente e `Connection.close()` falhar ao sair do bloco, o método propaga `PersistenceException` embora os dados já estejam confirmados. O chamador pode repetir e duplicar uma operação; isso é resultado ambíguo, não rollback. O caso não é reproduzido sem conexão interceptável.
- **Baixo — limite de constraints verificado.** O script e o schema conectado expõem as três constraints de quantidade. Uma consulta encontrou zero lotes com total não positivo, saldo negativo ou saldo acima do total. Não foram alterados dados nem schema nesta auditoria.
- **Baixo — projeções SQL repetidas.** As consultas de leitura de produto/lote repetem listas de colunas em alguns métodos, embora o mapeamento de linhas seja compartilhado. Uma constante/helper pode reduzir divergência futura, com pequeno custo e risco de alterar consultas; oportunidade, não defeito funcional comprovado.

`TransactionWork` descreve callback sem retorno; `TransactionAction<T>` descreve callback com resultado. `execute` adapta o primeiro para o mecanismo privado genérico `executeInTransaction`; `executeAndReturn` delega a ação diretamente. O método comum abre conexão, desativa auto-commit, chama a operação uma vez, commita e só então retorna resultado; ao falhar, tenta rollback e suprime uma eventual falha de rollback na exceção original.

### Desempenho

Não foi medido gargalo e não foi feito benchmark artificial. As consultas observadas são pontuais, statements usam parâmetros e mapeadores JDBC; a integração não oferece evidência de carga. A busca de lotes por produto carrega o produto e depois consulta lotes, mas não foi identificado loop N+1. Otimização de consultas/conexões é prematura até haver volume representativo.

### Regras abertas

- A política de elegibilidade de perdas ainda precisa de decisão, embora a causa individual seja recebida e registrada.
- `ADJUSTMENT` existe no enum, mas falta serviço, regras, autorização e auditoria da operação.
- A venda opera sobre um lote por chamada. A seleção/distribuição entre lotes e política FEFO ainda não estão implementadas.

## 5. Etapas do projeto e percentuais estimados

Percentuais são estimativas de avanço contra critérios de conclusão, definidas por Leonardo Alves e revisadas com base no estado do repositório. Não são métricas automáticas, cobertura de testes nem promessa de prazo. Etapas futuras ficam em 0% enquanto não houver implementação observável.

| Etapa | Avanço | Critério usado e o que falta |
|---|---:|---|
| 1. Domínio e serviços em Java puro | **65%** | Há categorias, produtos, lotes, movimentos e os fluxos básicos de entrada/venda/perda. Faltam regras acordadas, seleção multi-lote/FEFO, ajustes e fornecedores. |
| 2. Persistência MariaDB/JDBC | **70%** | Esquema, repositórios JDBC e gerenciador existem. Atomicidade e concorrência parcial foram validadas; faltam falhas na abertura/configuração e testes JDBC diretos dos repositories restantes. |
| 3. Terminal operacional | **45%** | Cadastro e consultas essenciais estão no menu. Ainda não há fluxos de venda, perda, ajuste, histórico ou operação completa para o mercado. |
| 4. Qualidade e testes | **75%** | 121 testes passaram, incluindo 46 integrações MariaDB; permanecem lacunas de abertura/configuração/close e de repositories sem testes JDBC dedicados. |
| 5. Tratamento de erros | **0%** | Próxima etapa oficial; exceções específicas e responsabilidades por camada ainda não foram implementadas. |
| 6. HTTP nativo do Java | **0%** | Etapa após tratamento de erros; faltam servidor, roteamento, DTOs, JSON e testes de fronteira. O primeiro endpoint será `GET /api/health`. |
| 7. Front-end Angular com IA | **0%** | Ainda não existe aplicação Angular. Planejado depois de uma API estável; a integração e as decisões de produto continuam sob responsabilidade de Leonardo. |
| 8. Spring Boot v2 | **0%** | É uma migração futura para estudo comparativo, depois da v1 Java puro + HTTP e da experiência com Angular. Não antecipar framework na etapa atual. |

Os números não são somados nem convertidos em uma média geral: tratamento de erros, HTTP, Angular e Spring Boot são etapas separadas ainda não iniciadas.

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

1. **Próxima etapa oficial — tratamento de erros:** definir exceções específicas; separar validação, regra de negócio, recurso ausente e falha de persistência; definir propagação/responsabilidade por camada e preparar mapeamento para HTTP. Não iniciada nesta auditoria.
2. **Etapa seguinte — HTTP nativo do Java:** estudar requisições/respostas, implementar roteamento, DTOs e JSON; criar `GET /api/health` e avançar gradualmente com testes HTTP, sem Spring Boot.
3. Como evolução de confiabilidade, cobrir falha de abertura/configuração, fechamento pós-commit e contratos JDBC restantes se houver ponto de injeção aprovado.
4. Depois das etapas anteriores, avançar para Angular e, futuramente, Spring Boot v2 segundo o roadmap.

Detalhamento funcional e decisões em aberto estão em [requirements.md](requirements.md); a visão geral está no [README](../README.md).
