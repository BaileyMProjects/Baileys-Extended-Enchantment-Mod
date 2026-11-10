# Bailey's Extended Enchantments

[![CurseForge downloads](https://img.shields.io/curseforge/dt/1579193?label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/baileys-extended-enchantments)
![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-green)
![Forge 47](https://img.shields.io/badge/Forge-47.3-orange)

A Minecraft enchantment expansion for **Forge 1.20.1**, published on
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/baileys-extended-enchantments).
It pushes vanilla enchantments past their normal caps, adds 35 enchantments of its own, and
builds a Deep Dark progression around two new blocks.

## Highlights

- **Ancient Enchanting Table and Ancient Anvil.** Deep Dark blocks that can enchant past the
  vanilla caps (Sharpness VII, Protection VII, Power X, and others).
- **35 new enchantments** for weapons, tools, bows and armour. They include Vein Miner,
  Timber, Auto Smelt, Vampiric, Frostbite and Lava Walker, plus an evasion armour line,
  Mirage → Dissonance.
- **Ultimates.** A prestige system where an item's top enchantment can be transformed into
  a stronger Ultimate form at the Ancient Anvil, with one Ultimate per item.
- **Secret enchantments.** Hidden enchantments unlocked through a ritual with its own
  reveal sequence.
- **The Shadow Monarch** (1.2.0). The Umbra and Requiem enchantments let you raise
  defeated mobs, and even players, as a shadow army. The army is managed from its own
  screen and costed by a classifier that learns how dangerous each mob is from how it
  actually fights, so modded mobs are priced fairly too.
- **Worldgen loot.** Ancient City chests can contain the mod's Corrupted Smithing Template.

## Development timeline

| Date | Milestone |
|---|---|
| 18 June 2026 | First public release on CurseForge (1.0.2, Forge 1.20.1) |
| June – September 2026 | Continued development: balance reworks, new enchantments, GUI and texture redesign |
| 21 September 2026 | Git history begins (see note below) |
| 22 September 2026 | 1.2.0 released: the Shadow Monarch update |

> **About the commit history:** the mod was developed and released for several months before
> it was put under version control. The repository starts on 21 September 2026 with a
> *Baseline* commit containing the project as it stood that day, so the earlier work is
> inside that commit rather than spread across the history. Release dates on the
> [CurseForge files page](https://www.curseforge.com/minecraft/mc-mods/baileys-extended-enchantments/files/all)
> are an independent record of the earlier timeline. Every commit date in this repository
> is the real date the work was committed.

The full per-release history is in [CHANGELOG.md](CHANGELOG.md).

## Building from source

Requires **JDK 17**.

```sh
./gradlew build        # Windows: gradlew.bat build
```

The jar is written to `build/libs/`. Running `./gradlew runClient` launches a development
client with the mod loaded.

## Licence

**All Rights Reserved.** See [LICENSE](LICENSE). The source is published so it can be read.
It is not open source, and it may not be copied, modified or redistributed without permission.
You are welcome to play the mod and include it in modpacks through CurseForge.
