# MarketControl — requisitos, estado funcional e roadmap

**Responsável e autor das decisões de implementação:** Leonardo Alves
**Revisão:** 15 de novembro de 2026
**Estado:** projeto Java puro em desenvolvimento, com domínio, serviços, terminal, MariaDB/JDBC e movimentos parciais. Não existe API HTTP nem front-end Angular.

Este documento separa três coisas: comportamento que existe no código, comportamento confirmado por teste e objetivo ainda planejado. A presença de uma classe ou método não basta para marcar um requisito como concluído. Na colaboração atual, a IA atualiza documentação e testes; o código de produção é desenvolvido e alterado por Leonardo Alves.

## 1. Objetivo e visão de longo prazo

Construir um sistema de estoque para mercado que controle produtos, categorias, lotes, validade, quantidades e histórico de movimentações, com operações seguras e verificáveis.

O caminho de aprendizagem decidido é:

1. Consolidar o núcleo em **Java puro**: domínio, serviços, JDBC/MariaDB e terminal.
2. Expor os casos de uso por **HTTP em Java puro**, sem antecipar Spring.
3. Construir um front-end **Angular** para consumir a API. O plano de produto é que essa etapa tenha geração/apoio de IA em alto grau (planejada como 100% IA); ainda não foi iniciada. A autorização atual desta colaboração restringe a IA a testes e documentação, então Leonardo deverá decidir explicitamente o processo de geração do front-end quando essa fase começar.
4. Na **versão 2**, migrar o backend para Spring Boot como estudo comparativo, após conhecer o fluxo Java puro até HTTP.

Não introduzir Spring Boot, Spring MVC, JPA ou Hibernate na etapa atual.

## 2. Usuários e fronteiras

Os perfis futuros são administrador e operador de estoque. Cadastro, autenticação e permissões ainda não existem no código. A API futura deve receber apenas dados necessários, validar a entrada, não expor detalhes internos e manter regras de negócio fora da camada HTTP.

## 3. Estado dos requisitos funcionais

**Implementado** significa que o comportamento foi identificado no código e está coberto por evidência adequada. **Parcial** significa que existe uma parte do fluxo, faltam requisitos ou há falha conhecida. **Planejado** significa que ainda não existe implementação identificada.

| ID | Requisito | Estado atual observado |
|---|---|---|
| RF01 | Cadastrar produtos | Parcial: domínio, serviço, JDBC e fluxo de terminal existem; faltam decisões de catálogo e validação completa de ponta a ponta. |
| RF02 | Editar produtos | Parcial: serviço e repositório permitem edição; fluxo de terminal existe; ampliar casos de fronteira e validações. |
| RF03 | Ativar/desativar produto | Planejado: `Product` não possui estado ativo. |
| RF04 | Cadastrar e editar categorias | Parcial: domínio, serviço, JDBC e interface de terminal para cadastro existem; ampliar operações de terminal e regras de categoria inativa. |
| RF05 | Cadastrar fornecedores | Planejado: não há modelo, serviço, tabela nem relação com lote. |
| RF06 | Registrar entrada inicial de mercadoria | Parcial: `LotService.save` salva lote e movimento/item `ENTRY` na mesma transação. O teste JDBC atualizado confirma que o saldo inicial não é contado duas vezes; rollback por falha ao gravar o movimento ainda merece teste de integração dedicado. |
| RF07 | Cadastrar lotes e validade | Parcial: lote JDBC, datas e quantidades existem. O teste de criação confirma saldo consistente e reconstituição; ampliar casos de falha e entrada adicional. |
| RF08 | Registrar venda/saída | Parcial: `StockMovementService.registerSale` opera em um lote por chamada e atualiza saldo/movimento sob transação. Faltam seleção e distribuição entre lotes e fluxo no terminal. |
| RF09 | Consultar estoque disponível | Parcial: `JdbcLotRepository` agrega saldo de lotes com validade hoje ou futura; validação do saldo depende de lotes íntegros. |
| RF10 | Evitar venda de lote vencido | Parcial: `Lot.isExpired` identifica validade anterior a hoje e o SQL de venda exige validade não vencida. Ampliar testes de limites e regra de timezone/data. |
| RF11 | Alertar sobre validade próxima | Planejado: não há consulta, política configurada nem alerta. |
| RF12 | Consultar histórico de entradas/saídas/perdas/ajustes | Parcial: modelo e gravação JDBC de movimento/item existem; repositório de leitura, serviço de consulta e interface não foram identificados. |
| RF13 | Alertar estoque mínimo | Planejado: `Product` não contém estoque mínimo nem existe consulta/alerta. |
| RF14 | Registrar perda | Parcial: `StockMovementService.registerLoss` atualiza um lote e grava movimento transacional; falta definir elegibilidade da perda e fluxo de terminal. |
| RF15 | Consultar produtos por categoria | Planejado: produtos referenciam categoria e são listados, mas não há busca dedicada por categoria. |
| RF16 | Cadastrar usuários | Planejado. |
| RF17 | Autenticar usuários | Planejado para discussão na v2; nenhuma autenticação existe. |
| RF18 | Consultar usuários | Planejado. |
| RF19 | Permissões por perfil | Planejado; depende de identidade e autenticação. |
| RF20 | Corrigir/ajustar estoque com justificativa | Planejado: `MovementType.ADJUSTMENT` existe, mas não há caso de uso de ajuste identificado. |
| RF21 | Venda consumir múltiplos lotes | Planejado: atualmente a operação recebe um `lotId`; definir FEFO ou outra política e desempate. |
| RF22 | Expor casos de uso por HTTP | Planejado: nenhum servidor/endpoint HTTP existe. A primeira versão deve ser Java puro. |
| RF23 | Cliente web Angular | Planejado após API HTTP; ainda sem projeto Angular ou contrato de integração. |
| RF24 | Backend Spring Boot | Planejado exclusivamente para a versão 2, após a etapa Java puro. |

