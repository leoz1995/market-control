# Market Control

Sistema de gerenciamento de estoque para estabelecimentos comerciais, desenvolvido para praticar Java puro e evoluir, por etapas, para persistência JDBC e acesso via protocolo HTTP.

## Objetivo

Controlar produtos, categorias, fornecedores, lotes, entradas, saídas e perdas; identificar produtos vencidos, próximos do vencimento ou abaixo do estoque mínimo; preservar o histórico e a integridade do estoque.

## Estado atual

Projeto em desenvolvimento. A implementação existente já contém uma aplicação de terminal, cadastro/edição de produtos e categorias, associação de produto a categoria, serviços com armazenamento em memória, índice de busca por código de barras e operações iniciais de lote com quantidade e validade. Há testes JUnit para partes do domínio e serviços.

O fluxo completo de movimentações, histórico, fornecedores, usuários, persistência JDBC e API HTTP ainda está no plano de evolução. Consulte [docs/requirements.md](docs/requirements.md) para o inventário por requisito, decisões em aberto e etapas de implementação. Consulte [relatorio-aprendizado-marketcontrol.htm](relatorio-aprendizado-marketcontrol.htm) para a trilha de estudo e os critérios de progresso.

## Decisão de domínio em avaliação: estoque mínimo e unidade

Estoque mínimo e unidade de medida são propriedades do **produto**, pois o estoque agregado do produto é a soma de seus lotes. A interação do terminal pode solicitar esses dados durante o primeiro fluxo de cadastro de lote, se isso for mais didático para o usuário; nesse caso, os valores devem ser registrados uma única vez no produto e reutilizados nos lotes seguintes. Não devem ser repetidos como política independente em cada lote. Esta decisão está marcada como proposta a validar em `docs/requirements.md`.

## Tecnologias e restrições desta fase

- Java 25 (configurado no Maven).
- Maven.
- JUnit Jupiter 5 (configurado no Maven).
- Java Collections Framework para armazenamento inicial em memória.
- Sem Spring nesta trilha. JDBC e HTTP serão estudados diretamente depois que o domínio e os casos de uso estiverem concluídos e testados.

Dependências auxiliares futuras, como driver JDBC ou biblioteca de JSON, devem ser introduzidas apenas na etapa correspondente e justificadas na documentação.

## Organização conceitual

- `domain.model`: entidades e regras que protegem o estado válido.
- `domain.model.interfaces`: contratos de consulta oferecidos a consumidores.
- `service`: coordenação dos casos de uso e armazenamento em memória nesta fase.
- `dtos`: dados de entrada/saída entre partes da aplicação.
- `Program`: interação de terminal; deve traduzir entrada e apresentar resultado, sem concentrar regras de domínio.
- `src/test`: testes automatizados das regras e fluxos.

As implementações JDBC e HTTP futuras devem permanecer fora do domínio, para que regras e testes de negócio continuem funcionando sem servidor ou banco.

## Como executar

Requer JDK 25 e Maven instalados. Na raiz do repositório, use `mvn test` para executar os testes e `mvn package` para compilar e empacotar. Para iniciar a aplicação pelo Maven, use `mvn exec:java` somente depois de configurar o plugin correspondente; por enquanto, execute `marketcontrol.Program` pela IDE ou pelo launcher Java com o classpath apropriado.

## Documentação

- [Requisitos, regras de negócio e plano do projeto](docs/requirements.md)
- [Trilha de aprendizado, etapas, commits sugeridos e avaliação](relatorio-aprendizado-marketcontrol.htm)
- [Guia de fundamentos Java](guia-java-fundamentos.htm)
- [Guia de leitura de testes](guia-como-ler-testes-java.htm)

Os dois guias HTML existentes estão sendo revisados para acompanhar a implementação real. Exemplos neles devem ser entendidos como didáticos quando identificados como tais, não como funcionalidades já entregues.

## Critério de conclusão

O projeto será considerado concluído quando os requisitos acordados tiverem implementação, testes adequados, documentação de execução, persistência JDBC transacional e uma interface HTTP testável, respeitando as etapas e decisões registradas em `docs/requirements.md`. Requisitos explicitamente adiados (por exemplo, autenticação indicada como v2) devem permanecer marcados como adiados, não como concluídos.

By Leonardo Alves
