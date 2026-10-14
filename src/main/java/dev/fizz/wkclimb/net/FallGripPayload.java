package dev.fizz.wkclimb.net;

import dev.fizz.wkclimb.WKClimb;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a White Knuckle fall just hurt the player. grab = it ended by catching a hold
 * ("fall-grab": each holding hand loses 3 grip and splatters blood); land = it ended on the ground.
 */
public record FallGripPayload(boolean grab, boolean land) implements CustomPacketPayload {
	public static final Type<FallGripPayload> TYPE = new Type<>(WKClimb.id("fall_grip"));
	public static final StreamCodec<ByteBuf, FallGripPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, FallGripPayload::grab,
			ByteBufCodecs.BOOL, FallGripPayload::land,
			FallGripPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
