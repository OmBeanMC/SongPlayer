package com.github.hhhzzzsss.songplayer.mixin;

import com.github.hhhzzzsss.songplayer.playing.ProgressDisplay;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Shadow
    private int scaledWidth;

    @Shadow
    private int scaledHeight;

    @Shadow
    private int heldItemTooltipFade;
    //no idea what this mixin was originally supposed to do
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;F)V",
            at = @At("TAIL")
    )
    private void render(MatrixStack matrixStack, float tickDelta, CallbackInfo ci) {
        ProgressDisplay.getInstance().onRenderHUD(matrixStack, scaledWidth, scaledHeight, heldItemTooltipFade);
    }
}