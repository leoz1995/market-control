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

> O MarketControl é um projeto em evolução, não um produto pronto para operação comercial. O estado e os percentuais abaixo descrevem o código e os testes observados em **18 de novembro de 2026**. A suíte foi executada contra o MariaDB local de testes nesta revisão.

---

## Visão do projeto

O sistema registra categorias e produtos, acompanha estoque por lote e validade, e representa entradas, saídas e perdas como eventos persistidos. Cada aquisição inicia um lote independente; o custo de aquisição fica no item da entrada e é recuperado para valorizar vendas e perdas pelo custo histórico.

A aplicação é atualmente um programa Java de terminal. Seu núcleo é composto por modelos de domínio, serviços de aplicação, contratos de repositório e implementações JDBC para MariaDB. Não há ainda API HTTP, autenticação, frontend ou implantação de produção.

### Estado comprovado nesta revisão

| Verificação | Resultado observado |
|---|---:|
| Fontes de produção compiladas | 37 |
| Fontes de teste compiladas | 22 |
| Testes JUnit executados | 108 |
| Aprovados / falhas / erros / ignorados | **108 / 0 / 0 / 0** |
| Integrações MariaDB na suíte | Sim; conexão local autorizada e acessível |
| Alteração de schema nesta revisão | Nenhuma |
| Cobertura percentual de código | Não configurada / não medida |

