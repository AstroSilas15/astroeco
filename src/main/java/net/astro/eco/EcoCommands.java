package net.astro.eco;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EcoCommands {
    private EcoCommands() {}

    /** Admins: Spieler mit dem Tag "eco_admin" (oder die Konsole). */
    private static boolean isAdmin(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        return p == null || p.getTags().contains("eco_admin");
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        // /bal [Spieler]  (auch /balance, /money)
        LiteralCommandNode<CommandSourceStack> bal = d.register(
            Commands.literal("bal")
                .executes(ctx -> showBalance(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> showBalance(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));
        d.register(Commands.literal("balance").redirect(bal));
        d.register(Commands.literal("money").redirect(bal));

        // /pay <Spieler> <Betrag>
        d.register(Commands.literal("pay")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                    .executes(ctx -> pay(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                        LongArgumentType.getLong(ctx, "amount"))))));

        // /baltop
        d.register(Commands.literal("baltop").executes(ctx -> baltop(ctx.getSource())));

        // /shop und /sell und /worth
        d.register(Commands.literal("shop").executes(ctx -> {
            GuiMenus.openMain(ctx.getSource().getPlayerOrException());
            return 1;
        }));
        d.register(Commands.literal("sell").executes(ctx -> {
            GuiMenus.openSell(ctx.getSource().getPlayerOrException());
            return 1;
        }));
        d.register(Commands.literal("worth").executes(ctx -> worth(ctx.getSource())));

        // /eco give|take|set <Spieler> <Betrag>   (nur Admins)
        d.register(Commands.literal("eco").requires(EcoCommands::isAdmin)
            .then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                    .executes(ctx -> admin(ctx.getSource(), "give", EntityArgument.getPlayer(ctx, "player"),
                        LongArgumentType.getLong(ctx, "amount"))))))
            .then(Commands.literal("take").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                    .executes(ctx -> admin(ctx.getSource(), "take", EntityArgument.getPlayer(ctx, "player"),
                        LongArgumentType.getLong(ctx, "amount"))))))
            .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                    .executes(ctx -> admin(ctx.getSource(), "set", EntityArgument.getPlayer(ctx, "player"),
                        LongArgumentType.getLong(ctx, "amount")))))));
    }

    private static int showBalance(CommandSourceStack src, ServerPlayer target) {
        long money = AstroEconomy.ECONOMY.get(target.getUUID());
        src.sendSuccess(() -> Component.literal("Kontostand von " + target.getGameProfile().name() + ": ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal("$" + Money.format(money)).withStyle(ChatFormatting.GREEN)), false);
        return 1;
    }

    private static int pay(CommandSourceStack src, ServerPlayer target, long amount) throws CommandSyntaxException {
        ServerPlayer payer = src.getPlayerOrException();
        Economy eco = AstroEconomy.ECONOMY;
        if (payer.getUUID().equals(target.getUUID())) {
            src.sendFailure(Component.literal("Du kannst dir nicht selbst Geld senden."));
            return 0;
        }
        if (eco.get(payer.getUUID()) < amount) {
            src.sendFailure(Component.literal("Du hast nicht genug Geld."));
            return 0;
        }
        eco.add(payer.getUUID(), -amount);
        eco.add(target.getUUID(), amount);
        String shown = "$" + Money.format(amount);
        payer.sendSystemMessage(Component.literal("Du hast " + shown + " an " + target.getGameProfile().name() + " gesendet.")
            .withStyle(ChatFormatting.GREEN));
        target.sendSystemMessage(Component.literal("Du hast " + shown + " von " + payer.getGameProfile().name() + " erhalten.")
            .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int baltop(CommandSourceStack src) {
        List<Map.Entry<UUID, Long>> top = AstroEconomy.ECONOMY.top(10);
        src.sendSuccess(() -> Component.literal("=== Reichste Spieler ===").withStyle(ChatFormatting.GOLD), false);
        int rank = 1;
        for (Map.Entry<UUID, Long> e : top) {
            final int r = rank++;
            final String name = AstroEconomy.ECONOMY.name(e.getKey());
            final long value = e.getValue();
            src.sendSuccess(() -> Component.literal(r + ". " + name + "  ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("$" + Money.format(value)).withStyle(ChatFormatting.GREEN)), false);
        }
        return 1;
    }

    private static int worth(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ItemStack hand = p.getMainHandItem();
        long unit = AstroEconomy.PRICES.unitValue(hand);
        if (unit <= 0) {
            src.sendFailure(Component.literal("Dieses Item hat keinen Wert."));
            return 0;
        }
        long total = unit * hand.getCount();
        src.sendSuccess(() -> Component.literal("Wert: $" + unit + " pro Stueck, Stapel: $" + Money.format(total))
            .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int admin(CommandSourceStack src, String mode, ServerPlayer target, long amount) {
        Economy eco = AstroEconomy.ECONOMY;
        UUID id = target.getUUID();
        switch (mode) {
            case "give" -> eco.add(id, amount);
            case "take" -> eco.add(id, -amount);
            default -> eco.set(id, amount);
        }
        src.sendSuccess(() -> Component.literal("Kontostand von " + target.getGameProfile().name() + " ist jetzt $"
            + Money.format(eco.get(id))).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
