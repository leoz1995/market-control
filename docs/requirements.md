# MarketControl — requisitos e estado funcional

> Atualizado em **18/11/2026** após inspeção do código atual e execução de `mvn clean test` com acesso ao MariaDB local de testes. Este documento separa decisões de domínio, funcionalidades existentes e itens planejados. Um requisito estar descrito aqui não significa que esteja implementado.

## 1. Objetivo

Construir gradualmente um sistema de controle de estoque para mercado, praticando engenharia de software em Java: domínio, JDBC/MariaDB, transações, testes, HTTP e, mais adiante, uma interface independente.

O estado atual é uma aplicação Java de terminal, com domínio, serviços e persistência JDBC. Não há API HTTP, frontend, autenticação ou deploy. Para configuração, comandos, arquitetura detalhada e evidência dos testes, consulte o [README](../README.md).

## 2. Decisões de domínio estabelecidas

### 2.1 Produto, categoria e unidade

- Produto pertence a uma categoria e possui nome, código de barras, descrição opcional e unidade.
- Categorias possuem nome, descrição e estado ativo.
- Códigos de barras são normalizados em caixa alta e comparados sem espaços nas pontas pelo fluxo de serviço/repositório.
- Unidade é um código persistido do enum: `UNIT`, `KILOGRAM`, `GRAM`, `LITER` ou `MILLILITER`.
- Alterar os códigos numéricos persistidos exige migração coordenada. Regras de conversão entre unidades ainda não foram definidas.

### 2.2 Fornecedores

- Identidade UUID; documento empresarial com 14 dígitos; e-mail obrigatório.
- Fornecedor pode estar ativo ou inativo.
- `ENTRY` requer fornecedor ativo. Fornecedor inativo permanece consultável para histórico.
- `EXIT` e `LOSS` não exigem fornecedor e persistem `supplier_id` como `NULL`.
- Registro de atualização/inativação via serviço ainda não está completo: o repository declara `update`, mas a implementação JDBC está TODO.
- Não remover fisicamente fornecedor associado a movimentos; a FK do schema deve impedir remoção incompatível.

### 2.3 Lotes e aquisições

- Um lote representa o lote físico de um produto e tem UUID, produto, data de entrada, validade e saldo disponível.
- `availableQuantity` é o saldo corrente; não existe `totalReceivedQuantity` no modelo ou no schema atual.
- A primeira aquisição cria lote, `ENTRY` e item da movimentação; quantidade e custo histórico da entrada ficam no item.
- Cada nova aquisição do mesmo produto cria outro lote. Não existe operação atual para acrescentar quantidade a lote antigo.
- Quantidade inicial deve ser positiva; entrada não pode ser futura; validade não pode anteceder entrada.
- Lote só é considerado vencido quando `expirationDate` é anterior à data atual.
- Venda não pode usar lote vencido; perda pode consumir lote vencido, desde que haja saldo.

### 2.4 Movimentações e custo

- Tipos no enum: `ENTRY`, `EXIT`, `LOSS`, `ADJUSTMENT`; códigos são persistidos numericamente.
- Movimento tem UUID, tipo, data/hora, descrição, fornecedor opcional e um ou mais itens.
- Entrada pode conter vários itens e um fornecedor por cabeçalho. O fluxo de serviço atualmente implementado para aquisição cria um item por lote.
- Item contém `lotId`, quantidade positiva e `unitCost` positivo. Total é calculado com `BigDecimal` e escala de duas casas.
- `unitCost` é custo histórico de aquisição, não preço de venda ao consumidor.
- Custo de venda/perda vem do item da `ENTRY` inicial daquele lote. Ausência ou ambiguidade deve falhar; não inventar custo.
- `ADJUSTMENT` existe como tipo, mas não há caso de uso de ajuste implementado.

### 2.5 Transações

