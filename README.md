# Market Control

Aplicação de terminal em Java para cadastro de categorias e produtos e controle inicial de lotes e quantidades em MariaDB. O projeto organiza regras de domínio, casos de uso e persistência JDBC em camadas separadas. Está em desenvolvimento; ainda não implementa todo o escopo de movimentações e histórico descrito em [requisitos](docs/requirements.md).

## Funcionalidades disponíveis

- Cadastrar e editar categorias, incluindo ativação/desativação.
- Cadastrar produtos com nome, código de barras, descrição, categoria e unidade de medida.
- Validar e normalizar nomes, descrições e códigos de barras; rejeitar código de barras duplicado.
- Listar produtos e pesquisar por código de barras.
- Cadastrar lotes com datas de entrada e validade e quantidades decimais.
- Consultar lotes e quantidade disponível total de um produto.
- Registrar saída de quantidade de um lote, impedindo saldo negativo e consumo de lote vencido.
- Persistir categorias, produtos e lotes em MariaDB pelos repositórios JDBC.

## Tecnologias

- Java 25
- Maven
- MariaDB e MariaDB Connector/J 3.5.6
- JUnit Jupiter 5.11.4

## Organização do projeto

```text
src/main/java/marketcontrol/
├── Program.java                         # interface de terminal
├── domain/model/                        # Category, Product, Lot e contratos de leitura
├── domain/repository/                   # contratos dos repositórios
├── dtos/                                # dados de edição do produto
├── exception/                           # exceções de persistência
├── infrastructure/database/             # conexão JDBC
├── infrastructure/persistence/jdbc/     # repositórios MariaDB
└── service/                             # casos de uso
src/test/java/                           # testes de domínio e integração JDBC
database/                                # scripts SQL das tabelas
```

Os serviços dependem das interfaces de repositório. As implementações em uso são `JdbcCategoryRepository`, `JdbcProductRepository` e `JdbcLotRepository`. A camada de domínio não abre conexões nem executa SQL.

## Requisitos para executar

- JDK 25 e Maven instalados.
- MariaDB ativo e acessível pela aplicação.
- Banco `marketcontrol` criado, com as tabelas definidas nos scripts SQL deste repositório.
- Arquivo de configuração JDBC no classpath.

### Configurar o MariaDB

Execute os scripts na ordem abaixo dentro do banco `marketcontrol`:

1. `database/01_categories.sql`
2. `database/02_products.sql`
3. `database/03_lots.sql`

Os scripts criam as tabelas, mas não criam o banco ou o usuário. Configure o banco e conceda ao usuário da aplicação permissões para consultar, inserir, atualizar e excluir dados usados pelos testes.

Crie `src/main/resources/database.properties` a partir de `src/main/resources/database.properties.example` e informe os valores do seu ambiente:

```properties
db.url=jdbc:mariadb://localhost:3306/marketcontrol
db.user=SEU_USUARIO
db.password=SUA_SENHA
```

O arquivo com credenciais locais está no `.gitignore`; não o inclua no repositório. `DatabaseConnection` carrega esse arquivo como recurso de classpath e usa `db.url`, `db.user` e `db.password` para abrir a conexão.

## Testes

Execute todos os testes com:

```bash
mvn test
```

Os testes de modelo exercitam regras de `Product` e `Lot` sem banco. Os testes de serviço, a verificação de conexão e os testes de lote usam MariaDB/JDBC e precisam do banco e das tabelas preparados. Os testes de integração criam dados identificados para cada execução e removem esses registros ao final.

## Executar a aplicação

Compile o projeto com `mvn package` e execute `marketcontrol.Program` pela IDE, configurando `target/classes` e as dependências Maven no classpath. O plugin `exec-maven-plugin` ainda não está configurado no `pom.xml`, portanto `mvn exec:java` não é atualmente um comando suportado.

## Limites atuais

O terminal implementa fluxos iniciais, mas o projeto ainda não oferece fornecedores, autenticação, permissões, histórico/auditoria de movimentações, saídas consolidadas entre múltiplos lotes, perdas, ajustes, alertas de validade próxima ou estoque mínimo. O detalhamento de requisitos e seu estado está em [docs/requirements.md](docs/requirements.md); o registro de desenvolvimento está em [docs/meu_progresso.md](docs/meu_progresso.md).
