package dev.fizz.wkclimb.gametest;

import dev.fizz.wkclimb.client.ClimbController;
import dev.fizz.wkclimb.client.ClimbHands;
import dev.fizz.wkclimb.client.ClimbSounds;
import dev.fizz.wkclimb.sim.ClimbSim;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLScancode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * End-to-end check in a real client on a superflat world (ground surface y=-61, player feet at y=-60).
 *   - a 3-high stone wall to the north (top at y=-57): its ledge is grabbable
 *   - a 16-high stone wall further east: its side is not
 *   - a 3-high packed-ice wall to the south: icy ledge
 * Set WKCLIMB_TEST_PACK to the extracted "White Knuckle Assets" folder to test with the real sprites/sounds.
 */
public class ClimbClientGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("wkclimb-gametest");
	private static final String PACK = "White Knuckle Assets";

	@Override
	public void runTest(ClientGameTestContext ctx) {
		boolean withPack = installPack(ctx);
		settingsScreen(ctx);

		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			ctx.waitTicks(60); // 26.3: no waitForChunksRender
			var server = sp.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("difficulty peaceful");
			server.runCommand("fill -4 -60 -3 4 -58 -2 minecraft:stone");
			server.runCommand("fill 10 -60 -3 14 -45 -2 minecraft:stone");
			server.runCommand("fill -4 -60 10 4 -58 11 minecraft:packed_ice");
			server.runCommand("fill -10 -61 40 10 -61 80 minecraft:packed_ice");
			ctx.waitTicks(5);

			// The gametest framework restores default options at test start (after the mod's first-tick
			// apply), so apply White Knuckle's bindings again here and check them.
			ctx.runOnClient(mc -> dev.fizz.wkclimb.client.ClimbKeybinds.apply(mc.options));
			LOG.info("[wkclimb] keys: sprint={} sneak={} inventory={} applied={}",
					ctx.computeOnClient(mc -> mc.options.keySprint.saveString()), ctx.computeOnClient(mc -> mc.options.keyShift.saveString()),
					ctx.computeOnClient(mc -> mc.options.keyInventory.saveString()), dev.fizz.wkclimb.ClimbConfig.wkKeybindsApplied);
			check(ctx, "White Knuckle default keys: sprint LShift, crouch LCtrl, inventory Tab", mc ->
					mc.options.keySprint.getDefaultKey() != null
					&& mc.options.keySprint.saveString().equals("key.keyboard.left.shift")
					&& mc.options.keyShift.saveString().equals("key.keyboard.left.control")
					&& mc.options.keyInventory.saveString().equals("key.keyboard.tab"));
			server.runCommand("setblock 1 -59 -1 minecraft:stone_button[face=wall,facing=south]");

			if (withPack) {
				check(ctx, "WK hand sprites loaded", mc -> ClimbHands.isActive());
				check(ctx, "WK crosshair loaded", mc -> dev.fizz.wkclimb.client.ClimbCrosshair.available());
				check(ctx, "WK climbing sounds loaded", mc -> mc.getSoundManager().getSoundEvent(ClimbSounds.HANDHOLD.event().location()) != null
						&& mc.getSoundManager().getSoundEvent(ClimbSounds.FATIGUE_LEVEL.event().location()) != null);
			}

			// --- The side of a tall wall is not a ledge ---
			server.runCommand("tp @a 12.5 -60 0.0 180 0");
			ctx.waitTicks(10);
			ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.waitTicks(4);
			check(ctx, "looking at the middle of a wall: nothing to grab", mc -> ClimbController.INSTANCE.hovered() == null && !hand(0).isHanging());
			ctx.takeScreenshot("wkclimb-01-wall-side");
			ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_LEFT);

			// --- A ledge: both hands catch its top edge ---
			// (aim 55 degrees up: the ledge edge is 1 block ahead and 1.56 above White Knuckle's 1.44 eye, 57.3
			// degrees up, so the aim line passes 0.076 blocks from it, inside the 0.12-block catch radius)
			server.runCommand("tp @a 0.5 -60 0.0 180 -55");
			ctx.waitTicks(10);
			ctx.takeScreenshot("wkclimb-02-hands-at-rest");
			double y0 = playerY(ctx);
			ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_RIGHT);
			ctx.waitTicks(3);
			check(ctx, "both hands grab the ledge", mc -> hand(0).isHanging() && hand(1).isHanging());
			double holdY = ctx.computeOnClient(mc -> ClimbController.toMcPublic(hand(0).holdPoint()).y);
			LOG.info("[wkclimb] hold point y = {} (ledge top is -57)", holdY);
			if (Math.abs(holdY - -57.0) > 1e-6) throw new AssertionError("hold not snapped to the ledge: " + holdY);
			ctx.waitTicks(10);
			ctx.takeScreenshot("wkclimb-03-grabbed-ledge");

			// --- Grip drains while hanging off the ground ---
			double g0 = hand(0).grip();
			ctx.waitTicks(40);
			double g1 = hand(0).grip();
			LOG.info("[wkclimb] grip over 2 s hanging with two hands: {} -> {} (expected about -1.0)", g0, g1);
			if (!(g1 < g0 - 0.7 && g1 > g0 - 1.3)) throw new AssertionError("grip drain " + g0 + " -> " + g1);

			// --- Pull up and over the ledge ---
			ctx.getInput().holdKey(o -> o.keyUp);
			ctx.waitTicks(40);
			double y1 = playerY(ctx);
			ctx.takeScreenshot("wkclimb-04-mantled");
			ctx.getInput().releaseKey(o -> o.keyUp);
			ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_RIGHT);
			ctx.waitTicks(10);
			double yTop = playerY(ctx);
			LOG.info("[wkclimb] climbing: y {} -> {} while holding, {} after letting go (top of wall is -57)", y0, y1, yTop);
			if (yTop < -57.01) throw new AssertionError("did not get up onto the ledge, y=" + yTop);

			// --- A button on the wall works as a handhold ---
			server.runCommand("tp @a 1.5 -60 0.5 180 5");
			ctx.waitTicks(30);
			ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.waitTicks(3);
			check(ctx, "grabs the stone button on the wall", mc -> hand(0).isHanging()
					&& mc.level.getBlockState(net.minecraft.core.BlockPos.containing(ClimbController.toMcPublic(hand(0).holdPoint())))
							.is(net.minecraft.tags.BlockTags.BUTTONS));
			ctx.takeScreenshot("wkclimb-03a-button");
			ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.waitTicks(30);

			// --- A ledge below you: stand on the wall and grab its edge at your feet ---
			server.runCommand("tp @a 0.5 -57 -1.5 0 72");
			ctx.waitTicks(20);
			check(ctx, "standing on top of the wall", mc -> mc.player.onGround() && Math.abs(mc.player.getY() - -57.0) < 1e-6);
			ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.waitTicks(3);
			check(ctx, "grabs the ledge below, at the wall's top edge", mc -> hand(0).isHanging());
			double belowY = ctx.computeOnClient(mc -> ClimbController.toMcPublic(hand(0).holdPoint()).y);
			double belowZ = ctx.computeOnClient(mc -> ClimbController.toMcPublic(hand(0).holdPoint()).z);
			LOG.info("[wkclimb] ledge-below hold at y={} z={} (edge is y=-57, z=-1)", belowY, belowZ);
			if (Math.abs(belowY - -57.0) > 1e-6 || Math.abs(belowZ - -1.0) > 1e-6) throw new AssertionError("wrong ledge-below hold");
			ctx.takeScreenshot("wkclimb-03b-ledge-below");
			// step off the edge and hang from it
			for (int t = 0; t < 60; t += 10) {
				ctx.getInput().holdKey(o -> o.keyUp);
				ctx.waitTicks(10);
				LOG.info("[wkclimb]   stepping off: t={} hanging={} pos={} onGround={}", t, hand(0).isHanging(),
						ctx.computeOnClient(mc -> mc.player.position().toString()), ctx.computeOnClient(mc -> mc.player.onGround()));
			}
			ctx.getInput().releaseKey(o -> o.keyUp);
			ctx.waitTicks(20);
			LOG.info("[wkclimb] after stepping off, hanging={} pos={} onGround={}", hand(0).isHanging(),
					ctx.computeOnClient(mc -> mc.player.position().toString()), ctx.computeOnClient(mc -> mc.player.onGround()));
			ctx.takeScreenshot("wkclimb-03c-hanging-below-edge");
			check(ctx, "hanging from the ledge below the top", mc -> hand(0).isHanging() && !mc.player.onGround() && mc.player.getY() < -57.5);
			ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.waitTicks(30);

			// --- Climbing jump: both hands let go, then catch the ledge again ---
			server.runCommand("tp @a 0.5 -60 0.0 180 -55");
			ctx.waitTicks(30);
			ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_RIGHT);
			ctx.waitTicks(15);
			double yJump = playerY(ctx);
			ctx.getInput().holdKey(o -> o.keyJump);
			ctx.waitTicks(2);
			ctx.getInput().releaseKey(o -> o.keyJump);
			boolean releasedOnJump = !hand(0).isHanging() && !hand(1).isHanging();
			boolean wkAir = ctx.computeOnClient(mc -> ClimbController.INSTANCE.isAirborneFromClimb());
			LOG.info("[wkclimb] after the jump, WK air physics in charge: {}", wkAir);
			if (!wkAir) throw new AssertionError("WK air physics not active after a climbing jump");
			ctx.waitTicks(4);
			LOG.info("[wkclimb] jump: released={} y {} -> {}", releasedOnJump, yJump, playerY(ctx));
			if (!releasedOnJump) throw new AssertionError("jump did not release the hands");
			ctx.waitTicks(20);
			check(ctx, "a hand catches a ledge again after the jump while the buttons are held",
					mc -> hand(0).isHanging() || hand(1).isHanging());
			ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_RIGHT);
			ctx.waitTicks(2);
			check(ctx, "releasing the buttons lets go", mc -> !hand(0).isHanging() && !hand(1).isHanging());
			ctx.waitTicks(20);
			check(ctx, "landed: vanilla movement is back", mc -> mc.player.onGround() && !ClimbController.INSTANCE.isAirborneFromClimb());

			// --- One hand until fatigue; screenshots as the hand reddens ---
			// (the full 10 s one-hand drain is in ClimbSimTest; here: 3 grip at 1/s drops at 2.9 s = 58 ticks)
			server.runCommand("tp @a 0.5 -60 0.0 180 -55");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> ClimbController.INSTANCE.sim.resetHands(3.0));
			ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.waitTicks(3);
			check(ctx, "left hand hanging alone", mc -> hand(0).isHanging() && !hand(1).isHanging());
			int ticks = 0;
			while (hand(0).isHanging() && ticks < 200) {
				ctx.waitTick();
				ticks++;
				if (ticks == 20) ctx.takeScreenshot("wkclimb-05-grip-half");
				if (ticks == 45) ctx.takeScreenshot("wkclimb-06-grip-low");
			}
			LOG.info("[wkclimb] one-hand hang from 3 grip lasted {} more ticks, grip now {}, grabWait {}", ticks, hand(0).grip(), hand(0).grabWait());
			ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_LEFT);
			if (ticks < 45 || ticks > 65) throw new AssertionError("fatigue after " + ticks + " ticks");
			if (hand(0).grabWait() < 0.8) throw new AssertionError("no fatigue cooldown");

			// --- Icy ledge: CL_Handhold_Icy slips after 2 s ---
			server.runCommand("tp @a 0.5 -60 9.0 0 -55");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> ClimbController.INSTANCE.sim.resetHands(10.0));
			ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.waitTicks(2);
			check(ctx, "left hand grabs the ice ledge", mc -> hand(0).isHanging());
			int iceTicks = 0;
			while (hand(0).isHanging() && iceTicks < 200) {
				ctx.waitTick();
				iceTicks++;
			}
			LOG.info("[wkclimb] Chimney ice hold lasted {} ticks (maxHoldTime 2 s = 40 ticks)", iceTicks);
			ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_LEFT);
			if (iceTicks < 30 || iceTicks > 50) throw new AssertionError("ice hold lasted " + iceTicks + " ticks");

			// --- Non-full blocks: ledges sit on the visible shape ---
			server.runCommand("fill 0 -60 90 2 -60 90 minecraft:oak_fence");
			server.runCommand("fill 0 -60 95 2 -60 95 minecraft:cobblestone_wall");
			server.runCommand("setblock 1 -60 100 minecraft:stone_stairs[facing=north]");
			server.runCommand("setblock 1 -60 105 minecraft:stone");
			server.runCommand("setblock 1 -59 105 minecraft:lantern");
			ctx.waitTicks(5);
			// between two fence posts (posts at x 0.5 and 1.5): the rail's south edge, visible top y -59
			grabAt(ctx, server, "fence rail between posts", 1.0, -59.0, 90.5625, 1.5, 91.9, -59.0, -59.0);
			// wall between posts: side top at 14/16 (low) or 16/16
			grabAt(ctx, server, "wall middle", 1.0, -59.125, 95.6875, 1.5, 96.9, -59.13, -58.99);
			// stair facing north: the lower step's front edge at half height
			grabAt(ctx, server, "stair lower step", 1.5, -59.5, 101.0, 1.5, 102.2, -59.5, -59.5);
			// lantern on a block: its body top (7/16) or cap (9/16)
			grabAt(ctx, server, "lantern top", 1.5, -58.5625, 105.6875, 1.5, 106.7, -58.57, -58.43);

			// --- Swinging from a rod (Handhold-Bar: dragMult 4, climbMult 0.1) ---
			server.runCommand("fill -2 -58 60 2 -58 60 minecraft:iron_chain[axis=x]");
			server.runCommand("fill 0 -60 61 0 -59 61 minecraft:iron_chain[axis=y]");
			server.runCommand("tp @a 0.5 -60 60.5 0 -89");
			ctx.waitTicks(20);
			ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_RIGHT);
			ctx.waitTicks(3);
			LOG.info("[wkclimb] chain: hovered={} hanging={} {} handhold={}", ClimbController.INSTANCE.hovered(),
					hand(0).isHanging(), hand(1).isHanging(), hand(0).handhold());
			check(ctx, "both hands grab the chain as a bar", mc -> hand(0).isHanging() && hand(1).isHanging()
					&& hand(0).handhold() == dev.fizz.wkclimb.sim.Handhold.BAR);
			ctx.waitTicks(20);
			// look ahead and pump: W pushes along the view (moveAxis = camForward + climbDir)
			ctx.runOnClient(mc -> mc.player.setXRot(0));
			check(ctx, "rods let you through while swinging", mc -> dev.fizz.wkclimb.WKClimb.clientThroughBars);
			ctx.getInput().holdKey(o -> o.keyUp);
			ctx.waitTicks(20);
			ctx.getInput().releaseKey(o -> o.keyUp);
			double minZ = 1e9, maxZ = -1e9;
			for (int t = 0; t < 60; t++) {
				ctx.waitTick();
				double z = ctx.computeOnClient(mc -> mc.player.getZ());
				minZ = Math.min(minZ, z);
				maxZ = Math.max(maxZ, z);
				if (t == 10) ctx.takeScreenshot("wkclimb-08b-swinging");
			}
			LOG.info("[wkclimb] swing on the chain: z from {} to {} ({} blocks), still hanging {}", minZ, maxZ, maxZ - minZ, hand(0).isHanging());
			ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_RIGHT);
			if (maxZ - minZ < 0.5) throw new AssertionError("no swing: " + (maxZ - minZ));
			// a vertical chain (z 61.41..61.59) would stop the body (half-width 0.3) at z 61.11
			if (maxZ < 61.3) throw new AssertionError("swing stopped at the chain rod (z " + maxZ + ")");
			ctx.waitTicks(30);
			// on the ground again rods are solid
			server.runCommand("tp @a 0.5 -60 63.0 180 0");
			ctx.waitTicks(30);
			ctx.getInput().holdKey(o -> o.keyUp);
			ctx.waitTicks(20);
			ctx.getInput().releaseKey(o -> o.keyUp);
			double barZ = ctx.computeOnClient(mc -> mc.player.getZ());
			LOG.info("[wkclimb] walking into the chain on foot stops at z {} (chain z 61.41..61.59, so about 61.89)", barZ);
			if (barZ < 61.8) throw new AssertionError("walked through the chain on foot: z " + barZ);

			// --- A wall of iron bars is a solid, climbable grate, not a rod ---
			server.runCommand("fill -2 -60 70 2 -57 70 minecraft:iron_bars");
			server.runCommand("tp @a 0.5 -60 71.6 180 0");
			ctx.waitTicks(30);
			ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.waitTicks(3);
			check(ctx, "grabs the iron-bar wall as a plain handhold (no swinging, no pass-through)", mc -> hand(0).isHanging()
					&& hand(0).handhold() == dev.fizz.wkclimb.sim.Handhold.DEFAULT && !dev.fizz.wkclimb.WKClimb.clientThroughBars);
			ctx.getInput().holdKey(o -> o.keyUp);
			ctx.waitTicks(30);
			ctx.getInput().releaseKey(o -> o.keyUp);
			double wallZ = ctx.computeOnClient(mc -> mc.player.getZ());
			LOG.info("[wkclimb] pushing into the iron-bar wall while holding it: z {} (pane z 70.44..70.56, so 70.86 or more)", wallZ);
			ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_LEFT);
			if (wallZ < 70.8) throw new AssertionError("passed through the iron-bar wall: z " + wallZ);
			ctx.waitTicks(20);

			// --- Damage overlay: hit from the right ---
			server.runCommand("tp @a 0.5 -60 64.0 180 0");
			ctx.waitTicks(20);
			server.runCommand("damage @p 6 minecraft:generic at 6.5 -59 64.0");
			ctx.waitTicks(2);
			LOG.info("[wkclimb] after a hit from the right: hurt vignette {}, indicators L {} T {} R {} B {}, blood splats {}",
					dev.fizz.wkclimb.client.ClimbDamage.hurtAmount(), dev.fizz.wkclimb.client.ClimbDamage.directionAlpha(0),
					dev.fizz.wkclimb.client.ClimbDamage.directionAlpha(1), dev.fizz.wkclimb.client.ClimbDamage.directionAlpha(2),
					dev.fizz.wkclimb.client.ClimbDamage.directionAlpha(3), dev.fizz.wkclimb.client.ClimbDamage.splatCount());
			ctx.takeScreenshot("wkclimb-09-hurt");
			check(ctx, "hurt vignette, right-side indicator and blood after a hit from the right", mc ->
					dev.fizz.wkclimb.client.ClimbDamage.hurtAmount() > 0.4
					&& dev.fizz.wkclimb.client.ClimbDamage.directionAlpha(2) > 0.5
					&& dev.fizz.wkclimb.client.ClimbDamage.directionAlpha(0) < 0.01
					&& dev.fizz.wkclimb.client.ClimbDamage.splatCount() == 2);
			ctx.waitTicks(10);
			ctx.takeScreenshot("wkclimb-09b-hurt-later");
			// low health: the damageFX grade (towards greyscale), measured in the screenshots
			server.runCommand("effect give @p minecraft:instant_health 1 10");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> dev.fizz.wkclimb.client.ClimbDamage.resetForTest());
			ctx.waitTicks(2);
			double satFull = centerSaturation(ctx.takeScreenshot("wkclimb-09c-full-health"));
			server.runCommand("damage @p 15 minecraft:generic");
			ctx.waitTicks(10);
			double blendLow = ctx.computeOnClient(mc -> dev.fizz.wkclimb.client.ClimbDamage.colorGradeBlend());
			String effect = ctx.computeOnClient(mc -> dev.fizz.wkclimb.client.ClimbDamage.gradeRequested() ? dev.fizz.wkclimb.client.ClimbDamage.GRADE.toString() : "null");
			double satLow = centerSaturation(ctx.takeScreenshot("wkclimb-09d-low-health-grey"));
			LOG.info("[wkclimb] at 25% health: grade {} ({}), centre saturation {} vs {} at full health", blendLow, effect, satLow, satFull);
			if (blendLow < 0.25 || !effect.equals("wkclimb:damage")) throw new AssertionError("no damage grade at low health");
			if (!(satLow < satFull * 0.8)) throw new AssertionError("screen did not lose colour: " + satLow + " vs " + satFull);
			// regen: nothing for 5 s, then health climbs 1 HP/s and the grade fades smoothly
			StringBuilder fade = new StringBuilder();
			double prev = blendLow, heldAt4s = -1;
			int changes = 0;
			for (int t = 0; t < 160; t += 10) {
				ctx.waitTicks(10);
				double b = ctx.computeOnClient(mc -> dev.fizz.wkclimb.client.ClimbDamage.colorGradeBlend());
				if (t + 10 == 80) heldAt4s = b;
				if (b > prev + 1e-6) throw new AssertionError("grade went back up while healing: " + prev + " -> " + b);
				if (b < prev - 1e-6) changes++;
				prev = b;
				fade.append(String.format(" %.0fs:%.2f", (t + 10) / 20.0, b));
			}
			LOG.info("[wkclimb] grade while healing:{} ({} decreases)", fade, changes);
			if (changes < 5) throw new AssertionError("grade did not fade smoothly: " + changes + " changes");
			if (Math.abs(heldAt4s - blendLow) > 1e-6) throw new AssertionError("grade faded during the 5 s heal delay");
			server.runCommand("effect give @p minecraft:instant_health 1 10");
			// the grade goes at full health; the post pass stays until the hurt vignette has faded too
			ctx.waitTicks(10);
			check(ctx, "grade clears at full health", mc -> dev.fizz.wkclimb.client.ClimbDamage.colorGradeBlend() == 0);
			ctx.waitFor(mc -> !dev.fizz.wkclimb.client.ClimbDamage.gradeRequested(), 400);
			server.runCommand("effect give @p minecraft:instant_health 1 5");
			ctx.waitTicks(20);

			// --- Damage look vs the game: white, green and black walls at the health levels of the
			// reference screenshots (WK 5/4/3/2/1/0.1 = 20/16/12/8/4/0.4). Each damaged frame is checked
			// against the model (wk_damage.glsl, ported below) applied to the full-health frame.
			String[][] walls = { { "white", "white_concrete" }, { "green", "lime_concrete" }, { "black", "black_concrete" } };
			for (int wi = 0; wi < walls.length; wi++) {
				int x = 200 + wi * 60;
				server.runCommand("tp @a " + x + ".5 -60 216.5 0 0"); // first, so the wall's chunk is loaded
				ctx.waitTicks(10);
				server.runCommand("fill " + (x - 25) + " -61 220 " + (x + 25) + " -25 220 minecraft:" + walls[wi][1]);
				server.runCommand("effect give @p minecraft:instant_health 1 10");
				ctx.waitTicks(30);
				ctx.runOnClient(mc -> dev.fizz.wkclimb.client.ClimbDamage.resetForTest());
				ctx.waitTicks(3);
				java.nio.file.Path full = ctx.takeScreenshot("wkclimb-dmg-" + walls[wi][0] + "-20");
				float health = 20;
				for (float hit : new float[] { 4, 4, 4, 4, 3.6f }) {
					server.runCommand("damage @p " + hit + " minecraft:generic");
					health -= hit;
					ctx.waitTicks(50); // blood, indicators and camera shake gone; regen starts at 5 s
					ctx.runOnClient(mc -> dev.fizz.wkclimb.client.ClimbDamage.settleForTest());
					ctx.waitTicks(3);
					float[] look = ctx.computeOnClient(mc -> dev.fizz.wkclimb.client.ClimbDamage.lookForTest());
					float clientHealth = ctx.computeOnClient(mc -> mc.player.getHealth());
					java.nio.file.Path shot = ctx.takeScreenshot(String.format("wkclimb-dmg-%s-%.1f", walls[wi][0], health));
					double err = lookError(full, shot, look);
					LOG.info("[wkclimb] damage look {} at health {} (client {}): saturation {} brightness {} vignette {}; mean error vs model {}/255",
							walls[wi][0], health, clientHealth, look[0], look[1], look[2], String.format("%.2f", err));
					if (err > 3) throw new AssertionError("damage look differs from the model: " + err + "/255 on " + walls[wi][0] + " at " + health);
					if (withPack && wi == walls.length - 1 && health < 1) {
						// near-death loops: damageFXCurve(2%) x 0.8 = 0.423, dyingAudioCurve(2%) x 0.8 = 0.797 in
						// White Knuckle, times the low_health_volume setting
						float[] loops = ctx.computeOnClient(mc -> dev.fizz.wkclimb.client.ClimbSounds.loopVolumesForTest());
						float setting = (float) dev.fizz.wkclimb.ClimbConfig.lowHealthVolume;
						LOG.info("[wkclimb] near-death loops at 2% health: heartbeat {}, mortal wound {} (WK 0.423, 0.797 x setting {})",
								loops[1], loops[2], setting);
						if (Math.abs(loops[1] - 0.423f * setting) > 0.05f * setting || Math.abs(loops[2] - 0.797f * setting) > 0.05f * setting) {
							throw new AssertionError("near-death loop volumes off: " + loops[1] + ", " + loops[2] + " at setting " + setting);
						}
					}
				}
			}
			server.runCommand("effect give @p minecraft:instant_health 1 10");
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> dev.fizz.wkclimb.client.ClimbDamage.resetForTest());
			// --- Hand stamina red: only the left hand loses grip (each hand shows its own); screenshots on the
			// black wall at stamina used 0.05..1, chosen mid-step so the 3 ticks of regen can't cross a step
			server.runCommand("tp @a 320.5 -60 216.5 0 0");
			ctx.waitTicks(10);
			for (double used : new double[] { 0.05, 0.11, 0.3, 0.43, 0.55, 0.62, 0.8, 1.0 }) {
				ctx.runOnClient(mc -> {
					ClimbController.INSTANCE.sim.setGripForTest(0, 10 * (1 - used));
					ClimbController.INSTANCE.sim.setGripForTest(1, 10);
				});
				ctx.waitTicks(2);
				double gripNow = ctx.computeOnClient(mc -> hand(0).gripPercent());
				ctx.takeScreenshot(String.format("wkclimb-stamina-%.2f", used));
				LOG.info("[wkclimb] stamina screenshot: set used {}, left grip now {}", used, gripNow);
			}
			ctx.runOnClient(mc -> ClimbController.INSTANCE.sim.resetHands(10));

			// --- White Knuckle health rules (server side, x4 scale: WK 5 health = Minecraft 20) ---
			server.runCommand("tp @a 30.5 -60 30.5 0 0");
			server.runCommand("effect give @p minecraft:instant_health 1 10");
			server.runCommand("effect give @p minecraft:hunger 2 100");
			ctx.waitTicks(30);
			int food = server.computeOnServer(sv -> sv.getPlayerList().getPlayers().get(0).getFoodData().getFoodLevel());
			LOG.info("[wkclimb] food after a strong hunger effect: {} (White Knuckle has no hunger)", food);
			if (food != 20) throw new AssertionError("hunger still active: " + food);
			ctx.takeScreenshot("wkclimb-10-no-hotbar");

			server.runCommand("damage @p 8 minecraft:generic");
			ctx.waitTicks(2);
			float h0 = serverHealth(server);
			ctx.waitTicks(80);
			float h4 = serverHealth(server);
			ctx.waitTicks(60);
			float h7 = serverHealth(server);
			LOG.info("[wkclimb] regen: {} right after the hit, {} after 4 s, {} after 7 s (WK: nothing for 5 s, then 1 HP/s)", h0, h4, h7);
			if (Math.abs(h4 - h0) > 0.05) throw new AssertionError("healed during the 5 s delay");
			if (h7 - h4 < 1.5 || h7 - h4 > 2.5) throw new AssertionError("regen rate off: " + (h7 - h4));

			server.runCommand("effect give @p minecraft:instant_health 1 10");
			ctx.waitTicks(20);
			server.runCommand("tp @a 30.5 -30 30.5 0 0");
			ctx.waitFor(mc -> mc.player.onGround() && mc.player.getY() < -59.5, 400);
			ctx.waitTicks(5);
			float afterShort = serverHealth(server);
			LOG.info("[wkclimb] 30-block fall: health {} (WK safe distance 50 units = 40 blocks; vanilla would deal 27)", afterShort);
			if (afterShort < 19.9f) throw new AssertionError("30-block fall hurt: " + afterShort);

			server.runCommand("tp @a 30.5 0 30.5 0 0");
			// WindSound(): the falling wind loop follows fall speed; past 50 units the camera shakes
			float maxWind = -1;
			double maxShake = 0;
			for (int t = 0; t < 600; t++) {
				ctx.waitTick();
				maxWind = Math.max(maxWind, ctx.computeOnClient(mc -> dev.fizz.wkclimb.client.ClimbSounds.loopVolumesForTest()[0]));
				maxShake = Math.max(maxShake, ctx.computeOnClient(mc -> dev.fizz.wkclimb.client.ClimbCamera.shakeForTest()));
				if (ctx.computeOnClient(mc -> mc.player.onGround() && mc.player.getY() < -59.5)) break;
			}
			LOG.info("[wkclimb] 60-block fall: loudest wind {} (WK: (fall speed - 2) x 0.03), most camera shake {}", maxWind, maxShake);
			if (withPack && maxWind < 0.2f) throw new AssertionError("no falling wind: " + maxWind);
			if (maxShake < 0.005) throw new AssertionError("no camera shake past the safe fall distance: " + maxShake);
			ctx.waitTicks(5);
			float afterLong = serverHealth(server);
			LOG.info("[wkclimb] 60-block fall: health {} (WK: ceil((75 - 60) / 15) + 1 = 2 -> 8, plus 0.4 per second past 40 blocks)", afterLong);
			if (afterLong > 12.0f || afterLong < 10.0f) throw new AssertionError("60-block fall damage off: " + (20 - afterLong));

			server.runCommand("effect give @p minecraft:instant_health 1 10");
			server.runCommand("fill 34 -60 34 36 -57 36 minecraft:water");
			server.runCommand("tp @a 35.5 -60 35.5 0 0");
			ctx.waitTicks(5);
			// (breath runs down at 1/s from 10; start from 2 s left to keep the test short)
			server.runOnServer(sv -> dev.fizz.wkclimb.WKHealth.setBreath(sv.getPlayerList().getPlayers().get(0).getUUID(), 2.0));
			float wet0 = serverHealth(server);
			ctx.waitTicks(30);
			float wet15 = serverHealth(server);
			ctx.waitTicks(70);
			float wet5 = serverHealth(server);
			LOG.info("[wkclimb] underwater with 2 s of breath: {} at 0 s, {} at 1.5 s, {} at 5 s (WK: no damage until breath is gone, then 1 HP/s)", wet0, wet15, wet5);
			server.runCommand("fill 34 -60 34 36 -57 36 minecraft:air");
			if (wet15 < wet0 - 0.05) throw new AssertionError("drowned with breath left");
			if (wet0 - wet5 < 3 || wet0 - wet5 > 4.5) throw new AssertionError("drowning rate off: " + (wet0 - wet5));
			server.runCommand("effect give @p minecraft:instant_health 1 10");
			ctx.waitTicks(20);

			// --- White Knuckle ground movement ---
			// expected speeds from the WK constants: v = sqrt(a/k) - a per step, a = speed*0.02*0.15, k = 2.4,
			// then * 175 units/s * 0.8 blocks/unit
			double walkExpected = (Math.sqrt(0.85 * 0.003 / 2.4) - 0.85 * 0.003) * 175 * 0.8;
			double sprintExpected = (Math.sqrt(2.5 * 0.003 / 2.4) - 2.5 * 0.003) * 175 * 0.8;
			server.runCommand("tp @a 0.5 -60 20.5 0 0");
			ctx.waitTicks(20);
			ctx.getInput().holdKey(o -> o.keyUp);
			ctx.waitTicks(30);
			double walk = horizontalSpeed(ctx);
			ctx.getInput().holdKey(o -> o.keySprint);
			ctx.waitTicks(30);
			double sprint = horizontalSpeed(ctx);
			float fov = ctx.computeOnClient(mc -> dev.fizz.wkclimb.client.ClimbCamera.fov(0f));
			int baseFov = 90; // White Knuckle's default playerFOV
			ctx.getInput().releaseKey(o -> o.keySprint);
			ctx.getInput().releaseKey(o -> o.keyUp);
			LOG.info("[wkclimb] walk {} b/s (WK {}), sprint {} b/s (WK {}), sprint FOV {} (setting {} + 15)",
					walk, walkExpected, sprint, sprintExpected, fov, baseFov);
			if (Math.abs(walk - walkExpected) > 0.4) throw new AssertionError("walk speed " + walk);
			if (Math.abs(sprint - sprintExpected) > 0.6) throw new AssertionError("sprint speed " + sprint);
			if (Math.abs(fov - (baseFov + 15)) > 1.5) throw new AssertionError("sprint FOV " + fov);
			ctx.waitTicks(30);
			double groundY = playerY(ctx);
			ctx.getInput().holdKey(o -> o.keyJump);
			double apex = groundY;
			for (int t = 0; t < 30; t++) { ctx.waitTick(); apex = Math.max(apex, playerY(ctx)); }
			ctx.getInput().releaseKey(o -> o.keyJump);
			LOG.info("[wkclimb] ground jump apex {} blocks (WK: jumpHeight 0.4 x gravity 0.2 -> about 2)", apex - groundY);
			if (apex - groundY < 1.5 || apex - groundY > 2.3) throw new AssertionError("jump apex " + (apex - groundY));
			ctx.waitTicks(30);

			// --- Ice floor (Ice Footsteps: friction 0.05, moveLerp 0.2, moveSpeed 0.6): you slide ---
			server.runCommand("tp @a 0.5 -60 45.5 0 0");
			ctx.waitTicks(20);
			ctx.getInput().holdKey(o -> o.keyUp);
			ctx.waitTicks(40);
			ctx.getInput().releaseKey(o -> o.keyUp);
			double stopZ = ctx.computeOnClient(mc -> mc.player.getZ());
			ctx.waitTicks(40);
			double iceSlide = ctx.computeOnClient(mc -> mc.player.getZ()) - stopZ;
			server.runCommand("tp @a 0.5 -60 20.5 0 0");
			ctx.waitTicks(20);
			ctx.getInput().holdKey(o -> o.keyUp);
			ctx.waitTicks(40);
			ctx.getInput().releaseKey(o -> o.keyUp);
			double stopZ2 = ctx.computeOnClient(mc -> mc.player.getZ());
			ctx.waitTicks(40);
			double grassSlide = ctx.computeOnClient(mc -> mc.player.getZ()) - stopZ2;
			LOG.info("[wkclimb] slide after letting go of W: ice {} blocks, grass {} blocks", iceSlide, grassSlide);
			if (!(iceSlide > 1.0 && iceSlide > 5 * grassSlide)) throw new AssertionError("ice did not slide");

			// --- Snow on ledges, and inner corners (both reported by a player) ---
			// one snow layer has no collision box: the block edge under it must still be a ledge
			server.runCommand("fill 116 -60 -3 124 -59 -2 minecraft:stone");
			server.runCommand("fill 116 -58 -3 124 -58 -2 minecraft:snow[layers=1]");
			Vec3 snow1 = tryGrab(ctx, server, 120.5, 0.0, 120.5, -58.0, -1.0);
			// three layers have a collision box: the snow's own edge (6/16 up) is the ledge
			server.runCommand("fill 126 -60 -3 134 -59 -2 minecraft:stone");
			server.runCommand("fill 126 -58 -3 134 -58 -2 minecraft:snow[layers=3]");
			Vec3 snow3 = tryGrab(ctx, server, 130.5, 0.0, 130.5, -57.625, -1.0);
			// inner corner under an overhang: block A, and block B one up and one back with air under it.
			// A's back edge meets B's front face; from the front there's nothing to hold there.
			server.runCommand("fill 136 -60 -2 144 -60 -2 minecraft:stone");
			server.runCommand("fill 136 -59 -3 144 -59 -3 minecraft:stone");
			Vec3 overhang = tryGrab(ctx, server, 140.5, -0.65, 140.5, -59.0, -2.0);
			// inner corner against a taller block: the end of a ledge pressed into the neighbour's side
			server.runCommand("setblock 150 -60 -2 minecraft:stone");
			server.runCommand("fill 151 -60 -2 151 -59 -2 minecraft:stone");
			Vec3 sideCorner = tryGrab(ctx, server, 150.8, 0.2, 150.97, -59.0, -1.0);
			Vec3 sideMiddle = tryGrab(ctx, server, 150.5, 0.2, 150.5, -59.0, -1.0);
			// a flat floor has no ledges: not where four blocks meet, nor along a seam between two
			Vec3 floorCorner = tryGrab(ctx, server, 170.5, 0.5, 171.0, -60.0, 0.0);
			Vec3 floorSeam = tryGrab(ctx, server, 170.5, 0.5, 170.5, -60.0, -0.0);
			LOG.info("[wkclimb] snow 1 layer: {}; snow 3 layers: {}; overhang inner corner: {}; side inner corner: {}; middle of that ledge: {}; floor corner: {}; floor seam: {}",
					snow1, snow3, overhang, sideCorner, sideMiddle, floorCorner, floorSeam);
			if (floorCorner != null || floorSeam != null) throw new AssertionError("grabbed a flat floor: corner " + floorCorner + ", seam " + floorSeam);
			if (snow1 == null || Math.abs(snow1.y - -58.0) > 1e-3) throw new AssertionError("ledge under one snow layer: " + snow1);
			if (snow3 == null || Math.abs(snow3.y - -57.625) > 1e-3) throw new AssertionError("edge of three snow layers: " + snow3);
			if (overhang != null && Math.abs(overhang.z - -2.0) < 1e-3) throw new AssertionError("grabbed the inner corner under the overhang: " + overhang);
			if (sideCorner != null) throw new AssertionError("grabbed the inner corner against the taller block: " + sideCorner);
			if (sideMiddle == null || Math.abs(sideMiddle.y - -59.0) > 1e-3) throw new AssertionError("middle of the ledge beside the taller block: " + sideMiddle);

			// --- Body: White Knuckle's eye height, crouching off a ledge, the step-height option ---
			server.runCommand("tp @a 60.5 -60 50.5 0 0");
			ctx.waitTicks(10);
			float eyeStand = ctx.computeOnClient(mc -> mc.player.getEyeHeight());
			ctx.getInput().holdKey(SDLScancode.SDL_SCANCODE_LCTRL);
			ctx.waitTicks(10);
			float eyeCrouch = ctx.computeOnClient(mc -> mc.player.getEyeHeight());
			ctx.getInput().releaseKey(SDLScancode.SDL_SCANCODE_LCTRL);
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> ClimbController.INSTANCE.setEnabled(false));
			ctx.waitTicks(2);
			float eyeVanilla = ctx.computeOnClient(mc -> mc.player.getEyeHeight());
			ctx.runOnClient(mc -> ClimbController.INSTANCE.setEnabled(true));
			ctx.waitTicks(2);
			LOG.info("[wkclimb] eye height {} standing, {} crouched (WK 1.44 / 0.84), {} with climbing off (Minecraft 1.62)",
					eyeStand, eyeCrouch, eyeVanilla);
			if (Math.abs(eyeStand - 1.44f) > 0.01f || Math.abs(eyeCrouch - 0.84f) > 0.01f || Math.abs(eyeVanilla - 1.62f) > 0.01f) {
				throw new AssertionError("eye heights off: " + eyeStand + " / " + eyeCrouch + " / " + eyeVanilla);
			}
			// crouching doesn't hold you at a ledge (that's Minecraft's sneak guard; White Knuckle has none)
			server.runCommand("fill 59 -60 59 61 -60 61 minecraft:stone");
			server.runCommand("tp @a 60.5 -59 61.4 0 0");
			ctx.waitTicks(10);
			ctx.getInput().holdKey(SDLScancode.SDL_SCANCODE_LCTRL);
			ctx.getInput().holdKey(SDLScancode.SDL_SCANCODE_W);
			ctx.waitTicks(60);
			ctx.getInput().releaseKey(SDLScancode.SDL_SCANCODE_W);
			ctx.getInput().releaseKey(SDLScancode.SDL_SCANCODE_LCTRL);
			double edgeY = ctx.computeOnClient(mc -> mc.player.getY());
			LOG.info("[wkclimb] crouch-walking off a 1-block ledge: y {} (started at -59)", edgeY);
			if (edgeY > -59.5) throw new AssertionError("crouching held the player at the ledge: y " + edgeY);
			// step height: Minecraft's 0.6 unless exact_step_height (White Knuckle's 0.15 x 1.5 x 0.8 = 0.18)
			float stepDefault = ctx.computeOnClient(mc -> mc.player.maxUpStep());
			dev.fizz.wkclimb.ClimbConfig.exactStepHeight = true;
			float stepExact = ctx.computeOnClient(mc -> mc.player.maxUpStep());
			dev.fizz.wkclimb.ClimbConfig.exactStepHeight = false;
			LOG.info("[wkclimb] step height {} by default, {} with exact_step_height", stepDefault, stepExact);
			if (Math.abs(stepDefault - 0.6f) > 1e-4f || Math.abs(stepExact - 0.18f) > 1e-4f) {
				throw new AssertionError("step heights off: " + stepDefault + " / " + stepExact);
			}
			// grabbing a fungus-like block (moss) plays White Knuckle's metafungus grab
			server.runCommand("fill 76 -60 -3 84 -58 -2 minecraft:moss_block");
			server.runCommand("tp @a 80.5 -60 0.0 180 -55");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> ClimbController.INSTANCE.sim.resetHands(10.0));
			ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.waitTicks(5);
			boolean mossHeld = ctx.computeOnClient(mc -> hand(0).isHanging());
			String grabSound = ctx.computeOnClient(mc -> dev.fizz.wkclimb.client.ClimbSounds.lastPlayedForTest());
			ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_LEFT);
			ctx.waitTicks(10);
			LOG.info("[wkclimb] grabbed a moss ledge: {}, sound {}", mossHeld, grabSound);
			if (!mossHeld) throw new AssertionError("couldn't grab the moss ledge");
			if (withPack && !grabSound.equals("wkclimb:climbing.handhold_metafungus")) throw new AssertionError("moss grab sound: " + grabSound);

			// --- Sprinting: the sprint pose and ViewSway bob/tilt (screenshots a few frames apart) ---
			server.runCommand("tp @a 0.5 -60 30.5 0 0");
			ctx.waitTicks(20);
			ctx.takeScreenshot("wkclimb-07-standing");
			ctx.getInput().holdKey(o -> o.keyUp);
			ctx.getInput().holdKey(o -> o.keySprint);
			ctx.waitTicks(20);
			for (int f = 0; f < 4; f++) {
				ctx.waitTicks(2);
				ctx.takeScreenshot("wkclimb-08-sprint-" + f);
			}
			ctx.getInput().releaseKey(o -> o.keySprint);
			ctx.getInput().releaseKey(o -> o.keyUp);
			ctx.waitTicks(10);

			// --- Slime attack trace: health, grade and post effect every tick ---
			server.runCommand("effect give @p minecraft:instant_health 1 10");
			server.runCommand("difficulty normal");
			server.runCommand("tp @a 50.5 -60 50.5 0 0");
			ctx.waitTicks(20);
			server.runCommand("summon minecraft:slime 50.5 -60 52.5 {Size:2}");
			StringBuilder trace = new StringBuilder();
			String last = "";
			for (int t = 0; t < 300; t++) {
				ctx.waitTick();
				float sh = serverHealth(server);
				float ch = ctx.computeOnClient(mc -> mc.player.getHealth());
				int st = ctx.computeOnClient(mc -> dev.fizz.wkclimb.client.ClimbDamage.colorGradeStep());
				String fx = ctx.computeOnClient(mc -> dev.fizz.wkclimb.client.ClimbDamage.gradeRequested() ? dev.fizz.wkclimb.client.ClimbDamage.GRADE.toString() : "null");
				boolean dead = ctx.computeOnClient(mc -> mc.player.isDeadOrDying());
				String row = String.format("server %.2f client %.2f step %d fx %s dead %s", sh, ch, st, fx, dead);
				if (!row.equals(last)) trace.append("\n  t=").append(t).append(' ').append(row);
				last = row;
				if (dead) break;
			}
			LOG.info("[wkclimb] slime trace (changes only):{}", trace);
			server.runCommand("kill @e[type=minecraft:slime]");
			server.runCommand("difficulty peaceful");
			if (ctx.computeOnClient(mc -> mc.player.isDeadOrDying())) {
				ctx.runOnClient(mc -> mc.player.respawn());
				ctx.waitFor(mc -> mc.player != null && !mc.player.isDeadOrDying() && mc.gui.screen() == null, 200);
				ctx.waitTicks(20);
				float respawned = serverHealth(server);
				int stepAfter = ctx.computeOnClient(mc -> dev.fizz.wkclimb.client.ClimbDamage.colorGradeStep());
				LOG.info("[wkclimb] after respawn: health {}, grade step {}", respawned, stepAfter);
				if (respawned < 19.9f) throw new AssertionError("respawn took damage: " + respawned);
			}
			server.runCommand("effect give @p minecraft:instant_health 1 10");
			ctx.waitTicks(20);
			// a teleport down is not a fall
			server.runCommand("tp @a 50.5 20 50.5");
			ctx.waitTicks(5);
			server.runCommand("tp @a 50.5 -60 50.5");
			ctx.waitTicks(20);
			float afterTp = serverHealth(server);
			LOG.info("[wkclimb] teleport 80 blocks down: health {} (not a fall)", afterTp);
			if (afterTp < 19.9f) throw new AssertionError("teleport counted as a fall: " + afterTp);

			// --- Toggling off gives the mouse back to Minecraft ---
			ctx.getInput().pressKey(SDLScancode.SDL_SCANCODE_R);
			ctx.waitTick();
			check(ctx, "toggle key turns the hands off", mc -> !ClimbController.INSTANCE.isEnabled());
		}

		// --- Dedicated server: the mod runs there, the client sees it has the mod (climbing stays on),
		// and hanging for 5 s doesn't get the player kicked for flying ---
		ctx.runOnClient(mc -> ClimbController.INSTANCE.setEnabled(true)); // keys don't work on the title screen
		try (TestDedicatedServerContext ds = ctx.worldBuilder().createServer()) {
			TestDedicatedServerConnection conn = ds.connect(); // 26.3: not AutoCloseable, closed below
			try {
				ctx.waitTicks(60); // 26.3: no waitForChunksRender
				ds.runCommand("gamemode survival @a");
				ds.runCommand("difficulty peaceful");
				ds.runCommand("fill -4 -60 -3 4 -58 -2 minecraft:stone");
				ds.runCommand("tp @a 0.5 -60 0.0 180 -55");
				ctx.waitTicks(20);
				check(ctx, "climbing stays on with a modded dedicated server", mc -> ClimbController.INSTANCE.isEnabled());
				ctx.runOnClient(mc -> ClimbController.INSTANCE.sim.resetHands(10.0));
				ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_LEFT);
				ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_RIGHT);
				ctx.waitTicks(5);
				check(ctx, "both hands hang on a ledge on the dedicated server", mc -> hand(0).isHanging() && hand(1).isHanging());
				boolean wkMode = ds.computeOnServer(server -> dev.fizz.wkclimb.WKClimb.isWkMode(server.getPlayerList().getPlayers().get(0)));
				if (!wkMode) throw new AssertionError("the dedicated server doesn't know the player is in White Knuckle mode");
				for (int t = 0; t < 100; t++) {
					ctx.waitTick();
					if (ctx.computeOnClient(mc -> mc.getConnection() == null || mc.level == null)) {
						throw new AssertionError("disconnected while hanging on the dedicated server");
					}
				}
				check(ctx, "still hanging and connected after 5 s on the dedicated server", mc -> hand(0).isHanging() && mc.getConnection() != null);
				ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_LEFT);
				ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_RIGHT);
				ctx.waitTicks(10);
				LOG.info("[wkclimb] dedicated server: climbing on, hung 5 s without a kick, server WK mode {}", wkMode);
			} finally {
				conn.close();
			}
		}
		LOG.info("[wkclimb] ALL CHECKS PASSED (with WK asset pack: {})", withPack);
	}

	/** The settings screen (Mod Menu's Config button): a toggle changes the setting and is saved on close. */
	private static void settingsScreen(ClientGameTestContext ctx) {
		ctx.waitFor(mc -> mc.gui.overlay() == null); // the pack's reload screen takes the clicks
		ctx.setScreen(() -> new dev.fizz.wkclimb.client.ClimbSettingsScreen(null));
		ctx.waitTicks(2);
		ctx.takeScreenshot("wkclimb-settings");
		clickInList(ctx, "Ledges only: ON");
		check(ctx, "settings: Ledges only toggled off", mc -> !dev.fizz.wkclimb.ClimbConfig.ledgesOnly);
		clickInList(ctx, "Build...");
		check(ctx, "settings: Build... opens the setup screen",
				mc -> mc.gui.screen() instanceof dev.fizz.wkclimb.client.assets.AssetSetupScreen);
		ctx.setScreen(() -> null);
		Path file = ctx.computeOnClient(mc -> net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("wkclimb.properties"));
		check(ctx, "settings: saved to wkclimb.properties", mc -> {
			try {
				return Files.readAllLines(file).contains("ledges_only=false");
			} catch (IOException e) {
				return false;
			}
		});
		ctx.runOnClient(mc -> {
			dev.fizz.wkclimb.ClimbConfig.ledgesOnly = true;
			dev.fizz.wkclimb.ClimbConfig.save();
		});
	}

	/**
	 * Clicks the widget showing this text with the mouse, like a player, scrolling its list to it first.
	 * clickScreenButton only finds the screen's top-level buttons, not the ones inside an options list.
	 */
	private static void clickInList(ClientGameTestContext ctx, String text) {
		// Rows that haven't been drawn keep an old position, so scroll a step at a time until it's in view.
		for (int step = 0; ; step++) {
			boolean inView = ctx.computeOnClient(mc -> {
				var area = new net.minecraft.client.gui.components.AbstractScrollArea[1];
				var w = findWidget(mc.gui.screen(), text, null, area);
				if (w == null) throw new AssertionError("no widget '" + text + "' on " + mc.gui.screen());
				var a = area[0];
				if (a == null || (w.getY() >= a.getY() && w.getBottom() <= a.getBottom())) return true;
				if (a.scrollAmount() >= a.maxScrollAmount()) throw new AssertionError("can't scroll to '" + text + "'");
				a.setScrollAmount(a.scrollAmount() + a.getHeight() / 2.0);
				return false;
			});
			if (inView) break;
			if (step > 20) throw new AssertionError("can't scroll to '" + text + "'");
			ctx.waitTick();
		}
		double[] at = ctx.computeOnClient(mc -> {
			var w = findWidget(mc.gui.screen(), text, null, new net.minecraft.client.gui.components.AbstractScrollArea[1]);
			int scale = mc.getWindow().getGuiScale();
			return new double[] {(w.getX() + w.getWidth() / 2.0) * scale, (w.getY() + w.getHeight() / 2.0) * scale};
		});
		ctx.getInput().setCursorPos(at[0], at[1]);
		ctx.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
		ctx.waitTick();
	}

	/** The widget showing text under parent; area[0] gets the scrolling list it's in, if any. */
	private static net.minecraft.client.gui.components.AbstractWidget findWidget(Object parent, String text,
			net.minecraft.client.gui.components.AbstractScrollArea inArea, net.minecraft.client.gui.components.AbstractScrollArea[] area) {
		if (!(parent instanceof net.minecraft.client.gui.components.events.ContainerEventHandler c)) return null;
		if (parent instanceof net.minecraft.client.gui.components.AbstractScrollArea a) inArea = a;
		for (var child : c.children()) {
			if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getMessage().getString().equals(text)) {
				area[0] = inArea;
				return w;
			}
			var found = findWidget(child, text, inArea, area);
			if (found != null) return found;
		}
		return null;
	}

	/** Copies the extracted pack into this client's resourcepacks folder and turns it on. */
	private static boolean installPack(ClientGameTestContext ctx) {
		String src = System.getenv("WKCLIMB_TEST_PACK");
		if (src == null || !Files.isDirectory(Path.of(src))) {
			LOG.info("[wkclimb] no WKCLIMB_TEST_PACK, testing with fallback visuals and sounds");
			return false;
		}
		CompletableFuture<Void> reload = ctx.computeOnClient(mc -> {
			Path dest = mc.getResourcePackDirectory().resolve(PACK);
			copyTree(Path.of(src), dest);
			var repo = mc.getResourcePackRepository();
			repo.reload();
			List<String> selected = new ArrayList<>(repo.getSelectedIds());
			if (!selected.contains("file/" + PACK)) selected.add("file/" + PACK);
			repo.setSelected(selected);
			return mc.reloadResourcePacks();
		});
		ctx.waitFor(mc -> reload.isDone(), 2400);
		LOG.info("[wkclimb] installed and enabled the WK asset pack from {}", src);
		return true;
	}

	private static void copyTree(Path from, Path to) {
		try (Stream<Path> files = Files.walk(from)) {
			for (Path p : (Iterable<Path>) files::iterator) {
				Path target = to.resolve(from.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(target);
				else Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/** Stand at (sx, -60, sz), look at the target point, grab, and check the grip height range. */
	/** Stand at (sx, -60, sz), look at the target, hold the left button: the grip point, or null if nothing was caught. */
	private static Vec3 tryGrab(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
			double sx, double sz, double tx, double ty, double tz) {
		server.runCommand("tp @a " + sx + " -60 " + sz);
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> ClimbController.INSTANCE.sim.resetHands(10.0));
		ctx.runOnClient(mc -> mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, new Vec3(tx, ty, tz)));
		// wait out the 0.2 s grab memory of wherever the view pointed before, so only this aim counts
		ctx.waitTicks(6);
		ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_LEFT);
		ctx.waitTicks(3);
		Vec3 grip = ctx.computeOnClient(mc -> hand(0).isHanging() ? ClimbController.toMcPublic(hand(0).holdPoint()) : null);
		ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_LEFT);
		ctx.waitTicks(30);
		return grip;
	}

	private static void grabAt(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
			String what, double tx, double ty, double tz, double sx, double sz, double minY, double maxY) {
		server.runCommand("tp @a " + sx + " -60 " + sz);
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
				new net.minecraft.world.phys.Vec3(tx, ty, tz)));
		ctx.waitTick();
		ctx.getInput().holdMouse(SDLMouse.SDL_BUTTON_LEFT);
		ctx.waitTicks(3);
		boolean hanging = hand(0).isHanging();
		var grip = ctx.computeOnClient(mc -> ClimbController.toMcPublic(hand(0).holdPoint()));
		LOG.info("[wkclimb] {}: hanging={} grip={} (expected y {}..{})", what, hanging, grip, minY, maxY);
		ctx.takeScreenshot("wkclimb-nf-" + what.replace(' ', '-'));
		ctx.getInput().releaseMouse(SDLMouse.SDL_BUTTON_LEFT);
		ctx.waitTicks(30);
		if (!hanging || grip.y < minY - 1e-3 || grip.y > maxY + 1e-3) throw new AssertionError(what + ": hanging=" + hanging + " grip=" + grip);
	}

	/** Mean HSV saturation of the centre quarter of a screenshot. */
	/**
	 * Mean per-channel difference (0-255) between a damaged frame and wk_damage.glsl applied to the
	 * full-health frame, over the upper 60% of the screen (the hands sway between frames), away
	 * from the crosshair.
	 */
	private static double lookError(java.nio.file.Path fullPng, java.nio.file.Path hurtPng, float[] look) {
		try {
			java.awt.image.BufferedImage full = javax.imageio.ImageIO.read(fullPng.toFile()), hurt = javax.imageio.ImageIO.read(hurtPng.toFile());
			int w = full.getWidth(), h = full.getHeight();
			double unit = Math.min(1, w / 6000.0) * 3.375 * (w / h > 2.5 ? 0.6 : 1);
			double sum = 0;
			int n = 0;
			for (int y = 0; y < h * 6 / 10; y += 3) {
				for (int x = 0; x < w; x += 3) {
					if (Math.abs(x - w / 2) < w / 20 && Math.abs(y - h / 2) < h / 20) continue;
					int a = full.getRGB(x, y), b = hurt.getRGB(x, y);
					double[] c = new double[3];
					for (int k = 0; k < 3; k++) c[k] = toLinear(((a >> (16 - 8 * k)) & 255) / 255.0);
					double value = Math.max(c[0], Math.max(c[1], c[2]));
					double tx = ((x + 0.5) / w - 0.5) / (1 + 400 * unit / w), ty = ((y + 0.5) / h - 0.5) / (1 + 200 * unit / h);
					double r = Math.hypot(tx, ty);
					double alpha = 0.81569 * look[2] * (0.0864 + 0.9136 * (1 - Math.exp(-Math.pow(r / 0.2872, 2.751))));
					double[] tint = { 154 / 255.0, 7 / 255.0, 7 / 255.0 };
					for (int k = 0; k < 3; k++) {
						double g = Math.min(1, Math.max(0, (value + look[0] * (c[k] - value)) * look[1]));
						double out = g * (1 - alpha) + toLinear(tint[k] * look[2]) * alpha;
						sum += Math.abs(toSrgb(out) * 255 - ((b >> (16 - 8 * k)) & 255));
						n++;
					}
				}
			}
			return sum / n;
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static double toLinear(double c) { return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }

	private static double toSrgb(double c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055; }

	private static double centerSaturation(java.nio.file.Path png) {
		try {
			java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(png.toFile());
			int w = img.getWidth(), h = img.getHeight();
			double sum = 0;
			int n = 0;
			for (int y = h * 3 / 8; y < h * 5 / 8; y += 2) {
				for (int x = w * 3 / 8; x < w * 5 / 8; x += 2) {
					int rgb = img.getRGB(x, y);
					int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
					int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
					sum += max == 0 ? 0 : (max - min) / (double) max;
					n++;
				}
			}
			return sum / n;
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static float serverHealth(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		return server.computeOnServer(sv -> sv.getPlayerList().getPlayers().get(0).getHealth());
	}

	private static double horizontalSpeed(ClientGameTestContext ctx) {
		double x0 = ctx.computeOnClient(mc -> mc.player.getX()), z0 = ctx.computeOnClient(mc -> mc.player.getZ());
		ctx.waitTicks(10);
		double x1 = ctx.computeOnClient(mc -> mc.player.getX()), z1 = ctx.computeOnClient(mc -> mc.player.getZ());
		return Math.hypot(x1 - x0, z1 - z0) * 2; // blocks per second over 10 ticks
	}

	private static ClimbSim.Hand hand(int i) {
		return ClimbController.INSTANCE.sim.hands[i];
	}

	private static double playerY(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.getY());
	}

	private interface Check { boolean test(Minecraft mc); }

	private static void check(ClientGameTestContext ctx, String what, Check c) {
		boolean ok = ctx.computeOnClient(c::test);
		LOG.info("[wkclimb] {}: {}", what, ok ? "ok" : "FAILED");
		if (!ok) throw new AssertionError(what);
	}
}
