package dev.fizz.wkclimb;

import dev.fizz.wkclimb.mixin.ServerGamePacketListenerImplAccessor;
import dev.fizz.wkclimb.net.ClimbStatePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WKClimb implements ModInitializer {
	public static final String MOD_ID = "wkclimb";

	/** Blocks that can be grabbed when "handholds_only" is on in the config. */
	public static final TagKey<Block> HANDHOLDS = TagKey.create(Registries.BLOCK, id("handholds"));
	/** Never grabbable. */
	public static final TagKey<Block> UNCLIMBABLE = TagKey.create(Registries.BLOCK, id("unclimbable"));
	/** CL_Handhold_Icy: the hand slides and slips after 3 s. */
	public static final TagKey<Block> ICY = TagKey.create(Registries.BLOCK, id("icy_handholds"));
	/** Grabbable anywhere, not just at the top edge (ladders, vines, chains, bars). */
	public static final TagKey<Block> GRAB_ANYWHERE = TagKey.create(Registries.BLOCK, id("grab_anywhere"));
	/** Uses White Knuckle's chain grab sound. */
	public static final TagKey<Block> CHAIN_SOUND = TagKey.create(Registries.BLOCK, id("chain_sound"));
	/** Rod-like blocks you swing from: White Knuckle's Handhold-Bar preset. */
	public static final TagKey<Block> BARS = TagKey.create(Registries.BLOCK, id("bars"));
	/** The climbMult 0.8 handhold variant. */
	public static final TagKey<Block> SLOW = TagKey.create(Registries.BLOCK, id("slow_handholds"));

	private static final Set<UUID> HANGING = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> THROUGH_BARS = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> WK_MODE = ConcurrentHashMap.newKeySet();
	/** Set by the client while White Knuckle mode is on for the local player. */
	public static volatile boolean clientWkMode;

	/** Whether this player plays by White Knuckle's health rules right now. */
	public static boolean isWkMode(net.minecraft.world.entity.Entity entity) {
		if (!(entity instanceof net.minecraft.world.entity.player.Player player)) return false;
		if (player.level().isClientSide()) return clientWkMode && player.isLocalPlayer();
		return WK_MODE.contains(player.getUUID());
	}
	/** Set by the client for the local player (the only player whose movement the client simulates). */
	public static volatile boolean clientThroughBars;

	/**
	 * A rod is a #wkclimb:bars block whose visible shape is thin (under half a block) along at least two
	 * axes: a chain, an end or lightning rod, bamboo, an unconnected iron-bar post. Iron bars joined into
	 * a pane are wide along one axis, so they count as a wall (a climbable grate), not a rod.
	 */
	public static boolean isRod(net.minecraft.world.level.block.state.BlockState state,
			net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
		if (!state.is(BARS)) return false;
		net.minecraft.world.phys.shapes.VoxelShape shape = state.getShape(level, pos);
		if (shape.isEmpty()) return false;
		net.minecraft.world.phys.AABB b = shape.bounds();
		int thin = 0;
		if (b.getXsize() < 0.5) thin++;
		if (b.getYsize() < 0.5) thin++;
		if (b.getZsize() < 0.5) thin++;
		return thin >= 2;
	}

	/** Whether rod-like blocks should not collide with this entity right now. */
	public static boolean passesThroughBars(net.minecraft.world.entity.Entity entity) {
		if (!(entity instanceof net.minecraft.world.entity.player.Player player)) return false;
		if (player.level().isClientSide()) return clientThroughBars && player.isLocalPlayer();
		return THROUGH_BARS.contains(player.getUUID());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(ClimbStatePayload.TYPE, ClimbStatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(dev.fizz.wkclimb.net.FallGripPayload.TYPE, dev.fizz.wkclimb.net.FallGripPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ClimbStatePayload.TYPE, (payload, context) -> {
			UUID id = context.player().getUUID();
			if (payload.hanging()) HANGING.add(id);
			else HANGING.remove(id);
			if (payload.throughBars()) THROUGH_BARS.add(id);
			else THROUGH_BARS.remove(id);
			if (payload.wkMode()) WK_MODE.add(id);
			else {
				WK_MODE.remove(id);
				WKHealth.forget(id);
			}
		});
		// A respawn starts White Knuckle's health state fresh (breath, falls, heal timer).
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
				(oldPlayer, newPlayer, alive) -> WKHealth.forget(newPlayer.getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			HANGING.remove(handler.getPlayer().getUUID());
			THROUGH_BARS.remove(handler.getPlayer().getUUID());
			WK_MODE.remove(handler.getPlayer().getUUID());
			WKHealth.forget(handler.getPlayer().getUUID());
		});

		// A hanging player is holding on: no fall distance builds up and the anti-fly check stays quiet.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!WK_MODE.isEmpty()) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (WK_MODE.contains(player.getUUID())) WKHealth.tick(player, HANGING.contains(player.getUUID()));
				}
			}
			if (HANGING.isEmpty()) return;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!HANGING.contains(player.getUUID())) continue;
				player.resetFallDistance();
				((ServerGamePacketListenerImplAccessor) player.connection).wkclimb$setAboveGroundTickCount(0);
			}
		});
	}
}
