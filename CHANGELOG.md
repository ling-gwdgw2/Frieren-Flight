# Changelog — Zoltraak: Cinematic Edition

All notable changes, hotfixes, and compatibility updates for **Zoltraak: Cinematic Edition** (`zoltraak_cinematic`, supporting **NeoForge 1.21.1** and **Forge 1.20.1**) are documented in this file.

---

## [2.0.1] — 2026-11-21 (Compatibility & Stability Patch)

This patch focuses on **large-modpack compatibility**, eliminating startup Mixin crashes with rendering/camera mods, resolving **Veil** GLSL shader recompiler failures, protecting Iron's Spells dungeon focus loot tables, and clarifying Survival spell progression.

### Fixed — Mod Compatibility & Mixin Overhaul (NeoForge 1.21.1 & Forge 1.20.1)
- **Eliminated `GameRendererMixin` on both loaders** and **eliminated `CameraMixin` on Forge 1.20.1**:
  - Replaced all render-pass, screen-shake, FOV, hand-hiding, and Forge cutscene-camera mixins with official loader client event hooks ([`NeoForgeClientRenderHooks`](src/main/java/com/frierenflight/zoltraakcinematic/client/NeoForgeClientRenderHooks.java) and [`ForgeClientRenderHooks`](forge/src/main/java/com/frierenflight/zoltraakcinematic/forge/client/ForgeClientRenderHooks.java)):
    - `RenderFrameEvent.Pre` (NeoForge) / `TickEvent.RenderTickEvent` `Phase.START` (Forge) → `ZoltraakRenderPass.begin()`
    - `RenderLevelStageEvent` (`Stage.AFTER_LEVEL`, `EventPriority.HIGHEST`) → `ZoltraakRenderPass.finish()`
    - `ViewportEvent.ComputeCameraAngles` (`EventPriority.HIGHEST`) → Cutscene camera roll & `GargantuaShake` harmonic screen shake (plus Forge cutscene camera orbit override)
    - `ViewportEvent.ComputeFov` (`EventPriority.NORMAL`) → Cutscene FOV blending
    - `RenderHandEvent` (`EventPriority.HIGHEST`) → Hand suppression during cutscenes
  - Resolves `"Mixin config zoltraak_cinematic.forge.mixins.json couldn't be applied"` startup crashes in Forge 1.20.1 modpacks using rendering/camera mods (**OptiFine**, **Vivecraft**, **FirstPersonModel**, **Immersive Portals**, **Rubidium**, **Physics Mod**, etc.).
- **Fixed `Bosses'Rise` (`block_factorys_bosses`) startup crash on NeoForge 1.21.1**:
  - Removed all 6 `@Shadow` fields and methods (`detached`, `position`, `xRot`, `yRot`, `setPosition`, `setRotation`) from NeoForge [`CameraMixin`](src/main/java/com/frierenflight/zoltraakcinematic/mixin/CameraMixin.java), which previously conflicted with `Bosses'Rise`'s `@Shadow public abstract void setPosition(Vec3)`. Cutscene camera takeover now uses public getters + cached reflection.
- **Made all remaining Mixins non-fatal**:
  - Updated [`zoltraak_cinematic.mixins.json`](src/main/resources/zoltraak_cinematic.mixins.json) and [`zoltraak_cinematic.forge.mixins.json`](forge/src/main/resources/zoltraak_cinematic.forge.mixins.json) to `"required": false` and `"defaultRequire": 0`, with `require = 0` on remaining `@Inject` hooks (`PlayerModelPoseMixin`, NeoForge `CameraMixin`).
- **Fixed NeoForge 1.21.1 Mixin class leakage & Dedicated Server isolation**:
  - Prevented NeoForge `com/frierenflight/zoltraakcinematic/mixin/*` classes from being staged into the Forge 1.20.1 JAR.
  - Isolated client event registration inside `ClientSetup` in both [`ZoltraakCinematicMod`](src/main/java/com/frierenflight/zoltraakcinematic/ZoltraakCinematicMod.java) and [`ZoltraakCinematicForgeMod`](forge/src/main/java/com/frierenflight/zoltraakcinematic/forge/ZoltraakCinematicForgeMod.java).

