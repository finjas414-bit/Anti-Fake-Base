package net.fakebasefilter;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.Deque;

public class FakeBaseFilterClient implements ClientModInitializer {

    private record Pending(int x, int z, long dueTick) {}

    private final Deque<Pending> queue = new ArrayDeque<>();
    private long tick = 0;

    @Override
    public void onInitializeClient() {
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) ->
                queue.add(new Pending(chunk.getPos().x, chunk.getPos().z, tick + Config.settleTicks)));

        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> { queue.clear(); FakeBaseFilterApi.clear(); });
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> { queue.clear(); FakeBaseFilterApi.clear(); });

        // pro Tick maximal ein Chunk -> kein Lag
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            tick++;
            if (mc.world == null || mc.player == null) return;
            Pending p = queue.peekFirst();
            if (p == null || p.dueTick() > tick) return;
            queue.pollFirst();

            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(p.x(), p.z());
            if (chunk == null) return;

            Verdict v = ChunkAnalyzer.analyze(mc.world, chunk);
            if (v.containers() == 0 && !v.isFake()) return; // uninteressant
            FakeBaseFilterApi.VERDICTS.put(net.minecraft.util.math.ChunkPos.toLong(p.x(), p.z()), v);
            if (Config.chat) report(mc, v);
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("fbf")
                        .then(ClientCommandManager.literal("list").executes(ctx -> {
                            int real = 0, fake = 0;
                            for (Verdict v : FakeBaseFilterApi.all()) {
                                if (v.isRealCandidate()) { real++; msg(ctx.getSource().getClient(), v, true); }
                                else if (v.isFake()) fake++;
                            }
                            ctx.getSource().sendFeedback(Text.literal("Echt: " + real + " | gefiltert (Fake): " + fake));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("fake").executes(ctx -> {
                            for (Verdict v : FakeBaseFilterApi.all()) if (v.isFake()) msg(ctx.getSource().getClient(), v, false);
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("clear").executes(ctx -> {
                            FakeBaseFilterApi.clear();
                            ctx.getSource().sendFeedback(Text.literal("Zurueckgesetzt."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("chat").executes(ctx -> {
                            Config.chat = !Config.chat;
                            ctx.getSource().sendFeedback(Text.literal("Chat-Meldungen: " + Config.chat));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("float")
                                .then(ClientCommandManager.argument("n", IntegerArgumentType.integer(1, 50)).executes(ctx -> {
                                    Config.floatMin = IntegerArgumentType.getInteger(ctx, "n");
                                    ctx.getSource().sendFeedback(Text.literal("floatMin = " + Config.floatMin));
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("cave")
                                .then(ClientCommandManager.argument("n", IntegerArgumentType.integer(1, 343)).executes(ctx -> {
                                    Config.caveAirMin = IntegerArgumentType.getInteger(ctx, "n");
                                    ctx.getSource().sendFeedback(Text.literal("caveAirMin = " + Config.caveAirMin));
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("ratio")
                                .then(ClientCommandManager.argument("r", DoubleArgumentType.doubleArg(0.1, 1.0)).executes(ctx -> {
                                    Config.caveRatio = DoubleArgumentType.getDouble(ctx, "r");
                                    ctx.getSource().sendFeedback(Text.literal("caveRatio = " + Config.caveRatio));
                                    return 1;
                                })))));
    }

    private static void report(MinecraftClient mc, Verdict v) {
        if (v.isRealCandidate()) msg(mc, v, true);
        // Fakes werden bewusst NICHT im Chat gemeldet (gefiltert); sichtbar mit /fbf fake
    }

    private static void msg(MinecraftClient mc, Verdict v, boolean real) {
        if (mc.player == null) return;
        String where = v.sampleX() + " " + v.sampleY() + " " + v.sampleZ();
        Text t = real
                ? Text.literal("[FBF] Echte Base? ").formatted(Formatting.GREEN)
                    .append(Text.literal(where + " (" + v.containers() + " Container)").formatted(Formatting.WHITE))
                : Text.literal("[FBF] FAKE ").formatted(Formatting.RED)
                    .append(Text.literal(where + " - " + String.join(", ", v.reasons())).formatted(Formatting.GRAY));
        mc.player.sendMessage(t, false);
    }
}
