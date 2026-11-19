// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.entity;

import com.frierenflight.zoltraakcinematic.ZoltraakDamage;
import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicEntities;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicSounds;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicSpells;
import com.frierenflight.zoltraakcinematic.spell.ExplosionSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * ExplosionMagicEntity
 * Handles the downward sky-strike beam, catastrophic blast damage,
 * shallow parabolic bowl crater carving with flung FallingBlockEntity debris,
 * and client detonation state for ExplosionMagicRenderer.
 */
public class ExplosionMagicEntity extends Entity implements TraceableEntity, OwnableEntity {

    public static final int DETONATE_TICK = 28;
    public static final int LIFETIME_TICKS = 220;

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
    private static final EntityDataAccessor<Float> DATA_TIME_SCALE =
            SynchedEntityData.defineId(ExplosionMagicEntity.class, EntityDataSerializers.FLOAT);

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
        this.entityData.set(DATA_TIME_SCALE, ZoltraakCinematicConfig.getExplosionAnimationScale());
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
        builder.define(DATA_TIME_SCALE, 1.0f);
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

    public float getTimeScale() {
        return Math.max(0.4f, Math.min(2.5f, this.entityData.get(DATA_TIME_SCALE)));
    }

    public int getDetonateTick() {
        return Math.max(4, Math.round(DETONATE_TICK * getTimeScale()));
    }

