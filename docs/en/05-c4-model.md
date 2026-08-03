# 05. C4 model

The diagrams use the official C4-PlantUML standard-library includes and are independently renderable.

| View | File |
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
| Redemption sequence | [sequence-redemption.puml](diagrams/sequence-redemption.puml) |
| Deployment | [c4-deployment.puml](diagrams/c4-deployment.puml) |
| Database overview | [database-overview.puml](diagrams/database-overview.puml) |

Render locally with:

```bash
plantuml docs/en/diagrams/*.puml
```

Or:

```bash
docker run --rm -v "$PWD:/workspace" -w /workspace plantuml/plantuml \
  docs/en/diagrams/*.puml
```

`<C4/...>` must be available in the PlantUML standard library; otherwise install C4-PlantUML or use an official PlantUML server.