## 4. Requisitos não funcionais e princípios

- **RNF01 — aprendizagem:** priorizar Java puro e entender as responsabilidades antes de adotar framework.
- **RNF02 — estoque:** impedir saldo negativo e garantir consistência entre lotes, movimentos e itens.
- **RNF03 — números:** quantidades fracionárias usam `BigDecimal`; definir escala, arredondamento e relação com unidade comercial.
- **RNF04 — datas:** usar `LocalDate` para entrada/validade e `LocalDateTime` para movimento; definir relógio e limites de validade para testes previsíveis.
- **RNF05 — atomicidade:** quando saldo e histórico mudarem juntos, ambas as alterações devem commitar ou nenhuma alteração persistida deve permanecer.
- **RNF06 — testes:** manter unitários isolados e integração JDBC identificada como tal; não chamar fake de persistência real.
- **RNF07 — persistência:** serviços dependem de contratos; JDBC não deve deslocar regra de domínio para SQL sem decisão explícita.
- **RNF08 — HTTP:** a futura camada HTTP deve traduzir contratos de casos de uso, validar dados de entrada e mapear erros para respostas seguras.
- **RNF09 — segurança:** credenciais não ficam no repositório; erros HTTP não expõem SQL, stack traces ou segredos.
- **RNF10 — propriedade do projeto:** Leonardo Alves decide as regras e desenvolve código de produção. IA nesta etapa pode atualizar testes e documentação conforme autorização.

## 5. Regras de negócio: estado e decisões

### 5.1 Categorias e produtos

Regras observadas: categoria tem nome não vazio e estado ativo; produto exige nome, barcode, categoria ativa e unidade; nome/descrição/barcode têm normalização; barcode é consultado para evitar duplicidade no serviço.

Pendências: política única de comparação de nomes de categoria; possibilidade de editar produto para categoria inativa; ativação de produto; fornecedores; campos de estoque mínimo e custo.

### 5.2 Unidades e quantidades

O enum atual define `UNIT`, `KILOGRAM`, `GRAM`, `LITER` e `MILLILITER`, com códigos 1 a 5 persistidos no banco. A unidade pertence ao produto e lotes usam quantidades expressas nessa unidade.

Decidir e documentar: se conversões (por exemplo, kg ↔ g) serão aceitas; escala máxima; se produtos empacotados usam unidade de embalagem ou conteúdo líquido; regra para estoque misto; arredondamento em fracionamento.

### 5.3 Lotes e saldo

Regras atualmente implementadas no modelo: produto e datas obrigatórios; quantidade inicial maior que zero; entrada não pode ser futura; validade não pode anteceder entrada; reconstituição exige saldo não negativo e não acima do inicial; lote é vencido se validade for anterior a hoje.

