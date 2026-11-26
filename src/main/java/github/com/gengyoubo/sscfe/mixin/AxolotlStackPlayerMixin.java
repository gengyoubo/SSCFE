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
    private static final EntityDataAccessor<Byte> sscfe$LINK_MODE =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.BYTE);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void sscfe$defineStackFlag(CallbackInfo ci) {
        ((Player) (Object) this).getEntityData().define(sscfe$LINK_MODE, NONE);
    }

    @Override public byte sscfe$linkMode() {
        return ((Player) (Object) this).getEntityData().get(sscfe$LINK_MODE);
    }

    @Override public void sscfe$setLinkMode(byte mode) {
        ((Player) (Object) this).getEntityData().set(sscfe$LINK_MODE, mode);
    }

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void sscfe$stackCrawl(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (AxolotlStackService.isLinked(player)) {
            player.setPose(Pose.SWIMMING);
            ci.cancel();
        }
    }

    // Forge's default is a seated rider. Stack and chain participants lie on the ground or each other.
    public boolean shouldRiderSit() {
        return !AxolotlStackService.isLinked((Player) (Object) this);
    }
}
