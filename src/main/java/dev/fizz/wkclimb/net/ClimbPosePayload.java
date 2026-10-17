package dev.fizz.wkclimb.net;

import dev.fizz.wkclimb.WKClimb;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server -> client: another player's hands (which hold on, and where in the world), so this client can
 * draw that player's arms reaching to the holds. Sent to everyone who can see them when it changes.
 */
public record ClimbPosePayload(int entityId, boolean leftHolding, Vec3 leftHold, boolean rightHolding, Vec3 rightHold)
		implements CustomPacketPayload {
	public static final Type<ClimbPosePayload> TYPE = new Type<>(WKClimb.id("climb_pose"));
	public static final StreamCodec<ByteBuf, ClimbPosePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ClimbPosePayload::entityId,
			ByteBufCodecs.BOOL, ClimbPosePayload::leftHolding,
			Vec3.STREAM_CODEC, ClimbPosePayload::leftHold,
			ByteBufCodecs.BOOL, ClimbPosePayload::rightHolding,
			Vec3.STREAM_CODEC, ClimbPosePayload::rightHold,
			ClimbPosePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
