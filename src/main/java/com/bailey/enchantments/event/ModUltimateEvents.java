package com.bailey.enchantments.event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.enchantment.ModEnchantments;
import com.bailey.enchantments.util.EnchantCommandOverride;
import com.bailey.enchantments.util.Ultimates;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public class ModUltimateEvents {

    private static final java.util.UUID VITALITY_UUID      = java.util.UUID.fromString("3a6f2b1c-8e4d-4f5a-9b7c-0d2e1f3a4b5c");
    private static final java.util.UUID SWIFTNESS_UUID     = java.util.UUID.fromString("7c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f");
    private static final java.util.UUID AEGIS_SLOW_UUID    = java.util.UUID.fromString("1b2c3d4e-5f6a-7b8c-9d0e-1f2a3b4c5d6e");
    private static final java.util.UUID FROSTBITE_UUID     = java.util.UUID.fromString("4d5e6f7a-8b9c-0d1e-2f3a-4b5c6d7e8f9a");
    private static final java.util.UUID SHOCKWAVE_SLOW_UUID = java.util.UUID.fromString("5e6f7a8b-9c0d-1e2f-3a4b-5c6d7e8f9a0b");

    private static final ThreadLocal<Boolean> REFLECTING       = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> OVERDRIVE_ACTIVE  = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> VEIN_MINER_ACTIVE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> TIMBER_ACTIVE     = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> MULTISHOT_ACTIVE  = ThreadLocal.withInitial(() -> false);
    private static final Map<UUID, List<ItemStack>> ETERNAL_ITEMS = new HashMap<>();
    private static final Set<UUID> HOMING_ARROWS = new HashSet<>();

    // Record what Abundance/Auto-Smelt should do to the drops of a just-broken block, keyed
    // by the block's own position rather than by nearby-player proximity: popped item entities
    // always spawn at the broken block regardless of how far away the player's reach let them
    // mine from, so position is the reliable key (proximity search previously missed normal-reach
    // mining at >3 blocks and could misattribute drops to an unrelated nearby player).
    private record AbundanceBoost(Map<Item, Integer> targetCounts, long expiryTick) {}
    private static final Map<BlockPos, AbundanceBoost> ABUNDANCE_BREAKS = new HashMap<>();
    private static final Map<BlockPos, Long> AUTO_SMELT_BREAKS = new HashMap<>();

    private record LavaCrust(BlockPos pos, ServerLevel level, long expiryTick) {}
    private static final List<LavaCrust> LAVA_CRUST = new ArrayList<>();

    private record BleedData(float damagePerTick, int ticksRemaining) {}
    private static final Map<UUID, BleedData> BLEEDING = new HashMap<>();

    private record DotData(float damagePerTick, int ticksRemaining) {}
    private static final Map<UUID, DotData> VENOMED = new HashMap<>();
    private static final Map<UUID, DotData> WITHERED = new HashMap<>();

    private record SlowExpiry(long expiryTick) {}
    private static final Map<UUID, SlowExpiry> FROSTBITTEN = new HashMap<>();
    private static final Map<UUID, SlowExpiry> SHOCKWAVE_SLOWED = new HashMap<>();

    private static final ThreadLocal<Boolean> EXECUTING = ThreadLocal.withInitial(() -> false);

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final EquipmentSlot[] ALL_EQUIPMENT_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    // -------------------------------------------------------------------------
    // Execute: bonus damage scaling with victim's missing health (pre-armor)
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity cause = event.getSource().getDirectEntity();
        if (!(cause instanceof LivingEntity attacker)) return;
        LivingEntity victim = event.getEntity();
        ItemStack weapon = attacker.getMainHandItem();
        if (Ultimates.hasUltimate(weapon, ModEnchantments.EXECUTE.get())) {
            // Flat 20% damage bonus
            event.setAmount(event.getAmount() * 1.2f);

            // Apply/refresh bleed: 2 damage per second for 3 seconds
            BLEEDING.put(victim.getUUID(), new BleedData(2.0f, 60));

            // Executioner: below 20% HP → instant kill
            if (!EXECUTING.get() && victim.getHealth() <= victim.getMaxHealth() * 0.2f) {
                EXECUTING.set(true);
                try {
                    if (attacker.level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.CRIT,
                                victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                                100, 0.6, 0.6, 0.6, 0.4);
                        sl.sendParticles(ParticleTypes.ENCHANTED_HIT,
                                victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                                60, 0.6, 0.6, 0.6, 0.4);
                        sl.sendParticles(ParticleTypes.SOUL,
                                victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                                25, 0.3, 0.5, 0.3, 0.05);
                        sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                                SoundEvents.LIGHTNING_BOLT_THUNDER, victim.getSoundSource(), 1.5f, 0.7f);
                    }
                    event.setAmount(victim.getHealth() + victim.getAbsorptionAmount() + 100f);
                } finally {
                    EXECUTING.set(false);
                }
            }
        }

        if (victim.level().isClientSide()) return;

        // Venom: tick damage over a brief window (no potion effect)
        int venomLevel = Ultimates.getLevel(weapon, ModEnchantments.VENOM.get());
        if (venomLevel > 0) {
            float dpt = venomLevel >= 3 ? 2.0f : 1.0f;
            int ticks = venomLevel == 1 ? 20 : venomLevel == 2 ? 30 : 40;
            VENOMED.put(victim.getUUID(), new DotData(dpt, ticks));
        }

        // Wither Touch: tick damage over a brief window (no potion effect)
        int witherTouchLevel = Ultimates.getLevel(weapon, ModEnchantments.WITHER_TOUCH.get());
        if (witherTouchLevel > 0) {
            float dpt = witherTouchLevel >= 3 ? 2.0f : 1.0f;
            int ticks = witherTouchLevel == 1 ? 20 : witherTouchLevel == 2 ? 30 : 40;
            WITHERED.put(victim.getUUID(), new DotData(dpt, ticks));
        }

        // Frostbite: temporary movement speed attribute reduction (no potion effect)
        int frostbiteLevel = Ultimates.getLevel(weapon, ModEnchantments.FROSTBITE.get());
        if (frostbiteLevel > 0 && victim.level() instanceof ServerLevel fsl) {
            double reduction = frostbiteLevel == 2 ? 0.4 : 0.25;
            int duration = frostbiteLevel == 2 ? 30 : 20;
            var speedAttr = victim.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.removeModifier(FROSTBITE_UUID);
                speedAttr.addTransientModifier(new AttributeModifier(
                        FROSTBITE_UUID, "frostbite", -reduction, AttributeModifier.Operation.MULTIPLY_BASE));
                FROSTBITTEN.put(victim.getUUID(), new SlowExpiry(fsl.getGameTime() + duration));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shockwave, Retribution, Lightning Strike — post-armor damage
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        Entity cause = event.getSource().getDirectEntity();
        if (!(cause instanceof LivingEntity attacker)) return;
        if (attacker.level().isClientSide()) return;

        LivingEntity victim = event.getEntity();
        ItemStack weapon = attacker.getMainHandItem();

        // Execute blood splatter on hit
        if (Ultimates.hasUltimate(weapon, ModEnchantments.EXECUTE.get())
                && victim.level() instanceof ServerLevel sl) {
            sl.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    20, 0.3, 0.3, 0.3, 0.15);
        }

        // Shockwave
        if (Ultimates.hasUltimate(weapon, ModEnchantments.SHOCKWAVE.get())
                && victim.level() instanceof ServerLevel shockSl) {
            AABB area = new AABB(victim.blockPosition()).inflate(5.0);
            Vec3 epicentre = attacker.position();
            long shockExpiry = shockSl.getGameTime() + 40;
            for (LivingEntity target : victim.level().getEntitiesOfClass(LivingEntity.class, area,
                    e -> e != attacker)) {
                Vec3 push = target.position().subtract(epicentre).normalize().scale(3.0);
                target.addDeltaMovement(new Vec3(push.x, 0.4, push.z));
                var speedAttr = target.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speedAttr != null) {
                    speedAttr.removeModifier(SHOCKWAVE_SLOW_UUID);
                    speedAttr.addTransientModifier(new AttributeModifier(
                            SHOCKWAVE_SLOW_UUID, "shockwave", -0.2, AttributeModifier.Operation.MULTIPLY_BASE));
                    SHOCKWAVE_SLOWED.put(target.getUUID(), new SlowExpiry(shockExpiry));
                }
            }
        }

        // Retribution
        if (!REFLECTING.get()) {
            boolean wearingRetribution = false;
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                if (Ultimates.hasUltimate(victim.getItemBySlot(slot), ModEnchantments.RETRIBUTION.get())) {
                    wearingRetribution = true;
                    break;
                }
            }
            if (wearingRetribution) {
                REFLECTING.set(true);
                try {
                    attacker.hurt(victim.damageSources().thorns(victim), event.getAmount());
                    // Description promises Weakness + Slowness on the attacker, not just the
                    // damage reflection.
                    attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                    attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0));
                } finally {
                    REFLECTING.set(false);
                }
            }
        }

        // Lightning Strike
        if (Ultimates.hasUltimate(weapon, ModEnchantments.LIGHTNING_STRIKE.get())
                && attacker.level() instanceof ServerLevel sl) {
            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, sl);
            bolt.setPos(victim.getX(), victim.getY(), victim.getZ());
            bolt.setVisualOnly(false);
            sl.addFreshEntity(bolt);
        }

        // Vampiric: heal attacker for a portion of post-armor damage dealt
        int vampiricLevel = Ultimates.getLevel(weapon, ModEnchantments.VAMPIRIC.get());
        if (vampiricLevel > 0) {
            float healFraction = vampiricLevel == 2 ? 0.25f : 0.15f;
            attacker.heal(event.getAmount() * healFraction);
        }
    }

    // -------------------------------------------------------------------------
    // Overdrive: 3×3 mining
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        Level level = (Level) event.getLevel();

        // Record the boosted Abundance/Auto-Smelt outcome for this exact block position so
        // EntityJoinLevelEvent can correct the drop once it spawns.
        if (player.level() instanceof ServerLevel sl) {
            recordAbundanceAndSmelt(player, sl, event.getState(), event.getPos());
        }

        if (OVERDRIVE_ACTIVE.get()) return;
        if (!Ultimates.hasUltimate(player.getMainHandItem(), ModEnchantments.OVERDRIVE.get())) return;

        BlockPos origin = event.getPos();
        Direction.Axis axis = Direction.Axis.Y;
        HitResult hit = player.pick(6.0, 0.0F, false);
        if (hit instanceof BlockHitResult bhr) axis = bhr.getDirection().getAxis();

        OVERDRIVE_ACTIVE.set(true);
        try {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    BlockPos target = switch (axis) {
                        case Y -> origin.offset(a, 0, b);
                        case X -> origin.offset(0, a, b);
                        case Z -> origin.offset(a, b, 0);
                    };
                    BlockState ts = level.getBlockState(target);
                    if (!ts.isAir() && ts.getDestroySpeed(level, target) >= 0
                            && ts.canHarvestBlock(level, target, player)) {
                        // level.destroyBlock(...) below is the low-level world API, not the
                        // player-break path — it never fires BlockEvent.BreakEvent, so it
                        // would never have been recorded for Abundance/Auto-Smelt otherwise.
                        if (player.level() instanceof ServerLevel sl) {
                            recordAbundanceAndSmelt(player, sl, ts, target);
                        }
                        level.destroyBlock(target, true, player);
                    }
                }
            }
        } finally {
            OVERDRIVE_ACTIVE.set(false);
        }
    }

    // -------------------------------------------------------------------------
    // Abundance + Auto-Smelt: intercept item drops as they enter the world
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Level level = event.getLevel();
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();

        // Handle homing arrow tracking AND Multishot
        if (event.getEntity() instanceof AbstractArrow arrow) {
            if (!(arrow.getOwner() instanceof Player player)) {
                // skip
            } else if (!MULTISHOT_ACTIVE.get()) {
                ItemStack bow = getBow(player);
                if (!bow.isEmpty()) {
                    if (EnchantmentHelper.getEnchantments(bow).getOrDefault(ModEnchantments.HOMING.get(), 0) > 0) {
                        HOMING_ARROWS.add(arrow.getUUID());
                    }

                    // Obliterate replaces vanilla Power on the bow, so vanilla's own
                    // "+0.5 per level" arrow damage bonus (BowItem.releaseUsing) no longer
                    // applies. Re-add the flat +5.5 the description promises.
                    if (Ultimates.hasUltimate(bow, ModEnchantments.OBLITERATE.get())) {
                        arrow.setBaseDamage(arrow.getBaseDamage() + 5.5);
                    }

                    if (Ultimates.hasUltimate(bow, ModEnchantments.MULTISHOT.get())) {
                        Vec3 vel = arrow.getDeltaMovement();
                        MULTISHOT_ACTIVE.set(true);
                        try {
                            for (int side : new int[]{-10, 10}) {
                                double angle = Math.toRadians(side);
                                double cos = Math.cos(angle), sin = Math.sin(angle);
                                Vec3 sideVel = new Vec3(vel.x * cos - vel.z * sin, vel.y,
                                        vel.x * sin + vel.z * cos);
                                Arrow sideArrow = new Arrow(EntityType.ARROW, sl);
                                sideArrow.setOwner(player);
                                sideArrow.setPos(arrow.getX(), arrow.getY(), arrow.getZ());
                                sideArrow.setDeltaMovement(sideVel);
                                sideArrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                                sl.addFreshEntity(sideArrow);
                            }
                        } finally {
                            MULTISHOT_ACTIVE.set(false);
                        }
                    }
                }
            }
            return;
        }

        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        BlockPos dropPos = itemEntity.blockPosition();

        // Abundance: replace the vanilla (unenchanted) drop with the amount computed at break
        // time, which already simulates Fortune V and doubles it.
        AbundanceBoost boost = ABUNDANCE_BREAKS.get(dropPos);
        if (boost != null && boost.expiryTick() >= now) {
            ItemStack stack = itemEntity.getItem();
            Integer target = boost.targetCounts().get(stack.getItem());
            if (target != null && target > 0) {
                stack.setCount(Math.min(target, stack.getMaxStackSize()));
                itemEntity.setItem(stack);
                // Don't apply the same allotment again to a second item entity of this type
                // dropped by the same block break.
                boost.targetCounts().put(stack.getItem(), 0);
            }
        }

        // Auto-Smelt: replace each drop with its smelting result
        Long smeltExpiry = AUTO_SMELT_BREAKS.get(dropPos);
        if (smeltExpiry != null && smeltExpiry >= now) {
            ItemStack drop = itemEntity.getItem();
            net.minecraft.world.SimpleContainer smeltInput = new net.minecraft.world.SimpleContainer(1);
            smeltInput.setItem(0, drop);
            var recipe = sl.getServer().getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING, smeltInput, sl);
            if (recipe.isPresent()) {
                ItemStack result = recipe.get().assemble(smeltInput, sl.registryAccess())
                        .copyWithCount(drop.getCount());
                itemEntity.setItem(result);
            }
        }
    }

    // Shared by every block-destruction path: the real player break (BlockEvent.BreakEvent,
    // in onBlockBreak) as well as every "chain destroy" feature that mines extra blocks via
    // level.destroyBlock(...) directly (Overdrive's 3x3, Vein Miner's BFS, Timber's felling).
    // That low-level world API never fires BlockEvent.BreakEvent, so without calling this
    // explicitly for each of those extra blocks too, Abundance/Auto-Smelt would silently only
    // ever apply to the one block the player directly clicked — exactly what was happening
    // when Vein Miner and Abundance were on the same tool together.
    private static void recordAbundanceAndSmelt(ServerPlayer player, ServerLevel level,
                                                  BlockState state, BlockPos pos) {
        BlockPos brokenPos = pos.immutable();
        if (Ultimates.hasUltimate(player.getMainHandItem(), ModEnchantments.ABUNDANCE.get())) {
            Map<Item, Integer> targets = abundanceTargetCounts(state, level, brokenPos, player);
            ABUNDANCE_BREAKS.put(brokenPos, new AbundanceBoost(targets, level.getGameTime() + 5));
        }
        if (Ultimates.hasUltimate(player.getMainHandItem(), ModEnchantments.AUTO_SMELT.get())) {
            AUTO_SMELT_BREAKS.put(brokenPos, level.getGameTime() + 5);
        }
    }

    // Abundance's description is "drops as though mined with Fortune V, then doubled." We
    // compute the block's actual loot table result using a temporary Fortune-V copy of the
    // tool (vanilla Fortune is what ore loot tables actually check for — our Abundance
    // enchantment isn't vanilla Fortune, so simply having Abundance on the tool never
    // triggered the loot table's own fortune bonus) and double every resulting stack, instead
    // of just doubling whatever the unenchanted drop happened to be.
    private static Map<Item, Integer> abundanceTargetCounts(BlockState state, ServerLevel level,
                                                              BlockPos pos, ServerPlayer player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        ItemStack fortuneTool = player.getMainHandItem().copy();
        Map<Enchantment, Integer> enchants = new HashMap<>(EnchantmentHelper.getEnchantments(fortuneTool));
        enchants.put(Enchantments.BLOCK_FORTUNE, 5);
        EnchantmentHelper.setEnchantments(enchants, fortuneTool);

        Map<Item, Integer> totals = new HashMap<>();
        for (ItemStack drop : Block.getDrops(state, level, pos, blockEntity, player, fortuneTool)) {
            totals.merge(drop.getItem(), drop.getCount() * 2, Integer::sum);
        }
        return totals;
    }

    private static ItemStack getBow(Player player) {
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && main.is(Items.BOW)) return main;
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.is(Items.BOW)) return off;
        return ItemStack.EMPTY;
    }

    // -------------------------------------------------------------------------
    // Obliterate + Tempest arrow impact
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(projectile instanceof AbstractArrow arrow)) return;
        HOMING_ARROWS.remove(arrow.getUUID());
        if (!(arrow.getOwner() instanceof LivingEntity shooter)) return;
        if (arrow.level().isClientSide()) return;

        ItemStack bow = shooter.getMainHandItem();
        if (bow.isEmpty()) bow = shooter.getOffhandItem();

        if (Ultimates.hasUltimate(bow, ModEnchantments.OBLITERATE.get())) {
            Vec3 pos = arrow.position();
            Level level = arrow.level();
            AABB blastArea = new AABB(arrow.blockPosition()).inflate(2.5);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, blastArea,
                    e -> e != shooter)) {
                double dist = target.position().distanceTo(pos);
                float dmg = (float) Math.max(0, 8.0 - dist * 2.0);
                if (dmg > 0) target.hurt(level.damageSources().explosion(arrow, shooter), dmg);
                Vec3 push = target.position().subtract(pos).normalize().scale(2.0);
                target.addDeltaMovement(new Vec3(push.x, 0.3, push.z));
            }
        }

        if (Ultimates.hasUltimate(bow, ModEnchantments.TEMPEST.get())
                && arrow.level() instanceof ServerLevel sl) {
            Vec3 pos = arrow.position();
            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, sl);
            bolt.setPos(pos.x, pos.y, pos.z);
            bolt.setVisualOnly(false);
            sl.addFreshEntity(bolt);
            AABB blastArea = new AABB(arrow.blockPosition()).inflate(3.0);
            for (LivingEntity target : sl.getEntitiesOfClass(LivingEntity.class, blastArea,
                    e -> e != shooter)) {
                Vec3 push = target.position().subtract(pos).normalize().scale(5.0);
                target.addDeltaMovement(new Vec3(push.x, 0.5, push.z));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Eternal: save eternal items on death, restore on respawn
    // Plunder: double mob drops
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        Entity killer = event.getSource().getEntity();
        boolean hasPlunder = killer instanceof Player killerPlayer
                && Ultimates.hasUltimate(killerPlayer.getMainHandItem(), ModEnchantments.PLUNDER.get());
        if (hasPlunder) {
            for (ItemEntity drop : event.getDrops()) {
                ItemStack stack = drop.getItem();
                stack.setCount(Math.min(stack.getCount() * 2, stack.getMaxStackSize()));
            }
        }

        if (!(event.getEntity() instanceof Player player)) return;

        List<ItemEntity> toRemove = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack item = drop.getItem();
            if (Ultimates.hasUltimate(item, ModEnchantments.ETERNAL.get())
                    || Ultimates.getLevel(item, Enchantments.UNBREAKING) >= 5) {
                toRemove.add(drop);
            }
        }
        if (toRemove.isEmpty()) return;
        event.getDrops().removeAll(toRemove);
        List<ItemStack> saved = new ArrayList<>();
        for (ItemEntity drop : toRemove) saved.add(drop.getItem().copy());
        ETERNAL_ITEMS.put(player.getUUID(), saved);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        List<ItemStack> saved = ETERNAL_ITEMS.remove(event.getEntity().getUUID());
        if (saved == null) return;
        for (ItemStack stack : saved) event.getEntity().addItem(stack);
    }

    // -------------------------------------------------------------------------
    // Let an operator's /enchant command apply mod enchantments (including ultimates)
    // directly, the same way the Ancient Anvil can — it's an explicit, permission-gated
    // admin action, not something a player can trigger on themselves or from loot.
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        String input = event.getParseResults().getReader().getString();
        int slash = input.indexOf('/');
        String command = (slash >= 0 ? input.substring(slash + 1) : input).stripLeading();
        if (command.equals("enchant") || command.startsWith("enchant ")) {
            EnchantCommandOverride.markActiveForThisTick();
        }
    }

    // -------------------------------------------------------------------------
    // Per-tick: Zephyr, Aegis, Overdrive Haste, Unbreaking V, Lava Walker
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide()) return;

        handleZephyr(player);

        for (EquipmentSlot slot : ALL_EQUIPMENT_SLOTS) {
            ItemStack item = player.getItemBySlot(slot);
            // The Eternal upgrade *removes* vanilla Unbreaking from the item (replaced by the
            // Eternal enchantment), so checking only for Unbreaking V here meant an upgraded
            // item stopped being unbreakable the moment it became Eternal. "Never breaks" must
            // hold for both the true Unbreaking-V over-cap and the Eternal ultimate.
            if (!item.isEmpty() && item.isDamaged()
                    && (Ultimates.getLevel(item, Enchantments.UNBREAKING) >= 5
                        || Ultimates.hasUltimate(item, ModEnchantments.ETERNAL.get()))) {
                item.setDamageValue(0);
            }
        }

        if (Ultimates.hasUltimate(player.getItemBySlot(EquipmentSlot.FEET), ModEnchantments.LAVA_WALKER.get())
                && player.level() instanceof ServerLevel sl && player.tickCount % 5 == 0) {
            int feetY = Mth.floor(player.getY() - 0.2);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos checkPos = new BlockPos(Mth.floor(player.getX()) + dx, feetY,
                            Mth.floor(player.getZ()) + dz);
                    FluidState fluid = sl.getFluidState(checkPos);
                    if (fluid.is(FluidTags.LAVA) && fluid.isSource()) {
                        sl.setBlock(checkPos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                        LAVA_CRUST.add(new LavaCrust(checkPos, sl, sl.getGameTime() + 60));
                    }
                }
            }
            long now = sl.getGameTime();
            LAVA_CRUST.removeIf(crust -> {
                if (crust.expiryTick() <= now) {
                    if (crust.level().getBlockState(crust.pos()).is(Blocks.MAGMA_BLOCK)) {
                        crust.level().setBlock(crust.pos(), Blocks.LAVA.defaultBlockState(), 3);
                    }
                    return true;
                }
                return false;
            });
        }

        if (player.tickCount % 20 == 0) {
            // Overdrive: Haste I while tool is in hand
            if (Ultimates.hasUltimate(player.getMainHandItem(), ModEnchantments.OVERDRIVE.get())) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 30, 0, false, false));
            }

            // Aegis: slowness penalty scales with piece count (damage reduction handled in onLivingHurtAegis)
            int aegisCount = 0;
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                if (Ultimates.hasUltimate(player.getItemBySlot(slot), ModEnchantments.AEGIS.get())) aegisCount++;
            }
            var aegisSpeedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (aegisSpeedAttr != null) {
                aegisSpeedAttr.removeModifier(AEGIS_SLOW_UUID);
                if (aegisCount > 0) {
                    aegisSpeedAttr.addTransientModifier(new AttributeModifier(
                            AEGIS_SLOW_UUID, "aegis_slowness", -aegisCount * 0.20,
                            AttributeModifier.Operation.MULTIPLY_BASE));
                }
            }

            // Swiftness: speed bonus from boots via attribute modifier
            int swiftnessLevel = Ultimates.getLevel(player.getItemBySlot(EquipmentSlot.FEET), ModEnchantments.SWIFTNESS.get());
            var swiftSpeedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (swiftSpeedAttr != null) {
                swiftSpeedAttr.removeModifier(SWIFTNESS_UUID);
                if (swiftnessLevel > 0) {
                    swiftSpeedAttr.addTransientModifier(new AttributeModifier(
                            SWIFTNESS_UUID, "swiftness", swiftnessLevel * 0.15,
                            AttributeModifier.Operation.MULTIPLY_BASE));
                }
            }

            // Vitality: +1 heart per level per piece via MAX_HEALTH attribute modifier
            var maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.removeModifier(VITALITY_UUID);
                int totalVitalityHearts = 0;
                for (EquipmentSlot slot : ARMOR_SLOTS) {
                    totalVitalityHearts += Ultimates.getLevel(player.getItemBySlot(slot), ModEnchantments.VITALITY.get());
                }
                if (totalVitalityHearts > 0) {
                    maxHealthAttr.addTransientModifier(new AttributeModifier(
                            VITALITY_UUID, "vitality", totalVitalityHearts * 2.0,
                            AttributeModifier.Operation.ADDITION));
                }
            }
        }
    }

    private static void handleZephyr(Player player) {
        boolean hasZephyr = Ultimates.hasUltimate(player.getItemBySlot(EquipmentSlot.FEET),
                ModEnchantments.ZEPHYR.get());
        if (hasZephyr) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
        } else if (!player.isCreative() && !player.isSpectator()) {
            if (player.getAbilities().mayfly) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Zephyr: no fall damage
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (Ultimates.hasUltimate(player.getItemBySlot(EquipmentSlot.FEET), ModEnchantments.ZEPHYR.get())) {
            event.setDistance(0.0f);
        }
    }

    // -------------------------------------------------------------------------
    // Vein Miner
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onBlockBreakVeinMiner(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (VEIN_MINER_ACTIVE.get()) return;
        if (!Ultimates.hasUltimate(player.getMainHandItem(), ModEnchantments.VEIN_MINER.get())) return;
        if (!event.getState().is(Tags.Blocks.ORES)) return;

        Level level = (Level) event.getLevel();
        BlockPos origin = event.getPos();
        Block targetBlock = event.getState().getBlock();

        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(origin);
        visited.add(origin);
        int count = 0;

        VEIN_MINER_ACTIVE.set(true);
        try {
            while (!queue.isEmpty() && count < 64) {
                BlockPos current = queue.poll();
                if (!current.equals(origin)) {
                    BlockState bs = level.getBlockState(current);
                    if (bs.canHarvestBlock(level, current, player)) {
                        if (player.level() instanceof ServerLevel sl) {
                            recordAbundanceAndSmelt(player, sl, bs, current);
                        }
                        level.destroyBlock(current, true, player);
                        count++;
                    }
                }
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = current.relative(dir);
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        if (level.getBlockState(neighbor).is(targetBlock)) queue.add(neighbor);
                    }
                }
            }
        } finally {
            VEIN_MINER_ACTIVE.set(false);
        }
    }

    // -------------------------------------------------------------------------
    // Timber
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onBlockBreakTimber(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (TIMBER_ACTIVE.get()) return;
        if (!Ultimates.hasUltimate(player.getMainHandItem(), ModEnchantments.TIMBER.get())) return;
        if (!event.getState().is(BlockTags.LOGS)) return;
        Level level = (Level) event.getLevel();
        if (level.getBlockState(event.getPos().below()).is(BlockTags.LOGS)) return;

        BlockPos origin = event.getPos();
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(origin);
        visited.add(origin);
        int count = 0;

        TIMBER_ACTIVE.set(true);
        try {
            while (!queue.isEmpty() && count < 256) {
                BlockPos current = queue.poll();
                if (!current.equals(origin)) {
                    BlockState bs = level.getBlockState(current);
                    if (bs.canHarvestBlock(level, current, player)) {
                        if (player.level() instanceof ServerLevel sl) {
                            recordAbundanceAndSmelt(player, sl, bs, current);
                        }
                        level.destroyBlock(current, true, player);
                        count++;
                    }
                }
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = current.relative(dir);
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        if (level.getBlockState(neighbor).is(BlockTags.LOGS)) queue.add(neighbor);
                    }
                }
            }
        } finally {
            TIMBER_ACTIVE.set(false);
        }
    }

    // -------------------------------------------------------------------------
    // Homing: steer arrows toward nearest entity each tick
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onLevelTick(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;

        ServerLevel sl = (ServerLevel) event.level;

        // Any /enchant command this tick has already run by now (command packets are
        // processed earlier in the tick than level ticking) — safe to clear the override.
        EnchantCommandOverride.clear();

        // Process bleeding entities
        if (!BLEEDING.isEmpty()) {
            List<UUID> expired = new ArrayList<>();
            for (Map.Entry<UUID, BleedData> entry : new ArrayList<>(BLEEDING.entrySet())) {
                UUID uuid = entry.getKey();
                BleedData bleed = entry.getValue();
                Entity ent = sl.getEntity(uuid);
                if (ent == null) continue; // entity in a different dimension
                if (!(ent instanceof LivingEntity target) || target.isRemoved() || !target.isAlive()) {
                    expired.add(uuid);
                    continue;
                }
                // Dripping blood particles every 5 ticks
                if (bleed.ticksRemaining() % 5 == 0) {
                    sl.sendParticles(ParticleTypes.FALLING_LAVA,
                            target.getX(), target.getY() + target.getBbHeight() * 0.8, target.getZ(),
                            4, 0.2, 0.1, 0.2, 0.02);
                }
                // Bleed damage every 20 ticks
                if (bleed.ticksRemaining() % 20 == 0) {
                    target.hurt(sl.damageSources().magic(), bleed.damagePerTick());
                }
                int remaining = bleed.ticksRemaining() - 1;
                if (remaining <= 0) expired.add(uuid);
                else BLEEDING.put(uuid, new BleedData(bleed.damagePerTick(), remaining));
            }
            expired.forEach(BLEEDING::remove);
        }

        // Process venom (tick damage, no potion effect)
        if (!VENOMED.isEmpty()) {
            List<UUID> expired = new ArrayList<>();
            for (Map.Entry<UUID, DotData> entry : new ArrayList<>(VENOMED.entrySet())) {
                UUID uuid = entry.getKey();
                DotData dot = entry.getValue();
                Entity ent = sl.getEntity(uuid);
                if (ent == null) continue;
                if (!(ent instanceof LivingEntity target) || !target.isAlive()) { expired.add(uuid); continue; }
                if (dot.ticksRemaining() % 10 == 0) target.hurt(sl.damageSources().magic(), dot.damagePerTick());
                int remaining = dot.ticksRemaining() - 1;
                if (remaining <= 0) expired.add(uuid);
                else VENOMED.put(uuid, new DotData(dot.damagePerTick(), remaining));
            }
            expired.forEach(VENOMED::remove);
        }

        // Process wither touch (tick damage, no potion effect)
        if (!WITHERED.isEmpty()) {
            List<UUID> expired = new ArrayList<>();
            for (Map.Entry<UUID, DotData> entry : new ArrayList<>(WITHERED.entrySet())) {
                UUID uuid = entry.getKey();
                DotData dot = entry.getValue();
                Entity ent = sl.getEntity(uuid);
                if (ent == null) continue;
                if (!(ent instanceof LivingEntity target) || !target.isAlive()) { expired.add(uuid); continue; }
                if (dot.ticksRemaining() % 10 == 0) target.hurt(sl.damageSources().wither(), dot.damagePerTick());
                int remaining = dot.ticksRemaining() - 1;
                if (remaining <= 0) expired.add(uuid);
                else WITHERED.put(uuid, new DotData(dot.damagePerTick(), remaining));
            }
            expired.forEach(WITHERED::remove);
        }

        // Prune stale Abundance/Auto-Smelt break records (e.g. creative-mode breaks, or a
        // break whose drop never matched an item entity) so these maps don't grow forever.
        ABUNDANCE_BREAKS.entrySet().removeIf(entry -> entry.getValue().expiryTick() < sl.getGameTime());
        AUTO_SMELT_BREAKS.entrySet().removeIf(entry -> entry.getValue() < sl.getGameTime());

        // Remove expired frostbite speed modifiers
        FROSTBITTEN.entrySet().removeIf(entry -> {
            if (entry.getValue().expiryTick() > sl.getGameTime()) return false;
            Entity ent = sl.getEntity(entry.getKey());
            if (ent instanceof LivingEntity le) {
                var attr = le.getAttribute(Attributes.MOVEMENT_SPEED);
                if (attr != null) attr.removeModifier(FROSTBITE_UUID);
            }
            return true;
        });

        // Remove expired shockwave speed modifiers
        SHOCKWAVE_SLOWED.entrySet().removeIf(entry -> {
            if (entry.getValue().expiryTick() > sl.getGameTime()) return false;
            Entity ent = sl.getEntity(entry.getKey());
            if (ent instanceof LivingEntity le) {
                var attr = le.getAttribute(Attributes.MOVEMENT_SPEED);
                if (attr != null) attr.removeModifier(SHOCKWAVE_SLOW_UUID);
            }
            return true;
        });

        if (HOMING_ARROWS.isEmpty()) return;

        for (UUID arrowId : new java.util.HashSet<>(HOMING_ARROWS)) {
            Entity entity = sl.getEntity(arrowId);
            if (!(entity instanceof AbstractArrow arrow)) {
                if (entity == null || entity.isRemoved()) HOMING_ARROWS.remove(arrowId);
                continue;
            }
            if (arrow.isRemoved()) {
                HOMING_ARROWS.remove(arrowId);
                continue;
            }
            Level level = arrow.level();
            Entity owner = arrow.getOwner();
            AABB searchBox = new AABB(arrow.blockPosition()).inflate(32.0);
            LivingEntity nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, searchBox,
                    e -> e != owner && e.isAlive()
                         && e instanceof net.minecraft.world.entity.monster.Enemy)) {
                double dist = candidate.getBoundingBox().getCenter().distanceToSqr(arrow.position());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = candidate;
                }
            }
            if (nearest != null) {
                Vec3 targetCenter = nearest.getBoundingBox().getCenter();
                Vec3 toTarget = targetCenter.subtract(arrow.position()).normalize();
                Vec3 vel = arrow.getDeltaMovement();
                double speed = Math.max(vel.length(), 1.5);
                Vec3 vn = vel.length() > 0 ? vel.normalize() : toTarget;
                // More aggressive homing when within 6 blocks
                double homeFactor = nearestDist < 36.0 ? 0.75 : 0.45;
                Vec3 mixed = vn.scale(1.0 - homeFactor).add(toTarget.scale(homeFactor)).normalize();
                Vec3 steered = mixed.scale(speed);
                arrow.setDeltaMovement(steered);
                arrow.setYRot((float) (Math.atan2(steered.x, steered.z) * (180.0 / Math.PI)));
                arrow.setXRot((float) (Math.atan2(steered.y,
                        Math.sqrt(steered.x * steered.x + steered.z * steered.z)) * (180.0 / Math.PI)));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Aegis: reduce incoming damage per piece (replaces Resistance effect)
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onLivingHurtAegis(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        int aegisCount = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (Ultimates.hasUltimate(victim.getItemBySlot(slot), ModEnchantments.AEGIS.get())) aegisCount++;
        }
        if (aegisCount > 0) {
            event.setAmount(event.getAmount() * (1.0f - aegisCount * 0.225f));
        }
    }

    // -------------------------------------------------------------------------
    // Over-cap Protection (V–VII): rescale the enchant-protection layer so a full
    // set yields 70% / 75% / 80% reduction instead of vanilla's flat 80% cap.
    // Levels I–IV and every other protection type are left exactly as vanilla.
    //
    // Vanilla treats Protection as EPF = level per piece (so a full set of V/VI/VII
    // is 20/24/28 EPF, all clamped to the 20-EPF / 80% cap — identical). We map the
    // over-cap levels to lower per-piece EPF (V→4.375, VI→4.6875, VII→5) so a full
    // set lands on exactly 17.5 / 18.75 / 20 EPF = 70% / 75% / 80%. We do this by
    // recomputing the magic-absorb factor with the over-cap "excess" removed and
    // rescaling the post-vanilla damage by myFactor/vanillaFactor — this preserves
    // vanilla armour-point absorption, Resistance, the 20-EPF cap, and any mix of
    // other protection enchantments untouched.
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onLivingHurtOverCapProtection(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;

        double excess = 0.0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(
                    Enchantments.ALL_DAMAGE_PROTECTION, victim.getItemBySlot(slot));
            if (level >= 7)      excess += 2.0;      // VII+: EPF 7 → 5
            else if (level == 6) excess += 1.3125;   // VI:   EPF 6 → 4.6875
            else if (level == 5) excess += 0.625;    // V:    EPF 5 → 4.375
        }
        if (excess <= 0.0) return;

        int vanillaK = EnchantmentHelper.getDamageProtection(victim.getArmorSlots(), event.getSource());
        if (vanillaK <= 0) return; // source bypasses enchantment protection

        double myK = Math.max(0.0, vanillaK - excess);
        double vanillaFactor = 1.0 - Math.min(20.0, vanillaK) / 25.0;
        double myFactor      = 1.0 - Math.min(20.0, myK) / 25.0;
        if (vanillaFactor <= 0.0) return;

        event.setAmount((float) (event.getAmount() * (myFactor / vanillaFactor)));
    }

    // -------------------------------------------------------------------------
    // Lava Walker: cancel fire damage (replaces Fire Resistance effect)
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onLavaWalkerFire(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!Ultimates.hasUltimate(player.getItemBySlot(EquipmentSlot.FEET), ModEnchantments.LAVA_WALKER.get())) return;
        if (event.getSource().typeHolder().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) event.setCanceled(true);
    }

    // -------------------------------------------------------------------------
    // Sculk sensors used as Ancient Enchanting Table "bookshelves" go permanently
    // silent: swallow the vibration before it ever reaches their listener.
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onVanillaGameEvent(VanillaGameEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;
        BlockPos pos = BlockPos.containing(event.getEventPosition());
        if (com.bailey.enchantments.util.SculkMuting.isNearMutedSensor(level, pos)) {
            event.setCanceled(true);
        }
    }

}
