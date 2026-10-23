# Unreleased - 2026-??-??

- Added Hit Select.
- Added Sprint Reset, with a Dynamic mode that picks between W-tapping, S-tapping and zest tapping, and a slider for how long it stops for.
- Added Blink, which holds back packets so you look like you teleport to other players.
- Added Timer, which speeds up or slows down the whole game.
- Auto Clicker can now play back recorded click profiles.
- Auto Clicker's butterfly mode ramps up its click speed, and clicks are spread on a log-normal curve.
- The click GUI now opens as an overlay on top of any screen, and draws correctly alongside other mods' screen rendering.
- Rewrote Bridge Assist with better edge detection and block selection. It now sneaks once per block, has a Skip Sneak option, and no longer lets you fall off edges.
- Fixed Bridge Assist double-sneaking or sneaking when it didn't need to.
- Fixed Bridge Assist's forced sneak being dropped when another mod overrides the sneak key.
- AxolotlClient's Freelook now works on every server.
- Fixed Fast Place and No Jump Delay not registering while the key was already held.
- Sprint Reset taps are only extended while the target is backing off.
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
