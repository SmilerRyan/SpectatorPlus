package com.hpfxd.spectatorplus.fabric.sync;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

public class PlayerInventoryArmorStore {
    private final NonNullList<ItemStack> fullInventory;

    public PlayerInventoryArmorStore(NonNullList<ItemStack> fullInventory) {
        // Deep copy the list to prevent modifications to the original
        this.fullInventory = NonNullList.withSize(fullInventory.size(), ItemStack.EMPTY);
        for (int i = 0; i < fullInventory.size(); i++) {
            this.fullInventory.set(i, fullInventory.get(i).copy());
        }
    }

    public NonNullList<ItemStack> getFullInventory() {
        return this.fullInventory;
    }
}
