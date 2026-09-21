# Changelog

All notable changes to Bailey's Extended Enchantment Mod.

This changelog names bugs as well as features, including bugs you may never have noticed. If
something shipped broken, it gets written down here.

---

## 1.1.0

The first release since the 1.20.1 port. It adds the Deep Dark expansion, the Secret
enchantment tier, a reworked enchanting structure, and changes where enchantments come from.
It also fixes four bugs, two of which were present in the published version, and redesigns
all three custom screens.

⚠️ **Read "where boosted levels come from" below before updating.** Nothing is removed from
items you already own, but how new boosted levels are obtained has changed.

### 🎨 Changed — visuals

- **All three custom screens have been redesigned** — the Ancient Enchanting Table and both
  Ancient Anvil tabs. They now use proper bevelled panels and recessed slots instead of flat
  grey boxes, in a deepslate palette with sculk corruption creeping in from the edges.
- **The Dark Crystal and the Scarlet Crystal have been redrawn, and now look different from
  each other.** The Scarlet Crystal was previously the Dark Crystal's shape in a different
  colour; it is now a tall shard cluster against the Dark Crystal's squat cut gem.
- **Both crystals now pulse.** The Dark Crystal glows sculk-blue from within, the Scarlet
  Crystal red.
- The greyed-out slot hints were updated to match the new crystal shapes.

⚠️ Armour and block textures are unchanged and are still due a pass.

### ✨ Added — the Deep Dark expansion

A parallel progression path built around the Deep Dark, intended to make the Warden fight
survivable rather than impossible.

- **Corrupted armour set** — Corrupted Helmet, Chestplate, Leggings and Boots, upgraded from
  diamond via a new **Corrupted Upgrade Smithing Template** found in Ancient City chests. The
  template can be duplicated like a vanilla one.
- **Set bonuses for the full set** — immunity to **Darkness**, a movement speed bonus, and
  heavy damage reduction against Deep Dark sources including the Warden's sonic boom.
- **Sculkbane** — a damage enchantment that supersedes the Sharpness family rather than
  stacking with it.
- **Deepward** — a protection enchantment that supersedes the Protection family. It still
  allows Feather Falling, exactly as vanilla Protection does.
- **Shatter** — Sculkbane's Ultimate. **Silence** — Deepward's Ultimate.
- **Resonance** — the first Secret enchantment, gambled for with Execute.
- **Scarlet Crystal**, crafted from a **Warden Heart**, and the Secret ritual itself.

### ✨ Added — the reworked enchanting structure

- **Honest level caps.** The tables now offer the levels they can actually produce. A higher
  "true cap" tier was previously advertised but was unreachable in game; the numbers you see
  are now the numbers you get. ⚠️ No item lost a level — the caps on gear were always the real
  ones.
- **A Remove tab** on the Ancient Anvil, for stripping enchantments back off an item.
  Ultimates and Secrets cannot be removed — they are bound to the item.
- ⚠️ **Corrupted bookshelves are now the only thing that powers the Ancient Enchanting Table.**
  Ordinary bookshelves and sculk no longer contribute. **This is a deliberate break**: an
  existing Ancient Enchanting Table will read as unpowered until it is surrounded by corrupted
  bookshelves.

### ⚖️ Changed — balance

- **Protection's boosted cap lowered** from VIII to VII, with over-cap Protection rescaled so a
  full set lands at 70/75/80% reduction at V/VI/VII rather than overshooting.
- **The Warden now always drops 1–2 echo shards**, flat, with no Looting scaling.

### 🐛 Fixed

- **Librarian villagers could sell Ultimate enchantment books.** Villager trades are filtered on
  a different flag than loot and the enchanting table, and that flag was never set on Ultimates.
  The books could never actually be applied to anything, so this was a wasted purchase rather
  than a way to obtain an Ultimate — but it should never have been offered. Ultimates and
  Secrets now come from the Ancient Anvil and nothing else, with no exceptions.
- **The Ancient Enchanting Table could roll Curse of Binding and Curse of Vanishing.** The table
  deliberately accepts "treasure" enchantments, which is how this mod's own enchantments get
  into its pool; both vanilla curses are treasure enchantments and were riding in alongside
  them. Curses are now excluded from the table's pool entirely.
  **This fix is not retroactive** — an item that already picked up a curse from the table keeps
  it. Nothing about those items is broken; they just have a curse you did not ask for.
- **Enchantment books for this mod's enchantments generated in chests but could not be used.**
  World loot has always been able to produce them, and a normal anvil has always refused to
  apply them, so finding one did nothing. See *Changed* below.
