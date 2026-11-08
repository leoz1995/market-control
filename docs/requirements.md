# Market Control — requisitos e plano de engenharia

**Versão do documento:** 1.1 — escopo de aprendizagem Java puro
**Estado:** em desenvolvimento; núcleo de domínio e serviços em memória parcial
**Tecnologia-alvo desta trilha:** Java 25, Maven, JUnit 5; depois JDBC e HTTP sem Spring

Este documento é a fonte de verdade funcional do projeto. Requisitos ainda não implementados permanecem no plano; não são declarados concluídos por existirem classes ou testes parciais.

## 1. Objetivo

Desenvolver um sistema de gerenciamento de estoque para estabelecimentos comerciais, controlando produtos, categorias, fornecedores, lotes, entradas, saídas e perdas. O sistema deve identificar produtos vencidos e próximos do vencimento, permitir consultar estoque disponível e histórico, e impedir operações que corrompam o saldo.

O projeto tem dois objetivos complementares: (1) entregar um sistema útil com regras verificáveis; (2) aprender Java puro progressivamente, primeiro com coleções em memória, depois testes, JDBC e protocolo HTTP. Spring não faz parte da trilha atual.

## 2. Usuários previstos

- **Administrador:** gerencia produtos, categorias, fornecedores e usuários; consulta histórico conforme decisão de autorização.
- **Operador de estoque:** registra entradas, saídas, perdas e consultas autorizadas.

Autenticação é indicada como v2 no requisito RF17. A implementação de autorização sem autenticação exige uma decisão de escopo explícita; não se deve simular segurança real em uma interface que não identifica o usuário.

## 3. Problemas que o sistema deve resolver

- Falta de controle de validade por lote.
- Produtos vencidos podem permanecer no estoque comercializável.
- Falta de histórico para apurar perdas e alterações de saldo.
- Operações manuais geram erros.
- Operadores não recebem informação preventiva sobre validade e baixo estoque.

## 4. Requisitos funcionais e estado de entrega

Legenda: **Parcial** significa que há parte do fluxo implementada, mas o requisito não está fechado ponta a ponta. **Planejado** significa que ainda não foi identificado no código atual.

| ID | Requisito | Estado observado |
|---|---|---|
| RF01 | Cadastrar produtos | Parcial |
| RF02 | Editar informações dos produtos | Parcial |
| RF03 | Desabilitar produtos | Parcial; confirmar efeitos nos fluxos de estoque |
| RF04 | Cadastrar categorias | Parcial |
| RF05 | Cadastrar fornecedores | Planejado |
| RF06 | Registrar entrada de mercadorias | Planejado como movimentação auditável; há cadastro inicial de lote |
| RF07 | Registrar lotes e validades | Parcial |
| RF08 | Registrar saída de mercadorias | Planejado como caso de uso que pode consumir vários lotes |
| RF09 | Consultar estoque disponível | Parcial; existe totalização básica por produto |
| RF10 | Identificar produtos/lotes vencidos | Parcial; existe verificação no lote |
| RF11 | Identificar produtos/lotes próximos do vencimento | Planejado |
| RF12 | Consultar histórico de entradas, saídas, perdas e ajustes | Planejado |
| RF13 | Identificar produtos com baixo estoque | Planejado |
| RF14 | Registrar perdas por vencimento | Planejado |
| RF15 | Consultar produtos pela categoria | Planejado |
| RF16 | Cadastrar usuários | Planejado |
| RF17 | Autenticar usuários | Fora do escopo inicial; previsto como v2 |
| RF18 | Consultar usuários | Planejado |
| RF19 | Alterar permissões dos usuários | Planejado; depende de decisão sobre autenticação e identidade do ator |

## 5. Requisitos não funcionais

