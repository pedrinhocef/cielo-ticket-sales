# Máquina de estados de pagamento

Estados persistidos: `PENDING`, `APPROVED`, `DENIED`, `CANCELED` e `FAILED_TECHNICAL`.

`PENDING` é o único estado que aceita callback. `APPROVED`, `DENIED` e `CANCELED` são terminais e imutáveis. `FAILED_TECHNICAL` pode voltar a `PENDING` somente após ação explícita de nova tentativa.

Estados de UI como “abrindo pagamento” são efêmeros. Se a pessoa retornar da Cielo sem callback, a compra permanece `PENDING` e a interface mostra “aguardando confirmação”, com opções de nova tentativa, histórico ou retorno à lista.

Após process death, a recuperação procura nesta ordem: referência do callback, referência do `SavedStateHandle`, compra externa ativa no banco. Havendo mais de uma compra ativa sem referência, a UI não escolhe uma silenciosamente.
