# Unreleased - 2026-??-??

- Added Auto Block, which blocks with your sword right before you can take damage again. Its Force Block Animation option keeps your sword looking blocked between real blocks.
- Added Lag Range, which lags you to stay out of your opponent's range for easy first hits.
- Added Combat Jump, which jumps into fights and times jumps in trades so your hits land first and as criticals.
- Added Hit Select. It lets hits through while you close in, can burst click held back clicks once the target can take damage, and keeps out of the way of zest tapping.
- Added Sprint Reset, with a Dynamic mode that picks between W-tapping, S-tapping and zest tapping, and a slider for how long it stops for. Taps only get extended while the target is backing off.
- Hit Select and Sprint Reset predict when the server will let a target take damage again, including after hits from fishing rods, snowballs, eggs and arrows, so projectiles don't need "Use Server Attack Time".
- Added Blink, which holds back packets so you look like you teleport to other players.
- Added Clutch, which turns and places blocks under you when you fall off an edge or get knocked into the void. It looks ahead to where you will be, builds off blocks up to a few placements away, and turns back to wherever you move your mouse in the meantime.
- Added Block In, which surrounds you with blocks starting on the side facing your opponent. Toggle mode turns itself off when done, and Auto mode blocks you in when an opponent gets close while you stand still. It aims without moving your camera, and its Rotation Strafe option (Silent, Strict or Off) picks how your movement follows that aim.
- Added Timer, which speeds up or slows down the whole game.
- Added Criticals, which makes more of your hits land as critical hits. Lag mode holds your packets while you fall after a hit, so the server sees you falling for longer, and Tick mode skips ahead to the moment you start falling, like Timer Range.
- Added Auto Armor, which puts on the armor set that protects you the most, from your inventory or straight from your hotbar.
- Added Inventory Cleaner, which drops junk and worse gear, merges stacks and sorts your hotbar. It keeps exactly the armor Auto Armor would wear.
- Added Chest Stealer. It skips items Inventory Cleaner would drop, puts items straight into their hotbar slot, and can pause and miss-click like a real player.
- Added Refill, which moves soups and healing potions into your empty hotbar slots when you open your inventory.
- Inventory modules share one open inventory and take turns instead of reopening it. Their cursor travel time follows Fitts's law.
- Added Notifications, which shows a popup in the corner when a module is toggled or a profile is loaded. Each kind of popup can be turned off.
- Added Knockback Delay, which delays the knockback you take when hit, with distance, chance, maximum delay and condition options.
- Added Chams, which draws players through walls, skipping teammates and bots. It can draw them in a flat color with an opacity slider or at full brightness, and the same options can apply to your first-person hand. It works with Argentum's entity instancing and entity culling.
- Added Item ESP, which draws boxes around dropped items like iron, gold, diamonds, emeralds, golden apples and ender pearls. It can show stack counts and highlight everything else in one color.
- Added Fast Mine, which mines blocks faster and shortens the pause after breaking one. It can also count that pause down while you aren't mining, which looks the same as vanilla.
- Added Tags, which lets you tag players with `.tag <player> <tags>` in chat, or toggle "friend" by middle-clicking them. Combat modules skip players with a chosen tag when picking targets. Tagged players also show up in their own Tags category in the ClickGUI, where you can change their tags or click one to toggle "friend".
- Added Fake Block, which shows your sword as blocking without actually blocking. Only you can see it.
- Added Auto Heal, which eats soup and golden heads from your hotbar when your health drops low, uses two in a row after a big hit, and can drop the empty bowls.
- Added Indicators, which shows arrows around your crosshair pointing at incoming arrows, ender pearls and fireballs, with their distance.
- Added Pointers, which shows arrows around your crosshair pointing at other players, even ones outside your view. Pointers can be flat or tilted, colored by distance, name tag color or by hand, and pushed further out for faraway players.
- Added Instant Stop, which stops you as soon as you let go of your movement keys by briefly tapping the opposite keys. An option keeps it from tapping the key opposite the one you released.
- Added No Slow, which reduces the slowdown from blocking, eating, drinking and drawing bows. Each has its own speed, and sword blocking has NCP and Switch bypass modes.
- Added Auto Dodge, which strafes or jumps out of the way of incoming arrows, fireballs, fishing rod bobbers, snowballs, eggs and ender pearls without fighting Sprint Reset, Combat Jump or Bridge Assist.
- Added Name Tags, which replaces player name tags with ones that show health, distance, armor, enchantments and the tags you gave them.
- Added No Hurt Cam, which stops your camera from shaking when you take damage. If you use AxolotlClient or Argentum Extras, it also switches their hurt camera setting so both stay in sync.
- Added Bed Wars, which alerts you when enemies get near your bed or hold fireballs, TNT, invisibility potions, obsidian or ender pearls, and stops you from breaking your own bed or misplacing blocks around its defense.
- Added Auto Rod, which throws your fishing rod at opponents so it lands right as they can take damage again, then swaps back to your weapon.
- Added Auto Lava, which places lava from a bucket under opponents to burn and slow them, then picks it back up. It can wait for a critical so it pairs with Combat Jump.
- Added Auto Extinguish, which places water under you when you're on fire and picks it back up once the fire is out.
- Added Liquid Steal, which picks up water and lava your opponents place near you with an empty bucket from your hotbar. Water and lava can be turned off separately.
- Added config profiles under the new Profiles category. Each profile shows up there like a module: click it to switch, bind a key to it, and pick which categories (Client and Visual by default) keep your current settings when you switch. Create Profile saves your current settings as a new one. The Default profile always keeps the default settings, so changing anything on it makes a new profile for you.
- Added multi-select settings, which let you pick several entries from a list in the ClickGui.
- Added setting sections, which group related settings under collapsible headers. Dependent settings are indented beneath the setting they depend on.
- The click GUI now opens as an overlay on top of any screen, and draws correctly alongside other mods' screen rendering.
- Auto Clicker can now play back recorded click profiles.
- Auto Clicker's click timing is more human, with warm-up, exhaustion, hitches and varied hold times. Butterfly mode now has overlapping clicks.
- Rewrote Bridge Assist with better edge detection and block selection. It sneaks once per block, has a new Skip Sneak option, and no longer lets you fall off edges. Pre-Sneak, Strafe Align and Sneak On Jump's Jump Sneak Height option carry over.
- Bridge Assist can now record you bridging, set its Edge Offset, Unsneak Delay, Randomize and Jump Sneak Height options to match how you sneak, and save them as a new profile.
- Bridge Assist's options are grouped into General, Misc. Features, Sneak On Jump and Conditions.
- Weapons now uses two multi-select settings, Items and Enchantments, instead of seven separate toggles. Existing settings carry over.
- Fast Place's Smart mode now falls back to regular fast place while bridging.
- Added a Bed Defense Guard option to Fast Place, which places at normal speed on your own bed defense so you don't waste blocks like obsidian.
- AxolotlClient's Freelook now works on every server.
- Fixed Bridge Assist double-sneaking or sneaking when it didn't need to.
- Fixed Bridge Assist's forced sneak being dropped when another mod overrides the sneak key.
- Fixed lag-based modules (Backtrack, Blink) replaying packets that another module like Velocity had canceled.
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
