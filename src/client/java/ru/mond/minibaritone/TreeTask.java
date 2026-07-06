package ru.mond.minibaritone;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;

public final class TreeTask {
    private static final int SCAN_PER_TICK = 20_000;

    private final BlockPos center;
    private final int radius;
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;
    private final int radiusSq;

    private int x;
    private int y;
    private int z;
    private boolean scanFinished;
    private final List<BlockPos> foundLogs = new ArrayList<>();
    private final Queue<BlockPos> queue = new ArrayDeque<>();

    public TreeTask(ClientWorld world, BlockPos center, int radius) {
        this.center = center.toImmutable();
        this.radius = Math.max(1, Math.min(100, radius));
        this.radiusSq = this.radius * this.radius;

        this.minX = center.getX() - this.radius;
        this.maxX = center.getX() + this.radius;
        this.minY = Math.max(world.getBottomY(), center.getY() - this.radius);
        this.maxY = Math.min(world.getTopYInclusive() - 1, center.getY() + this.radius);
        this.minZ = center.getZ() - this.radius;
        this.maxZ = center.getZ() + this.radius;

        this.x = minX;
        this.y = minY;
        this.z = minZ;
    }

    public boolean scanTick(ClientWorld world) {
        if (scanFinished) return true;

        int checked = 0;
        while (checked++ < SCAN_PER_TICK) {
            if (x > maxX) {
                finishScan();
                return true;
            }

            if (insideSphere(x, y, z) && isLoaded(world, x, z)) {
                BlockPos pos = new BlockPos(x, y, z);
                if (BlockUtils.isLog(world, pos)) {
                    foundLogs.add(pos.toImmutable());
                }
            }

            advance();
        }
        return false;
    }

    public boolean isScanFinished() {
        return scanFinished;
    }

    public int foundCount() {
        return scanFinished ? queue.size() : foundLogs.size();
    }

    public BlockPos pollNextExistingLog(ClientWorld world) {
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (BlockUtils.isLog(world, pos)) return pos;
        }
        return null;
    }

    public boolean hasMoreLogs() {
        return !queue.isEmpty();
    }

    private boolean insideSphere(int px, int py, int pz) {
        int dx = px - center.getX();
        int dy = py - center.getY();
        int dz = pz - center.getZ();
        return dx * dx + dy * dy + dz * dz <= radiusSq;
    }

    private boolean isLoaded(ClientWorld world, int px, int pz) {
        return world.isChunkLoaded(px >> 4, pz >> 4);
    }

    private void advance() {
        z++;
        if (z > maxZ) {
            z = minZ;
            y++;
        }
        if (y > maxY) {
            y = minY;
            x++;
        }
    }

    private void finishScan() {
        scanFinished = true;
        foundLogs.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(center)));
        queue.clear();
        queue.addAll(foundLogs);
    }
}