- Persistir saldo e movimento associado na mesma conexão e transação JDBC.
- Entrada inicial: salvar lote + `ENTRY` + item como unidade atômica.
- Venda/perda: atualizar saldo e registrar movimento/item atomicamente.
- Erro em qualquer escrita antes do commit precisa resultar em rollback observável no banco.
- Operações sobre estoque devem proteger o saldo contra valores negativos, incluindo concorrência. Os testes atuais confirmam atualização condicional individual, mas não substituem teste de corrida concorrente.

## 3. Matriz de funcionalidades

Estados: **Implementado e exercitado** = comportamento atual com evidência de execução adequada; **Parcial** = existe parte ou falta caminho/cobertura; **Planejado** = ainda não identificado no código.

| ID | Requisito | Estado atual observado |
|---|---|---|
| RF01 | Cadastrar categoria | Implementado por domínio, serviço, JDBC e CLI; testes de integração existentes. |
| RF02 | Consultar/listar/editar categoria | Implementado por serviço e JDBC; CLI oferece cadastro, não ciclo completo. |
| RF03 | Cadastrar produto | Implementado por domínio, serviço, JDBC e CLI; integração exercita persistência. |
| RF04 | Consultar/listar/buscar produto | Implementado por serviços/JDBC e CLI. |
| RF05 | Editar produto | Implementado em domínio, serviço, JDBC e CLI; unidade de medida não pode ser alterada. |
| RF06 | Ativar/desativar produto | Planejado; produto não possui estado ativo. |
| RF07 | Cadastrar fornecedor | Parcial: modelo, serviço save e persistência JDBC existem; CLI não oferece cadastro. |
| RF08 | Consultar fornecedor por UUID/documento | Parcial: busca por UUID e existência por documento; sem listagem. |
| RF09 | Editar/inativar fornecedor | Pendente: `JdbcSupplierRepository.update` não implementado e serviço não expõe atualização. |
| RF10 | Entrada inicial de mercadoria | Implementado: fornecedor ativo, lote, movimento ENTRY/item e custo histórico no mesmo transaction manager. |
| RF11 | Nova aquisição gera novo lote | Implementado e exercitado na integração; não há entrada adicional no lote existente. |
| RF12 | Consultar lote(s) e saldo | Implementado por serviço/repository; total agrega lotes com validade hoje ou futura. |
| RF13 | Venda de estoque | Parcial: `registerSale` atende um lote por chamada e grava movimento; sem CLI nem distribuição por múltiplos lotes. |
| RF14 | Bloquear venda vencida/sem saldo | Implementado por update SQL condicional; integração cobre validade/saldo. |
| RF15 | Registrar perda | Parcial: `registerLoss` por lote, com causa, transação e custo histórico; sem CLI. |
| RF16 | Perda de produto vencido | Implementado no SQL de perda, que não bloqueia por data; integração cobre o caso. |
| RF17 | Recuperar custo histórico | Implementado por consulta da ENTRY inicial via lote; integração verifica custo. Schema não impõe unicidade dessa entrada. |
| RF18 | Consultar histórico de movimentos | Pendente: cabeçalhos/itens são gravados, mas não há consulta/repository/service de histórico. |
| RF19 | Alertar validade próxima | Planejado; não há regra de janela nem alerta. |
| RF20 | Estoque mínimo/alerta | Planejado; produto não contém mínimo nem serviço de alerta. |
| RF21 | Ajustar estoque com motivo | Planejado; enum `ADJUSTMENT` existe, sem caso de uso. |
| RF22 | Saída distribuída entre vários lotes | Planejado; política FEFO não implementada nem decidida como requisito final. |
| RF23 | API HTTP | Planejado; nenhum servidor/endpoint existe. |
| RF24 | Frontend independente | Planejado após contrato HTTP estável; nenhum projeto frontend existe. |
| RF25 | Autenticação/autorização | Planejado; usuários e permissões não existem. |
| RF26 | Spring Boot | Planejado para etapa posterior de aprendizagem; o backend atual é Java puro/JDBC. |