**Duplicação inicial corrigida no diff em avaliação:** `Lot.create` inicia saldo disponível igual ao inicial. `LotService.save` persiste o lote e chama `registerInitialEntry`, que registra o cabeçalho e o item `ENTRY` sem atualizar novamente o saldo. O método e SQL de soma foram renomeados para representar entrada adicional (`registerAdditionalEntry` / `increaseAvailableQuantityForAdditionalEntry`). O teste JDBC de criação foi ajustado para exigir saldo 10 para lote inicial de 10, movimento e item únicos com quantidade 10 e sucesso de `findById`.

Decisões pendentes: status “vence em breve” (proposta anterior: até sete dias, inclusive); tratamento físico de vencidos; registro de lote do fornecedor/fabricação/custo; quantidade zero em consulta/alerta.

### 5.4 Movimentações e transações

`StockMovement` exige tipo, data/hora, descrição não vazia e pelo menos um item não nulo. `StockMovementItem` exige lote e quantidade maior que zero. `JdbcStockMovementRepository` grava cabeçalho e itens na conexão recebida.

O `StockMovementService` implementa entrada, venda e perda. Venda/perda envolvem atualização condicional de saldo e gravação do movimento dentro de `TransactionManager.execute`. Entrada recebe `Connection`; no caminho `LotService.save`, o chamador abre a transação para gravar lote e entrada em conjunto.

Ainda precisa ser definido/testado: quais lotes podem receber perda (somente vencido ou avaria também); ajustes e reversões; histórico consultável; atomicidade sob falha de item parcial; concorrência; comportamento de commit indeterminado e falha de rollback. O rollback da conexão não restaura atributos de entidades Java já mutadas; o modelo atual de `Lot` é imutável.

### 5.5 Política futura de saída

Uma saída pode ser requerida em quantidade maior do que um lote possui. A política FEFO (vence primeiro, sai primeiro) é uma proposta ainda não implementada. Antes de adotar, decidir se saldo vencido é excluído, desempate, precisão, concorrência, bloqueio/atualização e semântica quando o conjunto elegível não cobre a venda.

## 6. Arquitetura observada

```text
Program (terminal)
    └── Services (casos de uso)
          ├── Domain models e enums
          ├── Repositórios (contratos)
          │     └── Implementações JDBC ── MariaDB
          └── TransactionManager ───────── Connection / commit / rollback
```

| Parte | Estado atual |
|---|---|
| `Program` | Menu de terminal; composição de serviços estática; sem API HTTP. |
| `domain/model` | Categoria, produto, lote, movimento e item de movimento. |
| `service` | Serviços de categoria, produto, lote e movimento. |
| `domain/repository` | Contratos JDBC-facing de categoria, produto, lote e movimento. Alguns contratos expõem `Connection` para operações transacionais. |
| `infrastructure/persistence/jdbc` | Implementações MariaDB e gerenciador JDBC. |
| `transaction` | `TransactionManager` e callback `TransactionWork`. |
| `database/` | Cinco scripts SQL em ordem de dependência; scripts não criam banco nem usuário. |
| `src/test` | JUnit 5; domínio unitário, serviços com fakes e integração com MariaDB. |

## 7. Testes e evidência atual

Última execução registrada em 15/11/2026: `mvn test` — **77 executados, 77 aprovados, nenhuma falha/erro/ignorado**. O teste de integração `LotServiceTest.lotCreationPersistsOnlyInitialQuantityAndEntryMovement` passou no MariaDB local e valida o saldo, movimento, item e reconstituição do lote. A evidência confirma a correção no caminho coberto; ainda falta teste JDBC de entrada adicional e rollback da entrada inicial.

Distribuição por classe, mantida em [meu_progresso.md](meu_progresso.md). Testes com fakes verificam interação/decisões do serviço, não persistência. Testes JDBC usam conexão MariaDB e queries de verificação; cleanup remove apenas dados de teste associados à categoria do caso.

Não há ferramenta de cobertura configurada, portanto não há porcentagem de cobertura reportada. Lacunas: falhas de commit/rollback e exceção suprimida, concorrência, falha em inserção de item após cabeçalho, leitura do histórico, comportamento JDBC de erro, perda no banco, entrada com falha e fluxo terminal completo.

