package com.bailey.enchantments.command;

import com.bailey.enchantments.BaileysEnchantments;
import com.bailey.enchantments.shadow.Necromancer;
import com.bailey.enchantments.shadow.ShadowBalance;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Operator commands, for testing.
 *
 * <p>Bailey, 2026-09-22: <i>"add an admin command that maxes my necromancy level just because
 * its really annoying for testing."</i> Quite right — the ladder is deliberately a long grind,
 * and grinding it is not testing it.
 *
 * <p>⚠️ Permission level 2, the same as {@code /gamemode}. This hands out the whole progression
 * system, so it is not something a player should reach on a server.
 *
 * <pre>
 *   /baileys necromancer max [player]
 *   /baileys necromancer set &lt;level&gt; [player]
 *   /baileys necromancer reset [player]
 * </pre>
 */
@Mod.EventBusSubscriber(modid = BaileysEnchantments.MODID)
public final class ModCommands {
    private ModCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("baileys")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("necromancer")
                        .then(Commands.literal("max")
                                .executes(ctx -> set(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                                        ShadowBalance.MAX_NECROMANCER_LEVEL))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> set(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                ShadowBalance.MAX_NECROMANCER_LEVEL))))
                        .then(Commands.literal("reset")
                                .executes(ctx -> set(ctx.getSource(), ctx.getSource().getPlayerOrException(), 1))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> set(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"), 1))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("level",
                                                IntegerArgumentType.integer(1, ShadowBalance.MAX_NECROMANCER_LEVEL))
                                        .executes(ctx -> set(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "level")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> set(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "level"))))))));
    }

    private static int set(net.minecraft.commands.CommandSourceStack source, ServerPlayer target, int level) {
        Necromancer necro = Necromancer.get(target.server);
        necro.setLevel(target.getUUID(), level);
        int slots = Necromancer.slotsForLevel(level);

        source.sendSuccess(() -> Component.translatable("baileys_enchantments.command.necromancer",
                target.getName(), level, slots), true);
        if (target != source.getEntity()) {
            target.displayClientMessage(Component.translatable(
                    "baileys_enchantments.necro.level", level, slots), false);
        }
        return level;
    }
}
