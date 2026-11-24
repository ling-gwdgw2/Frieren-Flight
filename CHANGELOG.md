# Changelog — Zoltraak: Cinematic Edition

All notable changes, hotfixes, and compatibility updates for **Zoltraak: Cinematic Edition** (`zoltraak_cinematic`, supporting **NeoForge 1.21.1** and **Forge 1.20.1**) are documented in this file.

---

## [2.0.2] — 2026-11-24 (Audio Overhaul & Sky Vortex Ring Continuity Patch)

This patch introduces newly synthesized anime-style **Zoltraak Beam** and **Explosion** sound effects, switches general charge/boss sound events to clean vanilla Minecraft audio, removes the post-blast ear-ringing sound, and fixes **Explosion Magic** 8-tier sky vortex ring reveal/handoff continuity across vanilla and Iris/Oculus shaderpacks.

### Added & Changed — Hybrid Custom Anime DSP + Vanilla Audio
- **New Synthesized Zoltraak Beam & Detonation Audio (`5` Mono 44.1 kHz `.ogg` files)**:
  - Added [`zoltraak_fire.ogg`](src/main/resources/assets/zoltraak_cinematic/sounds/zoltraak_fire.ogg), [`zoltraak_great_fire.ogg`](src/main/resources/assets/zoltraak_cinematic/sounds/zoltraak_great_fire.ogg), and [`zoltraak_impact.ogg`](src/main/resources/assets/zoltraak_cinematic/sounds/zoltraak_impact.ogg) using cascaded all-pass phase dispersion (`30`–`56` stages), dual-operator FM plasma cores, and damped-comb waveguides synced to `ZoltraakMode.SINGLE` and `ZoltraakMode.LARGE` (`4.0s` sustained hyper-beam).
  - Added [`explosion_detonate.ogg`](src/main/resources/assets/zoltraak_cinematic/sounds/explosion_detonate.ogg) (`4.80s` signature Explosion detonation) and updated [`astral_singularity_collapse.ogg`](src/main/resources/assets/zoltraak_cinematic/sounds/astral_singularity_collapse.ogg) (`3.40s` singularity collapse blast).
- **Dedicated Explosion Sound Events (`ModCinematicSounds` — `18` total events)**:
  - Registered `explosion_charge`, `explosion_launch`, `explosion_pillar`, and `explosion_detonate` in [`ModCinematicSounds`](src/main/java/com/frierenflight/zoltraakcinematic/registry/ModCinematicSounds.java), wired to [`ExplosionSpell`](src/main/java/com/frierenflight/zoltraakcinematic/spell/ExplosionSpell.java) and [`ExplosionMagicEntity`](src/main/java/com/frierenflight/zoltraakcinematic/entity/ExplosionMagicEntity.java).
- **Switched Non-Beam/Non-Detonation Events to Vanilla Minecraft**:
  - Updated [`sounds.json`](src/main/resources/assets/zoltraak_cinematic/sounds.json) (`"type": "event"`) so `zoltraak_charge`, `explosion_charge`, `explosion_launch`, `explosion_pillar`, `singularity_charge`, `singularity_active`, and all `qual_*` boss events play vanilla Minecraft sound events while preserving EN/TH subtitles, attenuation distances, and the `Mod Sound Volume` slider.
  - Removed 14 unused `.ogg` files and eliminated redundant vanilla sound doubling in [`ZoltraakCinematicBeamEntity`](src/main/java/com/frierenflight/zoltraakcinematic/entity/ZoltraakCinematicBeamEntity.java).
- **Removed Post-Blast Ear Ringing (`tinnitus` / `earRinging`)**:
  - Deleted `astral_singularity_tinnitus.ogg` and removed `ModCinematicSounds.TINNITUS`, `tinnitus` subtitles, and `earRinging` / `earRingingVolume` config options from [`ZoltraakCinematicClientConfig`](src/main/java/com/frierenflight/zoltraakcinematic/config/ZoltraakCinematicClientConfig.java), [`ZoltraakConfigScreen`](src/main/java/com/frierenflight/zoltraakcinematic/client/gui/ZoltraakConfigScreen.java), [`ZoltraakSodiumConfig`](src/main/java/com/frierenflight/zoltraakcinematic/compat/sodium/ZoltraakSodiumConfig.java), [`ZoltraakEmbeddiumConfig`](forge/src/main/java/com/frierenflight/zoltraakcinematic/forge/compat/ZoltraakEmbeddiumConfig.java), [`GargantuaScreenFx`](src/main/java/com/frierenflight/zoltraakcinematic/client/cinematic/GargantuaScreenFx.java), [`GargantuaEntity`](src/main/java/com/frierenflight/zoltraakcinematic/entity/GargantuaEntity.java), [`ZoltraakCinematicClientEvents`](src/main/java/com/frierenflight/zoltraakcinematic/client/ZoltraakCinematicClientEvents.java), `en_us.json`, and `th_th.json`.

