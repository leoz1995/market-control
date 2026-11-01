# Sistema de Mercado v.1.0

## 1.0 - Objetivo

Desenvolver um sistema de gerenciamento de estoque para estabelecimentos comerciais, permitindo controlar produtos, fornecedores, lotes, entradas e saídas de mercadorias, além de identificar produtos vencidos e próximos do vencimento, auxiliando na prevenção de perdas e na retirada de produtos impróprios para comercialização.

----

## 2.0 - Usuários do sistema

- Administrador : Responsável pelo cadastro e gerenciamento dos produtos, categorias e fornecedores.
- Operador de estoque: responsável por registrar entradas, saídas e consultar o estoque.

## 3.0 - Problemas identificados

- *Ausência de controle por lote - Não se sabe qual mercadoria vence primeiro*
- *Ausência de monitoramento - Produtos vencidos podem permanecer disponíveis*
- *Falta de histórico de perdas - O mercado não conhece o prejuízo por vencimento*
- *Conrole manual - Maior possibilidade de erro humano*
- *Ausência de alertar - O operador não consegue agir preventivamente* 

## 4.0 - Requisitos funcionais
### Representam aquilo que o sistema deverá fazer

- RF01 - Cadastrar produtos
- RF02 - Editar informações dos produtos
- RF03 - Desabilitar produtos
- RF04 - Cadastrar categorias
- RF05 - Cadastrar fornecedores
- RF06 - Registrar entrada de mercadorias
- RF07 - Registrar **LOTES** e suas **validades**
- RF08 - Registrar saída de mercadorias
- RF09 - Consultar o estoque disponível
- RF10 - Identificar produtos vencidos
- RF11 - Identificar produtos próximos do vencimento
- RF12 - Consultar histórico de movimentações (entradas, saídas, perdas, ajustes)
- RF13 - Identificar produtos com baixo estoque
- RF14 - Registrar perdas por vencimento
- RF15 - Consultar produtos pela categoria
- RF16 - Cadastrar usuários
- RF17 - Autenticar Usuários (v.2)
- RF18 - Consultar usuários
- RF19 - Alterar permissões dos usuários

## 4.1 - Requisitos não funcionais
### Determinam características de qualidade e restrições técnicas

- RNF01 - Desenvolvimento inicial em Java Puro
- RNF02 - Evitar quantidades negativas
- RNF03 - Utilizar BigDecimal para valores monetários e quantidades fracionárias
- RNF04 - LocalDate para datas de validade
- RNF05 - Não permitir movimentações que comprometam a integridade do estoque
- RNF06 - Disponibilizar testes automátizados para regras críticas
- RNF07 - Permitir integração futura ao banco de dados
- RNF08 - Permitir exposição futura com protocolo HTTP
- RNF09 - A arquitetura deverá permitir que o sistema seja acessado futuramente por interfaces web e mobile, por meio de uma API HTTP

## 4.2 - Regras de Negócio - Cadastro de produtos

- RN01 - Todo produto deve possuir nome
- RN02 - Todo produto deve possuir uma categoria válida
- RN03 - Código de barras não pode ser duplicado
- RN04 - Estoque mínimo não pode ser negativo
- RN05 - Produto desativado não pode receber novas entradas comerciais

## 4.3 - Regras de Negócio - Controle de LOTES

- RN06 - Todo lote deve ser associado a um produto.
- RN07 - Todo lote deve possuir validade
- RN08 - A quantidade inicial deve ser maior que zero
- RN09 - A quantidade disponível nunca pode ser negativa
- RN10 - A validade não pode ser anterior à data de entrada
- RN11 - Um lote deve manter sua identificação original

## 4.4 - Regras de Negócio - Controle de Movimentações
- RN12 - Não pode existir saída superior à quantidade dispoonível
- RN13 - Não pode existir saída de lote vencido
- RN14 - Toda movimentação deve possur data e tipo
- RN15 - Uma movimentação registrada não deve ser apagada do histórico
- RN16 - Uma perda deve reduzir a quantidade disponível
- RN17 - Uma saída deve identificar os lotes consumidos
- RN18 - Um ajuste deve registrar sua justificativa

