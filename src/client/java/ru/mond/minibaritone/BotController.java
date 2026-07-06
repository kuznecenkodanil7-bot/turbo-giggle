package ru.mond.minibaritone;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class BotController {
    private enum Mode {
        IDLE,
        GOTO,
        TREE_SCAN,
        TREE_MINE
    }

    private Mode mode = Mode.IDLE;
    private boolean breakAhead = true;
    private boolean placeBlocks = true;

    private BlockPos target;
    private List<BlockPos> path = List.of();
    private int pathIndex = 0;
    private int repathCooldown = 0;

    private Vec3d lastPosition = Vec3d.ZERO;
    private int stuckTicks = 0;

    private TreeTask treeTask;
    private BlockPos activeTreeLog;
    private int infoCooldown = 0;

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) return;

        if (infoCooldown > 0) infoCooldown--;
        if (repathCooldown > 0) repathCooldown--;

        switch (mode) {
            case IDLE -> stopMovement(client);
            case GOTO -> {
                boolean reached = followTarget(client, target);
                if (reached) {
                    message(client, "[MiniBaritone] Цель достигнута");
                    stop();
                }
            }
            case TREE_SCAN -> tickTreeScan(client);
            case TREE_MINE -> tickTreeMine(client);
        }
    }

    public void gotoBlock(BlockPos target) {
        this.mode = Mode.GOTO;
        this.target = target.toImmutable();
        clearPath();
    }

    public void mineTrees(int radius) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        this.mode = Mode.TREE_SCAN;
        this.treeTask = new TreeTask(client.world, client.player.getBlockPos(), radius);
        this.activeTreeLog = null;
        this.target = null;
        clearPath();
    }

    public void stop() {
        this.mode = Mode.IDLE;
        this.target = null;
        this.treeTask = null;
        this.activeTreeLog = null;
        clearPath();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) stopMovement(client);
    }

    public void setBreakAhead(boolean enabled) {
        this.breakAhead = enabled;
        clearPath();
    }

    public void setPlaceBlocks(boolean enabled) {
        this.placeBlocks = enabled;
        clearPath();
    }

    public String statusLine() {
        String extra = "";
        if (target != null) extra += " target=" + target.toShortString();
        if (treeTask != null) extra += " logs=" + treeTask.foundCount();
        if (activeTreeLog != null) extra += " activeLog=" + activeTreeLog.toShortString();
        return "[MiniBaritone] mode=" + mode
            + " breakAhead=" + breakAhead
            + " placeBlocks=" + placeBlocks
            + " path=" + path.size()
            + extra;
    }

    private void tickTreeScan(MinecraftClient client) {
        stopMovement(client);
        if (treeTask == null || client.world == null) {
            stop();
            return;
        }

        boolean finished = treeTask.scanTick(client.world);
        if (!finished) {
            if (infoCooldown == 0) {
                message(client, "[MiniBaritone] Сканирую деревья... найдено логов: " + treeTask.foundCount());
                infoCooldown = 60;
            }
            return;
        }

        message(client, "[MiniBaritone] Сканирование завершено. Логов в очереди: " + treeTask.foundCount());
        mode = Mode.TREE_MINE;
        clearPath();
    }

    private void tickTreeMine(MinecraftClient client) {
        if (treeTask == null || client.world == null || client.player == null) {
            stop();
            return;
        }

        if (activeTreeLog != null && !BlockUtils.isLog(client.world, activeTreeLog)) {
            activeTreeLog = null;
            clearPath();
        }

        if (activeTreeLog == null) {
            activeTreeLog = treeTask.pollNextExistingLog(client.world);
            clearPath();
        }

        if (activeTreeLog == null) {
            message(client, "[MiniBaritone] Добыча дерева завершена");
            stop();
            return;
        }

        if (canReach(client.player, activeTreeLog)) {
            stopMovement(client);
            BlockUtils.breakBlock(client, activeTreeLog);
            return;
        }

        BlockPos stand = Pathfinder.findStandNear(client.world, activeTreeLog, breakAhead, placeBlocks);
        if (stand == null) {
            activeTreeLog = null;
            clearPath();
            return;
        }

        boolean reached = followTarget(client, stand);
        if (reached && canReach(client.player, activeTreeLog)) {
            stopMovement(client);
            BlockUtils.breakBlock(client, activeTreeLog);
        }
    }

    private boolean followTarget(MinecraftClient client, BlockPos goal) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null || goal == null) return true;

        BlockPos playerPos = player.getBlockPos();
        updateStuckState(player);

        if (isNear(playerPos, goal)) {
            stopMovement(client);
            return true;
        }

        if (path.isEmpty() || pathIndex >= path.size() || repathCooldown == 0 || stuckTicks > 45) {
            path = Pathfinder.findPath(world, playerPos, goal, breakAhead, placeBlocks, Pathfinder.DEFAULT_MAX_NODES);
            pathIndex = path.size() > 1 ? 1 : 0;
            repathCooldown = 20;
            stuckTicks = 0;
        }

        if (path.isEmpty()) {
            stopMovement(client);
            if (infoCooldown == 0) {
                message(client, "[MiniBaritone] Не смог построить путь к " + goal.toShortString());
                infoCooldown = 80;
            }
            return false;
        }

        if (pathIndex >= path.size()) {
            stopMovement(client);
            return isNear(player.getBlockPos(), goal);
        }

        BlockPos next = path.get(pathIndex);
        if (isAtNode(player, next)) {
            pathIndex++;
            if (pathIndex >= path.size()) return false;
            next = path.get(pathIndex);
        }

        prepareNextNode(client, next);
        return false;
    }

    private void prepareNextNode(MinecraftClient client, BlockPos next) {
        if (client.world == null || client.player == null) return;

        BlockPos feet = next;
        BlockPos head = next.up();
        BlockPos floor = next.down();

        if (breakAhead) {
            if (!BlockUtils.isSafeAirLike(client.world, feet) && BlockUtils.breakBlock(client, feet)) {
                stopMovement(client);
                return;
            }
            if (!BlockUtils.isSafeAirLike(client.world, head) && BlockUtils.breakBlock(client, head)) {
                stopMovement(client);
                return;
            }
        }

        if (placeBlocks && !BlockUtils.isSolidFloor(client.world, floor)) {
            stopMovement(client);
            BlockUtils.placeBlock(client, floor);
            return;
        }

        moveToward(client, next);
    }

    private void moveToward(MinecraftClient client, BlockPos next) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        Vec3d targetCenter = Vec3d.ofBottomCenter(next).add(0.0, 0.05, 0.0);
        faceXZ(player, targetCenter);

        client.options.forwardKey.setPressed(true);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.sprintKey.setPressed(true);

        boolean needJump = next.getY() > player.getBlockPos().getY();
        if (!needJump && client.world != null) {
            BlockPos inFront = player.getBlockPos().offset(player.getHorizontalFacing());
            needJump = !BlockUtils.isSafeAirLike(client.world, inFront) && BlockUtils.isSolidFloor(client.world, inFront);
        }
        client.options.jumpKey.setPressed(needJump);
        client.options.sneakKey.setPressed(false);
    }

    private void updateStuckState(ClientPlayerEntity player) {
        Vec3d current = player.getPos();
        if (current.squaredDistanceTo(lastPosition) < 0.0009) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastPosition = current;
        }
    }

    private static boolean isNear(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        int dz = Math.abs(a.getZ() - b.getZ());
        return dx <= 1 && dy <= 1 && dz <= 1;
    }

    private static boolean isAtNode(ClientPlayerEntity player, BlockPos node) {
        Vec3d center = Vec3d.ofBottomCenter(node);
        double dx = player.getX() - center.x;
        double dz = player.getZ() - center.z;
        double horizontalSq = dx * dx + dz * dz;
        return horizontalSq < 0.18 && Math.abs(player.getY() - node.getY()) < 1.15;
    }

    private static boolean canReach(ClientPlayerEntity player, BlockPos pos) {
        return player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= 20.25;
    }

    private static void faceXZ(ClientPlayerEntity player, Vec3d target) {
        double dx = target.x - player.getX();
        double dz = target.z - player.getZ();
        if (Math.abs(dx) < 0.0001 && Math.abs(dz) < 0.0001) return;
        float yaw = (float)(MathHelper.atan2(dz, dx) * 57.2957763671875D) - 90.0F;
        player.setYaw(yaw);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);
    }

    private void clearPath() {
        this.path = List.of();
        this.pathIndex = 0;
        this.repathCooldown = 0;
        this.stuckTicks = 0;
    }

    private static void stopMovement(MinecraftClient client) {
        if (client == null || client.options == null) return;
        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.sneakKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
    }

    private static void message(MinecraftClient client, String text) {
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal(text), false);
        }
    }
}
