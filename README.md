# RaindanceMC/shops

- **`shops-module`** — Rain's Flexible Modules — Shops: players' own chest shops — an item frame showing the item on a chest and a `[market]` sign with the price; item, price and name float over it; `/market` finds every shop and trades from afar. Optionally a market world: endless generated streets with stall plots (one free, more to buy), houses and a square; the built plots rotate every few days so everybody gets a turn near the square. Your own stalls and house fronts as `.schem` in `plugins/RainsShops/market/`.
- **`shops-standalone`** — Rain's Shops: the shops module and the standard wrapper, shaded together.

Part of [RaindanceMC](https://github.com/RaindanceMC); built on [RainsCore](https://github.com/RaindanceMC/RainsCore) and the shared parent in [framework](https://github.com/RaindanceMC/framework).

## Build

```
mvn install
```

Needs Java 25 and read access to `packages.tstieh.de` (server ids `packages-releases` / `packages-snapshots` in `~/.m2/settings.xml`).

## Plugins

- `shops-standalone/target/RainsShops-0.6.0.jar` — also published as `de.raindancer.modules:shops-standalone:0.6.0`

## Versions

A module's version is a property in this repository's root `pom.xml`, a plugin's is `plugin.version` in its own pom. CI publishes every version that is not in the registry yet on the next push to master.
