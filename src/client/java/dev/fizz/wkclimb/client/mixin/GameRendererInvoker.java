package dev.fizz.wkclimb.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererInvoker {
	@Invoker("getFov")
	float wkclimb$getFov(Camera camera, float partialTick, boolean useFovSetting);

	@Invoker("setPostEffect")
	void wkclimb$setPostEffect(net.minecraft.resources.ResourceLocation id);
}
