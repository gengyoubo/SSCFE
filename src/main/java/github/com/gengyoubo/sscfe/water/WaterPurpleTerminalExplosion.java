package github.com.gengyoubo.sscfe.water;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

/** A budgeted sphere, with no vanilla explosion, repeated entity damage, or persistent chunk tickets. */
final class WaterPurpleTerminalExplosion {
    final UUID id = UUID.randomUUID();
    final Vec3 center;
    final double breakRadius, damageRadius, visualRadius, damage;
    final SphereScan scan;
    final Set<ChunkPos> chunks;
    private final ServerLevel level;
    private final ServerPlayer player;
    private final WaterPurpleTerrain terrain;
    private final WaterPurpleChunkLoader loader;
    private final boolean broadcast;
    private BlockPos blocked;
    int age, ticks, waitingTicks;
    boolean started, damaged, finished, aborted;

    WaterPurpleTerminalExplosion(ServerLevel level, ServerPlayer player, Vec3 center, int duration,
                                 WaterPurpleTerrain terrain, WaterPurpleChunkLoader loader, boolean broadcast) {
        this.level = level; this.player = player; this.center = center;
        this.terrain = terrain; this.loader = loader; this.broadcast = broadcast;
        double power = WaterPurpleRules.power(duration);
        breakRadius = WaterCurseConfig.TERMINAL_BREAK_RADIUS.get() * power;
        damageRadius = WaterCurseConfig.TERMINAL_DAMAGE_RADIUS.get() * power;
        visualRadius = WaterCurseConfig.TERMINAL_VISUAL_RADIUS.get() * power;
        damage = WaterCurseConfig.TERMINAL_DAMAGE.get() * power;
        scan = new SphereScan(center, breakRadius, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        double radius = Math.max(breakRadius, damageRadius);
        chunks = center.y - radius >= level.getMaxBuildHeight() || center.y + radius < level.getMinBuildHeight()
                ? Set.of() : WaterPurpleChunkLoader.footprint(center, center, radius);
    }

    boolean tick(int budget) {
        if (finished) return true;
        ticks++;
        boolean breaking = WaterCurseConfig.BREAK_BLOCKS.get() && player.mayBuild();
        if (!started) {
            if (loader != null) {
                var state = loader.ensure(chunks);
                if (state == WaterPurpleChunkLoader.State.MISSING || state == WaterPurpleChunkLoader.State.FAILED) {
                    abort(state == WaterPurpleChunkLoader.State.MISSING ? "chunk_missing" : "chunk_load_failed"); return true;
                }
                if (state != WaterPurpleChunkLoader.State.READY) return waitForChunks();
            }
            started = true; waitingTicks = 0;
            publish(false);
        }
        boolean progress = false;
        if (age < 30 || !breaking || (blocked == null && scan.finished())) age++;
        if (age >= 4 && !damaged) {
            damaged = true;
            damageEntities();
            if (finished) return true;
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4F, 0.65F);
        }
        for (int i = 0; breaking && age >= 4 && i < budget; i++) {
            BlockPos pos = blocked != null ? blocked : scan.next();
            blocked = null;
            if (pos == null) break;
            if (!scan.intersects(pos)) { progress = true; continue; }
            if (loader != null) {
                var state = loader.ensure(Set.of(new ChunkPos(pos)));
                if (state == WaterPurpleChunkLoader.State.WAIT) { blocked = pos; break; }
                if (state != WaterPurpleChunkLoader.State.READY) { abort("chunk_load_failed"); return true; }
            }
            progress = true;
            terrain.process(pos);
        }
        if (blocked != null && !progress) {
            if (waitForChunks()) return true;
        } else waitingTicks = 0;
        if (age >= 60) { finished = true; publish(true); return true; }
        if (ticks % 5 == 0 || (age == 4 && ticks == 4)) publish(false);
        return false;
    }

    private boolean waitForChunks() {
        if (++waitingTicks > WaterCurseConfig.CHUNK_WAIT_TICKS.get()) { abort("chunk_wait_timeout"); return true; }
        return false;
    }
    private void damageEntities() {
        AABB bounds = new AABB(center, center).inflate(damageRadius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds,
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            if (target instanceof Player other && !player.canHarmPlayer(other)) continue;
            if (!Double.isFinite(WaterPurpleRules.boxEntryDistance(target.getBoundingBox(), center, Vec3.ZERO, 0D, damageRadius))) continue;
            int invulnerable = target.invulnerableTime;
            target.invulnerableTime = 0;
            boolean hit = target.hurt(level.damageSources().indirectMagic(player, player), (float) damage);
            target.invulnerableTime = invulnerable;
            if (finished) return;
            if (hit && target.isAlive()) {
                Vec3 push = target.getBoundingBox().getCenter().subtract(center).normalize().scale(2D * Math.min(1D, damage / 500D));
                target.push(push.x, Math.max(0.2D, push.y), push.z); target.hurtMarked = true;
            }
        }
    }
    WaterCurseNetwork.Burst snapshot(boolean end) {
        double work = !WaterCurseConfig.BREAK_BLOCKS.get() || !player.mayBuild() ? 1D : scan.progress();
        return new WaterCurseNetwork.Burst(id, level.dimension().location(), center, visualRadius, age, work, end);
    }
    private void publish(boolean end) { if (broadcast) WaterCurseNetwork.broadcast(snapshot(end)); }
    void cancel() { if (!finished) { aborted = true; finished = true; if (started) publish(true); } }
    private void abort(String reason) {
        player.displayClientMessage(Component.translatable("message.sscfe.water_curse." + reason), true);
        cancel();
    }

    /** Each world-grid cube is enumerated once; intersection uses the whole cube rather than its center. */
    static final class SphereScan {
        final Vec3 center;
        final double radius;
        final int x, y, z, sx, sy, total;
        int cursor;
        SphereScan(Vec3 center, double radius, int minY, int maxY) {
            this.center = center; this.radius = radius;
            x = (int) Math.floor(center.x - radius - 1E-7D);
            y = Math.max(minY, (int) Math.floor(center.y - radius - 1E-7D));
            z = (int) Math.floor(center.z - radius - 1E-7D);
            sx = (int) Math.floor(center.x + radius) - x + 1;
            sy = Math.max(0, Math.min(maxY, (int) Math.floor(center.y + radius)) - y + 1);
            int sz = (int) Math.floor(center.z + radius) - z + 1;
            total = sx * sy * sz;
        }
        BlockPos next() {
            if (finished()) return null;
            int index = cursor++;
            return new BlockPos(x + index % sx, y + (index / sx) % sy, z + index / (sx * sy));
        }
        boolean intersects(BlockPos pos) {
            return Double.isFinite(WaterPurpleRules.blockEntryDistance(pos, center, Vec3.ZERO, 0D, radius));
        }
        boolean finished() { return cursor >= total; }
        double progress() { return total == 0 ? 1D : cursor / (double) total; }
    }
}
