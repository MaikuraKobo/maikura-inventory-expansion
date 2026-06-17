package jp.maikurakobo.inventoryexpansion.mixin;

import jp.maikurakobo.inventoryexpansion.access.ExpandedInventoryScreenHandlerAccess;
import jp.maikurakobo.inventoryexpansion.config.MaikuraInventoryExpansionConfig;
import jp.maikurakobo.inventoryexpansion.storage.ExpandedInventoryStorage;
import jp.maikurakobo.inventoryexpansion.screen.ExpandedInventorySlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlayerScreenHandler.class, priority = 100)
public abstract class PlayerScreenHandlerMixin extends ScreenHandler implements ExpandedInventoryScreenHandlerAccess {
    private static final int VANILLA_INVENTORY_START = 9;
    private static final int VANILLA_OFFHAND_SLOT = 45;
    private int maikura_inventory_expansion$extraStart = -1;
    private int maikura_inventory_expansion$extraCount = 0;
    private static final int MAIKURA_BASE_COLUMNS = 6;
    private static final int MAIKURA_BASE_ROWS = 9;
    private int maikura_inventory_expansion$extraColumns = 3;
    private int maikura_inventory_expansion$extraRows = 9;

    private PlayerScreenHandlerMixin() {
        super(null, 0);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void maikura_inventory_expansion$addExpandedSlots(PlayerInventory playerInventory, boolean onServer, PlayerEntity owner, CallbackInfo ci) {
        // Mod Menu changes are saved to config on the client. The player inventory screen handler
        // is created on the logical server side, so reload the file here to avoid stale fixed sizes.
        MaikuraInventoryExpansionConfig.load();

        Inventory expandedInventory = ExpandedInventoryStorage.getInventory(owner);
        if (expandedInventory instanceof net.minecraft.inventory.SimpleInventory simpleInventory) {
            ExpandedInventoryStorage.sanitizeInventory(simpleInventory);
        }
        this.maikura_inventory_expansion$extraStart = this.slots.size();

        int columns = MaikuraInventoryExpansionConfig.columns();
        int rows = MaikuraInventoryExpansionConfig.rows();
        // ScreenHandler上の追加スロット数は常に6x9=54で固定する。
        // クライアント/サーバーの設定反映タイミングやTrinkets等の後付けスロットが絡んでも、
        // 「見た目だけ存在するスロット」や「保存されないスロット」が出ないようにする。
        this.maikura_inventory_expansion$extraCount = MAIKURA_BASE_COLUMNS * MAIKURA_BASE_ROWS;
        this.maikura_inventory_expansion$extraColumns = columns;
        this.maikura_inventory_expansion$extraRows = rows;
        int startX = maikura_inventory_expansion$slotsOffsetX(columns);
        int startY = MaikuraInventoryExpansionConfig.slotsOffsetY();

        // 内部インベントリは常に6x9固定。
        // 表示は右上基準で切り抜き、非表示スロットは画面外へ置く。
        // 例: 3列表示なら各行の4,5,6列目だけを表示し、1,2,3列目は保持だけする。
        int visibleColumns = Math.min(columns, MAIKURA_BASE_COLUMNS);
        int visibleRows = Math.min(rows, MAIKURA_BASE_ROWS);
        int hiddenColumns = MAIKURA_BASE_COLUMNS - visibleColumns;
        for (int row = 0; row < MAIKURA_BASE_ROWS; row++) {
            for (int column = 0; column < MAIKURA_BASE_COLUMNS; column++) {
                int index = column + row * MAIKURA_BASE_COLUMNS;
                boolean visible = row < visibleRows && column >= hiddenColumns;
                int x = visible ? startX + (column - hiddenColumns) * 18 : -10000;
                int y = visible ? startY + row * 18 : -10000;
                this.addSlot(new ExpandedInventorySlot(expandedInventory, index, x, y));
            }
        }
    }

    @Inject(method = "quickMove", at = @At("HEAD"), cancellable = true)
    private void maikura_inventory_expansion$quickMove(PlayerEntity player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        int extraStart = this.maikura_inventory_expansion$extraStart;
        int extraCount = this.maikura_inventory_expansion$extraCount;
        if (extraStart < 0 || extraCount <= 0) {
            return;
        }

        int extraEnd = extraStart + extraCount;
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            return;
        }

        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasStack()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        boolean fromExtra = slot instanceof ExpandedInventorySlot;
        boolean fromPlayerInventory = slotIndex >= VANILLA_INVENTORY_START && slotIndex < VANILLA_OFFHAND_SLOT;
        boolean fromEquipmentOrOffhand = (slotIndex >= 5 && slotIndex < 9) || slotIndex == VANILLA_OFFHAND_SLOT;
        if (!fromExtra && !fromPlayerInventory && !fromEquipmentOrOffhand) {
            return;
        }

        ItemStack originalStack = slot.getStack();
        ItemStack movedStack = originalStack.copy();

        if (fromEquipmentOrOffhand) {
            if (!this.insertItem(originalStack, VANILLA_INVENTORY_START, VANILLA_OFFHAND_SLOT, true)) {
                if (!ExpandedInventoryStorage.canStoreInExpandedInventory(originalStack)) {
                    cir.setReturnValue(ItemStack.EMPTY);
                    return;
                }
                if (!maikura_inventory_expansion$insertItemIntoVisibleExtraSlots(originalStack)) {
                    cir.setReturnValue(ItemStack.EMPTY);
                    return;
                }
            }
        } else if (fromExtra) {
            // 追加インベントリからShiftクリックした場合も、装備品はまず対応する装備枠へ送る。
            // その後は通常の希望順どおり、ホットバー → 通常インベントリの順で戻す。
            this.insertItem(originalStack, 5, 9, false);

            if (!originalStack.isEmpty()) {
                this.insertItem(originalStack, 36, 45, false);
            }
            if (!originalStack.isEmpty()) {
                this.insertItem(originalStack, 9, 36, false);
            }

            if (!originalStack.isEmpty()) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
        } else {
            int beforeCount = originalStack.getCount();

            this.insertItem(originalStack, 5, 9, false);

            if (!originalStack.isEmpty()) {
                if (slotIndex >= 9 && slotIndex < 36) {
                    this.insertItem(originalStack, 36, 45, false);
                } else if (slotIndex >= 36 && slotIndex < 45) {
                    this.insertItem(originalStack, 9, 36, false);
                }
            }

            if (!originalStack.isEmpty() && originalStack.getCount() == beforeCount) {
                if (!ExpandedInventoryStorage.canStoreInExpandedInventory(originalStack)) {
                    cir.setReturnValue(ItemStack.EMPTY);
                    return;
                }
                if (!maikura_inventory_expansion$insertItemIntoVisibleExtraSlots(originalStack)) {
                    return;
                }
            }
        }

        if (originalStack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }

        cir.setReturnValue(movedStack);
    }


