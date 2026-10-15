package dev.fizz.wkclimb.mixin;

import dev.fizz.wkclimb.WKClimb;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Minecraft stops a sneaking player at the edge of a block; White Knuckle's crouch has no such guard,
 * so in White Knuckle mode crouching lets you walk off ledges. Applied on both sides: the server replays
 * the client's movement with the same check, and would otherwise pull the player back to the edge.
 */
@Mixin(Player.class)
public abstract class NoSneakEdgeMixin {
	@Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
	private void wkclimb$noEdgeGuard(Vec3 movement, MoverType moverType, CallbackInfoReturnable<Vec3> cir) {
		if (WKClimb.isWkMode((Player) (Object) this)) cir.setReturnValue(movement);
	}
}
