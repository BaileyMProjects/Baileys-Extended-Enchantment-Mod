package com.bailey.enchantments.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;

// Adds one item to an existing loot table, without owning that table.
//
// ⚠️ THIS EXISTS SPECIFICALLY SO THE MOD STOPS DOING WHAT IT DID TO THE WARDEN.
// data/minecraft/loot_tables/entities/warden.json is a WHOLESALE OVERRIDE of the vanilla
// table. It works, but it silently wins against every other mod that adds warden drops —
// last datapack loaded takes the entire table, so the other mod's additions simply vanish
// with no error and no log line. Ancient City chests are a far more contested table than the
// Warden, so repeating that pattern there would have been actively antisocial.
//
// A Global Loot Modifier is additive: any number of mods can attach to the same table and
// all of them apply. New loot goes through THIS class, never through a new file under
// data/minecraft/loot_tables/. → Brain/Reference/Modding gotchas.md
//
// Everything conditional is left to the loot conditions in the JSON rather than being fields
// here — which table to attach to (forge:loot_table_id) and how often (minecraft:random_chance)
// are both plain conditions, so this class stays a single job: append one item.
public class AddItemModifier extends LootModifier {

    public static final Codec<AddItemModifier> CODEC = RecordCodecBuilder.create(instance ->
            codecStart(instance)
                    .and(ForgeRegistries.ITEMS.getCodec().fieldOf("item").forGetter(m -> m.item))
                    .apply(instance, AddItemModifier::new));

    private final Item item;

    public AddItemModifier(LootItemCondition[] conditions, Item item) {
        super(conditions);
        this.item = item;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // LootModifier.apply has already tested every condition; doApply only runs when they
        // all passed, so there is nothing to re-check here.
        generatedLoot.add(new ItemStack(this.item));
        return generatedLoot;
    }

    @Override
    public Codec<? extends net.minecraftforge.common.loot.IGlobalLootModifier> codec() {
        return CODEC;
    }
}