- **Over-capped items were refused by normal anvils entirely.** A Sharpness VI sword could not
  be renamed, repaired, or combined. This was a deliberate guard against a real problem — a
  vanilla anvil silently knocks boosted levels back down to the vanilla maximum — but it was far
  broader than the problem it was guarding against.

### ⚖️ Changed — where boosted levels come from

- **The Ancient Anvil can no longer create a boosted level out of two vanilla-max ones.**
  Previously, combining two Protection IV books on an Ancient Anvil produced Protection V. It
  no longer does. The first boosted level of any enchantment now comes from the **Ancient
  Enchanting Table** and nowhere else.
- **Boosted levels can still be combined upward.** Protection **V + V still gives VI** — once
  you are above vanilla's maximum, merging works as before.
  - Protection IV + IV → **IV** (was V)
  - Protection V + V → **VI** (unchanged)
  - Protection V + IV → **V** (unchanged)
- ⚠️ **This removes something Ancient Anvils could previously do.** Items you already have keep
  every level they have — nothing is taken off your gear. What changes is how new boosted levels
  are obtained from here on.
- This mod's own enchantments are unaffected; they merge to their normal maximum exactly as
  before.

### ⚔️ Changed — Secret enchantments

- **Secret enchantments are now gambled for with an Ultimate, not with a maxed ordinary
  enchantment.** The Secret tier now sits above the Ultimate tier rather than beside it: you
  must earn the Ultimate first, and then risk it.
- **The odds are now 50%**, up from 20%. Both halves moved together — the chance went up
  because the stake went up. A failed roll destroys the Ultimate, so you have to re-max a base
  enchantment and re-run the Ultimate ritual before trying again.
- In practice this means roughly **15 Wardens per Secret instead of 25**, but around **two
  Ultimates spent** where it previously cost none.
- Resonance is now gambled for with **Execute**. As before, you cannot have both.
- The on-screen warning now reads "Destroys the Ultimate either way."

### 📝 Note on the corrupted set

- The full set grants **two** bonuses: Darkness immunity and a movement speed bonus, on top of
  its Deep Dark damage reduction. A third, "no sculk slowdown", was planned and dropped — sculk
  has never slowed players down in vanilla, so there was nothing to remove.

### ✨ Changed — how you get enchantments

The Deep Dark gear is meant to make the Deep Dark survivable, and it was gated behind beating
the Deep Dark first. This release opens up how the non-Ultimate enchantments are obtained,
without touching where the powerful ones come from.

- **A normal anvil can now apply this mod's enchantments** from an enchanted book. Scaling is
  still slow, because you have to find the books — but you no longer need an Ancient Anvil to
  use one at all.
- **This mod's enchantments still never appear in a normal enchanting table.** That has not
  changed, and will not.
- **Boosted "over-cap" levels are preserved by a normal anvil, but never created by one.**
  A normal anvil can never raise an enchantment above the higher of its normal maximum and the
  level you already had:
  - Sharpness VI + Sharpness VI → **VI** (merges, no longer downgraded)
  - Sharpness VI + Sharpness V → **VI** (your boosted level survives)
  - Sharpness V + Sharpness V → **V** (over-caps still come only from the Ancient Enchanting Table)
  - A boosted item can now be renamed and repaired normally.
- **Librarian trades no longer offer any of this mod's enchantments.** A villager can be
  re-rolled forever, which would have made "you have to find the books" meaningless. Chest and
  fishing loot are unchanged and remain the way books are found.
- **Ultimate and Secret enchantments remain exclusive to the Ancient Anvil.** (Secrets changed in
  other ways — see above — but not in where they come from.)
  They cannot be applied, traded for, found, or merged anywhere else.

---

## Released

> ⚠️ Which of these was the last published to CurseForge is not recorded. It does not affect
> 1.1.0, which is above both and clear of the `1.0.2` number that was used during development
> but never released.

### 1.0.1

Build produced 2026-06-16.

### 1.0.0 — port to Forge 1.20.1

The port from NeoForge 1.21.11 to Forge 1.20.1 (Java 17).

- Ancient Enchanting Table, powered by sculk
- Ancient Anvil, with both tabs
- Enchantment distribution: vanilla table gives vanilla enchantments only; the Ancient
  Enchanting Table gives boosted levels and this mod's enchantments, but no Ultimates; the
  Ancient Anvil is the only source of Ultimates
- Homing targets hostile mobs only, with improved close-range steering
