<h1 align="center">
  SkyReview: Mod for Hypixel SkyBlock
</h1>

<div align="center">

<img src="src/main/resources/assets/skyreview/icon.png" width="96" height="96" alt="SkyReview Logo"/>

[![Discord](https://img.shields.io/badge/Discord-Join%20Community-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/KpVndhhNDr)

</div>

## What it does

SkyReview is a Fabric Minecraft mod for [Hypixel SkyBlock](https://hypixel.net/categories/skyblock/) that adds a transparent player reputation and review system.

* **Player Ratings:** Submit and view 1-5 star reviews with category tags (Dungeons, Kuudra, Safari, Other).
* **Party Finder Integration:** Hover over party members in chat or menus to instantly see their reputation and average rating.
* **Teammate Tracking:** Automatically records recent party members and prevents fake reviews by verifying played-together history.
* **Stats & Profile:** Inspect dungeon floor completions, Kuudra tiers, and player bios.

## Commands

* `/sr` - Open the main menu and leaderboard.
* `/sr <player>` - Open the profile and reviews of a player.
* `/sr me` - Open your own profile, bio, and reviews.
* `/sr rate [player]` - Rate a player you played with.
* `/sr history` - View recently recorded teammates.
* `/sr reload` - Refresh ratings and cache.
* `/sr help` - More commands.

## Building from source

Requirements: JDK 21+

```bash
# Windows
gradlew.bat build -x test

# Linux / macOS
./gradlew build -x test
```

The compiled file is saved to `build/libs/SkyReview-26.1.2-0.9.9.jar`.

## Community

Join the [Discord server](https://discord.gg/KpVndhhNDr) for news, bug reports, and updates.
