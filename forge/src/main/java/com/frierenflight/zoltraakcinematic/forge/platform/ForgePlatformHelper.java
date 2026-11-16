// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.forge.platform;

import com.frierenflight.zoltraakcinematic.forge.network.ForgeDefenseCastPacket;
import com.frierenflight.zoltraakcinematic.forge.network.ForgeFlightTogglePacket;
import com.frierenflight.zoltraakcinematic.forge.network.ForgeNetworkHandler;
import com.frierenflight.zoltraakcinematic.platform.IPlatformHelper;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import io.redspace.ironsspellbooks.setup.Messages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;

public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Forge 1.20.1";
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Entity getPartParent(Entity entity) {
        if (entity instanceof PartEntity<?> part) {
            return part.getParent();
        }
        return null;
    }

    @Override
    public void sendDefenseCastToServer(int mode) {
        ForgeNetworkHandler.CHANNEL.sendToServer(new ForgeDefenseCastPacket(mode));
    }

    @Override
    public void sendFlightToggleToServer() {
        ForgeNetworkHandler.CHANNEL.sendToServer(new ForgeFlightTogglePacket());
    }

    @Override
    public void syncPlayerMana(ServerPlayer player, MagicData magicData) {
        Messages.sendToPlayer(new SyncManaPacket(magicData), player);
    }

    @Override
    public Item.Properties applyStaffAttributes(Item.Properties properties) {
        // In Forge 1.20.1, Item.Properties does not take attributes;
        // StaffItem assigns attributes in its own constructor / getDefaultAttributeModifiers.
        return properties;
    }
}

