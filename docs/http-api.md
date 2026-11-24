# Contratos HTTP do MarketControl

Base local padrão: `http://127.0.0.1:8080`. Todos os endpoints ficam sob `/api`; requests e responses JSON usam UTF-8. `health`, login, refresh e logout são públicos. As rotas de negócio exigem `Authorization: Bearer <accessToken>`; usuários administrativos só podem ser geridos por ADMIN.

## Rotas

| Método | Caminho | Sucesso |
|---|---|---|
| GET | `/api/health` | `200` JSON `{"status":"UP"}`; liveness HTTP, não verifica o banco. |
| GET | `/api/products` | `200` com array de produtos, inclusive `[]` quando vazio. |
| POST | `/api/products` | `201` com produto criado e header `Location`. |
| GET | `/api/products/summary` | `200` com cadastro, categoria, saldo de lotes não vencidos (`quantity`) e contagem de lotes (`lots`). |
| GET | `/api/products/{id}` | `200` ou `404`. |
| PUT | `/api/products/{id}` | `200` com produto atualizado; a unidade deve permanecer igual à atual. |
| GET | `/api/products/barcode/{code}` | `200` ou `404`; busca normaliza o código de barras. |
| GET | `/api/products/{id}/lots` | `200` com os lotes do produto. |
| GET | `/api/products/{id}/stock` | `200` com `{ "productId": "...", "totalAvailableQuantity": 12.000 }`. |
| GET | `/api/categories` | `200` com array de categorias. |
| POST | `/api/categories` | `201` com categoria criada e `Location`; `active` omitido assume `true`. |
| PUT | `/api/categories/{id}` | `200`; o request deve incluir `active`. |
| POST | `/api/suppliers` | `201` com fornecedor criado e `Location`. |
| GET | `/api/suppliers/{id}` | `200` ou `404`. |
| PUT | `/api/suppliers/{id}` | `200` com fornecedor atualizado. |
| POST | `/api/suppliers/{id}/deactivation` | `200` com fornecedor inativo. |
| POST | `/api/lots` | `201` com lote criado e `Location`; grava ENTRY inicial e custo na mesma transação. |
| GET | `/api/lots/{id}` | `200` ou `404`. |
| POST | `/api/lots/{id}/sales` | `204`; JSON `{ "quantity": 2.000 }`. |
| POST | `/api/lots/{id}/losses` | `204`; JSON `{ "quantity": 1.000, "cause": "Produto danificado" }`. |
| POST | `/api/auth/login` | `200` com access token de 15 min, refresh token e usuário. |
| POST | `/api/auth/refresh` | `200`; troca refresh token por um novo par de tokens. |
| POST | `/api/auth/logout` | `204`; body `{ "refreshToken": "..." }`, revoga a sessão. |
| GET | `/api/users` | `200` com contas sem campos de senha; somente ADMIN. |
| POST | `/api/users` | `201`; cria usuário com `{ "name", "email", "password", "role" }`; papel omitido cria conta INACTIVE. |
| PUT | `/api/users/{id}/role` | `200`; body `{ "role": "ADMIN" | "STOCK_OPERATOR" | "INACTIVE" }`. |
| POST | `/api/users/{id}/activation` | `200`; body `{ "role": "ADMIN" | "STOCK_OPERATOR" }`. |
| POST | `/api/users/{id}/deactivation` | `200`; desativa e revoga todas as sessões da conta. |

Os métodos implementados são `GET`, `POST` e `PUT`; não há `PATCH` ou `DELETE`. Fornecedores podem ser inativados pela rota de ação indicada acima. Não existe rota para listar todos os fornecedores nem para consultar histórico de movimentos. Operadores podem ler dados e registrar recebimentos/vendas/perdas; mutações de cadastro e rotas de usuários exigem ADMIN.

`{id}` é UUID. Unidade usa `UNIT`, `KILOGRAM`, `GRAM`, `LITER` ou `MILLILITER`. Quantidades e custos são decimais JSON e precisam respeitar as validações do domínio.

## Produtos e estoque

`GET /api/products` retorna objetos com `id`, `name`, `barCode`, `description`, `unit` e `category`. Exemplo:

