package jp.maikurakobo.inventoryexpansion.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MaikuraInventoryExpansionConfig {
    public static final int MIN_COLUMNS = 3;
    public static final int MAX_COLUMNS = 6;
    public static final int MIN_ROWS = 3;
    public static final int MAX_ROWS = 9;
    public static final int MAX_SIZE = MAX_COLUMNS * MAX_ROWS;

    private static final Pattern COLUMNS_PATTERN = Pattern.compile("\\\"columns\\\"\\s*:\\s*(\\d+)");
    private static final Pattern ROWS_PATTERN = Pattern.compile("\\\"rows\\\"\\s*:\\s*(\\d+)");

    private static int columns = 3;
    private static int rows = 9;

    private MaikuraInventoryExpansionConfig() {
    }

    public static void load() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                saveDefault(path);
                return;
            }

            String json = Files.readString(path, StandardCharsets.UTF_8);
            columns = clamp(readInt(json, COLUMNS_PATTERN, columns), MIN_COLUMNS, MAX_COLUMNS);
            rows = clamp(readInt(json, ROWS_PATTERN, rows), MIN_ROWS, MAX_ROWS);

            // 範囲外の値が入っていた場合は補正後の値を書き戻す。
            save(path);
        } catch (IOException ignored) {
            // 設定読み込みに失敗してもゲーム起動を止めない。
            columns = 3;
            rows = 9;
        }
    }

    public static int columns() {
        return columns;
    }

    public static int rows() {
        return rows;
    }

    public static void setColumns(int value) {
        columns = clamp(value, MIN_COLUMNS, MAX_COLUMNS);
        saveQuietly();
    }

    public static void setRows(int value) {
        rows = clamp(value, MIN_ROWS, MAX_ROWS);
        saveQuietly();
    }

    public static int visibleSize() {
        return columns * rows;
    }

    public static int panelWidth() {
        return columns * 18 + 8;
    }

    public static int panelHeight() {
        return rows * 18 + 4;
    }

    public static int panelOffsetX() {
        return -(panelWidth() + 6);
    }

    public static int panelOffsetY() {
        return 0;
    }

    public static int slotsOffsetX() {
        return panelOffsetX() + 6;
    }

    public static int slotsOffsetY() {
        return 4;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("maikura_inventory_expansion")
                .resolve("config.json");
    }

    private static int readInt(String json, Pattern pattern, int fallback) {
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void saveDefault(Path path) throws IOException {
        columns = 3;
        rows = 9;
        save(path);
    }

    private static void saveQuietly() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            save(path);
        } catch (IOException ignored) {
            // 設定保存に失敗してもゲーム操作を止めない。
        }
    }

    private static void save(Path path) throws IOException {
        String json = """
                {
                  \"columns\": %d,
                  \"rows\": %d
                }
                """.formatted(columns, rows);
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }
}
