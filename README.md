# Zoltraak: Cinematic Edition

<p align="center">
  <img src="logo.png" alt="Zoltraak: Cinematic Edition Logo" width="160" height="160" />
</p>

<p align="center">
  <strong>Cinematic Zoltraak (一般攻撃魔法 - ゾルトラーク), Defensive Magic, Flight, Explosion Magic &amp; Relativistic Black Hole Spells for Iron's Spells 'n Spellbooks</strong><br>
  <em>เวอร์ชัน 2.0.2 — รองรับทั้ง Minecraft NeoForge 1.21.1 และ Forge 1.20.1 พร้อมเอนจินเรนเดอร์และ GLSL 150 Shaders ในตัว 100%</em>
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
- **28 Original GLSL 150 Core Shaders (84 files)**: Dedicated vertex/fragment pipelines including a 4-pass `GL_RGBA16F` relativistic black hole pipeline (`gargantua`, `gargantua_bloom_down`, `gargantua_bloom_up`, `gargantua_composite`), lensing previews, photonic/abyssal beams, pyroclastic impact domes (`zoltraak_blast_dome`, `zoltraak_pyroclast`), stylized Explosion Magic pipelines (`explosion_dome`, `explosion_dust_wave`, `explosion_post`, `explosion_pyroclast`, `explosion_vortex_ring`), hexagonal barriers, shockwaves, and unsealing rituals.
- **Iris / Oculus & Sodium / Embeddium Compatibility**: Automatic shader-pack detection with deferred emissive passes (`ShaderCompatibility`, `ZoltraakRenderPass`) plus native Video Settings integration for **Sodium** (NeoForge 1.21.1) and **Embeddium** (Forge 1.20.1).
- **Keyframe Camera Rig & Hybrid Anime DSP + Vanilla Audio**: Smooth cutscene camera control (`CinematicRigController`) and custom-synthesized `.ogg` beam/explosion audio paired with vanilla Minecraft sound events (`18` sound events total), with full **English (`en_us`) & Thai (`th_th`)** localization (`245` keys).

---

## Key Features / ฟีเจอร์หลัก

### 1. Magic Schools & Spells (2 สายเวทเฉพาะ + สาย Fire, 8 คาถา)
| Spell ID | Display Name | School | Highlights |
| :--- | :--- | :--- | :--- |
| `zoltraak` | **Zoltraak: Frieren Blast** | `ordinary_magic` | Fast piercing photonic beam with a 12-block impact shockwave and bright glow. |
| `corrupted_zoltraak` | **Corrupted Zoltraak: Qual** | `ordinary_magic` | Qual's forbidden void beam with magenta flares, armor-piercing damage, barrier shattering, and Wither effect. |
| `zoltraak_barrage` | **Zoltraak: Rapid Barrage** | `ordinary_magic` | Summons a **24-circle** magic array firing continuous guided light bolts. |
| `corrupted_barrage` | **Corrupted Zoltraak: Rapid Barrage** | `ordinary_magic` | Dark-matter **24-circle** rapid barrage designed to pressure barriers and wither targets. |
| `defense` | **Defensive Magic (防御魔法)** | `ordinary_magic` | Deploys a hexagonal magic shield in front of the caster, or a **360° Geodesic Dome** when sneaking/double-pressing, while still allowing offensive casting. |
| `flight` | **Flight Magic (飛行魔法)** | `ordinary_magic` | Free 3D aerial flight with dynamic mana consumption based on altitude and sprint speed, HUD readout, and camera turn-tilt. |
| `gargantua` | **Gargantua: Event Horizon** | `black_hole` | 4.0-second incantation summoning a supermassive black hole with gravitational light bending, accretion disk, debris pull, and collapse detonation. |
| `explosion` | **Explosion Magic** | `irons_spellbooks:fire` | 5.5-second Crimson Demon-style incantation channeling stacked sky vortex rings before unleashing a sky strike, sunburst blast dome, volcanic crater, full mana drain, and brief exhaustion. |

### 2. Boss Encounter & Unsealing Ritual
- **Qual, Elder Sage of Corruption (`qual_boss`)**: Multi-phase aerial boss (`2000` base HP + player scaling) featuring 4-tier combat AI (`QualCataclysmicGoal`, `QualAntiBarrierGoal` with anti-shield Shadow Blink, `QualCombatGoal`, `QualFollowPlayerGoal`).
- **Qual's Sealing Stone (`qual_sealing_stone`)**: Indestructible ritual monolith block that initiates a 5-second unsealing ceremony before awakening Qual.

