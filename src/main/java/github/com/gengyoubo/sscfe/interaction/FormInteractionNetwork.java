package github.com.gengyoubo.sscfe.interaction;

import github.com.gengyoubo.sscfe.Sscfe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

public final class FormInteractionNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Sscfe.MOD_ID, "form_interaction"),
            () -> "1", "1"::equals, "1"::equals);

    public static void initialize() {
        CHANNEL.registerMessage(0, Interact.class, Interact::encode, Interact::decode, Interact::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void interact(int targetId) { CHANNEL.sendToServer(new Interact(targetId)); }

    public record Interact(int targetId) {
        static void encode(Interact value, FriendlyByteBuf buf) { buf.writeVarInt(value.targetId); }
        static Interact decode(FriendlyByteBuf buf) { return new Interact(buf.readVarInt()); }
        static void handle(Interact value, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> {
                var sender = context.getSender();
                if (sender != null && sender.level().getEntity(value.targetId) instanceof Player target) {
                    AxolotlStackService.tryStack(sender, target);
                }
            });
            context.setPacketHandled(true);
        }
    }

    private FormInteractionNetwork() {}
}
