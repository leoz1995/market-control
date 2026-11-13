# Progresso do Market Control

Registro do estado observado no repositório em novembro de 2026. Atualize este documento conforme novas funcionalidades e validações forem concluídas.

## Implementado

- Modelos `Category`, `Product` e `Lot`, com validações iniciais e contratos de leitura.
- Unidade de medida obrigatória no produto (`UNIT`, `KILOGRAM`, `GRAM`, `LITER` e `MILLILITER`).
- Regras de normalização de nome, código de barras e descrição; rejeição de código de barras repetido.
- Serviços para cadastro/edição de categorias e produtos e operações básicas de lotes.
- Contratos de repositório e persistência JDBC/MariaDB para categorias, produtos e lotes.
- Aplicação de terminal com cadastro e edição de produtos, categorias, lotes e consultas básicas de estoque.
- Scripts de tabelas em `database/` e exemplo de configuração em `src/main/resources/database.properties.example`.
- Testes unitários dos modelos e testes de integração JDBC para serviços de categoria, produto e lote, além da conexão.

## Validação mais recente

Com JDK 25, Maven, MariaDB ativo, banco e tabelas preparados e `src/main/resources/database.properties` configurado:

```bash
mvn test
```

A execução validada passou com 47 testes, sem falhas ou erros. Os testes JDBC precisam de acesso de rede ao banco; quando o ambiente os executar em sandbox sem acesso ao MariaDB, a conexão falhará por restrição do ambiente.

## Aprendizados exercitados

- Separação entre domínio, serviços, contratos de repositório e infraestrutura JDBC.
- Uso de `BigDecimal` para quantidades e `LocalDate` para datas de validade.
- Normalização antes da verificação de duplicidade de código de barras.
- Testes de regras de domínio separados de testes de integração com banco.
- Uso de chaves estrangeiras para preservar a relação de categorias, produtos e lotes.

## Pontos que ainda precisam de trabalho

- Fornecedores e identificação de origem do lote.
- Estoque mínimo por produto e alertas de reposição.
- Movimentações persistidas e auditáveis para entradas, saídas, perdas e ajustes.
- Saída que consome vários lotes com política de seleção (por exemplo, FEFO).
- Garantia transacional e testes de rollback para operações compostas.
- Consultas de vencidos/próximos do vencimento e regras de produto/categoria inativos.
- Decisões de autenticação, usuários e permissões.
- API HTTP, caso continue no escopo do projeto.
- Ampliar cobertura dos fluxos de terminal e documentar decisões de negócio pendentes.

## Próxima etapa sugerida

Definir o modelo de movimentação e suas regras de atomicidade antes de adicionar mais operações de estoque. Registrar o movimento e atualizar os saldos na mesma transação; validar o rollback com testes JDBC.

Consulte o [plano de requisitos](requirements.md) para o estado detalhado de cada requisito.
