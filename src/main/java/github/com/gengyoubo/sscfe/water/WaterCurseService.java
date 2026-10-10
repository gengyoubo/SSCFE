package github.com.gengyoubo.sscfe.water;

import github.com.gengyoubo.sscfe.Sscfe;
import github.com.gengyoubo.sscfe.init.ModWaterContent;
import github.com.gengyoubo.sscfe.init.ModWaterSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Sscfe.MOD_ID)
public final class WaterCurseService {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Map<UUID, Cast> CASTS = new HashMap<>();
    private static final List<Beam> BEAMS = new ArrayList<>();
    private static final Map<UUID, Long> LAST_CONFIGURE = new HashMap<>();
    private static final int MAX_BEAMS = 8;
    private static boolean tickingBeams;

    public static boolean canUse(Player player) { return player.isCreative() || AxolotlWaterService.isAxolotl(player); }

    public static ItemStack accessory(Player player) {
        if (!canUse(player)) return ItemStack.EMPTY;
        // Creative testing also works with a held accessory, without a Curios slot.
        if (player.isCreative()) {
            if (player.getMainHandItem().is(ModWaterContent.WATER_CURSE.get())) return player.getMainHandItem();
            if (player.getOffhandItem().is(ModWaterContent.WATER_CURSE.get())) return player.getOffhandItem();
        }
        if (!ModList.get().isLoaded("curios")) return ItemStack.EMPTY;
        return CuriosWaterCompat.equipped(player).stream().filter(stack -> stack.is(ModWaterContent.WATER_CURSE.get()))
                .findFirst().orElse(ItemStack.EMPTY);
    }

    public static boolean casting(Player player) { return CASTS.containsKey(player.getUUID()); }

