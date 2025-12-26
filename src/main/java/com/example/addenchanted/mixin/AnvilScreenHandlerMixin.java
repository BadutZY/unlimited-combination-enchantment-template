package com.example.addenchanted.mixin;

import com.example.addenchanted.AdditionalEnchanted;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin Anvil - SERVER SYNC FIXED
 * Problem: Server tidak detect apply book operation
 * Solution: Enhanced logging + robust enchantment detection
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin extends ForgingScreenHandler {

    @Unique
    private boolean isTransferOperation = false;

    @Unique
    private boolean isApplyBookOperation = false;

    @Unique
    private int customLevelCost = 0;

    // FIXED: Constructor disesuaikan dengan ForgingScreenHandler di MC 1.21
    // ForgingScreenHandler membutuhkan 4 parameter, bukan 5
    public AnvilScreenHandlerMixin() {
        super(null, 0, null, null);
    }

    @Unique
    private static boolean isTransferableItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // Check if item can hold enchantments
        return stack.isDamageable() || stack.getItem() instanceof ArmorItem;
    }

    @Unique
    private static boolean hasEnchantments(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Check regular enchantments
        ItemEnchantmentsComponent enchants = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchants != null && !enchants.isEmpty()) {
            return true;
        }

        // Check stored enchantments (for books)
        ItemEnchantmentsComponent storedEnchants = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        return storedEnchants != null && !storedEnchants.isEmpty();
    }

    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void handleEnchantmentOperations(CallbackInfo ci) {
        AnvilScreenHandler handler = (AnvilScreenHandler) (Object) this;
        Inventory input = this.input;
        ItemStack leftItem = input.getStack(0);
        ItemStack rightItem = input.getStack(1);

        // Reset flags
        isTransferOperation = false;
        isApplyBookOperation = false;
        customLevelCost = 0;

        AdditionalEnchanted.LOGGER.info("=== Anvil Update ===");
        AdditionalEnchanted.LOGGER.info("Left: {}", leftItem.isEmpty() ? "Empty" : leftItem.getItem());
        AdditionalEnchanted.LOGGER.info("Right: {}", rightItem.isEmpty() ? "Empty" : rightItem.getItem());

        // ENHANCED LOGGING
        if (!leftItem.isEmpty()) {
            AdditionalEnchanted.LOGGER.info("Left has enchants: {}", hasEnchantments(leftItem));
            AdditionalEnchanted.LOGGER.info("Left is transferable: {}", isTransferableItem(leftItem));
        }
        if (!rightItem.isEmpty()) {
            AdditionalEnchanted.LOGGER.info("Right has enchants: {}", hasEnchantments(rightItem));
            AdditionalEnchanted.LOGGER.info("Right is book: {}", rightItem.isOf(Items.BOOK));
            AdditionalEnchanted.LOGGER.info("Right is enchanted book: {}", rightItem.isOf(Items.ENCHANTED_BOOK));
        }

        // OPERASI 1: Transfer enchantment dari tool ke book
        if (!leftItem.isEmpty() && !rightItem.isEmpty() &&
                isTransferableItem(leftItem) && rightItem.isOf(Items.BOOK)) {

            ItemEnchantmentsComponent enchantments = leftItem.get(DataComponentTypes.ENCHANTMENTS);

            if (enchantments != null && !enchantments.isEmpty()) {
                AdditionalEnchanted.LOGGER.info("✓ TRANSFER OPERATION DETECTED: {} enchants to book", enchantments.getSize());

                ItemStack resultBook = new ItemStack(Items.ENCHANTED_BOOK);
                ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);

                for (RegistryEntry<Enchantment> enchant : enchantments.getEnchantments()) {
                    int lvl = enchantments.getLevel(enchant);
                    builder.add(enchant, lvl);
                    AdditionalEnchanted.LOGGER.info("  - Transferring: {} lvl {}",
                            enchant.getKey().map(k -> k.getValue().toString()).orElse("unknown"), lvl);
                }

                resultBook.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());
                this.output.setStack(0, resultBook);

                customLevelCost = 0;
                ((AnvilScreenHandlerAccessor) handler).getLevelCost().set(0);

                isTransferOperation = true;
                this.sendContentUpdates();
                ci.cancel();
                return;
            }
        }

        // OPERASI 2: Apply enchanted book ke tool (FIXED DETECTION)
        if (!leftItem.isEmpty() && !rightItem.isEmpty()) {
            boolean isToolAndBook = isTransferableItem(leftItem) && rightItem.isOf(Items.ENCHANTED_BOOK);
            boolean isBookAndTool = leftItem.isOf(Items.ENCHANTED_BOOK) && isTransferableItem(rightItem);

            AdditionalEnchanted.LOGGER.info("Checking apply operation:");
            AdditionalEnchanted.LOGGER.info("  isToolAndBook: {}", isToolAndBook);
            AdditionalEnchanted.LOGGER.info("  isBookAndTool: {}", isBookAndTool);

            if (isToolAndBook || isBookAndTool) {
                ItemStack tool = isToolAndBook ? leftItem : rightItem;
                ItemStack book = isToolAndBook ? rightItem : leftItem;

                // CRITICAL FIX: Check STORED_ENCHANTMENTS for enchanted book
                ItemEnchantmentsComponent bookEnchants = book.get(DataComponentTypes.STORED_ENCHANTMENTS);

                AdditionalEnchanted.LOGGER.info("Book enchantments check:");
                AdditionalEnchanted.LOGGER.info("  bookEnchants is null: {}", bookEnchants == null);
                if (bookEnchants != null) {
                    AdditionalEnchanted.LOGGER.info("  bookEnchants isEmpty: {}", bookEnchants.isEmpty());
                    AdditionalEnchanted.LOGGER.info("  bookEnchants size: {}", bookEnchants.getSize());
                }

                if (bookEnchants != null && !bookEnchants.isEmpty()) {
                    AdditionalEnchanted.LOGGER.info("✓ APPLY OPERATION DETECTED: {} enchants from book to tool", bookEnchants.getSize());

                    // Clone tool dan gabungkan enchantments
                    ItemStack result = tool.copy();
                    ItemEnchantmentsComponent toolEnchants = result.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);

                    ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(toolEnchants);

                    int enchantCount = 0;
                    for (RegistryEntry<Enchantment> enchant : bookEnchants.getEnchantments()) {
                        int bookLevel = bookEnchants.getLevel(enchant);
                        int currentLevel = toolEnchants.getLevel(enchant);

                        int newLevel = Math.max(bookLevel, currentLevel);
                        if (bookLevel == currentLevel && bookLevel > 0) {
                            newLevel = bookLevel + 1;
                        }

                        builder.add(enchant, newLevel);
                        enchantCount++;

                        String enchantName = enchant.getKey().map(k -> k.getValue().toString()).orElse("unknown");
                        AdditionalEnchanted.LOGGER.info("  - Applying: {} lvl {} (current: {}, book: {}, new: {})",
                                enchantName, newLevel, currentLevel, bookLevel, newLevel);
                    }

                    result.set(DataComponentTypes.ENCHANTMENTS, builder.build());

                    // Cost formula: 1-5 levels
                    customLevelCost = Math.min(1 + (enchantCount / 3), 5);

                    this.output.setStack(0, result);
                    ((AnvilScreenHandlerAccessor) handler).getLevelCost().set(customLevelCost);

                    isApplyBookOperation = true;

                    AdditionalEnchanted.LOGGER.info("✓ Result ready! Cost: {} levels", customLevelCost);
                    this.sendContentUpdates();
                    ci.cancel();
                    return;
                } else {
                    AdditionalEnchanted.LOGGER.warn("✗ Book has no stored enchantments!");
                }
            }
        }

        AdditionalEnchanted.LOGGER.info("✗ No custom operation detected, using default anvil logic");
    }

    @Inject(method = "canTakeOutput", at = @At("HEAD"), cancellable = true)
    private void allowCustomOutput(PlayerEntity player, boolean present, CallbackInfoReturnable<Boolean> cir) {
        if (isTransferOperation) {
            AdditionalEnchanted.LOGGER.info("✓ Transfer operation: allowing output (cost 0)");
            cir.setReturnValue(true);
        } else if (isApplyBookOperation) {
            int cost = customLevelCost;
            boolean canAfford = player.getAbilities().creativeMode || player.experienceLevel >= cost;

            AdditionalEnchanted.LOGGER.info("✓ Apply operation check: cost={}, player level={}, creative={}, can afford={}",
                    cost, player.experienceLevel, player.getAbilities().creativeMode, canAfford);

            cir.setReturnValue(canAfford);
        }
    }

    @Inject(method = "onTakeOutput", at = @At("HEAD"), cancellable = true)
    private void handleCustomTakeOutput(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (isTransferOperation && !stack.isEmpty() && stack.isOf(Items.ENCHANTED_BOOK)) {
            AdditionalEnchanted.LOGGER.info("✓✓✓ TRANSFER OUTPUT TAKEN ✓✓✓");

            // Sound effects
            this.context.run((world, pos) -> {
                world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
                world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5F, 1.5F);
            });

            Inventory input = this.input;
            ItemStack leftItem = input.getStack(0);
            ItemStack rightItem = input.getStack(1);

            leftItem.decrement(1);
            rightItem.decrement(1);
            this.output.setStack(0, ItemStack.EMPTY);

            this.context.run((world, pos) -> {
                world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            });

            isTransferOperation = false;
            ci.cancel();
        }
        else if (isApplyBookOperation && !stack.isEmpty()) {
            AdditionalEnchanted.LOGGER.info("✓✓✓ APPLY OUTPUT TAKEN ✓✓✓");

            // Sound effects
            this.context.run((world, pos) -> {
                world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
                world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 0.7F, 1.2F);
            });

            Inventory input = this.input;
            ItemStack leftItem = input.getStack(0);
            ItemStack rightItem = input.getStack(1);

            // Deduct XP
            if (!player.getAbilities().creativeMode) {
                int cost = customLevelCost;
                player.applyEnchantmentCosts(stack, cost);
                AdditionalEnchanted.LOGGER.info("✓ Deducted {} levels (remaining: {})", cost, player.experienceLevel);
            } else {
                AdditionalEnchanted.LOGGER.info("✓ Creative mode: no XP deducted");
            }

            // Consume items
            leftItem.decrement(1);
            rightItem.decrement(1);
            this.output.setStack(0, ItemStack.EMPTY);

            this.context.run((world, pos) -> {
                world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            });

            isApplyBookOperation = false;
            ci.cancel();
        }
    }
}