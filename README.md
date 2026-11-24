# MarketControl

API HTTP educacional para controle de produtos, categorias, fornecedores, lotes e movimentos de estoque. O backend usa Java 25, `jdk.httpserver`, Jackson, JDBC e MariaDB, sem Spring ou ORM. Os endpoints estão sendo construídos iterativamente com auxílio de IA; as decisões de domínio e a revisão final são responsabilidade do autor.

## Requisitos e configuração

- JDK 25 e Maven.
- MariaDB com o esquema e as migrations descritos em [docs/requirements.md](docs/requirements.md).
- `src/main/resources/database.properties` configurado para a conta de aplicação com privilégios mínimos. Não execute a aplicação como root do banco.

Copie `src/main/resources/database.properties.example` para `src/main/resources/database.properties` e configure `db.url`, `db.user` e `db.password`. Para HTTP local, forneça também `MARKETCONTROL_JWT_SECRET` ou `jwt.secret` no arquivo local ignorado. Em deploy, `MARKETCONTROL_DB_URL`, `MARKETCONTROL_DB_USER` e `MARKETCONTROL_DB_PASSWORD` substituem as configurações de banco e podem ser usados sem incluir `database.properties` no artefato. A conta `marketcontrol_app` deve manter os privilégios definidos pelas migrations 08 e 11, incluindo a criação de lotes pela procedure atômica. Não armazene credenciais administrativas nesse arquivo.

Para popular contas locais de demonstração, aplique `database/09_users.sql` e depois `database/10_auth_demo_accounts.sql` com uma conta DBA. As três contas usam a senha `Teste@2026!`: `admin@marketcontrol.local` (ADMIN), `operador@marketcontrol.local` (STOCK_OPERATOR) e `inativo@marketcontrol.local` (INACTIVE). O seed não altera contas que já existam. Para sessões e administração de usuários, aplique também `database/11_user_sessions.sql` como DBA.

## Executar

Inicie `marketcontrol.Program` pela IDE com as dependências Maven no classpath. Por padrão, o servidor escuta em `127.0.0.1:8080` via HTTP para desenvolvimento local e permanece ativo até o shutdown. HTTPS é habilitado com um keystore PKCS12 externo definido por `MARKETCONTROL_TLS_KEYSTORE` e `MARKETCONTROL_TLS_KEYSTORE_PASSWORD`. `MARKETCONTROL_REQUIRE_HTTPS=true` faz o processo falhar ao iniciar se TLS não estiver configurado; `MARKETCONTROL_ENV=production` exige HTTPS e um segredo externo, e binds fora do loopback sempre exigem TLS. Defina `MARKETCONTROL_JWT_SECRET` com valor aleatório de ao menos 32 bytes, guardado em secret manager ou variável protegida, nunca em arquivos versionados. Use certificado emitido por autoridade confiável e sirva o frontend por HTTPS. O build Angular `--configuration production` usa `/api` na mesma origem; encaminhe essa rota pelo reverse proxy ao backend HTTPS. Configure `-Dmarketcontrol.http.host`, `-Dmarketcontrol.http.port` e `-Dmarketcontrol.http.cors.allowed-origin` para ajustar bind, porta e origem CORS. CORS não autentica nem autoriza chamadas.

## Endpoints

As rotas usam JSON UTF-8. Contratos e exemplos estão em [docs/http-api.md](docs/http-api.md).

O bootstrap vazio deve aplicar `database/09_users.sql`, depois `database/10_auth_demo_accounts.sql` (opcional, apenas contas locais) e `database/11_user_sessions.sql`, todos após as migrations 01–08 e com DBA quando indicado. O app precisa dos grants de usuário e sessão da migration 11.

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
| POST | `/api/auth/login` | Autentica e cria sessão com access e refresh tokens |
| POST | `/api/auth/refresh` | Rotaciona o refresh token e emite um novo access token |
| POST | `/api/auth/logout` | Revoga a sessão pelo refresh token |
| GET | `/api/users` | Lista usuários (ADMIN) |
| POST | `/api/users` | Cria usuário (ADMIN; papel opcional, padrão INACTIVE) |
| PUT | `/api/users/{id}/role` | Altera papel (ADMIN) |
| POST | `/api/users/{id}/activation` | Ativa conta com papel ADMIN ou STOCK_OPERATOR |
| POST | `/api/users/{id}/deactivation` | Desativa conta e revoga sessões (ADMIN) |

Exemplo:

O login retorna `accessToken`, `refreshToken`, `expiresInSeconds` e os dados do usuário. Envie `Authorization: Bearer <accessToken>` nas rotas protegidas; renove o access token com o refresh token antes ou depois dos 15 minutos. O logout aceita o refresh token e invalida a sessão no banco.

Requests de escrita devem enviar `Content-Type: application/json`. Criações retornam `201 Created` e `Location`; vendas e perdas retornam `204 No Content`. Erros têm formato JSON seguro, request ID e status HTTP correspondente. Detalhes JDBC e stack traces ficam apenas nos logs.

## Arquitetura e segurança

`Program` compõe repositórios e serviços; `HttpServerApplication` administra o ciclo de vida do servidor; `ApiRouter` direciona as chamadas aos handlers por recurso. Handlers convertem HTTP/JSON em chamadas de serviço e DTOs de resposta. `ErrorHandlingHandler` centraliza a tradução de exceções.

O servidor usa virtual threads por request. Corpos JSON são limitados a 1 MiB, e o parser limita profundidade e tamanho dos tokens. Rotas de negócio exigem access token; operadores podem consultar e movimentar estoque, enquanto operações administrativas e contas ficam restritas a ADMIN. Access tokens duram 15 minutos; refresh tokens aleatórios são guardados apenas como hash no banco, rotacionados a cada renovação e expiram após 30 dias. Logout, desativação e mudanças de papel invalidam o uso das sessões no servidor. O bind padrão em loopback mantém HTTP restrito ao desenvolvimento local.

## Testes

```bash
mvn clean test -Dstyle.color=never
```

Testes HTTP usam porta efêmera e repositórios em memória. Testes JDBC e de concorrência dependem do MariaDB de teste. Se a limpeza de fixtures precisar de privilégios administrativos, passe as credenciais como propriedades JVM (`-Ddb.admin.user=... -Ddb.admin.password=...`), sem colocá-las em arquivos versionados ou no classpath da aplicação. A suíte grava fixtures e alguns casos pressupõem tabelas vazias; nunca aponte os testes para dados que deseja preservar.

## Limitações atuais

- Ainda faltam rate limiting, OpenAPI, deploy automatizado, recuperação segura de senha e interface administrativa para gestão de usuários.
- A health route verifica liveness HTTP, não prontidão do banco.
- O resumo de estoque contabiliza quantidades por unidade no contrato; não há regra de estoque mínimo.
- A resposta de lote não inclui fornecedor nem custos; não existe rota de leitura de histórico de custos.
- Não há venda distribuída entre lotes nem política FEFO.

O modo HTTPS está implementado, mas operação pública depende de configurar certificado e segredos reais no ambiente de deploy. O build Angular de produção usa `/api` na mesma origem, então o reverse proxy deve servir o frontend em HTTPS e encaminhar `/api` ao backend.
