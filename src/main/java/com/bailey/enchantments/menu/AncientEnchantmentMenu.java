package com.bailey.enchantments.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.block.AncientEnchantingTableBlock;
import com.bailey.enchantments.block.ModBlocks;
import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.util.AncientEnchantContext;
import com.bailey.enchantments.util.EnchantPower;
import com.bailey.enchantments.util.OverCaps;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.registries.ForgeRegistries;

public class AncientEnchantmentMenu extends AbstractContainerMenu {
    static final ResourceLocation EMPTY_SLOT_ECHO_SHARD =
            new ResourceLocation(BaileysEnchantments.MODID, "container/slot/echo_shard");

    // ⚠️ ROW_POWER must stay {0, 8, 15}. The Phase 3 scan volume strictly contains
    // vanilla's ring, so an existing 15-sculk table still scores >= 15 and keeps row 3.
    // Raising these thresholds would strip row 3 from every published world.
    public static final int[] ROW_POWER = {0, 8, 15};
    public static final int[] REQUIRED_LEVELS = {10, 20, 30};
    public static final int[] LEVELS_CONSUMED = {3, 6, 9};
    public static final int[] SHARD_COSTS = {1, 1, 1};

    // Structure power buys better rolls, not higher caps — caps are a fixed table (see
    // OverCaps). The curve itself lives in EnchantPower.powerFactor, shared with the
    // anvil: 0 at or below the old vanilla-shaped maximum, so existing tables roll
    // identically and only big builds roll better.

    // How far up its range a roll is pushed at full power. 0.5 = halfway to the ceiling;
    // deliberately not 1.0, which would make every offer the cap and kill the variance.
    private static final float CEILING_BIAS_AT_FULL_POWER = 0.5F;

    private static final float UPGRADE_CHANCE = 0.15F;
    private static final float UPGRADE_CHANCE_MAX = 0.45F;
    private static final float[] ROW_BONUS_CHANCE = {0.35F, 0.45F, 0.55F};
    private static final float[] ROW_BONUS_CHANCE_MAX = {0.55F, 0.70F, 0.80F};

    private final Container enchantSlots = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            AncientEnchantmentMenu.this.slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;
    private final DataSlot enchantmentSeed = DataSlot.standalone();
    public final int[] costs = new int[3];
    public final int[] enchantClue = {-1, -1, -1};
    public final int[] levelClue = {-1, -1, -1};

