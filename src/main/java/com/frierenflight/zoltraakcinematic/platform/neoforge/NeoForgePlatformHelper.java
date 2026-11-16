// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.platform.neoforge;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import com.frierenflight.zoltraakcinematic.network.DefenseCastPayload;
import com.frierenflight.zoltraakcinematic.network.FlightTogglePayload;
import com.frierenflight.zoltraakcinematic.platform.IPlatformHelper;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.weapons.StaffItem;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "NeoForge 1.21.1";
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
        PacketDistributor.sendToServer(new DefenseCastPayload(mode));
    }

    @Override
    public void sendFlightToggleToServer() {
        PacketDistributor.sendToServer(new FlightTogglePayload());
    }

    @Override
    public void syncPlayerMana(ServerPlayer player, MagicData magicData) {
        PacketDistributor.sendToPlayer(player, new SyncManaPacket(magicData));
    }

    @Override
    public Item.Properties applyStaffAttributes(Item.Properties properties) {
        ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(StaffItem.BASE_ATTACK_DAMAGE_ID, 6.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(StaffItem.BASE_ATTACK_SPEED_ID, -3.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(AttributeRegistry.SPELL_POWER,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "staff_spell_power"),
                                0.45, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        EquipmentSlotGroup.MAINHAND)
                .add(com.frierenflight.zoltraakcinematic.registry.ModCinematicAttributes.ZOLTRAAK_SPELL_POWER,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "staff_zoltraak_power"),
                                0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        EquipmentSlotGroup.MAINHAND)
                .add(AttributeRegistry.MAX_MANA,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "staff_max_mana"),
                                500.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(AttributeRegistry.COOLDOWN_REDUCTION,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "staff_cooldown_reduction"),
                                0.20, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        EquipmentSlotGroup.MAINHAND)
                .add(AttributeRegistry.CAST_TIME_REDUCTION,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "staff_cast_time_reduction"),
                                com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getStaffCastSpeedBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        EquipmentSlotGroup.MAINHAND)
                .add(AttributeRegistry.MANA_REGEN,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "staff_mana_regen"),
                                com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getStaffManaRegenBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
        return properties.attributes(modifiers);
    }
}

