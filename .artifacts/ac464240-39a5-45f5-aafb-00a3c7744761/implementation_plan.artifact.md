# Remake Discord Rich Presence Integration

Recreate and simplify the Discord Rich Presence implementation to provide a robust, clean, and headache-free experience based on official Discord guidelines and developer documentation.

## User Review Required

> [!IMPORTANT]
> This will replace the complex multi-file gateway and asset registrar setup with a streamlined, direct, and self-contained Discord presence client (`MeowDiscordClient.kt` and `MeowDiscordModels.kt`), making it much easier to maintain, port, or modify.

## Proposed Changes

### [Component Name] Discord Presence Integration

#### [NEW] [MeowDiscordClient.kt](file:///C:/Users/Rexx-Gaming-PC/AndroidStudioProjects/Meow/app/src/main/java/com/meow/music/discord/MeowDiscordClient.kt)
- A clean, modern, and reliable WebSocket/gateway client that handles token authentication, heartbeat, and activity presence updates without brittle complexity.

#### [NEW] [MeowDiscordModels.kt](file:///C:/Users/Rexx-Gaming-PC/AndroidStudioProjects/Meow/app/src/main/java/com/meow/music/discord/MeowDiscordModels.kt)
- Simplified data models for activity, song metadata, assets, and timestamps.

#### [MODIFY] [DiscordPresence.kt](file:///C:/Users/Rexx-Gaming-PC/AndroidStudioProjects/Meow/app/src/main/java/com/meow/music/discord/DiscordPresence.kt)
- Update to use the new `MeowDiscordClient` for seamless presence syncing, sign-in, and clearing.

## Verification Plan

### Automated Tests
- Build verification via `gradle_build` to ensure zero compilation errors.

### Manual Verification
- Verify Discord presence settings toggle and music playback state updates correctly.
