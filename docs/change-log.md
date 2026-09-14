# Lista de mudanças para avaliação

## Qualidade e diretrizes assistidas por IA

- `AGENTS.md` estabelece regras de segurança, arquitetura, recuperação de pagamento e validação.
- `docs/architecture.md` e `docs/payment-state-machine.md` explicam limites dos módulos e transições permitidas.
- `docs/refactor-prompt.md` fornece um prompt reproduzível para alterações futuras por IA.

## Arquitetura e resiliência

- Eventos mockados foram isolados atrás do contrato reativo `EventRepository`; `FakeEventRepository` é a implementação do MVP.
- Casos de uso focados concentram recuperação, início, retry, conclusão, callback e construção do deep link; a `EventsViewModel` mantém intenções, estado e efeitos de UI.
- `PurchaseRepository` é um contrato de domínio; `RoomPurchaseRepository` implementa a persistência e converte a entidade Room para o modelo `Purchase`.
- A compra pendente é recuperada primeiro pela referência salva e, se ausente, por uma única compra pendente persistida no Room.
- A volta da Cielo sem callback deixa a compra em estado pendente e elimina o loading infinito.
- Estados e efeitos Compose respeitam o ciclo de vida.

## Experiência e histórico

- A barra inferior oferece Eventos e Ingressos.
- A tela Meus ingressos lista as compras persistidas e filtra todos os estados do pagamento.
- Strings visíveis da nova feature estão em resources; regras e números de negócio têm constantes nomeadas.

## Validação

- Testes cobrem o caso de uso de pagamento, recuperação por banco, callback após process death sem `SavedStateHandle`, retorno sem callback, proteção de idempotência e filtros do histórico.
- Testes Compose instrumentados verificam a lista de ingressos, estado vazio e seleção de filtro; o APK de testes é compilado no CI/local e executado em emulador ou dispositivo Android.
- A suíte `./gradlew test` e o build `./gradlew :app:assembleDebug` foram executados com sucesso após as mudanças.
