// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.network;

import com.frierenflight.zoltraakcinematic.event.FlightEvents;
import com.frierenflight.zoltraakcinematic.platform.Services;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicMobEffects;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicSpells;
import com.frierenflight.zoltraakcinematic.spell.FlightMagicSpell;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.MagicHelper;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

public record FlightTogglePayload() {

    public static void handleServer(ServerPlayer player) {
        int spellLevel = getPlayerFlightSpellLevel(player);
        if (spellLevel <= 0) {
            player.displayClientMessage(
                    Component.translatable("ui.zoltraak_cinematic.flight_not_in_spellbook").withStyle(ChatFormatting.RED),
                    true
            );
            player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.2F);
            return;
        }

        FlightMagicSpell spell = (FlightMagicSpell) ModCinematicSpells.FLIGHT.get();
        MagicData magicData = MagicData.getPlayerMagicData(player);

        if (player.hasEffect(ModCinematicMobEffects.FLIGHT.get())) {
            player.removeEffect(ModCinematicMobEffects.FLIGHT.get());
            FlightEvents.disableFlight(player);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.9F, 0.8F);
            player.displayClientMessage(
                    Component.translatable("ui.zoltraak_cinematic.flight_deactivated").withStyle(ChatFormatting.AQUA),
                    true
            );
        } else {
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

            player.addEffect(new MobEffectInstance(ModCinematicMobEffects.FLIGHT.get(), MobEffectInstance.INFINITE_DURATION, spellLevel - 1, false, false, true));
            FlightEvents.enableFlight(player);
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.7F);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.5F);
            player.displayClientMessage(
                    Component.translatable("ui.zoltraak_cinematic.flight_activated").withStyle(ChatFormatting.AQUA),
                    true
            );
        }
    }

    public static int getPlayerFlightSpellLevel(ServerPlayer player) {
        FlightMagicSpell spell = (FlightMagicSpell) ModCinematicSpells.FLIGHT.get();
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

