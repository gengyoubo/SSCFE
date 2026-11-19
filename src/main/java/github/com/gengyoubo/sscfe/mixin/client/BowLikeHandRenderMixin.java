package github.com.gengyoubo.sscfe.mixin.client;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla first-person hand selection only recognises Items.BOW / Items.CROSSBOW, so modded bows and
 * crossbows (compound kinetic bow/crossbow) render the off-hand while drawing and while charged.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class BowLikeHandRenderMixin {
    @Redirect(method = {"evaluateWhichHandsToRender", "selectionUsingItemWhileHoldingBowLike", "isChargedCrossbow"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private static boolean sscfe$isBowLike(ItemStack stack, Item item) {
        if (item == Items.CROSSBOW) return stack.getItem() instanceof CrossbowItem;
        if (item == Items.BOW) return stack.getItem() instanceof BowItem;
        return stack.is(item);
    }
}
