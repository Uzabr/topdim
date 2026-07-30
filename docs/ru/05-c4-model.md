# 05. C4-модель

Диаграммы используют официальный C4-PlantUML через стандартные includes (`<C4/...>`) и рендерятся независимо.

| Уровень/flow | Файл |
|---|---|
| Context | [c4-context.puml](diagrams/c4-context.puml) |
| Containers | [c4-container.puml](diagrams/c4-container.puml) |
| Gateway components | [c4-component-gateway.puml](diagrams/c4-component-gateway.puml) |
| Identity components | [c4-component-identity.puml](diagrams/c4-component-identity.puml) |
| Coupon components | [c4-component-coupon.puml](diagrams/c4-component-coupon.puml) |
| Order components | [c4-component-order.puml](diagrams/c4-component-order.puml) |
| Payment components | [c4-component-payment.puml](diagrams/c4-component-payment.puml) |
| Notification components | [c4-component-notification.puml](diagrams/c4-component-notification.puml) |
| Media components | [c4-component-media.puml](diagrams/c4-component-media.puml) |
| Bazaar components | [c4-component-bazaar.puml](diagrams/c4-component-bazaar.puml) |
| Supporting services | [c4-component-supporting-services.puml](diagrams/c4-component-supporting-services.puml) |
| Authentication flow | [c4-dynamic-authentication.puml](diagrams/c4-dynamic-authentication.puml) |
| Purchase flow | [c4-dynamic-purchase.puml](diagrams/c4-dynamic-purchase.puml) |
| Redemption flow | [sequence-redemption.puml](diagrams/sequence-redemption.puml) |
| Deployment | [c4-deployment.puml](diagrams/c4-deployment.puml) |
| Database overview | [database-overview.puml](diagrams/database-overview.puml) |

## Рендеринг

При установленном PlantUML и Graphviz:

```bash
plantuml docs/ru/diagrams/*.puml
```

Для контейнера:

```bash
docker run --rm -v "$PWD:/workspace" -w /workspace plantuml/plantuml \
  docs/ru/diagrams/*.puml
```

Remote includes не используются; `<C4/C4_Context>` разрешается стандартной библиотекой PlantUML. Если окружение не содержит C4 stdlib, нужно установить C4-PlantUML либо использовать официальный PlantUML server.
