package jp.maikurakobo.inventoryexpansion.storage;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtSizeTracker;
import com.mojang.serialization.DynamicOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ExpandedInventoryStorage {
    public static final int SIZE = 54;
    private static final Map<String, SavingInventory> CACHE = new ConcurrentHashMap<>();

    private ExpandedInventoryStorage() {
    }

    public static SimpleInventory getInventory(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return new SimpleInventory(SIZE);
        }

        Path worldDir = worldDataDir(serverPlayer);
        String cacheKey = worldDir.toAbsolutePath().normalize() + ":" + serverPlayer.getUuid();
        return CACHE.computeIfAbsent(cacheKey, ignored -> loadInventory(serverPlayer, worldDir));
    }

    private static SavingInventory loadInventory(ServerPlayerEntity player, Path worldDir) {
        Path path = playerFile(worldDir, player.getUuid());
        SavingInventory inventory = new SavingInventory(SIZE, path, player.getRegistryManager());
        if (!Files.exists(path)) {
            return inventory;
        }

        try {
            NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
            if (root == null) {
                return inventory;
            }
            DynamicOps<NbtElement> ops = player.getRegistryManager().getOps(NbtOps.INSTANCE);
            NbtList items = root.getList("Items").orElse(new NbtList());
            for (int i = 0; i < items.size(); i++) {
                NbtCompound entry = items.getCompound(i).orElse(new NbtCompound());
                int slot = entry.getInt("Slot").orElse(-1);
                if (slot >= 0 && slot < SIZE) {
                    NbtElement stackNbt = entry.get("Stack");
                    if (stackNbt != null) {
                        ItemStack.CODEC.parse(ops, stackNbt).result().ifPresent(stack -> {
                            ItemStack safeStack = sanitizeStack(stack);
                            if (!safeStack.isEmpty()) {
                                inventory.setStack(slot, safeStack);
                            }
                        });
                    }
                }
            }

            // 旧データに危険なItemStackが残り続けないよう、読み込み成功時に安全化済みデータで上書きする。
            sanitizeInventory(inventory);
            inventory.markDirty();
        } catch (Exception ignored) {
            // 壊れた保存データで起動不能にしないため、読み込み失敗時は空の拡張インベントリとして扱う。
        }
        return inventory;
    }

    /**
     * 追加インベントリへ入るItemStackの安全化。
     *
     * Stack Expansionなどで本来スタックできないアイテムが複数スタック化している場合、
     * 追加インベントリ内では1個単位に正規化する。
     * エンチャント本・角笛・記入済み本・シュルカーなどの中身データは保持し、countだけを補正する。
     * ItemStackを新規作成するとDataComponentが欠落し、エンチャント内容などが消えるため、
     * 必ずcopy()+setCount()で安全化する。
     */
    public static ItemStack sanitizeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        try {
            int safeMax = safeMaxCountForExpandedInventory(stack);
            if (safeMax <= 0) {
                return ItemStack.EMPTY;
            }

            if (stack.getCount() > safeMax) {
                // DataComponent/NBTを消さないよう、ItemStackは作り直さずcountだけ補正する。
                // 角笛のInstrument、シュルカー内容などを保持する。
                ItemStack fixed = stack.copy();
                fixed.setCount(safeMax);
                return fixed;
            }

            return stack;
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * 画面同期や保存の前に、既存スロットへ残った危険スタックも実データとして正規化する。
     */
    public static void sanitizeInventory(SimpleInventory inventory) {
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack current = inventory.getStack(slot);
            ItemStack safe = sanitizeStack(current);
            if (safe != current) {
                inventory.setStack(slot, safe);
            }
        }
    }


    /**
     * 追加インベントリ内で安全に扱うための最大数。
     *
     * Stack拡張系MODのスタック数拡張は基本的に尊重する。
     * ただし、エンチャント本・角笛・シュルカーボックスなど、
     * DataComponent/NBT由来で同期クラッシュやデータ破損が起きやすい代表アイテムだけは1個単位にする。
     * ポーション系はテストで安全確認済みのため、Stack MOD側のスタック数を尊重する。
     */
    public static int safeMaxCountForExpandedInventory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        if (isSpecialSingleStackItem(stack)) {
            return 1;
        }
        try {
            return Math.max(1, stack.getMaxCount());
        } catch (Exception ignored) {
            try {
                return Math.max(1, stack.getItem().getMaxCount());
            } catch (Exception ignoredAgain) {
                return 1;
            }
        }
    }

    /**
     * バニラでは本来スタック不可で、かつNBT/DataComponentを持ちやすい代表アイテム。
     * 追加インベントリではStack MOD併用時も1個ずつ扱う。
     *
     * ベッド・ボート・バケツ・トロッコ・防具・ツールなどはここに含めず、
     * Stack MOD側の最大スタック数を尊重する。
     */
    public static boolean isSpecialSingleStackItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            Identifier id = Registries.ITEM.getId(stack.getItem());
            String namespace = id.getNamespace();
            String path = id.getPath();

            if (!"minecraft".equals(namespace)) {
                return stack.getItem().getMaxCount() == 1;
            }

            return isGoatHorn(stack)
                    || "enchanted_book".equals(path)
                    || "written_book".equals(path)
                    || "writable_book".equals(path)
                    || "bundle".equals(path)
                    || "filled_map".equals(path)
                    || path.endsWith("shulker_box");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isEnchantedBook(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            Identifier id = Registries.ITEM.getId(stack.getItem());
            return "minecraft".equals(id.getNamespace()) && "enchanted_book".equals(id.getPath());
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isGoatHorn(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            Identifier id = Registries.ITEM.getId(stack.getItem());
            return "minecraft".equals(id.getNamespace()) && "goat_horn".equals(id.getPath());
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean canStoreInExpandedInventory(ItemStack stack) {
        return !sanitizeStack(stack).isEmpty();
    }

    private static Path playerFile(Path worldDir, UUID uuid) {
        return worldDir
                .resolve("data")
                .resolve("maikura_inventory_expansion")
                .resolve("playerdata")
                .resolve(uuid + ".nbt");
    }

    private static Path worldDataDir(ServerPlayerEntity player) {
        // まずMinecraftServer#getSavePath(ROOT)を使う。ここが成功すればシングル/マルチ両方でワールド別になる。
        try {
            Object server = invokeNoArgs(player, "getServer");
            if (server != null) {
                try {
                    Method getSavePath = server.getClass().getMethod("getSavePath", WorldSavePath.class);
                    Object result = getSavePath.invoke(server, WorldSavePath.ROOT);
                    if (result instanceof Path path) {
                        return path.toAbsolutePath().normalize();
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Yarn差分に備えて下のフォールバックへ進む。
                }
            }
        } catch (Exception ignored) {
            // 反射失敗時はフォールバックへ進む。
        }

        // 専用サーバーの標準配置: 実行フォルダ/world/level.dat
        Path dedicatedDefault = Paths.get("world");
        if (Files.exists(dedicatedDefault.resolve("level.dat"))) {
            return dedicatedDefault.toAbsolutePath().normalize();
        }

        // シングルプレイのフォールバック: .minecraft/saves内で直近更新のワールドを使う。
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path savesDir = gameDir.resolve("saves");
        Optional<Path> newestSave = Optional.empty();
        if (Files.isDirectory(savesDir)) {
            try (var stream = Files.list(savesDir)) {
                newestSave = stream
                        .filter(path -> Files.exists(path.resolve("level.dat")))
                        .max(Comparator.comparingLong(ExpandedInventoryStorage::safeLastModified));
            } catch (IOException ignored) {
                // フォールバックへ進む。
            }
        }
        if (newestSave.isPresent()) {
            return newestSave.get().toAbsolutePath().normalize();
        }

        // 最後の保険。ここに来るのは特殊環境のみ。
        return FabricLoader.getInstance().getConfigDir()
                .resolve("maikura_inventory_expansion")
                .resolve("unknown_world")
                .toAbsolutePath()
                .normalize();
    }

    private static Object invokeNoArgs(Object target, String methodName) throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static long safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path.resolve("level.dat")).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static final class SavingInventory extends SimpleInventory {
        private final Path savePath;
        private final RegistryWrapper.WrapperLookup registries;

        private SavingInventory(int size, Path savePath, RegistryWrapper.WrapperLookup registries) {
            super(size);
            this.savePath = savePath;
            this.registries = registries;
        }

        @Override
        public void markDirty() {
            super.markDirty();
            save();
        }

        private void save() {
            try {
                Files.createDirectories(savePath.getParent());
                NbtCompound root = new NbtCompound();
                NbtList items = new NbtList();
                for (int slot = 0; slot < size(); slot++) {
                    final int savedSlot = slot;
                    ItemStack stack = sanitizeStack(getStack(savedSlot));
                    if (!stack.isEmpty()) {
                        NbtCompound entry = new NbtCompound();
                        DynamicOps<NbtElement> ops = registries.getOps(NbtOps.INSTANCE);
                        ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(stackNbt -> {
                            entry.putInt("Slot", savedSlot);
                            entry.put("Stack", stackNbt);
                            items.add(entry);
                        });
                    }
                }
                root.put("Items", items);
                NbtIo.writeCompressed(root, savePath);
            } catch (IOException ignored) {
                // 保存失敗はゲーム進行を止めない。
            }
        }
    }
}
