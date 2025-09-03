package hotian.addon.songplayer.songplayer.mixin;

import hotian.addon.songplayer.songplayer.Config;
import hotian.addon.songplayer.songplayer.playing.SongHandler;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
    @Redirect(method = "tick()V", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerEntity;noClip:Z"))
    private void redirectNoClip(PlayerEntity instance, boolean value) {
        if (Config.getConfig().flightNoclip && !SongHandler.getInstance().isIdle())
            instance.noClip = instance.getAbilities().flying;
        else
            instance.noClip = value;
    }
}
