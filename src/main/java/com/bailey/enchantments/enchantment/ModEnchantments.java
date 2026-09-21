package com.bailey.enchantments.enchantment;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.util.DeepDarkBalance;
import com.bailey.enchantments.util.EnchantCommandOverride;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.DamageEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, BaileysEnchantments.MODID);

    // Custom category for axe-only enchantments
    public static final EnchantmentCategory AXES =
            EnchantmentCategory.create("BAILEY_AXES", item -> item instanceof AxeItem);

    // -------------------------------------------------------------------------
    // Ultimates: only available through the Ancient Enchanting Table
    // -------------------------------------------------------------------------
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final EquipmentSlot[] ALL_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /**
     * The flat damage an Ultimate on the Sharpness lineage carries, matching what Sharpness VII
     * would give.
     *
     * <p>⚠️ <b>This exists because the ritual DESTROYS the maxed Sharpness it upgrades from.</b>
     * Bailey, 2026-09-21: <i>"the ultimate and secret ability have no damage buff like sharpness
     * so you should change that to match what sharpness 7 would be."</i> He is right, and it was
     * a real hole: a sword going from Sharpness VI to Execute LOST 3.5 flat damage and gained
     * only a 20% multiplier, so the mod's most expensive upgrade could be a raw downgrade.
     *
     * <p>Derived from vanilla, not guessed — {@code DamageEnchantment.getDamageBonus} for the
     * Sharpness type is {@code 1.0F + max(0, level - 1) * 0.5F}, verified from the 1.20.1
     * bytecode. Level 7 gives {@code 1.0 + 6 * 0.5 = 4.0}.
     *
     * <p>⚠️ VII, one above the boosted Sharpness cap of VI in {@link com.bailey.enchantments.util.OverCaps},
     * so the upgrade is a genuine step up rather than a sidestep. If that cap ever moves, this
     * should move with it.
     */
    private static final float SHARPNESS_VII_DAMAGE = 4.0F;

    public static final RegistryObject<Enchantment> EXECUTE = ENCHANTMENTS.register("execute",
            () -> new UltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.WEAPON, EquipmentSlot.MAINHAND) {
                @Override
                public float getDamageBonus(int level, MobType type) { return SHARPNESS_VII_DAMAGE; }
            });

    public static final RegistryObject<Enchantment> OBLITERATE = ENCHANTMENTS.register("obliterate",
            () -> new UltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.BOW, EquipmentSlot.MAINHAND));

    public static final RegistryObject<Enchantment> OVERDRIVE = ENCHANTMENTS.register("overdrive",
            () -> new UltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.DIGGER, EquipmentSlot.MAINHAND));

    public static final RegistryObject<Enchantment> AEGIS = ENCHANTMENTS.register("aegis",
            () -> new UltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.ARMOR, ARMOR_SLOTS));

    public static final RegistryObject<Enchantment> ZEPHYR = ENCHANTMENTS.register("zephyr",
            () -> new UltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.ARMOR_FEET, EquipmentSlot.FEET));

    public static final RegistryObject<Enchantment> ETERNAL = ENCHANTMENTS.register("eternal",
            () -> new UltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.BREAKABLE, ALL_SLOTS));

    public static final RegistryObject<Enchantment> ABUNDANCE = ENCHANTMENTS.register("abundance",
            () -> new UltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.DIGGER, EquipmentSlot.MAINHAND));

    public static final RegistryObject<Enchantment> PLUNDER = ENCHANTMENTS.register("plunder",
            () -> new UltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.WEAPON, EquipmentSlot.MAINHAND));

    public static final RegistryObject<Enchantment> RETRIBUTION = ENCHANTMENTS.register("retribution",
            () -> new UltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.ARMOR, ARMOR_SLOTS));

    public static final RegistryObject<Enchantment> SHOCKWAVE = ENCHANTMENTS.register("shockwave",
            () -> new UltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.WEAPON, EquipmentSlot.MAINHAND));

    public static final RegistryObject<Enchantment> TEMPEST = ENCHANTMENTS.register("tempest",
            () -> new UltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.BOW, EquipmentSlot.MAINHAND));

    public static final RegistryObject<Enchantment> LIGHTNING_STRIKE = ENCHANTMENTS.register("lightning_strike",
            () -> new UltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.WEAPON, EquipmentSlot.MAINHAND));

    public static final RegistryObject<Enchantment> TIMBER = ENCHANTMENTS.register("timber",
            () -> new UltimateEnchantment(Enchantment.Rarity.VERY_RARE, AXES, EquipmentSlot.MAINHAND));

    public static final RegistryObject<Enchantment> MULTISHOT = ENCHANTMENTS.register("multishot",
            () -> new UltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.BOW, EquipmentSlot.MAINHAND));


    /**
     * The evasion armour line, designed by Bailey 2026-09-22.
     *
     * <p><b>Mirage</b> upgrades from <b>Projectile Protection</b>, and that base was chosen for
     * a structural reason rather than a thematic one. Swift Sneak was the prettier fit — the
     * Ancient City's own enchantment — but it is <b>leggings-only</b>, and the ritual upgrades a
     * maxed base on the item carrying it, so a full set of Mirage would have been
     * <b>unobtainable</b>. Projectile Protection is on all four pieces.
     *
     * <p>✅ It also buys the competition for free: the protection family is mutually exclusive in
     * vanilla and only one Ultimate fits per item, so every armour piece is <b>Aegis or
     * Mirage</b>, never both.
     *
     * <p>⚠️ Knock-on: {@code Deepward} supersedes the whole protection family, so an evasion set
     * gives up Deepward. That is a deliberate build tradeoff.
     */
    public static final RegistryObject<Enchantment> MIRAGE = ENCHANTMENTS.register("mirage",
            () -> new UltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.ARMOR, ARMOR_SLOTS));

    /**
     * Mirage's Secret: sharper evasion, plus a toggleable mode with the flash step.
     *
     * <p>⚠️ The mode needs a <b>full corrupted set</b> plus at least one Dissonance piece —
     * "corruption spreads". Win the gamble once and it carries across the set, rather than
     * needing all four pieces won separately, which would have cost ~80 Wardens instead of ~15.
     * Behaviour lives in {@code event/MirageEvents.java}.
     */
    public static final RegistryObject<Enchantment> DISSONANCE = ENCHANTMENTS.register("dissonance",
            () -> new SecretEnchantment(EnchantmentCategory.ARMOR, ARMOR_SLOTS));
    public static final RegistryObject<Enchantment> HOMING = ENCHANTMENTS.register("homing",
            () -> new SimpleModEnchantment(Enchantment.Rarity.RARE, EnchantmentCategory.BOW, 1, EquipmentSlot.MAINHAND));

    // -------------------------------------------------------------------------
    // Common tier: appear in both vanilla and ancient enchanting table
    // -------------------------------------------------------------------------
    public static final RegistryObject<Enchantment> WITHER_TOUCH = ENCHANTMENTS.register("wither_touch",
            () -> new WeaponEffectEnchantment(Enchantment.Rarity.UNCOMMON, 3));

    public static final RegistryObject<Enchantment> LAVA_WALKER = ENCHANTMENTS.register("lava_walker",
            () -> new SimpleModEnchantment(Enchantment.Rarity.RARE, EnchantmentCategory.ARMOR_FEET, 1, EquipmentSlot.FEET) {
                @Override
                public boolean isTreasureOnly() { return true; } // boots only via chest loot or table
            });

    public static final RegistryObject<Enchantment> AUTO_SMELT = ENCHANTMENTS.register("auto_smelt",
            () -> new SimpleModEnchantment(Enchantment.Rarity.RARE, EnchantmentCategory.DIGGER, 1, EquipmentSlot.MAINHAND));

    public static final RegistryObject<Enchantment> VEIN_MINER = ENCHANTMENTS.register("vein_miner",
            () -> new SimpleModEnchantment(Enchantment.Rarity.RARE, EnchantmentCategory.DIGGER, 1, EquipmentSlot.MAINHAND));

    // -------------------------------------------------------------------------
    // Batch 1: multi-level enchantments
    // -------------------------------------------------------------------------
    public static final RegistryObject<Enchantment> VENOM = ENCHANTMENTS.register("venom",
            () -> new WeaponEffectEnchantment(Enchantment.Rarity.UNCOMMON, 3));

    public static final RegistryObject<Enchantment> FROSTBITE = ENCHANTMENTS.register("frostbite",
            () -> new WeaponEffectEnchantment(Enchantment.Rarity.UNCOMMON, 2));

    public static final RegistryObject<Enchantment> VAMPIRIC = ENCHANTMENTS.register("vampiric",
            () -> new SimpleModEnchantment(Enchantment.Rarity.RARE, EnchantmentCategory.WEAPON, 2, EquipmentSlot.MAINHAND));

    public static final RegistryObject<Enchantment> SWIFTNESS = ENCHANTMENTS.register("swiftness",
            () -> new SimpleModEnchantment(Enchantment.Rarity.UNCOMMON, EnchantmentCategory.ARMOR_FEET, 3, EquipmentSlot.FEET));

    public static final RegistryObject<Enchantment> VITALITY = ENCHANTMENTS.register("vitality",
            () -> new SimpleModEnchantment(Enchantment.Rarity.RARE, EnchantmentCategory.ARMOR, 2, ARMOR_SLOTS));

    // -------------------------------------------------------------------------
    // Deep Dark tier: a parallel ladder to Sharpness/Protection rather than an
    // addition to it. Each one supersedes its vanilla family instead of stacking
    // with it, so the choice is "the vanilla enchant or the deep one", never both.
    // Bonus behaviour lives in event/DeepDarkCombatEvents.java.
    // -------------------------------------------------------------------------
    public static final RegistryObject<Enchantment> SCULKBANE = ENCHANTMENTS.register("sculkbane",
            () -> new DeepDarkEnchantment(Enchantment.Rarity.RARE, EnchantmentCategory.WEAPON,
                    DeepDarkBalance.SCULKBANE_MAX_LEVEL, DamageEnchantment.class, EquipmentSlot.MAINHAND));

    public static final RegistryObject<Enchantment> DEEPWARD = ENCHANTMENTS.register("deepward",
            () -> new DeepDarkEnchantment(Enchantment.Rarity.RARE, EnchantmentCategory.ARMOR,
                    DeepDarkBalance.DEEPWARD_MAX_LEVEL, ProtectionEnchantment.class, ARMOR_SLOTS));

    // The deep ladder's ceiling — a second Ultimate tier beside the vanilla-based one.
    // Sculkbane V → Shatter, Deepward IV → Silence, both through the Ancient Anvil ritual
    // like every other Ultimate. Behaviour in event/DeepDarkUltimateEvents.java.
    public static final RegistryObject<Enchantment> SHATTER = ENCHANTMENTS.register("shatter",
            () -> new DeepDarkUltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.WEAPON,
                    DamageEnchantment.class, EquipmentSlot.MAINHAND));

    public static final RegistryObject<Enchantment> SILENCE = ENCHANTMENTS.register("silence",
            () -> new DeepDarkUltimateEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.ARMOR,
                    ProtectionEnchantment.class, ARMOR_SLOTS));

    // -------------------------------------------------------------------------
    // Secret tier: the red gamble. Not stronger than an Ultimate — different.
    //
    // ⚠️ SecretEnchantment extends UltimateEnchantment ON PURPOSE, and that inheritance is
    // load-bearing rather than convenience. Ultimates.isUltimate() tests
    // `instanceof UltimateEnchantment`, so every rule already written for Ultimates — the
    // Remove tab's filter, removeAllUltimates, one-per-item, treasure-only, undiscoverable,
    // never placed by loot — covers Secrets automatically. Making this a sibling class
    // instead would silently make Secrets REMOVABLE at the Remove tab, which contradicts
    // Brain/Decisions/Ultimates are bound to the item.md.
    //
    // Behaviour lives in event/SecretEnchantmentEvents.java.
    // -------------------------------------------------------------------------
    public static final RegistryObject<Enchantment> RESONANCE = ENCHANTMENTS.register("resonance",
            () -> new SecretEnchantment(EnchantmentCategory.WEAPON, EquipmentSlot.MAINHAND) {
                // Resonance is gambled FROM Execute and keeps its perks, so it keeps its damage
                // too -- anything less would make winning the gamble a damage loss.
                @Override
                public float getDamageBonus(int level, MobType type) { return SHARPNESS_VII_DAMAGE; }
            });

    // -------------------------------------------------------------------------
    // Base class for simple non-ultimate enchantments
    // -------------------------------------------------------------------------
    public static class SimpleModEnchantment extends Enchantment {
        private final int maxLevel;

        public SimpleModEnchantment(Rarity rarity, EnchantmentCategory category, int maxLevel, EquipmentSlot... slots) {
            super(rarity, category, slots);
            this.maxLevel = maxLevel;
        }

        @Override
        public int getMaxLevel() { return this.maxLevel; }

        @Override
        public int getMinCost(int level) { return 10 + (level - 1) * 20; }

        @Override
        public int getMaxCost(int level) { return getMinCost(level) + 50; }

        // Keep all mod enchantments out of the vanilla enchanting table's roll pool.
        @Override
        public boolean isTreasureOnly() { return true; }

        // Closed 2026-09-21. Librarian trades filter the registry on isTradeable() and NOTHING
        // else — not isTreasureOnly, not isDiscoverable — so leaving this at its default of
        // true handed the entire mod roster to anyone with a lectern. A librarian can be
        // re-rolled indefinitely, which would have made "you have to find the books" false.
        // Chest loot stays as the faucet. -> Brain/Decisions/Enchantment sourcing.md
        @Override
        public boolean isTradeable() { return false; }

        // A NORMAL ANVIL MAY APPLY THESE (decided 2026-09-21).
        //
        // This used to require AncientEnchantContext, which meant a mod enchantment could only
        // ever be applied inside our own menus. Combined with isDiscoverable() being left at
        // true, that made every mod-enchant book generated in world loot a dead end: the game
        // handed them out and then refused to let anyone use them.
        //
        // Books still generate exactly where they did before; the difference is that a plain
        // vanilla anvil now accepts one. Scaling stays gated on finding books rather than on
        // beating the Deep Dark first, which was the whole point.
        //
        // ⚠️ The BOOK short-circuit is deliberate and load-bearing: super.canEnchant() asks the
        // category, and no category accepts a plain book, so dropping it would change what
        // vanilla's loot functions are willing to put an enchantment on.
        //
        // ⚠️ Ultimates and Secrets do NOT inherit this. UltimateEnchantment overrides
        // canEnchant separately and stays sealed to the Ancient Anvil.
        @Override
        public boolean canEnchant(ItemStack stack) {
            if (stack.is(Items.BOOK)) return true;
            return super.canEnchant(stack);
        }
    }

    // -------------------------------------------------------------------------
    // Weapon effect enchantments: mutually exclusive with each other
    // -------------------------------------------------------------------------
    public static class WeaponEffectEnchantment extends SimpleModEnchantment {
        public WeaponEffectEnchantment(Rarity rarity, int maxLevel) {
            super(rarity, EnchantmentCategory.WEAPON, maxLevel, EquipmentSlot.MAINHAND);
        }

        @Override
        protected boolean checkCompatibility(Enchantment other) {
            if (other instanceof WeaponEffectEnchantment) return false;
            return super.checkCompatibility(other);
        }
    }

    // -------------------------------------------------------------------------
    // Deep Dark enchantments: each one supersedes a whole vanilla family.
    //
    // ⚠️ EXCLUSIVITY IS THIS OVERRIDE, AND ONLY THIS OVERRIDE. On 1.20.1 there is no
    // data-driven exclusivity — the tags/enchantment/exclusive/ JSONs left on disk by the
    // 1.21 port load nothing, warn about nothing, and would let these stack silently.
    // See Brain/Reference/Port cruft in resources.md before "fixing" this with a JSON.
    //
    // `supersedes` is the vanilla base class this replaces, so the rule covers every member
    // of the family at once — Sharpness/Smite/Bane of Arthropods, or the protection types —
    // and picks up modded subclasses of them for free.
    // -------------------------------------------------------------------------
    public static class DeepDarkEnchantment extends SimpleModEnchantment {
        private final Class<? extends Enchantment> supersedes;

        public DeepDarkEnchantment(Rarity rarity, EnchantmentCategory category, int maxLevel,
                                   Class<? extends Enchantment> supersedes, EquipmentSlot... slots) {
            super(rarity, category, maxLevel, slots);
            this.supersedes = supersedes;
        }

        @Override
        protected boolean checkCompatibility(Enchantment other) {
            if (supersedesFamily(this.supersedes, other)) return false;
            return super.checkCompatibility(other);
        }
    }

    // Shatter and Silence: Ultimates in every respect (treasure-only, undiscoverable, one per
    // item, ritual-placed), that additionally keep the deep ladder's exclusivity.
    //
    // ⚠️ Java has no multiple inheritance, so this cannot extend both UltimateEnchantment and
    // DeepDarkEnchantment. The Ultimate half is inherited — the more dangerous half to get
    // wrong — and the one-line exclusivity rule is shared through supersedesFamily() rather
    // than copied, so the two can never drift apart.
    //
    // ⚠️ Without this, exclusivity would evaporate exactly at the top of the ladder: the
    // ritual removes Sculkbane when it grants Shatter, leaving a sword that would happily
    // take Sharpness back on in the Forge tab and run both damage ladders at once.
    public static class DeepDarkUltimateEnchantment extends UltimateEnchantment {
        private final Class<? extends Enchantment> supersedes;

        public DeepDarkUltimateEnchantment(Rarity rarity, EnchantmentCategory category,
                                           Class<? extends Enchantment> supersedes, EquipmentSlot... slots) {
            super(rarity, category, slots);
            this.supersedes = supersedes;
        }

        @Override
        protected boolean checkCompatibility(Enchantment other) {
            if (supersedesFamily(this.supersedes, other)) return false;
            return super.checkCompatibility(other);
        }
    }

    // The deep ladder's one exclusivity rule, in one place, used by both tiers.
    //
    // Feather Falling is deliberately exempt. It is a ProtectionEnchantment by class, but
    // vanilla itself lets it coexist with every other protection type because it answers a
    // different problem (falling, not being hit). Excluding it would mean Deepward boots could
    // never take Feather Falling — a harsher rule than vanilla applies to its own Protection,
    // and not what "an alternative to Protection" means. Identity check rather than reading
    // ProtectionEnchantment.type, so this assumes nothing about a field that is not ours.
    private static boolean supersedesFamily(Class<? extends Enchantment> family, Enchantment other) {
        if (other == Enchantments.FALL_PROTECTION) return false;
        return family.isInstance(other);
    }

    // -------------------------------------------------------------------------
    // Secret enchantments: an Ultimate in every mechanical respect, distinguished only by
    // how it is obtained (a 20% gamble that destroys the base enchantment either way) and
    // how it is drawn (red, not pink).
    //
    // ⚠️ Deliberately adds NO new rules. Everything that makes an Ultimate an Ultimate is
    // inherited unchanged; if this class ever needs to override canEnchant or isTreasureOnly,
    // that is a sign the Secret tier has drifted from being "a differently-sourced Ultimate"
    // into something that needs its own audit.
    // -------------------------------------------------------------------------
    public static class SecretEnchantment extends UltimateEnchantment {
        public SecretEnchantment(EnchantmentCategory category, EquipmentSlot... slots) {
            super(Rarity.VERY_RARE, category, slots);
        }
    }

    // -------------------------------------------------------------------------
    // Base class for ultimate enchantments: no vanilla table, no stacking
    // -------------------------------------------------------------------------
    public static class UltimateEnchantment extends Enchantment {
        public UltimateEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot... slots) {
            super(rarity, category, slots);
        }

        @Override
        public int getMaxLevel() { return 1; }

        @Override
        public int getMinCost(int level) { return 100; }

        @Override
        public int getMaxCost(int level) { return 250; }

        // Out of the vanilla enchanting table's pool.
        //
        // ⚠️ This comment used to read "Ultimates never show up in villager trades or loot",
        // which was simply false — isTreasureOnly() governs the enchanting table and nothing
        // else. Loot was in fact blocked, but by isDiscoverable() below; trades were not
        // blocked at all. See isTradeable().
        @Override
        public boolean isTreasureOnly() { return true; }

        // 🐛 FIXED 2026-09-21 — this was the one real leak in the Ultimate seal, and it shipped.
        //
        // VillagerTrades.EnchantBookForEmeralds filters the registry on isTradeable() ALONE.
        // It does not consult isTreasureOnly, isDiscoverable or canEnchant. With this left at
        // its default of true, a librarian could offer an Execute book for emeralds.
        //
        // canEnchant() below still refused to apply it, so the damage was a trap purchase
        // rather than a balance break — but it contradicted "Ultimates only come from the
        // Ancient Anvil" in the published jar.
        //
        // Inherited by SecretEnchantment and DeepDarkUltimateEnchantment, which is the point:
        // the seal is defined once, here.
        @Override
        public boolean isTradeable() { return false; }

        // Belt-and-suspenders: even if something ever rolled treasure-only enchantments,
        // ultimates must never be discoverable by vanilla's random-enchant loot functions
        // (enchant_randomly / enchant_with_levels), on books or items. The Ancient Anvil's
        // ultimate-upgrade tab never goes through this — it assigns the ultimate directly.
        @Override
        public boolean isDiscoverable() { return false; }

        // Ultimates are normally only ever placed by the Ancient Anvil's ultimate-upgrade
        // ritual, which writes the enchantment in directly and never calls canEnchant() at
        // all. The one deliberate exception is an operator's /enchant command — gated by
        // EnchantCommandOverride, set only while that permission-gated command is running.
        // Every other path (loot, fishing, trades, random-enchant commands/functions) leaves
        // this false on both books and items.
        @Override
        public boolean canEnchant(ItemStack stack) { return EnchantCommandOverride.isActive(); }

        // An item can never have two ultimates at once
        @Override
        protected boolean checkCompatibility(Enchantment other) {
            if (other instanceof UltimateEnchantment) return false;
            return super.checkCompatibility(other);
        }
    }
}
