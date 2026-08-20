# Cielo Ticket Sales App

Aplicativo Android em Kotlin para venda de ingressos de eventos locais, com fluxo simples de uma compra por evento e pagamento via Deep Link Cielo Smart.

## Execução do projeto

Pré-requisitos: Android Studio, JDK 17+, Android SDK e um emulador Android ou o emulador Cielo Smart para validar o pagamento de ponta a ponta.

Crie o arquivo `local.properties` na raiz do repositório. Ele já está ignorado pelo Git.

```properties
CIELO_CLIENT_ID=seu_client_id
CIELO_ACCESS_TOKEN=seu_access_token
```

Em seguida, execute:

```bash
./gradlew installDebug
./gradlew test
```

Para a validação Cielo, instale o emulador oficial Cielo Smart, instale este APK de debug, selecione um evento e uma quantidade e conclua o pagamento no emulador. Credenciais reais nunca devem ser versionadas ou registradas em logs.

## Arquitetura

- `:app`: ponto de entrada, configuração Hilt e Activity de callback.
- `:core`: persistência Room, estado da compra e transação de idempotência.
- `:feature:cielo`: construtor do payload Cielo, parser de callback e abstração de credenciais.
- `:feature:events`: interface Compose, ViewModel e orquestração do pagamento.

O app usa MVVM, estado de UI unidirecional, Hilt e Room. Um `Mutex`, uma chave de idempotência persistida e uma operação atômica do Room evitam a criação concorrente ou repetida de pagamentos. Uma compra pendente é recuperada após Process Death, mas nunca é iniciada automaticamente.

## Integração Cielo Smart

O app cria um payload de pagamento em Base64 e dispara o Deep Link App-to-App configurado da Cielo Smart. O callback é recebido pela `CieloResponseActivity`, encaminhado à ViewModel, correlacionado à referência da compra pendente e persistido como aprovado, negado, cancelado ou falha técnica.

Controles importantes:

- `reference` é a chave local de idempotência.
- Uma aprovação exige identificador de transação válido e referência correspondente.
- O `code` do JSON de callback define o resultado de negócio; o `responsecode` da URL não é usado como resultado do pagamento.
- A ausência do aplicativo Cielo é tratada como falha técnica com nova tentativa disponível.

Referências oficiais: [pagamento](https://docs.cielo.com.br/cielo-smart/docs/pagamento), [exemplo de Deep Link](https://docs.cielo.com.br/cielo-smart/docs/deep-link-exemplo-de-codigo), [configuração do Manifest](https://docs.cielo.com.br/cielo-smart/docs/configurando-o-android-manifest) e [códigos de erro](https://docs.cielo.com.br/cielo-smart/docs/codigos-de-erro).

## Bibliotecas

- Jetpack Compose e Material 3: interface.
- Hilt: injeção de dependências.
- Room: estado local da compra e idempotência atômica.
- Kotlin Coroutines e Flow: operações assíncronas e estado de UI.
- Coil: imagens dos eventos.
- ZXing: QR Code opcional do ingresso.
- JUnit, MockK e Robolectric: testes automatizados.

## Testes

`./gradlew test` executa atualmente 19 testes locais aprovados:

- `:app`: 1
- `:core`: 3
- `:feature:cielo`: 8
- `:feature:events`: 7

Eles cobrem payload e parser de callback, idempotência no Room, proteção contra duplo clique, nova tentativa, falha de abertura e reidratação de compra pendente. Testes instrumentados exigem um dispositivo ou emulador conectado.

## Decisões e próximos passos

O case pede um fluxo de compra simples; por isso, cada pagamento contém ingressos de um evento, em vez de um carrinho com múltiplos eventos. O Room é apenas local; em produção, seria recomendada uma camada de backend para conciliação e auditoria no servidor.

Com mais tempo, as próximas melhorias seriam histórico de compras, validação do QR Code na entrada e testes ponta a ponta no emulador oficial Cielo. A validação final das credenciais e do ambiente Cielo continua sendo parte da homologação.

## Desenvolvimento assistido por IA

Ferramentas de IA foram usadas para revisar o protocolo Cielo, propor testes e refinar decisões de concorrência e idempotência.

- Especificação usada: requisitos do case, documentação Cielo Smart Deep Link e código local.
- Resumo dos prompts: validar o contrato de payload/callback, proteger contra pagamentos duplicados e adicionar testes para erro, nova tentativa e Process Death.
- Restrições: sem credenciais no controle de versão ou logs, sem uso do contrato REST de e-commerce da Cielo e proteção contra repetição localmente atômica e testável.
- Resultado: revisão ou implementação de verificações de payload e callback, idempotência no Room, nova tentativa na UI e testes unitários correspondentes.
