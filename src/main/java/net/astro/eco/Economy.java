package net.astro.eco;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Speichert die Kontostaende in config/astroeco/balances.json. */
public class Economy {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Path file;
    private boolean dirty = false;

    public Economy(Path file) {
        this.file = file;
    }

    public synchronized void load() {
        balances.clear();
        names.clear();
        if (!Files.exists(file)) return;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(text, JsonObject.class);
            if (root == null) return;
            if (root.has("balances")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("balances").entrySet()) {
                    balances.put(UUID.fromString(e.getKey()), e.getValue().getAsLong());
                }
            }
            if (root.has("names")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("names").entrySet()) {
                    names.put(UUID.fromString(e.getKey()), e.getValue().getAsString());
                }
            }
        } catch (Exception ex) {
            System.err.println("[AstroEconomy] Konnte balances.json nicht lesen: " + ex);
        }
    }

    public synchronized void save() {
        if (!dirty) return;
        try {
            JsonObject root = new JsonObject();
            JsonObject b = new JsonObject();
            for (Map.Entry<UUID, Long> e : balances.entrySet()) b.addProperty(e.getKey().toString(), e.getValue());
            JsonObject n = new JsonObject();
            for (Map.Entry<UUID, String> e : names.entrySet()) n.addProperty(e.getKey().toString(), e.getValue());
            root.add("balances", b);
            root.add("names", n);
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException ex) {
            System.err.println("[AstroEconomy] Konnte balances.json nicht speichern: " + ex);
        }
    }

    public synchronized boolean has(UUID id) {
        return balances.containsKey(id);
    }

    public synchronized long get(UUID id) {
        return balances.getOrDefault(id, 0L);
    }

    public synchronized void set(UUID id, long value) {
        balances.put(id, Math.max(0L, value));
        dirty = true;
    }

    public synchronized void add(UUID id, long delta) {
        set(id, get(id) + delta);
    }

    public synchronized void rememberName(UUID id, String name) {
        if (!name.equals(names.get(id))) {
            names.put(id, name);
            dirty = true;
        }
    }

    public synchronized String name(UUID id) {
        return names.getOrDefault(id, id.toString().substring(0, 8));
    }

    public synchronized List<Map.Entry<UUID, Long>> top(int limit) {
        List<Map.Entry<UUID, Long>> list = new ArrayList<>(balances.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }
}