## 4. Requisitos não funcionais

- **RNF01 — evolução didática:** consolidar Java/JDBC/SQL/transações antes de adotar abstrações de framework.
- **RNF02 — integridade:** impedir saldo negativo e manter lote/movimento/itens consistentes.
- **RNF03 — dinheiro e quantidades:** usar `BigDecimal`; documentar escala e arredondamento. Schema atual usa quantidade `DECIMAL(15,3)` e custo unitário `DECIMAL(15,2)`.
- **RNF04 — tempo:** datas de lote usam `LocalDate`; movimentos usam `LocalDateTime`. Alinhar relógio JVM e `CURRENT_DATE` do banco e tornar testes temporais previsíveis.
- **RNF05 — atomicidade:** qualquer alteração de saldo e histórico deve commitar integralmente ou não persistir.
- **RNF06 — testes honestos:** separar unitários com dublês de integração JDBC real; falha de ambiente deve ser reportada, não mascarada.
- **RNF07 — camadas:** regras permanecem no domínio/serviço; a API futura será adaptador e não dona da regra.
- **RNF08 — segurança futura:** proteger segredos; API não pode expor SQL, stack traces ou credenciais.
- **RNF09 — operação futura:** migrations reproduzíveis, CI, logs, backups e ambiente de deploy antes de declarar prontidão profissional.

## 5. Arquitetura e estrutura atuais

```text
src/main/java/marketcontrol/
├── Program.java                         # terminal e composição
├── dtos/ProductDTO.java                 # dados de edição de produto
├── domain/
│   ├── model/                            # Category, Product, Supplier, Lot, movimentos
│   │   ├── enums/                        # códigos de unidade e movimento
│   │   └── interfaces/                   # visões ReadOnly*
│   └── repository/                       # contratos dos repositories
├── service/                              # casos de uso
├── transaction/                          # abstração de transação/callbacks
├── infrastructure/
│   ├── database/DatabaseConnection.java  # properties e DriverManager
│   └── persistence/jdbc/                 # repositories e JdbcTransactionManager
└── exception/                             # persistência e mapeamento

src/test/java/                             # JUnit 5 unitário e integração MariaDB
database/                                  # seis scripts DDL
docs/requirements.md                       # decisões e matriz funcional
```

Os contratos `LotRepository` e `StockMovementRepository` recebem `java.sql.Connection` nas operações transacionais. Isso permite compartilhar a conexão, mas acopla parte do núcleo à tecnologia JDBC. A composição é manual no `Program` e não existe container de injeção.

## 6. Banco de dados

Scripts: `01_categories.sql`, `02_products.sql`, `03_lots.sql`, `04_suppliers.sql`, `05_stock_movements.sql`, `06_stock_movement_items.sql`.

| Relação | Integridade relevante atual |
|---|---|
| Produto → Categoria | FK; `category_id` não nulo; delete restrito. |
| Lote → Produto | FK; `product_id` não nulo. |
| Movimento → Fornecedor | FK nullable; índice em `supplier_id`. |
| Item → Movimento | FK; movimento obrigatório; índice. |
| Item → Lote | FK; lote obrigatório; índice. |
| Categoria | descrição não vazia por `CHECK`. |
| Produto | `bar_code` único; `unit` obrigatório. |
| Fornecedor | documento único e dados cadastrais requeridos. |
| Lote | `available_quantity DECIMAL(15,3)` não negativa; não existe coluna de total recebido. |
| Item | quantidade positiva (`DECIMAL(15,3)`) e custo positivo (`DECIMAL(15,2)`). |

**Ordem válida para schema vazio:** `01_categories.sql`, `02_products.sql`, `03_lots.sql`, `04_suppliers.sql`, `05_stock_movements.sql`, `06_stock_movement_items.sql`. Os scripts não criam banco/usuário e não são migrations incrementais.

