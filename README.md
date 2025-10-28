# 🛸 They’re Out There, Man!
*A Minecraft Forge Mod by [Exotic Matter Modding](https://github.com/exoticmattermodding)*

---

> “You ever look up at the night sky and feel like something’s looking back?”

---

## 👁️ Overview
**They’re Out There, Man!** (TOTM) brings the unexplained into Minecraft — glowing saucers, abduction beams, and eerie late-night encounters that make you question what’s out there.  
This mod blends cinematic atmosphere with smooth vehicle physics and subtle horror, giving players the chance to **pilot** or **be pursued by** mysterious flying craft.

Built for **Minecraft Forge 1.20.1 (Java 17)**.

---

## 🌌 Features

### 🛸 Flying Saucer Entity
- Fully **rideable UFO** with smooth flight and hover mechanics  
- Uses ground-tracking interpolation for realistic altitude stability  
- Includes **formation flight** and AI patrol behavior  
- Pilot-controlled **abduction beam**, camera orbiting, and custom sounds  
- Two texture states: **standard** and **damaged**

### 👁️ Abduction Beam
- Emits a vertical tractor beam capable of lifting mobs and items  
- Dynamic particle effects and lighting  
- Synced sound and animation events for abduction sequences  
- Controlled by the pilot or AI routine

### 🎮 Control System
- Feels like a hybrid between a **boat**, **minecart**, and **helicopter**  
- Player input (WASD + space/shift) mapped to smooth directional motion  
- Server-authoritative sync reduces rubberbanding and jitter  
- Idle hover logic ensures the saucer never falls when unpiloted

### 🧠 AI & Formation Logic
- Entities can form squadrons, following a designated leader  
- Real-time offset adjustment for altitude and horizontal positioning  
- Returns to **idle hover mode** when formation leader disconnects or dies

### 🌠 Atmospheric Events
- Random nighttime encounters with light pulses, hums, and environmental distortions  
- Flickering terrain lights and subtle particle cues build tension  
- Integrates with biome and weather conditions for maximum immersion

---

## 🧩 Technical Details

| Component | Description |
|------------|--------------|
| `FlyingSaucerEntity.java` | Core logic for flight, control, and hover physics |
| `FlyingSaucerRenderer.java` | Client-side rendering, rotation, and emissive textures |
| `ClientCameraHandler.java` | Third-person orbit and zoom camera logic |
| `BeamEntity.java` | Handles beam visuals and physics |
| `ModNetwork.java` / `SaucerJumpPacket.java` | Custom networking for client-server synchronization |
| `PilotClientState.java` | Tracks current pilot and camera control state |

---

## ⚙️ Installation

1. Install **Minecraft Forge 1.20.1**.  
2. Download the latest TOTM `.jar` file from [Releases](https://github.com/exoticmattermodding/theyre-out-there-man/releases).  
3. Drop it into your `mods/` folder.  
4. Launch Minecraft and check your logs for  
