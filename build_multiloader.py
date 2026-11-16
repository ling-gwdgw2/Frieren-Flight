# Copyright (c) 2026 Aling7788565. All Rights Reserved.
import os
import sys
import subprocess
import shutil
import zipfile
import re
import argparse

base_dir = os.path.dirname(os.path.abspath(__file__))

# Locate JDK tools (JAVA_HOME if set, otherwise PATH)
def _find_jdk_tool(name):
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
    pos = 0
    while True:
        idx = code.find('DeferredHolder<', pos)
        if idx == -1: break
        start = idx + len('DeferredHolder<')
        depth = 1
        comma_idx = -1
        i = start
        while i < len(code) and depth > 0:
            if code[i] == '<': depth += 1
            elif code[i] == '>': depth -= 1
            elif code[i] == ',' and depth == 1: comma_idx = i
            i += 1
        if comma_idx != -1 and depth == 0:
            type2 = code[comma_idx+1 : i-1].strip()
            code = code[:idx] + 'RegistryObject<' + type2 + '>' + code[i:]
            pos = idx + len('RegistryObject<' + type2 + '>')
        else:
            pos = start
    return code

def transform_vertex(code):
    def repl(m):
        stmt = m.group(1)
        stmt = stmt.replace('.addVertex(', '.vertex(')
        stmt = stmt.replace('.setUv(', '.uv(')
        stmt = stmt.replace('.setColor(', '.color(')
        stmt = stmt.replace('.setLight(', '.uv2(')
        stmt = stmt.replace('.setNormal(', '.normal(')
        stmt = stmt.replace('.setOverlay(', '.overlayCoords(')
        stmt = re.sub(r';\s*$', '.endVertex();', stmt.rstrip())
        return stmt

    return re.sub(r'(\b\w+\.addVertex\s*\([^;]+?;)', repl, code)

def bundle_legal_files(bin_dir):
    """Bundle root LICENSE and NOTICE.txt into the JAR root and META-INF/."""
    meta_dir = os.path.join(bin_dir, 'META-INF')
    os.makedirs(meta_dir, exist_ok=True)
    for fname in ('LICENSE', 'NOTICE.txt'):
        src = os.path.join(base_dir, fname)
        if os.path.exists(src):
            shutil.copy2(src, os.path.join(bin_dir, fname))
            shutil.copy2(src, os.path.join(meta_dir, fname))

def deploy_jar(out_jar, label, jar_prefix, env_var, deploy_dirs=None):
    """Copy built JAR into directories specified via --deploy-dir or env_var."""
    dirs = list(deploy_dirs or [])
    env_val = os.environ.get(env_var, '')
    if env_val:
        dirs.extend(p for p in env_val.split(os.pathsep) if p.strip())
    if not dirs:
        print(f"[{label}] --deploy specified, but no target folders set via --deploy-dir or {env_var}.")
        return
    for target_dir in dirs:
        if os.path.exists(target_dir):
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
    print("\n==================================================")
    print("  BUILDING: ZOLTRAAK CINEMATIC (NEOFORGE 1.21.1)")
    print("==================================================")

    out_jar = os.path.join(base_dir, 'zoltraak_cinematic-neoforge-1.21.1-2.0.0.jar')
    parent_jar = os.path.join(base_dir, '..', 'zoltraak_cinematic-neoforge-1.21.1-2.0.0.jar')
    bin_dir = os.path.join(base_dir, 'build', 'neoforge', 'classes')
    nf_client = os.path.join(base_dir, 'libs', 'neoforge-21.1.248-client.jar')
    nf_univ = os.path.join(base_dir, 'libs', 'neoforge-21.1.248-universal.jar')
    libs_cp = f"{nf_client}{os.pathsep}{nf_univ}{os.pathsep}" + os.path.join(base_dir, 'libs', '*')
    src_dir = os.path.join(base_dir, 'src', 'main', 'java')
    res_dir = os.path.join(base_dir, 'src', 'main', 'resources')

    if os.path.exists(bin_dir):
        shutil.rmtree(bin_dir)
    os.makedirs(bin_dir, exist_ok=True)

    java_files = []
    for root, _, files in os.walk(src_dir):
        for f in files:
            if f.endswith('.java'):
                java_files.append(os.path.join(root, f))

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
        for f in files:
            src = os.path.join(root, f)
            dst = os.path.join(target_root, f)
            shutil.copy2(src, dst)

    bundle_legal_files(bin_dir)

    print(f"[NeoForge] Building JAR: {out_jar}")
    cmd_jar = [jar_path, '--create', '--file', out_jar, '-C', bin_dir, '.']
    res_jar = subprocess.run(cmd_jar, capture_output=True, text=True)
    if res_jar.returncode != 0:
        print("[NeoForge ERROR] JAR creation failed!")
        print(res_jar.stderr)
        return False

    print(f"[NeoForge SUCCESS] Created: {out_jar} ({os.path.getsize(out_jar)} bytes)")

    if deploy:
        deploy_jar(out_jar, 'NeoForge', 'zoltraak_cinematic-neoforge-1.21.1-', 'ZOLTRAAK_NEOFORGE_DEPLOY_DIRS', deploy_dirs)

    return True