    private boolean maikura_inventory_expansion$insertItemIntoVisibleExtraSlots(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        // Shiftクリック/Shiftドラッグの自動投入先は「表示中の追加スロット」のみに限定する。
        // 内部インベントリは常に6x9=54スロット保持しているが、非表示スロットへ勝手に入ると
        // ユーザーからは消えたように見えるため、ここではhiddenフォールバックを行わない。
        // 対象はExpandedInventorySlot型のみなので、Trinkets等の後付けスロットも巻き込まない。
        return maikura_inventory_expansion$insertItemIntoExtraSlots(stack, false);
    }

    private boolean maikura_inventory_expansion$insertItemIntoExtraSlots(ItemStack stack, boolean includeHidden) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        // Shiftクリック/Shiftドラッグの投入先は、画面に見えている追加スロットを
        // 「見た目の左上 → 右下」順で走査する。
        // this.slots の登録順やTrinkets等の後付けスロット順に依存すると、
        // 6x9表示時に右側だけへ偏ることがあるため、座標ベースで並べ直す。
        List<Slot> targets = maikura_inventory_expansion$getExtraInsertTargets(includeHidden);
        boolean changed = false;

        for (Slot target : targets) {
            if (!target.hasStack() || !target.canInsert(stack)) {
                continue;
            }

            ItemStack existing = target.getStack();
            if (!ItemStack.areItemsAndComponentsEqual(existing, stack)) {
                continue;
            }

            int max = Math.min(target.getMaxItemCount(stack), existing.getMaxCount());
            int room = max - existing.getCount();
            if (room <= 0) {
                continue;
            }

            int move = Math.min(room, stack.getCount());
            existing.increment(move);
            stack.decrement(move);
            target.markDirty();
            changed = true;
            if (stack.isEmpty()) {
                return true;
            }
        }

        for (Slot target : targets) {
            if (target.hasStack() || !target.canInsert(stack)) {
                continue;
            }

            int max = Math.min(target.getMaxItemCount(stack), stack.getMaxCount());
            int move = Math.min(max, stack.getCount());
            if (move <= 0) {
                continue;
            }

            ItemStack inserted = stack.copy();
            inserted.setCount(move);
            target.setStack(inserted);
            stack.decrement(move);
            target.markDirty();
            changed = true;
            if (stack.isEmpty()) {
                return true;
            }
        }

        return changed;
    }

    private List<Slot> maikura_inventory_expansion$getExtraInsertTargets(boolean includeHidden) {
        List<Slot> targets = new ArrayList<>();
        for (Slot slot : this.slots) {
            if (!(slot instanceof ExpandedInventorySlot)) {
                continue;
            }
            if (!includeHidden && maikura_inventory_expansion$isHiddenExtraSlot(slot)) {
                continue;
            }
            targets.add(slot);
        }

        // Shift移動の投入順は、見た目の座標順ではなく追加インベントリ内部のスロット番号順にする。
        // 6x9表示時に右側だけへ偏らず、左上から自然に埋まるようにする。
        targets.sort(Comparator
                .comparingInt((Slot slot) -> slot.getIndex())
                .thenComparingInt(slot -> slot.id));
        return targets;
    }

    private static boolean maikura_inventory_expansion$isHiddenExtraSlot(Slot slot) {
        return slot.x < -1000 || slot.y < -1000;
    }

    private static int maikura_inventory_expansion$panelWidth(int columns) {
        return columns * 18 + 8;
    }

    private static int maikura_inventory_expansion$panelOffsetX(int columns) {
        return -(maikura_inventory_expansion$panelWidth(columns) + 6);
    }

    private static int maikura_inventory_expansion$slotsOffsetX(int columns) {
        return maikura_inventory_expansion$panelOffsetX(columns) + 6;
    }


    @Override
    public int maikura_inventory_expansion$getExtraStart() {
        return this.maikura_inventory_expansion$extraStart;
    }

    @Override
    public int maikura_inventory_expansion$getExtraCount() {
        return this.maikura_inventory_expansion$extraCount;
    }

    @Override
    public int maikura_inventory_expansion$getExtraColumns() {
        return this.maikura_inventory_expansion$extraColumns;
    }

    @Override
    public int maikura_inventory_expansion$getExtraRows() {
        return this.maikura_inventory_expansion$extraRows;
    }
}


// Maikura Inventory Expansion: expose the actual slot layout captured when this handler was created.