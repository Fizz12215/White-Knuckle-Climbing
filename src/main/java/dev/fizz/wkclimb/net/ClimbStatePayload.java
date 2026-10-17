package dev.fizz.wkclimb.net;

import dev.fizz.wkclimb.WKClimb;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Client -> server: whether the player is hanging from a hold this tick, and whether rod-like blocks
 * (#wkclimb:bars) should let them through (swinging from a bar), and whether White Knuckle mode
 * (its health, falls, breath and regen) is on. Also which hands hold on and where (world position),
 * which the server passes on to other players so their game shows the arms reaching (ClimbPosePayload).
 */
public record ClimbStatePayload(boolean hanging, boolean throughBars, boolean wkMode,
		boolean leftHolding, Vec3 leftHold, boolean rightHolding, Vec3 rightHold) implements CustomPacketPayload {
	/**
	 * v2: the hold spots were added after 0.5.0. A new name, so a client and server of different versions
	 * see that the other side lacks it (WKClimbClient's join check turns climbing off with a message)
	 * instead of failing to read each other's packets and disconnecting.
	 */
	public static final Type<ClimbStatePayload> TYPE = new Type<>(WKClimb.id("climb_state_v2"));
	public static final StreamCodec<ByteBuf, ClimbStatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, ClimbStatePayload::hanging,
			ByteBufCodecs.BOOL, ClimbStatePayload::throughBars,
			ByteBufCodecs.BOOL, ClimbStatePayload::wkMode,
			ByteBufCodecs.BOOL, ClimbStatePayload::leftHolding,
			Vec3.STREAM_CODEC, ClimbStatePayload::leftHold,
			ByteBufCodecs.BOOL, ClimbStatePayload::rightHolding,
			Vec3.STREAM_CODEC, ClimbStatePayload::rightHold,
			ClimbStatePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