## 7. Testes e evidência de execução

Na revisão de 18/11/2026 foram executados:

```text
mvn clean compile                 → BUILD SUCCESS; 37 classes de produção
mvn test-compile                  → BUILD SUCCESS; 22 fontes de teste
mvn clean test -Dstyle.color=never → BUILD SUCCESS; 108 testes, 0 falhas, 0 erros, 0 ignorados
```

O teste final acessou MariaDB local. A tentativa inicial sem permissão de rede falhou ao abrir socket; repetida com acesso autorizado, a suíte passou. Testes unitários cobrem categorias, produtos, fornecedores, lotes, movimentos, itens e enums. Integrações cobrem JDBC de produto/lote/fornecedor/movimento e transaction manager, além de fluxos de serviço. Há evidência DB de commit e rollback em entrada, venda e perda.

Limitações: não há métrica JaCoCo; não há teste de corrida concorrente no estado atual; nem todas as operações de Supplier ou leitura de histórico existem para serem testadas; conexão global via properties não separa profiles/ambientes. A classe `src/main/java/marketcontrol/Test.java` é um exercício de streams e não teste automatizado.

## 8. Roadmap em ordem de dependência

1. Tornar setup de banco repetível, corrigir a ordem de criação e separar configuração segura de teste/desenvolvimento.
2. Completar fornecedor: update/inativação com histórico, operação de serviço e testes para entrada ativa/inativa.
3. Completar casos de uso e consultas de movimentação; definir ajuste e venda multi-lote/FEFO antes da implementação.
4. Fortalecer schema/migrations e contratos JDBC; validar constraints e deleções necessárias.
5. Testar concorrência de venda/perda, isolamento e falhas transacionais observáveis.
6. Reduzir acoplamento de `Connection` nos contratos de aplicação e definir objetos de entrada/saída e erros estáveis.
7. Aprender HTTP com exercício Java puro e adaptar os casos de uso por endpoints finos; depois considerar Spring Boot comparativamente.
8. Antes de uso por frontend, adicionar DTOs de API, validação, autenticação/autorização, erros globais, CORS e OpenAPI.
9. Só após contrato de API estável iniciar frontend separado; agregar CI, migrations, observabilidade, backups e deploy antes de operação real.

## 9. Progresso qualitativo

Escala: 0 = ausente; 25 = estrutura inicial; 50 = fluxo principal parcial; 75 = núcleo demonstrado com evidência e lacunas de robustez; 100 = critérios de conclusão estabelecidos e demonstrados. Não é cobertura de código.

| Área | Estimativa atual |
|---|---:|
| Fundamentos de domínio | 70% |
| Modelagem OO | 65% |
| Arquitetura | 55% |
| Persistência JDBC | 70% |
| SQL e banco | 65% |
| Transações e consistência | 75% |
| Testes | 70% |
| Prontidão para estudar HTTP | 40% |
| Prontidão para frontend integrado | 15% |
| Projeto geral, considerando o objetivo de backend e frontend profissionais | 55% |

Estimativas significam progresso em relação ao escopo amplo, incluindo itens ainda não implementados. A suíte verde mede apenas os casos escritos e executados.

## 10. Limitações conhecidas e itens fora de escopo atual

- Não há API, frontend, autenticação, usuário ou autorização.
- Não existe venda distribuída entre lotes, FEFO final, ajuste ou alerta.
- Não existe consulta de histórico, paginação ou exportação.
- Fornecedor não tem atualização/inativação de ponta a ponta; JDBC update está TODO.
- Menu CLI não expõe fornecedor, venda, perda ou histórico; entrada exige UUID de fornecedor previamente existente.
- `findInitialEntryByLotId` pressupõe uma ENTRY por lote, porém o schema atual não impõe essa unicidade.
- Concorrência não foi comprovada; atualização SQL condicional cobre uma operação isolada, não a política concorrente completa.
- Código de saída e validade usam relógios JVM/DB que podem divergir por timezone.
- Configuração não tem perfil, pool, verificação de ambiente ou proteção automática contra banco incorreto.
- Não há pipeline CI, cobertura, migrations, logs estruturados, monitoramento, backup ou deploy.
- `src/main/java/marketcontrol/Test.java` é classe auxiliar experimental no source set principal.

