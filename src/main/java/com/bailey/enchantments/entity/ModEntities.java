package com.bailey.enchantments.entity;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.shadow.ShadowRemnant;

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
}
