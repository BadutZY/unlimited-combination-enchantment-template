package com.example.addenchanted;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdditionalEnchanted implements ModInitializer {
    public static final String MOD_ID = "unlimited-combination-enchantment";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Unlimited Combination Enchantment loaded: anvil cost cap removed, " +
                "enchantment restrictions removed, book-extraction recipe enabled.");
    }
}