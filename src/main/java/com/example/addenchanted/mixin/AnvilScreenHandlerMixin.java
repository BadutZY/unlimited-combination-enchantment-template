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
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Mixin Anvil - FIXED FOR MC 1.20.5
 * Menggunakan Component System untuk manipulasi enchantment
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin extends ForgingScreenHandler {

    @Unique
    private boolean isTransferOperation = false;

    @Unique
    private boolean isApplyBookOperation = false;

    @Unique
    private int customLevelCost = 0;

    public AnvilScreenHandlerMixin(@Nullable ScreenHandlerType<?> type, int syncId,
                                   PlayerEntity player, ScreenHandlerContext context) {
        super(type, syncId, player.getInventory(), context);
    }

    @Unique
    private static boolean isTransferableItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.isDamageable() || stack.getItem() instanceof ArmorItem;
    }

    @Unique
    private static boolean hasEnchantments(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemEnchantmentsComponent enchants = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        return !enchants.isEmpty();
    }

    @Unique
    private static Map<Enchantment, Integer> getEnchantments(ItemStack stack) {
        ItemEnchantmentsComponent enchants = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        Map<Enchantment, Integer> result = new HashMap<>();

        for (RegistryEntry<Enchantment> enchantEntry : enchants.getEnchantments()) {
            Enchantment enchant = enchantEntry.value();
            result.put(enchant, enchants.getLevel(enchant));
        }

        return result;
    }

    @Unique
    private static Map<Enchantment, Integer> getStoredEnchantments(ItemStack stack) {
        if (!stack.isOf(Items.ENCHANTED_BOOK)) {
            return new HashMap<>();
        }

        ItemEnchantmentsComponent storedEnchants = stack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        Map<Enchantment, Integer> result = new HashMap<>();

        for (RegistryEntry<Enchantment> enchantEntry : storedEnchants.getEnchantments()) {
            Enchantment enchant = enchantEntry.value();
            result.put(enchant, storedEnchants.getLevel(enchant));
        }

        return result;
    }

    @Unique
    private static void setStoredEnchantments(ItemStack stack, Map<Enchantment, Integer> enchantments) {
        if (!stack.isOf(Items.ENCHANTED_BOOK)) return;

        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);

        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            builder.add(entry.getKey(), entry.getValue());
        }

        stack.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());
    }

    @Unique
    private static void setEnchantments(ItemStack stack, Map<Enchantment, Integer> enchantments) {
        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);

        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            builder.add(entry.getKey(), entry.getValue());
        }

        stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());
    }

    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void handleEnchantmentOperations(CallbackInfo ci) {
        AnvilScreenHandler handler = (AnvilScreenHandler) (Object) this;
        Inventory input = this.input;
        ItemStack leftItem = input.getStack(0);
        ItemStack rightItem = input.getStack(1);

        isTransferOperation = false;
        isApplyBookOperation = false;
        customLevelCost = 0;

        AdditionalEnchanted.LOGGER.info("=== Anvil Update ===");
        AdditionalEnchanted.LOGGER.info("Left: {}", leftItem.isEmpty() ? "Empty" : leftItem.getItem());
        AdditionalEnchanted.LOGGER.info("Right: {}", rightItem.isEmpty() ? "Empty" : rightItem.getItem());

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

            Map<Enchantment, Integer> enchantments = getEnchantments(leftItem);

            if (!enchantments.isEmpty()) {
                AdditionalEnchanted.LOGGER.info("✓ TRANSFER OPERATION DETECTED: {} enchants to book",
                        enchantments.size());

                ItemStack resultBook = new ItemStack(Items.ENCHANTED_BOOK);
                setStoredEnchantments(resultBook, enchantments);

                for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                    AdditionalEnchanted.LOGGER.info("  - Transferring: {} lvl {}",
                            entry.getKey().toString(), entry.getValue());
                }

                this.output.setStack(0, resultBook);
                customLevelCost = 0;
                ((AnvilScreenHandlerAccessor) handler).getLevelCost().set(0);

                isTransferOperation = true;
                this.sendContentUpdates();
                ci.cancel();
                return;
            }
        }

        // OPERASI 2: Apply enchanted book ke tool
        if (!leftItem.isEmpty() && !rightItem.isEmpty()) {
            boolean isToolAndBook = isTransferableItem(leftItem) && rightItem.isOf(Items.ENCHANTED_BOOK);
            boolean isBookAndTool = leftItem.isOf(Items.ENCHANTED_BOOK) && isTransferableItem(rightItem);

            AdditionalEnchanted.LOGGER.info("Checking apply operation:");
            AdditionalEnchanted.LOGGER.info("  isToolAndBook: {}", isToolAndBook);
            AdditionalEnchanted.LOGGER.info("  isBookAndTool: {}", isBookAndTool);

            if (isToolAndBook || isBookAndTool) {
                ItemStack tool = isToolAndBook ? leftItem : rightItem;
                ItemStack book = isToolAndBook ? rightItem : leftItem;

                Map<Enchantment, Integer> bookEnchants = getStoredEnchantments(book);

                AdditionalEnchanted.LOGGER.info("Book enchantments check:");
                AdditionalEnchanted.LOGGER.info("  bookEnchants size: {}", bookEnchants.size());

                if (!bookEnchants.isEmpty()) {
                    AdditionalEnchanted.LOGGER.info("✓ APPLY OPERATION DETECTED: {} enchants from book to tool",
                            bookEnchants.size());

                    ItemStack result = tool.copy();
                    Map<Enchantment, Integer> toolEnchants = getEnchantments(result);
                    Map<Enchantment, Integer> newEnchants = new HashMap<>(toolEnchants);

                    int enchantCount = 0;
                    for (Map.Entry<Enchantment, Integer> entry : bookEnchants.entrySet()) {
                        Enchantment enchant = entry.getKey();
                        int bookLevel = entry.getValue();
                        int currentLevel = toolEnchants.getOrDefault(enchant, 0);

                        int newLevel = Math.max(bookLevel, currentLevel);
                        if (bookLevel == currentLevel && bookLevel > 0) {
                            newLevel = bookLevel + 1;
                        }

                        newEnchants.put(enchant, newLevel);
                        enchantCount++;

                        AdditionalEnchanted.LOGGER.info("  - Applying: {} lvl {} (current: {}, book: {}, new: {})",
                                enchant.toString(), newLevel, currentLevel, bookLevel, newLevel);
                    }

                    setEnchantments(result, newEnchants);

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

            this.context.run((world, pos) -> {
                world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
                world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 0.7F, 1.2F);
            });

            Inventory input = this.input;
            ItemStack leftItem = input.getStack(0);
            ItemStack rightItem = input.getStack(1);

            if (!player.getAbilities().creativeMode) {
                int cost = customLevelCost;
                player.addExperienceLevels(-cost);
                AdditionalEnchanted.LOGGER.info("✓ Deducted {} levels (remaining: {})", cost, player.experienceLevel);
            } else {
                AdditionalEnchanted.LOGGER.info("✓ Creative mode: no XP deducted");
            }

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