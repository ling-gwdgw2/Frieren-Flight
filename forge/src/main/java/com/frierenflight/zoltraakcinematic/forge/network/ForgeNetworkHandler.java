// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.forge.network;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ForgeNetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ZoltraakCinematicMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                ForgeDefenseCastPacket.class,
                ForgeDefenseCastPacket::encode,
                ForgeDefenseCastPacket::decode,
                ForgeDefenseCastPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                ForgeFlightTogglePacket.class,
                ForgeFlightTogglePacket::encode,
                ForgeFlightTogglePacket::decode,
                ForgeFlightTogglePacket::handle
        );
    }
}

