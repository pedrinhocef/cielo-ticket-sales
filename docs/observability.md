# Observabilidade

O módulo `:observability` concentra analytics de produto, diagnóstico técnico e auditoria sanitizada do fluxo. Features dependem apenas de `Observability`; Logcat, Room e futuros provedores remotos permanecem detalhes substituíveis.

## Fluxo

```text
Feature/use case/repository
          |
          v
     Observability
       /       \
  Logcat     Room diagnóstico
              (máximo 300)
```

`DefaultObservability` isola cada sink com tolerância a falhas. Analytics nunca bloqueia, cancela ou altera uma compra.

## Catálogo principal

- Início, persistência, retry e transição de estado do pagamento.
- Construção e abertura do Deep Link, sem registrar a URI.
- Callback recebido, interpretado, inválido ou com correlação divergente.
- Retorno sem callback e recuperação por `SavedStateHandle` ou banco.
- Callback tardio ignorado por proteção do estado terminal.
- Filtro do histórico e abertura do QR Code aprovado.

## Dados permitidos

- Nome tipado do evento, etapa e severidade.
- Status anterior e atual.
- Resultado sanitizado e origem da recuperação.
- Quantidade de ingressos e filtro selecionado.
- Classe simples da exceção, sem mensagem nem stack trace persistida.

## Dados proibidos

- Credenciais e tokens.
- Payload ou resposta completa da Cielo.
- URI completa do Deep Link ou callback.
- `idempotencyKey`, referência e ID de transação.
- Nome do evento comprado ou qualquer texto livre recebido externamente.

O banco `cielo_observability.db` é independente do banco de compras e usa retenção destrutiva adequada ao MVP. Um provedor remoto pode ser adicionado como novo sink sem alterar features ou regras financeiras.
