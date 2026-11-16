# MarketControl

Aplicação Java de controle de estoque para pequenos mercados, com persistência JDBC em MariaDB. O projeto está em desenvolvimento: oferece operações de cadastro e movimentação no domínio e um terminal para parte dos fluxos, mas ainda não é um sistema operacional completo.

## O que é o projeto?

O MarketControl organiza produtos por categoria, acompanha lotes e quantidades disponíveis e registra entradas, vendas e perdas. É um projeto em evolução para estudar modelagem de domínio, serviços, persistência relacional e transações usando Java sem frameworks de aplicação.

## Problema e escopo

O sistema pretende apoiar o controle de produtos, categorias, lotes, saldo e validade, reduzindo a dependência de registros manuais dispersos. Fornecedores, custo de aquisição, alertas e fluxos operacionais completos ainda fazem parte de possíveis evoluções.

### Implementado

- Criar, localizar e editar categorias.
- Cadastrar, listar, localizar por código de barras e editar produtos.
- Validar categorias ativas e manter códigos de barras normalizados e únicos.
- Criar e consultar lotes com datas de entrada e validade e quantidades decimais.
- Consultar saldo agregado em lotes não vencidos.
- Registrar entradas iniciais e adicionais, vendas e perdas por serviços de domínio.
- Gravar movimentos e seus itens associados em operações transacionais.
- Usar repositórios JDBC para categorias, produtos, lotes e movimentações.

### Ainda não implementado ou incompleto

- Fornecedores, custos por lote, usuários, autenticação e permissões.
- Alertas de validade próxima e estoque mínimo.
- Seleção/distribuição automática entre lotes (por exemplo, FEFO) e ajuste de estoque.
- Consulta do histórico de movimentações e fluxos de venda/perda/ajuste no terminal.
- API HTTP, interface web e integração com front-end.

## Tecnologias

- Java 25 (versão de compilação declarada no Maven).
- Apache Maven, com Maven Compiler Plugin 3.13.0 e Surefire 3.5.2.
- MariaDB; a instância usada nesta validação reportou 10.11.19.
- JDBC com MariaDB Connector/J 3.5.6.
- JUnit Jupiter 5.11.4.

O projeto não usa Spring, JPA ou Hibernate.

## Arquitetura e organização

O código separa modelos e contratos de repositório, serviços de aplicação e adaptadores JDBC. `JdbcTransactionManager` coordena transações explícitas nos fluxos que gravam múltiplos registros. Essa é uma organização em camadas simples, sem framework arquitetural externo.

```text
src/main/java/marketcontrol/
├── Program.java                         # interface de terminal
├── domain/model/                        # entidades, enums e interfaces de leitura
├── domain/repository/                   # contratos de repositório
├── dtos/                                # dados de entrada para edição
├── exception/                           # falhas de persistência e mapeamento
├── infrastructure/database/             # leitura de configuração e conexão JDBC
├── infrastructure/persistence/jdbc/     # repositórios e gerenciador transacional
├── service/                             # operações de aplicação
└── transaction/                         # contratos dos callbacks transacionais
src/test/java/                           # testes de domínio, serviço e integração JDBC
database/                                # scripts SQL numerados para criação do schema
docs/                                    # requisitos e notas de progresso existentes
```

## Pré-requisitos

- JDK 25.
- Maven (a validação usou Maven 3.8.7).
- MariaDB compatível com os scripts SQL; a instância validada foi MariaDB 10.11.19.

## Configuração e execução

1. Clone o repositório e entre na pasta do projeto.

   ```bash
   git clone <URL_DO_REPOSITORIO>
   cd marketcontrol
   ```