    public AncientEnchantmentMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public AncientEnchantmentMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(ModMenuTypes.ANCIENT_ENCHANTING_TABLE.get(), containerId);
        this.access = access;
        this.addSlot(new Slot(this.enchantSlots, 0, 15, 47) {
            @Override
            public int getMaxStackSize() { return 1; }
        });
        this.addSlot(new Slot(this.enchantSlots, 1, 35, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) { return stack.is(Items.ECHO_SHARD); }
            @Override
            public com.mojang.datafixers.util.Pair<net.minecraft.resources.ResourceLocation, net.minecraft.resources.ResourceLocation> getNoItemIcon() {
                return com.mojang.datafixers.util.Pair.of(
                        net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS,
                        new net.minecraft.resources.ResourceLocation("item/empty_slot_amethyst_shard"));
            }
        });
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
        this.addDataSlot(DataSlot.shared(this.costs, 0));
        this.addDataSlot(DataSlot.shared(this.costs, 1));
        this.addDataSlot(DataSlot.shared(this.costs, 2));
        this.addDataSlot(this.enchantmentSeed).set(playerInventory.player.getEnchantmentSeed());
        this.addDataSlot(DataSlot.shared(this.enchantClue, 0));
        this.addDataSlot(DataSlot.shared(this.enchantClue, 1));
        this.addDataSlot(DataSlot.shared(this.enchantClue, 2));
        this.addDataSlot(DataSlot.shared(this.levelClue, 0));
        this.addDataSlot(DataSlot.shared(this.levelClue, 1));
        this.addDataSlot(DataSlot.shared(this.levelClue, 2));
    }

    @Override
    public void slotsChanged(Container inventory) {
        if (inventory != this.enchantSlots) return;
        ItemStack itemstack = inventory.getItem(0);
        if (!itemstack.isEmpty() && itemstack.isEnchantable()) {
            this.access.execute((level, pos) -> AncientEnchantContext.runActive(() -> {
                int power = EnchantPower.totalPower(level, pos);

                Set<Enchantment> usedEnchants = new HashSet<>();
                for (int row = 0; row < 3; row++) {
                    this.costs[row] = 0;
                    this.enchantClue[row] = -1;
                    this.levelClue[row] = -1;
                    if (power >= ROW_POWER[row]) {
                        List<EnchantmentInstance> offer = rollOffer(itemstack, row, false, usedEnchants, power);
                        if (!offer.isEmpty()) {
                            this.costs[row] = REQUIRED_LEVELS[row];
                            this.enchantClue[row] = BuiltInRegistries.ENCHANTMENT.getId(offer.get(0).enchantment);
                            this.levelClue[row] = offer.get(0).level;
                            usedEnchants.add(offer.get(0).enchantment);
                        }
                    }
                }
                this.broadcastChanges();
            }));
        } else {
            for (int i = 0; i < 3; i++) {
                this.costs[i] = 0;
                this.enchantClue[i] = -1;
                this.levelClue[i] = -1;
            }
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= this.costs.length) return false;
        ItemStack itemstack = this.enchantSlots.getItem(0);
        ItemStack shards = this.enchantSlots.getItem(1);
        int shardCost = SHARD_COSTS[id];
        if ((shards.isEmpty() || shards.getCount() < shardCost) && !player.getAbilities().instabuild) return false;
        if (this.costs[id] <= 0 || itemstack.isEmpty()
                || (player.experienceLevel < REQUIRED_LEVELS[id] && !player.getAbilities().instabuild)) return false;

        this.access.execute((level, pos) -> AncientEnchantContext.runActive(() -> {
            int power = EnchantPower.totalPower(level, pos);
            Set<Enchantment> usedEnchants = new HashSet<>();
            for (int r = 0; r < id; r++) {
                if (power >= ROW_POWER[r]) {
                    List<EnchantmentInstance> prior = rollOffer(itemstack, r, false, usedEnchants, power);
                    if (!prior.isEmpty()) usedEnchants.add(prior.get(0).enchantment);
                }
            }

            List<EnchantmentInstance> list = rollOffer(itemstack, id, true, usedEnchants, power);
            if (!list.isEmpty()) {
                player.onEnchantmentPerformed(itemstack, LEVELS_CONSUMED[id]);
                ItemStack result = applyEnchantments(itemstack, list);
                this.enchantSlots.setItem(0, result);

                shards.shrink(shardCost);
                if (shards.isEmpty()) this.enchantSlots.setItem(1, ItemStack.EMPTY);

                player.awardStat(Stats.ENCHANT_ITEM);
                if (player instanceof ServerPlayer sp) {
                    CriteriaTriggers.ENCHANTED_ITEM.trigger(sp, result, LEVELS_CONSUMED[id]);
                }

                this.enchantSlots.setChanged();
                this.enchantmentSeed.set(player.getEnchantmentSeed());
                this.slotsChanged(this.enchantSlots);
                level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS,
                        1.0F, level.random.nextFloat() * 0.1F + 0.9F);
            }
        }));
        return true;
    }

    private List<EnchantmentInstance> rollOffer(ItemStack stack, int row, boolean withExtras,
                                                 Set<Enchantment> exclude, int power) {
        RandomSource random = RandomSource.create(this.enchantmentSeed.get() + row);
        float factor = EnchantPower.powerFactor(power);

        // Gather candidates from the entire Forge enchantment registry
        List<Enchantment> candidates = new ArrayList<>();
        for (Enchantment ench : ForgeRegistries.ENCHANTMENTS.getValues()) {
            if (exclude.contains(ench)) continue;
            if (!ench.isDiscoverable()) continue;
            // 🐛 FIXED 2026-09-21 — the table could roll CURSES, and always could.
            //
            // Vanilla's own table never hits this because it filters on isTreasureOnly(), and
            // both curses are treasure-only. This table DELIBERATELY allows treasure — that is
            // how mod enchantments get into the pool at all — so the curses rode in with them:
            // BindingCurseEnchantment and VanishingCurseEnchantment override isTreasureOnly()
            // and isCurse(), but NOT isDiscoverable(), which defaults to true.
            //
            // ⚠️ Not retroactive. Items that already took a curse from this table keep it.
            if (ench.isCurse()) continue;
            if (com.bailey.enchantments.util.Ultimates.isUltimate(ench)) continue;
            if (!ench.canEnchant(stack) && !stack.is(Items.BOOK)) continue;
            candidates.add(ench);
        }
        if (candidates.isEmpty()) return List.of();

        Enchantment picked = candidates.get(random.nextInt(candidates.size()));
        int floor = OverCaps.tableFloor(picked);
        int ceiling = OverCaps.tableCeiling(picked);
        double t = (row + random.nextDouble()) / 3.0;
        // Push the roll toward the top of its range as the structure grows. t is a
        // fraction of the floor..ceiling span, so this can never exceed the ceiling.
        t += (1.0 - t) * factor * CEILING_BIAS_AT_FULL_POWER;
        int level = floor + (int) Math.round((ceiling - floor) * t);

        if (!withExtras) return List.of(new EnchantmentInstance(picked, level));

        if (random.nextFloat() < Mth.lerp(factor, UPGRADE_CHANCE, UPGRADE_CHANCE_MAX)
                && level < ceiling) level++;

        List<EnchantmentInstance> result = new ArrayList<>();
        result.add(new EnchantmentInstance(picked, level));

        float bonusChance = Mth.lerp(factor, ROW_BONUS_CHANCE[row], ROW_BONUS_CHANCE_MAX[row]);
        while (random.nextFloat() < bonusChance) {
            List<Enchantment> compatible = new ArrayList<>();
            for (Enchantment c : candidates) {
                boolean ok = true;
                for (EnchantmentInstance inst : result) {
                    if (inst.enchantment.equals(c) || !inst.enchantment.isCompatibleWith(c)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) compatible.add(c);
            }
            if (compatible.isEmpty()) break;
            Enchantment bonus = compatible.get(random.nextInt(compatible.size()));
            result.add(new EnchantmentInstance(bonus, OverCaps.tableFloor(bonus)));
            bonusChance /= 2.0F;
        }
        return result;
    }

    // Applies a list of enchantment instances to an item, merging into existing enchantments.
    // Handles books (converts to enchanted book with stored enchantments).
    private static ItemStack applyEnchantments(ItemStack base, List<EnchantmentInstance> enchantments) {
        if (base.is(Items.BOOK)) {
            ItemStack enchBook = new ItemStack(Items.ENCHANTED_BOOK);
            Map<Enchantment, Integer> stored = new HashMap<>();
            for (EnchantmentInstance inst : enchantments) {
                stored.merge(inst.enchantment, inst.level, Math::max);
            }
            EnchantmentHelper.setEnchantments(stored, enchBook);
            return enchBook;
        }
        ItemStack result = base.copy();
        Map<Enchantment, Integer> existing = new HashMap<>(EnchantmentHelper.getEnchantments(result));
        for (EnchantmentInstance inst : enchantments) {
            existing.merge(inst.enchantment, inst.level, Math::max);
        }
        EnchantmentHelper.setEnchantments(existing, result);
        return result;
    }

    public int getShardCount() {
        ItemStack stack = this.enchantSlots.getItem(1);
        return stack.isEmpty() ? 0 : stack.getCount();
    }

    public int getEnchantmentSeed() {
        return this.enchantmentSeed.get();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> this.clearContainer(player, this.enchantSlots));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.ANCIENT_ENCHANTING_TABLE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack inSlot = slot.getItem();
            result = inSlot.copy();
            if (index == 0) {
                if (!this.moveItemStackTo(inSlot, 2, 38, true)) return ItemStack.EMPTY;
            } else if (index == 1) {
                if (!this.moveItemStackTo(inSlot, 2, 38, true)) return ItemStack.EMPTY;
            } else if (inSlot.is(Items.ECHO_SHARD)) {
                if (!this.moveItemStackTo(inSlot, 1, 2, true)) return ItemStack.EMPTY;
            } else {
                if (this.slots.get(0).hasItem() || !this.slots.get(0).mayPlace(inSlot)) return ItemStack.EMPTY;
                ItemStack single = inSlot.copyWithCount(1);
                inSlot.shrink(1);
                this.slots.get(0).setByPlayer(single);
            }
            if (inSlot.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            if (inSlot.getCount() == result.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, inSlot);
        }
        return result;
    }
}
