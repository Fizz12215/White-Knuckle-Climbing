package dev.fizz.wkclimb.mixin;

import dev.fizz.wkclimb.WKClimb;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a player swings from a rod, rods don't collide with them (White Knuckle's bar handholds are
 * hand-only colliders). Walls of bars (joined iron-bar panes) stay solid: only blocks that are thin
 * in two directions count (WKClimb.isRod). Applied on both sides so the server agrees with the client.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BarCollisionMixin {
	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
			at = @At("HEAD"), cancellable = true)
	private void wkclimb$throughBars(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (context instanceof EntityCollisionContext entityContext && entityContext.getEntity() != null
				&& WKClimb.passesThroughBars(entityContext.getEntity())
				&& WKClimb.isRod((net.minecraft.world.level.block.state.BlockState) (Object) this, level, pos)) {
			cir.setReturnValue(Shapes.empty());
		}
	}
}
