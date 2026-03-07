package com.example.addenchanted.mixin;

import com.example.addenchanted.AdditionalEnchanted;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
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
 * FIXED: Menggunakan Inject instead of Overwrite untuk kompatibilitas lebih baik
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentCompatibilityMixin {

    /**
     * Inject ke method canBeCombined untuk mengizinkan semua kombinasi
     * Lebih aman daripada @Overwrite karena tidak mengubah bytecode langsung
     */
    @Inject(method = "canBeCombined", at = @At("HEAD"), cancellable = true)
    private static void allowAllCombinations(
            RegistryEntry<Enchantment> first,
            RegistryEntry<Enchantment> second,
            CallbackInfoReturnable<Boolean> cir) {

        // Log untuk debugging (optional, bisa dihapus untuk production)
        if (first != null && second != null) {
            AdditionalEnchanted.LOGGER.debug("Allowing combination: {} + {}",
                    first.getKey().map(k -> k.getValue().toString()).orElse("unknown"),
                    second.getKey().map(k -> k.getValue().toString()).orElse("unknown"));
        }

        // Selalu return true untuk mengizinkan semua kombinasi
        cir.setReturnValue(true);
    }
}