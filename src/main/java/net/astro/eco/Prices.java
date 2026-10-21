package net.astro.eco;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Verkaufspreise pro Stueck aus config/astroeco/prices.json. */
public class Prices {
    private static final Gson GSON = new Gson();
    private final Map<String, Long> prices = new HashMap<>();

    public void load(Path file) {
        prices.clear();
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(text, JsonObject.class);
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                prices.put(e.getKey(), e.getValue().getAsLong());
            }
        } catch (Exception ex) {
            System.err.println("[AstroEconomy] Konnte prices.json nicht lesen: " + ex);
        }
    }

    /** Wert eines einzelnen Items (0 = kein Wert). */
    public long unitValue(ItemStack stack) {
        if (stack.isEmpty()) return 0L;
        if (stack.isEnchanted() || stack.has(DataComponents.CUSTOM_NAME)) return 0L;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return prices.getOrDefault(id, 0L);
    }

    /** Wert des ganzen Stapels. */
    public long stackValue(ItemStack stack) {
        return unitValue(stack) * stack.getCount();
    }
}
