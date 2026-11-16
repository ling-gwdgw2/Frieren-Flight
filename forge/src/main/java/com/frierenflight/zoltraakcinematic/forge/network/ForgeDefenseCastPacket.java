// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.forge.network;

import com.frierenflight.zoltraakcinematic.network.DefenseCastPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ForgeDefenseCastPacket {
    private final int mode;

    public ForgeDefenseCastPacket(int mode) {
        this.mode = mode;
    }

    public static void encode(ForgeDefenseCastPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.mode);
    }

    public static ForgeDefenseCastPacket decode(FriendlyByteBuf buf) {
        return new ForgeDefenseCastPacket(buf.readVarInt());
    }

    public static void handle(ForgeDefenseCastPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                DefenseCastPayload.handleServer(player, msg.mode);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

