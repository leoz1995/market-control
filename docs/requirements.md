# MarketControl — requisitos e estado funcional

> Estado atualizado em 24/11/2026. Esta página descreve o código atual; itens marcados como ausentes continuam sendo trabalho futuro.

## Objetivo e arquitetura

MarketControl é uma API HTTP educacional para controle de estoque, escrita e compilada para Java 25, JDBC e MariaDB. Não há mais fluxo de terminal ou `Scanner`. `Program` compõe os repositórios/serviços e mantém o servidor HTTP ativo. O adaptador HTTP usa `jdk.httpserver`, handlers por recurso, DTOs de request/response, Jackson para JSON, tratamento central de erros e virtual threads.

```text
Program → HttpServerApplication → ApiRouter → resource handlers
                                      ↓
                                  services
                                      ↓
                     domain + repository contracts
                                      ↓
                             JDBC / MariaDB
```

As regras de negócio continuam nos serviços e domínio. Handlers traduzem HTTP/JSON; não executam SQL. `ErrorHandlingHandler` evita enviar exceções JDBC/stack traces ao consumidor e correlaciona erros com `X-Request-ID`.

## Matriz de funcionalidades

| Requisito | Estado |
|---|---|
| Listar/cadastrar/consultar/editar produtos | API `GET/POST /api/products`, `GET/PUT /api/products/{id}`. Busca adicional por barcode. |
| Consultar resumo agregado de estoque | API `GET /api/products/summary`; saldo inclui apenas lotes não vencidos e `lots` conta todos os lotes vinculados. |
| Listar/cadastrar/editar categorias | API `GET/POST /api/categories`, `PUT /api/categories/{id}`. |
| Cadastrar/consultar/atualizar/inativar fornecedor | API `POST /api/suppliers`, `GET/PUT /api/suppliers/{id}`, `POST /api/suppliers/{id}/deactivation`. Não há listagem porque o contrato de repositório atual não oferece consulta completa. |
| Criar lote com ENTRY inicial | API `POST /api/lots`; usa `LotService` e a procedure/grants existentes para atomicidade. |
| Consultar lote, lotes de produto e saldo | API `GET /api/lots/{id}`, `GET /api/products/{id}/lots` e `/stock`. |
| Registrar venda e perda | API `POST /api/lots/{id}/sales` e `/losses`; continua transacional e baseada em custo histórico. |
| Consultar histórico de movimentos | Não implementado; os movimentos são gravados, mas não há consulta de leitura. |
| Paginação | Não implementada. Listagens retornam todos os resultados. |
| Autenticação/autorização | Login com access JWT e refresh token rotativo persistido por hash; papéis `ADMIN`, `STOCK_OPERATOR` e `INACTIVE`; administração de usuários restrita a ADMIN. O servidor usa loopback por padrão. |
| Health | `GET /api/health` confirma liveness do processo HTTP; não verifica MariaDB. |
| Ajuste, FEFO e venda multi-lote | Não implementados. |

Contratos HTTP detalhados e exemplos estão em [http-api.md](http-api.md).

## Contratos e proteção HTTP

- Responses usam DTOs explícitos para não serializar diretamente entidades de domínio.
- Erros têm formato consistente com `status`, `error`, `message`, `path`, `requestId` e `timestamp`.
- Validação é `400`; ausência de recurso `404`; método inválido `405`; conflito de estado/duplicidade/saldo `409`; payload excedido `413`; tipo de mídia incompatível `415`; falha inesperada/persistência `500`; erro transitório de conexão `503`.
- Requisições de escrita exigem `application/json`; corpo limitado a 1 MiB; parser também restringe profundidade, comprimento de string e número.
- Respostas definem `X-Content-Type-Options: nosniff` e `Cache-Control: no-store`.
- CORS permite uma origem exata definida por `marketcontrol.http.cors.allowed-origin`, com métodos `GET`, `POST`, `PUT` e preflight `OPTIONS`. CORS é política de navegador, não mecanismo de autenticação.
- O servidor aceita conexões simultâneas em virtual threads; executor e servidor são encerrados juntos no shutdown.
- Inicialização usa host/porta configuráveis por `marketcontrol.http.host` e `marketcontrol.http.port`; o padrão é `127.0.0.1:8080`. A origem CORS usa `marketcontrol.http.cors.allowed-origin`.

