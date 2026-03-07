package com.example.addenchanted.mixin;

import com.example.addenchanted.AdditionalEnchanted;
import net.minecraft.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin untuk menghilangkan semua batasan kompatibilitas enchantment
 * Memungkinkan kombinasi seperti:
 * - Protection + Fire Protection + Blast Protection + Projectile Protection
 * - Sharpness + Smite + Bane of Arthropods
 * - Fortune + Silk Touch
 * - Infinity + Mending
 * - Dan semua kombinasi lainnya tanpa batasan
 *
 * FIXED: Untuk MC 1.20 - menggunakan @Inject untuk compatibility
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentCompatibilityMixin {

    /**
     * Inject ke method canCombine untuk mengizinkan semua kombinasi
     * Method ini dipanggil ketika game mengecek apakah 2 enchantment bisa digabung
     *
     * @param other Enchantment lain yang akan dicek compatibility-nya
     * @param cir Callback untuk return value
     */
    @Inject(method = "canCombine", at = @At("HEAD"), cancellable = true)
    protected void allowAllCombinations(Enchantment other, CallbackInfoReturnable<Boolean> cir) {
        // Log untuk debugging (optional, bisa dihapus untuk production)
        Enchantment self = (Enchantment) (Object) this;

        AdditionalEnchanted.LOGGER.debug("Allowing combination: {} + {}",
                self.getClass().getSimpleName(),
                other.getClass().getSimpleName());

        // Selalu return true untuk mengizinkan semua kombinasi
        // cancellable = true berarti kita bisa override return value original method
        cir.setReturnValue(true);
    }
}