### 3. Items, Curios & Relics (11 ไอเทม)
- **Staff & Spellbooks**: `Frieren's Staff` (`frieren_staff`), `Grimoire of Ordinary Magic` (`ordinary_grimoire`, 10 slots), `Grimoire of the Elder Sage` (`elder_sage_grimoire`, 12 slots)
- **Curios Relics**: `Ring of the Mirror Lotus` (`mirror_lotus_ring`), `Mana Concealment Pendant` (`mana_concealment_pendant`), `Qual's Core of Corruption` (`corruption_core`), `Chronos Time Watch` (`chronos_dilatation_watch` — activates a Time Dilation Field)
- **Progression & Utility**: `Horn of Corruption` (`horn_of_corruption`), `Scroll of Black Hole` (`gargantua_scroll`), `Qual's Sealing Stone` (`qual_sealing_stone`), `Qual Spawn Egg` (`qual_spawn_egg`)

### 4. Visual Settings Studio
- Press `\` (**Backslash**, chosen to avoid colliding with Iris's default `K` key) or open the mod configuration menu (and **Sodium** Video Settings on NeoForge 1.21.1 / **Embeddium** Video Settings on Forge 1.20.1) to tune first-person barrier/circle visibility, per-spell glow brightness (`0%–200%`), Gargantua raymarch quality (`Low 32` to `Ultra 128`), screen flashes, camera shake, and mod sound volume in real time.

---

## Survival Progression / วิธีหาและคราฟต์สโครลในโหมด Survival

All spells integrate directly with **Iron's Spells 'n Spellbooks** progression (**Scroll Forge**, **Inscription Table**, and **Arcane Anvil**):

### Crafting Scrolls at the Scroll Forge
| School | Spells | Required Focus Item | Ink Rarity |
| :--- | :--- | :--- | :--- |
| **Ordinary Magic** (`ordinary_magic`) | Defensive Magic, Flight, Zoltraak, Corrupted Zoltraak, Rapid Barrage, Corrupted Barrage | **Nether Star** *(intentional endgame focus)* | Uncommon / Rare / Epic Ink |
| **Black Hole** (`black_hole`) | Gargantua: Event Horizon | **Dragon Egg** *(or craft `Scroll of Black Hole` directly on a Crafting Table)* | Legendary Ink |
| **Fire** (`irons_spellbooks:fire`) | Explosion Magic | **Blaze Rod** | Legendary Ink |

### Obtaining & Equipping Spells in Survival
1. **Craft a Scroll** at Iron's Spells' **Scroll Forge** using **Ink** + **Paper** + the school's **Focus Item** above (or craft `Scroll of Black Hole` directly on a Crafting Table with a Dragon Egg, Legendary Ink, Ender Runes, Arcane Essence, and Paper).
2. **Inscribe into a Spellbook**: Place your Scroll and any Spellbook (such as `Grimoire of Ordinary Magic` or `Grimoire of the Elder Sage`) into the **Inscription Table** so you can cast the spell repeatedly without consuming the scroll.
3. **Upgrade Spell Level**: Use the **Arcane Anvil** with matching Ink to upgrade inscribed scrolls to higher levels.
4. **Boss & Endgame Relics**: Craft **Qual's Sealing Stone** to awaken **Qual, Elder Sage of Corruption**, then use his drops (`Horn of Corruption` & `Qual's Core of Corruption`) to forge **Frieren's Staff** and **Grimoire of the Elder Sage**.

> **สรุปสั้นๆ สำหรับโหมด Survival**: คราฟต์สโครลที่ **Scroll Forge** ของ Iron's Spells โดยใช้ **หมึก (Ink) + กระดาษ + Focus Item** ประจำสายเวท (**Nether Star** สำหรับสาย Ordinary Magic เพื่อสมดุลช่วงท้ายเกม, **Dragon Egg** สำหรับสาย Black Hole หรือคราฟต์คัมภีร์หลุมดำบนโต๊ะคราฟต์, และ **Blaze Rod** สำหรับ Explosion Magic) จากนั้นนำไปบันทึก (**Inscribe**) ลงสมุดเวทที่ **Inscription Table** เพื่อใช้งานได้ถาวรครับ

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
| **NeoForge** | `1.21.1` | NeoForge `21.1+` | `zoltraak_cinematic-neoforge-1.21.1-2.0.2.jar` |
| **Forge** | `1.20.1` | Forge `47+` | `zoltraak_cinematic-forge-1.20.1-2.0.2.jar` |

### Dependencies
- **Required**:
  - [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks)
  - [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) *(required on both NeoForge 1.21.1 and Forge 1.20.1 for Qual boss geometry & animations)*
  - [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) *(for Curio ring, necklace, charm, and spellbook slots)*
