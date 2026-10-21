package net.astro.eco;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.function.BiConsumer;

/** Alle Fenster (Chest-Menues) fuer Shop und Verkauf. Funktioniert auch fuer Bedrock ueber Geyser. */
public final class GuiMenus {
    private GuiMenus() {}

    private record Offer(Item item, int count, long price, String label) {}

    private static final Offer[] PVP = {
        new Offer(Items.TOTEM_OF_UNDYING, 1, 500, "Totem der Unsterblichkeit"),
        new Offer(Items.GOLDEN_APPLE, 1, 100, "Goldener Apfel"),
        new Offer(Items.ENDER_PEARL, 8, 150, "Enderperlen x8"),
        new Offer(Items.ARROW, 32, 40, "Pfeile x32"),
    };
    private static final Offer[] FOOD = {
        new Offer(Items.BREAD, 16, 20, "Brot x16"),
        new Offer(Items.COOKED_BEEF, 16, 50, "Gebratenes Steak x16"),
        new Offer(Items.BAKED_POTATO, 16, 25, "Ofenkartoffel x16"),
        new Offer(Items.GOLDEN_CARROT, 8, 60, "Goldene Karotte x8"),
    };
    private static final Offer[] NETHER = {
        new Offer(Items.NETHERRACK, 64, 30, "Netherrack x64"),
        new Offer(Items.NETHER_BRICKS, 64, 80, "Netherziegel x64"),
        new Offer(Items.SOUL_SAND, 32, 60, "Seelensand x32"),
        new Offer(Items.GLOWSTONE, 16, 80, "Glowstone x16"),
    };

    // ---------------------------------------------------------------- Hilfsfunktionen
    private static MutableComponent plain(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(s -> s.withItalic(false).withColor(color));
    }

