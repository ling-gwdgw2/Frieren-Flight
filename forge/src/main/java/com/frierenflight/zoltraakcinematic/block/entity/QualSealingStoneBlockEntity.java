// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.block.entity;

import com.frierenflight.zoltraakcinematic.entity.boss.QualBossEntity;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicBlocks;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicEntities;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class QualSealingStoneBlockEntity extends BlockEntity {

    public static final int TOTAL_UNSEAL_TICKS = 100; // 5.0 seconds total ritual

    private boolean unsealing = false;
    private int unsealTicks = 0;

    public QualSealingStoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModCinematicBlocks.QUAL_SEALING_STONE_BE.get(), pos, state);
    }

    public boolean isUnsealing() {
        return unsealing;
    }

    public void startUnsealing() {
        if (!this.unsealing && this.level != null && !this.level.isClientSide) {
            this.unsealing = true;
            this.unsealTicks = 0;
            this.setChanged();

            if (this.level instanceof ServerLevel sl) {
                sl.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
                sl.playSound(null, this.worldPosition, ModCinematicSounds.QUAL_PHASE_TRANSITION.get(), SoundSource.BLOCKS, 1.8f, 0.75f);
            }
        }
    }

    public void clientTick() {
        if (!this.unsealing) {
            return;
        }
        this.unsealTicks++;
    }

    public void tick() {
        if (!this.unsealing || this.level == null || this.level.isClientSide) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) this.level;
        unsealTicks++;

        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + 0.5;
        double cz = worldPosition.getZ() + 0.5;

        if (unsealTicks % 10 == 0) {
            serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }

        if (unsealTicks <= 30) {
            if (unsealTicks % 10 == 0) {
                serverLevel.playSound(null, worldPosition, ModCinematicSounds.SINGULARITY_CHARGE.get(), SoundSource.BLOCKS, 1.5f, 0.9f);
            }
        } else if (unsealTicks <= 65) {
            if (unsealTicks % 12 == 0) {
                serverLevel.playSound(null, worldPosition, ModCinematicSounds.SINGULARITY_ACTIVE.get(), SoundSource.BLOCKS, 1.4f, 1.1f);
            }
        } else if (unsealTicks < TOTAL_UNSEAL_TICKS) {
            if (unsealTicks % 10 == 0) {
                serverLevel.playSound(null, worldPosition, ModCinematicSounds.QUAL_PHASE_TRANSITION.get(), SoundSource.HOSTILE, 1.8f, 1.25f);
            }
        } else {
            serverLevel.playSound(null, worldPosition, ModCinematicSounds.SINGULARITY_EXPLODE.get(), SoundSource.HOSTILE, 3.5f, 0.85f);
            serverLevel.playSound(null, worldPosition, ModCinematicSounds.QUAL_SPAWN.get(), SoundSource.HOSTILE, 3.0f, 1.0f);

            QualBossEntity qual = ModCinematicEntities.QUAL_BOSS.get().create(serverLevel);
            if (qual != null) {
                qual.moveTo(cx, cy + 3.0, cz, 0.0f, 0.0f);
                qual.initializeHealthScaling();
                serverLevel.addFreshEntity(qual);
            }

            serverLevel.setBlock(worldPosition, Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), 3);
        }
    }

    public float getUnsealProgress(float partialTick) {
        if (!unsealing) return 0.0f;
        return net.minecraft.util.Mth.clamp((this.unsealTicks + partialTick) / (float) TOTAL_UNSEAL_TICKS, 0.0f, 1.0f);
    }

    public int getUnsealTicks() {
        return unsealTicks;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Unsealing", this.unsealing);
        tag.putInt("UnsealTicks", this.unsealTicks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.unsealing = tag.getBoolean("Unsealing");
        this.unsealTicks = tag.getInt("UnsealTicks");
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        super.onDataPacket(net, pkt);
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.unsealing = tag.getBoolean("Unsealing");
            this.unsealTicks = tag.getInt("UnsealTicks");
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        this.unsealing = tag.getBoolean("Unsealing");
        this.unsealTicks = tag.getInt("UnsealTicks");
    }
}
