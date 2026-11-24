// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.entity;

import com.frierenflight.zoltraakcinematic.ZoltraakDamage;
import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicEntities;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicSchools;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.GameRules;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import com.frierenflight.zoltraakcinematic.platform.Services;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;

/**
 * 🌌 GargantuaEntity
 * A supermassive rotating Kerr black hole with general relativistic gravitational lensing,
 * volumetric accretion disk, photon rings, and cataclysmic event horizon collapse.
 */
public class GargantuaEntity extends Entity implements TraceableEntity, OwnableEntity {

    public static final int LIFETIME_TICKS = 1200;      // 60.0 seconds (1 minute total!)
    public static final int TEAR_END_TICK = 60;         // 3.0s opening tear & expansion
    public static final int HOLD_END_TICK = 1060;       // 53.0s active pull & accretion vortex
    public static final int CRITICAL_START_TICK = 1060; // 53.0s criticality onset
    public static final int CRITICAL_END_TICK = 1100;   // 55.0s criticality peak & implosion
    public static final int BLAST_TICK = 1100;          // 55.0s Supernova detonation!
    public static final int FADE_START_TICK = 1115;     // 55.75s cosmic fade begin

    public static final float GRAVITATIONAL_RADIUS = 4.0f; // r_g in blocks
    public static final float SPIN = 0.6f;                // a/M
    public static final float HORIZON_RADIUS = 1.8f;      // 1.8 r_g = 7.2 blocks
    public static final float SHADOW_RADIUS = 5.196f;     // 5.196 r_g = 20.8 blocks
    public static final float DISK_INNER_RADIUS = 3.83f;  // ISCO = 15.3 blocks
    public static final float DISK_OUTER_RADIUS = 13.5f;  // 54.0 blocks
    public static final double PULL_RADIUS = 100.0d;     // Inward gravity field (default 100 blocks)
    public static final double BLAST_RADIUS = 32.0d;      // Apocalyptic blast radius
    private static final double PULL_ACCELERATION = 0.12d;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_AXIS_X =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AXIS_Y =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AXIS_Z =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SWALLOWED =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DISPLAY =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_SPELL_POWER =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MAX_LIFETIME =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.INT);

    private UUID casterUuid;
    private boolean blastResolved = false;
    private boolean clientBlastTriggered = false;

    private boolean clientCutsceneTriggered = false;
    private int tornBlockCount = 0;
    private int liveDebrisCount = 0;
    private final Map<BlockPos, BlockState> tornBlocksHistory = new LinkedHashMap<>();

    public GargantuaEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    public GargantuaEntity(Level level, LivingEntity caster, Vec3 center, Vec3 spinAxis) {
        this(level, caster, center, spinAxis, 500.0f);
    }

    public GargantuaEntity(Level level, LivingEntity caster, Vec3 center, Vec3 spinAxis, float spellPower) {
        this(level, caster, center, spinAxis, spellPower, LIFETIME_TICKS);
    }

    public GargantuaEntity(Level level, LivingEntity caster, Vec3 center, Vec3 spinAxis, float spellPower, int customLifetime) {
        this(ModCinematicEntities.GARGANTUA.get(), level);
        setPos(center.x, center.y, center.z);
        this.entityData.set(DATA_MAX_LIFETIME, Math.max(60, customLifetime));
        configure(caster, center, spinAxis, level.random.nextInt(), spellPower);
    }

    public void configure(LivingEntity caster, Vec3 center, Vec3 spinAxis, int seed) {
        configure(caster, center, spinAxis, seed, 500.0f);
    }

    public void configure(LivingEntity caster, Vec3 center, Vec3 spinAxis, int seed, float spellPower) {
        if (caster != null) {
            this.casterUuid = caster.getUUID();
            this.entityData.set(DATA_CASTER_ID, caster.getId());
        }
        this.entityData.set(DATA_START_GAME_TICK, this.level().getGameTime());
        this.entityData.set(DATA_SEED, seed);
        this.entityData.set(DATA_SPELL_POWER, Math.max(1.0f, spellPower));
        Vec3 normAxis = (spinAxis != null && spinAxis.lengthSqr() > 0.001) ? spinAxis.normalize() : new Vec3(0.055d, 0.997d, 0.055d).normalize();
        this.entityData.set(DATA_AXIS_X, (float) normAxis.x);
        this.entityData.set(DATA_AXIS_Y, (float) normAxis.y);
        this.entityData.set(DATA_AXIS_Z, (float) normAxis.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_CASTER_ID, -1);
        builder.define(DATA_START_GAME_TICK, -1L);
        builder.define(DATA_SEED, 0);
        builder.define(DATA_AXIS_X, 0.0f);
        builder.define(DATA_AXIS_Y, 1.0f);
        builder.define(DATA_AXIS_Z, 0.0f);
        builder.define(DATA_SWALLOWED, 0);
        builder.define(DATA_DISPLAY, false);
        builder.define(DATA_SPELL_POWER, 500.0f);
        builder.define(DATA_MAX_LIFETIME, LIFETIME_TICKS);
    }

    public int getCasterId() {
        return this.entityData.get(DATA_CASTER_ID);
    }

    public float getSpellPower() {
        return this.entityData.get(DATA_SPELL_POWER);
    }

    public void setSpellPower(float power) {
        this.entityData.set(DATA_SPELL_POWER, Math.max(1.0f, power));
    }

    public int getMaxLifetimeTicks() {
        return this.entityData.get(DATA_MAX_LIFETIME);
    }

    public void setMaxLifetimeTicks(int ticks) {
        this.entityData.set(DATA_MAX_LIFETIME, Math.max(60, ticks));
    }

    public int getTearEndTick() {
        int max = getMaxLifetimeTicks();
        if (max == LIFETIME_TICKS) return TEAR_END_TICK;
        return Math.max(20, Math.round(max * 0.10f));
    }

    public int getBlastTick() {
        int max = getMaxLifetimeTicks();
        if (max == LIFETIME_TICKS) return BLAST_TICK;
        return Math.max(getTearEndTick() + 40, Math.round(max * 0.875f));
    }

    public int getCriticalStartTick() {
        int max = getMaxLifetimeTicks();
        if (max == LIFETIME_TICKS) return CRITICAL_START_TICK;
        return Math.max(getTearEndTick() + 20, getBlastTick() - 30);
    }

    public int getHoldEndTick() {
        return getCriticalStartTick();
    }

    public int getCriticalEndTick() {
        return getBlastTick();
    }

    public int getFadeStartTick() {
        int max = getMaxLifetimeTicks();
        if (max == LIFETIME_TICKS) return FADE_START_TICK;
        return Math.min(max - 10, getBlastTick() + 10);
    }

    public static boolean isEntityImmuneToVortex(Entity entity, LivingEntity caster) {
        if (entity == null || entity == caster) return true;
        if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand) return true;
        if (entity instanceof Player p && (p.isCreative() || p.isSpectator())) return true;
        if (caster instanceof com.frierenflight.zoltraakcinematic.entity.boss.QualBossEntity &&
            entity instanceof com.frierenflight.zoltraakcinematic.entity.boss.QualBossEntity) {
            return true;
        }
        return false;
    }

    @Override
    public UUID getOwnerUUID() {
        return this.casterUuid;
    }

    @Override
    public LivingEntity getOwner() {
        if (this.level() instanceof ServerLevel serverLevel) {
            return resolveCaster(serverLevel);
        }
        int id = getCasterId();
        if (id >= 0) {
            Entity e = this.level().getEntity(id);
            if (e instanceof LivingEntity living) return living;
        }
        return null;
    }

    public int getSwallowedCount() {
        return this.entityData.get(DATA_SWALLOWED);
    }

    public void noteSwallowed() {
        this.entityData.set(DATA_SWALLOWED, this.getSwallowedCount() + 1);
    }

    public Vec3 spinAxis() {
        return new Vec3(
                this.entityData.get(DATA_AXIS_X),
                this.entityData.get(DATA_AXIS_Y),
                this.entityData.get(DATA_AXIS_Z)
        );
    }

    public int getTimelineAgeTicks() {
        return this.tickCount;
    }

    public float getVisualAgeTicks(float partialTicks) {
        long start = this.entityData.get(DATA_START_GAME_TICK);
        if (start <= 0) {
            return (float) this.tickCount + partialTicks;
        }
        float age = (float) (this.level().getGameTime() - start) + partialTicks;
        if (age < 0.0f || age > (float) (this.tickCount + 40)) {
            return (float) this.tickCount + partialTicks;
        }
        return Math.max(0.0f, age);
    }

    public Vec3 centre(float partialTicks) {
        double x = Mth.lerp((double) partialTicks, this.xo, this.getX());
        double y = Mth.lerp((double) partialTicks, this.yo, this.getY());
        double z = Mth.lerp((double) partialTicks, this.zo, this.getZ());
        return new Vec3(x, y, z);
    }

    public float gravitationalRadius(float partialTicks) {
        float age = getVisualAgeTicks(partialTicks);
        if (age <= 0.0f) return 0.15f;
        int tearEnd = getTearEndTick();
        int holdEnd = getHoldEndTick();
        int critEnd = getCriticalEndTick();
        if (age < (float) tearEnd) {
            float p = smoothstep(age / (float) tearEnd);
            return Mth.clamp(0.15f + (GRAVITATIONAL_RADIUS - 0.15f) * p, 0.15f, GRAVITATIONAL_RADIUS);
        }
        if (age <= (float) holdEnd) {
            return GRAVITATIONAL_RADIUS;
        }
        if (age <= (float) critEnd) {
            // Accelerating gravitational pinch inward before relativistic collapse snap
            float p = Mth.clamp((age - (float) holdEnd) / Math.max(1.0f, (float) critEnd - (float) holdEnd), 0.0f, 1.0f);
            float pinch = p * p * (0.35f + 0.65f * p);
            return Mth.clamp(GRAVITATIONAL_RADIUS - 1.65f * pinch, 2.35f, GRAVITATIONAL_RADIUS);
        }
        // Subtle spatial relaxation after core annihilation (keeps shader blast coordinate frame stable, never balloons the disk)
        float bp = blastProgress(partialTicks);
        float recoil = 1.0f - (float) Math.pow(1.0f - bp, 2.2);
        return 2.35f + 0.75f * recoil;
    }

    public float opened(float partialTicks) {
        float age = getVisualAgeTicks(partialTicks);
        if (age <= 0.0f) return 0.0f;
        int tearEnd = getTearEndTick();
        int blastTick = getBlastTick();
        int maxLife = getMaxLifetimeTicks();
        if (age < (float) tearEnd) {
            float progress = age / (float) tearEnd;
            return Mth.clamp(smoothstep(progress), 0.0f, 1.0f);
        }
        float postDuration = Math.max(20.0f, (float) (maxLife - blastTick));
        float dissolveStart = (float) blastTick + postDuration * 0.84f;
        if (age <= dissolveStart) {
            return 1.0f; // Keep spacetime field fully open across collapse snap & 3D shockwave expansion
        }
        if (age >= (float) maxLife) {
            return 0.0f;
        }
        float dissolveProgress = (age - dissolveStart) / Math.max(1.0f, (float) maxLife - dissolveStart);
        return Mth.clamp(1.0f - smoothstep(dissolveProgress), 0.0f, 1.0f);
    }

    public float criticality(float partialTicks) {
        float age = getVisualAgeTicks(partialTicks);
        int holdEnd = getHoldEndTick();
        int critEnd = getCriticalEndTick();
        if (age <= (float) holdEnd) return 0.0f;
        if (age >= (float) critEnd) return 1.0f;
        float p = Mth.clamp((age - (float) holdEnd) / Math.max(1.0f, (float) critEnd - (float) holdEnd), 0.0f, 1.0f);
        return Mth.clamp(p * p * (1.20f - 0.20f * p), 0.0f, 1.0f);
    }

    public boolean isBlasting(float partialTicks) {
        return getVisualAgeTicks(partialTicks) >= (float) getBlastTick();
    }

    public float blastProgress(float partialTicks) {
        float age = getVisualAgeTicks(partialTicks);
        int blastTick = getBlastTick();
        if (age < (float) blastTick) return 0.0f;
        float duration = Math.max(20.0f, (float) (getMaxLifetimeTicks() - blastTick));
        return Mth.clamp((age - (float) blastTick) / duration, 0.0f, 1.0f);
    }

    public float blastFlash(float partialTicks) {
        if (!isBlasting(partialTicks)) return 0.0f;
        float p = blastProgress(partialTicks);
        return (1.0f - p) * (1.0f - p);
    }

    public float fade(float partialTicks) {
        float age = getVisualAgeTicks(partialTicks);
        int fadeStart = getFadeStartTick();
        int maxLife = getMaxLifetimeTicks();
        if (age <= (float) fadeStart) return 0.0f;
        return Mth.clamp((age - (float) fadeStart) / ((float) maxLife - (float) fadeStart), 0.0f, 1.0f);
    }

    public float brightness(float partialTicks) {
        float age = getVisualAgeTicks(partialTicks);
        int tearEnd = getTearEndTick();
        int holdEnd = getHoldEndTick();
        int critEnd = getCriticalEndTick();
        int maxLife = getMaxLifetimeTicks();
        if (age <= (float) tearEnd) {
            return 0.5f + 0.5f * opened(partialTicks);
        }
        if (age <= (float) holdEnd) {
            return 1.0f;
        }
        if (age <= (float) critEnd) {
            return 1.0f + 0.45f * criticality(partialTicks);
        }
        if (age <= (float) maxLife) {
            float bp = blastProgress(partialTicks);
            return (1.0f + 0.25f * (1.0f - bp)) * opened(partialTicks);
        }
        return 0.0f;
    }

    private static float smoothstep(float x) {
        float t = Mth.clamp(x, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    @Override
    public boolean isPickable() {
        return false;
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
        Vec3 c = centre(1.0f);
        double r = 256.0;
        return new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide() && Services.PLATFORM.isClient()) {
            ClientHandler.onClientTick(this);
        }

        // Server-side Audio Cues (100% Custom Mod Sounds)
        if (!this.level().isClientSide()) {
            Vec3 c = centre(1.0f);

            // 1. Spacetime Rupture Opening (Tick 1)
            if (this.tickCount == 1) {
                this.level().playSound(null, c.x, c.y, c.z, ModCinematicSounds.SINGULARITY_CHARGE.get(), SoundSource.WEATHER, 4.0f, 0.95f);
            }

            // 2. Active Cosmic Hum (Ticks during active tear/hold)
            int tearEnd = getTearEndTick();
            int holdEnd = getHoldEndTick();
            if (this.tickCount > tearEnd && this.tickCount <= holdEnd && this.tickCount % 40 == 0) {
                this.level().playSound(null, c.x, c.y, c.z, ModCinematicSounds.SINGULARITY_ACTIVE.get(), SoundSource.WEATHER, 2.5f, 1.0f);
            }
        }

        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            // Anti-Burial Levitation (Auto Ground-Clearance System)
            applyGroundClearance(serverLevel);

            // Surface block tearing during active phase
            if (this.tickCount >= getTearEndTick() && this.tickCount <= getHoldEndTick()) {
                tearSurfaceBlocks(serverLevel);
            }

            // Gravitational pull applies until criticality ends
            if (this.tickCount <= getCriticalEndTick()) {
                applyPull(serverLevel);
            }

            // Supernova Detonation at blast tick
            if (this.tickCount >= getBlastTick() && !this.blastResolved) {
                resolveBlast(serverLevel);
                this.blastResolved = true;
            }

            if (this.tickCount >= getMaxLifetimeTicks()) {
                reconstructSurfaceBlocks(serverLevel);
                this.discard();
                return;
            }
        }

        if (this.tickCount >= getMaxLifetimeTicks()) {
            this.discard();
        }
    }

    /**
     * Anti-Burial Levitation (Auto Ground-Clearance System):
     * In open sky, elevates the supermassive singularity above solid terrain.
     * In caves, dungeons, or indoors, respects overhead ceilings so Gargantua never teleports
     * through roofs or onto the surface above a cave.
     */
    private void applyGroundClearance(ServerLevel level) {
        if (this.tickCount > 70) return;

        Vec3 pos = this.position();
        BlockPos blockPos = BlockPos.containing(pos.x, pos.y, pos.z);

        // 1. If spawned inside solid blocks, search locally (downward first, then upward)
        // for the nearest air pocket without scanning through cave roofs to the surface
        BlockState bs = level.getBlockState(blockPos);
        if (!bs.isAir() && bs.blocksMotion()) {
            for (int dy = 1; dy <= 24; dy++) {
                BlockPos downPos = blockPos.below(dy);
                if (downPos.getY() > level.getMinBuildHeight() && level.getBlockState(downPos).isAir()) {
                    this.setPos(pos.x, downPos.getY() + 0.5d, pos.z);
                    return;
                }
                BlockPos upPos = blockPos.above(dy);
                if (upPos.getY() < level.getMaxBuildHeight() && level.getBlockState(upPos).isAir()) {
                    this.setPos(pos.x, upPos.getY() + 0.5d, pos.z);
                    return;
                }
            }
            return;
        }

        // 2. Trace both downward and upward to detect enclosed rooms/caves vs open sky
        Vec3 downTrace = pos.subtract(0.0d, 55.0d, 0.0d);
        HitResult groundHit = level.clip(new ClipContext(
                pos, downTrace,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this
        ));

        Vec3 upTrace = pos.add(0.0d, 40.0d, 0.0d);
        HitResult ceilingHit = level.clip(new ClipContext(
                pos, upTrace,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this
        ));

        if (ceilingHit.getType() != HitResult.Type.MISS) {
            // Enclosed ceiling (cave, tunnel, indoor room, or Nether roof):
            // Keep singularity strictly inside the vertical gap and NEVER teleport above the ceiling!
            double ceilingY = ceilingHit.getLocation().y;
            double groundY = (groundHit.getType() != HitResult.Type.MISS)
                    ? groundHit.getLocation().y
                    : (pos.y - 4.0d);
            double availableGap = Math.max(0.0d, ceilingY - groundY);
            double minSafeY = groundY + Math.min(2.0d, availableGap * 0.45d);
            double maxSafeY = Math.max(minSafeY, ceilingY - Math.min(4.5d, availableGap * 0.45d));

            if (pos.y < minSafeY || pos.y > maxSafeY) {
                this.setPos(pos.x, Mth.clamp(pos.y, minSafeY, maxSafeY), pos.z);
            }
            return;
        }

        if (groundHit.getType() != HitResult.Type.MISS) {
            double groundY = groundHit.getLocation().y;
            double currentClearance = pos.y - groundY;
            double desiredClearance = 34.0d;

            if (currentClearance < desiredClearance) {
                double diff = desiredClearance - currentClearance;
                if (this.tickCount <= 2) {
                    // On initial spawn in open sky, adjust to safe altitude
                    this.setPos(pos.x, pos.y + diff, pos.z);
                } else {
                    // Smooth celestial ascension during opening seconds
                    double step = Math.min(1.4d, Math.max(0.15d, diff * 0.16d));
                    this.setPos(pos.x, pos.y + step, pos.z);
                }
            }
        }
    }

    @Override
    public void onClientRemoval() {
        super.onClientRemoval();
        if (Services.PLATFORM.isClient()) {
            ClientHandler.onClientRemove(this);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            reconstructSurfaceBlocks(serverLevel);
        }
        super.remove(reason);
        if (this.level().isClientSide() && Services.PLATFORM.isClient()) {
            ClientHandler.onClientRemove(this);
        }
    }

    private void reconstructSurfaceBlocks(ServerLevel level) {
        if (!ZoltraakCinematicConfig.isReconstructBlocksEnabled() || tornBlocksHistory.isEmpty()) return;
        for (Map.Entry<BlockPos, BlockState> entry : tornBlocksHistory.entrySet()) {
            BlockPos bp = entry.getKey();
            if (level.getBlockState(bp).isAir()) {
                level.setBlock(bp, entry.getValue(), 3);
            }
        }
        tornBlocksHistory.clear();
    }

    private static final class ClientHandler {
        private static void onClientTick(GargantuaEntity entity) {
            com.frierenflight.zoltraakcinematic.client.renderer.GargantuaPostProcessor.registerClientInstance(entity);

            if (!entity.clientCutsceneTriggered) {
                entity.clientCutsceneTriggered = true;
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    boolean isCaster = (entity.getCasterId() == mc.player.getId()) ||
                            (entity.getOwnerUUID() != null && entity.getOwnerUUID().equals(mc.player.getUUID())) ||
                            (entity.getCasterId() == -1 && entity.distanceToSqr(mc.player) <= (240.0 * 240.0));
                    if (isCaster) {
                        com.frierenflight.zoltraakcinematic.client.cinematic.GargantuaCutscenes.playSingularity(mc.player, entity);
                    }
                }
            }

            // Blast cue is handled cohesively by GargantuaScreenFx (audio) and the 4-pass shader Impact Frame
            if (entity.isBlasting(0.0f) && !entity.clientBlastTriggered) {
                entity.clientBlastTriggered = true;
            }
        }

        private static void onClientRemove(GargantuaEntity entity) {
            com.frierenflight.zoltraakcinematic.client.renderer.GargantuaPostProcessor.unregisterClientInstance(entity);
        }
    }

    private void tearSurfaceBlocks(ServerLevel level) {
        if (!ZoltraakCinematicConfig.isTearBlocksEnabled()) return;

        if (ZoltraakCinematicConfig.isRespectMobGriefing() &&
                !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }

        // Limit both total torn blocks and simultaneously orbiting debris to protect server TPS and client chunk rebuilds
        int maxTotal = ZoltraakCinematicConfig.getMaxDebrisBlocks();
        int maxLive = Math.min(40, maxTotal);
        if (this.tornBlockCount >= maxTotal || this.liveDebrisCount >= maxLive) return;
        if (this.tickCount % 5 != 0) return;

        Vec3 center = centre(1.0f);
        double minRadius = 2.5d;
        double maxRadius = 18.0d;
        Vec3 axis = spinAxis();

        int attempts = 2;
        for (int i = 0; i < attempts; i++) {
            if (this.tornBlockCount >= maxTotal || this.liveDebrisCount >= maxLive) break;
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double r = minRadius + Math.sqrt(this.random.nextDouble()) * (maxRadius - minRadius);

            int x = Mth.floor(center.x + Math.cos(angle) * r);
            int z = Mth.floor(center.z + Math.sin(angle) * r);

            BlockPos topPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z)).below();
            BlockPos targetPos = topPos;

            if (!canTearBlock(level, targetPos)) {
                targetPos = topPos.below();
                if (!canTearBlock(level, targetPos)) continue;
            }

            double normR = r / maxRadius;
            double maxDepth = Math.max(1.0, 4.0 * (1.0 - normR * normR));
            double groundY = center.y - 14.0;
            if (targetPos.getY() < groundY - maxDepth) continue;

            BlockState state = level.getBlockState(targetPos);
            this.tornBlockCount++;
            this.liveDebrisCount++;
            if (ZoltraakCinematicConfig.isReconstructBlocksEnabled()) {
                tornBlocksHistory.putIfAbsent(targetPos.immutable(), state);
            }

            FallingBlockEntity falling = FallingBlockEntity.fall(level, targetPos, state);
            falling.dropItem = false;
            falling.disableDrop();
            falling.time = 1;
            falling.setNoGravity(true);
            falling.addTag("gargantua_debris");

            Vec3 delta = center.subtract(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
            Vec3 inward = delta.normalize();
            Vec3 tangent = axis.cross(inward).normalize();

            Vec3 initVel = new Vec3(
                    tangent.x * 0.35 + inward.x * 0.15,
                    0.42 + this.random.nextDouble() * 0.15,
                    tangent.z * 0.35 + inward.z * 0.15
            );
            falling.setDeltaMovement(initVel);
            falling.hurtMarked = true;
        }
    }

    private boolean canTearBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        if (level.getBlockEntity(pos) != null) return false;
        if (pos.getY() <= level.getMinBuildHeight() + 2) return false;
        return true;
    }

    private void applyPull(ServerLevel level) {
        Vec3 center = centre(1.0f);
        double eventHorizonDist = (double) (HORIZON_RADIUS * GRAVITATIONAL_RADIUS); // 7.2 blocks
        double diskRadius = 32.0d; // Continuous damage zone across the entire accretion vortex
        double pullDist = ZoltraakCinematicConfig.getPullRadius(); // 100.0 blocks
        double pullDistSq = pullDist * pullDist;
        LivingEntity caster = resolveCaster(level);
        Vec3 axis = spinAxis();

        AABB box = new AABB(center.x - pullDist, center.y - pullDist, center.z - pullDist,
                center.x + pullDist, center.y + pullDist, center.z + pullDist);

        List<Entity> list = level.getEntities(this, box, e -> e.isAlive() && !isEntityImmuneToVortex(e, caster));
        float tidalPct = (float) ZoltraakCinematicConfig.getTidalDamage();
        double baseAccel = ZoltraakCinematicConfig.getPullStrength();
        boolean doDamageTick = (this.tickCount % 8 == 0);
        Set<UUID> damagedThisTick = doDamageTick ? new HashSet<>() : null;
        int seenDebris = 0;

        for (Entity e : list) {
            if (e instanceof FallingBlockEntity falling) {
                if (falling.tickCount > 240) {
                    falling.discard();
                    continue;
                }
                seenDebris++;
            }

            // Distance measured to closest point on bounding box surface (crucial for giant bosses)
            double boxDistSq = e.getBoundingBox().distanceToSqr(center);
            if (boxDistSq > pullDistSq) continue;
            double boxDist = Math.sqrt(boxDistSq);

            Vec3 delta = center.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
            double d = delta.length();
            if (d < 0.001) d = 0.001;

            // --- DAMAGE LOGIC (Accretion Disk & Event Horizon) ---
            if (doDamageTick && boxDist <= diskRadius) {
                LivingEntity targetLiving = ZoltraakDamage.resolveLivingTarget(e);

                if (targetLiving != null && targetLiving.isAlive() && !isEntityImmuneToVortex(targetLiving, caster)
                        && damagedThisTick.add(targetLiving.getUUID())) {
                    Entity damageCauser = (targetLiving instanceof Player && caster != null) ? caster : this;
                    DamageSource damageSource = GargantuaDamage.source(level, this, damageCauser);
                    if (caster instanceof Player player) {
                        targetLiving.setLastHurtByPlayer(player);
                    }

                    float spellPowerMult = Math.max(0.1f, getSpellPower() / 500.0f);
                    float maxTidalCap = 200.0f * spellPowerMult;
                    float dmg;
                    if (boxDist <= eventHorizonDist) {
                        dmg = Math.max(35.0f * spellPowerMult, targetLiving.getMaxHealth() * (tidalPct * spellPowerMult));
                    } else {
                        double proximity = (diskRadius - boxDist) / (diskRadius - eventHorizonDist);
                        float plasmaFactor = (float) (0.30 + 0.70 * proximity);
                        dmg = Math.max(20.0f * spellPowerMult, targetLiving.getMaxHealth() * (tidalPct * plasmaFactor * spellPowerMult));
                    }
                    dmg = Math.min(maxTidalCap, dmg);

                    if (ZoltraakDamage.apply(targetLiving, dmg, damageSource, ModCinematicSchools.BLACK_HOLE.get(), true)) {
                        noteSwallowed();
                    }
                }
            }

            // Stagger server->client motion packet syncing for debris and items to prevent network floods
            boolean syncMotionPacket = (e instanceof Player)
                    || (e instanceof LivingEntity)
                    || (((this.tickCount + e.getId()) % 3) == 0);

            // --- PHYSICAL MOVEMENT & ACCELERATION LOGIC ---
            if (d <= eventHorizonDist) {
                if (e instanceof FallingBlockEntity falling) {
                    noteSwallowed();
                    seenDebris = Math.max(0, seenDebris - 1);
                    if ((this.tickCount & 1) == 0) {
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, falling.getBlockState()),
                                falling.getX(), falling.getY(), falling.getZ(),
                                6, 0.35, 0.35, 0.35, 0.12);
                    }
                    falling.discard();
                    continue;
                }
                if (e instanceof Projectile) {
                    noteSwallowed();
                    e.discard();
                    continue;
                }
                if (e instanceof ItemEntity item) {
                    item.setDeltaMovement(item.getDeltaMovement().scale(0.85));
                    continue;
                }
                Vec3 inward = delta.normalize().scale(0.15);
                e.setDeltaMovement(e.getDeltaMovement().scale(0.50).add(inward));
                e.hurtMarked = true;
            } else {
                if (e instanceof FallingBlockEntity falling) {
                    falling.time = 1;
                    falling.setNoGravity(true);

                    Vec3 inward = delta.scale(1.0 / d);
                    Vec3 tangent = axis.cross(inward);
                    double tanLen = tangent.length();
                    if (tanLen > 1.0e-4) {
                        tangent = tangent.scale(1.0 / tanLen);
                    }

                    double proximity = Mth.clamp((pullDist - d) / (pullDist - eventHorizonDist), 0.0, 1.0);
                    double inwardSpeed = 0.25 + proximity * 0.55;
                    double tangentSpeed = 0.40 + proximity * 0.85;

                    double verticalDist = center.y - (falling.getY() + 0.5);
                    double verticalSpeed = Mth.clamp(verticalDist * 0.12, -0.4, 0.4);

                    Vec3 targetVel = new Vec3(
                            inward.x * inwardSpeed + tangent.x * tangentSpeed,
                            verticalSpeed + inward.y * (inwardSpeed * 0.5),
                            inward.z * inwardSpeed + tangent.z * tangentSpeed
                    );

                    falling.setDeltaMovement(falling.getDeltaMovement().scale(0.60).add(targetVel.scale(0.40)));
                    if (syncMotionPacket) {
                        falling.hurtMarked = true;
                    }
                    continue;
                }

                double proximity = (pullDist - d) / pullDist;
                double strength = baseAccel * (1.0 + proximity * 3.5);
                Vec3 accel = delta.scale(strength / d);

                if (e instanceof Projectile) {
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.85).add(accel.scale(2.5)));
                } else if (e instanceof ItemEntity item) {
                    item.setDeltaMovement(item.getDeltaMovement().scale(0.92).add(accel));
                    if (syncMotionPacket) {
                        item.hurtMarked = true;
                    }
                } else {
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.92).add(accel));
                    e.hurtMarked = true;
                }
            }
        }
        this.liveDebrisCount = seenDebris;
    }

    private void resolveBlast(ServerLevel level) {
        Vec3 center = centre(1.0f);
        LivingEntity caster = resolveCaster(level);

        // Thunderous Apocalyptic Supernova Sound (Custom Collapse Explosion)
        level.playSound(null, center.x, center.y, center.z, ModCinematicSounds.SINGULARITY_EXPLODE.get(), SoundSource.WEATHER, 8.0f, 0.85f);

        double blastRadius = ZoltraakCinematicConfig.getBlastRadius();
        AABB blastBox = new AABB(center.x - blastRadius, center.y - blastRadius, center.z - blastRadius,
                center.x + blastRadius, center.y + blastRadius, center.z + blastRadius);

        // Vaporize any remaining swirling debris at detonation
        List<FallingBlockEntity> debrisList = level.getEntitiesOfClass(FallingBlockEntity.class, blastBox,
                f -> f.getTags().contains("gargantua_debris"));
        for (FallingBlockEntity debris : debrisList) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, debris.getBlockState()),
                    debris.getX(), debris.getY(), debris.getZ(), 8, 0.4, 0.4, 0.4, 0.2);
            debris.discard();
        }

        List<Entity> list = level.getEntities(this, blastBox, e -> e.isAlive() && !isEntityImmuneToVortex(e, caster));
        Set<UUID> hitTargets = new HashSet<>();

        float spellPowerMult = Math.max(0.1f, getSpellPower() / 500.0f);
        float baseBlast = ((float) ZoltraakCinematicConfig.getBlastDamage() * spellPowerMult);
        float maxSwallowedBonus = baseBlast * 1.0f; // Scale up to +100% bonus damage based on swallowed mass
        float swallowedBonus = Math.min((float) getSwallowedCount() * 10.0f * spellPowerMult, maxSwallowedBonus);
        float baseDamage = baseBlast + swallowedBonus;

        for (Entity e : list) {
            LivingEntity target = ZoltraakDamage.resolveLivingTarget(e);
            if (target == null || !target.isAlive() || isEntityImmuneToVortex(target, caster)) continue;
            if (!hitTargets.add(target.getUUID())) continue;

            Entity damageCauser = (target instanceof Player && caster != null) ? caster : this;
            DamageSource damageSource = GargantuaDamage.source(level, this, damageCauser);
            if (caster instanceof Player player) {
                target.setLastHurtByPlayer(player);
            }

            double dist = Math.sqrt(target.getBoundingBox().distanceToSqr(center));
            float falloff = (float) Math.max(0.25, 1.0 - dist / blastRadius);
            float finalDamage = baseDamage * falloff;
            if (ZoltraakDamage.apply(target, finalDamage, damageSource, ModCinematicSchools.BLACK_HOLE.get(), false)) {
                Vec3 push = target.position().subtract(center).normalize().scale(3.2 * falloff);
                target.setDeltaMovement(target.getDeltaMovement().add(push));
                target.hurtMarked = true;
            }
        }
    }

    private LivingEntity resolveCaster(ServerLevel level) {
        if (this.casterUuid != null) {
            Entity e = level.getEntity(this.casterUuid);
            if (e instanceof LivingEntity living) return living;
        }
        int id = getCasterId();
        if (id >= 0) {
            Entity e = level.getEntity(id);
            if (e instanceof LivingEntity living) return living;
        }
        return null;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Caster")) {
            this.casterUuid = tag.getUUID("Caster");
        }
        if (tag.contains("Swallowed")) {
            this.entityData.set(DATA_SWALLOWED, tag.getInt("Swallowed"));
        }
        if (tag.contains("SpellPower")) {
            setSpellPower(tag.getFloat("SpellPower"));
        }
        if (tag.contains("MaxLifetime")) {
            setMaxLifetimeTicks(tag.getInt("MaxLifetime"));
        }
        if (tag.contains("ElapsedTicks")) {
            int elapsed = Math.max(0, tag.getInt("ElapsedTicks"));
            this.tickCount = elapsed;
            if (this.level() != null) {
                this.entityData.set(DATA_START_GAME_TICK, Math.max(1L, this.level().getGameTime() - elapsed));
            }
        }
        if (tag.contains("Seed")) {
            this.entityData.set(DATA_SEED, tag.getInt("Seed"));
        }
        if (tag.contains("AxisX") && tag.contains("AxisY") && tag.contains("AxisZ")) {
            Vec3 axis = new Vec3(tag.getFloat("AxisX"), tag.getFloat("AxisY"), tag.getFloat("AxisZ"));
            Vec3 normAxis = axis.lengthSqr() > 0.001 ? axis.normalize() : new Vec3(0.0, 1.0, 0.0);
            this.entityData.set(DATA_AXIS_X, (float) normAxis.x);
            this.entityData.set(DATA_AXIS_Y, (float) normAxis.y);
            this.entityData.set(DATA_AXIS_Z, (float) normAxis.z);
        }
        if (tag.contains("Display")) {
            this.entityData.set(DATA_DISPLAY, tag.getBoolean("Display"));
        }
        if (tag.contains("BlastResolved")) {
            this.blastResolved = tag.getBoolean("BlastResolved");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.casterUuid != null) {
            tag.putUUID("Caster", this.casterUuid);
        }
        tag.putInt("Swallowed", getSwallowedCount());
        tag.putFloat("SpellPower", getSpellPower());
        tag.putInt("MaxLifetime", getMaxLifetimeTicks());
        tag.putInt("ElapsedTicks", this.tickCount);
        tag.putInt("Seed", this.entityData.get(DATA_SEED));
        tag.putFloat("AxisX", this.entityData.get(DATA_AXIS_X));
        tag.putFloat("AxisY", this.entityData.get(DATA_AXIS_Y));
        tag.putFloat("AxisZ", this.entityData.get(DATA_AXIS_Z));
        tag.putBoolean("Display", this.entityData.get(DATA_DISPLAY));
        tag.putBoolean("BlastResolved", this.blastResolved);
    }
}