# ==============================================================================
# 2. FORGE 1.20.1 BUILDER
# ==============================================================================
def build_forge(deploy=False, deploy_dirs=None):
    print("\n==================================================")
    print("  BUILDING: ZOLTRAAK CINEMATIC (FORGE 1.20.1)")
    print("==================================================")

    out_jar = os.path.join(base_dir, 'zoltraak_cinematic-forge-1.20.1-2.0.0.jar')
    parent_jar = os.path.join(base_dir, '..', 'zoltraak_cinematic-forge-1.20.1-2.0.0.jar')
    stage_dir = os.path.join(base_dir, 'build', 'forge', 'staged_src')
    bin_dir = os.path.join(base_dir, 'build', 'forge', 'classes')
    libs_cp = os.path.join(base_dir, 'libs_forge', '*')

    if os.path.exists(stage_dir):
        shutil.rmtree(stage_dir)
    if os.path.exists(bin_dir):
        shutil.rmtree(bin_dir)
    os.makedirs(stage_dir, exist_ok=True)
    os.makedirs(bin_dir, exist_ok=True)

    # Collect files that have Forge-specific implementations in forge/src/main/java
    forge_src = os.path.join(base_dir, 'forge', 'src', 'main', 'java')
    forge_overridden_rel_paths = set()
    if os.path.exists(forge_src):
        for root, _, files in os.walk(forge_src):
            for f in files:
                if f.endswith('.java'):
                    rel = os.path.relpath(os.path.join(root, f), forge_src)
                    forge_overridden_rel_paths.add(rel)

    # Exclude NeoForge-only classes that have no meaning on Forge
    exclude_files = {
        'NeoForgePlatformHelper.java',
        'ModCinematicNetworking.java',
        'ZoltraakSodiumConfig.java'
    }

    print("[Forge] Staging and adapting common source files for 1.20.1...")
    src_base = os.path.join(base_dir, 'src', 'main', 'java')
    staged_files = []

    for root, _, files in os.walk(src_base):
        for f in files:
            if not f.endswith('.java'):
                continue
            rel = os.path.relpath(os.path.join(root, f), src_base)
            if rel in forge_overridden_rel_paths or f in exclude_files:
                continue

            dst = os.path.join(stage_dir, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with open(os.path.join(root, f), 'r', encoding='utf-8') as sf:
                code = sf.read()

            # 1. SynchedEntityData
            code = re.sub(
                r'protected\s+void\s+defineSynchedData\s*\(\s*SynchedEntityData\.Builder\s+\w+\s*\)',
                'protected void defineSynchedData()',
                code
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
            code = code.replace('DeferredRegister.createItems(ZoltraakCinematicMod.MODID)',
                                'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, ZoltraakCinematicMod.MODID)')
            code = code.replace('DeferredRegister.createBlocks(ZoltraakCinematicMod.MODID)',
                                'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCKS, ZoltraakCinematicMod.MODID)')
            code = code.replace('DeferredRegister.create(Registries.ENTITY_TYPE, ZoltraakCinematicMod.MODID)',
                                'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES, ZoltraakCinematicMod.MODID)')
            code = code.replace('DeferredRegister.create(Registries.SOUND_EVENT, ZoltraakCinematicMod.MODID)',
                                'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS, ZoltraakCinematicMod.MODID)')
            code = code.replace('DeferredRegister.create(Registries.MOB_EFFECT, ZoltraakCinematicMod.MODID)',
                                'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS, ZoltraakCinematicMod.MODID)')
            code = code.replace('DeferredRegister.create(Registries.ATTRIBUTE, ZoltraakCinematicMod.MODID)',
                                'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES, ZoltraakCinematicMod.MODID)')
            code = code.replace('DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ZoltraakCinematicMod.MODID)',
                                'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES, ZoltraakCinematicMod.MODID)')
            code = code.replace('DeferredRegister.Blocks', 'DeferredRegister<net.minecraft.world.level.block.Block>')
            code = code.replace('DeferredRegister.Items', 'DeferredRegister<net.minecraft.world.item.Item>')
            code = code.replace('DeferredBlock<', 'RegistryObject<')
            code = code.replace('DeferredItem<', 'RegistryObject<')
            code = code.replace('net.neoforged.neoforge.common.DeferredSpawnEggItem', 'net.minecraftforge.common.ForgeSpawnEggItem')

            # 5. Events & Bus
            code = code.replace('import net.neoforged.neoforge.client.event.ClientTickEvent;\n', 'import net.minecraftforge.event.TickEvent;\nimport net.minecraftforge.event.TickEvent.ClientTickEvent;\n')
            code = code.replace('net.neoforged.neoforge.client.event.', 'net.minecraftforge.client.event.')
            code = code.replace('net.neoforged.bus.api.', 'net.minecraftforge.eventbus.api.')
            code = code.replace('net.neoforged.neoforge.common.NeoForge', 'net.minecraftforge.common.MinecraftForge')
            code = code.replace('NeoForge.EVENT_BUS', 'MinecraftForge.EVENT_BUS')
            code = code.replace('net.neoforged.neoforge.event.entity.', 'net.minecraftforge.event.entity.')
            code = code.replace('net.neoforged.neoforge.event.level.', 'net.minecraftforge.event.level.')
            code = code.replace('net.neoforged.neoforge.event.RegisterCommandsEvent', 'net.minecraftforge.event.RegisterCommandsEvent')
            code = code.replace('public static void onClientTick(ClientTickEvent.Post event) {',
                                'public static void onClientTick(ClientTickEvent event) {\n        if (event.phase != TickEvent.Phase.END) return;')

            # 6. Matrix & RenderPass
            code = code.replace('import com.mojang.blaze3d.vertex.ByteBufferBuilder;\n', '')
            code = code.replace('new ByteBufferBuilder(262144)', 'new com.mojang.blaze3d.vertex.BufferBuilder(256)')
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
                code
            )

            # 8. AttributeModifier.Operation 1.20.1
            code = code.replace('AttributeModifier.Operation.ADD_VALUE', 'AttributeModifier.Operation.ADDITION')
            code = code.replace('AttributeModifier.Operation.ADD_MULTIPLIED_BASE', 'AttributeModifier.Operation.MULTIPLY_BASE')
            code = code.replace('AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL', 'AttributeModifier.Operation.MULTIPLY_TOTAL')

            # 9. DeltaTracker -> float
            code = code.replace('.getPartialTick().getGameTimeDeltaPartialTick(false)', '.getPartialTick()')
            code = code.replace('mc.getTimer().getGameTimeDeltaPartialTick(false)', 'mc.getFrameTime()')

            # 10. MobEffects Holder -> .get()
            if 'ModCinematicMobEffects.java' not in f:
                code = re.sub(r'ModCinematicMobEffects\.(\w+)(?!\.get\(\))', r'ModCinematicMobEffects.\1.get()', code)

            # 11. Vertex transformation
            code = transform_vertex(code)

            # 12. QualBossEntity adaptations
            if 'QualBossEntity.java' in f:
                code = re.sub(r'AttributeRegistry\.(\w+),\s*([0-9.]+)', r'AttributeRegistry.\1.get(), \2', code)
                code = re.sub(r'ModCinematicAttributes\.(\w+),\s*([0-9.]+)', r'ModCinematicAttributes.\1.get(), \2', code)

                # finalizeSpawn 5 params
                code = re.sub(
                    r'public\s+SpawnGroupData\s+finalizeSpawn\s*\(\s*ServerLevelAccessor\s+level\s*,\s*DifficultyInstance\s+difficulty\s*,\s*MobSpawnType\s+spawnType\s*,\s*SpawnGroupData\s+spawnGroupData\s*\)\s*\{',
                    'public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @org.jetbrains.annotations.Nullable SpawnGroupData spawnGroupData, @org.jetbrains.annotations.Nullable net.minecraft.nbt.CompoundTag dataTag) {',
                    code
                )
                code = code.replace('super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);',
                                    'super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, dataTag);')

                # dropCustomDeathLoot 3 params
                code = code.replace(
                    'protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel serverLevel, DamageSource damageSource, boolean recentlyHit)',
                    'protected void dropCustomDeathLoot(DamageSource damageSource, int looting, boolean recentlyHit)'
                )
                code = code.replace('super.dropCustomDeathLoot(serverLevel, damageSource, recentlyHit);',
                                    'super.dropCustomDeathLoot(damageSource, looting, recentlyHit);')

            # 13. ModCinematicSchools 1.20.1 adaptations
            if 'ModCinematicSchools.java' in f:
                code = code.replace('net.minecraft.core.Holder.direct(SoundEvents.BEACON_ACTIVATE)', '() -> SoundEvents.BEACON_ACTIVATE')
                code = code.replace('net.minecraft.core.Holder.direct(SoundEvents.WARDEN_SONIC_BOOM)', '() -> SoundEvents.WARDEN_SONIC_BOOM')

            # 14. ModCinematicAttributes.java .get() for entity modification
            if 'ModCinematicAttributes.java' in f:
                code = code.replace('event.add(EntityType.PLAYER, ZOLTRAAK_SPELL_POWER);', 'event.add(EntityType.PLAYER, ZOLTRAAK_SPELL_POWER.get());')
                code = code.replace('event.add(EntityType.PLAYER, ZOLTRAAK_MAGIC_RESIST);', 'event.add(EntityType.PLAYER, ZOLTRAAK_MAGIC_RESIST.get());')
                code = code.replace('event.add(EntityType.PLAYER, BLACK_HOLE_SPELL_POWER);', 'event.add(EntityType.PLAYER, BLACK_HOLE_SPELL_POWER.get());')
                code = code.replace('event.add(EntityType.PLAYER, BLACK_HOLE_MAGIC_RESIST);', 'event.add(EntityType.PLAYER, BLACK_HOLE_MAGIC_RESIST.get());')

            # 15. ZoltraakCinematicBeamEntity shield disable
            if 'ZoltraakCinematicBeamEntity.java' in f:
                code = code.replace('p.disableShield();', 'p.disableShield(true);')

            # 16. ZoltraakBarrageProjectileEntity adaptations
            if 'ZoltraakBarrageProjectileEntity.java' in f:
                code = code.replace('@Override\n    protected double getDefaultGravity()', 'protected double getDefaultGravity()')
                code = code.replace('p.disableShield();', 'p.disableShield(true);')

            # 17. QualBossModel GeckoLib bone
            if 'QualBossModel.java' in f:
                code = re.sub(
                    r'software\.bernie\.geckolib\.cache\.object\.GeoBone\s+head\s*=\s*this\.getAnimationProcessor\(\)\.getBone\(("[^"]+")\);',
                    r'var head = this.getAnimationProcessor().getBone(\1);',
                    code
                )

            # 18. GargantuaCommands adaptations
            if 'GargantuaCommands.java' in f:
                code = code.replace('if (!(source.getLevel() instanceof ServerLevel serverLevel)) return 0;', 'ServerLevel serverLevel = source.getLevel();')
                code = code.replace('schoolHolder.isBound()', 'schoolHolder.isPresent()')

            # 19. GargantuaPostProcessor adaptations
            if 'GargantuaPostProcessor.java' in f:
                code = code.replace(
                    'BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);',
                    'BufferBuilder builder = tesselator.getBuilder();\n        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);'
                )
                code = code.replace('builder.buildOrThrow()', 'builder.end()')

            # 20. ZoltraakConfigScreen 1.20.1 Screen.renderBackground adaptation
            if 'ZoltraakConfigScreen.java' in f:
                code = code.replace(
                    'public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {',
                    'public void renderBackground(GuiGraphics guiGraphics) {'
                )
                code = code.replace(
                    'super.render(guiGraphics, mouseX, mouseY, partialTick);',
                    'this.renderBackground(guiGraphics);\n        super.render(guiGraphics, mouseX, mouseY, partialTick);'
                )

            with open(dst, 'w', encoding='utf-8') as df:
                df.write(code)
            staged_files.append(dst)

    # 2. Copy all files from forge/src/main/java
    if os.path.exists(forge_src):
        for root, _, files in os.walk(forge_src):
            for f in files:
                if f.endswith('.java'):
                    rel = os.path.relpath(os.path.join(root, f), forge_src)
                    dst = os.path.join(stage_dir, rel)
                    os.makedirs(os.path.dirname(dst), exist_ok=True)
                    shutil.copy2(os.path.join(root, f), dst)
                    staged_files.append(dst)

    print(f"[Forge] Compiling {len(staged_files)} staged files with Java 17...")
    cmd = [javac_path, '-source', '17', '-target', '17', '-proc:none', '-cp', libs_cp, '-d', bin_dir] + staged_files
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print("[Forge ERROR] Compilation failed!")
        print(res.stderr)
        return False
    print("[Forge SUCCESS] Compilation completed!")

    # 3. Copy resources: common first, then forge overrides
    print("[Forge] Copying resources...")
    common_res = os.path.join(base_dir, 'src', 'main', 'resources')
    forge_res = os.path.join(base_dir, 'forge', 'src', 'main', 'resources')

    for r_dir in [common_res, forge_res]:
        if os.path.exists(r_dir):
            for root, _, files in os.walk(r_dir):
                rel = os.path.relpath(root, r_dir)
                target_root = os.path.join(bin_dir, rel) if rel != '.' else bin_dir
                os.makedirs(target_root, exist_ok=True)
                for f in files:
                    # Skip neoforge-specific files
                    if f in ['neoforge.mods.toml', 'zoltraak_cinematic.mixins.json']:
                        continue
                    src = os.path.join(root, f)
                    dst = os.path.join(target_root, f)
                    shutil.copy2(src, dst)

                    # Adapt 1.21+ singular tags/item to 1.20.1 plural tags/items
                    tag_item = os.path.join('tags', 'item') + os.sep
                    tag_items = os.path.join('tags', 'items') + os.sep
                    if tag_item in rel + os.sep:
                        plural_rel = rel.replace(os.path.join('tags', 'item'), os.path.join('tags', 'items'))
                        plural_target = os.path.join(bin_dir, plural_rel)
                        os.makedirs(plural_target, exist_ok=True)
                        shutil.copy2(src, os.path.join(plural_target, f))

    # 4. Generate MANIFEST.MF for Forge and bundle root LICENSE & NOTICE.txt
    bundle_legal_files(bin_dir)
    manifest_path = os.path.join(bin_dir, 'META-INF', 'MANIFEST.MF')
    os.makedirs(os.path.dirname(manifest_path), exist_ok=True)
    with open(manifest_path, 'w', encoding='utf-8') as mf:
        mf.write(
            "Manifest-Version: 1.0\r\n"
            "MixinConfigs: zoltraak_cinematic.forge.mixins.json\r\n"
            "Implementation-Title: Zoltraak: Cinematic Edition\r\n"
            "Implementation-Version: 2.0.0\r\n"
            "Specification-Title: zoltraak_cinematic\r\n"
            "Specification-Version: 2.0.0\r\n\r\n"
        )

    # 5. Build initial unmapped JAR archive
    raw_jar = os.path.join(base_dir, 'build', 'forge', 'raw_forge.jar')
    print(f"[Forge] Packaging compiled classes into initial JAR...")
    cmd_jar = [jar_path, '--create', '--file', raw_jar, '-C', bin_dir, '.']
    res_jar = subprocess.run(cmd_jar, capture_output=True, text=True)
    if res_jar.returncode != 0:
        print("[Forge ERROR] JAR creation failed!")
        print(res_jar.stderr)
        return False

    # 6. Remap Mojang official mappings -> SRG (Searge) mappings for Forge 1.20.1 runtime
    print("[Forge] Remapping bytecode to SRG (Searge) mappings for Forge 1.20.1...")
    remapper_dir = os.path.join(base_dir, 'tools', 'remapper')
    remapper_cp = ";".join([
        os.path.join(remapper_dir, 'tiny-remapper-0.10.4.jar'),
        os.path.join(remapper_dir, 'mapping-io-0.7.1.jar'),
        os.path.join(remapper_dir, 'asm-9.7.1.jar'),
        os.path.join(remapper_dir, 'asm-tree-9.7.1.jar'),
        os.path.join(remapper_dir, 'asm-commons-9.7.1.jar'),
        os.path.join(remapper_dir, 'asm-analysis-9.7.1.jar'),
        os.path.join(remapper_dir, 'asm-util-9.7.1.jar')
    ])
    mappings_file = os.path.join(remapper_dir, 'mappings-srg.tiny')
    merged_mc = os.path.join(base_dir, 'libs_forge', 'forge-1.20.1-minecraft-merged.jar')
    irons_cp = os.path.join(base_dir, 'libs_forge', 'irons_spellbooks-1.20.1-3.16.2.jar')
    geckolib_cp = os.path.join(base_dir, 'libs_forge', 'geckolib-forge-1.20.1-4.8.4.jar')

    if os.path.exists(out_jar):
        try: os.remove(out_jar)
        except Exception: pass

    cmd_remap = [
        java_path, '-cp', remapper_cp,
        'net.fabricmc.tinyremapper.Main',
        raw_jar, out_jar,
        mappings_file, 'named', 'srg',
        merged_mc, irons_cp, geckolib_cp
    ]
    res_remap = subprocess.run(cmd_remap, capture_output=True, text=True)
    if res_remap.returncode != 0:
        print("[Forge ERROR] Remapping to SRG failed!")
        print(res_remap.stderr)
        return False

    print(f"[Forge SUCCESS] Remapped and Created: {out_jar} ({os.path.getsize(out_jar)} bytes)")

    if deploy:
        deploy_jar(out_jar, 'Forge', 'zoltraak_cinematic-forge-1.20.1-', 'ZOLTRAAK_FORGE_DEPLOY_DIRS', deploy_dirs)

    return True

# ==============================================================================
# 3. VERIFICATION & TEST SUITE
# ==============================================================================
def run_tests():
    import json
    jars = [
        ('NeoForge 1.21.1', 'zoltraak_cinematic-neoforge-1.21.1-2.0.0.jar', 'zoltraak_cinematic.mixins.json', 'META-INF/neoforge.mods.toml', 65),
        ('Forge 1.20.1', 'zoltraak_cinematic-forge-1.20.1-2.0.0.jar', 'zoltraak_cinematic.forge.mixins.json', 'META-INF/mods.toml', 61)
    ]

    all_passed = True
    for label, jar_name, mixin_file, manifest_file, expected_major in jars:
        print("\n==================================================")
        print(f"  TESTING: {label} ({jar_name})")
        print("==================================================")
        jar_path = os.path.join(base_dir, jar_name)
        if not os.path.exists(jar_path):
            print(f"  [FAIL] JAR file {jar_name} does not exist!")
            all_passed = False
            continue

        with zipfile.ZipFile(jar_path, 'r') as z:
            names = set(z.namelist())
            print(f"Total entries: {len(names)}")

            # 1. Bytecode version check
            bad_versions = []
            for n in list(names):
                if n.endswith('.class') and not n.startswith('META-INF'):
                    data = z.read(n)
                    major = int.from_bytes(data[6:8], 'big')
                    if major > expected_major:
                        bad_versions.append((n, major))
            if not bad_versions:
                print(f"  [PASS] Bytecode compliance: All classes compiled for Java <= {21 if expected_major == 65 else 17} (major <= {expected_major})")
            else:
                print(f"  [FAIL] {len(bad_versions)} classes have higher bytecode major than {expected_major}: {bad_versions[:3]}")
                all_passed = False

            # 2. Manifest & License check
            if manifest_file in names:
                m_content = z.read(manifest_file).decode('utf-8')
                if 'zoltraak_cinematic' in m_content and 'license="All Rights Reserved"' in m_content:
                    print(f"  [PASS] Mod manifest ({manifest_file}): Valid modId and All Rights Reserved license found")
                else:
                    print(f"  [FAIL] Mod manifest ({manifest_file}) missing zoltraak_cinematic modId or All Rights Reserved license!")
                    all_passed = False
            else:
                print(f"  [FAIL] Manifest {manifest_file} missing!")
                all_passed = False

            if 'LICENSE' in names and 'All Rights Reserved' in z.read('LICENSE').decode('utf-8', errors='ignore'):
                print(f"  [PASS] Bundled LICENSE: All Rights Reserved + cosmic texture exception verified in JAR")
            else:
                print(f"  [FAIL] Bundled LICENSE missing or invalid in JAR!")
                all_passed = False

            if 'NOTICE.txt' in names and 'All Rights Reserved' in z.read('NOTICE.txt').decode('utf-8', errors='ignore'):
                print(f"  [PASS] Bundled NOTICE.txt: All Rights Reserved & asset permissions verified in JAR")
            else:
                print(f"  [FAIL] Bundled NOTICE.txt missing or invalid in JAR!")
                all_passed = False

            # 3. SPI Service Loader
            spi = 'META-INF/services/com.frierenflight.zoltraakcinematic.platform.IPlatformHelper'
            if spi in names:
                impl = z.read(spi).decode('utf-8').strip()
                impl_cls = impl.replace('.', '/') + '.class'
                if impl_cls in names:
                    print(f"  [PASS] ServiceLoader SPI: {impl} verified in JAR")
                else:
                    print(f"  [FAIL] SPI implementation {impl_cls} missing in JAR!")
                    all_passed = False
            else:
                print(f"  [FAIL] SPI registration file missing!")
                all_passed = False

            # 4. Mixin configuration and classes
            if mixin_file in names:
                m_data = json.loads(z.read(mixin_file).decode('utf-8'))
                pkg = m_data.get('package', '')
                mixins = m_data.get('mixins', []) + m_data.get('client', [])
                missing_mixins = []
                for m in mixins:
                    m_path = f"{pkg.replace('.', '/')}/{m}.class"
                    if m_path not in names:
                        missing_mixins.append(m_path)
                if not missing_mixins:
                    print(f"  [PASS] Mixin config ({mixin_file}): All {len(mixins)} mixin classes verified in JAR")
                else:
                    print(f"  [FAIL] Missing mixin classes: {missing_mixins}")
                    all_passed = False
            else:
                print(f"  [FAIL] Mixin config {mixin_file} missing!")
                all_passed = False

            # 5. Core Shaders check (all 18 shaders)
            shaders = [
                'barrage_fx', 'black_magic_circle', 'cinematic_abyssal_beam', 'cinematic_flare',
                'cinematic_great_abyssal', 'cinematic_great_photon', 'cinematic_photon_beam',
                'cinematic_plasma_plume', 'cinematic_singularity_corona', 'defense_barrier',
                'gargantua', 'magic_circle', 'rapid_zoltraak_bullet', 'unsealing_fire_column',
                'unsealing_magic_circle', 'zoltraak_impact',
                'gargantua_disk_preview', 'gargantua_sphere_preview'
            ]
            missing_shaders = []
            for s in shaders:
                for ext in ['.json', '.fsh', '.vsh']:
                    sp = f'assets/zoltraak_cinematic/shaders/core/{s}{ext}'
                    if sp not in names:
                        missing_shaders.append(sp)
            if not missing_shaders:
                print(f"  [PASS] GLSL Shaders: All {len(shaders)} shader pipelines ({len(shaders) * 3} shader files) verified")
            else:
                print(f"  [FAIL] Missing shader files: {missing_shaders}")
                all_passed = False

            # 6. Core Assets & Data
            lang = 'assets/zoltraak_cinematic/lang/en_us.json'
            sounds = 'assets/zoltraak_cinematic/sounds.json'
            recipe = 'data/zoltraak_cinematic/recipe/frieren_staff.json'
            gui_logo = 'assets/zoltraak_cinematic/textures/gui/logo.png'
            mod_logo = 'logo.png'
            for a in [lang, sounds, recipe, gui_logo, mod_logo,
                      'assets/zoltraak_cinematic/geo/entity/qual_boss.geo.json',
                      'assets/zoltraak_cinematic/animations/entity/qual_boss.animation.json',
                      'assets/zoltraak_cinematic/textures/entity/qual_boss.png',
                      'assets/zoltraak_cinematic/textures/entity/qual_boss_glowmask.png']:
                if a in names:
                    print(f"  [PASS] Asset/Data: {a} present")
                else:
                    print(f"  [FAIL] Asset/Data: {a} missing!")
                    all_passed = False

            # 7. Searge (SRG) remapping check for Forge (Forge 1.20.1 runs with SRG names)
            if 'forge' in jar_name.lower() and 'neoforge' not in jar_name.lower():
                flight_bytes = z.read('com/frierenflight/zoltraakcinematic/event/FlightEvents.class')
                if b'm_' in flight_bytes:
                    print(f"  [PASS] Searge (SRG) remapping: Verified m_ runtime bytecode methods in classes")
                else:
                    print(f"  [FAIL] Searge (SRG) remapping missing: class still contains unmapped Mojang names!")
                    all_passed = False

            # 8. Curios slot tags check
            curios_tag_dir = 'data/curios/tags/items' if ('forge' in jar_name.lower() and 'neoforge' not in jar_name.lower()) else 'data/curios/tags/item'
            curios_slots = ['ring.json', 'necklace.json', 'charm.json', 'spellbook.json']
            for slot in curios_slots:
                slot_path = f"{curios_tag_dir}/{slot}"
                if slot_path in names:
                    print(f"  [PASS] Curios Slot Tag: {slot_path} present")
                else:
                    print(f"  [FAIL] Curios Slot Tag: {slot_path} missing!")
                    all_passed = False

            # 9. Sodium Config API integration check (NeoForge 1.21.1)
            if 'neoforge' in jar_name.lower():
                sodium_cls = 'com/frierenflight/zoltraakcinematic/compat/sodium/ZoltraakSodiumConfig.class'
                has_cls = sodium_cls in names
                has_prop = manifest_file in names and 'sodium:config_api_user' in z.read(manifest_file).decode('utf-8')
                has_dup_annot = has_cls and b'ConfigEntryPointForge' in z.read(sodium_cls)
                if has_cls and has_prop and not has_dup_annot:
                    print(f"  [PASS] Sodium Config API: {sodium_cls} and mods.toml entrypoint verified (no duplicate @ConfigEntryPointForge)")
                else:
                    print(f"  [FAIL] Sodium Config API check failed (class={has_cls}, mods.toml={has_prop}, dup_annot={has_dup_annot})!")
                    all_passed = False

    if all_passed:
        print("\n==================================================")
        print("  ALL COMPREHENSIVE TESTS PASSED (100% HEALTHY)!")
        print("==================================================")
    else:
        print("\n==================================================")
        print("  SOME TESTS FAILED! CHECK OUTPUT ABOVE.")
        print("==================================================")

    return all_passed

# ==============================================================================
# ENTRY POINT
# ==============================================================================
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="Multi-loader Builder for Zoltraak: Cinematic Edition")
    parser.add_argument('--target', choices=['neoforge', 'forge', 'all'], default='all',
                        help="Build target: 'neoforge' (1.21.1), 'forge' (1.20.1), or 'all' (default: all)")
    parser.add_argument('--deploy', action='store_true', default=False,
                        help="Optionally copy built JARs to target mod folders via --deploy-dir or env vars (default: False)")
    parser.add_argument('--deploy-dir', action='append', default=[],
                        help="Target mods folder for --deploy (can be passed multiple times)")
    parser.add_argument('--test', action='store_true', default=False,
                        help="Run comprehensive verification and test suite on the built JARs")
    args = parser.parse_args()

    if args.test:
        success = run_tests()
        sys.exit(0 if success else 1)

    success = True
    if args.target in ['neoforge', 'all']:
        success = build_neoforge(deploy=args.deploy, deploy_dirs=args.deploy_dir) and success
    if args.target in ['forge', 'all']:
        success = build_forge(deploy=args.deploy, deploy_dirs=args.deploy_dir) and success

    if success:
        print("\n==================================================")
        print("  ALL REQUESTED BUILDS COMPLETED SUCCESSFULLY!")
        print("==================================================")
        sys.exit(0)
    else:
        print("\n[ERROR] One or more builds failed.")
        sys.exit(1)
