# Copyright (c) 2026 Aling7788565. All Rights Reserved.
"""Multi-loader build, resource/SRG adaptation, deployment, and verification suite
for Zoltraak: Cinematic Edition (NeoForge 1.21.1 & Forge 1.20.1).
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MOD_ID = 'zoltraak_cinematic'
MOD_VERSION = '2.0.2'

CORE_SHADER_NAMES = [
    'barrage_fx',
    'black_magic_circle',
    'cinematic_abyssal_beam',
    'cinematic_flare',
    'cinematic_great_abyssal',
    'cinematic_great_photon',
    'cinematic_photon_beam',
    'cinematic_plasma_plume',
    'cinematic_singularity_corona',
    'defense_barrier',
    'explosion_dome',
    'explosion_dust_wave',
    'explosion_post',
    'explosion_pyroclast',
    'explosion_vortex_ring',
    'gargantua',
    'gargantua_bloom_down',
    'gargantua_bloom_up',
    'gargantua_composite',
    'gargantua_disk_preview',
    'gargantua_sphere_preview',
    'magic_circle',
    'rapid_zoltraak_bullet',
    'unsealing_fire_column',
    'unsealing_magic_circle',
    'zoltraak_blast_dome',
    'zoltraak_impact',
    'zoltraak_pyroclast',
]

EXPECTED_RECIPES = [
    'chronos_dilatation_watch',
    'elder_sage_grimoire',
    'frieren_staff',
    'gargantua_scroll',
    'mana_concealment_pendant',
    'mirror_lotus_ring',
    'ordinary_grimoire',
    'qual_sealing_stone',
]

CURIOS_SLOTS = ['charm', 'necklace', 'ring', 'spellbook']

REQUIRED_ASSETS = [
    'assets/zoltraak_cinematic/lang/en_us.json',
    'assets/zoltraak_cinematic/lang/th_th.json',
    'assets/zoltraak_cinematic/sounds.json',
    'assets/zoltraak_cinematic/textures/gui/logo.png',
    'logo.png',
    'assets/zoltraak_cinematic/geo/entity/qual_boss.geo.json',
    'assets/zoltraak_cinematic/animations/entity/qual_boss.animation.json',
    'assets/zoltraak_cinematic/textures/entity/qual_boss.png',
    'assets/zoltraak_cinematic/textures/entity/qual_boss_glowmask.png',
    'assets/zoltraak_cinematic/textures/effect/noise_tile.png',
]

NEOFORGE_ONLY_FILES = {
    'NeoForgePlatformHelper.java',
    'ModCinematicNetworking.java',
    'ZoltraakSodiumConfig.java',
    'NeoForgeClientRenderHooks.java',
}

NEOFORGE_ONLY_CLASSES = [
    'com/frierenflight/zoltraakcinematic/platform/neoforge/NeoForgePlatformHelper.class',
    'com/frierenflight/zoltraakcinematic/network/ModCinematicNetworking.class',
    'com/frierenflight/zoltraakcinematic/compat/sodium/ZoltraakSodiumConfig.class',
    'com/frierenflight/zoltraakcinematic/client/NeoForgeClientRenderHooks.class',
]

NEOFORGE_ONLY_BYTECODE_MARKERS = [
    b'net/neoforged',
    b'ModConfigSpec',
    b'DeferredHolder',
    b'fromNamespaceAndPath',
    b'ByteBufferBuilder',
    b'Matrix4fStack',
    b'ConfigEntryPointForge',
]

TARGET_SPECS = {
    'neoforge': {
        'label': 'NeoForge 1.21.1',
        'jar_name': f'{MOD_ID}-neoforge-1.21.1-{MOD_VERSION}.jar',
        'mixin_file': f'{MOD_ID}.mixins.json',
        'refmap_file': None,
        'manifest_file': 'META-INF/neoforge.mods.toml',
        'expected_major': 65,
        'expected_spi': 'com.frierenflight.zoltraakcinematic.platform.neoforge.NeoForgePlatformHelper',
        'curios_tag_dir': 'data/curios/tags/item',
        'recipe_dir': f'data/{MOD_ID}/recipe',
        'recipe_result_key': 'id',
    },
    'forge': {
        'label': 'Forge 1.20.1',
        'jar_name': f'{MOD_ID}-forge-1.20.1-{MOD_VERSION}.jar',
        'mixin_file': f'{MOD_ID}.forge.mixins.json',
        'refmap_file': f'{MOD_ID}.forge.refmap.json',
        'manifest_file': 'META-INF/mods.toml',
        'expected_major': 61,
        'expected_spi': 'com.frierenflight.zoltraakcinematic.forge.platform.ForgePlatformHelper',
        'curios_tag_dir': 'data/curios/tags/items',
        'recipe_dir': f'data/{MOD_ID}/recipes',
        'recipe_result_key': 'item',
    },
}


def _find_jdk_tool(name):
    """Locate JDK CLI tools using JAVA_HOME if set, falling back to PATH."""
    java_home = os.environ.get('JAVA_HOME')
    if java_home:
        ext = '.exe' if os.name == 'nt' else ''
        candidate = os.path.join(java_home, 'bin', f'{name}{ext}')
        if os.path.exists(candidate):
            return candidate
    return name


javac_path = _find_jdk_tool('javac')
jar_path = _find_jdk_tool('jar')
java_path = _find_jdk_tool('java')


def fix_holder(code):
    """Convert NeoForge DeferredHolder<A, B> generics into Forge RegistryObject<B>."""
    pos = 0
    while True:
        idx = code.find('DeferredHolder<', pos)
        if idx == -1:
            break
        start = idx + len('DeferredHolder<')
        depth = 1
        comma_idx = -1
        i = start
        while i < len(code) and depth > 0:
            if code[i] == '<':
                depth += 1
            elif code[i] == '>':
                depth -= 1
            elif code[i] == ',' and depth == 1:
                comma_idx = i
            i += 1
        if comma_idx != -1 and depth == 0:
            type2 = code[comma_idx + 1 : i - 1].strip()
            replacement = f'RegistryObject<{type2}>'
            code = code[:idx] + replacement + code[i:]
            pos = idx + len(replacement)
        else:
            pos = start
    return code


def transform_vertex(code):
    """Convert MC 1.21 BufferBuilder chained vertex calls into MC 1.20.1 endVertex() calls."""
    def repl(match):
        stmt = match.group(1)
        stmt = stmt.replace('.addVertex(', '.vertex(')
        stmt = stmt.replace('.setUv(', '.uv(')
        stmt = stmt.replace('.setColor(', '.color(')
        stmt = stmt.replace('.setLight(', '.uv2(')
        stmt = stmt.replace('.setNormal(', '.normal(')
        stmt = stmt.replace('.setOverlay(', '.overlayCoords(')
        return re.sub(r';\s*$', '.endVertex();', stmt.rstrip())

    return re.sub(r'(\b\w+\.addVertex\s*\([^;]+?;)', repl, code)


def adapt_common_source_for_forge(filename, code):
    """Apply Mojmap NeoForge 1.21.1 -> Forge 1.20.1 source adaptations to a trunk Java file."""
    # 1. SynchedEntityData
    code = re.sub(
        r'protected\s+void\s+defineSynchedData\s*\(\s*SynchedEntityData\.Builder\s+\w+\s*\)',
        'protected void defineSynchedData()',
        code,
    )
    code = code.replace('super.defineSynchedData(builder);', 'super.defineSynchedData();')
    code = re.sub(r'\bbuilder\.define\b', 'this.entityData.define', code)

    # 2. ResourceLocation
    code = code.replace('ResourceLocation.fromNamespaceAndPath(', 'new ResourceLocation(')

    # 3. GeckoLib 4 packages
    code = code.replace('software.bernie.geckolib.animatable.instance', 'software.bernie.geckolib.core.animatable.instance')
    code = code.replace('software.bernie.geckolib.animation', 'software.bernie.geckolib.core.animation')
    code = code.replace('software.bernie.geckolib.core.animation.PlayState', 'software.bernie.geckolib.core.object.PlayState')

    # 4. Config & Registries
    code = code.replace('net.neoforged.neoforge.common.ModConfigSpec', 'net.minecraftforge.common.ForgeConfigSpec')
    code = code.replace('ModConfigSpec', 'ForgeConfigSpec')
    code = code.replace('import net.neoforged.neoforge.registries.DeferredHolder;', 'import net.minecraftforge.registries.RegistryObject;')
    code = code.replace('import net.neoforged.neoforge.registries.DeferredItem;', 'import net.minecraftforge.registries.RegistryObject;')
    code = code.replace('import net.neoforged.neoforge.registries.DeferredBlock;', 'import net.minecraftforge.registries.RegistryObject;')
    code = code.replace('import net.neoforged.neoforge.registries.DeferredRegister;', 'import net.minecraftforge.registries.DeferredRegister;')
    code = code.replace('net.neoforged.neoforge.registries.DeferredRegister', 'net.minecraftforge.registries.DeferredRegister')
    code = fix_holder(code)
    code = code.replace('net.neoforged.neoforge.registries.DeferredItem', 'net.minecraftforge.registries.RegistryObject')
    code = code.replace('net.neoforged.neoforge.registries.DeferredBlock', 'net.minecraftforge.registries.RegistryObject')
    code = code.replace(
        'DeferredRegister.createItems(ZoltraakCinematicMod.MODID)',
        'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, ZoltraakCinematicMod.MODID)',
    )
    code = code.replace(
        'DeferredRegister.createBlocks(ZoltraakCinematicMod.MODID)',
        'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCKS, ZoltraakCinematicMod.MODID)',
    )
    code = code.replace(
        'DeferredRegister.create(Registries.ENTITY_TYPE, ZoltraakCinematicMod.MODID)',
        'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES, ZoltraakCinematicMod.MODID)',
    )
    code = code.replace(
        'DeferredRegister.create(Registries.SOUND_EVENT, ZoltraakCinematicMod.MODID)',
        'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS, ZoltraakCinematicMod.MODID)',
    )
    code = code.replace(
        'DeferredRegister.create(Registries.MOB_EFFECT, ZoltraakCinematicMod.MODID)',
        'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS, ZoltraakCinematicMod.MODID)',
    )
    code = code.replace(
        'DeferredRegister.create(Registries.ATTRIBUTE, ZoltraakCinematicMod.MODID)',
        'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES, ZoltraakCinematicMod.MODID)',
    )
    code = code.replace(
        'DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ZoltraakCinematicMod.MODID)',
        'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES, ZoltraakCinematicMod.MODID)',
    )
    code = code.replace('DeferredRegister.Blocks', 'DeferredRegister<net.minecraft.world.level.block.Block>')
    code = code.replace('DeferredRegister.Items', 'DeferredRegister<net.minecraft.world.item.Item>')
    code = code.replace('DeferredBlock<', 'RegistryObject<')
    code = code.replace('DeferredItem<', 'RegistryObject<')
    code = code.replace('net.neoforged.neoforge.common.DeferredSpawnEggItem', 'net.minecraftforge.common.ForgeSpawnEggItem')

    # 5. Events & Bus
    code = code.replace(
        'import net.neoforged.neoforge.client.event.ClientTickEvent;\n',
        'import net.minecraftforge.event.TickEvent;\nimport net.minecraftforge.event.TickEvent.ClientTickEvent;\n',
    )
    code = code.replace('net.neoforged.neoforge.client.event.', 'net.minecraftforge.client.event.')
    code = code.replace('net.neoforged.bus.api.', 'net.minecraftforge.eventbus.api.')
    code = code.replace('net.neoforged.neoforge.common.NeoForge', 'net.minecraftforge.common.MinecraftForge')
    code = code.replace('NeoForge.EVENT_BUS', 'MinecraftForge.EVENT_BUS')
    code = code.replace('net.neoforged.neoforge.event.entity.', 'net.minecraftforge.event.entity.')
    code = code.replace('net.neoforged.neoforge.event.level.', 'net.minecraftforge.event.level.')
    code = code.replace('net.neoforged.neoforge.event.RegisterCommandsEvent', 'net.minecraftforge.event.RegisterCommandsEvent')
    code = code.replace(
        'public static void onClientTick(ClientTickEvent.Post event) {',
        'public static void onClientTick(ClientTickEvent event) {\n        if (event.phase != TickEvent.Phase.END) return;',
    )

    # 6. Matrix & RenderPass
    code = code.replace('import com.mojang.blaze3d.vertex.ByteBufferBuilder;\n', '')
    code = code.replace('new ByteBufferBuilder(262144)', 'new com.mojang.blaze3d.vertex.BufferBuilder(262144)')
    code = code.replace('org.joml.Matrix4fStack', 'com.mojang.blaze3d.vertex.PoseStack')
    code = code.replace('Matrix4fStack', 'PoseStack')
    code = code.replace('modelViewStack.set(draw.modelView);', 'modelViewStack.last().pose().set(draw.modelView);')
    code = re.sub(r'modelView\.set\(([^)]+)\);', r'modelView.last().pose().set(\1);', code)
    code = code.replace('modelView.pushMatrix();', 'modelView.pushPose();')
    code = code.replace('modelView.popMatrix();', 'modelView.popPose();')
    code = code.replace('GameRenderer.getRendertypeCloudsShader()', 'GameRenderer.getPositionTexColorNormalShader()')

    # 7. TooltipContext
    code = re.sub(
        r'appendHoverText\s*\(\s*ItemStack\s+(\w+)\s*,\s*(?:Item\.)?TooltipContext\s+(\w+)\s*,\s*List<Component>\s+(\w+)\s*,\s*TooltipFlag\s+(\w+)\s*\)',
        r'appendHoverText(ItemStack \1, net.minecraft.world.level.Level \2, List<Component> \3, TooltipFlag \4)',
        code,
    )

    # 8. AttributeModifier.Operation 1.20.1
    code = code.replace('AttributeModifier.Operation.ADD_VALUE', 'AttributeModifier.Operation.ADDITION')
    code = code.replace('AttributeModifier.Operation.ADD_MULTIPLIED_BASE', 'AttributeModifier.Operation.MULTIPLY_BASE')
    code = code.replace('AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL', 'AttributeModifier.Operation.MULTIPLY_TOTAL')

    # 9. DeltaTracker -> float
    code = code.replace('.getPartialTick().getGameTimeDeltaPartialTick(false)', '.getPartialTick()')
    code = code.replace('mc.getTimer().getGameTimeDeltaPartialTick(false)', 'mc.getFrameTime()')

    # 10. MobEffects Holder -> .get()
    if 'ModCinematicMobEffects.java' not in filename:
        code = re.sub(r'ModCinematicMobEffects\.(\w+)(?!\.get\(\))', r'ModCinematicMobEffects.\1.get()', code)

    # 11. Vertex transformation
    code = transform_vertex(code)

    # 12. QualBossEntity adaptations
    if 'QualBossEntity.java' in filename:
        code = re.sub(r'AttributeRegistry\.(\w+),\s*([0-9.]+)', r'AttributeRegistry.\1.get(), \2', code)
        code = re.sub(r'ModCinematicAttributes\.(\w+),\s*([0-9.]+)', r'ModCinematicAttributes.\1.get(), \2', code)
        code = re.sub(
            r'public\s+SpawnGroupData\s+finalizeSpawn\s*\(\s*ServerLevelAccessor\s+level\s*,\s*DifficultyInstance\s+difficulty\s*,\s*MobSpawnType\s+spawnType\s*,\s*SpawnGroupData\s+spawnGroupData\s*\)\s*\{',
            'public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @org.jetbrains.annotations.Nullable SpawnGroupData spawnGroupData, @org.jetbrains.annotations.Nullable net.minecraft.nbt.CompoundTag dataTag) {',
            code,
        )
        code = code.replace(
            'super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);',
            'super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, dataTag);',
        )
        code = code.replace(
            'protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel serverLevel, DamageSource damageSource, boolean recentlyHit)',
            'protected void dropCustomDeathLoot(DamageSource damageSource, int looting, boolean recentlyHit)',
        )
        code = code.replace(
            'super.dropCustomDeathLoot(serverLevel, damageSource, recentlyHit);',
            'super.dropCustomDeathLoot(damageSource, looting, recentlyHit);',
        )

    # 13. ModCinematicSchools 1.20.1 adaptations
    if 'ModCinematicSchools.java' in filename:
        code = code.replace('net.minecraft.core.Holder.direct(SoundEvents.BEACON_ACTIVATE)', '() -> SoundEvents.BEACON_ACTIVATE')
        code = code.replace('net.minecraft.core.Holder.direct(SoundEvents.WARDEN_SONIC_BOOM)', '() -> SoundEvents.WARDEN_SONIC_BOOM')

    # 14. ModCinematicAttributes.java .get() for entity modification
    if 'ModCinematicAttributes.java' in filename:
        for attr in ('ZOLTRAAK_SPELL_POWER', 'ZOLTRAAK_MAGIC_RESIST', 'BLACK_HOLE_SPELL_POWER', 'BLACK_HOLE_MAGIC_RESIST'):
            code = code.replace(f'event.add(EntityType.PLAYER, {attr});', f'event.add(EntityType.PLAYER, {attr}.get());')

    # 15. ZoltraakCinematicBeamEntity shield disable
    if 'ZoltraakCinematicBeamEntity.java' in filename:
        code = code.replace('p.disableShield();', 'p.disableShield(true);')

    # 16. ZoltraakBarrageProjectileEntity adaptations
    if 'ZoltraakBarrageProjectileEntity.java' in filename:
        code = code.replace('@Override\n    protected double getDefaultGravity()', 'protected double getDefaultGravity()')
        code = code.replace('p.disableShield();', 'p.disableShield(true);')

    # 17. QualBossModel GeckoLib bone
    if 'QualBossModel.java' in filename:
        code = re.sub(
            r'software\.bernie\.geckolib\.cache\.object\.GeoBone\s+head\s*=\s*this\.getAnimationProcessor\(\)\.getBone\(("[^"]+")\);',
            r'var head = this.getAnimationProcessor().getBone(\1);',
            code,
        )

    # 18. GargantuaCommands adaptations
    if 'GargantuaCommands.java' in filename:
        code = code.replace('if (!(source.getLevel() instanceof ServerLevel serverLevel)) return 0;', 'ServerLevel serverLevel = source.getLevel();')
        code = code.replace('schoolHolder.isBound()', 'schoolHolder.isPresent()')

    # 19. Post-processor & camera matrix adaptations for MC 1.20.1:
    # In MC 1.20.1, Camera.rotation() faces +Z (180 deg Y-flipped vs 1.21.1's -Z forward) and omits roll,
    # whereas RenderSystem.getInverseViewRotationMatrix() holds the exact inverse view rotation (V_rot^T)
    # computed by GameRenderer.renderLevel including roll/shake.
    if 'GargantuaPostProcessor.java' in filename or 'ExplosionPostProcessor.java' in filename:
        code = re.sub(
            r'new\s+Matrix4f\(\)\.rotation\(\s*new\s+(?:org\.joml\.)?Quaternionf\(\s*camera\.rotation\(\)\s*\)\.conjugate\(\)\s*\)',
            'new Matrix4f(new org.joml.Matrix3f(RenderSystem.getInverseViewRotationMatrix()).transpose())',
            code,
        )
    if 'GargantuaPostProcessor.java' in filename:
        code = code.replace(
            'BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);',
            'BufferBuilder builder = tesselator.getBuilder();\n        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);',
        )
        code = code.replace('builder.buildOrThrow()', 'builder.end()')

    # 19b. MC 1.20.1 cameraOrientation()/camera.rotation() maps (+1, 0, 0) to camera-left (-camRight);
    # negate X so billboard right vectors match MC 1.21.1 chirality & UV orientation.
    code = re.sub(
        r'new\s+Vector3f\(\s*1\.0[fF]\s*,\s*0\.0[fF]\s*,\s*0\.0[fF]\s*\)\.rotate\((mc\.getEntityRenderDispatcher\(\)\.cameraOrientation\(\)|this\.entityRenderDispatcher\.cameraOrientation\(\)|camera\.rotation\(\)|camRot)\)',
        r'new Vector3f(-1.0F, 0.0F, 0.0F).rotate(\1)',
        code,
    )

    # 20. ZoltraakConfigScreen 1.20.1 Screen.renderBackground adaptation
    if 'ZoltraakConfigScreen.java' in filename:
        code = code.replace(
            'public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {',
            'public void renderBackground(GuiGraphics guiGraphics) {',
        )
        code = code.replace(
            'super.render(guiGraphics, mouseX, mouseY, partialTick);',
            'this.renderBackground(guiGraphics);\n        super.render(guiGraphics, mouseX, mouseY, partialTick);',
        )

    return code


FORGE_IVIEWROT_PYROCLAST_SHADERS = ('explosion_pyroclast', 'zoltraak_pyroclast')
FORGE_IVIEWROT_DOME_SHADERS = ('explosion_dome', 'zoltraak_blast_dome')
FORGE_IVIEWROT_SHADERS = FORGE_IVIEWROT_PYROCLAST_SHADERS + FORGE_IVIEWROT_DOME_SHADERS


def adapt_core_shader_for_forge(fname, raw_bytes):
    """Adapt MC 1.21.1 core shaders that assume ModelViewMat holds camera rotation to MC 1.20.1 IViewRotMat.

    In MC 1.20.1, LevelRenderer bakes camera view rotation into PoseStack (Position is already in eye space)
    while ModelViewMat is Identity and built-in uniform IViewRotMat stores V_rot^-1 (V_rot^T).
    """
    stem, ext = os.path.splitext(fname)
    if stem not in FORGE_IVIEWROT_SHADERS:
        return raw_bytes

    text = raw_bytes.decode('utf-8')
    if ext == '.json':
        data = json.loads(text)
        uniforms = data.setdefault('uniforms', [])
        if not any(u.get('name') == 'IViewRotMat' for u in uniforms):
            uniforms.append({
                'name': 'IViewRotMat',
                'type': 'matrix3x3',
                'count': 9,
                'values': [1, 0, 0, 0, 1, 0, 0, 0, 1],
            })
        return (json.dumps(data, indent=2) + '\n').encode('utf-8')

    if ext == '.vsh':
        if 'uniform mat3 IViewRotMat;' not in text:
            text = text.replace(
                'uniform mat4 ModelViewMat;',
                'uniform mat4 ModelViewMat;\nuniform mat3 IViewRotMat;',
                1,
            )
        if stem in FORGE_IVIEWROT_PYROCLAST_SHADERS:
            text = text.replace(
                'vCamToFragWorld = eyePos.xyz * mat3(ModelViewMat);',
                'vCamToFragWorld = IViewRotMat * eyePos.xyz;',
            )
        elif stem in FORGE_IVIEWROT_DOME_SHADERS:
            text = text.replace(
                'vEyeNormal = (ModelViewMat * vec4(Normal, 0.0)).xyz;',
                'vEyeNormal = (ModelViewMat * vec4(transpose(IViewRotMat) * Normal, 0.0)).xyz;',
            )
        return text.encode('utf-8')

    return raw_bytes


def adapt_recipe_for_forge(raw_bytes):
    """Convert Minecraft 1.21 recipe JSON (`result.id`) to 1.20.1 (`result.item`)."""
    data = json.loads(raw_bytes.decode('utf-8'))
    result = data.get('result')
    if isinstance(result, dict) and 'id' in result and 'item' not in result:
        result['item'] = result.pop('id')
    return (json.dumps(data, indent=2) + '\n').encode('utf-8')


def copy_forge_resources(bin_dir):
    """Copy common resources adapted for MC 1.20.1, then apply Forge overrides."""
    common_res = os.path.join(BASE_DIR, 'src', 'main', 'resources')
    forge_res = os.path.join(BASE_DIR, 'forge', 'src', 'main', 'resources')

    if os.path.exists(common_res):
        for root, _, files in os.walk(common_res):
            rel = os.path.relpath(root, common_res)
            rel_posix = '' if rel == '.' else rel.replace(os.sep, '/')

            if rel_posix == f'data/{MOD_ID}/recipe':
                target_posix = f'data/{MOD_ID}/recipes'
            elif rel_posix.startswith('data/neoforge/tags/'):
                target_posix = rel_posix.replace('data/neoforge/tags/', 'data/forge/tags/', 1)
            elif rel_posix.startswith('data/') and re.search(r'/tags/item(?=/|$)', rel_posix):
                target_posix = re.sub(r'/tags/item(?=/|$)', '/tags/items', rel_posix)
            else:
                target_posix = rel_posix

            target_root = os.path.join(bin_dir, *target_posix.split('/')) if target_posix else bin_dir
            os.makedirs(target_root, exist_ok=True)

            for fname in files:
                if fname in ('neoforge.mods.toml', f'{MOD_ID}.mixins.json'):
                    continue
                src = os.path.join(root, fname)
                dst = os.path.join(target_root, fname)
                if rel_posix == f'data/{MOD_ID}/recipe' and fname.endswith('.json'):
                    with open(src, 'rb') as rf:
                        adapted = adapt_recipe_for_forge(rf.read())
                    with open(dst, 'wb') as wf:
                        wf.write(adapted)
                elif rel_posix == f'assets/{MOD_ID}/shaders/core' and fname.endswith(('.json', '.vsh')):
                    with open(src, 'rb') as rf:
                        adapted = adapt_core_shader_for_forge(fname, rf.read())
                    with open(dst, 'wb') as wf:
                        wf.write(adapted)
                else:
                    shutil.copy2(src, dst)

    if os.path.exists(forge_res):
        for root, _, files in os.walk(forge_res):
            rel = os.path.relpath(root, forge_res)
            target_root = os.path.join(bin_dir, rel) if rel != '.' else bin_dir
            os.makedirs(target_root, exist_ok=True)
            for fname in files:
                shutil.copy2(os.path.join(root, fname), os.path.join(target_root, fname))


def bundle_legal_files(bin_dir):
    """Bundle root LICENSE and NOTICE.txt into both JAR root and META-INF/."""
    meta_dir = os.path.join(bin_dir, 'META-INF')
    os.makedirs(meta_dir, exist_ok=True)
    for fname in ('LICENSE', 'NOTICE.txt'):
        src = os.path.join(BASE_DIR, fname)
        if os.path.exists(src):
            shutil.copy2(src, os.path.join(bin_dir, fname))
            shutil.copy2(src, os.path.join(meta_dir, fname))


def clean_build():
    """Remove temporary build and staging directories under build/."""
    build_dir = os.path.join(BASE_DIR, 'build')
    if os.path.exists(build_dir):
        shutil.rmtree(build_dir)
        print(f"[Clean] Removed build staging directory: {build_dir}")
    else:
        print("[Clean] build/ directory is already clean.")


def deploy_jar(out_jar, label, jar_prefix, env_var, deploy_dirs=None):
    """Copy a built JAR into target directories from --deploy-dir or an env var."""
    dirs = list(deploy_dirs or [])
    env_val = os.environ.get(env_var, '')
    if env_val:
        dirs.extend(p for p in env_val.split(os.pathsep) if p.strip())
    if not dirs:
        print(f"[{label}] --deploy specified, but no target folders set via --deploy-dir or {env_var}.")
        return
    for target_dir in dirs:
        if not os.path.exists(target_dir):
            print(f"[{label} WARNING] Deploy target directory does not exist: {target_dir}")
            continue
        try:
            for existing in os.listdir(target_dir):
                if existing.startswith(jar_prefix) and existing.endswith('.jar'):
                    os.remove(os.path.join(target_dir, existing))
            dest = os.path.join(target_dir, os.path.basename(out_jar))
            shutil.copy2(out_jar, dest)
            print(f"[{label}] Copied to instance: {dest}")
        except Exception as exc:
            print(f"[{label} WARNING] Failed to deploy to {target_dir}: {exc}")


# ==============================================================================
# 1. NEOFORGE 1.21.1 BUILDER
# ==============================================================================
def build_neoforge(deploy=False, deploy_dirs=None):
    """Compile and package the NeoForge 1.21.1 JAR."""
    print("\n==================================================")
    print("  BUILDING: ZOLTRAAK CINEMATIC (NEOFORGE 1.21.1)")
    print("==================================================")

    out_jar = os.path.join(BASE_DIR, TARGET_SPECS['neoforge']['jar_name'])
    bin_dir = os.path.join(BASE_DIR, 'build', 'neoforge', 'classes')
    nf_client = os.path.join(BASE_DIR, 'libs', 'neoforge-21.1.248-client.jar')
    nf_univ = os.path.join(BASE_DIR, 'libs', 'neoforge-21.1.248-universal.jar')
    libs_cp = f"{nf_client}{os.pathsep}{nf_univ}{os.pathsep}" + os.path.join(BASE_DIR, 'libs', '*')
    src_dir = os.path.join(BASE_DIR, 'src', 'main', 'java')
    res_dir = os.path.join(BASE_DIR, 'src', 'main', 'resources')

    if os.path.exists(bin_dir):
        shutil.rmtree(bin_dir)
    os.makedirs(bin_dir, exist_ok=True)

    java_files = [
        os.path.join(root, f)
        for root, _, files in os.walk(src_dir)
        for f in files
        if f.endswith('.java')
    ]

    print(f"[NeoForge] Compiling {len(java_files)} Java files with Java 21...")
    cmd = [javac_path, '-source', '21', '-target', '21', '-proc:none', '-cp', libs_cp, '-d', bin_dir] + java_files
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print("[NeoForge ERROR] Compilation failed!")
        print(res.stderr)
        return False
    print("[NeoForge SUCCESS] Compilation completed!")

    print("[NeoForge] Copying resources...")
    for root, _, files in os.walk(res_dir):
        rel = os.path.relpath(root, res_dir)
        target_root = os.path.join(bin_dir, rel) if rel != '.' else bin_dir
        os.makedirs(target_root, exist_ok=True)
        for fname in files:
            shutil.copy2(os.path.join(root, fname), os.path.join(target_root, fname))

    bundle_legal_files(bin_dir)

    print(f"[NeoForge] Building JAR: {out_jar}")
    res_jar = subprocess.run([jar_path, '--create', '--file', out_jar, '-C', bin_dir, '.'], capture_output=True, text=True)
    if res_jar.returncode != 0:
        print("[NeoForge ERROR] JAR creation failed!")
        print(res_jar.stderr)
        return False

    print(f"[NeoForge SUCCESS] Created: {out_jar} ({os.path.getsize(out_jar)} bytes)")

    if deploy:
        deploy_jar(out_jar, 'NeoForge', f'{MOD_ID}-neoforge-1.21.1-', 'ZOLTRAAK_NEOFORGE_DEPLOY_DIRS', deploy_dirs)

    return True


# ==============================================================================
# 2. FORGE 1.20.1 BUILDER
# ==============================================================================
def build_forge(deploy=False, deploy_dirs=None):
    """Stage, adapt, compile, SRG-remap, and package the Forge 1.20.1 JAR."""
    print("\n==================================================")
    print("  BUILDING: ZOLTRAAK CINEMATIC (FORGE 1.20.1)")
    print("==================================================")

    out_jar = os.path.join(BASE_DIR, TARGET_SPECS['forge']['jar_name'])
    stage_dir = os.path.join(BASE_DIR, 'build', 'forge', 'staged_src')
    bin_dir = os.path.join(BASE_DIR, 'build', 'forge', 'classes')
    libs_cp = os.path.join(BASE_DIR, 'libs_forge', '*')

    for d in (stage_dir, bin_dir):
        if os.path.exists(d):
            shutil.rmtree(d)
        os.makedirs(d, exist_ok=True)

    forge_src = os.path.join(BASE_DIR, 'forge', 'src', 'main', 'java')
    forge_overridden_rel_paths = set()
    forge_files = []
    if os.path.exists(forge_src):
        for root, _, files in os.walk(forge_src):
            for fname in files:
                if fname.endswith('.java'):
                    rel = os.path.relpath(os.path.join(root, fname), forge_src)
                    forge_overridden_rel_paths.add(rel)
                    forge_files.append((rel, os.path.join(root, fname)))

    print("[Forge] Staging and adapting common source files for 1.20.1...")
    src_base = os.path.join(BASE_DIR, 'src', 'main', 'java')
    staged_files = []
    trunk_count = 0

    for root, _, files in os.walk(src_base):
        for fname in files:
            if not fname.endswith('.java'):
                continue
            rel = os.path.relpath(os.path.join(root, fname), src_base)
            rel_posix = rel.replace(os.sep, '/')
            if rel in forge_overridden_rel_paths or fname in NEOFORGE_ONLY_FILES or rel_posix.startswith('com/frierenflight/zoltraakcinematic/mixin/'):
                continue

            dst = os.path.join(stage_dir, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with open(os.path.join(root, fname), 'r', encoding='utf-8') as sf:
                adapted = adapt_common_source_for_forge(fname, sf.read())
            with open(dst, 'w', encoding='utf-8') as df:
                df.write(adapted)
            staged_files.append(dst)
            trunk_count += 1

    for rel, src in forge_files:
        dst = os.path.join(stage_dir, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(src, dst)
        staged_files.append(dst)

    print(f"[Forge] Compiling {len(staged_files)} staged files ({trunk_count} trunk + {len(forge_files)} forge) with Java 17...")
    cmd = [javac_path, '-source', '17', '-target', '17', '-proc:none', '-cp', libs_cp, '-d', bin_dir] + staged_files
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print("[Forge ERROR] Compilation failed!")
        print(res.stderr)
        return False
    print("[Forge SUCCESS] Compilation completed!")

    print("[Forge] Copying and adapting resources (1.20.1 tags/items, recipes & Forge overrides)...")
    copy_forge_resources(bin_dir)
    bundle_legal_files(bin_dir)

    manifest_path = os.path.join(BASE_DIR, 'build', 'forge', 'MANIFEST.MF')
    with open(manifest_path, 'w', encoding='utf-8') as mf:
        mf.write(
            "Manifest-Version: 1.0\n"
            f"MixinConfigs: {MOD_ID}.forge.mixins.json\n"
            "Implementation-Title: Zoltraak: Cinematic Edition\n"
            f"Implementation-Version: {MOD_VERSION}\n"
            f"Specification-Title: {MOD_ID}\n"
            f"Specification-Version: {MOD_VERSION}\n"
        )

    raw_jar = os.path.join(BASE_DIR, 'build', 'forge', 'raw_forge.jar')
    print("[Forge] Packaging compiled classes into initial JAR...")
    res_jar = subprocess.run(
        [jar_path, '--create', '--file', raw_jar, '--manifest', manifest_path, '-C', bin_dir, '.'],
        capture_output=True,
        text=True,
    )
    if res_jar.returncode != 0:
        print("[Forge ERROR] JAR creation failed!")
        print(res_jar.stderr)
        return False

    print("[Forge] Remapping bytecode to SRG (Searge) mappings for Forge 1.20.1...")
    remapper_dir = os.path.join(BASE_DIR, 'tools', 'remapper')
    remapper_cp = os.pathsep.join([
        os.path.join(remapper_dir, 'tiny-remapper-0.10.4.jar'),
        os.path.join(remapper_dir, 'mapping-io-0.7.1.jar'),
        os.path.join(remapper_dir, 'asm-9.7.1.jar'),
        os.path.join(remapper_dir, 'asm-tree-9.7.1.jar'),
        os.path.join(remapper_dir, 'asm-commons-9.7.1.jar'),
        os.path.join(remapper_dir, 'asm-analysis-9.7.1.jar'),
        os.path.join(remapper_dir, 'asm-util-9.7.1.jar'),
    ])
    mappings_file = os.path.join(remapper_dir, 'mappings-srg.tiny')
    merged_mc = os.path.join(BASE_DIR, 'libs_forge', 'forge-1.20.1-minecraft-merged.jar')
    irons_cp = os.path.join(BASE_DIR, 'libs_forge', 'irons_spellbooks-1.20.1-3.16.2.jar')
    geckolib_cp = os.path.join(BASE_DIR, 'libs_forge', 'geckolib-forge-1.20.1-4.8.4.jar')
    embeddium_cp = os.path.join(BASE_DIR, 'libs_forge', 'embeddium-0.3.31+mc1.20.1.jar')

    if os.path.exists(out_jar):
        try:
            os.remove(out_jar)
        except Exception:
            pass

    cmd_remap = [
        java_path, '-cp', remapper_cp,
        'net.fabricmc.tinyremapper.Main',
        raw_jar, out_jar,
        mappings_file, 'named', 'srg',
        merged_mc, irons_cp, geckolib_cp,
    ]
    if os.path.exists(embeddium_cp):
        cmd_remap.append(embeddium_cp)
    res_remap = subprocess.run(cmd_remap, capture_output=True, text=True)
    if res_remap.returncode != 0:
        print("[Forge ERROR] Remapping to SRG failed!")
        print(res_remap.stderr)
        return False

    print(f"[Forge SUCCESS] Remapped and Created: {out_jar} ({os.path.getsize(out_jar)} bytes)")

    if deploy:
        deploy_jar(out_jar, 'Forge', f'{MOD_ID}-forge-1.20.1-', 'ZOLTRAAK_FORGE_DEPLOY_DIRS', deploy_dirs)

    return True


# ==============================================================================
# 3. VERIFICATION & TEST SUITE
# ==============================================================================
class _Reporter:
    def __init__(self):
        self.all_passed = True

    def check(self, condition, pass_msg, fail_msg):
        if condition:
            print(f"  [PASS] {pass_msg}")
        else:
            print(f"  [FAIL] {fail_msg}")
            self.all_passed = False


def _verify_bytecode_versions(rep, z, names, expected_major):
    bad_versions = []
    for entry in names:
        if entry.endswith('.class') and not entry.startswith('META-INF'):
            data = z.read(entry)
            major = int.from_bytes(data[6:8], 'big')
            if major > expected_major:
                bad_versions.append((entry, major))
    java_target = 21 if expected_major == 65 else 17
    rep.check(
        not bad_versions,
        f"Bytecode compliance: All classes compiled for Java <= {java_target} (major <= {expected_major})",
        f"{len(bad_versions)} classes exceed major {expected_major}: {bad_versions[:3]}",
    )


def _verify_manifests_and_legal(rep, z, names, target_key, spec):
    manifest_file = spec['manifest_file']
    if manifest_file in names:
        m_content = z.read(manifest_file).decode('utf-8')
        valid_manifest = (
            f'modId="{MOD_ID}"' in m_content
            and f'version="{MOD_VERSION}"' in m_content
            and 'license="All Rights Reserved"' in m_content
        )
        rep.check(
            valid_manifest,
            f"Mod manifest ({manifest_file}): modId={MOD_ID}, version={MOD_VERSION}, All Rights Reserved verified",
            f"Mod manifest ({manifest_file}) missing modId/version/license!",
        )
    else:
        rep.check(False, "", f"Manifest {manifest_file} missing!")

    for legal_name in ('LICENSE', 'NOTICE.txt'):
        has_root = legal_name in names and 'All Rights Reserved' in z.read(legal_name).decode('utf-8', errors='ignore')
        has_meta = f'META-INF/{legal_name}' in names
        rep.check(
            has_root and has_meta,
            f"Bundled {legal_name}: Verified at JAR root and META-INF/",
            f"Bundled {legal_name} missing/invalid (root={has_root}, META-INF={has_meta})!",
        )

    if target_key == 'forge':
        mf_ok = 'META-INF/MANIFEST.MF' in names and spec['mixin_file'] in z.read('META-INF/MANIFEST.MF').decode('utf-8', errors='ignore')
        rep.check(
            mf_ok,
            f"Forge MANIFEST.MF: MixinConfigs={spec['mixin_file']} declared",
            "Forge META-INF/MANIFEST.MF missing MixinConfigs declaration!",
        )


def _verify_spi(rep, z, names, spec):
    spi_file = 'META-INF/services/com.frierenflight.zoltraakcinematic.platform.IPlatformHelper'
    if spi_file not in names:
        rep.check(False, "", "ServiceLoader SPI registration file missing!")
        return
    impl = z.read(spi_file).decode('utf-8').strip()
    impl_cls = impl.replace('.', '/') + '.class'
    rep.check(
        impl == spec['expected_spi'] and impl_cls in names,
        f"ServiceLoader SPI: {impl} verified in JAR",
        f"SPI mismatch or missing class: got '{impl}' (expected '{spec['expected_spi']}'), in_jar={impl_cls in names}",
    )


def _verify_mixins_and_refmap(rep, z, names, spec):
    mixin_file = spec['mixin_file']
    if mixin_file not in names:
        rep.check(False, "", f"Mixin config {mixin_file} missing!")
        return

    m_data = json.loads(z.read(mixin_file).decode('utf-8'))
    pkg = m_data.get('package', '').replace('.', '/')
    mixins = m_data.get('mixins', []) + m_data.get('client', []) + m_data.get('server', [])
    missing_mixins = [f"{pkg}/{m}.class" for m in mixins if f"{pkg}/{m}.class" not in names]
    rep.check(
        not missing_mixins,
        f"Mixin config ({mixin_file}): All {len(mixins)} mixin classes verified in JAR",
        f"Missing mixin classes: {missing_mixins}",
    )

    refmap_file = spec['refmap_file']
    if refmap_file:
        if refmap_file not in names:
            rep.check(False, "", f"Forge mixin refmap {refmap_file} missing!")
            return
        ref_data = json.loads(z.read(refmap_file).decode('utf-8'))
        top_mappings = ref_data.get('mappings', {})
        searge_mappings = ref_data.get('data', {}).get('searge', {})
        missing_ref = [
            f"{pkg}/{m}"
            for m in mixins
            if not top_mappings.get(f"{pkg}/{m}") or not searge_mappings.get(f"{pkg}/{m}")
        ]
        rep.check(
            not missing_ref,
            f"Forge refmap ({refmap_file}): All {len(mixins)} mixins have mappings + searge entries",
            f"Incomplete refmap entries for mixins: {missing_ref}",
        )


def _verify_json_and_lang(rep, z, names):
    bad_json = []
    for entry in sorted(names):
        if entry.endswith('.json') or entry.endswith('.mcmeta'):
            try:
                json.loads(z.read(entry).decode('utf-8'))
            except Exception as exc:
                bad_json.append((entry, str(exc)))
    rep.check(
        not bad_json,
        "JSON / MCMETA syntax: All JSON/MCMETA entries in JAR parsed cleanly",
        f"Invalid JSON/MCMETA files: {bad_json[:5]}",
    )

    en_path = f'assets/{MOD_ID}/lang/en_us.json'
    th_path = f'assets/{MOD_ID}/lang/th_th.json'
    if en_path in names and th_path in names:
        en_dict = json.loads(z.read(en_path).decode('utf-8'))
        th_dict = json.loads(z.read(th_path).decode('utf-8'))
        missing_in_th = sorted(set(en_dict) - set(th_dict))
        extra_in_th = sorted(set(th_dict) - set(en_dict))
        blank_vals = [k for d in (en_dict, th_dict) for k, v in d.items() if not isinstance(v, str) or not v.strip()]
        rep.check(
            len(en_dict) > 0 and not missing_in_th and not extra_in_th and not blank_vals,
            f"Localization parity: en_us.json & th_th.json matched ({len(en_dict)} keys, zero blanks)",
            f"Lang mismatch: missing_in_th={missing_in_th[:5]}, extra_in_th={extra_in_th[:5]}, blanks={blank_vals[:5]}",
        )
    else:
        rep.check(False, "", f"Missing lang files (en_us={en_path in names}, th_th={th_path in names})")


def _verify_shaders(rep, z, names):
    shader_prefix = f'assets/{MOD_ID}/shaders/core/'
    missing_shaders = []
    shader_errors = []

    for shader in CORE_SHADER_NAMES:
        paths = [f'{shader_prefix}{shader}{ext}' for ext in ('.json', '.vsh', '.fsh')]
        absent = [p for p in paths if p not in names]
        if absent:
            missing_shaders.extend(absent)
            continue

        cfg = json.loads(z.read(paths[0]).decode('utf-8'))
        vsh = z.read(paths[1]).decode('utf-8')
        fsh = z.read(paths[2]).decode('utf-8')

        if not vsh.lstrip().startswith('#version 150') or not fsh.lstrip().startswith('#version 150'):
            shader_errors.append(f"{shader}: missing '#version 150' header")

        v_inputs = re.findall(r'(?m)^\s*in\s+\w+\s+(\w+)\s*;', vsh)
        j_attrs = cfg.get('attributes', [])
        if v_inputs != j_attrs:
            shader_errors.append(f"{shader}: .vsh inputs {v_inputs} != .json attributes {j_attrs}")

        v_outs = sorted(re.findall(r'(?m)^\s*(?:flat\s+)?out\s+(\w+\s+\w+)\s*;', vsh))
        f_ins = sorted(re.findall(r'(?m)^\s*(?:flat\s+)?in\s+(\w+\s+\w+)\s*;', fsh))
        if v_outs != f_ins:
            shader_errors.append(f"{shader}: .vsh outs {v_outs} != .fsh ins {f_ins}")

        for udecl in cfg.get('uniforms', []):
            uname = udecl.get('name', '')
            if uname and uname not in vsh and uname not in fsh:
                shader_errors.append(f"{shader}: uniform '{uname}' unused in .vsh/.fsh")

        for sampler in cfg.get('samplers', []):
            sname = sampler.get('name', '')
            if sname and sname not in fsh:
                shader_errors.append(f"{shader}: sampler '{sname}' not referenced in .fsh")

        # Veil's GLSL AST recompiler strips parentheses around parenthesized ternaries nested
        # inside arithmetic/comparison expressions (e.g. `a * (cond ? b : c)` -> `a * cond ? b : c`).
        for stage_ext, stage_src in (('.vsh', vsh), ('.fsh', fsh)):
            for lineno, sline in enumerate(stage_src.splitlines(), 1):
                stripped = sline.strip()
                if stripped.startswith('//') or '?' not in stripped:
                    continue
                if re.search(r'(?:[+\-*/<>]=?|\b(?:if|while)\s*\()\s*[^;]*\([^()]*\?[^()]*:[^()]*\)', stripped) or re.search(r'\([^()]*\?[^()]*:[^()]*\)\s*[+\-*/<>]=?', stripped):
                    shader_errors.append(f"{shader}{stage_ext}:{lineno}: parenthesized ternary nested in expression (unsafe for Veil GLSL recompiler)")

    rep.check(
        not missing_shaders and not shader_errors,
        f"GLSL 150 Shaders: All {len(CORE_SHADER_NAMES)} pipelines ({len(CORE_SHADER_NAMES) * 3} files) verified (#version 150, attributes, varyings, uniforms, samplers)",
        f"Shader errors: missing={missing_shaders[:5]}, issues={shader_errors[:5]}",
    )

    shaders_java = os.path.join(BASE_DIR, 'src', 'main', 'java', 'com', 'frierenflight', 'zoltraakcinematic', 'client', 'renderer', 'ZoltraakCinematicShaders.java')
    if os.path.exists(shaders_java):
        with open(shaders_java, 'r', encoding='utf-8') as sf:
            registered = sorted(set(re.findall(r'registerSafe\([^,]+,\s*"([^"]+)"', sf.read())))
        rep.check(
            registered == sorted(CORE_SHADER_NAMES),
            f"Shader registry sync: ZoltraakCinematicShaders registers all {len(registered)} shaders",
            f"ZoltraakCinematicShaders mismatch vs test list: diff={set(registered) ^ set(CORE_SHADER_NAMES)}",
        )


def _verify_assets_and_audio(rep, z, names):
    missing_req = [asset for asset in REQUIRED_ASSETS if asset not in names]
    rep.check(
        not missing_req,
        f"Core assets: All {len(REQUIRED_ASSETS)} required textures, models, and metadata files present",
        f"Missing core assets: {missing_req}",
    )

    sounds_path = f'assets/{MOD_ID}/sounds.json'
    if sounds_path in names:
        sounds_json = json.loads(z.read(sounds_path).decode('utf-8'))
        missing_ogg = []
        for entry in sounds_json.values():
            for snd in entry.get('sounds', []):
                raw_name = snd['name'] if isinstance(snd, dict) else snd
                ns, rel = raw_name.split(':', 1) if ':' in raw_name else (MOD_ID, raw_name)
                if ns == MOD_ID:
                    ogg_path = f'assets/{MOD_ID}/sounds/{rel}.ogg'
                    if ogg_path not in names:
                        missing_ogg.append(ogg_path)
        rep.check(
            not missing_ogg,
            f"Audio integrity: All {len(sounds_json)} events in sounds.json map to bundled .ogg files",
            f"Missing .ogg sound files: {missing_ogg}",
        )

    missing_refs = []
    model_prefix = f'assets/{MOD_ID}/models/'
    for entry in sorted(n for n in names if n.startswith(model_prefix) and n.endswith('.json')):
        model_data = json.loads(z.read(entry).decode('utf-8'))
        for tex in model_data.get('textures', {}).values():
            if isinstance(tex, str) and tex.startswith(f'{MOD_ID}:'):
                tex_path = f'assets/{MOD_ID}/textures/' + tex.split(':', 1)[1] + '.png'
                if tex_path not in names:
                    missing_refs.append((entry, tex_path))

    bs_prefix = f'assets/{MOD_ID}/blockstates/'
    for entry in sorted(n for n in names if n.startswith(bs_prefix) and n.endswith('.json')):
        bs_data = json.loads(z.read(entry).decode('utf-8'))
        for var in bs_data.get('variants', {}).values():
            items = var if isinstance(var, list) else [var]
            for item in items:
                mref = item.get('model', '')
                if mref.startswith(f'{MOD_ID}:'):
                    mpath = f'assets/{MOD_ID}/models/' + mref.split(':', 1)[1] + '.json'
                    if mpath not in names:
                        missing_refs.append((entry, mpath))

    rep.check(
        not missing_refs,
        "Models & blockstates: All internal texture and model references resolved",
        f"Broken model/blockstate references: {missing_refs[:5]}",
    )


def _verify_data_and_recipes(rep, z, names, target_key, spec):
    curios_dir = spec['curios_tag_dir']
    missing_slots = [f'{curios_dir}/{slot}.json' for slot in CURIOS_SLOTS if f'{curios_dir}/{slot}.json' not in names]
    rep.check(
        not missing_slots,
        f"Curios slot tags: All {len(CURIOS_SLOTS)} tags verified in {curios_dir}/",
        f"Missing Curios tags: {missing_slots}",
    )

    recipe_dir = spec['recipe_dir']
    result_key = spec['recipe_result_key']
    missing_recipes = []
    bad_recipes = []
    for recipe in EXPECTED_RECIPES:
        rpath = f'{recipe_dir}/{recipe}.json'
        if rpath not in names:
            missing_recipes.append(rpath)
            continue
        rdata = json.loads(z.read(rpath).decode('utf-8'))
        res = rdata.get('result', {})
        if not isinstance(res, dict) or result_key not in res:
            bad_recipes.append(f"{rpath} missing result.{result_key}")

    wrong_dir = 'recipes' if target_key == 'neoforge' else 'recipe'
    stale_recipe_paths = [n for n in names if n.startswith(f'data/{MOD_ID}/{wrong_dir}/')]

    rep.check(
        not missing_recipes and not bad_recipes and not stale_recipe_paths,
        f"Crafting recipes: All {len(EXPECTED_RECIPES)} recipes verified in {recipe_dir}/ (using result.{result_key})",
        f"Recipe issues: missing={missing_recipes}, invalid={bad_recipes}, wrong_dir={stale_recipe_paths}",
    )

    focus_tag_sub = 'item' if target_key == 'neoforge' else 'items'
    lootable_path = f'data/irons_spellbooks/tags/{focus_tag_sub}/lootable_focus.json'
    lootable_ok = False
    if lootable_path in names:
        ldata = json.loads(z.read(lootable_path).decode('utf-8'))
        removed = set(ldata.get('remove', []))
        lootable_ok = (
            '#zoltraak_cinematic:ordinary_magic_focus' in removed
            and '#zoltraak_cinematic:black_hole_focus' in removed
        )
    rep.check(
        lootable_ok,
        f"Focus loot protection ({lootable_path}): Excludes Nether Star & Dragon Egg focuses from dungeon chests",
        f"Missing or incomplete lootable_focus exclusion at {lootable_path}!",
    )


def _verify_loader_specifics(rep, z, names, target_key, spec):
    if target_key == 'forge':
        for cls_name in ('com/frierenflight/zoltraakcinematic/event/FlightEvents.class',
                         'com/frierenflight/zoltraakcinematic/event/DefenseEvents.class'):
            cls_ok = cls_name in names and b'm_' in z.read(cls_name)
            rep.check(
                cls_ok,
                f"Forge SRG remapping: Verified m_ runtime bytecode in {os.path.basename(cls_name)}",
                f"Forge SRG remapping missing or class absent: {cls_name}",
            )

        excluded_present = [c for c in NEOFORGE_ONLY_CLASSES if c in names]
        leaks = []
        for entry in sorted(n for n in names if n.endswith('.class')):
            data = z.read(entry)
            for marker in NEOFORGE_ONLY_BYTECODE_MARKERS:
                if marker in data:
                    leaks.append((entry, marker.decode('ascii', errors='ignore')))
        rep.check(
            not excluded_present and not leaks,
            "Forge bytecode isolation: Zero NeoForge-only classes or unadapted API markers found",
            f"Forge isolation failure: excluded_present={excluded_present}, leaks={leaks[:5]}",
        )

        # Verify MC 1.20.1 post-processor camera view matrix uses RenderSystem.getInverseViewRotationMatrix()
        # (unobfuscated Blaze3D method) rather than 180-deg-flipped Camera.rotation() (m_90567_).
        bad_post_cam = []
        for cls_name in (
            'com/frierenflight/zoltraakcinematic/client/renderer/GargantuaPostProcessor.class',
            'com/frierenflight/zoltraakcinematic/client/renderer/ExplosionPostProcessor.class',
        ):
            if cls_name not in names:
                bad_post_cam.append(f"{cls_name}:missing")
                continue
            raw = z.read(cls_name)
            if b'getInverseViewRotationMatrix' not in raw or b'm_90567_' in raw:
                bad_post_cam.append(os.path.basename(cls_name))
        rep.check(
            not bad_post_cam,
            "Forge 1.20.1 camera view matrix: GargantuaPostProcessor & ExplosionPostProcessor use RenderSystem.getInverseViewRotationMatrix()",
            f"Forge 1.20.1 post-processor camera view matrix not adapted: {bad_post_cam}",
        )

        # Verify MC 1.20.1 IViewRotMat shader adaptations for volumetric pyroclasts and blast domes
        bad_iview_shaders = []
        for sname in FORGE_IVIEWROT_SHADERS:
            jp = f'assets/{MOD_ID}/shaders/core/{sname}.json'
            vp = f'assets/{MOD_ID}/shaders/core/{sname}.vsh'
            jtxt = z.read(jp).decode('utf-8') if jp in names else ''
            vtxt = z.read(vp).decode('utf-8') if vp in names else ''
            if '"IViewRotMat"' not in jtxt or 'uniform mat3 IViewRotMat;' not in vtxt:
                bad_iview_shaders.append(sname)
            elif sname in FORGE_IVIEWROT_PYROCLAST_SHADERS and 'IViewRotMat * eyePos.xyz' not in vtxt:
                bad_iview_shaders.append(f"{sname}:vsh")
            elif sname in FORGE_IVIEWROT_DOME_SHADERS and 'transpose(IViewRotMat) * Normal' not in vtxt:
                bad_iview_shaders.append(f"{sname}:vsh")
        rep.check(
            not bad_iview_shaders,
            f"Forge 1.20.1 core shader view-space adaptation: All {len(FORGE_IVIEWROT_SHADERS)} shaders use IViewRotMat",
            f"Forge 1.20.1 IViewRotMat shader adaptation failed: {bad_iview_shaders}",
        )

        # Verify NeoForge 1.21.1 mixin classes never leak into the Forge 1.20.1 JAR
        leaked_nf_mixins = sorted(n for n in names if n.startswith('com/frierenflight/zoltraakcinematic/mixin/') and n.endswith('.class'))
        rep.check(
            not leaked_nf_mixins,
            "Forge mixin isolation: Zero NeoForge 1.21.1 mixin classes leaked into Forge JAR",
            f"NeoForge mixin classes leaked into Forge JAR: {leaked_nf_mixins}",
        )

        # Verify Embeddium 0.3.x Video Options GUI integration is bundled and SRG-remapped
        emb_cls = 'com/frierenflight/zoltraakcinematic/forge/compat/ZoltraakEmbeddiumConfig.class'
        emb_raw = z.read(emb_cls) if emb_cls in names else b''
        emb_ok = bool(emb_raw) and b'OptionGUIConstructionEvent' in emb_raw and b'm_237115_' in emb_raw
        rep.check(
            emb_ok,
            "Embeddium Config API: ZoltraakEmbeddiumConfig verified (OptionGUIConstructionEvent + SRG Component.translatable)",
            f"Embeddium Config API check failed (present={bool(emb_raw)})!",
        )
    else:
        manifest_file = spec['manifest_file']
        sodium_cls = 'com/frierenflight/zoltraakcinematic/compat/sodium/ZoltraakSodiumConfig.class'
        has_cls = sodium_cls in names
        has_prop = manifest_file in names and 'sodium:config_api_user' in z.read(manifest_file).decode('utf-8')
        has_dup_annot = has_cls and b'ConfigEntryPointForge' in z.read(sodium_cls)
        rep.check(
            has_cls and has_prop and not has_dup_annot,
            f"Sodium Config API: {sodium_cls} and mods.toml entrypoint verified (no duplicate @ConfigEntryPointForge)",
            f"Sodium Config API check failed (class={has_cls}, mods.toml={has_prop}, dup_annot={has_dup_annot})!",
        )


def run_tests(targets=None):
    """Run comprehensive JAR verification across NeoForge 1.21.1 and/or Forge 1.20.1 packages."""
    selected = targets or ['neoforge', 'forge']
    rep = _Reporter()

    for target_key in selected:
        spec = TARGET_SPECS[target_key]
        jar_name = spec['jar_name']
        print("\n==================================================")
        print(f"  TESTING: {spec['label']} ({jar_name})")
        print("==================================================")
        jar_file = os.path.join(BASE_DIR, jar_name)
        if not os.path.exists(jar_file):
            rep.check(False, "", f"JAR file {jar_name} does not exist!")
            continue

        with zipfile.ZipFile(jar_file, 'r') as z:
            names = set(z.namelist())
            print(f"Total entries: {len(names)}")
            _verify_bytecode_versions(rep, z, names, spec['expected_major'])
            _verify_manifests_and_legal(rep, z, names, target_key, spec)
            _verify_spi(rep, z, names, spec)
            _verify_mixins_and_refmap(rep, z, names, spec)
            _verify_json_and_lang(rep, z, names)
            _verify_shaders(rep, z, names)
            _verify_assets_and_audio(rep, z, names)
            _verify_data_and_recipes(rep, z, names, target_key, spec)
            _verify_loader_specifics(rep, z, names, target_key, spec)

    print("\n==================================================")
    if rep.all_passed:
        print("  ALL COMPREHENSIVE TESTS PASSED (100% HEALTHY)!")
    else:
        print("  SOME TESTS FAILED! CHECK OUTPUT ABOVE.")
    print("==================================================")
    return rep.all_passed


def main():
    parser = argparse.ArgumentParser(description="Multi-loader Builder & Verifier for Zoltraak: Cinematic Edition")
    parser.add_argument(
        '--target',
        choices=['neoforge', 'forge', 'all'],
        default=None,
        help="Build target: 'neoforge' (1.21.1), 'forge' (1.20.1), or 'all' (defaults to 'all' unless --test/--clean is used alone)",
    )
    parser.add_argument(
        '--deploy',
        action='store_true',
        default=False,
        help="Copy built JARs to target mod folders via --deploy-dir or ZOLTRAAK_*_DEPLOY_DIRS",
    )
    parser.add_argument(
        '--deploy-dir',
        action='append',
        default=[],
        help="Target mods folder for --deploy (can be passed multiple times)",
    )
    parser.add_argument(
        '--clean',
        action='store_true',
        default=False,
        help="Remove temporary build/ staging and intermediate class directories before building",
    )
    parser.add_argument(
        '--verify',
        action='store_true',
        default=False,
        help="Build target(s) and automatically run the comprehensive package verification suite",
    )
    parser.add_argument(
        '--test',
        action='store_true',
        default=False,
        help="Run package verification on existing JARs (or build first if paired with --target)",
    )
    args = parser.parse_args()

    if args.clean:
        clean_build()
        if args.target is None and not args.test and not args.verify:
            sys.exit(0)

    # `--test` without explicit `--target` or `--verify` validates existing JARs without rebuilding.
    if args.test and args.target is None and not args.verify:
        sys.exit(0 if run_tests() else 1)

    target_choice = args.target or 'all'
    selected_targets = ['neoforge', 'forge'] if target_choice == 'all' else [target_choice]

    success = True
    if 'neoforge' in selected_targets:
        success = build_neoforge(deploy=args.deploy, deploy_dirs=args.deploy_dir) and success
    if 'forge' in selected_targets:
        success = build_forge(deploy=args.deploy, deploy_dirs=args.deploy_dir) and success

    if not success:
        print("\n[ERROR] One or more builds failed.")
        sys.exit(1)

    print("\n==================================================")
    print("  ALL REQUESTED BUILDS COMPLETED SUCCESSFULLY!")
    print("==================================================")

    if args.verify or args.test:
        if not run_tests(selected_targets):
            sys.exit(1)

    sys.exit(0)


if __name__ == '__main__':
    main()
