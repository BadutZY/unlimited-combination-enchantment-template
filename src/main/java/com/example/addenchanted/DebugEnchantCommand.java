package com.example.addenchanted;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.Map;

/**
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
                context.getSource().sendFeedback(Text.of("§cOnly players can use this command!"), false);
                return 0;
            }

            ItemStack heldItem = player.getMainHandStack();

            if (heldItem.isEmpty()) {
                context.getSource().sendFeedback(Text.of("§eYou're not holding any item!"), false);
                return 0;
            }

            context.getSource().sendFeedback(Text.of("§6=== Item Debug Info ==="), false);
            context.getSource().sendFeedback(Text.of("§fItem: §a" + heldItem.getItem().toString()), false);
            context.getSource().sendFeedback(Text.of("§fCount: §a" + heldItem.getCount()), false);

            // Check regular enchantments (MC 1.19 way)
            Map<Enchantment, Integer> enchants = EnchantmentHelper.get(heldItem);
            if (enchants != null && !enchants.isEmpty()) {
                context.getSource().sendFeedback(Text.of("§b--- Regular Enchantments ---"), false);
                for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                    Enchantment enchant = entry.getKey();
                    int level = entry.getValue();
                    Identifier id = Registry.ENCHANTMENT.getId(enchant);
                    String name = id != null ? id.toString() : "unknown";
                    context.getSource().sendFeedback(Text.of("§f  - §e" + name + " §7lvl §a" + level), false);
                }
            } else {
                context.getSource().sendFeedback(Text.of("§7No regular enchantments"), false);
            }

            // Check stored enchantments (for books)
            if (heldItem.isOf(Items.ENCHANTED_BOOK)) {
                Map<Enchantment, Integer> storedEnchants = EnchantmentHelper.get(heldItem);
                if (storedEnchants != null && !storedEnchants.isEmpty()) {
                    context.getSource().sendFeedback(Text.of("§d--- Stored Enchantments (Book) ---"), false);
                    for (Map.Entry<Enchantment, Integer> entry : storedEnchants.entrySet()) {
                        Enchantment enchant = entry.getKey();
                        int level = entry.getValue();
                        Identifier id = Registry.ENCHANTMENT.getId(enchant);
                        String name = id != null ? id.toString() : "unknown";
                        context.getSource().sendFeedback(Text.of("§f  - §e" + name + " §7lvl §a" + level), false);
                    }
                } else {
                    context.getSource().sendFeedback(Text.of("§7No stored enchantments"), false);
                }
            }

            return 1;

        } catch (Exception e) {
            context.getSource().sendFeedback(Text.of("§cError: " + e.getMessage()), false);
            AdditionalEnchanted.LOGGER.error("Debug command error", e);
            return 0;
        }
    }
}