Os testes incluem validações de domínio, serviços com dublês e integração JDBC real. “108 aprovados” não significa que todas as funcionalidades ou cenários de produção estejam cobertos. A seção [Testes](#testes-e-evidências) explicita o que a suíte exerce e suas lacunas.

## Funcionalidades atuais

- Cadastro, consulta e edição de categorias e produtos via serviços; o terminal oferece cadastro/listagem/busca/edição de produto e cadastro de categoria.
- Produtos associados a categorias e a uma unidade de medida: unidade, quilograma, grama, litro ou mililitro.
- Cadastro e consulta de fornecedor por serviço/repositório. O terminal não possui menu de fornecedor e a atualização JDBC do fornecedor está pendente.
- Criação de lote com produto, entrada, validade e saldo inicial.
- Cada nova aquisição cria um novo lote e uma movimentação `ENTRY` associada ao fornecedor.
- Venda (`EXIT`) e perda (`LOSS`) decrementam o saldo por atualização SQL condicional e registram o evento na mesma transação.
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
| `service.SupplierService` | Cadastro, busca por UUID e verificação de documento duplicado. Não há fluxo de edição/inativação concluído. |
| `service.LotService` | Criação de lote e entrada inicial atômica, consulta por lote/produto e saldo agregado. |
| `service.StockMovementService` | Registro de entrada inicial, venda e perda; obtém custo histórico e coordena atualização de saldo com persistência do movimento. |
| `infrastructure.database.DatabaseConnection` | Carrega `database.properties` do classpath e abre conexão via `DriverManager`. |
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

O custo histórico é localizado pela associação `StockMovementItem.lotId` → item da movimentação `ENTRY` → `unitCost`. `JdbcStockMovementRepository.findInitialEntryByLotId` exige exatamente um resultado em tempo de leitura. O banco não expressa essa cardinalidade com constraint única; duplicidade é detectada pela aplicação como erro de mapeamento.

### Tabelas e restrições descritas pelos scripts versionados

| Tabela | Colunas relevantes e integridade |
|---|---|
| `categories` | UUID textual como PK; nome, descrição e `active`; descrição não vazia por `CHECK`. |
| `products` | PK; `bar_code` único; categoria obrigatória com FK `ON DELETE RESTRICT`; unidade obrigatória armazenada como código numérico. |
| `lots` | PK; produto obrigatório com FK; `entry_date`, `expiration_date`, `available_quantity DECIMAL(15,3)` obrigatório e `CHECK` para saldo não negativo. Não há `total_received_quantity`. |
| `suppliers` | PK; nomes, documento `CHAR(14)` único, e-mail, telefone, contato, estado e `created_at` obrigatórios. |
| `stock_movements` | PK; tipo, data/hora, descrição; `supplier_id` nullable, índice e FK para fornecedor. |
| `stock_movement_items` | PK; FKs para movimento e lote; quantidade `DECIMAL(15,3)` e custo unitário `DECIMAL(15,2)`; ambos positivos por `CHECK`; índices para as FKs. |

Os scripts são `database/01_categories.sql` a `database/06_stock_movement_items.sql` e estão numerados na ordem válida: **01_categories → 02_products → 03_lots → 04_suppliers → 05_stock_movements → 06_stock_movement_items**. Os scripts não criam banco, usuário, grants ou configuração de ambiente. O schema real foi exercitado pela suíte MariaDB em 18/11/2026; nenhuma DDL foi executada durante essa revisão.

### Transações e atomicidade

- Entrada inicial: `LotService.save` valida e carrega produto/fornecedor, cria lote e, em `TransactionManager.execute`, salva lote e movimento/item `ENTRY` pela mesma conexão.
- Venda: recupera custo da entrada, atualiza saldo somente se há saldo suficiente e validade não vencida, salva `EXIT`, e comita tudo junto.
- Perda: recupera custo de entrada, atualiza saldo sem filtro de validade, salva `LOSS`, e comita tudo junto.
- Se a escrita do evento falha depois de uma atualização/inserção, o gerenciador tenta rollback na conexão compartilhada.
- A suíte confirma no MariaDB os commits e rollbacks observáveis desses fluxos. Não há evidência atual de teste concorrente de duas vendas simultâneas no estado presente.

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

`DatabaseConnection` procura `database.properties` na raiz do classpath. Crie esse arquivo local a partir de `src/main/resources/database.properties.example` somente se ainda não existir, e configure `db.url`, `db.user` e `db.password`. Não sobrescreva um arquivo local que já contenha suas credenciais. O arquivo final contém segredo, está excluído pelo `.gitignore` e não deve ser versionado. A aplicação não seleciona o ambiente por variável de ambiente nem valida que a URL aponta para um banco não produtivo. **Aponte testes somente a um schema descartável/de testes.**

### Construir e testar

```bash
mvn clean compile
mvn test-compile
mvn test
mvn clean test
```

Os testes de integração acessam o MariaDB e escrevem dados temporários. A execução final em ambiente sem acesso ao banco não equivale a suíte completa. O último comando foi executado com `-Dstyle.color=never` para legibilidade do log.

### Executar o terminal

O ponto de entrada é `marketcontrol.Program`. Execute essa classe pela IDE depois de compilar e configurar o banco. O `pom.xml` não configura um plugin Maven de execução. O uso funcional depende de banco corretamente configurado e registros relacionados. A entrada de lote pelo menu pede UUID de fornecedor, mas o terminal não oferece cadastro de fornecedor.

## Testes e evidências

### Resultado real dos comandos nesta revisão

| Comando | Código de saída | Evidência |
|---|---:|---|
| `mvn clean compile` | 0 | `BUILD SUCCESS`; compilou 37 fontes Java para release 25. |
| `mvn test-compile` | 0 | `BUILD SUCCESS`; compilou 22 fontes de teste. |
| `mvn clean test -Dstyle.color=never` | 0 | `BUILD SUCCESS`; 108 testes, 0 falhas, 0 erros, 0 ignorados. Integrações MariaDB concluídas. |

Uma tentativa inicial da suíte dentro do sandbox falhou ao abrir socket para `localhost` (`SocketException: Operação não permitida`). Após executar com acesso autorizado ao MariaDB local, a suíte completa passou. A primeira saída é limitação de rede do ambiente, não um resultado de teste de regra.

O log da execução passou por warnings do driver em testes que provocam violações esperadas: `unit`/`active` nulos, barcode e documento duplicados e FK para lote inexistente. Esses warnings são as tentativas controladas que verificam constraints; o Surefire reportou zero falhas e zero erros.

### Resultado por classe

| Classe de teste | Casos | Tipo / evidência principal |
|---|---:|---|
| `ProductTest` | 19 | Unitário: criação, edição, normalização e validação de produto. |
| `CategoryTest` | 10 | Unitário: criação, edição, descrição, estado e reconstituição. |
| `SupplierTest` | 4 | Unitário: campos, documento numérico e estado/reconstituição. |
| `LotTest` | 6 | Unitário: datas, saldo, redução/adição e expiração. |
| `StockMovementTest` | 4 | Unitário: tipo/fornecedor, itens imutáveis e histórico inativo. |
| `StockMovementItemTest` | 3 | Unitário: quantidade/custo positivos, total arredondado e reconstituição. |
| `MovementTypeTest` | 2 | Unitário: códigos de movimento válidos e inválidos. |
| `UnitOfMeasureTest` | 2 | Unitário: códigos de unidade válidos e inválidos. |
| `SupplierServiceTest` | 2 | Serviço com dublê: cadastro, documento duplicado e argumentos de consulta. |
| `LotServiceTest` | 1 | Serviço com dublês: nova aquisição cria lote independente e ENTRY de custo histórico. |
| `StockMovementServiceTest` | 2 | Serviço com dublês: custo histórico/conexão compartilhada e ausência de ENTRY. |
| `LotServiceAtomicityTest` | 4 | Serviço com dublês: mesma conexão, rollback fake e validações de venda. |
| `CategoryServiceIntegrationTest` | 2 | Integração MariaDB: cadastro, busca e edição. |
| `ProductServiceIntegrationTest` | 9 | Integração MariaDB: cadastro, barcode, edição e categoria. |
| `JdbcProductRepositoryIntegrationTest` | 17 | JDBC/MariaDB: mapeamento, consulta, insert/update e constraints. |
| `JdbcLotRepositoryIntegrationTest` | 3 | JDBC/MariaDB: schema, saldo agregado, decremento e insuficiência. |
| `JdbcSupplierAndMovementIntegrationTest` | 5 | JDBC/MariaDB: fornecedor, FK, supplier nullable, custo, venda/perda e rollback. |
| `LotServiceIntegrationTest` | 4 | MariaDB: entrada atômica, lotes separados, rollback e fornecedor inativo. |
| `JdbcTransactionManagerIntegrationTest` | 8 | MariaDB: commit, rollback, callbacks, falhas e exceções suprimidas. |
| `DataBaseConnectionIntegrationTest` | 1 | MariaDB: abertura real de conexão configurada. |
| **Total** | **108** | **20 classes; 0 falhas, 0 erros, 0 ignorados.** |

Os dublês comprovam decisões/orquestração de serviço, não persistência. Os testes JDBC e de serviço de integração acessam MariaDB real. Os relatórios Surefire ficam em `target/surefire-reports` depois da execução. Não existe JaCoCo ou medida de cobertura de linhas/branches.

## Limitações e riscos técnicos atuais

1. **Fornecedor incompleto:** `JdbcSupplierRepository.update` é TODO; não há operação de serviço para editar/inativar e nem gerenciamento de fornecedor no CLI. A regra de histórico e inativação fica limitada aos objetos/repositório existentes.
2. **Setup de schema manual:** os scripts estão na ordem válida, mas criam tabelas diretamente e não constituem migrations repetíveis/versionadas; criação de banco, usuário e grants fica fora deles.
3. **Transação não cobre toda operação prévia:** produto e fornecedor são consultados antes da transação de criação de lote; a gravação conjunta lote + ENTRY/item é transacional, e esse é o limite de atomicidade pretendido no estado atual.
4. **Cardinalidade do custo histórico sem constraint:** a aplicação detecta zero ou múltiplas ENTRY por lote no momento da consulta, mas o banco não impede movimentos ENTRY duplicados para o mesmo lote.
5. **Concorrência ainda não validada:** SQL de decremento condicional evita saldo negativo em cada atualização, mas falta prova de corrida entre vendas e decisão documentada de isolamento/retentativa.
6. **Regras e relógio:** regra de vencimento usa relógio da JVM no modelo e `CURRENT_DATE` no MariaDB; timezone divergente perto da meia-noite pode produzir diferença.
7. **API de leitura do histórico inexistente:** persistem movimentos, mas faltam buscas por período/produto/lote, paginação, caso de uso e apresentação.
8. **CLI parcial:** não inclui fornecedor, saída, perda, ajuste ou histórico; erros de persistência não são tratados no laço principal como os erros de validação.
9. **Configuração/secrets:** properties do classpath são carregadas estaticamente; não há perfis de ambiente, validação preventiva, pool ou proteção contra apontamento acidental dos testes para outro banco.
10. **Produto ainda não operacional:** sem API HTTP, autenticação/autorização, auditoria de usuário, deploy, observabilidade, migrações versionadas ou frontend.
11. **Fonte experimental no pacote principal:** `src/main/java/marketcontrol/Test.java` contém um `main` de exercício de streams e não pertence ao fluxo do sistema; considerar movê-lo para teste/exemplos antes de distribuir artefato.

Nenhum desses pontos foi corrigido nesta documentação. São prioridades de evolução, não funcionalidades afirmadas como existentes.

### Achados técnicos concretos

| Prioridade | Local | Achado e efeito | Próxima ação técnica |
|---|---|---|---|
| Alta antes de uso real | `infrastructure/database/DatabaseConnection` e configuração de testes | A conexão depende de `database.properties` do classpath e não identifica/proíbe ambiente de produção. Uma configuração errada pode direcionar testes que gravam dados à base errada. | Separar configuração por ambiente e validar explicitamente o alvo de integração antes de qualquer escrita. |
| Média | `JdbcSupplierRepository.update` / `SupplierService` | `update` está TODO; não é possível completar ciclo de manutenção/inativação de fornecedor pelo serviço. | Implementar o caso de uso e testar inativação com histórico e bloqueio de nova ENTRY. |
| Média | `JdbcStockMovementRepository.findInitialEntryByLotId` + schema de movimentos/itens | A consulta exige uma única ENTRY por lote e falha se houver mais de uma, mas o schema não impõe essa cardinalidade. | Definir constraint/modelagem compatível ou regra de seleção versionada e coberta por teste. |
| Média | `Program.main` | O laço de terminal captura validação/estado, mas não `PersistenceException`; falha do banco pode encerrar o fluxo com stack trace não tratado. | Definir apresentação de erros de infraestrutura e comportamento recuperável no CLI. |
| Média | `Lot.isExpired` e SQL em `JdbcLotRepository` | A validade depende do relógio da JVM no domínio e de `CURRENT_DATE` no servidor MariaDB; timezone diferente pode divergir no limite diário. | Padronizar relógio/timezone e injetar uma fonte de tempo testável. |
| Baixa / higiene | `src/main/java/marketcontrol/Test.java` | Exercício de streams está no source set de produção e será compilado junto do aplicativo. | Mover para `src/test` ou pasta de exemplos fora do artefato executável. |

As prioridades refletem risco técnico e contexto do projeto educacional; não indicam que a aplicação esteja aprovada para operação comercial.

## Progresso estimado

Escala qualitativa do escopo de um backend profissional em construção: **0%** ausente; **25%** estrutura inicial; **50%** fluxo principal existe com lacunas relevantes; **75%** núcleo funcional com evidência de testes, restando robustez/integração; **100%** critérios de conclusão definidos demonstrados. Os valores são estimativas por critérios, não cobertura de código nem cálculo por arquivos.

| Área | Estimativa | O que já existe | O que falta para avançar |
|---|---:|---|---|
| Fundamentos de domínio | **70%** | Produtos, categorias, fornecedor, lote, entradas, vendas e perdas; custo histórico por lote. | Fechar ciclos de fornecedor, ajuste e política multi-lote; limites temporais/numerais formalizados. |
| Modelagem OO | **65%** | Construtores controlados, fábricas, reconstituição, interfaces de leitura, invariantes e `BigDecimal`. | Imutabilidade coerente, validação uniforme e contratos que não exponham JDBC. |
| Arquitetura | **55%** | Domínio/serviços/contratos/infra separados em pacotes; composição explícita. | Remover dependência JDBC dos contratos centrais, cobrir casos de uso pendentes e definir fronteiras de aplicação. |
| JDBC | **70%** | SQL preparado, mapeamento, repos para seis áreas e integração real. | Cobrir todos os métodos/repos, configuração por ambiente, política de erros e eventual pool após necessidade. |
| SQL e banco | **65%** | PKs, FKs, unicidade, checks e tipos decimais; schema real validado em testes. | Setup/migrações repetíveis, cardinalidade do ENTRY e validação de concorrência/índices por consultas reais. |
| Transações e consistência | **75%** | Conexão compartilhada, commit/rollback e evidência DB para entrada, venda e perda. | Testes concorrentes, retries/isolamento, falha ambígua após commit e operações de histórico/ajuste. |
| Testes | **70%** | 108 passam; unitários e integração MariaDB com rollback/constraints. | Coverage de métodos ausentes, testes de concorrência, isolamento do DB, relatório cobertura e CI reproduzível. |
| Prontidão para estudar HTTP | **40%** | Casos de uso já existem e contratos de serviço podem ser chamados por adaptador. | Consolidar contratos de aplicação, erro estável, configuração e teste; pode começar estudo conceitual, não exposição real. |
| Prontidão para frontend | **15%** | Nada de frontend; domínio fornece conceitos base para futuro contrato. | API estável, DTOs, autenticação, CORS, OpenAPI, ambiente, deploy e contrato testado. |
| **Progresso geral** | **55%** | Núcleo educacional demonstrável e transações centrais exercitadas. | Sistema ainda sem ciclo operacional completo, HTTP, segurança, frontend ou entrega/execução produtiva. |

## Roadmap técnico por dependência

1. **Fechar requisitos e configuração do núcleo:** estabelecer setup de schema repetível com ordem/dependências inequívocas; remover credenciais de classpath; tornar ambiente de teste seguro e explícito.
2. **Completar fornecedor e operações de estoque:** persistir atualização/inativação de fornecedor, impedir nova entrada inativa, criar consulta de histórico e decidir ajuste e política de venda multi-lote (por exemplo FEFO) antes de codificar.
3. **Firmar SQL/JDBC:** constraints compatíveis com invariantes (inclusive cardinalidade de ENTRY quando domínio permitir), testes de cada repository e comportamento de deleção/consulta documentado.
4. **Provar consistência concorrente:** duas vendas simultâneas, saldo insuficiente sob corrida, isolamento e recuperação de falhas; observar o resultado no banco antes/depois.
5. **Consolidar camada de aplicação:** casos de uso independentes de CLI/JDBC, contratos de entrada/saída, erros previsíveis, logs e configuração por ambiente. Isso evita transportar detalhes de console ou banco para HTTP.
6. **Estudar HTTP puro:** request/response, métodos/headers/status, serialização JSON, validação, segurança básica e testes de endpoint com adaptadores finos. O JDK oferece `HttpServer`, adequado para aprendizagem; construir uma API real somente após contratos de aplicação estáveis.
7. **Evoluir para Spring Boot:** recriar adaptadores HTTP/configuração/DI comparando o que o framework automatiza; não migrar regras de domínio para anotações sem necessidade.
8. **Segurança de API:** modelar identidade, autenticação, autorização por função, armazenamento seguro de senha, CORS e erros sem vazamento antes de qualquer uso por frontend.
9. **DTOs e validação HTTP:** separar payloads de API das entidades e dos DTOs internos; validar formato, tamanho e semântica sem duplicar regras do domínio.
10. **Erros globais e OpenAPI:** mapear erros de aplicação para respostas HTTP consistentes e documentar contratos verificáveis.
11. **Frontend independente:** iniciar após endpoints, autenticação, CORS, OpenAPI e ambientes locais estarem estáveis; automatizar testes do contrato e fluxo principal.
12. **Entrega profissional:** CI, migrations, observabilidade, backups, configuração de deploy e critérios de release. Só então avaliar prontidão operacional.

É possível estudar HTTP em paralelo em um exercício isolado. A recomendação é não integrar HTTP ao MarketControl nem iniciar frontend antes das etapas 1–5 e de uma decisão de segurança.

## Contribuição e limites atuais

Este repositório é individual e educacional. Não há licença declarada, política de contribuição, pipeline CI, guia de release ou ambiente de implantação versionado. Mudanças de domínio devem respeitar as decisões e requisitos registrados em `docs/requirements.md`, atualizando a documentação quando o comportamento real mudar.

## Licença

Nenhuma licença foi declarada. Não presuma permissão para redistribuição ou uso externo até que uma licença seja escolhida.
