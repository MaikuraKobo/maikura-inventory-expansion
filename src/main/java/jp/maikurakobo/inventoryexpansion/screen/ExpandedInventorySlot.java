package jp.maikurakobo.inventoryexpansion.screen;

import jp.maikurakobo.inventoryexpansion.storage.ExpandedInventoryStorage;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

/**
 * 追加インベントリ専用スロット。
 *
 * 通常クリック/ドラッグ/Shift移動のどの経路でも、
 * ログイン不能になりやすい危険なItemStackを入れないための保護。
 */
public class ExpandedInventorySlot extends Slot {
    public ExpandedInventorySlot(Inventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean canInsert(ItemStack stack) {
        return ExpandedInventoryStorage.canStoreInExpandedInventory(stack);
    }

    @Override
    public int getMaxItemCount() {
        ItemStack current = this.getStack();
        int safeMax = ExpandedInventoryStorage.safeMaxCountForExpandedInventory(current);
        return safeMax > 0 ? safeMax : super.getMaxItemCount();
    }

    @Override
    public int getMaxItemCount(ItemStack stack) {
        int safeMax = ExpandedInventoryStorage.safeMaxCountForExpandedInventory(stack);
        return safeMax > 0 ? safeMax : super.getMaxItemCount(stack);
    }

    @Override
    public void setStack(ItemStack stack) {
        super.setStack(ExpandedInventoryStorage.sanitizeStack(stack));
    }
}
