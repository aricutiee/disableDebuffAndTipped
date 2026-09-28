# DisableDebuffAndTipped 1.1.0

Updated from the original local plugin source for Paper 1.21.11 and Java 21.

Weaving potions use an eight-minute base duration (9,600 ticks). Existing bottles, brewed results, dropped bottles, consumption and thrown potions are covered. Drinkable, splash and lingering forms retain their identity, names and other metadata. Vanilla splash-distance and lingering-cloud duration scaling still apply, so a lingering application lasts two minutes and a distant splash can be shorter than eight minutes.

Turtle Master, Long Turtle Master and Strong Turtle Master bypass both the harmful-potion filter and the configurable upgrade limits in every potion form. Weaving also bypasses those limits. The menu does not offer ineffective switches for these exempt families.

Other harmful potions become water bottles, preserving potion form and stack size. Tipped and spectral arrows become ordinary arrows, as in the original plugin. These are potion exceptions, not exceptions for tipped arrows. Beneficial potion upgrade limits and the existing trial-spawner Strength/Swiftness behavior remain available.

`/potionlimiter` opens the existing admin interface. Permission: `potionlimiter.admin`, default OP. Existing `plugins/disableDebuffAndTipped/config.yml` is reused. Exempt families stay protected regardless of older settings. Inventory reconciliation runs four times per second; consumption and launch checks handle immediate use.

## Build and install

Run `gradlew.bat build` on Windows or `./gradlew build` elsewhere with Java 21. The Gradle wrapper resolves Paper 1.21.11. Stop the server, retain the old JAR as a `.jar.disabled` backup, put `DisableDebuffAndTipped-1.1.0.jar` in `plugins`, then start. Do not enable both versions at once.

## Verification

Six automated MockBukkit tests cover eight-minute duration and metadata in all three forms, repeated normalization, all Turtle Master variants despite restrictive settings, existing debuff and special-arrow rules, custom Weaving without exempting unrelated custom poison, ordinary buffs and explicit limits, inventory reconciliation, immediate consumption and cancelled consumption. MockBukkit lacks potion category lookup, so tests supply that platform classification through a test stub. Live visual/gameplay testing is not claimed.
