// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.network;

import com.frierenflight.zoltraakcinematic.entity.DefenseBarrierEntity;
import com.frierenflight.zoltraakcinematic.platform.Services;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicSpells;
import com.frierenflight.zoltraakcinematic.spell.DefensiveMagicSpell;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.MagicHelper;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public record DefenseCastPayload(int mode) {

    public static void handleServer(ServerPlayer player, int mode) {
        int spellLevel = getPlayerDefensiveSpellLevel(player);
        if (spellLevel <= 0) {
            player.displayClientMessage(
                    Component.translatable("ui.zoltraak_cinematic.defense_not_in_spellbook").withStyle(ChatFormatting.RED),
                    true
            );
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.2F);
            return;
        }

        ServerLevel level = player.serverLevel();
        DefensiveMagicSpell spell = (DefensiveMagicSpell) ModCinematicSpells.DEFENSE_MAGIC.get();
        DefenseBarrierEntity existing = DefenseBarrierEntity.find(player);
        MagicData magicData = MagicData.getPlayerMagicData(player);

        boolean isDoubleTapUpgrade = existing != null
                && existing.mode() == DefenseBarrierEntity.MODE_DIRECTIONAL
                && mode == DefenseBarrierEntity.MODE_DOME
                && existing.age(0.0F) <= 20;

        if (isDoubleTapUpgrade) {
            float capacity = spell.getSpellPower(spellLevel, player);
            int duration = spell.getDurationTicks(spellLevel, player);
            existing.refresh(capacity, DefenseBarrierEntity.MODE_DOME, duration);
            level.playSound(null, existing.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.3F, 1.5F);
            level.playSound(null, existing.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.9F, 1.8F);
            return;
        }

        if (magicData.getPlayerCooldowns().isOnCooldown(spell)) {
            return;
        }

        int manaCost = spell.getManaCost(spellLevel);
        if (!player.isCreative() && magicData.getMana() < manaCost) {
            player.displayClientMessage(
                    Component.translatable("ui.irons_spellbooks.not_enough_mana").withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        if (!player.isCreative()) {
            magicData.setMana(Math.max(0.0F, magicData.getMana() - manaCost));
            Services.PLATFORM.syncPlayerMana(player, magicData);
        }

        MagicHelper.MAGIC_MANAGER.addCooldown(player, spell, CastSource.SPELLBOOK);

        float capacity = spell.getSpellPower(spellLevel, player);
        int duration = spell.getDurationTicks(spellLevel, player);

        if (existing == null) {
            level.addFreshEntity(DefenseBarrierEntity.create(level, player, capacity, mode, duration));
        } else {
            existing.refresh(capacity, mode, duration);
        }
    }

    public static int getPlayerDefensiveSpellLevel(ServerPlayer player) {
        DefensiveMagicSpell spell = (DefensiveMagicSpell) ModCinematicSpells.DEFENSE_MAGIC.get();
        int bestLevel = 0;

        SpellSelectionManager ssm = new SpellSelectionManager(player);
        for (SpellSelectionManager.SelectionOption option : ssm.getAllSpells()) {
            if (option != null && option.spellData != null && option.spellData.getSpell() == spell) {
                bestLevel = Math.max(bestLevel, option.spellData.getLevel());
            }
        }

        if (bestLevel > 0) return bestLevel;

        for (ItemStack itemStack : player.getInventory().items) {
            if (itemStack.isEmpty()) continue;
            if (ISpellContainer.isSpellContainer(itemStack)) {
                ISpellContainer container = ISpellContainer.get(itemStack);
                if (container != null) {
                    for (SpellSlot slot : container.getAllSpells()) {
                        if (slot != null && slot.getSpell() == spell) {
                            bestLevel = Math.max(bestLevel, slot.getLevel());
                        }
                    }
                }
            }
        }

        return bestLevel;
    }
}

