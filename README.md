# NovaGuard

A high-performance anti-cheat for **Paper 1.21 through the latest release** servers, crafted by **Nova Studio**.

[![Version](https://img.shields.io/badge/version-1.7.0-blue)](https://github.com/Novastudio953/NovaGuard/releases)
[![Paper](https://img.shields.io/badge/paper-1.21%E2%80%93latest-orange)](https://papermc.io)
[![Kotlin](https://img.shields.io/badge/kotlin-2.x-purple)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

NovaGuard ships **50 toggleable checks** covering movement, combat, exploits, and macro-style automation — with a violation-level (VL) system, staff tooling, and smart false-positive protection built in.

---

## Features

### Detection
Every check is individually toggleable and carries its own max-VL, punishment, and ban-command configuration.

**Movement** — Fly, Speed, Timer, Jesus, Step, NoFall, Spider, Scaffold, Phase, ElytraFly, NoSlow, Sprint, BoatFly, Strafe, GroundSpoof

**Combat** — KillAura (Rate / Angle / Cooldown), Reach, AutoClicker, Criticals, Velocity, CrystalAura, AutoTotem, FastBow, Regen, Aimbot

**Exploit** — FastPlace, FastBreak, Nuker, FastEat, BadPackets, InventoryMove, PingSpoof, ChestStealer, InventoryClicker, GhostHand, Derp, Baritone, BookBan, IllegalItems, Xray, AutoArmor, AutoTool

**Anti-macro / client** — Macro interval detection, ClickSignature timing, AFKMacro micro-movement, InventoryBot loops, ChatMacro timing, ClientBrand blocklist

### Anti-Xray
Honeypot ores hidden inside solid rock, unexposed-ore ratio tracking, and diamond-per-hour alerts catch xrayers even when texture packs expose every ore. (Paper's built-in anti-xray is still recommended as the first layer.)

### Staff tools
- `/sus` — suspects GUI with threat scores, top detections, ping, Java/Bedrock status; click to teleport or view history
- `/novaguard freeze` — freeze a suspect in place
- `/novaguard verbose <player>` — stream a player's live detections
- `/novaguard replay <player>` — replay their last ~20 seconds of movement
- `/novaguard config` — toggle checks in-game via GUI
- Ban waves — queue bans and execute them all at once
- Player reports (`/report`), violation history, click-to-teleport alerts
- Discord webhook notifications
- Update checker — staff are notified when a new release drops
- Multi-language messages — English, Español, Tagalog (`settings.language`)

### Developer API
Other plugins can observe or cancel detections:

```java
// listen for flags
@EventHandler
public void onFlag(NovaGuardFlagEvent e) {
    getLogger().info(e.getPlayer().getName() + " flagged: " + e.getCheck().getId());
    // e.setCancelled(true); // cancel the violation entirely
}

// query state
double vl = NovaGuardAPI.getInstance().getViolationLevel(player, "killaura-rate");
```

---

## Commands

### False-positive protection
- **Grace period** — brand-new players need 2x VL before any punishment lands
- **Lag shield** — punishments auto-pause when TPS drops, alerts continue
- **Bedrock support** — Geyser/Floodgate players are exempt from checks that don't translate reliably to Bedrock
- **VPN / proxy / hosting blocker** — screens datacenter IPs at login (fails open, exempt list included)

---

## Commands

| Command | Description | Permission |
|---|---|---|
| `/novaguard reload` | Reload all configuration | `novaguard.admin` |
| `/novaguard list` | List all checks and their status | `novaguard.admin` |
| `/novaguard toggle <check>` | Enable/disable a check live | `novaguard.admin` |
| `/novaguard vl <player>` | View a player's violation levels | `novaguard.admin` |
| `/novaguard vlreset <player>` | Clear a player's violations | `novaguard.admin` |
| `/novaguard alerts` | Toggle staff alert messages | `novaguard.alerts` |
| `/novaguard freeze <player>` | Freeze / unfreeze a suspect | `novaguard.admin` |
| `/novaguard wave` | Execute the queued ban wave | `novaguard.admin` |
| `/novaguard wavelist` | View queued ban-wave targets | `novaguard.admin` |
| `/novaguard reports` | View open player reports | `novaguard.admin` |
| `/novaguard history <player>` | View a player's violation history | `novaguard.admin` |
| `/report <player> <reason>` | Report a suspected cheater | — |
| `/sus` | Open the suspects GUI | `novaguard.sus` |

Aliases: `/ng`, `/guard` for `/novaguard` · `/suspects` for `/sus`

**Other permissions:** `novaguard.bypass` — fully exempt a player from all checks.

---

## Configuration

| File | Purpose |
|---|---|
| `config.yml` | Global settings — Bedrock handling, VPN blocker, grace period, lag shield, Discord webhook |
| `checks.yml` | Per-check toggles, max-VL, punishments, ban commands |
| `messages.yml` | Every player-facing and staff message |
| `xray.yml` | Anti-xray honeypot and alert tuning |

All files generate with sane defaults on first run.

---

## Installation

1. Download the latest `NovaGuard-1.7.0.jar` from the releases page.
2. Drop it into your server's `plugins/` folder.
3. Restart the server. Configuration files generate automatically.

**Requirements:** Paper 1.21 or newer (including the latest 26.x builds), Java 21.

---

## Building from source

Requirements: JDK 21, Kotlin 2.x compiler, and the Paper 1.21 API jar.

```bash
# compile all sources
kotlinc -no-stdlib -no-reflect \
  -cp "paper-api.jar:kotlin-stdlib.jar:adventure-api.jar:adventure-key.jar:examination-api.jar" \
  -d build/classes $(find src/main/kotlin -name "*.kt")

# add resources, shade the Kotlin stdlib, and package
cp -r src/main/resources/* build/classes/
jar cf NovaGuard-1.7.0.jar -C build/classes .
```

---

## License

MIT — see [LICENSE](LICENSE). Copyright (c) 2026 Nova Studio.
