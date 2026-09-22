package com.bailey.enchantments.entity;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.shadow.ShadowRemnant;
import com.bailey.enchantments.shadow.ShadowPlayer;
import com.bailey.enchantments.shadow.ShadowDragon;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BaileysEnchantments.MODID);

    /**
     * The lingering remnant of a kill, waiting to be told to rise. Invisible on the client —
     * its presence is the particles the server sends — but it has a hitbox so it can be
     * clicked. MISC so nothing tries to spawn or despawn it as a creature.
     */
    public static final RegistryObject<EntityType<ShadowRemnant>> SHADOW_REMNANT =
            ENTITY_TYPES.register("shadow_remnant", () ->
                    EntityType.Builder.<ShadowRemnant>of(ShadowRemnant::new, MobCategory.MISC)
                            .sized(1.0F, 1.2F)
                            .clientTrackingRange(8)
                            .updateInterval(20)
                            .fireImmune()
                            .build("shadow_remnant"));

    /** The body a raised player wears. See {@link ShadowPlayer}. */
    public static final RegistryObject<EntityType<ShadowPlayer>> SHADOW_PLAYER =
            ENTITY_TYPES.register("shadow_player", () ->
                    EntityType.Builder.of(ShadowPlayer::new, MobCategory.MISC)
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(10)
                            .build("shadow_player"));

    /** The tamed dragon. See {@link ShadowDragon}. */
    public static final RegistryObject<EntityType<ShadowDragon>> SHADOW_DRAGON =
            ENTITY_TYPES.register("shadow_dragon", () ->
                    EntityType.Builder.of(ShadowDragon::new, MobCategory.MONSTER)
                            .sized(16.0F, 8.0F)
                            .fireImmune()
                            .clientTrackingRange(10)
                            .build("shadow_dragon"));
}
