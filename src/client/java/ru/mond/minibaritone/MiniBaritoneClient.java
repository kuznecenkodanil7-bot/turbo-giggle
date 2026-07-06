package ru.mond.minibaritone;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class MiniBaritoneClient implements ClientModInitializer {
    public static final String MOD_ID = "minibaritone";
    private static final BotController BOT = new BotController();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(BOT::tick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("mb")
                .then(ClientCommandManager.literal("goto")
                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                        .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                            .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                    int z = IntegerArgumentType.getInteger(ctx, "z");
                                    BOT.gotoBlock(new BlockPos(x, y, z));
                                    ctx.getSource().sendFeedback(Text.literal("[MiniBaritone] Иду к " + x + " " + y + " " + z));
                                    return 1;
                                })))))
                .then(ClientCommandManager.literal("stop")
                    .executes(ctx -> {
                        BOT.stop();
                        ctx.getSource().sendFeedback(Text.literal("[MiniBaritone] Остановлен"));
                        return 1;
                    }))
                .then(ClientCommandManager.literal("status")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(Text.literal(BOT.statusLine()));
                        return 1;
                    }))
                .then(ClientCommandManager.literal("breakahead")
                    .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                            BOT.setBreakAhead(enabled);
                            ctx.getSource().sendFeedback(Text.literal("[MiniBaritone] Ломание блоков перед собой: " + enabled));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("place")
                    .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                            BOT.setPlaceBlocks(enabled);
                            ctx.getSource().sendFeedback(Text.literal("[MiniBaritone] Постановка блоков: " + enabled));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("tree")
                    .executes(ctx -> {
                        BOT.mineTrees(100);
                        ctx.getSource().sendFeedback(Text.literal("[MiniBaritone] Ищу деревья в радиусе 100 блоков"));
                        return 1;
                    })
                    .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> {
                            int radius = IntegerArgumentType.getInteger(ctx, "radius");
                            BOT.mineTrees(radius);
                            ctx.getSource().sendFeedback(Text.literal("[MiniBaritone] Ищу деревья в радиусе " + radius + " блоков"));
                            return 1;
                        })))
        ));
    }
}