### Fixed — Explosion Magic 8-Tier Sky Vortex Ring Timing & Shaderpack Continuity
- **Fixed premature outer/inner ring rims under Iris/Oculus shaders**:
  - Synced `ExplosionChargeRenderer.emitIrisChargeProxy` ([`ExplosionChargeRenderer`](src/main/java/com/frierenflight/zoltraakcinematic/client/renderer/ExplosionChargeRenderer.java)) to the exact per-tier `appearStart` / `ringReveal` stagger of `emitSkyVortexColumn` and `cRing1`/`cRing2`, plus per-ring soft crimson bloom.
- **Eliminated charge-to-release scale bounce, rotation jump, and 1–2 tick blink-out**:
  - Held the 8 sky rings steady at `1.00x` through cast release until apex beam lock (`t = 7`) before smoothly converging (`1.00x → 0.72x` over `t = 7..29`) in [`ExplosionMagicRenderer`](src/main/java/com/frierenflight/zoltraakcinematic/client/renderer/ExplosionMagicRenderer.java).
  - Unified `SKY_COLUMN_SEED = 0.42F` and continuous `worldTime * 0.055` rotation across both renderers, added `ExplosionMagicRenderer.hasActiveEntityNear(...)` handoff guard + config radius fallback, matched post-release Iris double-rim bloom (`0.82` / `0.285`), fixed caster overhead ring cancel pop, and added sub-tick `partial` cast progress interpolation.

### สรุปการอัปเดตเวอร์ชัน 2.0.2 (ภาษาไทย)
- **ระบบเสียงสังเคราะห์อนิเมะ + เสียง Vanilla แบบผสม**:
  - สร้างไฟล์เสียงสังเคราะห์ใหม่สำหรับลำแสง Zoltraak (`zoltraak_fire.ogg`, `zoltraak_great_fire.ogg`, `zoltraak_impact.ogg`), เสียงระเบิด Explosion (`explosion_detonate.ogg`) และปรับปรุงเสียงยุบตัวหลุมดำ (`astral_singularity_collapse.ogg`)
  - เปลี่ยนเสียงชาร์จ ปล่อยเวท และเสียงบอสควาลไปใช้เสียงดั้งเดิมของ Minecraft (Vanilla) โดยยังคงซับไตเติลไทย/อังกฤษและตัวปรับระดับเสียงครบถ้วน พร้อมลบไฟล์ `.ogg` ที่ไม่ใช้ออก 14 ไฟล์
  - ถอดเสียงวิ้งในหู (`tinnitus` / `earRinging`) ออกจากตัวม็อดและเมนูตั้งค่าทั้งหมด
- **แก้จังหวะวงเวทบนฟ้าทั้ง 8 ชั้นของเวท Explosion ให้ลื่นไหลสมบูรณ์**:
  - แก้ปัญหาขอบวงเวทโผล่ขึ้นมาก่อนเวลาเมื่อเปิด Shaderpack (Iris/Oculus) โดยซิงก์ `emitIrisChargeProxy` ให้ทยอยเปิดทีละชั้นตรงกับวงเวทจริง
  - แก้อาการวงเวทหดแล้วดีดขยายกลับตอนปล่อยเวท, อาการมุมหมุนกระโดด (`SKY_COLUMN_SEED`), อาการกระพริบหาย 1–2 เฟรมช่วงรอซิงก์ Entity และเพิ่มการคำนวณความคืบหน้าระหว่างเฟรม (`partial`) ให้กางตัวนุ่มนวลเต็มเฟรมเรต

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
