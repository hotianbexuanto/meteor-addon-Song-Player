package hotian.addon.songplayer.modules;

import hotian.addon.songplayer.SongPlayerAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import hotian.addon.songplayer.songplayer.playing.SongHandler;
import hotian.addon.songplayer.songplayer.CommandProcessor;
import hotian.addon.songplayer.songplayer.SongPlayer;
import hotian.addon.songplayer.songplayer.Config;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.ProvidedStringSetting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SongPlayerModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> songName = sgGeneral.add(new ProvidedStringSetting.Builder()
            .name("song-name")
            .description("The name of the song to play")
            .defaultValue("")
            .supplier(this::getAvailableSongsArray)
            .build()
    );

    public SongPlayerModule() {
        super(SongPlayerAddon.CATEGORY, "song-player", "A module to play songs with noteblocks.");
    }

    @Override
    public void onActivate() {
        // Play the song when the module is activated
        if (!songName.get().isEmpty()) {
            SongHandler.getInstance().loadSong(songName.get());
        }
    }

    @Override
    public void onDeactivate() {
        // Stop the song when the module is deactivated using the same logic as the "stop" command
        if (!SongHandler.getInstance().isIdle()) {
            // This replicates the logic from CommandProcessor.stopCommand
            if (SongHandler.getInstance().cleaningUp) {
                SongHandler.getInstance().restoreStateAndReset();
            } else if (Config.getConfig().autoCleanup && SongHandler.getInstance().originalBlocks.size() != 0) {
                SongHandler.getInstance().partialResetAndCleanup();
            } else {
                SongHandler.getInstance().restoreStateAndReset();
            }
        }
    }
    
    // Get list of available songs from the songs directory as an array
    private String[] getAvailableSongsArray() {
        List<String> songs = getAvailableSongs();
        return songs.toArray(new String[0]);
    }
    
    // Get list of available songs from the songs directory
    public static List<String> getAvailableSongs() {
        try {
            if (!Files.exists(SongPlayer.SONG_DIR)) {
                return new ArrayList<>();
            }
            
            return Files.list(SongPlayer.SONG_DIR)
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }
}