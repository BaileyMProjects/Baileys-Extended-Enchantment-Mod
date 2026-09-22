package com.bailey.enchantments.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.bailey.enchantments.shadow.ShadowPlayer;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.UUIDUtil;

/**
 * Resolves a player shadow's skin from its profile, asynchronously, once.
 *
 * <p>The default skin is shown until the real one arrives; on an offline server it never does,
 * and the shadow stays a Steve or Alex in the dead player's gear. That is the honest fallback.
 */
public final class ShadowSkins {
    private ShadowSkins() {}

    private static final class Entry {
        ResourceLocation skin;
        boolean slim;
    }

    private static final Map<UUID, Entry> CACHE = new HashMap<>();

    private static Entry entry(ShadowPlayer shadow) {
        UUID id = shadow.profileId();
        String name = shadow.profileName();
        if (id == null) id = UUIDUtil.createOfflinePlayerUUID(name.isEmpty() ? "Shadow" : name);

        Entry existing = CACHE.get(id);
        if (existing != null) return existing;

        Entry entry = new Entry();
        entry.skin = DefaultPlayerSkin.getDefaultSkin(id);
        entry.slim = "slim".equals(DefaultPlayerSkin.getSkinModelName(id));
        CACHE.put(id, entry);

        if (!name.isEmpty()) {
            GameProfile profile = new GameProfile(id, name);
            Minecraft.getInstance().getSkinManager().registerSkins(profile, (type, location, texture) -> {
                if (type == MinecraftProfileTexture.Type.SKIN) {
                    entry.skin = location;
                    entry.slim = "slim".equals(texture.getMetadata("model"));
                }
            }, false);
        }
        return entry;
    }

    public static ResourceLocation skin(ShadowPlayer shadow) {
        return entry(shadow).skin;
    }

    public static boolean slim(ShadowPlayer shadow) {
        return entry(shadow).slim;
    }
}
