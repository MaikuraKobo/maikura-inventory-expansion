package jp.maikurakobo.inventoryexpansion;

import jp.maikurakobo.inventoryexpansion.config.MaikuraInventoryExpansionConfig;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

public class MaikuraInventoryExpansion implements ModInitializer {
    public static final String MOD_ID = "maikura_inventory_expansion";
    public static final Identifier EXPANDED_INVENTORY_ID = Identifier.of(MOD_ID, "expanded_inventory");

    @Override
    public void onInitialize() {
        MaikuraInventoryExpansionConfig.load();
    }
}
