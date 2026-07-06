package ru.mond.minibaritone;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class Pathfinder {
    public static final int DEFAULT_MAX_NODES = 7000;

    private Pathfinder() {}

    public static List<BlockPos> findPath(
        ClientWorld world,
        BlockPos rawStart,
        BlockPos rawTarget,
        boolean allowBreak,
        boolean allowPlace,
        int maxNodes
    ) {
        BlockPos start = normalizeStart(world, rawStart);
        BlockPos target = rawTarget.toImmutable();

        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Map<BlockPos, Node> best = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        Node first = new Node(start, null, 0.0, heuristic(start, target));
        open.add(first);
        best.put(start, first);

        int visited = 0;
        Node closest = first;

        while (!open.isEmpty() && visited++ < maxNodes) {
            Node current = open.poll();
            if (!closed.add(current.pos)) continue;

            if (heuristic(current.pos, target) < heuristic(closest.pos, target)) {
                closest = current;
            }

            if (isGoal(current.pos, target)) {
                return rebuild(current);
            }

            for (BlockPos nextPos : neighbors(world, current.pos, allowBreak, allowPlace)) {
                if (closed.contains(nextPos)) continue;
                double stepCost = stepCost(world, current.pos, nextPos, allowBreak, allowPlace);
                if (stepCost >= 999.0) continue;

                double nextG = current.g + stepCost;
                Node old = best.get(nextPos);
                if (old == null || nextG < old.g) {
                    Node next = new Node(nextPos, current, nextG, nextG + heuristic(nextPos, target));
                    best.put(nextPos, next);
                    open.add(next);
                }
            }
        }

        return closest == first ? List.of() : rebuild(closest);
    }

    public static BlockPos findStandNear(ClientWorld world, BlockPos target, boolean allowBreak, boolean allowPlace) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos base = target.offset(direction);
            for (int dy = -3; dy <= 2; dy++) {
                BlockPos feet = new BlockPos(base.getX(), target.getY() + dy, base.getZ());
                if (!BlockUtils.isInsideWorld(world, feet.getY())) continue;
                if (!BlockUtils.canStandOrMakeAt(world, feet, allowBreak, allowPlace)) continue;
                double distance = feet.getSquaredDistance(target);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = feet.toImmutable();
                }
            }
        }

        return best;
    }

    private static BlockPos normalizeStart(ClientWorld world, BlockPos rawStart) {
        if (BlockUtils.canStandAt(world, rawStart)) return rawStart.toImmutable();
        for (int dy = 0; dy >= -3; dy--) {
            BlockPos p = rawStart.add(0, dy, 0);
            if (BlockUtils.canStandAt(world, p)) return p.toImmutable();
        }
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos p = rawStart.add(0, dy, 0);
            if (BlockUtils.canStandAt(world, p)) return p.toImmutable();
        }
        return rawStart.toImmutable();
    }

    private static boolean isGoal(BlockPos pos, BlockPos target) {
        int dx = Math.abs(pos.getX() - target.getX());
        int dz = Math.abs(pos.getZ() - target.getZ());
        int dy = Math.abs(pos.getY() - target.getY());
        return dx <= 1 && dz <= 1 && dy <= 1;
    }

    private static List<BlockPos> neighbors(ClientWorld world, BlockPos current, boolean allowBreak, boolean allowPlace) {
        List<BlockPos> out = new ArrayList<>(4);
        for (Direction direction : Direction.Type.HORIZONTAL) {
            int x = current.getX() + direction.getOffsetX();
            int z = current.getZ() + direction.getOffsetZ();

            for (int dy : new int[] {0, 1, -1, -2}) {
                int y = current.getY() + dy;
                if (!BlockUtils.isInsideWorld(world, y)) continue;
                BlockPos feet = new BlockPos(x, y, z);
                if (BlockUtils.canStandOrMakeAt(world, feet, allowBreak, allowPlace)) {
                    out.add(feet.toImmutable());
                    break;
                }
            }
        }
        return out;
    }

    private static double stepCost(ClientWorld world, BlockPos from, BlockPos to, boolean allowBreak, boolean allowPlace) {
        double cost = BlockUtils.standCost(world, to, allowBreak, allowPlace);
        int dy = to.getY() - from.getY();
        if (dy > 0) cost += 1.5 * dy;
        if (dy < 0) cost += 0.5 * Math.abs(dy);
        return cost;
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX())
            + Math.abs(a.getZ() - b.getZ())
            + Math.abs(a.getY() - b.getY()) * 1.25;
    }

    private static List<BlockPos> rebuild(Node node) {
        List<BlockPos> path = new ArrayList<>();
        Node cursor = node;
        while (cursor != null) {
            path.add(cursor.pos.toImmutable());
            cursor = cursor.prev;
        }
        Collections.reverse(path);
        return path;
    }

    private static final class Node {
        final BlockPos pos;
        final Node prev;
        final double g;
        final double f;

        Node(BlockPos pos, Node prev, double g, double f) {
            this.pos = pos;
            this.prev = prev;
            this.g = g;
            this.f = f;
        }
    }
}
