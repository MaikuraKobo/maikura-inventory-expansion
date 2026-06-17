package jp.maikurakobo.inventoryexpansion.mixin;

import jp.maikurakobo.inventoryexpansion.access.ExpandedInventoryScreenHandlerAccess;
import jp.maikurakobo.inventoryexpansion.config.MaikuraInventoryExpansionConfig;
import jp.maikurakobo.inventoryexpansion.screen.ExpandedInventorySlot;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow @Final protected ScreenHandler handler;

    @Shadow protected abstract void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);

    private boolean maikura_inventory_expansion$quickCraftingExtra = false;
    private boolean maikura_inventory_expansion$rightDragPlacingExtra = false;
    private int maikura_inventory_expansion$quickCraftButton = 0;
    private final Set<Integer> maikura_inventory_expansion$quickCraftSlots = new HashSet<>();


    /**
     * 標準E画面の左側に追加した3x9スロット範囲は、バニラの背景外にある。
     * そのままだと「画面外クリック」として扱われてアイテムを捨ててしまうため、
     * この範囲だけは画面内クリックとして扱う。
     */
    @Inject(method = "isClickOutsideBounds", at = @At("HEAD"), cancellable = true)
    private void maikura_inventory_expansion$allowExtraInventoryClicks(
            double mouseX,
            double mouseY,
            int left,
            int top,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Object self = this;
        if (!(self instanceof InventoryScreen)) {
            return;
        }

        if (maikura_inventory_expansion$isRecipeBookOpen(self)) {
            return;
        }

        if (maikura_inventory_expansion$isInsideExtraPanel(mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 1.21.11のHandledScreenは背景外スロットのクリック検出が不安定なため、
     * 左追加スロット上のクリックは直接Slotクリックへ流す。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void maikura_inventory_expansion$clickExtraInventorySlot(
            Click click,
            boolean doubled,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Object self = this;
        if (!(self instanceof InventoryScreen)) {
            return;
        }
        int button = click.button();
        double mouseX = click.x();
        double mouseY = click.y();

        // レシピ本表示中は左追加スロットを非表示にしているため、
        // 裏側の追加スロットをクリックできないよう追加パネル範囲のクリックを消費する。
        if (maikura_inventory_expansion$isRecipeBookOpen(self)) {
            if (maikura_inventory_expansion$isInsideExtraPanel(mouseX, mouseY)) {
                cir.setReturnValue(true);
            }
            return;
        }
        if (button != 0 && button != 1) {
            return;
        }
        if (!maikura_inventory_expansion$isInsideExtraPanel(mouseX, mouseY)) {
            return;
        }

        Slot slot = maikura_inventory_expansion$getExtraSlotAt(mouseX, mouseY);
        if (slot == null) {
            cir.setReturnValue(true);
            return;
        }

        // 追加パネルはバニラ背景の外側にあるため、HandledScreenの標準スロット検出に任せると
        // ドラッグ/ダブルクリック時に画面外ドロップ扱いになることがある。
        // そのため追加スロット上だけ、標準ScreenHandlerへ正しいクリック種別を直接送る。
        if (doubled && button == 0) {
            this.onMouseClick(slot, slot.id, 0, SlotActionType.PICKUP_ALL);
            cir.setReturnValue(true);
            return;
        }

        boolean shiftDown = click.hasShift();
        if (shiftDown) {
            this.onMouseClick(slot, slot.id, 0, SlotActionType.QUICK_MOVE);
            cir.setReturnValue(true);
            return;
        }

        // カーソルにアイテムを持った状態の左クリックは、バニラ同様にPICKUPへ流す。
        // r3までは左クリックもQUICK_CRAFT開始扱いにしていたため、
        // 「追加インベントリ内のアイテムとカーソル上のアイテムを入れ替える」通常操作ができなかった。
        // 右クリックだけは、追加パネル外扱いによるドロップ誤判定を避けるため、従来通り1個配置処理を維持する。
        if (!this.handler.getCursorStack().isEmpty() && button == 1) {
            maikura_inventory_expansion$beginExtraRightDrag();
            maikura_inventory_expansion$placeOneIntoExtraSlot(slot);
            cir.setReturnValue(true);
            return;
        }

        this.onMouseClick(slot, slot.id, button, SlotActionType.PICKUP);
        cir.setReturnValue(true);
    }



    /**
     * 左追加スロット上で通常クリックした後、リリース側が画面外クリック扱いになると
     * カーソル保持中のアイテムがドロップされるため、追加パネル内のリリースは消費する。
     */
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void maikura_inventory_expansion$releaseExtraInventorySlot(
            Click click,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Object self = this;
        if (!(self instanceof InventoryScreen)) {
            return;
        }
        double mouseX = click.x();
        double mouseY = click.y();
        if (maikura_inventory_expansion$isRecipeBookOpen(self)) {
            if (maikura_inventory_expansion$isInsideExtraPanel(mouseX, mouseY)) {
                cir.setReturnValue(true);
            }
            return;
        }
        if (this.maikura_inventory_expansion$rightDragPlacingExtra) {
            Slot slot = maikura_inventory_expansion$getExtraSlotAt(mouseX, mouseY);
            if (slot != null) {
                maikura_inventory_expansion$placeOneIntoExtraSlot(slot);
            }
            maikura_inventory_expansion$endExtraRightDrag();
            cir.setReturnValue(true);
            return;
        }
        if (this.maikura_inventory_expansion$quickCraftingExtra) {
            Slot slot = maikura_inventory_expansion$getExtraSlotAt(mouseX, mouseY);
            if (slot != null) {
                maikura_inventory_expansion$addExtraQuickCraftSlot(slot);
            }
            maikura_inventory_expansion$endExtraQuickCraft();
            cir.setReturnValue(true);
            return;
        }
        if (maikura_inventory_expansion$isInsideExtraPanel(mouseX, mouseY)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 追加インベントリ上のドラッグ通過スロットをQUICK_CRAFTへ登録する。
     */
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void maikura_inventory_expansion$dragExtraInventorySlot(
            Click click,
            double horizontalAmount,
            double verticalAmount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Object self = this;
        if (!(self instanceof InventoryScreen)) {
            return;
        }
        double mouseX = click.x();
        double mouseY = click.y();
        if (maikura_inventory_expansion$isRecipeBookOpen(self)) {
            if (maikura_inventory_expansion$isInsideExtraPanel(mouseX, mouseY)) {
                cir.setReturnValue(true);
            }
            return;
        }
        if (this.maikura_inventory_expansion$rightDragPlacingExtra) {
            Slot slot = maikura_inventory_expansion$getExtraSlotAt(mouseX, mouseY);
            if (slot != null) {
                maikura_inventory_expansion$placeOneIntoExtraSlot(slot);
                cir.setReturnValue(true);
                return;
            }
            if (maikura_inventory_expansion$isInsideExtraPanel(mouseX, mouseY)) {
                cir.setReturnValue(true);
            }
            return;
        }
        if (!this.maikura_inventory_expansion$quickCraftingExtra) {
            return;
        }
        Slot slot = maikura_inventory_expansion$getExtraSlotAt(mouseX, mouseY);
        if (slot != null) {
            maikura_inventory_expansion$addExtraQuickCraftSlot(slot);
            cir.setReturnValue(true);
            return;
        }
        if (maikura_inventory_expansion$isInsideExtraPanel(mouseX, mouseY)) {
            cir.setReturnValue(true);
        }
    }

    private void maikura_inventory_expansion$beginExtraRightDrag() {
        this.maikura_inventory_expansion$rightDragPlacingExtra = true;
        this.maikura_inventory_expansion$quickCraftButton = 1;
        this.maikura_inventory_expansion$quickCraftSlots.clear();
    }

    private void maikura_inventory_expansion$placeOneIntoExtraSlot(Slot slot) {
        if (slot == null || !this.maikura_inventory_expansion$rightDragPlacingExtra) {
            return;
        }
        if (this.maikura_inventory_expansion$quickCraftSlots.add(slot.id)) {
            this.onMouseClick(slot, slot.id, 1, SlotActionType.PICKUP);
        }
    }

    private void maikura_inventory_expansion$endExtraRightDrag() {
        this.maikura_inventory_expansion$rightDragPlacingExtra = false;
        this.maikura_inventory_expansion$quickCraftButton = 0;
        this.maikura_inventory_expansion$quickCraftSlots.clear();
    }

    private void maikura_inventory_expansion$beginExtraQuickCraft(int button) {
        this.maikura_inventory_expansion$quickCraftingExtra = true;
        this.maikura_inventory_expansion$quickCraftButton = button;
        this.maikura_inventory_expansion$quickCraftSlots.clear();
        this.onMouseClick(null, -999, ScreenHandler.packQuickCraftData(0, button), SlotActionType.QUICK_CRAFT);
    }

    private void maikura_inventory_expansion$addExtraQuickCraftSlot(Slot slot) {
        if (slot == null || !this.maikura_inventory_expansion$quickCraftingExtra) {
            return;
        }
        if (this.maikura_inventory_expansion$quickCraftSlots.add(slot.id)) {
            this.onMouseClick(slot, slot.id, ScreenHandler.packQuickCraftData(1, this.maikura_inventory_expansion$quickCraftButton), SlotActionType.QUICK_CRAFT);
        }
    }

    private void maikura_inventory_expansion$endExtraQuickCraft() {
        if (!this.maikura_inventory_expansion$quickCraftingExtra) {
            return;
        }
        this.onMouseClick(null, -999, ScreenHandler.packQuickCraftData(2, this.maikura_inventory_expansion$quickCraftButton), SlotActionType.QUICK_CRAFT);
        this.maikura_inventory_expansion$quickCraftingExtra = false;
        this.maikura_inventory_expansion$rightDragPlacingExtra = false;
        this.maikura_inventory_expansion$quickCraftButton = 0;
        this.maikura_inventory_expansion$quickCraftSlots.clear();
    }

    private static boolean maikura_inventory_expansion$isRecipeBookOpen(Object screen) {
        try {
            Class<?> current = screen.getClass();
            while (current != null) {
                for (Field field : current.getDeclaredFields()) {
                    if (!field.getType().getName().toLowerCase().contains("recipebook")) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object recipeBook = field.get(screen);
                    if (recipeBook == null) {
                        continue;
                    }
                    for (String methodName : new String[] {"isOpen", "isGuiOpen"}) {
                        try {
                            Method method = recipeBook.getClass().getMethod(methodName);
                            Object result = method.invoke(recipeBook);
                            if (result instanceof Boolean) {
                                return (Boolean) result;
                            }
                        } catch (NoSuchMethodException ignored) {
                            // 次の候補へ
                        }
                    }
                }
                current = current.getSuperclass();
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // 状態取得失敗時は通常操作を優先する。
        }
        return false;
    }

    private boolean maikura_inventory_expansion$isInsideExtraPanel(double mouseX, double mouseY) {
        int columns = maikura_inventory_expansion$actualColumns();
        int rows = maikura_inventory_expansion$actualRows();
        if (columns <= 0 || rows <= 0) {
            return false;
        }
        int panelX = this.x + maikura_inventory_expansion$panelOffsetX(columns);
        int panelY = this.y + MaikuraInventoryExpansionConfig.panelOffsetY();
        return mouseX >= panelX && mouseX < panelX + maikura_inventory_expansion$panelWidth(columns)
                && mouseY >= panelY && mouseY < panelY + maikura_inventory_expansion$panelHeight(rows);
    }

    private Slot maikura_inventory_expansion$getExtraSlotAt(double mouseX, double mouseY) {
        // Trinkets等がPlayerScreenHandlerへ追加スロットを差し込む環境では、
        // extraStart～extraCountの連番前提が崩れることがある。
        // クリック判定は型で追加インベントリスロットだけを探す。
        for (Slot slot : this.handler.slots) {
            if (!(slot instanceof ExpandedInventorySlot)) {
                continue;
            }
            int slotX = this.x + slot.x;
            int slotY = this.y + slot.y;
            if (mouseX >= slotX - 1 && mouseX < slotX + 17
                    && mouseY >= slotY - 1 && mouseY < slotY + 17) {
                return slot;
            }
        }
        return null;
    }

    private int maikura_inventory_expansion$actualColumns() {
        if (this.handler instanceof ExpandedInventoryScreenHandlerAccess access) {
            return access.maikura_inventory_expansion$getExtraColumns();
        }
        return MaikuraInventoryExpansionConfig.columns();
    }

    private int maikura_inventory_expansion$actualRows() {
        if (this.handler instanceof ExpandedInventoryScreenHandlerAccess access) {
            return access.maikura_inventory_expansion$getExtraRows();
        }
        return MaikuraInventoryExpansionConfig.rows();
    }

    private static int maikura_inventory_expansion$panelWidth(int columns) {
        return columns * 18 + 8;
    }

    private static int maikura_inventory_expansion$panelHeight(int rows) {
        return rows * 18 + 4;
    }

    private static int maikura_inventory_expansion$panelOffsetX(int columns) {
        return -(maikura_inventory_expansion$panelWidth(columns) + 6);
    }
}