    private static ItemStack icon(Item item, int count, String name, ChatFormatting color, String... lore) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, plain(name, color));
        if (lore.length > 0) {
            List<Component> lines = new java.util.ArrayList<>();
            for (String l : lore) lines.add(plain(l, ChatFormatting.GRAY));
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        return stack;
    }

    private static ItemStack filler() {
        Item pane = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", "gray_stained_glass_pane"));
        if (pane == Items.AIR) pane = Items.ARROW;
        ItemStack s = new ItemStack(pane);
        s.set(DataComponents.CUSTOM_NAME, plain(" ", ChatFormatting.GRAY));
        return s;
    }

    /** Gibt dem Spieler das Item; was nicht ins Inventar passt, faellt vor ihm auf den Boden. */
    static void giveOrDrop(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (player.getInventory().add(stack)) return;
        if (stack.isEmpty()) return;
        ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3, player.getZ(), stack);
        drop.setPickUpDelay(10);
        player.level().addFreshEntity(drop);
    }

    private static void msg(ServerPlayer p, String text, ChatFormatting color) {
        p.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    // ---------------------------------------------------------------- Fenster ohne Entnahme (nur Klicks)
    private static final class GuiMenu extends ChestMenu {
        private final int rows;
        private final BiConsumer<Integer, ServerPlayer> onClick;

        GuiMenu(int id, Inventory inv, Container container, int rows, BiConsumer<Integer, ServerPlayer> onClick) {
            super(rows == 3 ? MenuType.GENERIC_9x3 : MenuType.GENERIC_9x6, id, inv, container, rows);
            this.rows = rows;
            this.onClick = onClick;
        }

        @Override
        public void clicked(int slotId, int button, ContainerInput input, Player player) {
            if (slotId >= 0 && slotId < rows * 9 && player instanceof ServerPlayer sp) {
                onClick.accept(slotId, sp);
            }
            this.sendAllDataToRemote();
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }
    }

    private static void openGui(ServerPlayer sp, String title, SimpleContainer box, int rows, BiConsumer<Integer, ServerPlayer> onClick) {
        sp.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new GuiMenu(id, inv, box, rows, onClick),
            Component.literal(title)));
    }

    // ---------------------------------------------------------------- Hauptmenue
    public static void openMain(ServerPlayer sp) {
        SimpleContainer box = new SimpleContainer(27);
        for (int i = 0; i < 27; i++) box.setItem(i, filler());
        box.setItem(11, icon(Items.TOTEM_OF_UNDYING, 1, "PvP", ChatFormatting.RED, "Klicke zum Oeffnen"));
        box.setItem(13, icon(Items.BREAD, 1, "Essen", ChatFormatting.GOLD, "Klicke zum Oeffnen"));
        box.setItem(15, icon(Items.NETHERRACK, 1, "Nether", ChatFormatting.DARK_RED, "Klicke zum Oeffnen"));
        box.setItem(22, icon(Items.EMERALD, 1, "Verkaufen", ChatFormatting.GREEN, "Oeffnet das Verkaufsfenster"));
        openGui(sp, "Shop", box, 3, (slot, player) -> {
            switch (slot) {
                case 11 -> openCategory(player, "Shop: PvP", PVP);
                case 13 -> openCategory(player, "Shop: Essen", FOOD);
                case 15 -> openCategory(player, "Shop: Nether", NETHER);
                case 22 -> openSell(player);
                default -> { }
            }
        });
    }

    private static final int[] OFFER_SLOTS = {10, 12, 14, 16};

    private static void openCategory(ServerPlayer sp, String title, Offer[] offers) {
        SimpleContainer box = new SimpleContainer(27);
        for (int i = 0; i < 27; i++) box.setItem(i, filler());
        for (int i = 0; i < offers.length && i < OFFER_SLOTS.length; i++) {
            Offer o = offers[i];
            box.setItem(OFFER_SLOTS[i], icon(o.item(), Math.min(o.count(), 64), o.label(), ChatFormatting.WHITE,
                "Preis: $" + Money.format(o.price()), "Klicke zum Kaufen"));
        }
        box.setItem(22, icon(Items.ARROW, 1, "Zurueck", ChatFormatting.YELLOW));
        openGui(sp, title, box, 3, (slot, player) -> {
            if (slot == 22) { openMain(player); return; }
            for (int i = 0; i < offers.length && i < OFFER_SLOTS.length; i++) {
                if (OFFER_SLOTS[i] == slot) buy(player, offers[i]);
            }
        });
    }

    private static void buy(ServerPlayer sp, Offer o) {
        Economy eco = AstroEconomy.ECONOMY;
        if (eco.get(sp.getUUID()) < o.price()) {
            msg(sp, "Du hast nicht genug Geld ($" + Money.format(o.price()) + " noetig).", ChatFormatting.RED);
            return;
        }
        eco.add(sp.getUUID(), -o.price());
        ItemStack stack = new ItemStack(o.item(), o.count());
        giveOrDrop(sp, stack);
        msg(sp, "Gekauft: " + o.label() + " fuer $" + Money.format(o.price()) + ".", ChatFormatting.GREEN);
    }

    // ---------------------------------------------------------------- Verkaufsfenster
    private static final int BUTTON = 53;

    private static final class SellMenu extends ChestMenu {
        private final SimpleContainer box;

        SellMenu(int id, Inventory inv, SimpleContainer box) {
            super(MenuType.GENERIC_9x6, id, inv, box, 6);
            this.box = box;
        }

        @Override
        public void clicked(int slotId, int button, ContainerInput input, Player player) {
            if (slotId == BUTTON) {
                if (player instanceof ServerPlayer sp) sell(sp);
                this.sendAllDataToRemote();
                return;
            }
            super.clicked(slotId, button, input, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            Slot slot = this.slots.get(index);
            if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
            ItemStack stack = slot.getItem();
            ItemStack copy = stack.copy();
            if (index < 54) {
                if (!this.moveItemStackTo(stack, 54, this.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(stack, 0, BUTTON, false)) return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
            return copy;
        }

        private void sell(ServerPlayer sp) {
            long total = 0;
            for (int i = 0; i < BUTTON; i++) {
                ItemStack s = box.getItem(i);
                if (s.isEmpty()) continue;
                long v = AstroEconomy.PRICES.stackValue(s);
                if (v > 0) {
                    total += v;
                    box.setItem(i, ItemStack.EMPTY);
                }
            }
            if (total <= 0) {
                msg(sp, "Im Fenster liegt nichts, was einen Wert hat.", ChatFormatting.RED);
                return;
            }
            AstroEconomy.ECONOMY.add(sp.getUUID(), total);
            msg(sp, "Verkauft fuer $" + Money.format(total) + ".", ChatFormatting.GREEN);
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            // alles, was nicht verkauft wurde, geht zurueck an den Spieler
            for (int i = 0; i < BUTTON; i++) {
                ItemStack s = box.removeItemNoUpdate(i);
                giveOrDrop(player, s);
            }
        }
    }

    public static void openSell(ServerPlayer sp) {
        SimpleContainer box = new SimpleContainer(54) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return slot != BUTTON;
            }
        };
        box.setItem(BUTTON, icon(Items.EMERALD, 1, "Alles verkaufen", ChatFormatting.GREEN,
            "Lege Items in das Fenster,", "dann klicke hier.", "Unverkaeufliches bekommst du zurueck."));
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new SellMenu(id, inv, box), Component.literal("Verkaufen")));
    }
}
