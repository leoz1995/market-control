# MarketControl

<div align="center">

### Controle de estoque com lotes, validade e histórico de movimentações

Aplicação de aprendizado avançado em **Java**, **JDBC** e **MariaDB**, construída para praticar modelagem de domínio, persistência relacional e consistência transacional sem esconder os fundamentos atrás de um framework.

![Java 25](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.8.7-C71A36?logo=apachemaven&logoColor=white)
![MariaDB](https://img.shields.io/badge/MariaDB-10.11-003545?logo=mariadb&logoColor=white)
![JUnit](https://img.shields.io/badge/JUnit-5.11.4-25A162?logo=junit5&logoColor=white)
![Status](https://img.shields.io/badge/status-em%20desenvolvimento-yellow)

</div>

> O MarketControl é um projeto em evolução, não um produto pronto para operação comercial. O estado abaixo descreve o código e os testes observados em **18 de novembro de 2026**. A suíte foi executada contra o MariaDB local de testes.

---

## Visão do projeto

O sistema registra categorias e produtos, acompanha estoque por lote e validade, e representa entradas, saídas e perdas como eventos persistidos. Cada aquisição inicia um lote independente; o custo de aquisição fica no item da entrada e é recuperado para valorizar vendas e perdas pelo custo histórico.

A aplicação é atualmente um programa Java de terminal. Seu núcleo é composto por modelos de domínio, serviços de aplicação, contratos de repositório e implementações JDBC para MariaDB. Não há ainda API HTTP, autenticação, frontend ou implantação de produção.

### Estado comprovado nesta revisão

| Verificação | Resultado observado |
|---|---:|
| Fontes de produção compiladas | 37 |
| Fontes de teste compiladas | 23 |
| Testes JUnit executados | 164 |
| Aprovados / falhas / erros / ignorados | **164 / 0 / 0 / 0** |
| Integrações MariaDB na suíte | Sim; conexão local autorizada e acessível |
| Bootstrap em schema vazio | 6 scripts executados em schema descartável; tabelas, FKs, índices e constraints verificados |
| Migrations 07 e 08 | Aplicadas ao banco local; constraints, triggers, procedure e grants inspecionados |
| Concorrência | 40 execuções simultâneas em duas threads/conexões independentes; todas passaram |
| Cobertura percentual de código | Não configurada / não medida |

Os testes incluem validações de domínio, serviços com dublês e integração JDBC real. “164 aprovados” não significa que todas as funcionalidades ou cenários de produção estejam cobertos. A seção [Testes](#testes-e-evidências) explicita o que a suíte exerce e suas lacunas.

## Funcionalidades atuais

- Cadastro, consulta e edição de categorias e produtos via serviços; o terminal oferece cadastro/listagem/busca/edição de produto e cadastro de categoria.
- Produtos associados a categorias e a uma unidade de medida: unidade, quilograma, grama, litro ou mililitro.
- Cadastro, consulta, atualização e inativação de fornecedor por serviço/repositório. O terminal não possui menu de fornecedor.
- Criação de lote com produto, entrada, validade e saldo inicial.
- Cada nova aquisição cria um novo lote e uma movimentação `ENTRY` associada ao fornecedor.
- Venda (`EXIT`) e perda (`LOSS`) decrementam o saldo por atualização SQL condicional e registram o evento na mesma transação. Testes concorrentes reais exercitam disputa pelo mesmo lote.
- Venda bloqueada se o lote estiver vencido ou não houver saldo; perda pode consumir lote vencido, se houver saldo.
- Custo da venda/perda obtido do `unit_cost` do movimento `ENTRY` associado ao lote. Não é fornecido um custo arbitrário na chamada de saída.
- Consulta de lotes de um produto e total de estoque disponível em lotes com validade hoje ou futura.

### Regras centrais do domínio

- `Lot.availableQuantity` é o saldo atual; o modelo não contém `totalReceivedQuantity`.
- A quantidade inicialmente recebida e seu custo são registrados no `StockMovementItem` de `ENTRY`.
- Quantidade e custo unitário do item de movimento devem ser positivos. O custo total é calculado pelo item com `BigDecimal` e arredondamento de duas casas.
- `ENTRY` exige fornecedor ativo; `EXIT` e `LOSS` não aceitam fornecedor. O schema permite `supplier_id` nulo para saídas e perdas.
- Fornecedor inativo pode ser mantido em histórico; não deve servir para nova entrada.
- Validade igual à data atual ainda não é considerada vencida. O teste de venda e a consulta de saldo usam a data do MariaDB (`CURRENT_DATE`); o domínio usa `LocalDate.now()` da JVM.
- `ADJUSTMENT` existe no enum e no banco como código de movimento, mas não há caso de uso de ajuste identificado.

Decisões e requisitos funcionais planejados estão mantidos em [`docs/requirements.md`](docs/requirements.md). Esta página é o guia de execução e o retrato técnico do código atual.

## Arquitetura

```text
Program (CLI)
    └── Services (casos de uso e coordenação)
          ├── Domain models / enums / interfaces de leitura
          ├── Repository interfaces
          │     └── Implementações JDBC
          └── TransactionManager
                    └── Connection → MariaDB (commit / rollback)
```

### Organização e responsabilidades

| Pacote / classe | Responsabilidade observada |
|---|---|
| `marketcontrol.Program` | Composição manual das dependências e menu de terminal. O menu atual não expõe fornecedor, venda, perda nem consulta de histórico. |
| `domain.model.Category` | Identidade, nome, descrição, estado ativo, edição e reconstituição persistida. |
| `domain.model.Product` | Identidade, nome, código de barras normalizado, descrição, categoria e unidade imutável. |
| `domain.model.Supplier` | Dados cadastrais, estado ativo e data de criação; oferece criação e reconstituição. |
| `domain.model.Lot` | Produto, datas e saldo disponível; valida criação/reconstituição, reduz/adiciona saldo e calcula validade. |
| `domain.model.StockMovement` | Cabeçalho de evento (`ENTRY`, `EXIT`, `LOSS`, `ADJUSTMENT`), fornecedor opcional conforme tipo e lista imutável de itens. |
| `domain.model.StockMovementItem` | Lote, quantidade, custo de aquisição unitário e total calculado. |
| `domain.model.enums` | Códigos persistidos para `MovementType` e `UnitOfMeasure`. Alterar códigos requer migração compatível. |
| `domain.model.interfaces.ReadOnly*` | Visões de leitura usadas em limites de serviço/modelo para categoria, produto, fornecedor e lote. |
| `domain.repository.*Repository` | Contratos de consulta/escrita. Operações transacionais de lote/movimento recebem `Connection`; portanto a abstração de domínio conhece JDBC em alguns pontos. |
| `service.CategoryService` | Cadastro, edição, consulta e listagem de categorias. |
| `service.ProductService` | Cadastro, edição, busca/listagem de produtos e validação de código de barras/categoria. |
| `service.SupplierService` | Cadastro, busca, atualização e inativação; bloqueia nova entrada de fornecedor inativo. |
| `service.LotService` | Criação de lote e entrada inicial atômica, consulta por lote/produto e saldo agregado. |
| `service.StockMovementService` | Registro de entrada inicial, venda e perda; obtém custo histórico e coordena atualização de saldo com persistência do movimento. |
| `infrastructure.database.DatabaseConnection` | Carrega `database.properties`; abre conexão de aplicação e conexão administrativa usada somente para limpeza das fixtures. |
| `infrastructure.persistence.jdbc.Jdbc*Repository` | SQL parametrizado, mapeamento de linhas e tradução de erros SQL para `PersistenceException`. |
| `JdbcTransactionManager` | Abre uma conexão, desativa auto-commit, executa callback, faz commit ou rollback e preserva falha de rollback como exceção suprimida. |
| `transaction.TransactionManager`, `TransactionWork`, `TransactionAction<T>` | Contratos dos callbacks transacionais sem retorno e com retorno. |
| `ProductDTO` | Record de dados para edição de produto; não é ainda um DTO de API. |
| `PersistenceException` / `DataMappingException` | Falha de persistência e falha ao reconstruir dados inválidos do banco. |

## Modelo de domínio e persistência

```text
Category 1 ─── N Product 1 ─── N Lot
Supplier 1 ─── N StockMovement (supplier opcional em EXIT/LOSS)
StockMovement 1 ─── N StockMovementItem N ─── 1 Lot
```

O custo histórico é localizado pelo marcador persistido `stock_movement_items.initial_entry_lot_id`, que identifica o item `ENTRY` original de cada lote. A migration 07 adiciona unicidade, FK, CHECK e triggers para validar e preservar essa origem. A migration 08 fecha a escrita direta: uma procedure cria lote, ENTRY e item na mesma transação, e `marketcontrol_app@localhost` não recebe INSERT em `lots`. O repositório consulta somente o marcador; entradas posteriores não o substituem.

### Tabelas e restrições descritas pelos scripts versionados

| Tabela | Colunas relevantes e integridade |
|---|---|
| `categories` | UUID textual como PK; nome, descrição e `active`; descrição não vazia por `CHECK`. |
| `products` | PK; `bar_code` único; categoria obrigatória com FK `ON DELETE RESTRICT`; unidade obrigatória armazenada como código numérico. |
| `lots` | PK; produto obrigatório com FK; `entry_date`, `expiration_date`, `available_quantity DECIMAL(15,3)` obrigatório e `CHECK` para saldo não negativo. Não há `total_received_quantity`. |
| `suppliers` | PK; nomes, documento `CHAR(14)` único, e-mail, telefone, contato, estado e `created_at` obrigatórios. |
| `stock_movements` | PK; tipo, data/hora, descrição; `supplier_id` nullable, índice e FK para fornecedor. |
| `stock_movement_items` | PK; FKs para movimento e lote; quantidade `DECIMAL(15,3)` e custo unitário `DECIMAL(15,2)`; ambos positivos por `CHECK`; índices para FKs. Migration 07 adiciona `initial_entry_lot_id` único, FK/CHECK e triggers de tipo/imutabilidade. |

Para um schema vazio, execute `database/01_categories.sql` a `database/06_stock_movement_items.sql`, nessa ordem. Essa ordem foi testada em `marketcontrol_bootstrap_test`, descartável, e validada quanto às tabelas, FKs, índices e constraints; o schema foi removido após o teste. Aplique depois as migrations `07_lot_initial_entry_integrity.sql` e `08_atomic_lot_creation_and_app_grants.sql` com root/administrador. A 07 interrompe antes do DDL se o histórico não tiver exatamente uma ENTRY candidata por lote. A 08 valida as origens existentes, cria a procedure e substitui os grants do usuário preexistente `marketcontrol_app@localhost`. Os scripts não criam banco ou usuário.

A FK imediata não consegue exigir item filho no commit, então o controle é feito pelo privilégio: a aplicação não pode inserir em `lots` e só consegue criar lotes pela procedure `SQL SECURITY DEFINER`. A procedure também funciona como transação própria se chamada por sessão em autocommit; dentro do `TransactionManager`, participa da transação do serviço. A exclusão da ENTRY é bloqueada mesmo que a conta de aplicação defina a variável de limpeza: o trigger só permite a exceção para sessão root, usada em testes.

### Transações e atomicidade

- Entrada inicial: `LotService.save` valida e carrega produto/fornecedor, cria lote e, em `TransactionManager.execute`, salva lote e movimento/item `ENTRY` pela mesma conexão.
- Venda: recupera custo da entrada, atualiza saldo somente se há saldo suficiente e validade não vencida, salva `EXIT`, e comita tudo junto.
- Perda: recupera custo de entrada, atualiza saldo sem filtro de validade, salva `LOSS`, e comita tudo junto.
- Se a escrita do evento falha depois de uma atualização/inserção, o gerenciador tenta rollback na conexão compartilhada.
- A suíte confirma no MariaDB os commits e rollbacks observáveis desses fluxos. Testes com duas threads, duas conexões e barreira antes das operações verificam venda/venda, venda/perda, consumo dentro do saldo e rollback concorrente.
- O débito usa uma única atualização condicional InnoDB (`available_quantity = available_quantity - ?` com `available_quantity >= ?`); as atualizações concorrentes são serializadas pelo lock de linha e reavaliam o saldo após a espera. O movimento pertence à mesma transação e reverte junto em falha.

## Tecnologias e versões

- **Java 25** — `maven.compiler.release/source/target` no `pom.xml`.
- **Maven** — Maven 3.8.7 no ambiente da revisão.
- **MariaDB** — Connector/J 3.5.6; instância de auditoria 10.11.19.
- **JUnit Jupiter** 5.11.4, executado pelo Maven Surefire 3.5.2.
- JDBC manual, SQL explícito e `BigDecimal` para quantidades/custos.
- Sem Spring, JPA/Hibernate, servidor HTTP, frontend, autenticação ou ferramenta de cobertura configurada.

## Como executar

### Pré-requisitos

1. JDK 25 e Maven instalados. `java -version` deve apontar para Java 25; a versão isolada do shell pode diferir do Java usado pelo Maven.
2. MariaDB compatível, com schema de teste `marketcontrol` criado e acessível.
3. As tabelas criadas na ordem de dependência indicada acima.

### Configuração de conexão

`DatabaseConnection` procura `database.properties` na raiz do classpath. Crie esse arquivo local a partir de `src/main/resources/database.properties.example` somente se ainda não existir. Configure `db.url`, `db.user` e `db.password` para `marketcontrol_app`. Os testes usam também `db.admin.user` e `db.admin.password` para limpar fixtures; configure uma conta administrativa local e nunca versione essas credenciais. Não sobrescreva um arquivo local existente. O arquivo final está excluído pelo `.gitignore`. A aplicação não seleciona o ambiente automaticamente nem valida que a URL aponta para banco não produtivo. **Aponte testes somente a um schema descartável/de testes.**

### Construir e testar

```bash
mvn clean compile
mvn test-compile
mvn test
mvn clean test
```

Os testes de integração acessam o MariaDB e escrevem dados temporários; a limpeza usa a conexão administrativa separada. O comando validado nesta revisão foi `mvn clean test -Dstyle.color=never -Ddb.admin.user=root -Ddb.admin.password=root`. Não exponha argumentos administrativos em ambientes compartilhados. Uma execução sem acesso ao banco não equivale à suíte completa.

### Executar o terminal

O ponto de entrada é `marketcontrol.Program`. Execute essa classe pela IDE depois de compilar e configurar o banco. O `pom.xml` não configura um plugin Maven de execução. O uso funcional depende de banco corretamente configurado e registros relacionados. A entrada de lote pelo menu pede UUID de fornecedor, mas o terminal não oferece cadastro de fornecedor.

## Testes e evidências

### Resultado real dos comandos nesta revisão

| Comando | Código de saída | Evidência |
|---|---:|---|
| `mvn clean test -Dstyle.color=never -Ddb.admin.user=root -Ddb.admin.password=root` | 0 | `BUILD SUCCESS`; compilou 37 fontes de produção e 23 fontes de teste; 164 testes, 0 falhas, 0 erros, 0 ignorados. Integrações MariaDB concluídas. |

O comando foi executado com acesso ao MariaDB local de teste. Os casos de concorrência são quatro cenários repetidos dez vezes cada, com duas threads e duas conexões de MariaDB independentes por disputa.

O log da execução passou por warnings do driver em testes que provocam violações esperadas: `unit`/`active` nulos, barcode e documento duplicados e FK para lote inexistente. Esses warnings são as tentativas controladas que verificam constraints; o Surefire reportou zero falhas e zero erros.

### Resultado por classe

| Classe de teste | Casos | Tipo / evidência principal |
|---|---:|---|
| `ProductTest` | 19 | Unitário: criação, edição, normalização e validação de produto. |
| `CategoryTest` | 10 | Unitário: criação, edição, descrição, estado e reconstituição. |
| `SupplierTest` | 6 | Unitário: campos, documento numérico e estado/reconstituição. |
| `LotTest` | 6 | Unitário: datas, saldo, redução/adição e expiração. |
| `StockMovementTest` | 4 | Unitário: tipo/fornecedor, itens imutáveis e histórico inativo. |
| `StockMovementItemTest` | 3 | Unitário: quantidade/custo positivos, total arredondado e reconstituição. |
| `MovementTypeTest` | 2 | Unitário: códigos de movimento válidos e inválidos. |
| `UnitOfMeasureTest` | 2 | Unitário: códigos de unidade válidos e inválidos. |
| `SupplierServiceTest` | 6 | Serviço com dublê: cadastro, atualização, inativação, fornecedor ausente e falha de persistência. |
| `LotServiceTest` | 1 | Serviço com dublês: nova aquisição cria lote independente e ENTRY de custo histórico. |
| `StockMovementServiceTest` | 2 | Serviço com dublês: custo histórico/conexão compartilhada e ausência de ENTRY. |
| `LotServiceAtomicityTest` | 4 | Serviço com dublês: mesma conexão, rollback fake e validações de venda. |
| `CategoryServiceIntegrationTest` | 2 | Integração MariaDB: cadastro, busca e edição. |
| `ProductServiceIntegrationTest` | 9 | Integração MariaDB: cadastro, barcode, edição e categoria. |
| `JdbcProductRepositoryIntegrationTest` | 17 | JDBC/MariaDB: mapeamento, consulta, insert/update e constraints. |
| `JdbcLotRepositoryIntegrationTest` | 3 | JDBC/MariaDB: schema, saldo agregado, decremento e insuficiência. |
| `JdbcSupplierAndMovementIntegrationTest` | 13 | JDBC/MariaDB: fornecedor, ENTRY original, duplicidade, FK, custos, venda/perda e rollback. |
| `LotServiceIntegrationTest` | 6 | MariaDB: procedure, INSERT direto negado, entrada atômica, rollback e fornecedor inativo. |
| `JdbcTransactionManagerIntegrationTest` | 8 | MariaDB: commit, rollback, callbacks, falhas e exceções suprimidas. |
| `DataBaseConnectionIntegrationTest` | 1 | MariaDB: abertura real de conexão configurada. |
| `JdbcStockMovementConcurrencyIntegrationTest` | 40 | MariaDB: 10 repetições por cenário; venda/venda, venda/perda, consumo dentro do saldo e rollback após falha. |
| **Total** | **164** | **21 classes; 0 falhas, 0 erros, 0 ignorados.** |

Os dublês comprovam decisões/orquestração de serviço, não persistência. Os testes JDBC e de serviço de integração acessam MariaDB real. Os relatórios Surefire ficam em `target/surefire-reports` depois da execução. Não existe JaCoCo ou medida de cobertura de linhas/branches.

## Limitações e riscos técnicos atuais

1. **Privilégios administrativos:** a conta `marketcontrol_app` não pode inserir em `lots`; root/DBA ainda pode alterar a procedure, grants ou triggers, como qualquer administrador do banco.
2. **Transação não cobre toda operação prévia:** produto e fornecedor são consultados antes da transação de criação; lote + ENTRY/item são gravados juntos.
3. **Histórico sem API de consulta:** movimentos existem e são persistidos, mas não há consultas por período/produto/lote, paginação ou apresentação.
4. **CLI parcial:** não inclui fornecedor, venda, perda, ajuste ou histórico; erros de persistência não são tratados no laço principal como os erros de validação.
5. **Configuração:** properties do classpath são carregadas estaticamente; não há perfis de ambiente, pool ou proteção automática contra apontamento acidental para outro banco.
6. **Prontidão operacional:** não há autenticação/autorização, auditoria de usuário, CI, observabilidade, backup, deploy ou frontend.
7. **Fonte experimental no pacote principal:** `src/main/java/marketcontrol/Test.java` contém um `main` de exercício de streams e não pertence ao fluxo do sistema.

Esses pontos são limites atuais, não funcionalidades afirmadas como existentes.

### Achados técnicos concretos

| Prioridade | Local | Achado e efeito | Próxima ação técnica |
|---|---|---|---|
| Alta antes de uso real | `infrastructure/database/DatabaseConnection` e configuração de testes | A conexão depende de `database.properties` do classpath e não identifica/proíbe ambiente de produção. Uma configuração errada pode direcionar testes que gravam dados à base errada. | Separar configuração por ambiente e validar explicitamente o alvo de integração antes de qualquer escrita. |
| Operacional | Migration 08 / grants MariaDB | `marketcontrol_app` não tem INSERT direto em `lots`; chamadas permitidas passam pela procedure atômica. Root continua privilegiado. | Preservar os grants mínimos e não executar a aplicação como root. |
| Média | `Program.main` | O laço de terminal captura validação/estado, mas não `PersistenceException`; falha do banco pode encerrar o fluxo com stack trace não tratado. | Definir apresentação de erros de infraestrutura e comportamento recuperável no CLI. |
| Média | `Lot.isExpired` e SQL em `JdbcLotRepository` | A validade depende do relógio da JVM no domínio e de `CURRENT_DATE` no servidor MariaDB; timezone diferente pode divergir no limite diário. | Padronizar relógio/timezone e injetar uma fonte de tempo testável. |
| Baixa / higiene | `src/main/java/marketcontrol/Test.java` | Exercício de streams está no source set de produção e será compilado junto do aplicativo. | Mover para `src/test` ou pasta de exemplos fora do artefato executável. |

As prioridades refletem risco técnico e contexto do projeto educacional; não indicam que a aplicação esteja aprovada para operação comercial.

## Maturidade e próximos passos

**Classificação:** projeto educacional de nível intermediário, com núcleo de domínio/JDBC funcional, testes MariaDB e transações concorrentes demonstradas. Está em estágio pré-MVP de backend, não em nível de produção. Essa classificação não é cobertura de código nem avaliação do nível pessoal do autor.

As migrations 07/08 fecharam a brecha para a conta da aplicação: criação de lote passa pela procedure atômica, INSERT direto foi revogado e a variável de limpeza não permite à aplicação excluir a ENTRY original.

O núcleo está pronto para iniciar HTTP como adaptador didático de escopo limitado. Isso não significa prontidão para produção: autenticação, autorização, configuração por ambiente, logs, CI, OpenAPI e deploy ainda precisam ser tratados. FEFO segue fora de escopo até haver decisão própria.

## Contribuição e limites atuais

Este repositório é individual e educacional. Não há licença declarada, política de contribuição, pipeline CI, guia de release ou ambiente de implantação versionado. Mudanças de domínio devem respeitar as decisões e requisitos registrados em `docs/requirements.md`, atualizando a documentação quando o comportamento real mudar.

## Licença

Nenhuma licença foi declarada. Não presuma permissão para redistribuição ou uso externo até que uma licença seja escolhida.
