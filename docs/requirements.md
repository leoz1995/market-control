# Market Control — requisitos e plano de engenharia

**Versão do documento:** 1.2 — atualizado para a implementação JDBC atual
**Estado:** em desenvolvimento; domínio inicial, serviços e persistência MariaDB/JDBC implementados; fluxos de movimentação e requisitos avançados pendentes
**Tecnologia atual:** Java 25, Maven, JUnit Jupiter 5.11.4, MariaDB Connector/J 3.5.6

Este documento é a fonte de verdade funcional do projeto. Requisitos ainda não implementados permanecem no plano; não são declarados concluídos por existirem classes ou testes parciais. Os serviços usam atualmente repositórios JDBC. Os testes de domínio são unitários; os testes de serviço e persistência acessam o MariaDB configurado em `src/main/resources/database.properties`.

## 1. Objetivo

Desenvolver um sistema de gerenciamento de estoque para estabelecimentos comerciais, controlando produtos, categorias, fornecedores, lotes, entradas, saídas e perdas. O sistema deve identificar produtos vencidos e próximos do vencimento, permitir consultar estoque disponível e histórico, e impedir operações que corrompam o saldo.

O projeto tem dois objetivos complementares: (1) entregar um sistema útil com regras verificáveis; (2) aprender Java puro progressivamente, usando testes e JDBC antes de decidir se o protocolo HTTP entra no escopo. A versão atual já persiste em MariaDB. Spring não faz parte da trilha atual.

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
| RF01 | Cadastrar produtos | Parcial; persistência JDBC, validação e unicidade do código implementadas; fluxo ainda básico |
| RF02 | Editar informações dos produtos | Parcial; edição JDBC dos dados do produto implementada |
| RF03 | Desabilitar produtos | Planejado; não existe estado ativo em `Product` |
| RF04 | Cadastrar categorias | Parcial; criação, busca e edição JDBC implementadas |
| RF05 | Cadastrar fornecedores | Planejado |
| RF06 | Registrar entrada de mercadorias | Parcial; cria lote inicial, sem movimento auditável |
| RF07 | Registrar lotes e validades | Parcial; lote é persistido via JDBC e valida datas/quantidades |
| RF08 | Registrar saída de mercadorias | Parcial; consumo de quantidade em um lote, sem movimento/histórico e sem distribuição multi-lote |
| RF09 | Consultar estoque disponível | Parcial; totalização JDBC considera lotes cuja validade é hoje ou futura |
| RF10 | Identificar produtos/lotes vencidos | Parcial; lote identifica validade vencida e impede seu consumo |
| RF11 | Identificar produtos/lotes próximos do vencimento | Planejado |
| RF12 | Consultar histórico de entradas, saídas, perdas e ajustes | Planejado |
| RF13 | Identificar produtos com baixo estoque | Planejado |
| RF14 | Registrar perdas por vencimento | Planejado |
| RF15 | Consultar produtos pela categoria | Planejado; produtos são associados a categorias, mas consulta dedicada não existe |
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
- **RNF07:** manter persistência atrás de interfaces de repositório para evitar regras de negócio dependentes de SQL ou da camada de transporte. Parcialmente atendido: os contratos e implementações JDBC existem; não há implementação em memória atual dos repositórios.
- **RNF08:** permitir exposição futura via protocolo HTTP.
- **RNF09:** permitir futuramente clientes web e mobile através de API HTTP.
- **RNF10:** tratar falhas por fronteira: erros de entrada recuperáveis no terminal, invariantes protegidas no domínio, erros técnicos identificáveis em persistência e respostas HTTP seguras; não engolir exceções nem expor detalhes internos.

## 6. Regras de negócio

### 6.1 Cadastro de produtos e categorias

- **RN01:** todo produto possui nome não nulo e não vazio após normalização.
- **RN02:** todo produto referencia uma categoria válida.
- **RN03:** código de barras normalizado é único.
- **RN04:** estoque mínimo não é negativo. Planejado: o modelo atual de `Product` ainda não possui estoque mínimo.
- **RN05:** produto desativado não recebe novas entradas comerciais.
- Uma categoria não pode ser duplicada segundo a política de comparação definida (proposta atual: ignorar espaços externos e caixa).
- Desativar uma categoria não apaga produtos/lotes históricos. Deve-se decidir se novos produtos podem escolhê-la; proposta: não.

### 6.2 Decisão de estoque mínimo e unidade de medida

**Decisão atual de implementação:** `unitOfMeasure` pertence a `Product`, não a `Lot`. O campo é obrigatório e é salvo/carregado pelo JDBC. `minimumStock` ainda não está implementado; a proposta é mantê-lo em `Product`, não em `Lot`.

Justificativa: o estoque mínimo é limiar de reposição do produto e é comparado ao estoque total agregado de seus lotes. Se estivesse em cada lote, um mesmo produto poderia ter vários limites concorrentes e não haveria regra clara para consolidá-los. A unidade também deve ser única por produto para impedir somar, por exemplo, quilogramas a unidades. O lote pode guardar custo unitário e quantidades expressas na unidade do produto.

**Fluxo atual de terminal:** unidade é informada durante o cadastro do produto. Estoque mínimo não é solicitado nem persistido. A definição do fluxo e das regras do mínimo continua pendente.

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
| Repositórios | Fronteira de armazenamento. Contratos e implementações JDBC atuais; regras não devem depender de SQL nem de uma implementação concreta. Repositório em memória poderá ser usado em testes/unitários se necessário. |
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

## 9. Estratégia progressiva de engenharia e situação atual

1. **Implementado parcialmente:** Product e Category com invariantes iniciais e interfaces de leitura.
2. **Implementado parcialmente:** Lot com validade, quantidade inicial/disponível e consumo unitário; sem fornecedor nem histórico de movimento.
3. **Implementado:** contratos `CategoryRepository`, `ProductRepository` e `LotRepository` e implementações JDBC/MariaDB.
4. **Implementado parcialmente:** serviços de categoria, produto e lote coordenam persistência; testes de domínio e integração cobrem cenários essenciais.
5. **Em andamento:** completar fluxos de estoque, transações e regras de operação antes de ampliar a interface.
6. **Pendente:** movimentos auditáveis, saídas que consomem vários lotes, perdas, ajustes, histórico, mínimo, alertas e fornecedores.
7. **Pendente:** decidir identidade/permissões e eventual autenticação.
8. **Pendente:** API HTTP, se mantida no escopo, com testes de fronteira.
9. Atualizar documentação e critérios de conclusão conforme cada requisito for implementado.

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

O núcleo Java puro estará concluído quando os requisitos acordados de cadastro, estoque, validade, movimentos e histórico funcionarem pela arquitetura de repositórios; invariantes críticas estiverem cobertas por testes JUnit; operações complexas tiverem semântica tudo-ou-nada; o terminal demonstrar os fluxos; e o aluno explicar modelo, persistência, exceções, testes e limitações.

A evolução para JDBC está concluída quando dados persistem após reinício, constraints protegem integridade, operações de movimento usam transação e rollback, e testes de integração provam o comportamento.

A evolução HTTP está concluída quando casos de uso são acessíveis por requisições documentadas, respostas/status são coerentes, erros não expõem detalhes internos, e testes exercitam a fronteira HTTP. Nenhum desses passos exige Spring.
