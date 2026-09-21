package com.bailey.enchantments.item;

import com.bailey.enchantments.BaileysEnchantments;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;
import java.util.function.Supplier;

// The corrupted set's material — the Deep Dark half of the parallel endgame.
//
// Every stat here is NETHERITE-IDENTICAL, and deliberately so: Bailey's call on 2026-08-09
// was that the Deep Dark is an ALTERNATIVE route to the Nether, not a tier above it. The two
// sets are meant to be side-grades you choose between. Values read out of
// ArmorMaterials.NETHERITE's bytecode on 2026-08-14 rather than copied from memory:
// durability ×37, defense {boots 3, legs 6, chest 8, helmet 3}, toughness 3.0,
// knockback resistance 0.1, enchantment value 15.
//
// ⚠️ THE SET IS NOT fireResistant(). That is not an oversight — it is the entire reason these
// stay side-grades. Netherite keeps lava-proofing as its identity; corrupted armour burns.
// The absence lives in ModItems (fire resistance is an Item.Properties flag, not a material
// property), so if you are checking that the trade still holds, look there, not here.
//
// ⚠️ What corrupted armour actually wins with is SET BONUSES, not these numbers. Sonic boom
// is in the bypasses_armor tag, so armour points do literally nothing against the Warden's
// signature attack — see the header of DeepDarkBalance. Raising the values below would not
// make the Warden fight easier by even one point of damage.
public enum ModArmorMaterials implements ArmorMaterial {

    // ⚠️ THE NAMESPACE IN THIS NAME IS LOAD-BEARING — do not "tidy" it to just "corrupted".
    //
    // HumanoidArmorLayer.getArmorResource (Forge-patched, verified against 47.4.20 on
    // 2026-08-14) takes getName(), splits it on the first ':' into domain + texture, and
    // formats "%s:textures/models/armor/%s_layer_%d%s.png". With a bare "corrupted" the
    // domain falls back to "minecraft" and the game looks for
    // minecraft:textures/models/armor/corrupted_layer_1.png, which does not exist — the
    // armour then renders INVISIBLE on the body while the inventory icon looks perfectly
    // fine. That failure mode is silent and easy to misread as a modelling problem.
    // With the namespace it resolves to
    // assets/baileys_enchantments/textures/models/armor/corrupted_layer_{1,2}.png.
    CORRUPTED(BaileysEnchantments.MODID + ":corrupted", 37,
            defense(3, 8, 6, 3), 15,
            SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0F, 0.1F,
            // Echo shards, not netherite ingots — the set repairs with the currency of the
            // place it comes from → Brain/Decisions/Echo shards are the enchant currency.md
            () -> Ingredient.of(Items.ECHO_SHARD));

    // Vanilla's per-type durability base, multiplied by durabilityMultiplier. Copied because
    // ArmorMaterials.HEALTH_FUNCTION_FOR_TYPE is private and cannot be delegated to.
    private static final EnumMap<ArmorItem.Type, Integer> DURABILITY_BASE = defense(11, 16, 15, 13);

    private final String name;
    private final int durabilityMultiplier;
    private final EnumMap<ArmorItem.Type, Integer> protection;
    private final int enchantmentValue;
    private final SoundEvent equipSound;
    private final float toughness;
    private final float knockbackResistance;
    private final Supplier<Ingredient> repairIngredient;

    // Resolved on first use, never at enum-init time — Ingredient.of touches the item
    // registry, which is not populated while this enum is being class-loaded.
    private Ingredient resolvedRepairIngredient;

    ModArmorMaterials(String name, int durabilityMultiplier, EnumMap<ArmorItem.Type, Integer> protection,
                      int enchantmentValue, SoundEvent equipSound, float toughness,
                      float knockbackResistance, Supplier<Ingredient> repairIngredient) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.protection = protection;
        this.enchantmentValue = enchantmentValue;
        this.equipSound = equipSound;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.repairIngredient = repairIngredient;
    }

    // Named arguments in slot order so a transposed pair is visible at the call site.
    private static EnumMap<ArmorItem.Type, Integer> defense(int helmet, int chestplate, int leggings, int boots) {
        EnumMap<ArmorItem.Type, Integer> map = new EnumMap<>(ArmorItem.Type.class);
        map.put(ArmorItem.Type.HELMET, helmet);
        map.put(ArmorItem.Type.CHESTPLATE, chestplate);
        map.put(ArmorItem.Type.LEGGINGS, leggings);
        map.put(ArmorItem.Type.BOOTS, boots);
        return map;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return DURABILITY_BASE.get(type) * this.durabilityMultiplier;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return this.protection.get(type);
    }

    @Override
    public int getEnchantmentValue() {
        return this.enchantmentValue;
    }

    @Override
    public SoundEvent getEquipSound() {
        return this.equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        if (this.resolvedRepairIngredient == null) {
            this.resolvedRepairIngredient = this.repairIngredient.get();
        }
        return this.resolvedRepairIngredient;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public float getToughness() {
        return this.toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return this.knockbackResistance;
    }
}
