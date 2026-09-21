package com.bailey.enchantments;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue LOG_ON_SETUP = BUILDER
            .comment("Whether to log a message when the mod loads")
            .define("logOnSetup", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();
}
