package github.com.gengyoubo.sscfe.mixin;

import github.com.gengyoubo.sscfe.water.WaterCurseService;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server rejects both position and look updates throughout a locked cast. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class WaterPurpleMovementMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void sscfe$lockWaterCast(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (WaterCurseService.casting(player)) ci.cancel();
    }
}
