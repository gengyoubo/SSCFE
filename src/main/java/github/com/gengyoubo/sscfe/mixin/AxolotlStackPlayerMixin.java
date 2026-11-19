package github.com.gengyoubo.sscfe.mixin;

import github.com.gengyoubo.sscfe.interaction.AxolotlStackService;
import github.com.gengyoubo.sscfe.interaction.AxolotlStackAccess;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Player.class, priority = 1100)
public abstract class AxolotlStackPlayerMixin implements AxolotlStackAccess {
    @Unique
    private static final EntityDataAccessor<Boolean> sscfe$STACK_RIDER =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void sscfe$defineStackFlag(CallbackInfo ci) {
        ((Player) (Object) this).getEntityData().define(sscfe$STACK_RIDER, false);
    }

    @Override public boolean sscfe$isStackRider() {
        return ((Player) (Object) this).getEntityData().get(sscfe$STACK_RIDER);
    }

    @Override public void sscfe$setStackRider(boolean value) {
        ((Player) (Object) this).getEntityData().set(sscfe$STACK_RIDER, value);
    }

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void sscfe$stackCrawl(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (AxolotlStackService.isStacked(player)) {
            player.setPose(Pose.SWIMMING);
            ci.cancel();
        }
    }

    // Forge's default is a seated rider. Stack participants lie on each other's backs.
    public boolean shouldRiderSit() {
        return !AxolotlStackService.isStacked((Player) (Object) this);
    }
}
