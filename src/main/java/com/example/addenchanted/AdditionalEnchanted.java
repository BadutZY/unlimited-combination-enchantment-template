package com.example.addenchanted;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdditionalEnchanted implements ModInitializer {
    public static final String MOD_ID = "unlimited-combination-enchantment";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("===========================================");
        LOGGER.info("Additional Enchanted Mod Loading...");
        LOGGER.info("All enchantments can now be combined!");
        LOGGER.info("- Protection types can be stacked");
        LOGGER.info("- Sharpness/Smite/Bane of Arthropods can be combined");
        LOGGER.info("- Fortune and Silk Touch can coexist");
        LOGGER.info("- And much more!");
        LOGGER.info("===========================================");

        // Register debug command for testing
        try {
            DebugEnchantCommand.register();
            LOGGER.info("âœ“ Debug command registered: /debugenchant");
        } catch (Exception e) {
            LOGGER.warn("Failed to register debug command (this is OK): {}", e.getMessage());
        }

        LOGGER.info("âœ“ Additional Enchanted Mod Loaded Successfully!");
    }
}