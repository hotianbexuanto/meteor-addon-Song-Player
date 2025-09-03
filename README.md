# SongPlayer Addon for Meteor Client

This is a Meteor Client addon that integrates the SongPlayer functionality, allowing you to play songs with noteblocks in Minecraft.

## Features

- Play MIDI and NBS files with noteblocks
- Automatic noteblock placement and tuning
- Playlist support
- Song items that can be shared with other players
- Various stage types (Default, Wide, Spherical)
- Configurable settings for creative/survival modes

## Installation

1. Download the latest release from the releases section
2. Place the jar file in your `.minecraft/mods` folder
3. Make sure you have Meteor Client installed
4. Launch Minecraft with the Fabric loader

## Usage

1. Place MIDI or NBS files in the `.minecraft/songs` folder
2. Use the `$play <filename>` command to play a song
3. The mod will automatically place the required noteblocks and play the song

## Commands

All SongPlayer commands are prefixed with `$` by default:

- `$play <filename or url>` - Play a song
- `$stop` - Stop playing
- `$skip` - Skip the current song
- `$loop` - Toggle song looping
- `$queue` - Show the current queue
- `$songs` - List available songs
- `$playlist` - Manage playlists
- `$help` - Show help information

For a complete list of commands and their usage, use `$help` in-game.

## Configuration

The mod creates a `SongPlayer` folder in your `.minecraft` directory with a `config.json` file where you can adjust various settings:

- Command prefix
- Creative/survival mode commands
- Stage type
- Movement settings (swing, rotate)
- Velocity threshold
- Break/place speeds
- And more...

## Building

To build the project yourself:

1. Clone this repository
2. Run `./gradlew build` in the project directory
3. The built jar will be in `build/libs/`

## Credits

- Original SongPlayer mod by hhhzzzsss
- Meteor Client by Meteor Development

## License

This template is available under the CC0 license. Feel free to use it for your own projects.
