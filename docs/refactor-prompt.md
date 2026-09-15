# Prompt de refatoração — Cielo Ticket Sales

Você é um desenvolvedor Android sênior. Trabalhe apenas no repositório atual e siga integralmente `AGENTS.md`, `docs/architecture.md` e `docs/payment-state-machine.md`.

## Objetivo

Amadurecer o MVP sem alterar o contrato Cielo que já funciona: reduzir a orquestração da ViewModel, recuperar compras após process death, eliminar loading infinito após retorno sem callback e criar histórico filtrável de ingressos.

## Requisitos

1. Use MVVM e Clean Architecture pragmática. Extraia use cases para iniciar, recuperar, repetir e concluir compras; a ViewModel deve ficar focada em intenções, estado e efeitos.
2. Room é a fonte de verdade. Na recuperação, buscar pela referência do callback, depois por `SavedStateHandle` e, se ausente, pela compra externa ativa persistida. Não retomar automaticamente o deep link.
3. Ao voltar da Cielo sem callback, substituir o loading por estado pendente/indeterminado, sem registrar cancelamento automaticamente.
4. Criar tela Compose “Meus ingressos”, acessível por navegação inferior com “Eventos” e “Ingressos”. Listar compras do Room e filtrar Todos, Aprovados, Pendentes, Cancelados, Negados e Falhas.
5. Coletar estados Compose com `collectAsStateWithLifecycle`. Efeitos únicos devem respeitar o ciclo de vida sem repetir abertura de aplicativo externo.
6. Manter eventos mockados para o MVP, mas isolar uma implementação `FakeEventRepository` atrás de uma interface de domínio reativa. Não adicionar API, Retrofit ou backend.
7. Não inventar campos, SDKs ou comportamentos Cielo. Não versionar credenciais.
8. Criar ou atualizar testes de use case, ViewModel, DAO e UI para process death, retorno sem callback, filtros e estados terminais.
9. Toda telemetria deve usar `:observability`, ser tipada e sanitizada. Nunca registrar payload, URI completa, credencial, referência ou ID de transação; falhas de sinks não podem afetar o pagamento.

## Validação

Ao final, execute `./gradlew test` e `./gradlew :app:assembleDebug`, relatando resultados e pendências de validação manual no emulador Cielo.
