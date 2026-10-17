package dev.fizz.wkclimb.gametest;

import dev.fizz.wkclimb.client.ClimbController;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import org.lwjgl.sdl.SDLMouse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * WKCLIMB_TEST_MP=1: this client climbs on a dedicated server (port 25570) while a second client
 * ("Viewer", started by hand with runClient) joins and watches from the side.
 */
final class MultiplayerScene {
	private static final Logger LOG = LoggerFactory.getLogger("wkclimb-mp");

	static void run(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> ClimbController.INSTANCE.setEnabled(true));
		Properties props = new Properties();
		props.setProperty("max-players", "4");
		props.setProperty("server-port", "25570");
		try (TestDedicatedServerContext ds = ctx.worldBuilder().createServer(props)) {
			TestDedicatedServerConnection conn = ds.connect();
			try {
				ctx.waitTicks(60);
				ds.runCommand("gamemode survival @a");
				ds.runCommand("whitelist off");
				ds.runCommand("difficulty peaceful");
				ds.runCommand("time set noon");
				ds.runCommand("gamerule advance_time false");
				ds.runCommand("fill -4 -60 -3 4 -58 -2 minecraft:stone");
				ds.runCommand("fill -4 -57 -6 4 -55 -5 minecraft:stone_bricks");
				ds.runCommand("tp @a 0.5 -60 0.0 180 -55");
				LOG.info("[wkclimb-mp] READY for the viewer on port 25570");
				ds.waitFor(s -> s.getPlayerList().getPlayerCount() >= 2, 20 * 180);
				int joined = ds.computeOnServer(s -> s.getPlayerList().getPlayerCount());
				LOG.info("[wkclimb-mp] viewer joined: {} players", joined);
				String me = ctx.computeOnClient(mc -> mc.player.getName().getString());
				String viewer = "@a[name=!" + me + "]";
				LOG.info("[wkclimb-mp] climber is {}", me);
				ds.runCommand("gamemode creative " + viewer);
				ctx.waitTicks(100); // the viewer's world loads

				for (int round = 0; round < 3; round++) {
					ds.runCommand("tp " + me + " 0.5 -60 0.0 180 -55");
					ds.runCommand(round == 1
							? "tp " + viewer + " -2.5 -60 1.5 facing 0.5 -58.2 -2.0"
							: "tp " + viewer + " 3.5 -60 2.0 facing 0.5 -58.2 -2.0");
					ctx.waitTicks(30);
					ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_LEFT);
					ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_RIGHT);
					ctx.waitTicks(60); // hanging
					boolean hanging = ctx.computeOnClient(mc -> ClimbController.INSTANCE.sim.anyHanging());
					LOG.info("[wkclimb-mp] round {}: hanging = {}", round, hanging);
					ctx.takeScreenshot("mp-climber-" + round + "-hanging");
					if (round == 0) {
						ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
						ctx.waitTicks(2);
						ctx.takeScreenshot("mp-climber-0-f5-front");
						ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
						ctx.waitTicks(2);
						ctx.takeScreenshot("mp-climber-0-f5-back");
						ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
						ctx.runOnClient(mc -> dev.fizz.wkclimb.ClimbConfig.minecraftHands = true);
						ctx.waitTicks(2);
						ctx.takeScreenshot("mp-climber-0-minecraft-arms");
						ctx.runOnClient(mc -> dev.fizz.wkclimb.ClimbConfig.minecraftHands = false);
					}
					ctx.getInput().holdKey(o -> o.keyUp);
					ctx.waitTicks(40); // pull up
					ctx.getInput().releaseKey(o -> o.keyUp);
					ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_LEFT);
					ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_RIGHT);
					ctx.waitTicks(20);
					double y = ctx.computeOnClient(mc -> mc.player.getY());
					LOG.info("[wkclimb-mp] round {}: after the pull-up y = {} (ledge top -57)", round, y);
					ctx.takeScreenshot("mp-climber-" + round + "-up");
				}
				int players = ds.computeOnServer(s -> s.getPlayerList().getPlayerCount());
				LOG.info("[wkclimb-mp] DONE, players still on the server: {}", players);
				ctx.waitTicks(40);
			} finally {
				conn.close();
			}
		}
	}
}
