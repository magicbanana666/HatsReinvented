# Hats Reinvented

**Mobs spawn wearing hats. Kill them to collect the hats, then wear and customise them.** For **Minecraft 26.1.2 / NeoForge**.

## Features

- **213 hats and 275 accessories**, as Tabula `.tbl` files in the `hats` folder of your game directory. Add your own hats there.
- Mobs spawn with a random hat (10% by default) with a **rarity** (common to legendary) and random **accessories**. Bosses get more and rarer hats.
- Kill a mob to **collect its hat** and the accessories it wore. A toast tells you what you unlocked.
- Press **H** for the hats menu: pick a hat, toggle accessories, **colour** it (RGB, opacity, hue, saturation, brightness), give it a **glint**, favourite it. Creative players can wear every hat.
- Hats sit on top of the head of **any** mob, vanilla or modded: the head is found automatically from the model ([BananaUtil](https://github.com/magicbanana666/BananaUtil)'s `HeadLocator`).
- A mob that kills a mob with a hat takes its hat.
- Works with [**Morphing Time**](https://github.com/magicbanana666/MorphingTime): your morph wears your hat.

## Commands

`/hats give <players> <count> <hat>`, `/hats set <entities> <allAccessories> <hat>`, `/hats remove <entities>`, `/hats clear <players>`, `/hats list`, `/hats reload`, `/hats reextract`

## Requirements

- [BananaUtil](https://github.com/magicbanana666/BananaUtil)

## License

[Don't Be an Asshole License](LICENSE.md): do whatever you want with it, port it, share it, just don't be an asshole.
