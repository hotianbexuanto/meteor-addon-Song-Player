package hotian.addon.songplayer.songplayer.mixin;

import hotian.addon.songplayer.songplayer.SongPlayer;
import hotian.addon.songplayer.songplayer.Util;
import hotian.addon.songplayer.songplayer.playing.SongHandler;
import hotian.addon.songplayer.songplayer.playing.Stage;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Used for debugging purposes

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Inject(at = @At("HEAD"), method = "handleBlockUpdate(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)V", cancellable = true)
    public void onHandleBlockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        Stage stage = SongHandler.getInstance().stage;
        if (stage != null && !SongHandler.getInstance().building) {
            for (BlockPos nbp : stage.noteblockPositions.values()) {
                if (nbp.equals(pos)) {
                    BlockState oldState = SongPlayer.MC.world.getBlockState(pos);
                    if (oldState.equals(state))
                        return;
                    Util.showChatMessage(String.format("§7舞台中的方块从 §2%s §7变为 §2%s", oldState.toString(), state.toString()));
                    break;
                }
            }
        }
    }
}
