package github.com.gengyoubo.sscfe.water;

import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.client.WaterPurpleEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class WaterCurseNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Sscfe.MOD_ID, "water_curse"), () -> "2", "2"::equals, "2"::equals);
    public enum Action { START, RELEASE, CANCEL, CONFIGURE }
    public enum Stage { CHARGE, RELEASE, CANCEL }

    public static void initialize() {
        CHANNEL.registerMessage(0, Input.class, Input::encode, Input::decode, Input::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Effect.class, Effect::encode, Effect::decode, Effect::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void input(Action action, int duration) { CHANNEL.sendToServer(new Input(action, duration)); }

    public static Effect effect(ServerPlayer player, Stage stage, boolean special, long startedAt, int duration, Vec3 origin, Vec3 direction) {
        return new Effect(player.getUUID(), player.level().dimension().location(), stage, special, startedAt,
                player.level().getGameTime(), duration, origin, direction);
    }

    public static void broadcast(ServerPlayer player, Effect effect) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), effect);
    }

    public static void send(ServerPlayer receiver, Effect effect) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> receiver), effect);
    }

    public record Input(Action action, int duration) {
        static void encode(Input value, FriendlyByteBuf buf) { buf.writeEnum(value.action); buf.writeVarInt(value.duration); }
        static Input decode(FriendlyByteBuf buf) { return new Input(buf.readEnum(Action.class), buf.readVarInt()); }
        static void handle(Input value, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) WaterCurseService.input(player, value.action, value.duration);
            });
            context.setPacketHandled(true);
        }
    }

    /** Origin is the authoritative charge core, also used as the projectile's starting point. */
    public record Effect(UUID caster, ResourceLocation dimension, Stage stage, boolean special,
                         long startedAt, long serverNow, int duration, Vec3 origin, Vec3 direction) {
        static void encode(Effect value, FriendlyByteBuf buf) {
            buf.writeUUID(value.caster); buf.writeResourceLocation(value.dimension); buf.writeEnum(value.stage);
            buf.writeBoolean(value.special); buf.writeLong(value.startedAt); buf.writeLong(value.serverNow); buf.writeVarInt(value.duration);
            writeVec(buf, value.origin); writeVec(buf, value.direction);
        }
        static Effect decode(FriendlyByteBuf buf) {
            return new Effect(buf.readUUID(), buf.readResourceLocation(), buf.readEnum(Stage.class),
                    buf.readBoolean(), buf.readLong(), buf.readLong(), buf.readVarInt(), readVec(buf), readVec(buf));
        }
        static void handle(Effect value, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WaterPurpleEffects.accept(value)));
            context.setPacketHandled(true);
        }
        private static void writeVec(FriendlyByteBuf buf, Vec3 v) { buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z); }
        private static Vec3 readVec(FriendlyByteBuf buf) { return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()); }
    }
    private WaterCurseNetwork() {}
}
