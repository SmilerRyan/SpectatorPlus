package com.hpfxd.spectatorplus.fabric.mixin;

import com.google.common.collect.Lists;
import com.hpfxd.spectatorplus.fabric.SpectatorMod;
import com.hpfxd.spectatorplus.fabric.sync.ServerSyncController;
import com.hpfxd.spectatorplus.fabric.sync.packet.ClientboundExperienceSyncPacket;
import com.hpfxd.spectatorplus.fabric.sync.packet.ClientboundFoodSyncPacket;
import com.hpfxd.spectatorplus.fabric.sync.packet.ClientboundHotbarSyncPacket;
import com.hpfxd.spectatorplus.fabric.sync.packet.ClientboundSelectedSlotSyncPacket;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.hpfxd.spectatorplus.fabric.sync.PlayerInventoryArmorStore;
import net.minecraft.core.NonNullList; // Added NonNullList import
import java.util.Set;
import com.hpfxd.spectatorplus.fabric.sync.PlayerInventoryArmorStore; // Ensure this import
import java.util.Map; // Ensure this import
import java.util.UUID; // Ensure this import
import java.util.concurrent.ConcurrentHashMap; // Ensure this import
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket; // Ensure this import
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket; // Ensure this import

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player {
    @Shadow public ServerGamePacketListenerImpl connection;
    @Shadow public abstract boolean teleportTo(ServerLevel newLevel, double x, double y, double z, Set<Relative> relative, float yaw, float pitch, boolean resetCamera);
    @Shadow public abstract void setCamera(@Nullable Entity entityToSpectate);

    @Unique
    private static final Map<UUID, PlayerInventoryArmorStore> spectatorSavedInventories = new ConcurrentHashMap<>();

    public ServerPlayerMixin(Level level, BlockPos pos, float yRot, GameProfile gameProfile) {
        super(level, pos, yRot, gameProfile);
    }

    @Inject(method = "setCamera(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void spectatorplus$manageSpectatorInventory(Entity entityToSpectate, CallbackInfo ci) {
        ServerPlayer spectator = (ServerPlayer) (Object) this;
        UUID spectatorId = spectator.getUUID();

        if (entityToSpectate == null || entityToSpectate == spectator) { // Stopping spectating or spectating self
            if (spectatorSavedInventories.containsKey(spectatorId)) {
                PlayerInventoryArmorStore saved = spectatorSavedInventories.remove(spectatorId);

                spectator.getInventory().load(saved.getFullInventory());

                // Send packet to client to update their view
                spectator.connection.send(new ClientboundContainerSetContentPacket(
                    spectator.containerMenu.containerId,
                    spectator.containerMenu.incrementStateId(),
                    spectator.getInventory().items, // Main inventory items
                    ItemStack.EMPTY // Carried item
                ));
            }
        } else if (entityToSpectate instanceof Player && entityToSpectate != spectator) { // Starting to spectate a new player
            // Save current inventory before it's changed
            // PlayerInventory.save() creates a new list with all items (main, armor, offhand)
            NonNullList<ItemStack> currentInventory = NonNullList.withSize(spectator.getInventory().getContainerSize(), ItemStack.EMPTY);
            spectator.getInventory().save(currentInventory); // Saves into currentInventory

            spectatorSavedInventories.put(spectatorId, new PlayerInventoryArmorStore(currentInventory));

            // Now, switch spectator's inventory to target's inventory
            if (entityToSpectate instanceof ServerPlayer) { // Double check, though outer if implies Player
                ServerPlayer target = (ServerPlayer) entityToSpectate;

                // Clear spectator's current inventory (main, armor, offhand)
                spectator.getInventory().clearContent();

                // Copy target's full inventory to spectator
                NonNullList<ItemStack> targetInventoryCopy = NonNullList.withSize(target.getInventory().getContainerSize(), ItemStack.EMPTY);
                target.getInventory().save(targetInventoryCopy); // Save target's inventory into the list
                spectator.getInventory().load(targetInventoryCopy);    // Load this list into spectator's inventory

                // Update client with the new inventory
                // ClientboundContainerSetContentPacket is already imported
                spectator.connection.send(new ClientboundContainerSetContentPacket(
                    spectator.containerMenu.containerId,
                    spectator.containerMenu.incrementStateId(),
                    spectator.getInventory().items, // Main inventory items
                    ItemStack.EMPTY // Carried item
                ));

                // Sync selected hotbar slot
                // Ensure ClientboundSetCarriedItemPacket is imported
                // import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
                spectator.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(target.getInventory().selected));
            }
        }
    }

    @Inject(method = "doTick()V", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerPlayer;lastFoodSaturationZero:Z", opcode = Opcodes.PUTFIELD))
    private void spectatorplus$syncFood(CallbackInfo ci) {
        ServerSyncController.broadcastPacketToSpectators((ServerPlayer) (Object) this, new ClientboundFoodSyncPacket(this.getUUID(), this.foodData.getFoodLevel(), this.foodData.getSaturationLevel()));
    }

    @Inject(method = "doTick()V", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerPlayer;lastSentExp:I", opcode = Opcodes.PUTFIELD))
    private void spectatorplus$syncExperience(CallbackInfo ci) {
        ServerSyncController.broadcastPacketToSpectators((ServerPlayer) (Object) this, new ClientboundExperienceSyncPacket(this.getUUID(), this.experienceProgress, this.getXpNeededForNextLevel(), this.experienceLevel));
    }

    @Inject(method = "setCamera(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void spectatorplus$syncAndSwitchInventoryToNewSpectator(Entity entityToSpectate, CallbackInfo ci) {
        ServerPlayer spectator = (ServerPlayer) (Object) this;

        // Only proceed if spectating a ServerPlayer and that player is not self
        if (entityToSpectate instanceof ServerPlayer && entityToSpectate != spectator) {
            ServerPlayer target = (ServerPlayer) entityToSpectate;

            // ORIGINAL SPECTATORPLUS SYNC LOGIC (NOW DISABLED/COMMENTED)
            // ServerSyncController.sendPacket(spectator, ClientboundExperienceSyncPacket.initializing(target));
            // ServerSyncController.sendPacket(spectator, ClientboundFoodSyncPacket.initializing(target));
            // ServerSyncController.sendPacket(spectator, ClientboundHotbarSyncPacket.initializing(target));
            // ServerSyncController.sendPacket(spectator, ClientboundSelectedSlotSyncPacket.initializing(target));

            // Send initial map data patch packet if the target has a map in inventory (this was part of original sync)
            for (final ItemStack stack : target.getInventory().items) {
                if (stack.is(Items.FILLED_MAP)) {
                    final MapId mapId = stack.get(DataComponents.MAP_ID);
                    if (mapId != null) {
                        final MapItemSavedData mapItemSavedData = MapItem.getSavedData(mapId, spectator.level());
                        if (mapItemSavedData != null) {
                            spectator.connection.send(getInitialMapDataPacket(mapId, mapItemSavedData));
                        }
                    }
                }
            }
        }
    }

    /**
     * Constructs a new {@link ClientboundMapItemDataPacket} containing all data from {@link MapItemSavedData} and not
     * relying that the player has previously received any updates of this map.
     */
    @Unique
    private static ClientboundMapItemDataPacket getInitialMapDataPacket(MapId mapId, MapItemSavedData data) {
        return new ClientboundMapItemDataPacket(mapId, data.scale, data.locked, Lists.newArrayList(data.getDecorations()), new MapItemSavedData.MapPatch(0, 0, 128, 128, data.colors));
    }

    @Inject(
            method = "synchronizeSpecialItemUpdates(Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V", ordinal = 0)
    )
    private void spectatorplus$syncMapData(CallbackInfo ci, @Local Packet<?> packet) {
        // Send map packet to any spectators of this player. If this is only an update patch of a previously sent map,
        // any spectators would have already received previous updates, so sending this is fine.
        for (final ServerPlayer spectator : ServerSyncController.getSpectators(this)) {
            spectator.connection.send(packet);
        }
    }

    @Inject(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;move(Lnet/minecraft/server/level/ServerPlayer;)V", shift = At.Shift.AFTER))
    private void spectatorplus$allowTransferBetweenLevels(CallbackInfo ci, @Local(ordinal = 0) Entity entity) {
        // MC-261799
        // Adapted fix from https://github.com/PaperMC/Paper/pull/9349

        if (entity.level() == this.level()) {
            if (SpectatorMod.config.autoUpdatePosition && this.tickCount % 20 == 0) {
                // We send the player an additional teleport packet here to indicate that the position of itself has been moved.
                // Without this packet, if a player travels a too far distance, chunks will start to become invisible for our spectator.

                this.connection.teleport(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
            }
        } else if (SpectatorMod.config.allowTransferBetweenLevels) {
            // Teleport ourselves to our camera
            this.teleportTo((ServerLevel) entity.level(), entity.getX(), entity.getY(), entity.getZ(), Set.of(), entity.getYRot(), entity.getXRot(), false);

            // Update the tracker of the other dimension for our cross-dimension teleport
            final var entityMap = ((ChunkMapAccessor) ((ServerLevel) entity.level()).getChunkSource().chunkMap).getEntityMap();
            final ChunkMap.TrackedEntity tracker = entityMap.get(entity.getId());
            if (tracker != null) {
                tracker.updatePlayer((ServerPlayer) (Object) this);
            }

            this.setCamera(entity);
        }
    }

    @Inject(method = "disconnect()V", at = @At("TAIL"))
    private void spectatorplus$handleDisconnect(CallbackInfo ci) {
        ServerPlayer disconnectingPlayer = (ServerPlayer) (Object) this;
        UUID disconnectingPlayerId = disconnectingPlayer.getUUID();

        if (spectatorSavedInventories.containsKey(disconnectingPlayerId)) {
            PlayerInventoryArmorStore saved = spectatorSavedInventories.remove(disconnectingPlayerId);
            disconnectingPlayer.getInventory().load(saved.getFullInventory());
        }

        if (disconnectingPlayer.getServer() != null) {
            for (ServerPlayer onlinePlayer : disconnectingPlayer.getServer().getPlayerList().getPlayers()) {
                if (onlinePlayer.getCamera() == disconnectingPlayer) {
                    onlinePlayer.setCamera(onlinePlayer);
                }
            }
        }
    }
}
