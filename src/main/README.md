# HoloTrade 2.0  (Paper 26.2 / Java 25)

Dos modulos independientes:

1. **Tiendas holograma**: un bloque con el item flotando encima. Click derecho con el item de pago
   = tradeo rapido; sin item = menu. Tipos: Trueque, Experiencia, Misterio, Demanda.
2. **Tiendas NPC (estilo Shopkeepers)**: aldeano u otro NPC con varias ofertas, menu propio
   paginado, compra en bloque, fidelidad, limites por jugador, reposicion de stock.

Pago por oferta/tienda: **solo items, solo dinero (Vault) o ambos** (el cliente elige).
Configurar dinero requiere el permiso `holotrade.money` (por defecto solo OP).

## Compilar
Necesitas JDK 25 y Maven:  `mvn clean package`  ->  `target/HoloTrade-2.0.0.jar` -> `plugins/`
Para dinero: Vault (o VaultUnlocked) + un plugin de economia.

## Comandos
/ht  /ht create  /ht list  /ht delete
/ht npc create [admin] | edit | name <texto> | link | delete | list    (mirando al NPC)

## Permisos
holotrade.use (todos) · holotrade.create (todos) · holotrade.money (op) · holotrade.admin (op)

## Notas
- Tiendas NPC de jugador: el stock sale de un cofre/barril vinculado (`/ht npc link`, luego click derecho al cofre).
  El cofre NO esta protegido: usa un plugin de proteccion (WorldGuard, GriefPrevention...).
- Tiendas NPC admin: stock infinito (o con reposicion), sin cofre.
- Datos: plugins/HoloTrade/shops.yml y npcs.yml
