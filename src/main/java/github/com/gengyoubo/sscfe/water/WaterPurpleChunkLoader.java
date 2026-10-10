package github.com.gengyoubo.sscfe.water;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Server-thread ownership; only the disk probe completes off-thread. Tickets are never saved. */
final class WaterPurpleChunkLoader implements AutoCloseable {
    enum State { READY, WAIT, MISSING, FAILED }
    private record Key(ServerLevel level, ChunkPos position) {}
    private static final TicketType<UUID> TICKET = TicketType.create("sscfe_water_purple", Comparator.<UUID>naturalOrder(), 60);
    private static final Map<Key, Set<UUID>> OWNERS = new HashMap<>();
    private static int requestTick = -1, requests;
    private final ServerLevel level;
    private final boolean generateChunks;
    private final UUID owner = UUID.randomUUID();
    private final Map<ChunkPos, Entry> entries = new LinkedHashMap<>();
    private boolean closed;
    private static final class Entry {
        CompletableFuture<Boolean> probe;
        State state = State.WAIT;
        boolean reserved, ticket;
        int renewedAt;
    }

    WaterPurpleChunkLoader(ServerLevel level) { this(level, WaterCurseConfig.GENERATE_CHUNKS.get()); }
    WaterPurpleChunkLoader(ServerLevel level, boolean generateChunks) {
        this.level = level;
        this.generateChunks = generateChunks;
    }
    static int totalHeldChunks() { return OWNERS.size(); }
    int heldChunks() { return (int) entries.values().stream().filter(e -> e.reserved).count(); }

    static Set<ChunkPos> footprint(Vec3 start, Vec3 end, double radius) {
        Vec3 flatStart = new Vec3(start.x, 0, start.z), delta = new Vec3(end.x - start.x, 0, end.z - start.z);
        double length = delta.length();
        Vec3 direction = length > 1E-9D ? delta.scale(1D / length) : Vec3.ZERO;
        int minX = (int) Math.floor((Math.min(start.x, end.x) - radius - 1E-7D) / 16D);
        int maxX = (int) Math.floor((Math.max(start.x, end.x) + radius) / 16D);
        int minZ = (int) Math.floor((Math.min(start.z, end.z) - radius - 1E-7D) / 16D);
        int maxZ = (int) Math.floor((Math.max(start.z, end.z) + radius) / 16D);
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            AABB box = new AABB(x * 16D, -1D, z * 16D, x * 16D + 16D, 1D, z * 16D + 16D);
            if (Double.isFinite(WaterPurpleRules.boxEntryDistance(box, flatStart, direction, length, radius))) chunks.add(new ChunkPos(x, z));
        }
        return chunks;
    }

    private boolean requestAvailable() {
        refreshBudget();
        if (requests >= WaterCurseConfig.CHUNK_REQUEST_BUDGET.get()) return false;
        requests++; return true;
    }
    private void refreshBudget() {
        int tick = level.getServer().getTickCount();
        if (tick != requestTick) { requestTick = tick; requests = 0; }
    }

    /** Reserve the whole critical footprint atomically, so several beams cannot deadlock on partial footprints. */
    State ensure(Set<ChunkPos> required) {
        refreshBudget();
        if (closed) return State.FAILED;
        for (ChunkPos pos : required) {
            Entry existing = entries.get(pos);
            if (existing != null && (existing.state == State.MISSING || existing.state == State.FAILED)) return existing.state;
        }
        int localNew = 0, globalNew = 0;
        for (ChunkPos pos : required) {
            Entry existing = entries.get(pos);
            if (existing == null || !existing.reserved) {
                localNew++;
                if (!OWNERS.containsKey(new Key(level, pos))) globalNew++;
            }
        }
        if (heldChunks() + localNew > WaterCurseConfig.MAX_CHUNKS_PER_BEAM.get()
                || OWNERS.size() + globalNew > WaterCurseConfig.MAX_TOTAL_CHUNKS.get()) return State.WAIT;
        for (ChunkPos pos : required) {
            Entry entry = entries.computeIfAbsent(pos, unused -> new Entry());
            if (!entry.reserved) {
                OWNERS.computeIfAbsent(new Key(level, pos), unused -> new HashSet<>()).add(owner);
                entry.reserved = true;
            }
        }
        State state = State.READY;
        for (ChunkPos pos : required) {
            State result = prepare(pos, entries.get(pos));
            if (result == State.MISSING || result == State.FAILED) return result;
            if (result != State.READY) state = State.WAIT;
        }
        return state;
    }

    private State prepare(ChunkPos pos, Entry entry) {
        if (entry.state == State.MISSING || entry.state == State.FAILED) return entry.state;
        boolean loaded = level.getChunkSource().getChunkNow(pos.x, pos.z) != null;
        if (!loaded && !generateChunks) {
            if (entry.probe == null) {
                if (!requestAvailable()) return State.WAIT;
                entry.probe = level.getChunkSource().chunkMap.read(pos).thenApply(tag -> tag.map(nbt -> {
                    var data = nbt.contains("Level") ? nbt.getCompound("Level") : nbt;
                    return ChunkStatus.byName(data.getString("Status")) == ChunkStatus.FULL;
                }).orElse(false));
            }
            if (!entry.probe.isDone()) return State.WAIT;
            if (entry.probe.isCompletedExceptionally() || entry.probe.isCancelled()) {
                entry.state = State.FAILED; release(pos, entry); return entry.state;
            }
            if (!entry.probe.getNow(false)) {
                entry.state = State.MISSING; release(pos, entry); return entry.state;
            }
        }
        if (!entry.ticket) {
            if (!requestAvailable()) return State.WAIT;
            level.getChunkSource().addRegionTicket(TICKET, pos, 0, owner);
            entry.ticket = true;
            entry.renewedAt = level.getServer().getTickCount();
        }
        renew(pos, entry);
        entry.state = loaded && level.areEntitiesLoaded(pos.toLong()) ? State.READY : State.WAIT;
        return entry.state;
    }

    void prefetch(Set<ChunkPos> window) {
        refreshBudget();
        for (ChunkPos pos : window) {
            if (requests >= WaterCurseConfig.CHUNK_REQUEST_BUDGET.get()) break;
            ensure(Set.of(pos));
        }
    }

    void trim(Set<ChunkPos> window, WaterPurpleBlockScan terrain, boolean breakBlocks, ChunkPos blocked) {
        entries.entrySet().removeIf(item -> {
            ChunkPos pos = item.getKey();
            if (window.contains(pos) || pos.equals(blocked) || (breakBlocks && terrain.needsChunk(pos))) return false;
            release(pos, item.getValue()); return true;
        });
        // Renew only our own expiring tickets; another beam/player's tickets are untouched.
        entries.forEach(this::renew);
    }

    private void renew(ChunkPos pos, Entry entry) {
        int tick = level.getServer().getTickCount();
        if (entry.ticket && tick - entry.renewedAt >= 20) {
            level.getChunkSource().addRegionTicket(TICKET, pos, 0, owner); entry.renewedAt = tick;
        }
    }

    private void release(ChunkPos pos, Entry entry) {
        if (entry.ticket) { level.getChunkSource().removeRegionTicket(TICKET, pos, 0, owner); entry.ticket = false; }
        if (entry.reserved) {
            Key key = new Key(level, pos);
            Set<UUID> owners = OWNERS.get(key);
            if (owners != null) { owners.remove(owner); if (owners.isEmpty()) OWNERS.remove(key); }
            entry.reserved = false;
        }
    }
    @Override public void close() {
        if (closed) return;
        entries.forEach(this::release); entries.clear(); closed = true;
    }
}
