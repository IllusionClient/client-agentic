# Unreleased - 2026-??-??

- Added combat modules: Auto Block, Lag Range, Combat Jump, Hit Select, Sprint Reset, Kill Aura, Criticals, Knockback Delay, Knockback Displace, Auto Rod, Auto Lava, Auto Dodge and Instant Stop.
- Hit Select and Sprint Reset now predict when a target can take damage again, including after projectile hits.
- Added Blink, Timer, No Slow, Fast Mine and Ping Fix. Ping Fix replaces Backtrack's Ping Fix option, and your setting carries over.
- Added Clutch and Block In, which place blocks for you when you fall or when an opponent gets close.
- Added inventory modules: Auto Armor, Inventory Cleaner, Chest Stealer, Refill and Auto Heal. They share one open inventory and take turns.
- Added Auto Extinguish, Liquid Steal and Bed Wars, which alerts you to threats near your bed and protects your bed defense.
- Added visual modules: Chams, Item ESP, Name Tags, Indicators, Pointers, Target HUD, Notifications and No Hurt Cam.
- Added Tags. Tag players with `.tag <player> <tags>` or middle-click to toggle "friend". Combat modules skip tagged players.
- Added config profiles in a new Profiles category. Switch, bind, tag (Closet, Semi, Blatant) and rename them, or use Generate Profile to make one from a Blatancy slider.
- Aim Assist can now rotate silently, Kill Aura can now turn your camera, and Clutch, Block In, Auto Rod, Auto Lava, Auto Extinguish and Liquid Steal gained Rotation settings to choose Silent or Camera and how they turn.
- Clutch and Block In now share the same turning as every other module, replacing Base Speed, Acceleration, Snap-back Duration and Rotation Speed.
- Added an Outline option to ESP's 3D mode.
- Added multi-select settings and collapsible setting sections.
- ClickGUI now opens as an overlay on any screen, and search finds modules by other clients' names.
- Auto Clicker can play back recorded click profiles, and its click timing is more human.
- Rewrote Bridge Assist with better edge detection and a new Skip Sneak option. It can also record you bridging and save the result as a profile.
- Weapons now uses two multi-select settings, Items and Enchantments. Existing settings carry over.
- Added Attack Delay, a client module that sets the time between attacks for Hit Select, Criticals and Sprint Reset. It replaces Hit Select's Pause Duration (your setting carries over) and Criticals' Cooldown.
- Fast Place's Smart mode falls back to regular fast place while bridging, and a new Bed Defense Guard option places at normal speed on your own bed defense.
- AxolotlClient's Freelook now works on every server.
- Backtrack can now show the target's real position with an ESP hitbox, which stays hidden from OBS.
- Fixed Bridge Assist double-sneaking, and its forced sneak being dropped when another mod overrides the sneak key.
- Fixed Backtrack and Blink replaying packets that another module like Velocity had canceled.
- Fixed Auto Extinguish and Auto Lava sometimes leaving their liquid behind on servers with ping.
- Fixed Fast Place and No Jump Delay not registering while the key was already held.
- Fixed the Backtrack ghost disappearing before the target caught up.
- Fixed module card and header text not being centered vertically.

# v0.3.0 - 2026-10-21

- Redesigned the click GUI and added a panel style.
- Added a button to the ClickGui module that resets the menu's positions.
- Click GUI frames no longer overlap: a dropped frame moves to the nearest free spot, and frames move out of the way when another one grows.
- Keybinds can now use mouse buttons, and modules can be set to only stay on while their key is held.
- Added Timer Range, which skips ahead toward your target when it would put you in reach.
- Added Auto Tool.
- Added Bed Plates.
- Added Chest ESP, a 3D mode for ESP, and an option to color ESP by team.
- Added Pre-Sneak and Strafe Align to Bridge Assist.
- Bridge Assist's sneaking and alignment are more reliable and no longer overshoot.
- Null Move and Bridge Assist now press and release the movement and sneak keys, so they show up on keystrokes overlays.
- Avoid Double-Sneaking now only places near block corners while sneaking diagonally backwards.
- Aim Assist is more human-like: it stays on the hovered target, holds steady when idle, and can be resisted by moving your mouse against it.
- Auto Clicker now randomizes press and release timing separately.
- Fixed a crash when Backtrack released a packet the game couldn't handle.
- Added a mod icon and updated the accent color.
- The dialog shown when Illusion is opened directly now links to Discord instead of Codeberg.

# v0.2.1 - 2026-10-16

- Added a documentation page listing every module and option.
- Auto Clicker's CPS now varies on a bell curve, so it stays near the set value more often and makes fewer big jumps.
- Illusion now requires Java 25.

# v0.2.0 - 2026-10-15

- Added Anti Debuff, which hides the negative visual effects.
- Added Backtrack, which lags other players as they move out of range.
- Added a Smart mode to No Jump Delay, which times jumps right before slabs and stairs so you step up them with the jump boost.
- Added a Stop When Resisting option to Aim Assist, which drops the target when you pull your mouse away from it.
- Added descriptions to all modules and options
- Module descriptions now show as a tooltip when you hover the module, instead of taking up a row in its settings.
- Made Freelook always work on multiplayer.
- Aim Assist's Mouse Pressed check now keeps aiming between clicks instead of cutting out each time the button is released.
- Fixed Auto Clicker not following Weapons module
- Fixed Aim Assist randomization getting cut off at the edge of the hitbox when multipoint is high.
- Fixed Aim Assist randomization changing speed with frame rate.

# v0.1.0 - 2026-10-10
Over a year in the making...

- Added Aim Assist module
- Added Auto Clicker module
- Added No Hit Delay module
- Added Weapons module
- Added Sprint module
- Added Null Move module
- Added No Jump Delay module
- Added Bridge Assist module
- Added Fast Place module
- Added ESP module
- Added Mod Overlay module
- Added ClickGui module
- Added Teams module
- Added Anti Bot module
