package jp.maikurakobo.inventoryexpansion.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class MaikuraInventoryExpansionConfigScreen extends Screen {
    private final Screen parent;
    private ButtonWidget columnsButton;
    private ButtonWidget rowsButton;

    public MaikuraInventoryExpansionConfigScreen(Screen parent) {
        super(Text.translatable("config.maikura_inventory_expansion.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        MaikuraInventoryExpansionConfig.load();
        int centerX = this.width / 2;
        int startY = this.height / 2 - 45;

        this.columnsButton = this.addDrawableChild(ButtonWidget.builder(columnsText(), button -> {
            int next = MaikuraInventoryExpansionConfig.columns() + 1;
            if (next > MaikuraInventoryExpansionConfig.MAX_COLUMNS) {
                next = MaikuraInventoryExpansionConfig.MIN_COLUMNS;
            }
            MaikuraInventoryExpansionConfig.setColumns(next);
            refreshButtons();
        }).dimensions(centerX - 100, startY, 200, 20).build());

        this.rowsButton = this.addDrawableChild(ButtonWidget.builder(rowsText(), button -> {
            int next = MaikuraInventoryExpansionConfig.rows() + 1;
            if (next > MaikuraInventoryExpansionConfig.MAX_ROWS) {
                next = MaikuraInventoryExpansionConfig.MIN_ROWS;
            }
            MaikuraInventoryExpansionConfig.setRows(next);
            refreshButtons();
        }).dimensions(centerX - 100, startY + 24, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions(centerX - 100, startY + 62, 200, 20).build());
    }

    private void refreshButtons() {
        if (this.columnsButton != null) {
            this.columnsButton.setMessage(columnsText());
        }
        if (this.rowsButton != null) {
            this.rowsButton.setMessage(rowsText());
        }
    }

    private static Text columnsText() {
        return Text.translatable("config.maikura_inventory_expansion.columns", MaikuraInventoryExpansionConfig.columns());
    }

    private static Text rowsText() {
        return Text.translatable("config.maikura_inventory_expansion.rows", MaikuraInventoryExpansionConfig.rows());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 75, 0xFFFFFF);
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.translatable("config.maikura_inventory_expansion.note"),
                this.width / 2,
                this.height / 2 + 5,
                0xA0A0A0
        );
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.translatable("config.maikura_inventory_expansion.stack_note"),
                this.width / 2,
                this.height / 2 + 18,
                0xA0A0A0
        );
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(this.parent);
    }
}
