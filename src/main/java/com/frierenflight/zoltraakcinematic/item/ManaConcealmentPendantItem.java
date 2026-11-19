// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.item;

import com.frierenflight.zoltraakcinematic.registry.ModCinematicAttributes;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.compat.Curios;
import io.redspace.ironsspellbooks.item.curios.CurioBaseItem;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Mana Concealment Pendant (จี้มนตราซ่อนมานา).
 * Flamme's ancient elven training: compresses mana leakage to near-invisibility.
 */
public class ManaConcealmentPendantItem extends CurioBaseItem {

    public ManaConcealmentPendantItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.RARE)
                .fireResistant());
        withAttributes(Curios.NECKLACE_SLOT,
                new AttributeContainer(ModCinematicAttributes.ZOLTRAAK_MAGIC_RESIST, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                new AttributeContainer(AttributeRegistry.SPELL_RESIST, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                new AttributeContainer(AttributeRegistry.CAST_TIME_REDUCTION, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                new AttributeContainer(AttributeRegistry.MAX_MANA, 150.0, AttributeModifier.Operation.ADD_VALUE)
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("item.zoltraak_cinematic.mana_concealment_pendant.desc")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
