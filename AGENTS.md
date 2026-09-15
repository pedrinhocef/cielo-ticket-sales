# Cielo Ticket Sales — Diretrizes de contribuição

## Escopo e segurança

- Preserve o fluxo Cielo existente. Não invente parâmetros, respostas ou SDKs; confirme o contrato na documentação oficial antes de alterá-lo.
- Nunca versione ou registre credenciais, tokens, payloads completos ou identificadores de pagamento.
- Room é a fonte local de verdade para compras. `SavedStateHandle` é apenas uma referência de restauração.
- Não reabra o aplicativo Cielo automaticamente após restauração de processo. A pessoa usuária deve iniciar explicitamente uma nova tentativa.

## Arquitetura

- UI Compose renderiza estado e emite intenções; não acessa DAO, Room ou regras de pagamento.
- ViewModels orquestram intenções, casos de uso, estado e efeitos; não constroem payloads, não interpretam callbacks e não implementam transições persistidas.
- Regras de negócio e transições de compra vivem em use cases.
- Interfaces de repositório pertencem ao domínio; implementações Room, mocks e integrações externas pertencem a dados.
- Estados terminais (`APPROVED`, `DENIED`, `CANCELED`) não podem ser sobrescritos por callbacks tardios.

## Pagamento e recuperação

- Uma compra externa ativa é persistida antes de abrir o deep link e identificada por `idempotencyKey`/`reference`.
- Na recuperação: usar a referência do callback; senão a do `SavedStateHandle`; senão consultar a compra ativa persistida. Não assumir uma compra quando houver ambiguidade.
- O retorno ao app sem callback significa resultado pendente, não cancelamento. A UI nunca pode permanecer em loading indefinidamente.

## Compose e testes

- Colete `StateFlow` na UI com `collectAsStateWithLifecycle()`.
- Colete efeitos de navegação/abertura externa respeitando o ciclo de vida e sem duplicar efeitos já consumidos.
- Toda alteração no fluxo de pagamento exige testes de transição, callback tardio, retorno sem callback e process death.
- Execute `./gradlew test` e `./gradlew :app:assembleDebug` antes de concluir uma alteração relevante.

## Observabilidade

- Registre eventos por meio do contrato `Observability`; features não podem depender diretamente de Logcat, Room ou provedores externos.
- Nunca registre credenciais, payloads, URIs completas, referências de idempotência, IDs de transação ou mensagens de exceção não sanitizadas.
- Use somente nomes, etapas, códigos e dimensões tipadas definidos em `:observability`.
- Falhas de observabilidade nunca podem interromper ou alterar o fluxo de pagamento.
- Diagnósticos locais usam banco separado, com retenção limitada; a tabela de compras não é armazenamento de analytics.