## 8. Roadmap e estimativa percentual

Os percentuais são estimativas qualitativas frente aos critérios desta seção e ao roadmap. Não equivalem a cobertura e não são calculados pelo número de arquivos.

| Etapa | Avanço estimado | Critério de conclusão |
|---|---:|---|
| Núcleo Java puro: domínio e serviços | **65%** | Regras aceitas, invariantes e fluxos de estoque concluídos, incluindo entrada correta, perda/ajuste e saída multi-lote testadas. |
| MariaDB/JDBC e transações | **70%** | Persistência completa, consistência/integridade, falhas de SQL, commit/rollback e concorrência comprovados por integração. |
| Terminal | **45%** | Operador percorre cadastro, entrada, venda, perda, ajuste e histórico com erros recuperáveis. |
| Testes/confiabilidade | **65%** | Suíte aprovada e integração repetível; permanecem cenários críticos a cobrir, como entrada adicional, concorrência e falhas de commit/rollback. |
| API HTTP Java puro | **0%** | Rotas e contratos documentados; validação, status e erros testados; sem dependência de Spring. |
| Front-end Angular (plano de IA) | **0%** | Aplicação Angular, integração com HTTP e testes de interface ainda não existem. |
| Spring Boot v2 | **0%** | Migração comparativa posterior à v1, protegida por testes de comportamento. |

Não há porcentagem agregada do projeto: etapas HTTP, Angular e Spring não começaram, e uma média ocultaria essa diferença.

## 9. Critérios de pronto por fase

### Fase 1 — Java puro + JDBC + HTTP

- Definir regras de negócio com Leonardo antes de assumir comportamento novo.
- Manter domínio e casos de uso desacoplados de terminal, HTTP e JDBC.
- Manter a criação inicial sem duplicação e provar em MariaDB que lote, saldo, cabeçalho e item correspondem; ampliar a evidência para rollback e entrada adicional.
- Manter todas as operações compostas atômicas; testar sucesso, falha de repositório, erro de SQL e estado depois da falha.
- Completar operação multi-lote segundo política escolhida e tratar concorrência.
- Implementar API HTTP Java puro sem framework; documentar endpoints, validações, erros e exemplos.
- Ter testes de unidade, integração JDBC e fronteira HTTP; manter suíte repetível.
- Demonstrar no terminal os casos definidos e conseguir explicar as decisões de arquitetura.

### Fase 1.5 — Angular

- Iniciar apenas com contratos HTTP estáveis e fluxo de produto aprovado.
- Planejar front-end Angular com a meta de produção 100% assistida/gerada por IA; a autorização de ferramentas e a revisão de Leonardo devem ser reavaliadas para essa fase, pois a colaboração atual está limitada a testes/documentação.
- Testar integração, validação, estados de carregamento/erro e compatibilidade com API.

### Fase 2 — Spring Boot

- Começar depois da v1 e do objetivo educacional Java puro + HTTP.
- Migrar em passos comparáveis, sem transformar a versão atual em Spring antes do aprendizado planejado.
- Preservar testes de comportamento para comparar soluções e compreender o que framework automatiza.

## 10. Questões para decisão do autor

1. Produtos pesados são vendidos em kg ou gramas? Qual escala decimal e política de arredondamento?
2. Como representar embalagem de conteúdo líquido versus venda por litro/ml?
3. Entrada de estoque cria um lote com saldo inicial ou cria lote zerado e aplica entrada? A regra precisa evitar duplicação.
4. Perda só se aplica a lote vencido ou também cobre avaria, roubo e descarte operacional?
5. Venda será em um lote ou pode distribuir entre vários? FEFO é a regra desejada?
6. Qual janela de validade próxima e qual comportamento de lotes vencidos?
7. Ajuste deve ser movimento de entrada/saída com justificativa ou tipo próprio `ADJUSTMENT`?
8. O histórico é imutável? Como corrigir/cancelar movimento?
9. Quais fluxos devem aparecer no menu antes da API?
10. Qual primeira versão do protocolo HTTP (rotas, autenticação, versionamento e formato de erro)?
11. Como Leonardo quer conduzir a etapa Angular planejada como 100% IA, mantendo decisão e revisão do produto?

## 11. Documentos relacionados

- [README e instruções do projeto](../README.md)
- [Progresso, testes e evidências](meu_progresso.md)
