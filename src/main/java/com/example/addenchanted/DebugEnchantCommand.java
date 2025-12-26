package com.example.addenchanted;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/*
 * Debug command untuk testing enchantment detection
 * Usage: /debugenchant
 * Akan menampilkan info tentang item di tangan player
 */
public class DebugEnchantCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("debugenchant")
                    .executes(DebugEnchantCommand::execute));
        });
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        try {
            var player = context.getSource().getPlayer();
            if (player == null) {
                context.getSource().sendFeedback(() -> Text.literal("Â§cOnly players can use this command!"), false);
                return 0;
            }

            ItemStack heldItem = player.getMainHandStack();

            if (heldItem.isEmpty()) {
                context.getSource().sendFeedback(() -> Text.literal("Â§eYou're not holding any item!"), false);
                return 0;
            }

            context.getSource().sendFeedback(() -> Text.literal("Â§6=== Item Debug Info ==="), false);
            context.getSource().sendFeedback(() -> Text.literal("Â§fItem: Â§a" + heldItem.getItem().toString()), false);
            context.getSource().sendFeedback(() -> Text.literal("Â§fCount: Â§a" + heldItem.getCount()), false);

            // Check regular enchantments
            ItemEnchantmentsComponent enchants = heldItem.get(DataComponentTypes.ENCHANTMENTS);
            if (enchants != null && !enchants.isEmpty()) {
                context.getSource().sendFeedback(() -> Text.literal("Â§b--- Regular Enchantments ---"), false);
                for (RegistryEntry<Enchantment> enchant : enchants.getEnchantments()) {
                    int level = enchants.getLevel(enchant);
                    String name = enchant.getKey().map(k -> k.getValue().toString()).orElse("unknown");
                    context.getSource().sendFeedback(() -> Text.literal("Â§f  - Â§e" + name + " Â§7lvl Â§a" + level), false);
                }
            } else {
                context.getSource().sendFeedback(() -> Text.literal("Â§7No regular enchantments"), false);
            }

            // Check stored enchantments (for books)
            ItemEnchantmentsComponent storedEnchants = heldItem.get(DataComponentTypes.STORED_ENCHANTMENTS);
            if (storedEnchants != null && !storedEnchants.isEmpty()) {
                context.getSource().sendFeedback(() -> Text.literal("Â§d--- Stored Enchantments (Book) ---"), false);
                for (RegistryEntry<Enchantment> enchant : storedEnchants.getEnchantments()) {
                    int level = storedEnchants.getLevel(enchant);
                    String name = enchant.getKey().map(k -> k.getValue().toString()).orElse("unknown");
                    context.getSource().sendFeedback(() -> Text.literal("Â§f  - Â§e" + name + " Â§7lvl Â§a" + level), false);
                }
            } else {
                context.getSource().sendFeedback(() -> Text.literal("Â§7No stored enchantments"), false);
            }

            return 1;

        } catch (Exception e) {
            context.getSource().sendFeedback(() -> Text.literal("Â§cError: " + e.getMessage()), false);
            AdditionalEnchanted.LOGGER.error("Debug command error", e);
            return 0;
        }
    }
}