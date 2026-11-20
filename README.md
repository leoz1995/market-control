# MarketControl

API HTTP educacional para controle de produtos, categorias, fornecedores, lotes e movimentos de estoque. O backend usa Java 25, `jdk.httpserver`, Jackson, JDBC e MariaDB, sem Spring ou ORM. Os endpoints estão sendo construídos iterativamente com auxílio de IA; as decisões de domínio e a revisão final são responsabilidade do autor.

## Requisitos e configuração

- JDK 25 e Maven.
- MariaDB com o esquema e as migrations descritos em [docs/requirements.md](docs/requirements.md).
- `src/main/resources/database.properties` configurado para a conta de aplicação com privilégios mínimos. Não execute a aplicação como root do banco.

Copie `src/main/resources/database.properties.example` para `src/main/resources/database.properties` e configure `db.url`, `db.user` e `db.password`. O arquivo local é ignorado pelo Git. A conta `marketcontrol_app` deve manter os privilégios definidos pela migration 08, incluindo a criação de lotes pela procedure atômica. Não armazene credenciais administrativas nesse arquivo.

## Executar

Inicie `marketcontrol.Program` pela IDE com as dependências Maven no classpath. Por padrão, o servidor escuta em `127.0.0.1:8080` e permanece ativo até o shutdown. Configure `-Dmarketcontrol.http.host`, `-Dmarketcontrol.http.port` e `-Dmarketcontrol.http.cors.allowed-origin` para ajustar o bind, a porta e a única origem CORS permitida. CORS não autentica nem autoriza chamadas.

## Endpoints

As rotas usam JSON UTF-8. Contratos e exemplos estão em [docs/http-api.md](docs/http-api.md).

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/health` | Liveness do processo HTTP |
| GET, POST | `/api/products` | Lista ou cadastra produtos |
| GET | `/api/products/summary` | Lista produtos com saldo dos lotes não vencidos e contagem de lotes |
| GET, PUT | `/api/products/{id}` | Consulta ou edita produto |
| GET | `/api/products/barcode/{code}` | Busca produto pelo código de barras |
| GET | `/api/products/{id}/lots` | Lista lotes de um produto |
| GET | `/api/products/{id}/stock` | Consulta o saldo disponível de um produto |
| GET, POST | `/api/categories` | Lista ou cadastra categorias |
| PUT | `/api/categories/{id}` | Edita categoria |
| POST | `/api/suppliers` | Cadastra fornecedor |
| GET, PUT | `/api/suppliers/{id}` | Consulta ou atualiza fornecedor |
| POST | `/api/suppliers/{id}/deactivation` | Inativa fornecedor preservando histórico |
| POST | `/api/lots` | Cria lote com entrada inicial atômica |
| GET | `/api/lots/{id}` | Consulta lote |
| POST | `/api/lots/{id}/sales` | Registra venda |
| POST | `/api/lots/{id}/losses` | Registra perda |

Exemplo:

```bash
curl -i http://127.0.0.1:8080/api/products
```

Requests de escrita devem enviar `Content-Type: application/json`. Criações retornam `201 Created` e `Location`; vendas e perdas retornam `204 No Content`. Erros têm formato JSON seguro, request ID e status HTTP correspondente. Detalhes JDBC e stack traces ficam apenas nos logs.

## Arquitetura e segurança

`Program` compõe repositórios e serviços; `HttpServerApplication` administra o ciclo de vida do servidor; `ApiRouter` direciona as chamadas aos handlers por recurso. Handlers convertem HTTP/JSON em chamadas de serviço e DTOs de resposta. `ErrorHandlingHandler` centraliza a tradução de exceções.

O servidor usa virtual threads por request. Corpos JSON são limitados a 1 MiB, e o parser limita profundidade e tamanho dos tokens. O bind padrão em loopback evita exposição acidental na rede. A API ainda não implementa autenticação ou autorização; mantenha o serviço restrito ao ambiente local.

## Testes

```bash
mvn clean test -Dstyle.color=never
```

Testes HTTP usam porta efêmera e repositórios em memória. Testes JDBC e de concorrência dependem do MariaDB de teste. Se a limpeza de fixtures precisar de privilégios administrativos, passe as credenciais como propriedades JVM (`-Ddb.admin.user=... -Ddb.admin.password=...`), sem colocá-las em arquivos versionados ou no classpath da aplicação. A suíte grava fixtures e alguns casos pressupõem tabelas vazias; nunca aponte os testes para dados que deseja preservar.

## Limitações atuais

- Não há autenticação/autorização, paginação, consulta de histórico de movimentos, rate limiting, TLS, OpenAPI ou deploy automatizado.
- A health route verifica liveness HTTP, não prontidão do banco.
- O resumo de estoque contabiliza quantidades por unidade no contrato; não há regra de estoque mínimo.
- A resposta de lote não inclui fornecedor nem custos; não existe rota de leitura de histórico de custos.
- Não há venda distribuída entre lotes nem política FEFO.

Esses limites tornam o estado atual adequado para desenvolvimento e estudo, não para exposição pública ou operação comercial. As próximas etapas incluem notificações, modelagem manual das entidades de autenticação/autorização e refinamento incremental da API.
