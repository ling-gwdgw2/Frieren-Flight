# Zoltraak: Cinematic Edition

<p align="center">
  <img src="logo.png" alt="Zoltraak: Cinematic Edition Logo" width="160" height="160" />
</p>

<p align="center">
  <strong>Cinematic Zoltraak (一般攻撃魔法 - ゾルトラーク), Defensive Magic, Flight, Explosion Magic &amp; Relativistic Black Hole Spells for Iron's Spells 'n Spellbooks</strong><br>
  <em>เวอร์ชัน 2.0.0 — รองรับทั้ง Minecraft NeoForge 1.21.1 และ Forge 1.20.1 พร้อมเอนจินเรนเดอร์และ GLSL 150 Shaders ในตัว 100%</em>
</p>

<p align="center">
  <a href="https://www.minecraft.net/"><img src="https://img.shields.io/badge/Minecraft-1.21.1%20%7C%201.20.1-brightgreen.svg" alt="Minecraft 1.21.1 | 1.20.1" /></a>
  <a href="https://neoforged.net/"><img src="https://img.shields.io/badge/NeoForge-21.1%2B-orange.svg" alt="NeoForge 21.1+" /></a>
  <a href="https://files.minecraftforge.net/"><img src="https://img.shields.io/badge/Forge-47%2B-blue.svg" alt="Forge 47+" /></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks"><img src="https://img.shields.io/badge/Requires-Iron's%20Spells%20'n%20Spellbooks-6f42c1.svg" alt="Requires Iron's Spells 'n Spellbooks" /></a>
  <a href="https://modrinth.com/mod/iris"><img src="https://img.shields.io/badge/Compatible-Iris%20%2F%20Oculus%20%26%20Sodium-33c0ff.svg" alt="Iris / Oculus & Sodium Compatible" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-All%20Rights%20Reserved-red.svg" alt="License: All Rights Reserved" /></a>
</p>

---

## Overview / ภาพรวมม็อด

