package dev.fizz.wkclimb.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerImplAccessor {
	@Accessor("aboveGroundTickCount")
	void wkclimb$setAboveGroundTickCount(int ticks);
}