### Fixed — Veil GLSL Shader Recompiler Compatibility (NeoForge 1.21.1 & Forge 1.20.1)
- **Resolved Veil 4.3.2 shader recompilation failures** (`gargantua`, `gargantua_composite`, `zoltraak_blast_dome`) and **prevented silent operator-precedence corruption** across 6 additional shaders (`black_magic_circle`, `magic_circle`, `explosion_dome`, `explosion_post`, `gargantua_disk_preview`, `gargantua_sphere_preview`):
  - Hoisted all parenthesized ternary sub-expressions nested inside arithmetic/comparison expressions into standalone local variables (`float x = cond ? a : b;`), working around Veil's GLSL AST printer stripping outer parentheses.
  - **Zero visual or FPS impact** when Veil is not installed (bit-for-bit identical math and GPU instructions); verified with **0 Veil shader errors** in a 206-mod NeoForge 1.21.1 modpack alongside Veil 4.3.2, Iris, Sodium, Bosses'Rise 2.1.2, and Epic Fight.

### Fixed — Loot Balance & Survival Documentation
- **Protected Iron's Spells dungeon focus loot tables**:
  - Added [`data/irons_spellbooks/tags/item/lootable_focus.json`](src/main/resources/data/irons_spellbooks/tags/item/lootable_focus.json) (NeoForge 1.21.1) and [`data/irons_spellbooks/tags/items/lootable_focus.json`](forge/src/main/resources/data/irons_spellbooks/tags/items/lootable_focus.json) (Forge 1.20.1) to exclude `#zoltraak_cinematic:ordinary_magic_focus` (`minecraft:nether_star`) and `#zoltraak_cinematic:black_hole_focus` (`minecraft:dragon_egg`) from random dungeon focus chests while keeping Scroll Forge crafting intact.
- **Added Survival Progression Guide**:
  - Documented Scroll Forge recipes, Focus items, Inscription Table usage, Arcane Anvil upgrades, and Qual boss relic progression in [`README.md`](README.md) (English & Thai).
- **Expanded Automated Build Verification (`build_multiloader.py`)**:
  - Added automated checks for Veil GLSL ternary safety across all 28 shaders (84 files), `lootable_focus.json` dungeon loot exclusion, and zero NeoForge mixin leaks in Forge 1.20.1.

### สรุปการอัปเดตเวอร์ชัน 2.0.1 (ภาษาไทย)
- **แก้ปัญหาแครชกับ Modpack ใหญ่ (ทั้ง Forge 1.20.1 และ NeoForge 1.21.1)**: ลบ `GameRendererMixin` ออกทั้ง 2 เวอร์ชัน, ลบ `CameraMixin` ฝั่ง Forge 1.20.1 และลบ `@Shadow` ทั้งหมดออกจาก `CameraMixin` ฝั่ง NeoForge 1.21.1 ย้ายไปใช้ Official Forge/NeoForge Events แทน — แก้แครช `"Mixin config couldn't be applied"` (OptiFine / Vivecraft / FirstPersonModel ฯลฯ) และแก้แครชชนกับ **Bosses'Rise** 100%
- **รองรับม็อด Veil 100%**: แก้โครงสร้าง ternary ใน GLSL shaders ทั้ง 9 ไฟล์ให้ผ่านตัว recompile ของ Veil โดยไม่มี error (ทดสอบผ่านแล้วใน Modpack 206 ม็อดร่วมกับ Veil 4.3.2, Iris, Sodium, Bosses'Rise และ Epic Fight) และไม่กระทบภาพ/FPS เมื่อไม่ได้ลง Veil
- **ป้องกันสมดุลกล่องดันเจี้ยน**: กัน `minecraft:nether_star` และ `minecraft:dragon_egg` ออกจาก `lootable_focus` ของ Iron's Spells เพื่อไม่ให้กล่องดันเจี้ยนสุ่มดรอปของบอส (Scroll Forge ยังคราฟต์ได้ปกติ)
- **เพิ่มคู่มือ Survival** ใน `README.md` อธิบายวิธีคราฟต์สโครลและบันทึกเวทลงสมุดเวทครบทุกสาย

---

## [2.0.0] — 2026-11-20 (Major Standalone Multi-Loader Release)

- Full standalone procedural Blaze3D/JOML rendering engine and **28 original GLSL 150 core shaders (84 files)** with zero external particle-engine dependencies.
- Added **Explosion Magic** (`explosion`) with stylized multi-ring sky charge, volcanic dome, pyroclast plumes, shockwave refraction post-processing, and boss range-cap compatibility.
- Added **Forge 1.20.1** full support with MC 1.20.1 `IViewRotMat` shader adaptation, `RenderSystem.getInverseViewRotationMatrix()` camera matrix fix, Oculus compatibility, and native **Embeddium 0.3.x** Video Settings integration (`ZoltraakEmbeddiumConfig`).
- Rebalanced all items, grimoires, Curios relics, and spells; improved dedicated server safety and CurseForge manifest declarations.
