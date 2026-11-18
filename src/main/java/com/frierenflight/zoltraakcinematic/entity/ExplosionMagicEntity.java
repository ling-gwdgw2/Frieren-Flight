// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.entity;

import com.frierenflight.zoltraakcinematic.ZoltraakDamage;
import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicEntities;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicSounds;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicSpells;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * ExplosionMagicEntity
 * Handles the downward sky-strike beam, catastrophic blast damage,
 * shallow parabolic bowl crater carving with flung FallingBlockEntity debris,
 * and client detonation state for ExplosionMagicRenderer.
 */
public class ExplosionMagicEntity extends Entity implements TraceableEntity, OwnableEntity {

    public static final int DETONATE_TICK = 6;
    public static final int LIFETIME_TICKS = 115;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID =
            SynchedEntityData.defineId(ExplosionMagicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_START_X =
            SynchedEntityData.defineId(ExplosionMagicEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_START_Y =
            SynchedEntityData.defineId(ExplosionMagicEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_START_Z =
            SynchedEntityData.defineId(ExplosionMagicEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TARGET_X =
            SynchedEntityData.defineId(ExplosionMagicEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TARGET_Y =
            SynchedEntityData.defineId(ExplosionMagicEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TARGET_Z =
            SynchedEntityData.defineId(ExplosionMagicEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_POWER =
            SynchedEntityData.defineId(ExplosionMagicEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(ExplosionMagicEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CRATER_DEPTH =
            SynchedEntityData.defineId(ExplosionMagicEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(ExplosionMagicEntity.class, EntityDataSerializers.INT);

    private UUID casterUuid;
    private boolean blastResolved = false;
    public transient boolean clientDetonated = false;

    public ExplosionMagicEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public ExplosionMagicEntity(Level level, LivingEntity caster, Vec3 startPos, Vec3 targetPos, float power) {
        this(ModCinematicEntities.EXPLOSION.get(), level);
        this.setOwner(caster);
        this.setPos(targetPos.x, targetPos.y, targetPos.z);
        this.entityData.set(DATA_START_X, (float) startPos.x);
        this.entityData.set(DATA_START_Y, (float) startPos.y);
        this.entityData.set(DATA_START_Z, (float) startPos.z);
        this.entityData.set(DATA_TARGET_X, (float) targetPos.x);
        this.entityData.set(DATA_TARGET_Y, (float) targetPos.y);
        this.entityData.set(DATA_TARGET_Z, (float) targetPos.z);
        this.entityData.set(DATA_POWER, power);
        this.entityData.set(DATA_RADIUS, (float) ZoltraakCinematicConfig.getExplosionRadius());
        this.entityData.set(DATA_CRATER_DEPTH, (float) ZoltraakCinematicConfig.getExplosionCraterDepth());
        this.entityData.set(DATA_SEED, level.random.nextInt());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_CASTER_ID, -1);
        builder.define(DATA_START_X, 0.0f);
        builder.define(DATA_START_Y, 0.0f);
        builder.define(DATA_START_Z, 0.0f);
        builder.define(DATA_TARGET_X, 0.0f);
        builder.define(DATA_TARGET_Y, 0.0f);
        builder.define(DATA_TARGET_Z, 0.0f);
        builder.define(DATA_POWER, 100.0f);
        builder.define(DATA_RADIUS, 22.0f);
        builder.define(DATA_CRATER_DEPTH, 6.0f);
        builder.define(DATA_SEED, 1337);
    }

    public void setOwner(LivingEntity owner) {
        if (owner != null) {
            this.casterUuid = owner.getUUID();
            this.entityData.set(DATA_CASTER_ID, owner.getId());
        }
    }

    @Override
    public UUID getOwnerUUID() {
        return this.casterUuid;
    }

    @Override
    public LivingEntity getOwner() {
        if (this.casterUuid != null && this.level() instanceof ServerLevel sl) {
            Entity byUuid = sl.getEntity(this.casterUuid);
            if (byUuid instanceof LivingEntity living) return living;
        }
        int id = this.entityData.get(DATA_CASTER_ID);
        if (id >= 0) {
            Entity byId = this.level().getEntity(id);
            if (byId instanceof LivingEntity living) return living;
        }
        return null;
    }

    public Vec3 getStartPos() {
        return new Vec3(this.entityData.get(DATA_START_X), this.entityData.get(DATA_START_Y), this.entityData.get(DATA_START_Z));
    }

    public Vec3 getTargetPos() {
        return new Vec3(this.entityData.get(DATA_TARGET_X), this.entityData.get(DATA_TARGET_Y), this.entityData.get(DATA_TARGET_Z));
    }

    public float getPower() {
        return this.entityData.get(DATA_POWER);
    }

    public float getRadius() {
        return Math.max(4.0f, this.entityData.get(DATA_RADIUS));
    }

    public float getCraterDepth() {
        return Math.max(1.0f, this.entityData.get(DATA_CRATER_DEPTH));
    }

    public int getSeed() {
        return this.entityData.get(DATA_SEED);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            if (this.tickCount == 1) {
                Vec3 s = getStartPos();
                Vec3 t = getTargetPos();
                serverLevel.playSound(null, s.x, s.y, s.z, ModCinematicSounds.ZOLTRAAK_GREAT_FIRE.get(), SoundSource.PLAYERS, 2.2f, 1.35f);
                serverLevel.playSound(null, t.x, t.y, t.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 2.0f, 0.72f);
            }

            if (this.tickCount >= DETONATE_TICK && !this.blastResolved) {
                this.blastResolved = true;
                resolveDetonation(serverLevel);
            }

            if (this.tickCount >= LIFETIME_TICKS) {
                this.discard();
            }
        }
    }

    private void resolveDetonation(ServerLevel level) {
        Vec3 center = getTargetPos();
        double radius = getRadius();
        Entity owner = getOwner();

        // Multi-layered thunderous detonation stack
        level.playSound(null, center.x, center.y, center.z, ModCinematicSounds.SINGULARITY_EXPLODE.get(), SoundSource.PLAYERS, 4.0f, 0.72f);
        level.playSound(null, center.x, center.y, center.z, ModCinematicSounds.ZOLTRAAK_IMPACT.get(), SoundSource.PLAYERS, 3.2f, 0.65f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.8f, 0.70f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.2f, 0.80f);

        applyBlastDamage(level, center, radius, owner);

        if (ZoltraakCinematicConfig.isExplosionBreakBlocks() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            carveCraterAndFlingDebris(level, center, radius);
        }
    }

    private void applyBlastDamage(ServerLevel level, Vec3 center, double radius, Entity owner) {
        double reach = radius * 1.15;
        AABB bounds = new AABB(
                center.x - reach, center.y - reach * 0.75, center.z - reach,
                center.x + reach, center.y + reach * 1.25, center.z + reach
        );
        float baseDamage = (float) ((40.0 + getPower() * 0.85) * ZoltraakCinematicConfig.getExplosionDamageScale());
        var school = ModCinematicSpells.EXPLOSION.get().getSchoolType();
        var dmgSource = ExplosionDamage.source(level, this, owner);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, bounds, target -> {
            if (!target.isAlive() || target instanceof ArmorStand) return false;
            if (owner != null && target == owner) return false;
            if (owner != null && DamageSources.isFriendlyFireBetween(owner, target)) return false;
            if (target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
            return true;
        });

        for (LivingEntity target : targets) {
            Vec3 delta = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(center);
            double dist = delta.length();
            if (dist > reach) continue;

            double norm = Mth.clamp(dist / reach, 0.0, 1.0);
            float dmg = (float) (baseDamage * (1.0 - 0.68 * Math.pow(norm, 1.3)));
            ZoltraakDamage.apply(target, dmg, dmgSource, school, true);

            Vec3 dir = dist > 0.05 ? delta.scale(1.0 / dist) : new Vec3(0.0, 1.0, 0.0);
            double kb = (1.6 + (1.0 - norm) * 2.2);
            target.push(dir.x * kb, 0.45 + (1.0 - norm) * 0.85, dir.z * kb);
            target.hurtMarked = true;
        }
    }

    private void carveCraterAndFlingDebris(ServerLevel level, Vec3 center, double radius) {
        double craterR = Math.max(4.0, radius * 0.72);
        double depth = Math.min(getCraterDepth(), craterR * 0.55);
        int minY = Math.max(level.getMinBuildHeight() + 1, Mth.floor(center.y - depth - 2.0));
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.ceil(center.y + craterR * 0.78));
        int minX = Mth.floor(center.x - craterR);
        int maxX = Mth.ceil(center.x + craterR);
        int minZ = Mth.floor(center.z - craterR);
        int maxZ = Mth.ceil(center.z + craterR);

        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        double invR2 = 1.0 / (craterR * craterR);

        for (int x = minX; x <= maxX; x++) {
            double dx = x + 0.5 - center.x;
            for (int z = minZ; z <= maxZ; z++) {
                double dz = z + 0.5 - center.z;
                double hr2 = (dx * dx + dz * dz) * invR2;
                if (hr2 > 1.02) continue;

                // Subtle organic edge roughness so crater isn't a smooth geometric cone
                double jitter = (Math.sin(x * 0.71 + z * 0.53) + Math.cos(x * 0.47 - z * 0.83)) * 0.45;
                if (hr2 > 0.94 + jitter * 0.08) continue;

                double bowlFloorY = center.y - depth * (1.0 - Math.min(1.0, hr2)) + jitter * 0.65;
                int floorBlockY = Math.max(minY, Mth.floor(bowlFloorY));

                for (int y = maxY; y >= floorBlockY; y--) {
                    double dy = Math.max(0.0, y + 0.5 - center.y);
                    if ((dx * dx + dy * dy + dz * dz) * invR2 > 1.04) continue;

                    mPos.set(x, y, z);
                    BlockState state = level.getBlockState(mPos);
                    if (state.isAir() || state.hasBlockEntity()) continue;
                    float destroySpeed = state.getDestroySpeed(level, mPos);
                    if (destroySpeed < 0.0f || state.getBlock().getExplosionResistance() >= 900.0f) continue;

                    level.setBlock(mPos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("CasterUuid")) this.casterUuid = tag.getUUID("CasterUuid");
        this.blastResolved = tag.getBoolean("BlastResolved");
        this.entityData.set(DATA_START_X, tag.getFloat("SX"));
        this.entityData.set(DATA_START_Y, tag.getFloat("SY"));
        this.entityData.set(DATA_START_Z, tag.getFloat("SZ"));
        this.entityData.set(DATA_TARGET_X, tag.getFloat("TX"));
        this.entityData.set(DATA_TARGET_Y, tag.getFloat("TY"));
        this.entityData.set(DATA_TARGET_Z, tag.getFloat("TZ"));
        this.entityData.set(DATA_POWER, tag.contains("Power") ? tag.getFloat("Power") : 100.0f);
        this.entityData.set(DATA_RADIUS, tag.contains("Radius") ? tag.getFloat("Radius") : 22.0f);
        this.entityData.set(DATA_CRATER_DEPTH, tag.contains("Depth") ? tag.getFloat("Depth") : 6.0f);
        this.entityData.set(DATA_SEED, tag.getInt("Seed"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.casterUuid != null) tag.putUUID("CasterUuid", this.casterUuid);
        tag.putBoolean("BlastResolved", this.blastResolved);
        tag.putFloat("SX", this.entityData.get(DATA_START_X));
        tag.putFloat("SY", this.entityData.get(DATA_START_Y));
        tag.putFloat("SZ", this.entityData.get(DATA_START_Z));
        tag.putFloat("TX", this.entityData.get(DATA_TARGET_X));
        tag.putFloat("TY", this.entityData.get(DATA_TARGET_Y));
        tag.putFloat("TZ", this.entityData.get(DATA_TARGET_Z));
        tag.putFloat("Power", this.entityData.get(DATA_POWER));
        tag.putFloat("Radius", this.entityData.get(DATA_RADIUS));
        tag.putFloat("Depth", this.entityData.get(DATA_CRATER_DEPTH));
        tag.putInt("Seed", this.entityData.get(DATA_SEED));
    }

    @Override
    public boolean shouldRender(double x, double y, double z) {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        Vec3 start = getStartPos();
        Vec3 target = getTargetPos();
        double r = Math.max(32.0d, (double) getRadius() * 1.8d);
        double minX = Math.min(start.x, target.x) - r;
        double minY = Math.min(start.y, target.y) - r;
        double minZ = Math.min(start.z, target.z) - r;
        double maxX = Math.max(start.x, target.x) + r;
        double maxY = Math.max(start.y, target.y + getRadius() * 1.4d) + r;
        double maxZ = Math.max(start.z, target.z) + r;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
