// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.item;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.weapons.StaffItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FrierenStaffItem extends StaffItem {
    private static final UUID SPELL_POWER_UUID = UUID.fromString("c2d4a1b0-8f92-4e3a-9762-d9c1048e9101");
    private static final UUID MAX_MANA_UUID = UUID.fromString("b3e5c2d1-9a03-4f4b-8873-ea21059f0212");
    private static final UUID COOLDOWN_REDUCTION_UUID = UUID.fromString("a4f6d3e2-0b14-4a5c-9984-fb32160a1323");
    private static final UUID ZOLTRAAK_POWER_UUID = UUID.fromString("d3e5a2b1-9f83-4a2b-8765-ca1029485721");
    private static final UUID CAST_TIME_REDUCTION_UUID = UUID.fromString("e5a7b4f3-1c25-4b6d-aa95-0c43271b2434");
    private static final UUID MANA_REGEN_UUID = UUID.fromString("f6b8c5a4-2d36-4c7e-bb06-1d54382c3545");

    public FrierenStaffItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant(),
                6.0,
                -3.0,
                createStaffAttributes());
    }

    private static Map<Attribute, AttributeModifier> createStaffAttributes() {
        Map<Attribute, AttributeModifier> map = new HashMap<>();
        map.put(AttributeRegistry.SPELL_POWER.get(),
                new AttributeModifier(SPELL_POWER_UUID, "Frieren Staff Spell Power", 0.45, AttributeModifier.Operation.MULTIPLY_BASE));
        map.put(com.frierenflight.zoltraakcinematic.registry.ModCinematicAttributes.ZOLTRAAK_SPELL_POWER.get(),
                new AttributeModifier(ZOLTRAAK_POWER_UUID, "Frieren Staff Zoltraak Power", 0.35, AttributeModifier.Operation.MULTIPLY_BASE));
        map.put(AttributeRegistry.MAX_MANA.get(),
                new AttributeModifier(MAX_MANA_UUID, "Frieren Staff Max Mana", 500.0, AttributeModifier.Operation.ADDITION));
        map.put(AttributeRegistry.COOLDOWN_REDUCTION.get(),
                new AttributeModifier(COOLDOWN_REDUCTION_UUID, "Frieren Staff Cooldown Reduction", 0.20, AttributeModifier.Operation.MULTIPLY_BASE));
        map.put(AttributeRegistry.CAST_TIME_REDUCTION.get(),
                new AttributeModifier(CAST_TIME_REDUCTION_UUID, "Frieren Staff Cast Speed",
                        com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getStaffCastSpeedBonus(),
                        AttributeModifier.Operation.MULTIPLY_BASE));
        map.put(AttributeRegistry.MANA_REGEN.get(),
                new AttributeModifier(MANA_REGEN_UUID, "Frieren Staff Mana Regen",
                        com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getStaffManaRegenBonus(),
                        AttributeModifier.Operation.MULTIPLY_BASE));
        return map;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.zoltraak_cinematic.frieren_staff.desc")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
    }
}

