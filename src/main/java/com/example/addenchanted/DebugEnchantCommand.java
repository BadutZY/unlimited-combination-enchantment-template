package com.example.addenchanted;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;

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
                context.getSource().sendFeedback(() -> Text.literal("§cOnly players can use this command!"), false);
                return 0;
            }

            ItemStack heldItem = player.getMainHandStack();

            if (heldItem.isEmpty()) {
                context.getSource().sendFeedback(() -> Text.literal("§eYou're not holding any item!"), false);
                return 0;
            }

            context.getSource().sendFeedback(() -> Text.literal("§6=== Item Debug Info ==="), false);
            context.getSource().sendFeedback(() -> Text.literal("§fItem: §a" + heldItem.getItem().toString()), false);
            context.getSource().sendFeedback(() -> Text.literal("§fCount: §a" + heldItem.getCount()), false);

            // Check regular enchantments (MC 1.20 way)
            Map<Enchantment, Integer> enchants = EnchantmentHelper.get(heldItem);
            if (enchants != null && !enchants.isEmpty()) {
                context.getSource().sendFeedback(() -> Text.literal("§b--- Regular Enchantments ---"), false);
                for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                    Enchantment enchant = entry.getKey();
                    int level = entry.getValue();
                    Identifier id = Registries.ENCHANTMENT.getId(enchant);
                    String name = id != null ? id.toString() : "unknown";
                    context.getSource().sendFeedback(() -> Text.literal("§f  - §e" + name + " §7lvl §a" + level), false);
                }
            } else {
                context.getSource().sendFeedback(() -> Text.literal("§7No regular enchantments"), false);
            }

            // Check stored enchantments (for books)
            if (heldItem.isOf(Items.ENCHANTED_BOOK)) {
                Map<Enchantment, Integer> storedEnchants = EnchantmentHelper.get(heldItem);
                if (storedEnchants != null && !storedEnchants.isEmpty()) {
                    context.getSource().sendFeedback(() -> Text.literal("§d--- Stored Enchantments (Book) ---"), false);
                    for (Map.Entry<Enchantment, Integer> entry : storedEnchants.entrySet()) {
                        Enchantment enchant = entry.getKey();
                        int level = entry.getValue();
                        Identifier id = Registries.ENCHANTMENT.getId(enchant);
                        String name = id != null ? id.toString() : "unknown";
                        context.getSource().sendFeedback(() -> Text.literal("§f  - §e" + name + " §7lvl §a" + level), false);
                    }
                } else {
                    context.getSource().sendFeedback(() -> Text.literal("§7No stored enchantments"), false);
                }
            }

            return 1;

        } catch (Exception e) {
            context.getSource().sendFeedback(() -> Text.literal("§cError: " + e.getMessage()), false);
            AdditionalEnchanted.LOGGER.error("Debug command error", e);
            return 0;
        }
    }
}