```json
[
  {
    "id": "00000000-0000-4000-8000-000000000001",
    "name": "Arroz",
    "barCode": "7890000000000",
    "description": null,
    "unit": "KILOGRAM",
    "category": {
      "id": "00000000-0000-4000-8000-000000000002",
      "name": "Mercearia",
      "description": "Produtos básicos",
      "active": true
    }
  }
]
```

`GET /api/products/summary` retorna os mesmos dados cadastrais e também `quantity` e `lots`. `quantity` soma o saldo de lotes cuja validade não passou; `lots` conta os lotes associados ao produto, incluindo lotes vencidos. As quantidades mantêm a unidade do produto e não devem ser somadas entre unidades diferentes. Não há regra de estoque mínimo nem timestamp de atualização no contrato.

`GET /api/products/{id}/lots` e `GET /api/lots/{id}` retornam lote com `id`, `productId`, `entryDate`, `expirationDate`, `availableQuantity` e `expired`. O contrato não inclui fornecedor, custo unitário ou custo total. Embora a criação de lote receba `supplierId` e `unitCost`, não há rota de leitura que exponha esses dados ligados ao lote; também não há rota de consulta de ENTRY/custo histórico.

### Criar produto

```bash
curl -i -X POST http://127.0.0.1:8080/api/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Arroz","barCode":"7890000000000","description":null,"categoryId":"00000000-0000-4000-8000-000000000001","unit":"KILOGRAM"}'
```

### Criar categoria

```json
{"name":"Mercearia","description":"Produtos básicos","active":true}
```

### Criar fornecedor

```json
{"legalName":"Fornecedor Ltda","tradeName":"Fornecedor","document":"12345678000199","email":"contato@example.test","phone":"+55 11 4000-0000","contactName":"Contato"}
```

### Criar lote com entrada inicial

```json
{"productId":"00000000-0000-4000-8000-000000000001","supplierId":"00000000-0000-4000-8000-000000000002","entryDate":"2026-11-19","expirationDate":"2027-11-19","initialQuantity":20.000,"unitCost":8.50}
```

## Erros e segurança

Erros são JSON e carregam o mesmo identificador no corpo e no header `X-Request-ID`:

```json
{
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Produto não encontrado.",
  "path": "/api/products/00000000-0000-4000-8000-000000000001",
  "requestId": "00000000-0000-4000-8000-000000000003",
  "timestamp": "2026-11-19T12:00:00Z"
}
```

- `400 BAD_REQUEST` / `INVALID_JSON`: validação de request ou JSON inválido.
- `404 NOT_FOUND`: caminho ou recurso inexistente.
- `405 METHOD_NOT_ALLOWED`: método incompatível; confira `Allow`.
- `409 CONFLICT`: duplicidade ou regra de saldo/estado impede a operação.
- `413 PAYLOAD_TOO_LARGE`: corpo excede 1 MiB.
- `415 UNSUPPORTED_MEDIA_TYPE`: request de escrita não usa `application/json`.
- `500`: falha inesperada ou persistência; detalhes ficam no log correlacionado por request ID.
- `503`: falha transitória de conexão com banco reconhecida na cadeia de causa.

Respostas incluem `X-Content-Type-Options: nosniff` e `Cache-Control: no-store`. CORS aceita uma origem exata configurável por `-Dmarketcontrol.http.cors.allowed-origin`, além de `GET`, `POST`, `PUT`, `OPTIONS` e os headers `Content-Type` e `Authorization`. CORS não substitui autenticação/autorização. Access tokens expirados retornam `401 TOKEN_EXPIRED`; refresh tokens são rotacionados e guardados no banco apenas como hash.

O backend também oferece HTTPS com keystore externo. Use `MARKETCONTROL_REQUIRE_HTTPS=true` em deploy, defina `MARKETCONTROL_TLS_KEYSTORE` e `MARKETCONTROL_TLS_KEYSTORE_PASSWORD`, e configure `MARKETCONTROL_ENV=production` com `MARKETCONTROL_JWT_SECRET` de pelo menos 32 bytes fornecido por secret manager. O modo HTTP sem TLS só inicia no loopback.
