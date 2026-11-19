package github.com.gengyoubo.sscfe.interaction;

import github.com.gengyoubo.sscfe.Sscfe;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.onixary.shapeShifterCurseForge.form.FormBodyType;
import net.onixary.shapeShifterCurseForge.form.FormDefinition;
import net.onixary.shapeShifterCurseForge.form.FormManager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A spider final form wearing a Spider Saddle can carry one humanoid or ordinary player.
 * The ride is a vanilla passenger link, so it synchronizes to every tracking client.
 */
@Mod.EventBusSubscriber(modid = Sscfe.MOD_ID)
public final class SpiderSaddleService {
    public static final ResourceLocation SPIDER_FINAL = ResourceLocation.fromNamespaceAndPath("shape-shifter-curse", "spider_3");
    private static final ResourceLocation AXOLOTL_FINAL = ResourceLocation.fromNamespaceAndPath("shape-shifter-curse", "axolotl_3");
    /**
     * Logical seat (hitbox, camera) on the abdomen at rest, measured from the spider's feet. The client
     * additionally draws the rider on the animated abdomen, see SpiderSaddleRenderer.
     */
    public static final double SEAT_BACK_OFFSET = 0.5D;
    public static final double SEAT_HEIGHT = 0.15D;
    /** Server-side record of rides created here, so unrelated player rides are never dismantled. */
    private static final Set<UUID> RIDERS = ConcurrentHashMap.newKeySet();

    public static boolean isSpiderFinal(Player player) {
        return SPIDER_FINAL.equals(FormManager.current(player).id());
    }

    public static boolean hasSaddle(Player player) {
        return ModList.get().isLoaded("curios") && CuriosSpiderSaddleCompat.hasSaddle(player);
    }

    /** Ordinary players and humanoid forms; feral bodies and the non-humanoid final forms cannot ride. */
    public static boolean isHumanoid(Player player) {
        FormDefinition form = FormManager.current(player);
        return form.bodyType() == FormBodyType.NORMAL && !SPIDER_FINAL.equals(form.id()) && !AXOLOTL_FINAL.equals(form.id());
    }

    public static boolean canCarry(Player spider) {
        return available(spider) && isSpiderFinal(spider) && hasSaddle(spider);
    }

    public static boolean canRide(Player rider) {
        return available(rider) && !rider.getAbilities().flying && isHumanoid(rider);
    }

    private static boolean available(Player player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.isSleeping()
                && !player.isFallFlying() && !player.isAutoSpinAttack();
    }

    /** Side-independent check used for both the click result and the server-side mount. */
    public static boolean canMount(Player rider, Player spider) {
        return rider != spider && rider.level() == spider.level() && !rider.isShiftKeyDown()
                && !rider.isPassenger() && !rider.isVehicle() && !spider.isPassenger() && !spider.isVehicle()
                && rider.distanceToSqr(spider) <= 36.0D && canRide(rider) && canCarry(spider);
    }

    public static boolean isSaddleLink(Entity carrier, Entity rider) {
        return carrier instanceof Player spider && rider instanceof Player player
                && player.getVehicle() == spider && canCarry(spider) && isHumanoid(player);
    }

    public static boolean tryMount(Player rider, Player spider) {
        if (rider.level().isClientSide || !canMount(rider, spider)) return false;
        RIDERS.add(rider.getUUID());
        if (!rider.startRiding(spider)) {
            RIDERS.remove(rider.getUUID());
            return false;
        }
        spider.positionRider(rider);
        rider.displayClientMessage(Component.translatable("message.sscfe.spider_saddle.mounted"), true);
        spider.displayClientMessage(Component.translatable("message.sscfe.spider_saddle.carrier"), true);
        return true;
    }

    public static Vec3 seatPosition(Player spider) {
        double yaw = Math.toRadians(spider.yBodyRot);
        return spider.position().add(Math.sin(yaw) * SEAT_BACK_OFFSET, SEAT_HEIGHT, -Math.cos(yaw) * SEAT_BACK_OFFSET);
    }

    public static boolean isRider(Player player) {
        return RIDERS.contains(player.getUUID());
    }

    /** Server side: riders leave when either side stops qualifying (saddle removed, form changed, death...). */
    public static void maintain(Player player) {
        if (player.level().isClientSide || !isRider(player)) return;
        if (player.getVehicle() instanceof Player spider && canCarry(spider) && canRide(player)) return;
        RIDERS.remove(player.getUUID());
        if (player.getVehicle() instanceof Player) player.stopRiding();
    }

    public static void leave(Player player) {
        if (RIDERS.remove(player.getUUID()) && player.getVehicle() instanceof Player) player.stopRiding();
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof Player rider && RIDERS.remove(rider.getUUID())) rider.stopRiding();
        }
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof Player spider)) return;
        Player rider = event.getEntity();
        if (!canMount(rider, spider)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!rider.level().isClientSide) tryMount(rider, spider);
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            maintain(event.player);
            // A carrier that loses the saddle or the form drops its rider on the same tick.
            for (Entity passenger : event.player.getPassengers()) {
                if (passenger instanceof Player rider) maintain(rider);
            }
        }
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) leave(player);
    }
    // Fires before PlayerList.remove(): a rider still mounted there would unload the spider player with it.
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { leave(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { leave(event.getEntity()); }

    private SpiderSaddleService() {}
}
