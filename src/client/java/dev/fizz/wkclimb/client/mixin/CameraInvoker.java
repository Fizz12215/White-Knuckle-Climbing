package dev.fizz.wkclimb.client.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraInvoker {
	/** The world's FOV for this partial tick, worked out now (Camera.getFov() is from the frame's start). */
	@Invoker("calculateFov")
	float wkclimb$calculateFov(float partialTick);

	/** The first-person hands' FOV for this partial tick (Minecraft draws them with their own projection). */
	@Invoker("calculateHudFov")
	float wkclimb$calculateHudFov(float partialTick);
}
