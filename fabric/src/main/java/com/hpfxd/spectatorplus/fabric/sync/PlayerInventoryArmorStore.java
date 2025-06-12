package com.hpfxd.spectatorplus.fabric.sync;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;

public class PlayerInventoryArmorStore {
    private final NonNullList<ItemStack> inventoryItems;
    private final NonNullList<ItemStack> armorItems;

    public PlayerInventoryArmorStore(NonNullList<ItemStack> inventoryItems, NonNullList<ItemStack> armorItems) {
        this.inventoryItems = NonNullList.withSize(inventoryItems.size(), ItemStack.EMPTY);
        for (int i = 0; i < inventoryItems.size(); i++) {
            this.inventoryItems.set(i, inventoryItems.get(i).copy());
        }

        this.armorItems = NonNullList.withSize(armorItems.size(), ItemStack.EMPTY);
        for (int i = 0; i < armorItems.size(); i++) {
            this.armorItems.set(i, armorItems.get(i).copy());
        }
    }

    public NonNullList<ItemStack> getInventoryItems() {
        return inventoryItems;
    }

    public NonNullList<ItemStack> getArmorItems() {
        return armorItems;
    }
}