**Zoltraak: Cinematic Edition** (`zoltraak_cinematic`) is a non-commercial fan modification inspired by *Sousou no Frieren* (*Frieren: Beyond Journey's End*) and *KonoSuba: God's Blessing on This Wonderful World!*, built on top of **Iron's Spells 'n Spellbooks** for **NeoForge 1.21.1** and **Forge 1.20.1**.

In v2.0.0, all rendering, camera choreography, shaders, and audio run completely standalone without external particle-engine dependencies:
- **Procedural Vector Geometry (Blaze3D & JOML)**: Real-time dynamic ribbons, tapered cylinders, 24-circle barrage arrays, layered blast debris/embers, and Class-I Frequency-7 geodesic shields (`492` cells / `1,470` edges).
- **26 Original GLSL 150 Core Shaders (78 files)**: Dedicated vertex/fragment pipelines including a 4-pass `GL_RGBA16F` relativistic black hole pipeline (`gargantua`, `gargantua_bloom_down`, `gargantua_bloom_up`, `gargantua_composite`), lensing previews, photonic/abyssal beams, pyroclastic impact domes (`zoltraak_blast_dome`, `zoltraak_pyroclast`), stylized Explosion Magic pipelines (`explosion_dome`, `explosion_dust_wave`, `explosion_vortex_ring`), hexagonal barriers, shockwaves, and unsealing rituals.
- **Iris / Oculus & Sodium Compatibility**: Automatic shader-pack detection with deferred emissive passes (`ShaderCompatibility`, `ZoltraakRenderPass`) plus native Sodium Video Settings integration on NeoForge 1.21.1 (`ZoltraakSodiumConfig`).
- **Keyframe Camera Rig & 15 Synthesized DSP Sounds**: Smooth cutscene camera control (`CinematicRigController`) and custom-synthesized `.ogg` audio effects, with full **English (`en_us`) & Thai (`th_th`)** localization (`211` keys).

---

## Key Features / ฟีเจอร์หลัก

### 1. Magic Schools & Spells (2 สายเวทเฉพาะ + สาย Fire, 8 คาถา)
| Spell ID | Display Name | School | Highlights |
| :--- | :--- | :--- | :--- |
| `zoltraak` | **Zoltraak: Frieren Blast** | `ordinary_magic` | Fast piercing photonic beam with impact shockwave; crouch/sneak triggers **Great Zoltraak** (`LARGE` mode) with extended range and cutscene. |
| `corrupted_zoltraak` | **Corrupted Zoltraak: Qual** | `ordinary_magic` | Qual's forbidden void beam with magenta flares, armor-piercing damage, and Wither effect. |
| `zoltraak_barrage` | **Zoltraak: Rapid Barrage** | `ordinary_magic` | Summons a **24-circle** magic array firing continuous guided light bolts. |
| `corrupted_barrage` | **Corrupted Zoltraak: Rapid Barrage** | `ordinary_magic` | Dark-matter **24-circle** rapid barrage designed to pressure barriers and wither targets. |
| `defense` | **Defensive Magic (防御魔法)** | `ordinary_magic` | Deploys a hexagonal magic shield in front of the caster, or a **360° Geodesic Dome** when sneaking/double-pressing, while still allowing offensive casting. |
| `flight` | **Flight Magic (飛行魔法)** | `ordinary_magic` | Free 3D aerial flight with dynamic mana consumption based on altitude and sprint speed, HUD readout, and camera turn-tilt. |
| `gargantua` | **Gargantua: Event Horizon** | `black_hole` | 4.0-second incantation summoning a supermassive black hole with gravitational light bending, accretion disk, debris pull, and collapse detonation. |
| `explosion` | **Explosion Magic** | `irons_spellbooks:fire` | 3.5-second Crimson Demon-style incantation channeling stacked sky vortex rings before unleashing a sky strike, sunburst blast dome, crater, full mana drain, and brief exhaustion. |

### 2. Boss Encounter & Unsealing Ritual
- **Qual, Elder Sage of Corruption (`qual_boss`)**: Multi-phase aerial boss (`1500` base HP + player scaling) featuring 4-tier combat AI (`QualCataclysmicGoal`, `QualAntiBarrierGoal` with anti-shield Shadow Blink, `QualCombatGoal`, `QualFollowPlayerGoal`).
- **Qual's Sealing Stone (`qual_sealing_stone`)**: Indestructible ritual monolith block that initiates a 5-second unsealing ceremony before awakening Qual.

### 3. Items, Curios & Relics (11 ไอเทม)
- **Staff & Spellbooks**: `Frieren's Staff` (`frieren_staff`), `Grimoire of Ordinary Magic` (`ordinary_grimoire`, 10 slots), `Grimoire of the Elder Sage` (`elder_sage_grimoire`, 12 slots)
- **Curios Relics**: `Ring of the Mirror Lotus` (`mirror_lotus_ring`), `Mana Concealment Pendant` (`mana_concealment_pendant`), `Qual's Core of Corruption` (`corruption_core`), `Chronos Time Watch` (`chronos_dilatation_watch` — activates a Time Dilation Field)
- **Progression & Utility**: `Saint Quartz of Corruption` (`horn_of_corruption`), `Scroll of Black Hole` (`gargantua_scroll`), `Qual's Sealing Stone` (`qual_sealing_stone`), `Qual Spawn Egg` (`qual_spawn_egg`)

### 4. Visual Settings Studio
- Press `\` (**Backslash**, chosen to avoid colliding with Iris's default `K` key) or open the mod configuration menu (and Sodium Video Settings on NeoForge 1.21.1) to tune first-person barrier/circle visibility, per-spell glow brightness (`0%–200%`), Gargantua raymarch quality (`Low 32` to `Ultra 128`), screen flashes, camera shake, and mod sound / tinnitus volume in real time.

---

## Controls & Commands / ปุ่มลัดและคำสั่ง

### Default Keybindings
| Key | Action | Notes |
| :---: | :--- | :--- |
| **`X`** | **Defensive Magic** | Single press: directional hex shield • Double press (or cast while sneaking): 360° Geodesic Dome |
| **`V`** | **Toggle Flight Magic** | Activates or deactivates 3D Flight Magic (requires `flight` in your equipped spellbook) |
| **`\`** | **Open Zoltraak Settings** | Opens the live visual/audio configuration studio |

### Operator Commands (Permission Level 2)
- `/singularity` or `/zoltraak singularity` — Summons a Gargantua black hole at your crosshair target.
- `/gargantua` or `/zoltraak gargantua` — Summons a Gargantua black hole at your crosshair target.
- `/gargantua night` or `/zoltraak gargantua night` — Sets the world time to night for optimal black hole & shader visibility.

---

## Requirements & Installation / ความต้องการระบบและการติดตั้ง

### Supported Targets
| Platform | Minecraft | Mod Loader | Output Artifact |
| :--- | :---: | :---: | :--- |
| **NeoForge** | `1.21.1` | NeoForge `21.1+` | `zoltraak_cinematic-neoforge-1.21.1-2.0.0.jar` |
| **Forge** | `1.20.1` | Forge `47+` | `zoltraak_cinematic-forge-1.20.1-2.0.0.jar` |

### Dependencies
- **Required**:
  - [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks)
  - [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) *(explicitly required on Forge 1.20.1; used for Qual boss geometry & animations)*
  - [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) *(for Curio ring, necklace, charm, and spellbook slots)*
- **Optional / Supported**:
  - **Iris** / **Oculus** Shaders
  - **Sodium** (native config integration on NeoForge 1.21.1)

### Installation Steps
1. Install Minecraft **1.21.1 (NeoForge)** or **1.20.1 (Forge)** along with the required dependencies above.
2. Place the matching `zoltraak_cinematic-*.jar` file into your `.minecraft/mods` directory.
3. Launch the game.

---

## Building from Source / วิธีการคอมไพล์จากซอร์สโค้ด

This repository uses a unified Python multi-loader build script ([`build_multiloader.py`](build_multiloader.py)) instead of Gradle.

### Prerequisites
- **Python 3**
- **JDK 21** (`javac`, `jar`, `java` on `PATH` or via `JAVA_HOME`) — Forge 1.20.1 bytecode is compiled with `-source 17 -target 17` and remapped to SRG via `tiny-remapper`
- Local dependency JARs placed in `libs/` (NeoForge 1.21.1) and `libs_forge/` (Forge 1.20.1), plus `tiny-remapper` / `asm` / `mapping-io` JARs and `mappings-srg.tiny` in `tools/remapper/` *(ignored in `.gitignore` to comply with Mojang EULA & mapping license terms)*

### Build & Verify
```bash
# Clean staging directory, build both NeoForge 1.21.1 and Forge 1.20.1 JARs, and run verification
python build_multiloader.py --clean --target all --verify

# Build a single loader and verify it
python build_multiloader.py --target neoforge --verify
python build_multiloader.py --target forge --verify

# Run the built-in JAR verification suite standalone on existing JARs
python build_multiloader.py --test
```

Optional cleanup or deployment to local test instance folders:
```bash
# Remove temporary build/ staging and intermediate outputs
python build_multiloader.py --clean

python build_multiloader.py --target neoforge --deploy --deploy-dir "path/to/instance/mods"
# Or configure ZOLTRAAK_NEOFORGE_DEPLOY_DIRS / ZOLTRAAK_FORGE_DEPLOY_DIRS environment variables
```

---

## Documentation / เอกสารประกอบในโปรเจกต์

- **[`ARCHITECTURE.md`](ARCHITECTURE.md)**: Technical architecture, rendering pipeline, dual-loader staging, and entity/shader diagrams.
- **[`LICENSE`](LICENSE)**: Full proprietary license terms (`All Rights Reserved`) with explicit permissions and Thai summary.
- **[`NOTICE.txt`](NOTICE.txt)**: Clean-room originality statement, asset/shader/audio inventory, third-party exceptions, and fan work disclaimer.
- **[`CREDITS.txt`](src/main/resources/assets/zoltraak_cinematic/textures/cosmic/CREDITS.txt)**: Source URLs and CC BY 4.0 / Public Domain attributions for `textures/cosmic/`.

---

## License, Permissions & Credits / ลิขสิทธิ์และสิทธิ์การใช้งาน

**Zoltraak: Cinematic Edition** is published as **Source-Available** under **All Rights Reserved** — Copyright (c) 2026 **Aling7788565**.

| Use Case | Permitted? | Details |
| :--- | :---: | :--- |
| **General Players (Full Gameplay)** | **YES (Free)** | Free, unrestricted access to all in-game features, spells, bosses, items, and shaders, plus servers, videos, and livestreams. |
| **Modpacks (Public / Private)** | **YES** | Unmodified official compiled mod JARs may be included in public or private Minecraft modpacks (CurseForge, Modrinth, etc.). |
| **Original Sound & Image Files** | **YES** | Original `.ogg` audio files (`sounds/`) and original `.png` textures (`textures/`, `logo.png`) may be used, modified, and redistributed freely as desired *(except third-party files in `textures/cosmic/` — see below)*. |
| **Copying, Cloning, or Redistributing Code** | **NO** | Copying, imitating/cloning, decompiling for reuse, modifying, or redistributing any mod **code** (Java source/bytecode, Mixins, GLSL 150 shaders, build/tooling scripts) — or re-uploading standalone copies of the mod JAR/code — is strictly prohibited. |

### สรุปสิทธิ์การใช้งาน (ภาษาไทย)
- **ผู้เล่นทั่วไป**: เข้าถึงฟีเจอร์และเนื้อหาในเกมได้เต็มรูปแบบ (ฟรี) รวมถึงเล่นบนเซิร์ฟเวอร์ ทำคลิปวิดีโอ หรือไลฟ์สตรีมได้
- **Modpack**: อนุญาตให้นำไฟล์ JAR ทางการที่ไม่ได้แก้ไขดัดแปลงไปรวมใน Modpack ทั้งแบบสาธารณะและส่วนตัวได้
- **ไฟล์เสียงและรูปภาพ**: อนุญาตให้นำไฟล์เสียง (`.ogg`) และไฟล์รูปภาพ/เท็กซ์เจอร์ต้นฉบับ (`.png`, `logo.png`) ของม็อดไปใช้งาน ดัดแปลง หรือเผยแพร่ต่อได้ตามต้องการ *(ยกเว้นภาพถ่ายอวกาศใน `textures/cosmic/` ซึ่งอยู่ภายใต้ NASA Public Domain และ CC BY 4.0 ที่ต้องระบุเครดิตตาม [`CREDITS.txt`](src/main/resources/assets/zoltraak_cinematic/textures/cosmic/CREDITS.txt))*
- **ข้อแม้เพียงประการเดียว (Code)**: ไม่อนุญาตให้คัดลอก code ลอกเลียน ถอดรหัสเพื่อนำโค้ดไปใช้ซ้ำ แก้ไขดัดแปลง หรือเผยแพร่ซ้ำซึ่ง code ของม็อด (Java, bytecode, Mixins, GLSL Shaders, สคริปต์) และห้ามอัปโหลด/เผยแพร่ตัวม็อดหรือซอร์สโค้ดแบบเดี่ยวซ้ำโดยไม่ได้รับอนุญาตเป็นลายลักษณ์อักษร

### Third-Party Exceptions & Acknowledgments
- **Cosmic Textures (`textures/cosmic/`)**: NASA Earth Observatory / SVS (Public Domain, 17 U.S.C. § 105) and European Southern Observatory / Solar System Scope (CC BY 4.0). See [`CREDITS.txt`](src/main/resources/assets/zoltraak_cinematic/textures/cosmic/CREDITS.txt).
- **Ecosystem**: [Iron's Spells 'n Spellbooks](https://github.com/iron431/Irons-Spells-n-Spellbooks) by Iron431, [NeoForge](https://neoforged.net/), [Minecraft Forge](https://files.minecraftforge.net/), [GeckoLib](https://github.com/bernie-g/geckolib), [Curios](https://github.com/TheIllusiveC4/Curios), [Sodium](https://modrinth.com/mod/sodium), and [Iris](https://modrinth.com/mod/iris) / Oculus.
- **Fan Work Disclaimer**: Non-commercial fan modification inspired by *"Sousou no Frieren"* created by Kanehito Yamada and Tsukasa Abe, and *"KonoSuba: God's Blessing on This Wonderful World!"* (*Kono Subarashii Sekai ni Shukufuku wo!*) created by Natsume Akatsuki and Kurone Mishima. Not an official product of Mojang AB, Microsoft Corporation, Shogakukan, Madhouse, Kadokawa Corporation, or Studio Deen.
