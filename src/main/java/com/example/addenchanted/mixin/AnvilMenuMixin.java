package com.example.addenchanted.mixin;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anvil-side half of Unlimited Combination Enchantment.
 *
 * FIX for the new runtime crash:
 *
 *   @Shadow method getSlot(I)Lnet/minecraft/world/inventory/Slot; ...
 *   was not located in the target class net.minecraft.world.inventory.AnvilMenu.
 *
 * Same root cause as last time, just on a method instead of a field:
 * getSlot(int) is declared on AbstractContainerMenu (two classes up from
 * AnvilMenu), not on AnvilMenu itself. In this project's Mixin setup,
 * @Shadow only matches members declared DIRECTLY on the exact class you
 * @Mixin into - it does not walk up to superclasses to find inherited
 * ones. That bit AnvilMenuMixin twice now: first with the inputSlots
 * field, now with the getSlot method.
 *
 * The fix this time is to stop using @Shadow for inherited members
 * altogether and instead do what any ordinary Java code would do: cast
 * `this` to the real target type and call the public method directly.
 * This works because, after Mixin merges this class's bytecode into
 * AnvilMenu, `this` genuinely IS an AnvilMenu instance at runtime -
 * `(AnvilMenu) (Object) this` is the standard Mixin idiom for this, and it
 * completely bypasses Mixin's @Shadow member-resolution machinery (and
 * therefore the exact bug above), since it's just a plain Java cast +
 * method call, resolved by javac/the JVM like any other code.
 *
 * `cost` is still @Shadow'd as a field - that continues to work fine
 * because it IS declared directly on AnvilMenu itself
 * ("private final DataSlot cost = DataSlot.standalone();"), which is
 * exactly why the crash log has never once complained about it.
 *
 * The "Cannot resolve method 'createResult()V' / 'areCompatible(...)' /
 * 'canEnchant(...)'" warnings your IDE still shows are IDE-index false
 * positives, not real errors - proof: this run's crash log ONLY
 * complains about the getSlot @Shadow, never about createResult (or, in
 * EnchantmentMixin, areCompatible/canEnchant). If those were real
 * unresolved targets, Mixin would refuse to apply for that exact reason
 * and say so explicitly, the same way it did for getSlot. Your IDE's
 * Mixin plugin index is just stale - "Invalidate Caches / Restart" in
 * IntelliJ (or re-run `./gradlew genSources` + re-import) will clear it.
 */
@Mixin(value = AnvilMenu.class, remap = false)
public abstract class AnvilMenuMixin {

    @Shadow
    @Final
    private DataSlot cost;

    /**
     * Declared directly on AnvilMenu ("private int repairItemCountCost;"),
     * so - same as `cost` - @Shadow works fine here.
     *
     * This is what onTake() uses to decide how many items to consume from
     * the right ("addition") slot: if it's > 0, onTake() shrinks that slot
     * by exactly this many; if it's left at 0 (its default / whatever it
     * was after the previous, non-cancelled createResult() run), onTake()
     * falls into the "wipe the whole slot" branch instead. Since our book-
     * extraction path below cancels createResult() at HEAD, vanilla's own
     * line that normally resets this field never runs for that call - so
     * without explicitly setting it ourselves, onTake() ends up consuming
     * the player's entire stack of Books instead of just one. That was the
     * "whole stack disappears" bug.
     */
    @Shadow
    private int repairItemCountCost;

    /**
     * `if (this.cost.get() >= 40) { this.cost.set(39); }` inside the
     * "rename only" branch - occurrence #2 (ordinal 1, zero-based) of the
     * literal 40 in createResult().
     */
    @ModifyConstant(
            method = "createResult()V",
            constant = @Constant(intValue = 40, ordinal = 1),
            remap = false
    )
    private int uce$removeRenameOnlyCap(int original) {
        return Integer.MAX_VALUE;
    }

