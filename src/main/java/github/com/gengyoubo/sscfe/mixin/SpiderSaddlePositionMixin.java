package github.com.gengyoubo.sscfe.mixin;

import github.com.gengyoubo.sscfe.interaction.SpiderSaddleService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Seats a Spider Saddle rider on the spider's abdomen instead of the vanilla player-on-player offset. */
@Mixin(Entity.class)
public abstract class SpiderSaddlePositionMixin {
    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("HEAD"), cancellable = true)
    private void sscfe$positionOnSaddle(Entity passenger, Entity.MoveFunction move, CallbackInfo ci) {
        Entity carrier = (Entity) (Object) this;
        if (!carrier.hasPassenger(passenger) || !SpiderSaddleService.isSaddleLink(carrier, passenger)) return;
        Player spider = (Player) carrier, rider = (Player) passenger;
        var seat = SpiderSaddleService.seatPosition(spider);
        move.accept(rider, seat.x, seat.y, seat.z);
        rider.yBodyRot = spider.yBodyRot;
        rider.yBodyRotO = spider.yBodyRotO;
        rider.resetFallDistance();
        ci.cancel();
    }
}