    public int getLifetimeTicks() {
        return Math.max(getDetonateTick() + 20, Math.round(LIFETIME_TICKS * getTimeScale()));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            int detonateTick = getDetonateTick();

            if (this.tickCount >= detonateTick && !this.blastResolved) {
                this.blastResolved = true;
                resolveDetonation(serverLevel);
            }

            if (this.tickCount >= getLifetimeTicks()) {
                this.discard();
            }
        }
    }

    private void resolveDetonation(ServerLevel level) {
        Vec3 center = getTargetPos();
        double radius = getRadius();
        Entity owner = getOwner();

        // Single clean explosion detonation sound
        level.playSound(null, center.x, center.y, center.z, ModCinematicSounds.SINGULARITY_EXPLODE.get(), SoundSource.PLAYERS, 4.2f, 0.68f);

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
        float baseDamage = ExplosionSpell.computeBlastDamage(getPower());
        var school = ModCinematicSpells.EXPLOSION.get().getSchoolType();
        DamageSource mobDmgSource = ExplosionDamage.source(level, this, this);
        DamageSource playerDmgSource = owner != null ? ExplosionDamage.source(level, this, owner) : mobDmgSource;
        boolean respectFF = ZoltraakCinematicConfig.isProtectAllies();

        List<Entity> candidates = level.getEntities(this, bounds, Entity::isAlive);
        Set<UUID> hitTargets = new HashSet<>();

        for (Entity e : candidates) {
            LivingEntity target = ZoltraakDamage.resolveLivingTarget(e);
            if (target == null || !target.isAlive() || target instanceof ArmorStand) continue;
            if (owner != null && target == owner) continue;
            if (owner != null && respectFF && DamageSources.isFriendlyFireBetween(owner, target)) continue;
            if (target instanceof Player player && (player.isCreative() || player.isSpectator()
                    || (owner instanceof Player op && !op.canHarmPlayer(player)))) continue;

            double dist = Math.sqrt(e.getBoundingBox().distanceToSqr(center));
            if (dist > reach || !hitTargets.add(target.getUUID())) continue;

            // Use blast entity as causingEntity for mobs/bosses so range-capped bosses (e.g. L_Ender's Cataclysm)
            // measure distance from the detonation epicenter rather than the distant caster, while preserving kill credit.
            DamageSource dmgSource = (target instanceof Player && owner != null) ? playerDmgSource : mobDmgSource;
            if (owner instanceof Player player) {
                target.setLastHurtByPlayer(player);
            }

            double norm = Mth.clamp(dist / reach, 0.0, 1.0);
            float dmg = (float) (baseDamage * (1.0 - 0.68 * Math.pow(norm, 1.3)));
            if (ZoltraakDamage.apply(target, dmg, dmgSource, school, true)) {
                Vec3 delta = target.getBoundingBox().getCenter().subtract(center);
                double deltaLen = delta.length();
                Vec3 dir = deltaLen > 0.05 ? delta.scale(1.0 / deltaLen) : new Vec3(0.0, 1.0, 0.0);
                double kb = 1.6 + (1.0 - norm) * 2.2;
                target.push(dir.x * kb, 0.45 + (1.0 - norm) * 0.85, dir.z * kb);
                target.hurtMarked = true;
            }
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

        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;
        int[][] carvedFloor = new int[sizeX][sizeZ];
        boolean[][] insideCrater = new boolean[sizeX][sizeZ];

        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        double invR2 = 1.0 / (craterR * craterR);

        // Pass 1: Carve parabolic blast bowl and record final floor height per column
        for (int x = minX; x <= maxX; x++) {
            double dx = x + 0.5 - center.x;
            int ix = x - minX;
            for (int z = minZ; z <= maxZ; z++) {
                double dz = z + 0.5 - center.z;
                double hr2 = (dx * dx + dz * dz) * invR2;
                if (hr2 > 1.02) continue;

                // Subtle organic edge roughness so crater isn't a smooth geometric cone
                double jitter = (Math.sin(x * 0.71 + z * 0.53) + Math.cos(x * 0.47 - z * 0.83)) * 0.45;
                if (hr2 > 0.94 + jitter * 0.08) continue;

                double bowlFloorY = center.y - depth * (1.0 - Math.min(1.0, hr2)) + jitter * 0.65;
                int floorBlockY = Math.max(minY, Mth.floor(bowlFloorY));
                int iz = z - minZ;
                insideCrater[ix][iz] = true;
                carvedFloor[ix][iz] = floorBlockY;

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

        if (!ZoltraakCinematicConfig.isExplosionVolcanicCrater()) {
            return;
        }

        // Pass 2: Scorch the carved crater into a volcanic magma core, branching fissures, dark charred stone & scattered fire
        boolean igniteFire = ZoltraakCinematicConfig.isExplosionCraterFire();
        int seed = getSeed();
        double angleOffset1 = (seed & 255) * 0.02454369;
        double angleOffset2 = ((seed >> 8) & 255) * 0.02454369;

        BlockState magmaState = Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockState blackstoneState = Blocks.BLACKSTONE.defaultBlockState();
        BlockState basaltState = Blocks.BASALT.defaultBlockState();
        BlockState deepslateState = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        BlockState fireState = Blocks.FIRE.defaultBlockState();
        BlockPos.MutableBlockPos abovePos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            double dx = x + 0.5 - center.x;
            int ix = x - minX;
            for (int z = minZ; z <= maxZ; z++) {
                int iz = z - minZ;
                if (!insideCrater[ix][iz]) continue;

                double dz = z + 0.5 - center.z;
                double rNorm = Math.sqrt(Math.max(0.0, (dx * dx + dz * dz) * invR2));
                int floorBlockY = carvedFloor[ix][iz];

                // Locate exposed solid terrain block at or just below the carved floor (avoids floating blocks over caves/air)
                int surfaceY = -9999;
                int searchMinY = Math.max(level.getMinBuildHeight() + 1, floorBlockY - 3);
                for (int sy = floorBlockY - 1; sy >= searchMinY; sy--) {
                    mPos.set(x, sy, z);
                    BlockState s = level.getBlockState(mPos);
                    if (!s.getFluidState().isEmpty()) {
                        surfaceY = -9999; // Never scorch underwater or inside lava pools
                        break;
                    }
                    if (!s.isAir()) {
                        if (isScorchableTerrain(level, mPos, s)) {
                            surfaceY = sy;
                        }
                        break;
                    }
                }
                if (surfaceY == -9999) continue;

                // Ensure the block directly above the crater surface is open air (not under a roof or fluid)
                abovePos.set(x, surfaceY + 1, z);
                BlockState aboveState = level.getBlockState(abovePos);
                if (!aboveState.isAir() || !aboveState.getFluidState().isEmpty()) continue;

                // Organic multi-frequency noise & domain-warped radial fissures radiating outward from the epicenter
                double nCoarse = volcanicNoise(x * 0.16, z * 0.16, seed);
                double nFine = volcanicNoise(x * 0.42 + 17.3, z * 0.42 - 11.7, seed ^ 0x5A3C);
                double hRand = columnHash(x, z, seed);
                double angle = Math.atan2(dz, dx);

                double warp = (nCoarse - 0.5) * 2.4 + (nFine - 0.5) * 0.9;
                double fissure1 = Math.pow(1.0 - Math.abs(Math.sin((angle * 6.0 + rNorm * 3.6 + warp + angleOffset1) * 0.5)), 3.2);
                double fissure2 = Math.pow(1.0 - Math.abs(Math.sin((angle * 11.0 - rNorm * 5.0 - warp * 0.8 + angleOffset2) * 0.5)), 4.0) * 0.84;
                double fissure = Math.max(fissure1, fissure2);

                // Ragged outer scorch edge so the rim blends naturally into surrounding grass/dirt/stone
                double scorchReach = 0.86 + (nCoarse - 0.5) * 0.22 + fissure * 0.14;
                if (rNorm > scorchReach) continue;

                // Determine volcanic block type for surface crust
                double coreRadius = 0.34 + (nCoarse - 0.5) * 0.16;
                boolean isMagmaCore = rNorm < coreRadius && hRand < (0.86 - rNorm * 0.35);
                boolean isMagmaFissure = rNorm < 0.86 && fissure > (0.48 + rNorm * 0.22);

                BlockState chosenSurface;
                if (isMagmaCore || isMagmaFissure) {
                    chosenSurface = magmaState;
                } else {
                    chosenSurface = pickScorchedStone(rNorm, nCoarse, nFine, hRand, blackstoneState, basaltState, deepslateState);
                }

                mPos.set(x, surfaceY, z);
                level.setBlock(mPos, chosenSurface, 2);

                // Also scorch exposed stepped wall sides on steeper inner crater slopes (no raw dirt seams peeking out)
                int minNeighborFloor = floorBlockY;
                if (ix > 0 && insideCrater[ix - 1][iz]) minNeighborFloor = Math.min(minNeighborFloor, carvedFloor[ix - 1][iz]);
                if (ix + 1 < sizeX && insideCrater[ix + 1][iz]) minNeighborFloor = Math.min(minNeighborFloor, carvedFloor[ix + 1][iz]);
                if (iz > 0 && insideCrater[ix][iz - 1]) minNeighborFloor = Math.min(minNeighborFloor, carvedFloor[ix][iz - 1]);
                if (iz + 1 < sizeZ && insideCrater[ix][iz + 1]) minNeighborFloor = Math.min(minNeighborFloor, carvedFloor[ix][iz + 1]);

                int wallBottomY = Math.max(level.getMinBuildHeight() + 1, Math.min(surfaceY - 1, minNeighborFloor - 1));
                int wallLimitY = Math.max(wallBottomY, surfaceY - 2);
                for (int wy = surfaceY - 1; wy >= wallLimitY; wy--) {
                    mPos.set(x, wy, z);
                    BlockState ws = level.getBlockState(mPos);
                    if (!isScorchableTerrain(level, mPos, ws)) break;
                    BlockState wallMat = (isMagmaFissure && wy == surfaceY - 1 && hRand < 0.45)
                            ? magmaState
                            : pickScorchedStone(rNorm, nCoarse, nFine, columnHash(x, z + wy * 13, seed), blackstoneState, basaltState, deepslateState);
                    level.setBlock(mPos, wallMat, 2);
                }

                // Scattered surface fire on magma veins & charred rock
                if (igniteFire && rNorm < 0.84) {
                    double fireChance = (chosenSurface == magmaState)
                            ? (0.14 - rNorm * 0.06)
                            : (0.065 * (1.0 - rNorm * 0.65));
                    double fireRoll = columnHash(x * 3 + 11, z * 5 - 7, seed ^ 0x2F91);
                    if (fireRoll < fireChance && fireState.canSurvive(level, abovePos)) {
                        level.setBlock(abovePos, fireState, 2);
                    }
                }
            }
        }
    }

    private static boolean isScorchableTerrain(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
            return false;
        }
        float destroySpeed = state.getDestroySpeed(level, pos);
        if (destroySpeed < 0.0f || state.getBlock().getExplosionResistance() >= 900.0f) {
            return false;
        }
        return state.isFaceSturdy(level, pos, Direction.UP);
    }

    private static BlockState pickScorchedStone(
            double rNorm, double nCoarse, double nFine, double hRand,
            BlockState blackstone, BlockState basalt, BlockState cobbledDeepslate
    ) {
        // Inner crater leans charred blackstone + basalt; outer rim transitions softly through cobbled deepslate
        double rimBlend = Mth.clamp((rNorm - 0.32) / 0.55, 0.0, 1.0);
        double patch = nCoarse * 0.62 + nFine * 0.28 + hRand * 0.10;
        if (patch < 0.44 - rimBlend * 0.14) {
            return blackstone;
        }
        if (patch < 0.78 - rimBlend * 0.16) {
            return basalt;
        }
        return cobbledDeepslate;
    }

    private static double volcanicNoise(double x, double z, int seed) {
        double s1 = (seed & 0xFF) * 0.13;
        double s2 = ((seed >> 8) & 0xFF) * 0.17;
        double a = Math.sin(x * 1.13 + s1) * Math.cos(z * 1.07 - s2);
        double b = Math.sin((x * 0.71 - z * 0.89) * 1.65 + s2);
        double c = Math.cos((x * 0.93 + z * 0.61) * 2.35 - s1);
        return Mth.clamp(0.5 + (a * 0.45 + b * 0.35 + c * 0.20) * 0.5, 0.0, 1.0);
    }

    private static double columnHash(int x, int z, int seed) {
        int h = x * 374761393 + z * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);
        return (h & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
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
        this.entityData.set(DATA_TIME_SCALE, tag.contains("TimeScale") ? tag.getFloat("TimeScale") : 1.0f);
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
        tag.putFloat("TimeScale", this.entityData.get(DATA_TIME_SCALE));
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
        double maxY = Math.max(start.y, target.y + getRadius() * 3.5d) + r + 48.0d;
        double maxZ = Math.max(start.z, target.z) + r;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