    public static void input(ServerPlayer player, WaterCurseNetwork.Action action, int requestedTicks) {
        if (action == WaterCurseNetwork.Action.CANCEL) { cancel(player); return; }
        // RELEASE from older clients does not determine server casting time.
        if (action == WaterCurseNetwork.Action.RELEASE) return;
        ItemStack stack = accessory(player);
        if (stack.isEmpty() || player.isSpectator() || !player.isAlive()) return;
        if (action == WaterCurseNetwork.Action.CONFIGURE) {
            if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) return;
            long now = player.level().getGameTime();
            if (now - LAST_CONFIGURE.getOrDefault(player.getUUID(), -100L) < 4) return;
            LAST_CONFIGURE.put(player.getUUID(), now);
            WaterCurseItem.configure(player, stack);
        } else if (action == WaterCurseNetwork.Action.START && WaterCurseItem.mode(stack) == WaterCurseItem.Mode.PURPLE) {
            if (casting(player)) { cancel(player); return; }
            start(player, stack, requestedTicks);
        }
    }

    private static void start(ServerPlayer player, ItemStack stack, int requestedTicks) {
        if (!player.isCreative() && (player.isPassenger() || player.isSleeping() || player.isFallFlying()
                || player.getCooldowns().isOnCooldown(ModWaterContent.WATER_CURSE.get()))) return;
        int min = WaterCurseConfig.MIN_CAST_TICKS.get();
        int max = Math.max(min, WaterCurseConfig.MAX_CAST_TICKS.get());
        int ticks = Math.max(min, Math.min(max, requestedTicks));
        if (!canPay(player, ticks)) { insufficient(player); return; }
        if (player.isCreative()) {
            player.stopRiding();
            if (player.isSleeping()) player.stopSleepInBed(false, true);
            player.stopFallFlying();
        }
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 origin = WaterPurpleRules.chargeOrigin(player.getEyePosition(), direction, WaterCurseConfig.CHARGE_DISTANCE.get());
        Cast cast = new Cast(player, stack, player.serverLevel(), player.position(), origin, direction,
                player.getYRot(), player.getXRot(), player.level().getGameTime(), ticks, WaterCurseItem.special(stack));
        CASTS.put(player.getUUID(), cast);
        lock(cast);
        player.connection.teleport(cast.position.x, cast.position.y, cast.position.z, cast.yaw, cast.pitch);
        broadcast(cast, WaterCurseNetwork.Stage.CHARGE);
        cast.level.playSound(null, player.blockPosition(), ModWaterSounds.CHARGE.get(), SoundSource.PLAYERS, 1F, 0.8F);
    }

    public static boolean canPay(Player player, int ticks) {
        if (player.isCreative()) return true;
        return player.getAirSupply() >= WaterPurpleRules.moistureCost(ticks, player.getMaxAirSupply())
                && AxolotlWaterService.availableWater(player) >= WaterPurpleRules.waterCost(ticks);
    }

    public static boolean pay(Player player, int ticks) {
        if (player.isCreative()) return true;
        if (!canPay(player, ticks) || !AxolotlWaterService.consumeWater(player, WaterPurpleRules.waterCost(ticks))) return false;
        player.setAirSupply(player.getAirSupply() - WaterPurpleRules.moistureCost(ticks, player.getMaxAirSupply()));
        return true;
    }

    private static void insufficient(Player player) { player.displayClientMessage(Component.translatable("message.sscfe.water_curse.resources"), true); }

    private static void lock(Cast cast) {
        cast.player.setDeltaMovement(Vec3.ZERO);
        cast.player.setPos(cast.position.x, cast.position.y, cast.position.z);
        cast.player.setYRot(cast.yaw); cast.player.setXRot(cast.pitch);
        cast.player.setYHeadRot(cast.yaw); cast.player.setYBodyRot(cast.yaw);
        cast.player.fallDistance = 0F;
    }

    private static void broadcast(Cast cast, WaterCurseNetwork.Stage stage) {
        WaterCurseNetwork.broadcast(cast.player, WaterCurseNetwork.effect(cast.player, stage, cast.special,
                cast.startedAt, cast.duration, cast.position, cast.origin, cast.direction));
    }

    public static void cancel(Player player) {
        Cast cast = CASTS.remove(player.getUUID());
        if (cast != null) {
            broadcast(cast, WaterCurseNetwork.Stage.CANCEL);
            // A server teleport also restores clients which missed the first lock packet.
            if (player instanceof ServerPlayer serverPlayer && serverPlayer.level() == cast.level && player.isAlive())
                serverPlayer.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Cast cast : List.copyOf(CASTS.values())) {
            ServerPlayer player = cast.player;
            ItemStack equipped = accessory(player);
            if (!player.isAlive() || player.isRemoved() || player.level() != cast.level || player.isSpectator()
                    || equipped != cast.stack || WaterCurseItem.mode(equipped) != WaterCurseItem.Mode.PURPLE
                    || WaterCurseItem.special(equipped) != cast.special || player.isPassenger()
                    || player.position().distanceToSqr(cast.position) > 64) {
                cancel(player); continue;
            }
            lock(cast);
            long elapsed = cast.level.getGameTime() - cast.startedAt;
            if (elapsed >= cast.duration) {
                if (BEAMS.size() >= MAX_BEAMS) {
                    player.displayClientMessage(Component.translatable("message.sscfe.water_curse.busy"), true);
                    cancel(player); continue;
                }
                if (!pay(player, cast.duration)) { insufficient(player); cancel(player); continue; }
                CASTS.remove(player.getUUID());
                BEAMS.add(new Beam(cast));
                broadcast(cast, WaterCurseNetwork.Stage.RELEASE);
                cast.level.playSound(null, player.blockPosition(), ModWaterSounds.RELEASE.get(), SoundSource.PLAYERS, 3F, 0.65F);
                if (!player.isCreative()) player.getCooldowns().addCooldown(ModWaterContent.WATER_CURSE.get(), 200);
                player.connection.teleport(cast.position.x, cast.position.y, cast.position.z, cast.yaw, cast.pitch);
            } else if (elapsed % 20 == 0) broadcast(cast, WaterCurseNetwork.Stage.CHARGE);
        }
        int budget = Math.max(1, WaterCurseConfig.BLOCK_BUDGET.get() / Math.max(1, BEAMS.size()));
        tickingBeams = true;
        try {
            BEAMS.removeIf(beam -> {
                try { return beam.tick(budget); }
                catch (RuntimeException error) { beam.close(); LOGGER.error("Water purple projectile stopped after an error", error); return true; }
            });
        } finally { tickingBeams = false; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damaged(LivingDamageEvent event) {
        if (event.getAmount() > 0F && event.getEntity() instanceof Player player && !player.level().isClientSide) cancel(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void waterCombat(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0F) return;
        if (event.getSource().getEntity() instanceof Player attacker && event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            ItemStack charm = accessory(attacker);
            WaterCurseItem.Mode mode = attacker.getMainHandItem().isEmpty() ? WaterCurseItem.Mode.FIST : WaterCurseItem.Mode.WEAPON;
            if (!charm.isEmpty() && WaterCurseItem.enabled(charm, mode) && (attacker.isCreative() || attacker.getAirSupply() >= 1)) {
                if (!attacker.isCreative()) attacker.setAirSupply(attacker.getAirSupply() - 1);
                float multiplier = event.getEntity() instanceof Blaze || event.getEntity() instanceof MagmaCube ? 2.5F : 1.5F;
                event.setAmount(event.getAmount() * multiplier);
                splash(event.getEntity());
            }
        }
        if (event.getEntity() instanceof Player victim) {
            ItemStack charm = accessory(victim);
            if (!charm.isEmpty() && WaterCurseItem.enabled(charm, WaterCurseItem.Mode.SHIELD) && (victim.isCreative() || victim.getAirSupply() >= 1)) {
                float reduced = WaterPurpleRules.shieldDamage(event.getAmount());
                if (!victim.isCreative()) victim.setAirSupply(victim.getAirSupply() - 1);
                event.setAmount(reduced);
                splash(victim);
            }
        }
    }

    private static void splash(LivingEntity entity) {
        ((ServerLevel) entity.level()).sendParticles(ParticleTypes.SPLASH, entity.getX(), entity.getY() + 0.8D,
                entity.getZ(), 12, 0.4D, 0.4D, 0.4D, 0.05D);
    }

    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) { cancel(player); removeBeams(player); }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        cancel(event.getEntity()); LAST_CONFIGURE.remove(event.getEntity().getUUID());
        removeBeams(event.getEntity());
    }
    private static void removeBeams(Player player) {
        BEAMS.stream().filter(beam -> beam.player == player).forEach(Beam::close);
        if (!tickingBeams) BEAMS.removeIf(beam -> beam.closed);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { cancel(event.getEntity()); removeBeams(event.getEntity()); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { BEAMS.forEach(Beam::close); BEAMS.clear(); }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        BEAMS.stream().filter(beam -> beam.level == event.getLevel()).forEach(Beam::close);
        if (!tickingBeams) BEAMS.removeIf(beam -> beam.closed);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { CASTS.clear(); BEAMS.clear(); LAST_CONFIGURE.clear(); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer receiver) {
            for (Cast cast : CASTS.values()) WaterCurseNetwork.send(receiver, WaterCurseNetwork.effect(cast.player, WaterCurseNetwork.Stage.CHARGE,
                    cast.special, cast.startedAt, cast.duration, cast.position, cast.origin, cast.direction));
            for (Beam beam : BEAMS) if (beam.cast != null && !beam.visualEnded) WaterCurseNetwork.send(receiver, beam.flight(false));
        }
    }

    public static boolean breakable(BlockState state, ServerLevel level, BlockPos pos) {
        float hardness = state.getDestroySpeed(level, pos);
        return !state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty()
                && hardness >= 0F && hardness < 50F && !state.is(BlockTags.DRAGON_IMMUNE)
                && !state.is(BlockTags.WITHER_IMMUNE);
    }

    public static boolean ore(BlockState state) { return state.is(ResourceLocationTags.ORES); }
    private static final class ResourceLocationTags {
        static final net.minecraft.tags.TagKey<Block> ORES = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("forge", "ores"));
    }

    private record Cast(ServerPlayer player, ItemStack stack, ServerLevel level, Vec3 position, Vec3 origin, Vec3 direction,
                        float yaw, float pitch, long startedAt, int duration, boolean special) {}

    /** Sweep the moving core each tick; budgeted terrain work cannot run ahead of it. */
    static final class Beam {
        final ServerPlayer player;
        final ServerLevel level;
        final Vec3 origin, direction;
        final int duration, radius, flightTicks;
        final double range;
        final WaterPurpleBlockScan blockScan;
        final Cast cast;
        final WaterPurpleChunkLoader chunkLoader;
        BlockPos blockedTerrain;
        int age, ticks, waitingTicks;
        boolean stopped, closed, moving = true, visualEnded;
        Beam(Cast cast) { this(cast.player, cast.level, cast.origin, cast.direction, cast.duration, cast); }
        Beam(ServerPlayer player, ServerLevel level, Vec3 origin, Vec3 direction, int duration) {
            this(player, level, origin, direction, duration, null);
        }
        private Beam(ServerPlayer player, ServerLevel level, Vec3 origin, Vec3 direction, int duration, Cast cast) {
            this.player = player; this.level = level; this.origin = origin; this.direction = direction; this.duration = duration;
            this.cast = cast;
            chunkLoader = cast != null && WaterCurseConfig.CHUNK_LOADING.get() ? new WaterPurpleChunkLoader(level) : null;
            range = WaterPurpleRules.range(duration); radius = WaterPurpleRules.breakRadius(duration);
            blockScan = new WaterPurpleBlockScan(origin, direction, range, radius, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
            flightTicks = WaterPurpleRules.flightTicks(duration);
        }
        boolean tick(int budget) {
            if (closed) return true;
            if (!player.isAlive() || player.isRemoved() || player.level() != level) { close(); return true; }
            ticks++;
            boolean breaking = WaterCurseConfig.BREAK_BLOCKS.get() && player.mayBuild();
            double previousDistance = WaterPurpleRules.travelDistance(duration, age);
            double nextDistance = WaterPurpleRules.travelDistance(duration, age + 1);
            boolean advance = !stopped && age < flightTicks;
            if (chunkLoader != null) {
                Vec3 head = origin.add(direction.scale(previousDistance));
                double windowRadius = Math.max(WaterPurpleRules.damageRadius(duration), radius + WaterCurseConfig.SIDE_MARGIN_CHUNKS.get() * 16D);
                Vec3 rear = origin.add(direction.scale(Math.max(0D, previousDistance - WaterCurseConfig.REAR_CHUNKS.get() * 16D)));
                Vec3 front = origin.add(direction.scale(Math.min(range, previousDistance + WaterCurseConfig.PRELOAD_CHUNKS.get() * 16D)));
                Set<ChunkPos> window = chunks(rear, front, windowRadius);
                chunkLoader.trim(window, blockScan, breaking, blockedTerrain == null ? null : new ChunkPos(blockedTerrain));
                if (advance) {
                    var state = chunkLoader.ensure(chunks(head, origin.add(direction.scale(nextDistance)), WaterPurpleRules.damageRadius(duration)));
                    if (state == WaterPurpleChunkLoader.State.MISSING) { stopFlight("chunk_missing"); advance = false; }
                    else if (state == WaterPurpleChunkLoader.State.FAILED) { fail("chunk_load_failed"); return true; }
                    else advance = state == WaterPurpleChunkLoader.State.READY;
                }
                if (advance) chunkLoader.prefetch(window);
            }
            if (advance) age++;
            double distance = WaterPurpleRules.travelDistance(duration, age);
            boolean progressed = advance;
            boolean wasMoving = moving;
            moving = advance && age < flightTicks;
            if (advance) {
                double hitRadius = WaterPurpleRules.damageRadius(duration);
                Vec3 previousCore = origin.add(direction.scale(previousDistance));
                Vec3 core = origin.add(direction.scale(distance));
                AABB bounds = new AABB(previousCore, core).inflate(hitRadius);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds,
                        target -> target != player && target.isAlive() && !target.isSpectator())) {
                    if (target instanceof Player other && !player.canHarmPlayer(other)) continue;
                    if (WaterPurpleRules.distanceToRaySquared(target.getBoundingBox().getCenter(), previousCore, direction,
                            distance - previousDistance) > hitRadius * hitRadius) continue;
                    int invulnerable = target.invulnerableTime;
                    target.invulnerableTime = 0;
                    target.hurt(level.damageSources().indirectMagic(player, player), WaterPurpleRules.damagePerTick(duration));
                    target.invulnerableTime = invulnerable;
                }
            }
            for (int i = 0; breaking && i < budget; i++) {
                BlockPos pos = blockedTerrain != null ? blockedTerrain : blockScan.pollReached(distance);
                blockedTerrain = null;
                if (pos == null) {
                    if (!blockScan.hasCandidate(distance)) break;
                    pos = blockScan.nextCandidate();
                    if (pos == null) continue;
                    progressed = true;
                    if (!level.isInWorldBounds(pos) || (chunkLoader == null && !level.hasChunkAt(pos))) continue;
                    double entry = WaterPurpleRules.blockEntryDistance(pos, origin, direction, range, radius);
                    if (!Double.isFinite(entry)) continue;
                    if (entry > distance + 1E-9D) { if (!stopped) blockScan.defer(pos, entry); continue; }
                }
                if (chunkLoader != null) {
                    var state = chunkLoader.ensure(Set.of(new ChunkPos(pos)));
                    if (state == WaterPurpleChunkLoader.State.WAIT) { blockedTerrain = pos; break; }
                    if (state == WaterPurpleChunkLoader.State.FAILED) { fail("chunk_load_failed"); return true; }
                    if (state == WaterPurpleChunkLoader.State.MISSING) { if (!stopped) stopFlight("chunk_missing"); continue; }
                }
                progressed = true;
                if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (!breakable(state, level, pos) || !level.mayInteract(player, pos)
                        || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player))) continue;
                if (level.getBlockState(pos) != state || level.getBlockEntity(pos) != null) continue;
                // Use the real tool so ore drops follow enchantments; limit ore drops to ten percent.
                if (ore(state) && level.random.nextFloat() < 0.1F)
                    Block.dropResources(state, level, pos, null, player, player.getMainHandItem());
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            if (chunkLoader != null && !progressed && ++waitingTicks > WaterCurseConfig.CHUNK_WAIT_TICKS.get()) { fail("chunk_wait_timeout"); return true; }
            if (progressed) waitingTicks = 0;
            if (cast != null && !visualEnded && (wasMoving != moving || ticks % 5 == 0 || age >= flightTicks || stopped)) {
                boolean ended = age >= flightTicks || stopped;
                WaterCurseNetwork.broadcast(flight(ended)); visualEnded = ended;
            }
            if ((age >= flightTicks || stopped) && (!breaking || (blockedTerrain == null && blockScan.finished()))) { close(); return true; }
            return false;
        }
        private Set<ChunkPos> chunks(Vec3 start, Vec3 end, double radius) {
            if (Math.min(start.y, end.y) - radius >= level.getMaxBuildHeight()
                    || Math.max(start.y, end.y) + radius < level.getMinBuildHeight()) return Set.of();
            return WaterPurpleChunkLoader.footprint(start, end, radius);
        }
        private WaterCurseNetwork.Flight flight(boolean ended) {
            var effect = new WaterCurseNetwork.Effect(player.getUUID(), level.dimension().location(), WaterCurseNetwork.Stage.RELEASE,
                    cast.special, cast.startedAt, level.getGameTime(), duration, cast.position, origin, direction);
            return new WaterCurseNetwork.Flight(effect, WaterPurpleRules.travelDistance(duration, age), moving, ended);
        }
        private void stopFlight(String reason) {
            stopped = true;
            blockScan.stopAt(WaterPurpleRules.travelDistance(duration, age));
            player.displayClientMessage(Component.translatable("message.sscfe.water_curse." + reason), true);
        }
        private void fail(String reason) { stopFlight(reason); close(); }
        void close() {
            if (closed) return;
            closed = true;
            if (chunkLoader != null) chunkLoader.close();
            if (cast != null && !visualEnded) { WaterCurseNetwork.broadcast(flight(true)); visualEnded = true; }
        }
    }
    private WaterCurseService() {}
}
