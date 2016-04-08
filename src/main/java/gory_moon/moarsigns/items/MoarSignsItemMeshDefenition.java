package gory_moon.moarsigns.items;

import gory_moon.moarsigns.api.SignInfo;
import net.minecraft.client.renderer.ItemMeshDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.ItemStack;

public class MoarSignsItemMeshDefenition implements ItemMeshDefinition {
    @Override
    public ModelResourceLocation getModelLocation(ItemStack stack) {
        SignInfo info = ItemMoarSign.getInfo(stack.getTagCompound());
        if (info == null) return null;
        String resourcePath = info.material.path.replace("/", "_") + info.itemName;
        return new ModelResourceLocation(info.modId + ":" + "signs/" + resourcePath, "inventory");
    }
}
