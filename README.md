# RaindanceMC/shops

- **`shops-module`** — Rain's Flexible Modules — Shops: players' own shops — a chest or barrel that sells and buys one exact item, grouped into stores, fronted by statues of their owners, found through `/bazaar`.
- **`shops-standalone`** — Rain's Shops: the shops module and the standard wrapper, shaded together.

Part of [RaindanceMC](https://github.com/RaindanceMC); built on [RainsCore](https://github.com/RaindanceMC/RainsCore) and the shared parent in [framework](https://github.com/RaindanceMC/framework).

## Build

```
mvn install
```

Needs Java 25 and read access to `packages.tstieh.de` (server ids `packages-releases` / `packages-snapshots` in `~/.m2/settings.xml`).

## Plugins

- `shops-standalone/target/RainsShops-0.1.3.jar` — also published as `de.raindancer.modules:shops-standalone:0.1.3`

## Versions

A module's version is a property in this repository's root `pom.xml`, a plugin's is `plugin.version` in its own pom. CI publishes every version that is not in the registry yet on the next push to master.
