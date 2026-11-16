// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.item;

import com.frierenflight.zoltraakcinematic.platform.Services;
import io.redspace.ironsspellbooks.item.weapons.StaffItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Frieren's Staff - Iconic magic weapon with powerful Iron's Spells attributes:
 * - Spell Power: +45%
 * - Max Mana: +500
 * - Cooldown Reduction: -20% (+0.20 reduction)
 */
public class FrierenStaffItem extends StaffItem {

    public FrierenStaffItem() {
        super(Services.PLATFORM.applyStaffAttributes(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.zoltraak_cinematic.frieren_staff.desc")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