- **RNF01:** desenvolver inicialmente em Java puro, sem Spring.
- **RNF02:** nunca permitir quantidades negativas.
- **RNF03:** usar `BigDecimal` para valores monetários e quantidades fracionárias; estabelecer regras de precisão e arredondamento.
- **RNF04:** usar `LocalDate` para validade.
- **RNF05:** rejeitar operações que comprometam integridade do estoque; movimentação deve ser tudo-ou-nada.
- **RNF06:** manter testes automatizados para regras críticas.
- **RNF07:** permitir substituir armazenamento em memória por JDBC sem mover regras de negócio para o banco ou camada de transporte.
- **RNF08:** permitir exposição futura via protocolo HTTP.
- **RNF09:** permitir futuramente clientes web e mobile através de API HTTP.
- **RNF10:** tratar falhas por fronteira: erros de entrada recuperáveis no terminal, invariantes protegidas no domínio, erros técnicos identificáveis em persistência e respostas HTTP seguras; não engolir exceções nem expor detalhes internos.

## 6. Regras de negócio

### 6.1 Cadastro de produtos e categorias

- **RN01:** todo produto possui nome não nulo e não vazio após normalização.
- **RN02:** todo produto referencia uma categoria válida.
- **RN03:** código de barras normalizado é único.
- **RN04:** estoque mínimo não é negativo.
- **RN05:** produto desativado não recebe novas entradas comerciais.
- Uma categoria não pode ser duplicada segundo a política de comparação definida (proposta atual: ignorar espaços externos e caixa).
- Desativar uma categoria não apaga produtos/lotes históricos. Deve-se decidir se novos produtos podem escolhê-la; proposta: não.

### 6.2 Decisão de estoque mínimo e unidade de medida

**Proposta de domínio para validação:** `minimumStock` e `unitOfMeasure` pertencem a `Product`, não a `Lot`.

Justificativa: o estoque mínimo é limiar de reposição do produto e é comparado ao estoque total agregado de seus lotes. Se estivesse em cada lote, um mesmo produto poderia ter vários limites concorrentes e não haveria regra clara para consolidá-los. A unidade também deve ser única por produto para impedir somar, por exemplo, quilogramas a unidades. O lote pode guardar custo unitário e quantidades expressas na unidade do produto.

**Fluxo de terminal desejado:** o operador pode cadastrar primeiro os dados básicos do produto e, no fluxo de criação do primeiro lote, informar unidade e estoque mínimo como etapa de configuração inicial do produto. O serviço deve gravar esses valores uma única vez em `Product`; não deve repeti-los como atributos independentes nos lotes seguintes. O produto não pode participar de estoque/entradas até essa configuração ser concluída. Alternativa mais simples para o primeiro MVP: solicitar esses campos durante o cadastro do produto. A escolha final deve ficar registrada como decisão de produto e testada.

### 6.3 Lotes e validade

- **RN06:** cada lote está ligado a um produto.
- **RN07:** cada lote tem validade.
- **RN08:** quantidade inicial é maior que zero.
- **RN09:** quantidade disponível nunca é negativa.
- **RN10:** validade não é anterior à data de entrada.
- **RN11:** identificação original do lote é preservada.
- Um lote pertence a um fornecedor, conforme a entidade proposta.
- Produto inativo não recebe novas entradas; lotes existentes continuam consultáveis para histórico e retirada.
- **Proposta de status de validade:** vencido quando validade é anterior a hoje; vence em breve quando validade está entre hoje e hoje + 7 dias, inclusivo; válido após essa janela. Lote com quantidade zero não participa de alertas operacionais. A política de uso de `Clock` deve permitir testes determinísticos.

### 6.4 Movimentações