2. Crie o banco e um usuário MariaDB com permissão para criar/consultar/alterar registros no banco. Exemplo para criar o schema:

   ```sql
   CREATE DATABASE marketcontrol CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

3. Execute os scripts em ordem, após selecionar o banco `marketcontrol`:

   ```bash
   mariadb -u <USUARIO> -p marketcontrol < database/01_categories.sql
   mariadb -u <USUARIO> -p marketcontrol < database/02_products.sql
   mariadb -u <USUARIO> -p marketcontrol < database/03_lots.sql
   mariadb -u <USUARIO> -p marketcontrol < database/04_stock_movements.sql
   mariadb -u <USUARIO> -p marketcontrol < database/05_stock_movement_items.sql
   ```

4. Copie `src/main/resources/database.properties.example` para `src/main/resources/database.properties` e informe as credenciais locais:

   ```properties
   db.url=jdbc:mariadb://localhost:3306/marketcontrol
   db.user=USUARIO_LOCAL
   db.password=SENHA_LOCAL
   ```

   `DatabaseConnection` lê esse arquivo do classpath. A aplicação não lê variáveis de ambiente para essa configuração. Não versione o arquivo com credenciais.

5. Compile o projeto:

   ```bash
   mvn package
   ```

6. Execute `marketcontrol.Program` pela IDE, com as dependências Maven no classpath e o diretório de recursos incluído. O projeto não configura um plugin Maven para iniciar a aplicação via `mvn exec:java`.

## Configuração suportada

| Chave | Finalidade | Exemplo fictício | Obrigatória? |
|---|---|---|---|
| `db.url` | URL JDBC do banco MariaDB | `jdbc:mariadb://localhost:3306/marketcontrol` | Sim |
| `db.user` | Usuário do banco | `marketcontrol_local` | Sim |
| `db.password` | Senha do usuário do banco | `senha_local_de_exemplo` | Sim |

As três chaves são propriedades de `database.properties`, não variáveis de ambiente. O arquivo é carregado estaticamente por `DatabaseConnection`; não há fallback configurável implementado.

## Testes

Execute os testes:

```bash
mvn test
```

Execute uma compilação limpa seguida da suíte completa:

```bash
mvn clean test
```

`mvn test` compila e executa os testes sem limpar os artefatos existentes. `mvn clean test` remove os artefatos de `target`, recompila do zero e executa a mesma suíte.

Para executar uma classe específica, use o nome Maven/Surefire real, por exemplo:

```bash
mvn -Dtest=CategoryTest test
```

Os testes unitários de domínio não precisam do banco. Os testes de integração JDBC, incluindo os serviços e o gerenciador de transações, precisam de MariaDB acessível e do arquivo `database.properties` configurado, com o schema criado pelos scripts. Eles criam registros identificados para cada execução e removem seus próprios registros; use uma base dedicada a testes.

Na validação desta revisão, `mvn clean test` executou 145 testes: todos aprovados, sem falhas, erros ou testes ignorados.

## Limitações conhecidas

- **Técnicas:** a conexão é carregada de um arquivo de propriedades por um método estático; não há pool de conexões nem configuração por ambiente. O terminal não possui comando para todos os serviços disponíveis.
- **Funcionais:** não há API HTTP, interface gráfica, fornecedores, autenticação, alertas, ajuste de estoque ou seleção FEFO. A consulta do histórico também não está exposta como fluxo de uso.
- **Testes:** os testes JDBC exigem banco MariaDB previamente configurado. Os testes não simulam falhas de abertura/configuração JDBC nem falha de fechamento após commit; a cobertura desse limite foi registrada em `docs/meu_progresso.md`.
- **Evolução:** uma API, alertas e fluxos adicionais podem ser avaliados depois de as regras de domínio e o tratamento de erros serem consolidados.

## Contribuições

Relate problemas ou proponha melhorias pela área de Issues/Pull Requests do repositório, quando estiver publicada. Inclua contexto reproduzível e os testes relevantes. Não há processo de contribuição adicional definido neste momento.

## Licença

Este repositório não contém um arquivo de licença. Portanto, nenhuma licença de uso ou redistribuição é declarada.

## Documentação complementar

- [Requisitos e estado funcional](docs/requirements.md)
- [Progresso e evidências técnicas](docs/meu_progresso.md)