Essas medidas protegem o limite HTTP local, mas não substituem rate limiting ou revisão de exposição em rede. A autenticação usa sessões persistidas e exige a migration 11; TLS pode ser habilitado pelo keystore externo. Não exponha a API sem configurar os segredos e certificados reais do ambiente.

## Regras de domínio e banco

- Produto pertence a categoria, tem barcode único, descrição opcional e unidade (`UNIT`, `KILOGRAM`, `GRAM`, `LITER`, `MILLILITER`).
- Categoria tem nome, descrição e estado ativo.
- Fornecedor usa documento empresarial de 14 dígitos; inativação preserva histórico. ENTRY exige fornecedor ativo.
- Um novo recebimento cria lote próprio. O custo original é registrado no item ENTRY e usado em EXIT/LOSS.
- Lote não pode ficar com saldo negativo. Venda bloqueia lote vencido e saldo insuficiente; perda pode consumir lote vencido se houver saldo.
- Lote + ENTRY + item são persistidos atomicamente via `create_lot_with_initial_entry`. A conta `marketcontrol_app` não tem INSERT direto em `lots`.
- A migration 07 protege a origem de custo ENTRY; a migration 08 cria a procedure e estabelece grants mínimos. A aplicação deve conectar com `marketcontrol_app`, nunca como root.

Bootstrap vazio: execute `database/01_categories.sql` a `06_stock_movement_items.sql` em ordem; aplique migrations 07 e 08, depois `09_users.sql` e `11_user_sessions.sql` com DBA. `10_auth_demo_accounts.sql` é opcional e só deve ser usado em banco local de demonstração.

## Build, execução e testes

- Maven compila com `maven.compiler.release=25`.
- Configuração de banco vem de `src/main/resources/database.properties` no classpath. Use arquivo local não versionado e ambiente dedicado de teste.
- Execute `marketcontrol.Program` pela IDE com as dependências Maven no classpath. O processo permanece ativo até shutdown.
- Suíte: `mvn clean test -Dstyle.color=never`. Testes HTTP devem usar porta efêmera/mocks; testes JDBC requerem MariaDB real e fixtures temporárias.
- Os testes de integração escrevem no banco e alguns pressupõem tabelas vazias. Nunca use a base de produção.

## Limitações e próximos passos

- Ainda faltam paginação, consulta de histórico de movimentos, idempotency keys, rate limiting, OpenAPI, métricas e deploy automatizado.
- `GET /api/health` verifica liveness HTTP, não prontidão do MariaDB.
- O resumo de produtos retorna quantidade por unidade, mas não existe uma regra de estoque mínimo.
- As respostas de lote não incluem fornecedor ou custos, e não há consulta de ENTRY/custo histórico.
- Não há venda distribuída entre lotes nem política FEFO.

Antes de exposição pública, configure TLS com certificado confiável, `MARKETCONTROL_REQUIRE_HTTPS=true`, segredo JWT por secret manager e origem CORS do frontend.

## Desenvolvimento assistido por IA

Os endpoints são construídos iterativamente com auxílio de IA e revisados em relação às regras de domínio e aos testes. As decisões de escopo e a revisão final permanecem sob responsabilidade do autor. A autenticação e autorização atuais usam sessões persistidas, papéis fixos e operações administrativas protegidas.

Os contratos JDBC transacionais ainda recebem `java.sql.Connection`, e a conexão usa `DriverManager` sem pool. Não há métricas, tracing, backup ou deploy automatizado. A API atual é uma base de desenvolvimento educacional, não um serviço pronto para uso público.