- RN19 - Cada usuário deve possuir CPF e Telefone único.
- RN20 - Operadores podem inserir, editar, desabilitar produto.
- RN21 - Apenas adminitrador poderá consultar histórico de movimentações (movimentações feitas por operador são apenas registradas) **RF12**
- RN22 - Apenas administradores podem cadastrar um operador
- RN23 - Produtos desativados não podem ser vendidos
- RN24 - O estoque total de um produto deve corresponder a soma das quantidades disponíveis de seus lotes.
- RN25 - Uma movimentação deve ser executada integralmente ou não deve produzir nenhuma alteração no estoque.
- RN26 - Um lote com quantidade disponível igual a zero não deve participar de consultas de estoque disponível ou alertas operacionais de validade.

## 4.5 - Controle de validade

- **[VENCIDO] - Data de validade anterior à data atual**
*O lote não pode ser comercializado e deve ser identificado para retirada do estoque comercializável.*

- **[PRÓXIMO DO VENCIMENTO]**
*O lote vence nos próximos 7 dias, incluindo hoje. O período será configurável futuramente.* 

- **[VÁLIDO]**
*O lote possui validade posterior a hoje e não está dentro da janela de alerta.*

## 4.6 - Entidades

- **User** *Representa administrador ou operador.*
**Atributos: {id, name, cpf, phone, email, passwordHash, role, active}**

- **Product** *Representa o produto.*
**Atributos: {id, name, barcode, description, category, minimumStock, unitOfMeasure, active}**

- **Category** *Classifica os produtos.*
**Atributos: {id, name, description, active}**

- **Supplier** *Representa os fornecedores.*
**Atributos: {id, name, document, phone, email, active}**

- **Lot** *Controla quantidades e validades.*
**Atributos: {id, product, supplier, batchCode, entryDate, manufacturingDate, expirationDate, initialQuantity, availableQuantity, unitCost}**

- **StockMovement** *Agrupa movimentações.*
**Atributos: {id, movementType, dateTime, description, performedBy, items}**

- **StockMovementItem** *Detalha os lotes movimentados.*
**Atributos: {id, lot, quantity, unitCost}**

## 4.7 - Relacionamentos com as Entidades

*Category* Uma categoria possui vários produtos.
*Product* Um produto possui vários lotes.
*Lot* Um lote pertence à um produto e possui um fornecedor.
*StockMovementItem* Registra o quanto foi movimentado em cada lote.
*StockMovement* Agrupa os itens de uma operação.

- *Uma movimentação pode envolver vários lotes. Exemplo: uma saída de 10 unidades pode consumir 6 do lote A e 4 do lote B.*

## 4.8 - Enumerações

- **UserRole** {ADMIN,OPERATOR}
- **MovementType** {ENTRY,EXIT,LOSS,ADJUSTMENT}
- **UnitOfMeasure** {UNIT,KILOGRAM,GRAM,LITER,MILILITER}

## 4.9 - Perguntas ao cliente

**Sobre produtos e estoque**
1. O mercado trabalha com produtos vendidos por unidade, peso e volume?
2. Um mesmo produto pode possuir diferentes fornecedores?
3. O mesmo fornecedor pode entregar diferentes lotes do mesmo produto?
4. O mercado precisa registrar o custo de aquisição de cada lote?

**Sobre validade**
5. Quantos dias antes do vencimento o sistema deve emitir alertas?
6. Os produtos vencidos devem ser automaticamente retirados do estoque comercializável?
7. Quem será responsável por confirmar a retirada física dos produtos?
8. O sistema precisa registrar fotografias ou justificativas das perdas?

**Sobre movimentações**
9. As saídas serão registradas manualmente ou por integração com um sistema de vendas?
10. É permitido cancelar uma movimentação?
11. Como o mercado deverá corrigir uma entrada ou saída registrada incorretamente?

**Sobre usuários**
12. Um operador pode desativar outro operador?
13. O mercado precisa de mais de um administrador?
14. O operador pode consultar apenas o estoque ou também os valores de aquisição?

**Sobre operação**
15. O sistema será utilizado em apenas uma loja ou várias filiais?
16. O mercado precisa consultar o estoque pelo celular?
17. O sistema precisa funcionar sem conexão com a internet?
