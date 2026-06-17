package jp.maikurakobo.inventoryexpansion.mixin;

import jp.maikurakobo.inventoryexpansion.access.ExpandedInventoryScreenHandlerAccess;
import jp.maikurakobo.inventoryexpansion.config.MaikuraInventoryExpansionConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends HandledScreen<PlayerScreenHandler> {
    private InventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "drawBackground", at = @At("TAIL"))
    private void maikura_inventory_expansion$drawExpandedSlots(DrawContext context, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        if (maikura_inventory_expansion$isRecipeBookOpen(this)) {
            return;
        }

        int columns = maikura_inventory_expansion$actualColumns();
        int rows = maikura_inventory_expansion$actualRows();
        if (columns <= 0 || rows <= 0) {
            return;
        }

        int panelWidth = maikura_inventory_expansion$panelWidth(columns);
        int panelHeight = maikura_inventory_expansion$panelHeight(rows);
        int panelX = this.x + maikura_inventory_expansion$panelOffsetX(columns);
        int panelY = this.y + MaikuraInventoryExpansionConfig.panelOffsetY();

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFFC6C6C6);
        context.fill(panelX + 3, panelY + 3, panelX + panelWidth - 3, panelY + panelHeight - 3, 0xFFE0E0E0);
        drawSlotGrid(
                context,
                this.x + maikura_inventory_expansion$slotsOffsetX(columns),
                this.y + MaikuraInventoryExpansionConfig.slotsOffsetY(),
                columns,
                rows
        );
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

    private static int maikura_inventory_expansion$slotsOffsetX(int columns) {
        return maikura_inventory_expansion$panelOffsetX(columns) + 6;
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
                        }
                    }
                }
                current = current.getSuperclass();
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
        }
        return false;
    }

    private static void drawSlotGrid(DrawContext context, int startX, int startY, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int sx = startX + column * 18;
                int sy = startY + row * 18;
                context.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF555555);
                context.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
                context.fill(sx + 1, sy + 1, sx + 16, sy + 16, 0xFFB8B8B8);
                context.fill(sx + 1, sy + 1, sx + 15, sy + 15, 0xFF9E9E9E);
            }
        }
    }
}
