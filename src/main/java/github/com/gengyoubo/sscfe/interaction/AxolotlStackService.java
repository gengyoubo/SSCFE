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

/**
 * Multiplayer axolotl groups built on vanilla passenger links, which synchronize the group to
 * riders, observers and late joiners. Two shapes exist:
 * <ul>
 *     <li>stack: each layer lies on the back of the one below;</li>
 *     <li>chain: each follower holds the tail of the one in front and is dragged behind it
 *     (see {@link AxolotlChainPhysics}).</li>
 * </ul>
 */
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

    private static boolean isLink(Entity carrier, Entity rider, byte mode) {
        return carrier instanceof Player base && rider instanceof Player upper
                && upper.getVehicle() == base && eligible(base) && eligible(upper)
                && ((AxolotlStackAccess) upper).sscfe$linkMode() == mode;
    }

    public static boolean isStackLink(Entity carrier, Entity rider) {
        return isLink(carrier, rider, AxolotlStackAccess.STACK);
    }

    public static boolean isChainLink(Entity front, Entity follower) {
        return isLink(front, follower, AxolotlStackAccess.CHAIN);
    }

    public static boolean isAnyLink(Entity carrier, Entity rider) {
        return isStackLink(carrier, rider) || isChainLink(carrier, rider);
    }

    /** Part of a stack or a chain: such players always crawl. */
    public static boolean isLinked(Player player) {
        return isAnyLink(player.getVehicle(), player)
                || player.getPassengers().stream().anyMatch(rider -> isAnyLink(player, rider));
    }

    public static boolean isStacked(Player player) {
        return isStackLink(player.getVehicle(), player)
                || player.getPassengers().stream().anyMatch(rider -> isStackLink(player, rider));
    }

    public static boolean isChained(Player player) {
        return isChainLink(player.getVehicle(), player)
                || player.getPassengers().stream().anyMatch(rider -> isChainLink(player, rider));
    }

    /** The player directly behind {@code front} in a chain, or null. */
    public static Player chainFollower(Player front) {
        for (Entity rider : front.getPassengers()) {
            if (rider instanceof Player follower && isChainLink(front, follower)) return follower;
        }
        return null;
    }

    /** The clicked player may be any layer; new players always join the top. */
    public static boolean tryStack(Player actor, Player target) {
        return tryJoin(actor, target, AxolotlStackAccess.STACK);
    }

    /** The clicked player may be anyone in the chain; new players always grab the last tail. */
    public static boolean tryChain(Player actor, Player target) {
        return tryJoin(actor, target, AxolotlStackAccess.CHAIN);
    }

    private static boolean tryJoin(Player actor, Player target, byte mode) {
        if (actor.level().isClientSide || actor == target || actor.level() != target.level()
                || !eligible(actor) || !eligible(target) || actor.isPassenger() || actor.isVehicle()
                || actor.isShiftKeyDown() || !actor.canReach(target, 0) || !actor.hasLineOfSight(target)) return false;

        // Walk down to the group's root; a stack and a chain never mix.
        Player root = target;
        int depth = 0;
        while (root.getVehicle() instanceof Player lower && depth++ < MAX_PLAYERS) {
            if (!isLink(lower, root, mode)) return false;
            root = lower;
        }
        if (root.isPassenger() || (!root.onGround() && !isLinked(root))) return false;

        Player end = root;
        int count = 1;
        while (true) {
            if (!eligible(end) || end.level() != actor.level() || end.isShiftKeyDown()) return false;
            if (end.getPassengers().isEmpty()) break;
            if (end.getPassengers().size() != 1 || !(end.getFirstPassenger() instanceof Player upper)
                    || !isLink(end, upper, mode) || ++count >= MAX_PLAYERS) return false;
            end = upper;
        }

        Pose oldEndPose = end.getPose(), oldActorPose = actor.getPose();
        end.setPose(Pose.SWIMMING);
        actor.setPose(Pose.SWIMMING);
        if (mode == AxolotlStackAccess.STACK) {
            Vec3 seat = passengerPosition(end);
            var bounds = actor.getBoundingBox().move(seat.subtract(actor.position()));
            if (!actor.level().getWorldBorder().isWithinBounds(bounds)
                    || !actor.level().noCollision(actor, bounds)) {
                end.setPose(oldEndPose);
                actor.setPose(oldActorPose);
                return false;
            }
        }
        ((AxolotlStackAccess) actor).sscfe$setLinkMode(mode);
        if (!actor.startRiding(end)) {
            ((AxolotlStackAccess) actor).sscfe$setLinkMode(AxolotlStackAccess.NONE);
            end.setPose(oldEndPose);
            actor.setPose(oldActorPose);
            return false;
        }
        // startRiding resets the rider's pose; restore it immediately on success.
        actor.setPose(Pose.SWIMMING);
        if (mode == AxolotlStackAccess.STACK) {
            end.positionRider(actor);
            actor.displayClientMessage(Component.translatable("message.sscfe.axolotl_stack.joined"), true);
            target.displayClientMessage(Component.translatable("message.sscfe.axolotl_stack.carrier"), true);
        } else {
            // The chain spring pulls the follower behind the front player over the next ticks.
            actor.displayClientMessage(Component.translatable("message.sscfe.axolotl_chain.joined"), true);
            end.displayClientMessage(Component.translatable("message.sscfe.axolotl_chain.front"), true);
        }
        return true;
    }

    public static Vec3 passengerPosition(Player carrier) {
        // Use body yaw so looking up/down or turning the head does not move the stack.
        double yaw = Math.toRadians(carrier.yBodyRot);
        return carrier.position().add(Math.sin(yaw) * LAYER_BACK_OFFSET, LAYER_HEIGHT,
                -Math.cos(yaw) * LAYER_BACK_OFFSET);
    }

    /** Called on the server; invalid forms and interrupted players leave the group. */
    public static void maintain(Player player) {
        if (player.level().isClientSide) return;
        if (!eligible(player) || player.isShiftKeyDown()) {
            leave(player);
            return;
        }
        AxolotlStackAccess self = (AxolotlStackAccess) player;
        if (self.sscfe$isLinkRider() && (!(player.getVehicle() instanceof Player carrier)
                || !isLink(carrier, player, self.sscfe$linkMode()))) {
            leave(player);
        }
        for (Entity rider : player.getPassengers()) {
            if (rider instanceof Player upper && ((AxolotlStackAccess) upper).sscfe$isLinkRider()
                    && !isAnyLink(player, upper)) {
                ((AxolotlStackAccess) upper).sscfe$setLinkMode(AxolotlStackAccess.NONE);
                upper.stopRiding();
            }
        }
        if (isLinked(player)) player.setPose(Pose.SWIMMING);
    }

    public static void leave(Player player) {
        boolean rider = ((AxolotlStackAccess) player).sscfe$isLinkRider();
        ((AxolotlStackAccess) player).sscfe$setLinkMode(AxolotlStackAccess.NONE);
        if (rider && player.getVehicle() instanceof Player) player.stopRiding();
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof Player upper && ((AxolotlStackAccess) upper).sscfe$isLinkRider()) {
                ((AxolotlStackAccess) upper).sscfe$setLinkMode(AxolotlStackAccess.NONE);
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
