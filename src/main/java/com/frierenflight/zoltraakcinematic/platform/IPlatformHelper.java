// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.platform;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

/**
 * Service Provider Interface (SPI) for loader- and version-independent operations.
 * Implemented separately for Forge (1.20.1) and NeoForge (1.21.1).
 */
public interface IPlatformHelper {

    /**
     * @return Platform identifier (e.g., "NeoForge 1.21.1" or "Forge 1.20.1")
     */
    String getPlatformName();

    /**
     * @return True if the physical environment is Client
     */
    boolean isClient();

    /**
     * @return True if the given mod id is loaded in the current runtime
     */
    boolean isModLoaded(String modId);

    /**
     * Extracts parent entity if entity is a multipart entity part.
     * Handles net.neoforged.neoforge.entity.PartEntity vs net.minecraftforge.entity.PartEntity.
     */
    Entity getPartParent(Entity entity);

    /**
     * Sends a Defense Cast packet to the server.
     */
    void sendDefenseCastToServer(int mode);

    /**
     * Sends a Flight Toggle packet to the server.
     */
    void sendFlightToggleToServer();

    /**
     * Synchronizes mana data to a specific player.
     */
    void syncPlayerMana(ServerPlayer player, MagicData magicData);

    /**
     * Attaches weapon attribute modifiers to Frieren's Staff item properties.
     */
    Item.Properties applyStaffAttributes(Item.Properties properties);
}

