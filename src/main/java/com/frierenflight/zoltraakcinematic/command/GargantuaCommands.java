// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.command;

import com.frierenflight.zoltraakcinematic.entity.GargantuaEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class GargantuaCommands {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(GargantuaCommands::onRegisterCommands);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("singularity")
                .requires(source -> source.hasPermission(2))
                .executes(context -> spawnGargantua(context.getSource())));

        dispatcher.register(Commands.literal("zoltraak")
                .then(Commands.literal("singularity")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> spawnGargantua(context.getSource()))));

        dispatcher.register(Commands.literal("gargantua")
                .requires(source -> source.hasPermission(2))
                .executes(context -> spawnGargantua(context.getSource()))
                .then(Commands.literal("night")
                        .executes(context -> setNightTime(context.getSource()))));

        dispatcher.register(Commands.literal("zoltraak")
                .then(Commands.literal("gargantua")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> spawnGargantua(context.getSource()))
                        .then(Commands.literal("night")
                                .executes(context -> setNightTime(context.getSource())))));
    }

    private static int setNightTime(CommandSourceStack source) {
        if (!(source.getLevel() instanceof ServerLevel serverLevel)) return 0;
        com.frierenflight.zoltraakcinematic.spell.GargantuaSpell.applyNightTimeOnActivation(serverLevel, true);
        source.sendSuccess(() -> Component.literal("§d[Zoltraak] §fSet world time to night ("
                + com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getNightTimeTicks() + ")."), true);
        return 1;
    }

    private static int spawnGargantua(CommandSourceStack source) {
        if (!(source.getLevel() instanceof ServerLevel serverLevel)) return 0;

        Vec3 pos;
        LivingEntity caster = null;
        Vec3 spinAxis = new Vec3(0.22d, 0.96d, 0.15d).normalize();
        if (source.getEntity() instanceof ServerPlayer player) {
            caster = player;
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0f);

            // Crosshair line-of-sight raycast
            net.minecraft.world.phys.HitResult hit = serverLevel.clip(new net.minecraft.world.level.ClipContext(
                    eye, eye.add(look.scale(56.0d)),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));

            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                Vec3 hitPos = hit.getLocation();
                double hitDist = eye.distanceTo(hitPos);
                net.minecraft.core.Direction face = (hit instanceof net.minecraft.world.phys.BlockHitResult bhr) ? bhr.getDirection() : net.minecraft.core.Direction.UP;
                if (face == net.minecraft.core.Direction.UP) {
                    pos = hitPos.add(0, net.minecraft.util.Mth.clamp(hitDist * 0.22d, 2.5d, 8.5d), 0);
                } else if (face == net.minecraft.core.Direction.DOWN) {
                    pos = hitPos.subtract(0, 2.5d, 0);
                } else {
                    pos = eye.add(look.scale(Math.max(2.0d, hitDist - 3.5d)));
                }
            } else {
                pos = eye.add(look.scale(44.0d));
            }

            // Line of sight safeguard
            net.minecraft.world.phys.HitResult los = serverLevel.clip(new net.minecraft.world.level.ClipContext(
                    eye, pos,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            if (los.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                pos = eye.add(look.scale(Math.max(1.5d, eye.distanceTo(los.getLocation()) - 1.5d)));
            }

            Vec3 toHoleDir = pos.subtract(eye).normalize();
            Vec3 worldUp = new Vec3(0.0d, 1.0d, 0.0d);
            Vec3 holeRight = toHoleDir.cross(worldUp);
            holeRight = holeRight.lengthSqr() < 1.0e-5d ? new Vec3(1.0d, 0.0d, 0.0d) : holeRight.normalize();
            Vec3 holeUp = holeRight.cross(toHoleDir).normalize();
            double rollRad = Math.toRadians(3.8d);
            Vec3 tiltedUp = holeUp.scale(Math.cos(rollRad)).add(holeRight.scale(Math.sin(rollRad)));
            double grazingRad = Math.toRadians(5.8d);
            spinAxis = tiltedUp.scale(Math.cos(grazingRad)).subtract(toHoleDir.scale(Math.sin(grazingRad))).normalize();
        } else {
            pos = source.getPosition().add(0, 22.0d, 0);
        }

        float spellPower = 500.0f;
        if (caster != null) {
            var schoolHolder = com.frierenflight.zoltraakcinematic.registry.ModCinematicSchools.BLACK_HOLE;
            if (schoolHolder != null && schoolHolder.isBound()) {
                spellPower = (float) (500.0 * schoolHolder.get().getPowerFor(caster));
            }
        }

        com.frierenflight.zoltraakcinematic.spell.GargantuaSpell.applyNightTimeOnActivation(serverLevel, false);

        GargantuaEntity blackHole = new GargantuaEntity(serverLevel, caster, pos, spinAxis, spellPower);
        serverLevel.addFreshEntity(blackHole);

        source.sendSuccess(() -> Component.literal("§d[Zoltraak Cinematic] §fManifested Astral Singularity!"), true);
        return 1;
    }
}