- **RN12:** saída não excede quantidade elegível disponível.
- **RN13:** lote vencido não pode ser comercializado.
- **RN14:** movimentação tem data/hora e tipo.
- **RN15:** movimentação confirmada não é apagada do histórico; correções são registradas como reversão/ajuste conforme regra a decidir.
- **RN16:** perda reduz quantidade disponível e gera registro.
- **RN17:** saída identifica os lotes consumidos e a quantidade retirada de cada um.
- **RN18:** ajuste inclui justificativa.
- **RN24:** estoque total do produto corresponde à soma de quantidades disponíveis dos lotes, respeitando a definição de estoque comercializável.
- **RN25:** movimentação inteira é confirmada ou nenhuma alteração de saldo/histórico permanece.
- **RN26:** lote com saldo zero não participa de consulta de estoque disponível nem alertas operacionais de validade.
- Regra de seleção de lotes (proposta a validar): consumir primeiro o lote elegível com vencimento mais próximo (FEFO), com desempate determinístico.

### 6.5 Usuários e permissões

- **RN19:** CPF e telefone de cada usuário são únicos após normalização.
- **RN20:** operadores podem inserir, editar e desabilitar produtos.
- **RN21:** somente administrador consulta histórico; movimentos feitos por operador são registrados.
- **RN22:** apenas administrador cadastra operador.
- **RN23:** produto desativado não pode ser vendido.
- A regra de autorização depende de identidade de usuário autenticada. Como RF17 está adiado para v2, decidir se o escopo atual implementa apenas modelo/autorização demonstrativa ou posterga usuários/permissões integralmente.

## 7. Entidades e responsabilidades previstas

| Entidade/parte | Responsabilidade e campos conceituais |
|---|---|
| `Product` | Identidade e dados de catálogo: id, nome, barcode, descrição, categoria, estoque mínimo, unidade, ativo. Protege regras de edição e configuração. |
| `Category` | Classificação: id, nome, descrição e estado ativo. Não apaga referências históricas. |
| `Supplier` | Origem de mercadoria: id, nome, documento, telefone, email e estado ativo. |
| `Lot` | Saldo rastreável: id, produto, fornecedor, código de lote, entrada, fabricação, validade, quantidade inicial/disponível e custo unitário. Não é dono do mínimo global do produto. |
| `StockMovement` | Cabeçalho imutável de uma operação: id, tipo, instante, descrição/justificativa, ator e itens. |
| `StockMovementItem` | Detalhe: lote, quantidade e custo aplicável àquele movimento. |
| `User` | Identidade, contatos normalizados, hash de senha quando autenticação entrar, papel e estado ativo. Não guardar senha em texto puro. |
| Serviços/casos de uso | Coordenam operações que envolvem várias entidades, validam pré-condições e aplicam tudo-ou-nada. Não devem concentrar entrada/saída de terminal. |
| Repositórios | Fronteira de armazenamento. Implementações em memória primeiro e JDBC depois; regras não devem depender de uma implementação concreta. |
| DTOs | Dados de entrada/saída de fronteiras; não são automaticamente entidades nem lugar para regras centrais. |
| `Program` | Menu e apresentação no terminal. Converte texto em valores, apresenta erros recuperáveis e chama casos de uso. |

## 8. Tipos e relacionamentos

- `UserRole`: `ADMIN`, `OPERATOR`.
- `MovementType`: `ENTRY`, `EXIT`, `LOSS`, `ADJUSTMENT`.
- `UnitOfMeasure`: `UNIT`, `KILOGRAM`, `GRAM`, `LITER`, `MILLILITER` (avaliar conversões entre unidades; no MVP, evitar conversão automática implícita).
- Categoria classifica vários produtos.
- Produto possui vários lotes.
- Lote pertence a um produto e fornecedor.
- Movimento contém um ou mais itens; cada item aponta para lote e quantidade.
- Uma saída pode dividir quantidade solicitada entre vários lotes.

## 9. Estratégia progressiva de engenharia