## 11. Decisões futuras que precisam de justificativa

Decisões confirmadas de lote, custo histórico, novos lotes por aquisição, fornecedor em `ENTRY`, saídas sem fornecedor e atomicidade não devem ser reabertas sem evidência técnica concreta. Permanecem decisões abertas: conversões de unidade, escalas/rounding de venda, janela de validade próxima, política para lotes vencidos em consulta, critérios e justificativa de perdas, política FEFO e desempate, fluxo de ajuste/reversão, retenção/histórico e modelo de usuários/perfis.

## 12. Avaliação do nível técnico observado

Esta avaliação considera decisões registradas, código do repositório e rigor solicitado para a auditoria. Ela **não prova autonomia de implementação**: acompanhar uma explicação, definir requisitos e revisar evidência são sinais diferentes de resolver sozinho uma tarefa de ponta a ponta.

| Área | Nível observado | Evidência e lacuna principal |
|---|---|---|
| Java e orientação a objetos | Intermediário | O projeto usa modelos, serviços, interfaces de leitura, enums e composição manual; falta avaliar autoria independente em mudanças maiores. |
| Encapsulamento e invariantes | Intermediário | Regras como lote separado por aquisição e custo de entrada foram explicitadas; inconsistências reais ainda existem no encapsulamento e nas validações do código. |
| Collections e generics | Intermediário | Uso de `List`, `Optional`, streams, cópias imutáveis e callbacks genéricos aparece no projeto; falta evidência de domínio de coleções em cenários mais complexos. |
| Exceções | Iniciante avançado | Há exceções de persistência/mapeamento e causas preservadas; classificação e fronteiras de erro ainda precisam ficar consistentes. |
| Separação de responsabilidades | Intermediário | Há pacotes de domínio, serviço e JDBC, mas contratos transacionais expõem `Connection` e CLI ainda coordena/comunica diretamente. |
| Modelagem de domínio | Intermediário | Regras e associações de lote/movimento/custo histórico são bem definidas; existem casos ainda incompletos como ajuste e venda multi-lote. |
| JDBC e SQL | Intermediário | Repositories usam SQL e PreparedStatement e há testes reais; ainda faltam migrations, cobertura de todos os métodos e política de concorrência. |
| Transações | Intermediário | Atomicidade é explicitamente exigida e confirmada nos fluxos centrais; corrida de operações simultâneas ainda não foi demonstrada. |
| Testes | Intermediário | 108 testes passam, incluindo integração e rollback; sem cobertura quantitativa, CI e testes concorrentes. |
| Raciocínio arquitetural | Intermediário avançado | A evolução Java puro → HTTP → Spring e frontend foi ordenada por dependências; a execução autônoma dessa evolução não está comprovada pelo diálogo. |
| Identificação e justificativa de problemas | Intermediário avançado no planejamento | O pedido exige evidência, separa falha de ambiente de falha funcional e estabelece limites de alteração; a competência prática independente ainda requer observação. |

**Síntese:** há sinais concretos de nível intermediário em decisões de domínio, leitura de arquitetura, JDBC e testes. Para afirmar nível intermediário de forma consistente, é necessário demonstrar autonomia repetida: transformar requisito em alteração pequena, implementar sem deslocar regra para camada errada, criar teste que falha antes e passa depois, verificar o banco/transação e explicar trade-offs. Para chegar a intermediário avançado, acrescentar concorrência, desenho de contratos HTTP seguros, migrations/CI e depuração de falhas de produção em exercícios feitos com pouca orientação.
