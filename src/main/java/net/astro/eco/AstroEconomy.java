package net.astro.eco;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AstroEconomy implements ModInitializer {
    public static Economy ECONOMY;
    public static Prices PRICES = new Prices();
    public static long START_MONEY = 1000L;
    public static boolean ACTIONBAR = true;

    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("astroeco");
            Files.createDirectories(dir);
            copyDefault("/astroeco/config.json", dir.resolve("config.json"));
            copyDefault("/astroeco/prices.json", dir.resolve("prices.json"));

            JsonObject cfg = new Gson().fromJson(Files.readString(dir.resolve("config.json"), StandardCharsets.UTF_8), JsonObject.class);
            if (cfg.has("startMoney")) START_MONEY = cfg.get("startMoney").getAsLong();
            if (cfg.has("actionbar")) ACTIONBAR = cfg.get("actionbar").getAsBoolean();

            PRICES.load(dir.resolve("prices.json"));
            ECONOMY = new Economy(dir.resolve("balances.json"));
            ECONOMY.load();
        } catch (Exception ex) {
            throw new RuntimeException("AstroEconomy konnte nicht starten", ex);
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            EcoCommands.register(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.getPlayer();
            ECONOMY.rememberName(p.getUUID(), p.getGameProfile().name());
            if (!ECONOMY.has(p.getUUID())) {
                ECONOMY.set(p.getUUID(), START_MONEY);
                p.sendSystemMessage(Component.literal("Willkommen! Du startest mit $" + Money.format(START_MONEY) + ". Mit /shop kannst du einkaufen.")
                    .withStyle(ChatFormatting.GREEN));
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(this::onTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ECONOMY.save());
    }

    private void onTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 20 == 0 && ACTIONBAR) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                showActionbar(server, p);
            }
        }
        if (tickCounter % 6000 == 0) {
            ECONOMY.save();
        }
    }

    private void showActionbar(MinecraftServer server, ServerPlayer p) {
        long money = ECONOMY.get(p.getUUID());
        int kills = p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS));
        int deaths = p.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
        Component line = Component.literal("Money: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("$" + Money.format(money)).withStyle(ChatFormatting.GREEN))
            .append(Component.literal("   Kills ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(Integer.toString(kills)).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("   Tode ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(Integer.toString(deaths)).withStyle(ChatFormatting.RED))
            .append(Component.literal("   Online ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(Integer.toString(server.getPlayerCount())).withStyle(ChatFormatting.AQUA));
        p.sendSystemMessage(line, true);
    }

    private static void copyDefault(String resource, Path target) throws Exception {
        if (Files.exists(target)) return;
        try (InputStream in = AstroEconomy.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Fehlende Ressource " + resource);
            Files.write(target, in.readAllBytes());
        }
    }
}
