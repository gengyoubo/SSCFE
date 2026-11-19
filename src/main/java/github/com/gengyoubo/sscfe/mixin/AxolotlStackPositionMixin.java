package github.com.gengyoubo.sscfe.mixin;

import github.com.gengyoubo.sscfe.interaction.AxolotlStackService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class AxolotlStackPositionMixin {
    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("HEAD"), cancellable = true)
    private void sscfe$positionOnBack(Entity passenger, Entity.MoveFunction move, CallbackInfo ci) {
        Entity carrier = (Entity) (Object) this;
        if (!carrier.hasPassenger(passenger) || !AxolotlStackService.isStackLink(carrier, passenger)) return;
        Player base = (Player) carrier, upper = (Player) passenger;
        base.setPose(Pose.SWIMMING);
        upper.setPose(Pose.SWIMMING);
        var seat = AxolotlStackService.passengerPosition(base);
        move.accept(upper, seat.x, seat.y, seat.z);
        upper.yBodyRot = base.yBodyRot;
        upper.yBodyRotO = base.yBodyRotO;
        upper.resetFallDistance();
        ci.cancel();
    }
}