1. Corrigir documentação e confirmar escopo/decisões.
2. Consolidar Product/Category e regras de duplicidade.
3. Consolidar coleções em memória, contratos de consulta e erros recuperáveis no terminal.
4. Completar Supplier/Lot, unidade, mínimo e validade.
5. Implementar movimentos, consumo multi-lote e atomicidade em memória.
6. Implementar histórico, perdas, ajustes, alertas e consultas.
7. Decidir usuários/autorização e concluir os testes JUnit do núcleo.
8. Definir contratos de repositório e schema SQL; implementar JDBC.
9. Tornar movimentações transacionais no banco e testar rollback/concorrência.
10. Expor casos de uso via HTTP com API do JDK para aprendizagem, serialização JSON escolhida conscientemente e testes de integração.
11. Documentar execução, decisões, limitações e exemplos de uso; depois avaliar se Spring é uma evolução útil.

Cada etapa tem estudos, perguntas, critérios de pronto e commits sugeridos em [relatorio-aprendizado-marketcontrol.htm](../relatorio-aprendizado-marketcontrol.htm). Não iniciar uma etapa só porque a anterior compila: exija evidência comportamental e explicação do aluno.

### 9.1 Política de tratamento de erros

- Validar argumentos e invariantes na classe que conhece a regra; falha deve ocorrer antes de alterar estado.
- Serviços/casos de uso coordenam erros entre entidades e garantem que uma operação não termine parcialmente.
- Busca ausente deve ter contrato explícito (por exemplo, resultado opcional ou erro de domínio); não usar `null` como sinal ambíguo.
- O terminal captura entradas inválidas onde o usuário pode corrigi-las e permite continuar; não captura toda exceção ao redor do programa para apenas imprimir uma mensagem.
- Repositórios JDBC preservam a causa técnica ao traduzir `SQLException`; caso de uso garante rollback quando necessário.
- HTTP mapeia falhas conhecidas para status e corpo seguros; falha inesperada não deve expor stack trace, SQL, caminhos ou segredos.
- Log e mensagem ao usuário têm objetivos diferentes. Registrar contexto útil no log sem incluir credenciais ou dados pessoais desnecessários.
- Cada erro relevante deve ter teste do comportamento e do estado posterior, especialmente “falhou, portanto nada foi alterado”.

## 10. Perguntas ainda abertas

### Produto e estoque
1. O mercado vende por unidade, peso e volume? Qual precisão e arredondamento?
2. Estoque mínimo é informado no cadastro do produto ou na configuração feita durante o primeiro lote? A proposta é mantê-lo no produto mesmo se a pergunta ocorrer no fluxo de lote.
3. Um produto pode ter vários fornecedores? Um fornecedor pode entregar diferentes lotes do mesmo produto?
4. Custo de aquisição precisa ser histórico por lote?

### Validade
5. A janela de sete dias inclui hoje e usa dias corridos?
6. Vencidos saem automaticamente do estoque comercializável ou aguardam registro de perda/retirada?
7. Quem confirma retirada física? É necessário guardar justificativa/foto?

### Movimentações
8. Saídas serão manuais ou integradas a vendas?
9. Correção será reversão ou ajuste? Existe cancelamento antes da confirmação?
10. FEFO é a política para escolher lotes?

### Usuários e operação
11. Operador pode desativar outro operador? Haverá mais de um admin?
12. Operador vê custo de aquisição?
13. Uma ou várias filiais? Precisa funcionar offline? Acesso móvel é requisito desta versão?
14. Como RF17 autenticação v2 se relaciona à autorização por papel prevista nas RN20–22?

## 11. Critério de conclusão do projeto Java puro

O núcleo Java puro está concluído quando os requisitos acordados de cadastro, estoque, validade, movimentos e histórico funcionam em memória; invariantes críticas são cobertas por testes JUnit; operações complexas têm semântica tudo-ou-nada; o terminal permite demonstrar os fluxos; e o aluno explica modelo, coleções, exceções, testes e limitações.

A evolução para JDBC está concluída quando dados persistem após reinício, constraints protegem integridade, operações de movimento usam transação e rollback, e testes de integração provam o comportamento.

A evolução HTTP está concluída quando casos de uso são acessíveis por requisições documentadas, respostas/status são coerentes, erros não expõem detalhes internos, e testes exercitam a fronteira HTTP. Nenhum desses passos exige Spring.
