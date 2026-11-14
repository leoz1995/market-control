# MarketControl

Sistema de controle de estoque para mercado, desenvolvido por **Leonardo Alves** como projeto de estudo de engenharia de software. A trilha prioriza Java puro, orientação a objetos, JDBC, MariaDB, testes e entendimento das decisões de arquitetura antes de introduzir frameworks.

## Visão e direção do projeto

O objetivo da primeira versão é levar o sistema em Java puro até uma API HTTP, mantendo domínio, serviços e persistência compreensíveis e testáveis. Depois dessa etapa está planejado um front-end Angular desenvolvido com apoio de IA. A versão 2 será a oportunidade de migrar o backend para Spring Boot e comparar as decisões tomadas no Java puro com as abstrações do framework.

**Responsabilidade pelo código:** Leonardo Alves desenvolve e decide as mudanças no código de produção. Nesta colaboração, a IA está autorizada a trabalhar em testes e documentação; não altera classes de produção, regras de negócio, build ou dependências.

## Estado atual observado

O projeto já usa Java 25, Maven, MariaDB e JDBC. Existem modelos e serviços para categorias, produtos, lotes e movimentações. Os repositórios JDBC persistem essas entidades; `JdbcTransactionManager` fornece transações usadas em operações de estoque.

O terminal (`Program`) permite cadastrar/listar/buscar/editar produtos, cadastrar categorias e cadastrar/consultar lotes. A criação de lote tenta registrar uma movimentação de entrada. O serviço de estoque também contém operações de entrada, venda e perda. Ainda não há menu de terminal para venda, perda, ajuste ou consulta de histórico.

**Falha observada nos testes atuais:** `LotService.save` persiste um lote com saldo disponível igual à quantidade inicial e, na mesma transação, `StockMovementService.registerEntry` incrementa novamente o saldo pela quantidade inicial. O banco fica com saldo duas vezes maior que o inicial; em seguida, `Lot.reconstitute` rejeita esse registro porque o saldo disponível supera o inicial. O teste de integração que verifica o saldo correto detecta essa falha. Nenhum código de produção foi alterado para corrigi-la.

## Funcionalidades e limites

### Presentes no código

- Cadastro, busca e edição de categorias.
- Cadastro, listagem, busca por código de barras e edição de produtos.
- Normalização de nome, descrição e código de barras; unicidade do código tratada pelo serviço.
- Unidade do produto: `UNIT`, `KILOGRAM`, `GRAM`, `LITER` e `MILLILITER`.
- Criação e consulta de lotes, com datas e quantidades decimais.
- Totalização do saldo em lotes não vencidos.
- Serviço para registrar entrada, venda e perda com movimento e item associado.
- Repositórios JDBC para categoria, produto, lote, movimentação e item de movimentação.
- Transações explícitas em operações de venda/perda e na criação de lote com entrada.

Existir uma classe ou método não significa que o fluxo esteja pronto: a entrada inicial apresenta a falha descrita acima. Consulte [requisitos e estado por funcionalidade](docs/requirements.md) e [progresso e evidências](docs/meu_progresso.md).

### Ainda não implementados ou incompletos

- Correção da duplicação do saldo inicial e validação integral da criação de lote com entrada.
- Consulta de histórico de movimentações.
- Ajuste de estoque e consumo de uma venda entre vários lotes (por exemplo, FEFO).
- Regras completas de validade próxima, estoque mínimo e alertas.
- Fornecedores, custo por lote, usuários, autenticação e permissões.
- Fluxos de venda/perda/ajuste no terminal.
- API HTTP em Java puro.
- Front-end Angular.
- Migração do backend para Spring Boot (v2, posterior à etapa Java puro + HTTP).

## Etapas e estimativas de avanço

As porcentagens são estimativas de engenharia de Leonardo Alves, não cobertura de código nem métrica automatizada. Elas comparam evidências existentes com os critérios de conclusão descritos em [meu_progresso.md](docs/meu_progresso.md). As etapas não formam uma média geral: etapas posteriores ainda não começaram.