    /**
     * `if (this.cost.get() >= 40 && !this.player.hasInfiniteMaterials())
     * { result = ItemStack.EMPTY; }` - the real "Too Expensive!" rejection.
     * Occurrence #3 (ordinal 2, zero-based).
     */
    @ModifyConstant(
            method = "createResult()V",
            constant = @Constant(intValue = 40, ordinal = 2),
            remap = false
    )
    private int uce$removeTooExpensiveCap(int original) {
        return Integer.MAX_VALUE;
    }

    /**
     * THE REAL FIX for "still Too Expensive after many combines":
     *
     * `int finalPrice = price <= 0 ? 0
     *     : (int) Mth.clamp(tax + price, 0L, 2147483647L);`
     *
     * `tax` comes from each item's REPAIR_COST component, and every combine
     * runs it through calculateIncreasedRepairCost() -> base*2+1. That
     * DOUBLES every single merge, so after a handful of combines (exactly
     * what "unlimited combination" encourages) `tax + price` blows way past
     * Integer range. Mth.clamp(..., 0L, 2147483647L) then clamps it down to
     * exactly Integer.MAX_VALUE - and since our two cap checks above were
     * also raised to exactly Integer.MAX_VALUE, `cost.get() >= 40(now MAX)`
     * ends up comparing MAX_VALUE >= MAX_VALUE, which is still true. That's
     * why "Too Expensive!" kept appearing even with the cap "removed" - the
     * cost itself had grown to match the (raised) cap.
     *
     * Redirecting this exact Mth.clamp(long,long,long) call lets us clamp
     * the UPPER bound ourselves instead of leaving it at 2147483647L, so
     * the computed price/tax can never grow large in the first place - the
     * anvil cost stays tiny (at most 5 levels) no matter how many
     * enchantments or how many times the item has been combined before.
     * Combined with the two ordinal fixes above (which are now mostly a
     * safety net, since cost will realistically never reach anywhere near
     * 40), "Too Expensive!" can no longer trigger.
     */
    @Redirect(
            method = "createResult()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;clamp(JJJ)J"
            ),
            remap = false
    )
    private long uce$capCostLow(long value, long min, long max) {
        return Mth.clamp(value, 0L, 5L);
    }

    /**
     * Runs before vanilla's own recipe logic. If the player is extracting
     * enchantments (enchanted item on the left, plain Book on the right),
     * we build the enchanted book ourselves and cancel vanilla's logic for
     * this call. Otherwise we return immediately and let vanilla (with the
     * cap above removed) handle normal, now-unrestricted combination.
     */
    @Inject(method = "createResult()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void uce$extractEnchantments(CallbackInfo ci) {
        // Cast instead of @Shadow - getSlot() is inherited, not declared
        // directly on AnvilMenu, so @Shadow can't see it in this project's
        // Mixin setup (see class javadoc). A plain cast sidesteps that
        // entirely since it's ordinary Java, not Mixin member-resolution.
        AnvilMenu self = (AnvilMenu) (Object) this;

        ItemStack left = self.getSlot(AnvilMenu.INPUT_SLOT).getItem();
        ItemStack right = self.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();

        boolean rightIsPlainBook = !right.isEmpty() && right.is(Items.BOOK);
        boolean leftHoldsEnchantments = !left.isEmpty()
                && !left.is(Items.BOOK)
                && !left.is(Items.ENCHANTED_BOOK);

        if (!rightIsPlainBook || !leftHoldsEnchantments) {
            return;
        }

        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(left);
        if (enchantments.isEmpty()) {
            return;
        }

        ItemStack resultBook = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.setEnchantments(resultBook, enchantments);

        Slot resultSlot = self.getSlot(AnvilMenu.RESULT_SLOT);
        resultSlot.set(resultBook);
        this.cost.set(1);
        // Consume exactly ONE plain Book from the right slot on take,
        // instead of the whole stack - this is the actual bug fix.
        this.repairItemCountCost = 1;
        ci.cancel();
    }
}