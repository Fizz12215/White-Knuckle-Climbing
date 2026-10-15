package dev.fizz.wkclimb.client;

import dev.fizz.wkclimb.sim.ClimbSim;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * White Knuckle's floor materials (ENT_Player.footsteps, values from the player prefab), matched to the
 * block under the player's feet. Each one sets friction, movement lerp, a speed multiplier and which
 * footstep sounds play. Minecraft's own block speed factor (soul sand, honey) multiplies on top.
 */
public final class ClimbFootsteps {
	private ClimbFootsteps() {}

	public record Material(ClimbSim.Footstep footstep, float minPitch, float maxPitch) {}

	public static final Material GENERIC = m("generic", 1, 1, 1, 0.95f, 1.0f);
	public static final Material METAL = m("metal", 1, 1, 1, 0.9f, 1.1f);
	public static final Material VENT = m("vent", 1, 1, 1, 0.9f, 1.1f);
	public static final Material CARPET = m("carpet", 1, 1, 1, 1.0f, 1.0f);
	public static final Material WATER = m("water", 1, 1, 1, 0.95f, 1.0f);
	public static final Material SLUDGE = m("sludge", 1.1, 0.9, 0.9, 0.95f, 1.0f);
	public static final Material FLESH = m("flesh", 1, 1, 1, 0.85f, 1.02f);
	public static final Material WOOD = m("wood", 1, 1, 1, 1.0f, 1.0f);
	public static final Material METAFUNGUS = m("metafungus", 1, 1, 1, 1.0f, 1.0f);
	/** Chimney's slippery floors: friction 0.05, moveLerp 0.2, moveSpeed 0.6. */
	public static final Material ICE = m("ice", 0.05, 0.2, 0.6, 1.4f, 1.6f);

	private static Material m(String name, double friction, double lerp, double speed, float minPitch, float maxPitch) {
		return new Material(new ClimbSim.Footstep(name, friction, lerp, speed), minPitch, maxPitch);
	}

	public static Material material(LocalPlayer player) {
		if (player.isInWater() || player.level().getFluidState(player.blockPosition()).is(net.minecraft.tags.FluidTags.WATER)) return WATER;
		BlockState state = player.getBlockStateOn();
		if (state.is(BlockTags.ICE) || state.getBlock().getFriction() > 0.9f) return ICE;
		SoundType sound = state.getSoundType();
		if (sound == SoundType.COPPER_GRATE) return VENT;
		if (sound == SoundType.METAL || sound == SoundType.ANVIL || sound == SoundType.CHAIN || sound == SoundType.COPPER
				|| sound == SoundType.NETHERITE_BLOCK || sound == SoundType.HEAVY_CORE || sound == SoundType.IRON) return METAL;
		if (sound == SoundType.WOOD || sound == SoundType.NETHER_WOOD || sound == SoundType.BAMBOO_WOOD
				|| sound == SoundType.CHERRY_WOOD || sound == SoundType.STEM || sound == SoundType.LADDER
				|| sound == SoundType.SCAFFOLDING) return WOOD;
		if (sound == SoundType.WOOL) return CARPET;
		if (sound == SoundType.MUD || sound == SoundType.SOUL_SAND || sound == SoundType.SOUL_SOIL
				|| sound == SoundType.HONEY_BLOCK) return SLUDGE;
		if (sound == SoundType.SLIME_BLOCK || sound == SoundType.SCULK || sound == SoundType.WART_BLOCK) return FLESH;
		if (isMetafungus(state)) return METAFUNGUS;
		return GENERIC;
	}

	/** Blocks that stand in for White Knuckle's metafungus: their footsteps and grab sound use its sets. */
	public static boolean isMetafungus(BlockState state) {
		SoundType sound = state.getSoundType();
		return sound == SoundType.FUNGUS || sound == SoundType.SHROOMLIGHT || sound == SoundType.NYLIUM
				|| sound == SoundType.MOSS || sound == SoundType.FROGLIGHT;
	}

	/** The material under the player, with Minecraft's block speed factor folded into moveSpeed. */
	public static ClimbSim.Footstep under(LocalPlayer player) {
		ClimbSim.Footstep f = material(player).footstep();
		// Entity.getBlockSpeedFactor(): the block at the feet, else the block underneath
		float factor = player.level().getBlockState(player.blockPosition()).getBlock().getSpeedFactor();
		if (factor == 1.0f) factor = player.getBlockStateOn().getBlock().getSpeedFactor();
		return factor == 1.0f ? f : new ClimbSim.Footstep(f.name(), f.friction(), f.moveLerp(), f.moveSpeed() * factor);
	}
}
