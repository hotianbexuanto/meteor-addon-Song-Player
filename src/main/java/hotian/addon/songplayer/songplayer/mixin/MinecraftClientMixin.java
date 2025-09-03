package hotian.addon.songplayer.songplayer.mixin;

import hotian.addon.songplayer.songplayer.SongPlayer;
import hotian.addon.songplayer.songplayer.Util;
import hotian.addon.songplayer.songplayer.item.SongItemConfirmationScreen;
import hotian.addon.songplayer.songplayer.item.SongItemUtils;
import hotian.addon.songplayer.songplayer.playing.ProgressDisplay;
import hotian.addon.songplayer.songplayer.playing.SongHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
	@Shadow
	public HitResult crosshairTarget;

	@Shadow
	private int itemUseCooldown;

	@Inject(at = @At("HEAD"), method = "render(Z)V")
	public void onRender(boolean tick, CallbackInfo ci) {
		if (SongPlayer.MC.world != null && SongPlayer.MC.player != null && SongPlayer.MC.interactionManager != null) {
			SongHandler.getInstance().onUpdate(false);
		} else {
			SongHandler.getInstance().onNotIngame();
		}
	}

	@Inject(at = @At("HEAD"), method = "tick()V")
	public void onTick(CallbackInfo ci) {
		if (SongPlayer.MC.world != null && SongPlayer.MC.player != null && SongPlayer.MC.interactionManager != null) {
			SongHandler.getInstance().onUpdate(true);
		}
		ProgressDisplay.getInstance().onTick();
	}

	@Inject(at = @At("HEAD"), method = "doItemUse()V", cancellable = true)
	private void onDoItemUse(CallbackInfo ci) {
		if (crosshairTarget != null) {
			if (crosshairTarget.getType() == HitResult.Type.ENTITY) {
				EntityHitResult entityHitResult = (EntityHitResult)this.crosshairTarget;
				Entity entity = entityHitResult.getEntity();
				if (entity instanceof ItemFrameEntity || entity instanceof GlowItemFrameEntity) {
					return;
				}
			}
			else if (crosshairTarget.getType() == HitResult.Type.BLOCK) {
				BlockHitResult blockHitResult = (BlockHitResult)this.crosshairTarget;
				BlockEntity blockEntity = SongPlayer.MC.world.getBlockEntity(blockHitResult.getBlockPos());
				if (blockEntity != null && blockEntity instanceof LockableContainerBlockEntity) {
					return;
				}
			}
		}

		ItemStack stack = SongPlayer.MC.player.getStackInHand(Hand.MAIN_HAND);
		if (SongItemUtils.isSongItem(stack)) {
			try {
				SongPlayer.MC.setScreen(new SongItemConfirmationScreen(stack));
			} catch (Exception e) {
				Util.showChatMessage("§c加载歌曲物品失败: §4" + e.getMessage());
			}
			itemUseCooldown = 4;
			ci.cancel();
		}
	}
}