| Etapa | Avanço estimado | Evidência principal / trabalho restante |
|---|---:|---|
| Domínio e serviços Java puro | 65% | Entidades, serviços e invariantes iniciais existem; entrada de lote tem inconsistência e faltam casos como FEFO, ajustes e fornecedor. |
| Persistência JDBC/MariaDB | 70% | Repositórios JDBC e transações existem; validar fluxos completos e corrigir/testar criação de lote com entrada. |
| Terminal e fluxo operacional | 45% | Cadastro e consulta básicos existem; faltam vendas, perdas, ajustes e histórico no menu. |
| Testes e confiabilidade do núcleo | 60% | Há testes de domínio, serviço e JDBC; execução atual: 76 aprovados e 1 falha conhecida. Há lacunas de concorrência e falhas de commit/rollback. |
| API HTTP em Java puro | 0% | Ainda não há servidor, endpoints, contratos HTTP ou testes da fronteira. |
| Front-end Angular com apoio de IA | 0% | Planejado após a API; ainda sem aplicação, integração ou testes de interface. |
| Backend Spring Boot v2 | 0% | Migração posterior ao aprendizado do caminho Java puro até HTTP; não iniciar antes dos critérios da v1. |

## Tecnologias atuais

- Java 25
- Maven
- MariaDB e MariaDB Connector/J 3.5.6
- JUnit Jupiter 5.11.4

Spring Boot, Spring MVC, JPA e Hibernate **não** fazem parte da implementação atual nem da etapa Java puro.

## Estrutura

```text
src/main/java/marketcontrol/
├── Program.java                         # interface atual de terminal
├── domain/model/                        # entidades e enums de domínio
├── domain/repository/                   # contratos de persistência
├── dtos/                                # dados de edição
├── exception/                           # exceções de persistência
├── infrastructure/database/             # conexão JDBC
├── infrastructure/persistence/jdbc/     # repositórios e transações JDBC
├── service/                             # casos de uso
└── transaction/                         # contrato de trabalho transacional
src/test/java/                           # unitários, fakes e integração JDBC
database/                                # scripts de schema MariaDB
docs/                                    # requisitos, roadmap e progresso
```

## Preparação local

Requer JDK 25, Maven e MariaDB. Crie o banco `marketcontrol`, execute os scripts SQL pela ordem numerada em `database/` e copie `src/main/resources/database.properties.example` para `src/main/resources/database.properties`.

```properties
db.url=jdbc:mariadb://localhost:3306/marketcontrol
db.user=SEU_USUARIO
db.password=SUA_SENHA
```

Use credenciais locais com permissões adequadas e não as versione. A conexão usada pela aplicação é carregada por `DatabaseConnection`.

## Testes

```bash
mvn test
```

Os testes de domínio e os testes com fakes não precisam de banco. Testes de serviço/JDBC e a verificação de conexão acessam o MariaDB configurado; os testes de integração criam dados com identificadores próprios e os removem no cleanup. Use uma base exclusiva de testes.

Na execução registrada em **14 de novembro de 2026**, a suíte compilou e executou 77 casos: **76 passaram e 1 falhou** em `LotServiceTest.lotCreationPersistsOnlyInitialQuantityAndEntryMovement`. A falha é a duplicação do saldo de entrada descrita acima. Reexecute após mudanças; este resultado é um registro, não uma garantia para execuções futuras.

## Executar o terminal

Compile com `mvn package` e execute `marketcontrol.Program` pela IDE, com `target/classes` e as dependências Maven no classpath. O projeto ainda não configura `exec-maven-plugin`, portanto `mvn exec:java` não é um comando disponível.

## Documentos

- [Requisitos, estado funcional e critérios](docs/requirements.md)
- [Progresso, evidências de teste e plano por etapa](docs/meu_progresso.md)
