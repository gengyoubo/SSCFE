package github.com.gengyoubo.sscfe.water;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ChunkPos;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Comparator;
import java.util.PriorityQueue;

/** Disjoint world-grid slabs: no rotated point samples and no path-sized visited set. */
final class WaterPurpleBlockScan {
    private record Pending(BlockPos position, double entryDistance) {}
    private final PriorityQueue<Pending> pending = new PriorityQueue<>(Comparator.comparingDouble(Pending::entryDistance));
    private final Long2IntOpenHashMap pendingChunks = new Long2IntOpenHashMap();
    private final Vec3 origin, direction;
    private final double range, radius, axisOrigin, axisDirection;
    private final int axis, step, minY, maxY;
    private int lastSlice;
    private int nextSlice;
    private Slice current;

    WaterPurpleBlockScan(Vec3 origin, Vec3 direction, double range, double radius, int minY, int maxY) {
        this.origin = origin; this.direction = direction; this.range = range; this.radius = radius;
        this.minY = minY; this.maxY = maxY;
        axis = Math.abs(direction.x) >= Math.abs(direction.y) && Math.abs(direction.x) >= Math.abs(direction.z)
                ? 0 : Math.abs(direction.y) >= Math.abs(direction.z) ? 1 : 2;
        axisOrigin = coordinate(origin, axis); axisDirection = coordinate(direction, axis);
        step = axisDirection >= 0D ? 1 : -1;
        nextSlice = floor(axisOrigin - step * radius - (step > 0 ? 1E-7D : 0D));
        lastSlice = floor(axisOrigin + axisDirection * range + step * radius - (step < 0 ? 1E-7D : 0D));
        if (axis == 1) {
            nextSlice = step > 0 ? Math.max(nextSlice, minY) : Math.min(nextSlice, maxY);
        }
    }

    boolean hasCandidate(double reachedDistance) {
        if (current != null) return true;
        if (step > 0 ? nextSlice > lastSlice : nextSlice < lastSlice) return false;
        if (axis == 1 && (nextSlice < minY || nextSlice > maxY)) return false;
        int reachedSlice = floor(axisOrigin + axisDirection * reachedDistance + step * radius - (step < 0 ? 1E-7D : 0D));
        return step > 0 ? nextSlice <= reachedSlice : nextSlice >= reachedSlice;
    }

    /** Consumes one candidate. A clipped, empty slab also consumes one budget unit. */
    BlockPos nextCandidate() {
        if (current == null) {
            current = slice(nextSlice);
            nextSlice += step;
        }
        BlockPos position = current.next();
        if (current.finished()) current = null;
        return position;
    }

    void defer(BlockPos position, double entryDistance) {
        pending.add(new Pending(position, entryDistance));
        pendingChunks.addTo(new ChunkPos(position).toLong(), 1);
    }
    BlockPos pollReached(double distance) {
        if (pending.isEmpty() || pending.peek().entryDistance > distance + 1E-9D) return null;
        BlockPos pos = pending.remove().position;
        long chunk = new ChunkPos(pos).toLong();
        if (pendingChunks.addTo(chunk, -1) == 1) pendingChunks.remove(chunk);
        return pos;
    }
    boolean needsChunk(ChunkPos chunk) {
        if (pendingChunks.containsKey(chunk.toLong())) return true;
        if (finished()) return false;
        if (axis == 1) return true;
        int slab = current == null ? nextSlice : nextSlice - step;
        int min = (axis == 0 ? chunk.x : chunk.z) * 16;
        return step > 0 ? min + 15 >= slab : min <= slab;
    }
    void stopAt(double distance) {
        lastSlice = floor(axisOrigin + axisDirection * distance + step * radius - (step < 0 ? 1E-7D : 0D));
        pending.removeIf(item -> {
            if (item.entryDistance <= distance + 1E-9D) return false;
            long chunk = new ChunkPos(item.position).toLong();
            if (pendingChunks.addTo(chunk, -1) == 1) pendingChunks.remove(chunk);
            return true;
        });
    }
    boolean finished() {
        boolean exhausted = (step > 0 ? nextSlice > lastSlice : nextSlice < lastSlice)
                || (axis == 1 && (nextSlice < minY || nextSlice > maxY));
        return current == null && exhausted && pending.isEmpty();
    }

    private Slice slice(int coordinate) {
        double t0 = (coordinate - axisOrigin) / axisDirection;
        double t1 = (coordinate + 1D - axisOrigin) / axisDirection;
        double padding = radius / Math.abs(axisDirection);
        double low = Math.max(0D, Math.min(t0, t1) - padding);
        double high = Math.min(range, Math.max(t0, t1) + padding);
        Vec3 start = origin.add(direction.scale(low)), end = origin.add(direction.scale(high));
        int x0 = floor(Math.min(start.x, end.x) - radius - 1E-7D), x1 = floor(Math.max(start.x, end.x) + radius);
        int y0 = Math.max(minY, floor(Math.min(start.y, end.y) - radius - 1E-7D));
        int y1 = Math.min(maxY, floor(Math.max(start.y, end.y) + radius));
        int z0 = floor(Math.min(start.z, end.z) - radius - 1E-7D), z1 = floor(Math.max(start.z, end.z) + radius);
        if (axis == 0) { x0 = coordinate; x1 = coordinate; }
        if (axis == 1) { y0 = coordinate; y1 = coordinate; }
        if (axis == 2) { z0 = coordinate; z1 = coordinate; }
        return new Slice(x0, y0, z0, Math.max(0, x1 - x0 + 1), Math.max(0, y1 - y0 + 1), Math.max(0, z1 - z0 + 1));
    }

    private static double coordinate(Vec3 vector, int axis) { return axis == 0 ? vector.x : axis == 1 ? vector.y : vector.z; }
    private static int floor(double value) { return (int) Math.floor(value); }
    private static final class Slice {
        final int x, y, z, sizeX, sizeY, total;
        int cursor;
        Slice(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
            this.x = x; this.y = y; this.z = z; this.sizeX = sizeX; this.sizeY = sizeY;
            total = sizeX * sizeY * sizeZ;
        }
        BlockPos next() {
            if (finished()) return null;
            int index = cursor++;
            return new BlockPos(x + index % sizeX, y + (index / sizeX) % sizeY, z + index / (sizeX * sizeY));
        }
        boolean finished() { return cursor >= total; }
    }
}
