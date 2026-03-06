package com.example.addenchanted.mixin;

import com.example.addenchanted.AdditionalEnchanted;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
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
 * Mixin Anvil - FIXED FOR MC 1.19
 * Menggunakan EnchantmentHelper dan NBT untuk manipulasi enchantment
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin extends ForgingScreenHandler {

    @Unique
    private boolean isTransferOperation = false;

    @Unique
    private boolean isApplyBookOperation = false;

    @Unique
    private int customLevelCost = 0;

    // Constructor untuk MC 1.19
    public AnvilScreenHandlerMixin(@Nullable ScreenHandlerType<?> type, int syncId,
                                   PlayerEntity player, ScreenHandlerContext context) {
        super(type, syncId, player.getInventory(), context);
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
        Map<Enchantment, Integer> enchants = EnchantmentHelper.get(stack);
        return enchants != null && !enchants.isEmpty();
    }

    @Unique
    private static Map<Enchantment, Integer> getStoredEnchantments(ItemStack stack) {
        if (!stack.isOf(Items.ENCHANTED_BOOK)) {
            return new HashMap<>();
        }

        // Di MC 1.19, enchantment disimpan di NBT
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains("StoredEnchantments", 9)) {
            return new HashMap<>();
        }

        Map<Enchantment, Integer> result = new HashMap<>();
        NbtList enchantList = nbt.getList("StoredEnchantments", 10);

        for (int i = 0; i < enchantList.size(); i++) {
            NbtCompound enchantNbt = enchantList.getCompound(i);
            String idStr = enchantNbt.getString("id");
            int level = enchantNbt.getInt("lvl");

            Identifier enchantId = new Identifier(idStr);
            Enchantment enchantment = Registry.ENCHANTMENT.get(enchantId);

            if (enchantment != null) {
                result.put(enchantment, level);
            }
        }

        return result;
    }

    @Unique
    private static void setStoredEnchantments(ItemStack stack, Map<Enchantment, Integer> enchantments) {
        if (!stack.isOf(Items.ENCHANTED_BOOK)) return;

        NbtCompound nbt = stack.getOrCreateNbt();
        NbtList enchantList = new NbtList();

        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            NbtCompound enchantNbt = new NbtCompound();
            Identifier id = Registry.ENCHANTMENT.getId(entry.getKey());
            if (id != null) {
                enchantNbt.putString("id", id.toString());
                enchantNbt.putInt("lvl", entry.getValue());
                enchantList.add(enchantNbt);
            }
        }

        nbt.put("StoredEnchantments", enchantList);
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

            Map<Enchantment, Integer> enchantments = EnchantmentHelper.get(leftItem);

            if (enchantments != null && !enchantments.isEmpty()) {
                AdditionalEnchanted.LOGGER.info("✓ TRANSFER OPERATION DETECTED: {} enchants to book",
                        enchantments.size());

                ItemStack resultBook = new ItemStack(Items.ENCHANTED_BOOK);
                setStoredEnchantments(resultBook, enchantments);

                for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                    Identifier id = Registry.ENCHANTMENT.getId(entry.getKey());
                    AdditionalEnchanted.LOGGER.info("  - Transferring: {} lvl {}",
                            id != null ? id.toString() : "unknown", entry.getValue());
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

                    // Clone tool dan gabungkan enchantments
                    ItemStack result = tool.copy();
                    Map<Enchantment, Integer> toolEnchants = EnchantmentHelper.get(result);
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

                        Identifier id = Registry.ENCHANTMENT.getId(enchant);
                        AdditionalEnchanted.LOGGER.info("  - Applying: {} lvl {} (current: {}, book: {}, new: {})",
                                id != null ? id.toString() : "unknown", newLevel, currentLevel, bookLevel, newLevel);
                    }

                    EnchantmentHelper.set(newEnchants, result);

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
                player.addExperienceLevels(-cost);
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