package github.com.gengyoubo.sscfe.interaction;

import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.water.AxolotlWaterService;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Vanilla passenger links synchronize the stack to riders, observers and late joiners. */
@Mod.EventBusSubscriber(modid = Sscfe.MOD_ID)
public final class AxolotlStackService {
    public static final int MAX_PLAYERS = 8;
    /** Each layer hugs the one below; overlapping models are intentional so the pile stays compact. */
    public static final double LAYER_BACK_OFFSET = 0.35D;
    public static final double LAYER_HEIGHT = 0.2D;

    public static boolean eligible(Player player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.isSleeping() && !player.isFallFlying() && !player.getAbilities().flying
                && !player.isAutoSpinAttack() && AxolotlWaterService.isAxolotl(player);
    }

    public static boolean isStackLink(Entity carrier, Entity rider) {
        return carrier instanceof Player base && rider instanceof Player upper
                && upper.getVehicle() == base && eligible(base) && eligible(upper)
                && ((AxolotlStackAccess) upper).sscfe$isStackRider();
    }

    public static boolean isStacked(Player player) {
        return isStackLink(player.getVehicle(), player)
                || player.getPassengers().stream().anyMatch(rider -> isStackLink(player, rider));
    }

    /** The clicked player may be any layer; new players always join the top. */
    public static boolean tryStack(Player actor, Player target) {
        if (actor.level().isClientSide || actor == target || actor.level() != target.level()
                || !eligible(actor) || !eligible(target) || actor.isPassenger() || actor.isVehicle()
                || actor.isShiftKeyDown() || !actor.canReach(target, 0) || !actor.hasLineOfSight(target)) return false;

        Player root = target;
        int depth = 0;
        while (root.getVehicle() instanceof Player lower && depth++ < MAX_PLAYERS) {
            if (!isStackLink(lower, root)) return false;
            root = lower;
        }
        if (root.isPassenger() || (!root.onGround() && !isStacked(root))) return false;

        Player top = root;
        int count = 1;
        while (true) {
            if (!eligible(top) || top.level() != actor.level() || top.isShiftKeyDown()) return false;
            if (top.getPassengers().isEmpty()) break;
            if (top.getPassengers().size() != 1 || !(top.getFirstPassenger() instanceof Player upper)
                    || !isStackLink(top, upper) || ++count >= MAX_PLAYERS) return false;
            top = upper;
        }

        Pose oldTopPose = top.getPose(), oldActorPose = actor.getPose();
        top.setPose(Pose.SWIMMING);
        actor.setPose(Pose.SWIMMING);
        Vec3 seat = passengerPosition(top);
        var bounds = actor.getBoundingBox().move(seat.subtract(actor.position()));
        if (!actor.level().getWorldBorder().isWithinBounds(bounds)
                || !actor.level().noCollision(actor, bounds)) {
            top.setPose(oldTopPose);
            actor.setPose(oldActorPose);
            return false;
        }
        ((AxolotlStackAccess) actor).sscfe$setStackRider(true);
        if (!actor.startRiding(top)) {
            ((AxolotlStackAccess) actor).sscfe$setStackRider(false);
            top.setPose(oldTopPose);
            actor.setPose(oldActorPose);
            return false;
        }
        // startRiding resets the rider's pose; restore it immediately on success.
        actor.setPose(Pose.SWIMMING);
        top.positionRider(actor);
        actor.displayClientMessage(Component.translatable("message.sscfe.axolotl_stack.joined"), true);
        target.displayClientMessage(Component.translatable("message.sscfe.axolotl_stack.carrier"), true);
        return true;
    }

    public static Vec3 passengerPosition(Player carrier) {
        // Use body yaw so looking up/down or turning the head does not move the stack.
        double yaw = Math.toRadians(carrier.yBodyRot);
        return carrier.position().add(Math.sin(yaw) * LAYER_BACK_OFFSET, LAYER_HEIGHT,
                -Math.cos(yaw) * LAYER_BACK_OFFSET);
    }

    /** Called on the server; invalid forms and interrupted players leave the stack. */
    public static void maintain(Player player) {
        if (player.level().isClientSide) return;
        if (!eligible(player) || player.isShiftKeyDown()) {
            leave(player);
            return;
        }
        if (((AxolotlStackAccess) player).sscfe$isStackRider() && (!(player.getVehicle() instanceof Player carrier)
                || !isStackLink(carrier, player))) {
            leave(player);
        }
        for (Entity rider : player.getPassengers()) {
            if (rider instanceof Player upper && ((AxolotlStackAccess) upper).sscfe$isStackRider()
                    && !isStackLink(player, upper)) {
                ((AxolotlStackAccess) upper).sscfe$setStackRider(false);
                upper.stopRiding();
            }
        }
        if (isStacked(player)) player.setPose(Pose.SWIMMING);
    }

    public static void leave(Player player) {
        boolean rider = ((AxolotlStackAccess) player).sscfe$isStackRider();
        ((AxolotlStackAccess) player).sscfe$setStackRider(false);
        if (rider && player.getVehicle() instanceof Player) player.stopRiding();
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof Player upper && ((AxolotlStackAccess) upper).sscfe$isStackRider()) {
                ((AxolotlStackAccess) upper).sscfe$setStackRider(false);
                upper.stopRiding();
            }
        }
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) maintain(event.player);
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) leave(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { leave(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { leave(event.getEntity()); }

    private AxolotlStackService() {}
}
