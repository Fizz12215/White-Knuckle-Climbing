package dev.fizz.wkclimb.client.mixin;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Post-pass uniform buffers are created read-only. For this mod's own passes (named "wkclimb:..."),
 * also allow copying into them, so the damage grade's strength can be updated every frame.
 */
@Mixin(PostPass.class)
public abstract class PostPassMixin {
	@Shadow @Final private String name;

	@ModifyArg(method = "<init>", at = @At(value = "INVOKE",
			target = "Lcom/mojang/renderpearl/api/device/GpuDevice;createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/renderpearl/api/buffers/GpuBuffer;"),
			index = 1)
	private int wkclimb$writableUniforms(int usage) {
		return name != null && name.startsWith("wkclimb:") ? usage | GpuBuffer.USAGE_COPY_DST : usage;
	}
}
