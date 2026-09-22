package com.bailey.enchantments.shadow;

import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

/**
 * The scoreboard team every shadow joins, for one reason: the vanilla glow outline takes its
 * colour from the team. Blue team + glowing tag = the blue rim of the show, drawn by the game
 * itself on any body, and visible through walls so an owner can always find their army.
 */
public final class ShadowTeam {
    private ShadowTeam() {}

    private static final String NAME = "baileys_shadows";

    private static PlayerTeam team(Scoreboard scoreboard) {
        PlayerTeam team = scoreboard.getPlayerTeam(NAME);
        if (team == null) {
            team = scoreboard.addPlayerTeam(NAME);
            team.setColor(ChatFormatting.BLUE);
            team.setNameTagVisibility(Team.Visibility.NEVER);
        }
        return team;
    }

    public static void join(Entity entity) {
        Scoreboard scoreboard = entity.level().getScoreboard();
        scoreboard.addPlayerToTeam(entity.getStringUUID(), team(scoreboard));
        entity.setGlowingTag(true);
    }

    public static void leave(Entity entity) {
        entity.level().getScoreboard().removePlayerFromTeam(entity.getStringUUID());
    }
}