- **Optional / Supported**:
  - **Iris** / **Oculus** Shaders
  - **Sodium** (native config integration on NeoForge 1.21.1) & **Embeddium** (native config integration on Forge 1.20.1)

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

- **[`CHANGELOG.md`](CHANGELOG.md)**: Release history and detailed patch notes (v2.0.2, v2.0.1 & v2.0.0).
- **[`ARCHITECTURE.md`](ARCHITECTURE.md)**: Technical architecture, rendering pipeline, dual-loader staging, and entity/shader diagrams.
- **[`LICENSE`](LICENSE)**: Full proprietary license terms (`All Rights Reserved`) with explicit permissions and Thai summary.
- **[`NOTICE.txt`](NOTICE.txt)**: Clean-room originality statement, asset/shader/audio inventory, third-party exceptions, and fan work disclaimer.

---

## License, Permissions & Credits / ลิขสิทธิ์และสิทธิ์การใช้งาน

**Zoltraak: Cinematic Edition** is published as **Source-Available** under **All Rights Reserved** — Copyright (c) 2026 **Aling7788565**.

| Use Case | Permitted? | Details |
| :--- | :---: | :--- |
| **General Players (Full Gameplay)** | **YES (Free)** | Free, unrestricted access to all in-game features, spells, bosses, items, and shaders, plus servers, videos, and livestreams. |
| **Modpacks (Public / Private)** | **YES** | Unmodified official compiled mod JARs may be included in public or private Minecraft modpacks (CurseForge, Modrinth, etc.). |
| **Original Sound & Image Files** | **YES** | Original `.ogg` audio files (`sounds/`) and original `.png` textures (`textures/`, `logo.png`) may be used, modified, and redistributed freely as desired. |
| **Copying, Cloning, or Redistributing Code** | **NO** | Copying, imitating/cloning, decompiling for reuse, modifying, or redistributing any mod **code** (Java source/bytecode, Mixins, GLSL 150 shaders, build/tooling scripts) — or re-uploading standalone copies of the mod JAR/code — is strictly prohibited. |

### สรุปสิทธิ์การใช้งาน (ภาษาไทย)
- **ผู้เล่นทั่วไป**: เข้าถึงฟีเจอร์และเนื้อหาในเกมได้เต็มรูปแบบ (ฟรี) รวมถึงเล่นบนเซิร์ฟเวอร์ ทำคลิปวิดีโอ หรือไลฟ์สตรีมได้
- **Modpack**: อนุญาตให้นำไฟล์ JAR ทางการที่ไม่ได้แก้ไขดัดแปลงไปรวมใน Modpack ทั้งแบบสาธารณะและส่วนตัวได้
- **ไฟล์เสียงและรูปภาพ**: อนุญาตให้นำไฟล์เสียง (`.ogg`) และไฟล์รูปภาพ/เท็กซ์เจอร์ต้นฉบับ (`.png`, `logo.png`) ของม็อดไปใช้งาน ดัดแปลง หรือเผยแพร่ต่อได้ตามต้องการ
- **ข้อแม้เพียงประการเดียว (Code)**: ไม่อนุญาตให้คัดลอก code ลอกเลียน ถอดรหัสเพื่อนำโค้ดไปใช้ซ้ำ แก้ไขดัดแปลง หรือเผยแพร่ซ้ำซึ่ง code ของม็อด (Java, bytecode, Mixins, GLSL Shaders, สคริปต์) และห้ามอัปโหลด/เผยแพร่ตัวม็อดหรือซอร์สโค้ดแบบเดี่ยวซ้ำโดยไม่ได้รับอนุญาตเป็นลายลักษณ์อักษร

### Third-Party Exceptions & Acknowledgments
- **Ecosystem**: [Iron's Spells 'n Spellbooks](https://github.com/iron431/Irons-Spells-n-Spellbooks) by Iron431, [NeoForge](https://neoforged.net/), [Minecraft Forge](https://files.minecraftforge.net/), [GeckoLib](https://github.com/bernie-g/geckolib), [Curios](https://github.com/TheIllusiveC4/Curios), [Sodium](https://modrinth.com/mod/sodium), and [Iris](https://modrinth.com/mod/iris) / Oculus.
- **Fan Work Disclaimer**: Non-commercial fan modification inspired by *"Sousou no Frieren"* created by Kanehito Yamada and Tsukasa Abe, and *"KonoSuba: God's Blessing on This Wonderful World!"* (*Kono Subarashii Sekai ni Shukufuku wo!*) created by Natsume Akatsuki and Kurone Mishima. Not an official product of Mojang AB, Microsoft Corporation, Shogakukan, Madhouse, Kadokawa Corporation, or Studio Deen.
