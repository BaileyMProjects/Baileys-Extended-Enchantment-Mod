package com.bailey.enchantments.menu;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.block.ModBlocks;
import com.bailey.enchantments.item.ModItems;
import com.bailey.enchantments.event.SecretRitualEvents;
import com.bailey.enchantments.util.AncientEnchantContext;
import com.bailey.enchantments.util.SecretReveal;
import com.bailey.enchantments.util.EnchantPower;
import com.bailey.enchantments.util.DeepDarkBalance;
import com.bailey.enchantments.util.OverCaps;
import com.bailey.enchantments.util.Secrets;
import com.bailey.enchantments.util.Ultimates;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AncientAnvilMenu extends AbstractContainerMenu {
    public static final int TAB_FORGE = 0;
    public static final int TAB_ULTIMATE = 1;
    public static final int TAB_DISENCHANT = 2;
    // ⚠️ Tab buttons must be ids 0..n-1 in tab order: AncientAnvilScreen.tabAt() maps a
    // tab's index directly to its button id. BTN_CYCLE_UPGRADE used to be 2 and had to
    // move when the third tab took that id.
    private static final int BTN_TAB_FORGE = 0;
    private static final int BTN_TAB_ULTIMATE = 1;
    private static final int BTN_TAB_DISENCHANT = 2;
    public static final int BTN_CYCLE_UPGRADE = 3;
    public static final int BTN_CYCLE_DISENCHANT = 4;
    // The Secret tier's toggle. ⚠️ Ids 0..2 are reserved for tabs by AncientAnvilScreen.tabAt();
    // anything new must start at 3 or above.
    public static final int BTN_TOGGLE_SECRET = 5;
    private static final int ULTIMATE_COST = 30;
    // ⚠️ The ritual gets cheaper with a bigger structure but never free — this floor is
    // what stops a large build trivialising the mod's single most expensive action.
    private static final int ULTIMATE_COST_FLOOR = 15;
    private static final int MAX_NAME_LENGTH = 50;
    // Vanilla per-level cost formulas get steep at the boosted levels we allow past vanilla max
    // (e.g. Sharpness VI = 56 levels on its own). Scale it down so one merge isn't a huge grind.
    private static final double FORGE_COST_SCALE = 0.7;
    private static final double FORGE_COST_SCALE_MIN = 0.4;
    // Selective removal. Cheaper than the ritual by a wide margin — it's a correction,
    // not an achievement — but never free, so it can't be spammed to fish for rolls.
    private static final int DISENCHANT_COST = 10;
    private static final int DISENCHANT_COST_FLOOR = 3;

    private static final ResourceLocation EMPTY_SLOT_TEMPLATE =
            new ResourceLocation(BaileysEnchantments.MODID, "container/slot/echo_upgrade_template");
    private static final ResourceLocation EMPTY_SLOT_CRYSTAL =
            new ResourceLocation(BaileysEnchantments.MODID, "container/slot/dark_crystal");

    private static final int FORGE_BASE = 0;
    private static final int FORGE_ADDITIONAL = 1;
    private static final int FORGE_RESULT = 2;
    private static final int ULT_GEAR = 3;
    private static final int ULT_TEMPLATE = 4;
    private static final int ULT_CRYSTAL = 5;
    private static final int ULT_RESULT = 6;
    private static final int DIS_INPUT = 7;
    private static final int DIS_RESULT = 8;
    private static final int INV_START = 9;
    private static final int INV_END = INV_START + 36;

    private final ContainerLevelAccess access;
    private final Player owner;

    private final Container forgeInputs = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            AncientAnvilMenu.this.slotsChanged(this);
        }
    };
    private final ResultContainer forgeResult = new ResultContainer();

    private final Container ultimateInputs = new SimpleContainer(3) {
        @Override
        public void setChanged() {
            super.setChanged();
            AncientAnvilMenu.this.slotsChanged(this);
        }
    };
    private final ResultContainer ultimateResult = new ResultContainer();

    private final Container disenchantInput = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            AncientAnvilMenu.this.slotsChanged(this);
        }
    };
    private final ResultContainer disenchantResult = new ResultContainer();

    private final DataSlot activeTab = DataSlot.standalone();
    private final DataSlot forgeCost = DataSlot.standalone();
    private final DataSlot ultimateReady = DataSlot.standalone();
    private final DataSlot upgradeCount = DataSlot.standalone();
    private final DataSlot selectedUpgrade = DataSlot.standalone();
    // ⚠️ Must be a DataSlot, not a getter. The client-side menu is built with
    // ContainerLevelAccess.NULL and so cannot scan the structure; without syncing, the
    // screen would advertise the full 30 while the server charged the scaled price.
    private final DataSlot ultimateCost = DataSlot.standalone();

    private final DataSlot disenchantCount = DataSlot.standalone();
    private final DataSlot selectedDisenchant = DataSlot.standalone();
    private final DataSlot disenchantCost = DataSlot.standalone();
    // Registry id + level of the enchantment currently targeted for removal, so the
    // screen can name it. Same trick AncientEnchantmentMenu uses for its row clues —
    // the option list itself is server-side only.
    // 1 while the player has flicked the Secret toggle on. A DataSlot because the SCREEN
    // needs it to draw the toggle, the odds and the warning, and the client menu cannot see
    // server state any other way.
    private final DataSlot secretMode = DataSlot.standalone();

    private final DataSlot disenchantClue = DataSlot.standalone();
    private final DataSlot disenchantClueLevel = DataSlot.standalone();

    private List<Enchantment> upgradeOptions = new ArrayList<>();
    private List<Enchantment> removableOptions = new ArrayList<>();

    private int repairItemCountCost;
    private String itemName;

    public AncientAnvilMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public AncientAnvilMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(ModMenuTypes.ANCIENT_ANVIL.get(), containerId);
        this.access = access;
        this.owner = playerInventory.player;

        this.addSlot(new TabSlot(this.forgeInputs, 0, 27, 47, TAB_FORGE));
        this.addSlot(new TabSlot(this.forgeInputs, 1, 76, 47, TAB_FORGE));
        this.addSlot(new ForgeResultSlot(this.forgeResult, 0, 134, 47));

        this.addSlot(new TabSlot(this.ultimateInputs, 0, 26, 48, TAB_ULTIMATE) {
            @Override
            public boolean mayPlace(ItemStack stack) { return canHoldEnchantments(stack); }
            @Override
            public com.mojang.datafixers.util.Pair<net.minecraft.resources.ResourceLocation, net.minecraft.resources.ResourceLocation> getNoItemIcon() {
                return com.mojang.datafixers.util.Pair.of(
                        net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS,
                        new net.minecraft.resources.ResourceLocation("item/empty_slot_sword"));
            }
        });
        this.addSlot(new TabSlot(this.ultimateInputs, 1, 8, 48, TAB_ULTIMATE) {
            @Override
            public boolean mayPlace(ItemStack stack) { return stack.is(ModItems.ECHO_UPGRADE_TEMPLATE.get()); }
            @Override
            public com.mojang.datafixers.util.Pair<net.minecraft.resources.ResourceLocation, net.minecraft.resources.ResourceLocation> getNoItemIcon() {
                return com.mojang.datafixers.util.Pair.of(
                        net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS,
                        new net.minecraft.resources.ResourceLocation("baileys_enchantments", "item/empty_slot_echo_upgrade_template"));
            }
        });
        this.addSlot(new TabSlot(this.ultimateInputs, 2, 44, 48, TAB_ULTIMATE) {
            @Override
            // Both crystals are accepted here and the MISMATCH is rejected in
            // createUltimateResult instead. Gating the slot on secretMode would mean flicking
            // the toggle could invalidate an item already sitting in the slot, and vanilla has
            // no good answer for a slot whose contents stop being legal.
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.DARK_CRYSTAL.get()) || stack.is(ModItems.SCARLET_CRYSTAL.get());
            }
            @Override
            public com.mojang.datafixers.util.Pair<net.minecraft.resources.ResourceLocation, net.minecraft.resources.ResourceLocation> getNoItemIcon() {
                return com.mojang.datafixers.util.Pair.of(
                        net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS,
                        new net.minecraft.resources.ResourceLocation("baileys_enchantments",
                                AncientAnvilMenu.this.isSecretMode() ? "item/empty_slot_scarlet_crystal" : "item/empty_slot_dark_crystal"));
            }
        });
        this.addSlot(new UltimateResultSlot(this.ultimateResult, 0, 98, 48));

        // Deliberately sharing the Ultimate tab's slot coordinates: this tab reuses that
        // background texture, so the input and result land on boxes the texture already
        // draws. The two unused boxes at x=8 and x=44 are cosmetic only.
        this.addSlot(new TabSlot(this.disenchantInput, 0, 26, 48, TAB_DISENCHANT) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !EnchantmentHelper.getEnchantments(stack).isEmpty();
            }
        });
        this.addSlot(new DisenchantResultSlot(this.disenchantResult, 0, 98, 48));

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        this.addDataSlot(this.activeTab);
        this.addDataSlot(this.forgeCost);
        this.addDataSlot(this.ultimateReady);
        this.addDataSlot(this.upgradeCount);
        this.addDataSlot(this.selectedUpgrade);

        this.ultimateCost.set(ULTIMATE_COST);
        this.addDataSlot(this.ultimateCost);

        this.disenchantCost.set(DISENCHANT_COST);
        this.disenchantClue.set(-1);
        this.disenchantClueLevel.set(-1);
        this.addDataSlot(this.disenchantCount);
        this.addDataSlot(this.selectedDisenchant);
        this.addDataSlot(this.disenchantCost);
        this.addDataSlot(this.disenchantClue);
        this.addDataSlot(this.disenchantClueLevel);

        this.secretMode.set(0);
        this.addDataSlot(this.secretMode);
    }

    // Structure power at this anvil. 0 on the client, where access is NULL — every
    // power-derived number the client needs arrives through a DataSlot instead.
    private int structurePower() {
        return this.access.evaluate(EnchantPower::totalPower).orElse(0);
    }

    // Both costs fall as the structure grows — the half of the CurseForge proposal that
    // makes the build worth extending past what the table alone needs.
    private static int scaledUltimateCost(int power) {
        return Math.round(Mth.lerp(EnchantPower.powerFactor(power), ULTIMATE_COST, ULTIMATE_COST_FLOOR));
    }

    private static double scaledForgeCostScale(int power) {
        return Mth.lerp(EnchantPower.powerFactor(power), (float) FORGE_COST_SCALE, (float) FORGE_COST_SCALE_MIN);
    }

    private static int scaledDisenchantCost(int power) {
        return Math.round(Mth.lerp(EnchantPower.powerFactor(power), DISENCHANT_COST, DISENCHANT_COST_FLOOR));
    }

    private static boolean canHoldEnchantments(ItemStack stack) {
        return !stack.isEmpty() && (stack.isEnchantable() || !EnchantmentHelper.getEnchantments(stack).isEmpty());
    }

    public int getActiveTab() { return this.activeTab.get(); }
    public int getForgeCost() { return this.forgeCost.get(); }
    public boolean isUltimateReady() { return this.ultimateReady.get() != 0; }
    public int getUltimateCost() { return this.ultimateCost.get(); }

    // Whether the Secret toggle is flicked on. Drives the crystal the ritual demands, the
    // eligible-base list, the slot icon, and everything the screen draws in red.
    public boolean isSecretMode() { return this.secretMode.get() != 0; }

    // The odds, for the screen to show. Surfaced as a percent so the warning text and the
    // actual roll can never disagree: both read DeepDarkBalance.
    public static int getSecretChancePercent() {
        return Math.round(DeepDarkBalance.SECRET_SUCCESS_CHANCE * 100.0F);
    }
    public int getUpgradeCount() { return this.upgradeCount.get(); }
    public int getSelectedUpgrade() { return this.selectedUpgrade.get(); }
    public int getDisenchantCount() { return this.disenchantCount.get(); }
    public int getSelectedDisenchant() { return this.selectedDisenchant.get(); }
    public int getDisenchantCost() { return this.disenchantCost.get(); }
    public int getDisenchantClue() { return this.disenchantClue.get(); }
    public int getDisenchantClueLevel() { return this.disenchantClueLevel.get(); }

    public boolean setItemName(String name) {
        String s = name;
        if (s.length() <= MAX_NAME_LENGTH && !s.equals(this.itemName)) {
            this.itemName = s;
            if (this.getActiveTab() == TAB_FORGE) createForgeResult();
            return true;
        }
        return false;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BTN_TAB_FORGE) {
            this.activeTab.set(TAB_FORGE);
            this.broadcastChanges();
            return true;
        }
        if (id == BTN_TAB_ULTIMATE) {
            this.activeTab.set(TAB_ULTIMATE);
            this.broadcastChanges();
            return true;
        }
        if (id == BTN_TAB_DISENCHANT) {
            this.activeTab.set(TAB_DISENCHANT);
            this.broadcastChanges();
            return true;
        }
        if (id == BTN_CYCLE_UPGRADE) {
            if (this.upgradeCount.get() <= 1) return false;
            int next = (this.selectedUpgrade.get() + 1) % this.upgradeCount.get();
            this.selectedUpgrade.set(next);
            if (!player.level().isClientSide()) createUltimateResult();
            this.broadcastChanges();
            return true;
        }
        if (id == BTN_TOGGLE_SECRET) {
            this.secretMode.set(this.secretMode.get() == 0 ? 1 : 0);
            // The eligible-base list differs between the two modes, so the whole result has to
            // be rebuilt, not merely redrawn. Without this the screen would keep offering the
            // Ultimate base list while the server had already switched to the Secret one.
            this.selectedUpgrade.set(0);
            if (!player.level().isClientSide()) createUltimateResult();
            this.broadcastChanges();
            return true;
        }
        if (id == BTN_CYCLE_DISENCHANT) {
            if (this.disenchantCount.get() <= 1) return false;
            int next = (this.selectedDisenchant.get() + 1) % this.disenchantCount.get();
            this.selectedDisenchant.set(next);
            if (!player.level().isClientSide()) createDisenchantResult();
            this.broadcastChanges();
            return true;
        }
        return false;
    }

    @Override
    public void slotsChanged(Container container) {
        if (container == this.forgeInputs) createForgeResult();
        else if (container == this.ultimateInputs) createUltimateResult();
        else if (container == this.disenchantInput) createDisenchantResult();
    }

    // -------------------------------------------------------------------------
    // Disenchant: selective removal of a single non-Ultimate enchantment
    // -------------------------------------------------------------------------
    private void createDisenchantResult() {
        ItemStack input = this.disenchantInput.getItem(0);
        this.removableOptions = new ArrayList<>();
        this.disenchantCost.set(scaledDisenchantCost(structurePower()));

        Map<Enchantment, Integer> current = input.isEmpty()
                ? Map.of()
                : EnchantmentHelper.getEnchantments(input);

        for (Enchantment ench : current.keySet()) {
            // ⚠️ Ultimates are bound to the item. They are filtered out of the option
            // list entirely, so they never appear as a removal target — not merely
            // rejected on click. The only exits remain a full grindstone wipe or
            // re-running the ritual with a different Ultimate.
            if (Ultimates.isUltimate(ench)) continue;
            this.removableOptions.add(ench);
        }
        // Stable order, so cycling doesn't reshuffle between recomputes.
        this.removableOptions.sort(Comparator.comparing(e -> {
            ResourceLocation key = ForgeRegistries.ENCHANTMENTS.getKey(e);
            return key == null ? "" : key.toString();
        }));

        this.disenchantCount.set(this.removableOptions.size());

        if (this.removableOptions.isEmpty()) {
            this.selectedDisenchant.set(0);
            this.disenchantClue.set(-1);
            this.disenchantClueLevel.set(-1);
            this.disenchantResult.setItem(0, ItemStack.EMPTY);
            this.broadcastChanges();
            return;
        }

        if (this.selectedDisenchant.get() >= this.removableOptions.size()) this.selectedDisenchant.set(0);
        Enchantment target = this.removableOptions.get(this.selectedDisenchant.get());

        this.disenchantClue.set(BuiltInRegistries.ENCHANTMENT.getId(target));
        this.disenchantClueLevel.set(current.get(target));

        // Removed enchantments are destroyed, not returned on a book.
        Map<Enchantment, Integer> remaining = new LinkedHashMap<>(current);
        remaining.remove(target);
        ItemStack result = input.copy();
        EnchantmentHelper.setEnchantments(remaining, result);
        this.disenchantResult.setItem(0, result);
        this.broadcastChanges();
    }

    // -------------------------------------------------------------------------
    // Ultimate transform
    // -------------------------------------------------------------------------
    private void createUltimateResult() {
        ItemStack gear = this.ultimateInputs.getItem(0);
        ItemStack template = this.ultimateInputs.getItem(1);
        ItemStack crystal = this.ultimateInputs.getItem(2);

        final boolean secret = this.isSecretMode();

        this.ultimateReady.set(0);
        // Recomputed here rather than on demand, so the gate in mayPickup, the charge in
        // onTake and the number on screen are all the same value.
        this.ultimateCost.set(scaledUltimateCost(structurePower()));

        // The crystal required is what the toggle selects: Dark for the certain Ultimate,
        // Scarlet for the gamble. A mismatched crystal produces no result at all rather than
        // silently falling back to the other mode.
        ItemStack requiredCrystal = secret ? ModItems.SCARLET_CRYSTAL.get().getDefaultInstance()
                                           : ModItems.DARK_CRYSTAL.get().getDefaultInstance();

        if (gear.isEmpty()
                || !template.is(ModItems.ECHO_UPGRADE_TEMPLATE.get())
                || !crystal.is(requiredCrystal.getItem())
                || !canHoldEnchantments(gear)) {
            this.ultimateResult.setItem(0, ItemStack.EMPTY);
            this.upgradeOptions.clear();
            this.upgradeCount.set(0);
            this.selectedUpgrade.set(0);
            this.broadcastChanges();
            return;
        }

        Map<Enchantment, Integer> current = EnchantmentHelper.getEnchantments(gear);

        List<Enchantment> options = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : current.entrySet()) {
            Enchantment ench = entry.getKey();
            if (entry.getValue() != OverCaps.tableCeiling(ench)) continue;
            // ⚠️ In Secret mode the eligible inputs are ULTIMATES the item already carries, not
            // maxed base enchantments (redesigned 2026-09-21). The level gate above needs no
            // special case: an Ultimate is level 1 and OverCaps.tableCeiling falls through to
            // getMaxLevel(), which is also 1, so a held Ultimate passes it unchanged.
            //
            // Because an item can carry only ONE Ultimate, this list holds 0 or 1 entry in
            // Secret mode and the selector arrows are vestigial there. Known, not a bug.
            Enchantment target = secret
                    ? Secrets.getSecretForUltimate(ench)
                    : Ultimates.getUltimateForBase(ench, gear);
            if (target == null) continue;
            options.add(ench);
        }

        this.upgradeOptions = options;
        this.upgradeCount.set(options.size());

        if (options.isEmpty()) {
            this.ultimateResult.setItem(0, ItemStack.EMPTY);
            this.selectedUpgrade.set(0);
            this.broadcastChanges();
            return;
        }

        int idx = Mth.clamp(this.selectedUpgrade.get(), 0, options.size() - 1);
        this.selectedUpgrade.set(idx);

        Enchantment chosen = options.get(idx);

        ItemStack result = gear.copy();
        Map<Enchantment, Integer> upgraded = new HashMap<>(current);
        Ultimates.removeAllUltimates(upgraded);
        upgraded.remove(chosen);

        if (!secret) {
            upgraded.put(Ultimates.getUltimateForBase(chosen, gear), 1);
        }
        // ⚠️ IN SECRET MODE NOTHING IS ADDED HERE. THIS IS THE WHOLE SECURITY MODEL OF THE
        // GAMBLE, not a missing branch.
        //
        // This preview recomputes on EVERY slot change, so anything decided here can be
        // re-decided for free by nudging an item in and out of a slot. Rolling here would let
        // a player fish for a success and the roll would mean nothing. So the preview shows the
        // GUARANTEED part only — your ULTIMATE is gone — and the roll happens
        // once, server-side, in UltimateResultSlot.onTake, at the moment of commitment.
        //
        // A player therefore sees exactly the same preview whether they are about to win or
        // lose, which is also the honest thing to show: the loss is certain, the win is not.
        EnchantmentHelper.setEnchantments(upgraded, result);
        this.ultimateResult.setItem(0, result);
        this.ultimateReady.set(1);
        this.broadcastChanges();
    }

    // The Secret roll. Server-side, called once, from onTake only.
    //
    // ⚠️ Returns the enchantment to grant or null on a failed roll. The caller must consume
    // the inputs either way — "all or nothing" means the ULTIMATE and the crystal are
    // spent on a failure exactly as they are on a success.
    private Enchantment rollSecret(Player player) {
        if (this.upgradeOptions.isEmpty()) return null;
        int idx = Mth.clamp(this.selectedUpgrade.get(), 0, this.upgradeOptions.size() - 1);
        Enchantment chosen = this.upgradeOptions.get(idx);
        Enchantment secretEnchant = Secrets.getSecretForUltimate(chosen);
        if (secretEnchant == null) return null;

        if (player.getRandom().nextFloat() >= DeepDarkBalance.SECRET_SUCCESS_CHANCE) return null;
        return secretEnchant;
    }

    // -------------------------------------------------------------------------
    // Forge: repair + rename + boosted-cap enchantment merge
    // -------------------------------------------------------------------------
    private void createForgeResult() {
        ItemStack base = this.forgeInputs.getItem(0);
        ItemStack additional = this.forgeInputs.getItem(1);

        this.forgeCost.set(1);
        // Scanned once per result, not per enchantment — the scan is ~160 block lookups.
        final double forgeScale = scaledForgeCostScale(structurePower());
        int costAccrued = 0;
        long repairCostCarry = 0L;
        int renameCost = 0;

        if (base.isEmpty() || !canHoldEnchantments(base)) {
            this.forgeResult.setItem(0, ItemStack.EMPTY);
            this.forgeCost.set(0);
            this.broadcastChanges();
            return;
        }

        ItemStack result = base.copy();
        Map<Enchantment, Integer> enchantments = new HashMap<>(EnchantmentHelper.getEnchantments(result));
        CompoundTag baseTag = base.getTag();
        CompoundTag addTag = additional.getTag();
        repairCostCarry += (long) (baseTag != null ? baseTag.getInt("RepairCost") : 0)
                + (addTag != null ? addTag.getInt("RepairCost") : 0);
        this.repairItemCountCost = 0;

        if (!additional.isEmpty()) {
            boolean fromBook = addTag != null && addTag.contains("StoredEnchantments");
            if (result.isDamageableItem() && base.getItem().isValidRepairItem(base, additional)) {
                int toRepair = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                if (toRepair <= 0) {
                    this.forgeResult.setItem(0, ItemStack.EMPTY);
                    this.forgeCost.set(0);
                    this.broadcastChanges();
                    return;
                }
                int used;
                for (used = 0; toRepair > 0 && used < additional.getCount(); used++) {
                    result.setDamageValue(result.getDamageValue() - toRepair);
                    costAccrued++;
                    toRepair = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                }
                this.repairItemCountCost = used;
            } else {
                if (!fromBook && (!result.is(additional.getItem()) || !result.isDamageableItem())) {
                    this.forgeResult.setItem(0, ItemStack.EMPTY);
                    this.forgeCost.set(0);
                    this.broadcastChanges();
                    return;
                }

                if (result.isDamageableItem() && !fromBook) {
                    int baseRemaining = base.getMaxDamage() - base.getDamageValue();
                    int addRemaining = additional.getMaxDamage() - additional.getDamageValue();
                    int bonus = addRemaining + result.getMaxDamage() * 12 / 100;
                    int newDamage = Math.max(0, result.getMaxDamage() - (baseRemaining + bonus));
                    if (newDamage < result.getDamageValue()) {
                        result.setDamageValue(newDamage);
                        costAccrued += 2;
                    }
                }

                Map<Enchantment, Integer> toMerge = EnchantmentHelper.getEnchantments(additional);
                boolean anyApplied = false;
                boolean anyRejected = false;

                for (Map.Entry<Enchantment, Integer> entry : toMerge.entrySet()) {
                    Enchantment ench = entry.getKey();
                    int currentLevel = enchantments.getOrDefault(ench, 0);
                    int mergedLevel = currentLevel == entry.getValue() ? currentLevel + 1
                            : Math.max(entry.getValue(), currentLevel);
                    // Forging a custom-enchanted book onto gear is a deliberate player action,
                    // so it's allowed to land the enchantment straight on the item — flag the
                    // context so SimpleModEnchantment#canEnchant doesn't reject it.
                    boolean compatible = AncientEnchantContext.runActive(() ->
                            base.canApplyAtEnchantingTable(ench)) || this.owner.getAbilities().instabuild;

                    for (Enchantment existing : enchantments.keySet()) {
                        if (!existing.equals(ench) && !existing.isCompatibleWith(ench)) {
                            compatible = false;
                            costAccrued++;
                        }
                    }

                    if (!compatible) {
                        anyRejected = true;
                    } else {
                        anyApplied = true;
                        // ⚠️ NOT tableCeiling. An anvil merge may climb WITHIN the over-cap
                        // range but may never step INTO it: Prot V + V -> VI, but IV + IV
                        // stays IV. The first over-cap level always comes from the Ancient
                        // Enchanting Table. See OverCaps.mergeCeiling for the full rule.
                        int cap = OverCaps.mergeCeiling(ench, currentLevel, entry.getValue());
                        if (mergedLevel > cap) mergedLevel = cap;
                        enchantments.put(ench, mergedLevel);
                        int anvilCost = (int) Math.ceil(ench.getMinCost(mergedLevel) * forgeScale);
                        if (fromBook) anvilCost = Math.max(1, anvilCost / 2);
                        costAccrued += anvilCost;
                        if (base.getCount() > 1) costAccrued = 40;
                    }
                }

                if (anyRejected && !anyApplied) {
                    this.forgeResult.setItem(0, ItemStack.EMPTY);
                    this.forgeCost.set(0);
                    this.broadcastChanges();
                    return;
                }
            }
        }

        if (this.itemName != null && !this.itemName.isBlank()) {
            if (!this.itemName.equals(base.getHoverName().getString())) {
                renameCost = 1;
                costAccrued += renameCost;
                result.setHoverName(Component.literal(this.itemName));
            }
        } else if (base.hasCustomHoverName()) {
            renameCost = 1;
            costAccrued += renameCost;
            result.resetHoverName();
        }

        int total = costAccrued <= 0 ? 0 : (int) Mth.clamp(repairCostCarry + costAccrued, 0L, Integer.MAX_VALUE);
        this.forgeCost.set(total);
        if (costAccrued <= 0) result = ItemStack.EMPTY;

        if (!result.isEmpty()) {
            CompoundTag resultTag = result.getOrCreateTag();
            int baseRepair = resultTag.getInt("RepairCost");
            int addRepair = addTag != null ? addTag.getInt("RepairCost") : 0;
            if (baseRepair < addRepair) baseRepair = addRepair;
            if (renameCost != costAccrued || renameCost == 0) {
                baseRepair = AnvilMenu.calculateIncreasedRepairCost(baseRepair);
            }
            resultTag.putInt("RepairCost", baseRepair);
            EnchantmentHelper.setEnchantments(enchantments, result);
        }

        this.forgeResult.setItem(0, result);
        this.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.ANCIENT_ANVIL.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> {
            this.clearContainer(player, this.forgeInputs);
            this.clearContainer(player, this.ultimateInputs);
            this.clearContainer(player, this.disenchantInput);
        });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack inSlot = slot.getItem();
            moved = inSlot.copy();
            if (index == FORGE_RESULT || index == ULT_RESULT || index == DIS_RESULT) {
                if (!this.moveItemStackTo(inSlot, INV_START, INV_END, true)) return ItemStack.EMPTY;
                slot.onQuickCraft(inSlot, moved);
            } else if (index >= INV_START && index < INV_END) {
                int target;
                int targetEnd;
                switch (this.getActiveTab()) {
                    case TAB_FORGE -> { target = FORGE_BASE; targetEnd = FORGE_RESULT; }
                    case TAB_DISENCHANT -> { target = DIS_INPUT; targetEnd = DIS_RESULT; }
                    default -> { target = ULT_GEAR; targetEnd = ULT_RESULT; }
                }
                if (!this.moveItemStackTo(inSlot, target, targetEnd, false)) return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(inSlot, INV_START, INV_END, false)) return ItemStack.EMPTY;
            }
            if (inSlot.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            if (inSlot.getCount() == moved.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, inSlot);
        }
        return moved;
    }

    private class TabSlot extends Slot {
        private final int tab;
        TabSlot(Container container, int index, int x, int y, int tab) {
            super(container, index, x, y);
            this.tab = tab;
        }
        @Override
        public boolean isActive() {
            return AncientAnvilMenu.this.getActiveTab() == this.tab;
        }
    }

    private class ForgeResultSlot extends Slot {
        ForgeResultSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override
        public boolean isActive() { return AncientAnvilMenu.this.getActiveTab() == TAB_FORGE; }
        @Override
        public boolean mayPlace(ItemStack stack) { return false; }
        @Override
        public boolean mayPickup(Player player) {
            return (player.getAbilities().instabuild || player.experienceLevel >= AncientAnvilMenu.this.forgeCost.get())
                    && AncientAnvilMenu.this.forgeCost.get() > 0 && this.hasItem();
        }
        @Override
        public void onTake(Player player, ItemStack stack) {
            if (!player.getAbilities().instabuild) player.giveExperienceLevels(-AncientAnvilMenu.this.forgeCost.get());
            AncientAnvilMenu.this.forgeInputs.setItem(0, ItemStack.EMPTY);
            if (AncientAnvilMenu.this.repairItemCountCost > 0) {
                ItemStack add = AncientAnvilMenu.this.forgeInputs.getItem(1);
                if (!add.isEmpty() && add.getCount() > AncientAnvilMenu.this.repairItemCountCost) {
                    add.shrink(AncientAnvilMenu.this.repairItemCountCost);
                    AncientAnvilMenu.this.forgeInputs.setItem(1, add);
                } else {
                    AncientAnvilMenu.this.forgeInputs.setItem(1, ItemStack.EMPTY);
                }
            } else {
                AncientAnvilMenu.this.forgeInputs.setItem(1, ItemStack.EMPTY);
            }
            AncientAnvilMenu.this.forgeCost.set(0);
            AncientAnvilMenu.this.access.execute((level, pos) ->
                    level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 1.0F));
        }
    }

    private class DisenchantResultSlot extends Slot {
        DisenchantResultSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override
        public boolean isActive() { return AncientAnvilMenu.this.getActiveTab() == TAB_DISENCHANT; }
        @Override
        public boolean mayPlace(ItemStack stack) { return false; }
        @Override
        public boolean mayPickup(Player player) {
            return (player.getAbilities().instabuild
                    || player.experienceLevel >= AncientAnvilMenu.this.getDisenchantCost()) && this.hasItem();
        }
        @Override
        public void onTake(Player player, ItemStack stack) {
            if (!player.getAbilities().instabuild) {
                player.giveExperienceLevels(-AncientAnvilMenu.this.getDisenchantCost());
            }
            AncientAnvilMenu.this.selectedDisenchant.set(0);
            // Clearing the input fires setChanged -> slotsChanged, which rebuilds the
            // (now empty) result. The removed enchantment is simply gone.
            AncientAnvilMenu.this.disenchantInput.setItem(0, ItemStack.EMPTY);
            AncientAnvilMenu.this.access.execute((level, pos) ->
                    level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0F, 1.1F));
        }
    }

    private class UltimateResultSlot extends Slot {
        UltimateResultSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override
        public boolean isActive() { return AncientAnvilMenu.this.getActiveTab() == TAB_ULTIMATE; }
        @Override
        public boolean mayPlace(ItemStack stack) { return false; }
        @Override
        public boolean mayPickup(Player player) {
            return (player.getAbilities().instabuild
                    || player.experienceLevel >= AncientAnvilMenu.this.getUltimateCost()) && this.hasItem();
        }
        @Override
        public void onTake(Player player, ItemStack stack) {
            if (!player.getAbilities().instabuild) {
                player.giveExperienceLevels(-AncientAnvilMenu.this.getUltimateCost());
            }

            // ⚠️ THE GAMBLE IS RESOLVED HERE AND NOWHERE ELSE, and only on the server.
            //
            // onTake fires once, at the moment the player commits. createUltimateResult runs on
            // every slot change, so a roll there could be re-rolled for free by jiggling an
            // input and the 20% would be decorative. See Brain/Decisions/Secret enchantments.md.
            //
            // The stack handed to us is the one the player is walking away with, so mutating it
            // here is what turns a failed preview into a won item.
            if (AncientAnvilMenu.this.isSecretMode() && player instanceof ServerPlayer sp) {
                Enchantment won = AncientAnvilMenu.this.rollSecret(player);
                if (won != null) {
                    Map<Enchantment, Integer> enchants =
                            new HashMap<>(EnchantmentHelper.getEnchantments(stack));
                    enchants.put(won, 1);
                    EnchantmentHelper.setEnchantments(enchants, stack);
                }

                // ATTEMPTING is what reveals the Secret's name -- win or lose. Losing already
                // costs an Ultimate and a crystal, which is price enough for a name.
                // -> Brain/Decisions/Open decisions.md #29
                SecretReveal.reveal(sp);

                // The player does not walk away with this. It goes onto the anvil as a real,
                // owner-locked item they have to collect -- Bailey, 2026-09-21: "item is only
                // yours and you have to pick it up off the table."
                //
                // Emptying the taken stack is what moves it from their cursor to the ceremony.
                ItemStack prize = stack.copy();
                stack.setCount(0);

                AncientAnvilMenu.this.access.execute((level, pos) -> {
                    if (level instanceof ServerLevel server) {
                        if (won != null) {
                            SecretRitualEvents.playSuccess(server, pos, sp, prize);
                        } else {
                            SecretRitualEvents.playFailure(server, pos, sp, prize);
                        }
                    }
                });

                // Eject them to watch it. ⚠️ Queued rather than called inline: we are in the
                // middle of the container's own take handling, and closing a menu from inside
                // that is asking for trouble. This runs at the end of the tick instead.
                sp.getServer().execute(sp::closeContainer);
            }

            AncientAnvilMenu.this.ultimateInputs.setItem(0, ItemStack.EMPTY);
            AncientAnvilMenu.this.ultimateInputs.getItem(1).shrink(1);
            // ⚠️ Consumed on a FAILED roll too. "All or nothing" is the whole point: the crystal
            // and the maxed base enchantment are spent either way, and the base is already gone
            // because the preview removed it.
            AncientAnvilMenu.this.ultimateInputs.getItem(2).shrink(1);
            AncientAnvilMenu.this.ultimateInputs.setChanged();
            AncientAnvilMenu.this.access.execute((level, pos) ->
                    level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 1.2F));
        }
    }
}
