# Arquitetura alvo

O aplicativo usa MVVM com limites de Clean Architecture pragmáticos para o MVP.

```text
Compose UI -> ViewModel -> Use case -> Domain repository -> Data implementation
                                  -> Payment gateway
                                  -> Observability contract -> independent sinks
```

- `:app` contém a composição de navegação, Activities e configuração Hilt.
- `:core` contém modelos de domínio, contratos, casos de uso de compra e implementações locais Room.
- `:feature:cielo` implementa `PaymentGateway`, adaptando payload e callback do contrato Cielo sem expor suas classes à feature de Eventos.
- `:feature:events` contém a lista de eventos, casos de uso focados e o ViewModel. Recuperação, início, retry, conclusão, callback e construção do deep link possuem responsabilidades separadas.
- `:feature:purchases` contém o histórico, filtros e detalhes de compras.
- `:observability` contém o contrato transversal, eventos tipados, sanitização, Logcat e persistência diagnóstica limitada.

Os contratos expõem modelos de domínio. A entidade Room e seus mapeamentos permanecem em `:core:data`, sem vazar para ViewModels, telas ou casos de uso.

Para o MVP, eventos continuam locais. O contrato reativo deve ser mantido para que uma fonte remota substitua a implementação fake sem alterar a apresentação.

A observabilidade é best effort: nenhuma falha de sink pode alterar uma transição financeira. O banco diagnóstico é separado do banco de compras para não misturar fonte de verdade do negócio com telemetria descartável.
