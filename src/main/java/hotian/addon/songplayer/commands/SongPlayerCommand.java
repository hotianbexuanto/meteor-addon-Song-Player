package hotian.addon.songplayer.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import hotian.addon.songplayer.songplayer.CommandProcessor;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class SongPlayerCommand extends Command {
    public SongPlayerCommand() {
        super("songplayer", "SongPlayer commands");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            // Process SongPlayer commands
            // This is a placeholder - in a real implementation, you would integrate
            // the SongPlayer command processing logic here
            return SINGLE_SUCCESS;
        });
    }
}