package com.example.addenchanted.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes vanilla's enchantment restrictions so any combination of
 * enchantments can be merged together on any item.
 *
 * ROOT CAUSE of the two "Cannot resolve method" errors on this file:
 *
 *  - "isCompatibleWith" never existed on Enchantment in the 26.2 source you
 *    provided. The real method AnvilMenu#createResult() calls to decide
 *    whether two enchantments may coexist is the STATIC method
 *    Enchantment.areCompatible(Holder<Enchantment>, Holder<Enchantment>):
 *
 *        if (!other.equals(enchantmentHolder)
 *                && !Enchantment.areCompatible(enchantmentHolder, other)) {
 *            compatible = false;
 *            ...
 *        }
 *
 *    That is why the old injector could never resolve - the target simply
 *    doesn't exist under that name. Fixed below by injecting into the real
 *    static method instead (note the injector below is now static too,
 *    and takes BOTH Holder<Enchantment> parameters - "this" no longer
 *    applies since areCompatible is static).
 *
 *  - canEnchant(ItemStack) DOES exist with exactly this signature in the
 *    source you pasted, so this injector should compile as-is against that
 *    exact source. If it still fails to resolve on your machine, your local
 *    compiled 26.2 jar has a slightly different signature than the source
 *    you copied from (e.g. the parameter type could be
 *    net.minecraft.world.item.ItemInstance, a supertype ItemStack now
 *    implements, instead of ItemStack directly) - if so, just swap the
 *    parameter type on uce$alwaysEnchantable below to match your jar and
 *    nothing else needs to change.
 *
 * remap = false everywhere: MC 26.x ships fully de-obfuscated (Mojang
 * mappings are the only names that exist), so there's no intermediary name
 * for Mixin to remap through anymore.
 */
@Mixin(value = Enchantment.class, remap = false)
public abstract class EnchantmentMixin {

    /**
     * Enchantment.areCompatible(...) is what actually gates exclusive-set
     * conflicts (Sharpness/Smite/Bane of Arthropods, the Protection family,
     * etc.). Forcing it to always return true removes every exclusivity
     * restriction anvil-wide, so any enchantments can be stacked together.
     *
     * Static method -> static injector, both Holder<Enchantment> parameters
     * are explicit (no implicit "this").
     */
    @Inject(
            method = "areCompatible(Lnet/minecraft/core/Holder;Lnet/minecraft/core/Holder;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void uce$alwaysCompatible(
            Holder<Enchantment> enchantment,
            Holder<Enchantment> other,
            CallbackInfoReturnable<Boolean> cir
    ) {
        cir.setReturnValue(true);
    }

    /**
     * Enchantment#canEnchant(ItemStack) decides whether THIS enchantment is
     * allowed on a given item type (e.g. vanilla only lets Frost Walker
     * target boots). Forcing it to always return true lets any enchanted
     * book be applied, via the anvil, to any item that can otherwise hold
     * enchantments at all (see EnchantmentHelper.canStoreEnchantments, which
     * AnvilMenu#createResult() checks separately and is untouched here).
     */
    @Inject(
            method = "canEnchant(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void uce$alwaysEnchantable(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}