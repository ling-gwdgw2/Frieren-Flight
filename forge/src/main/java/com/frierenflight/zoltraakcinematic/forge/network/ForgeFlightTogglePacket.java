// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.forge.network;

import com.frierenflight.zoltraakcinematic.network.FlightTogglePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ForgeFlightTogglePacket {

    public ForgeFlightTogglePacket() {
    }

    public static void encode(ForgeFlightTogglePacket msg, FriendlyByteBuf buf) {
    }

    public static ForgeFlightTogglePacket decode(FriendlyByteBuf buf) {
        return new ForgeFlightTogglePacket();
    }

    public static void handle(ForgeFlightTogglePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                FlightTogglePayload.handleServer(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

