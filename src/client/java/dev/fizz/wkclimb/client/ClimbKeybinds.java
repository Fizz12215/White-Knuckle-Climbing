package dev.fizz.wkclimb.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fizz.wkclimb.ClimbConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.lwjgl.glfw.GLFW;

/**
 * White Knuckle's default bindings (Rewired "Default" keyboard and mouse maps in the level1 scene),
 * applied once to the matching Minecraft controls. You can still rebind them afterwards.
 *
 *   WK action              WK default        Minecraft control
 *   Move                   W A S D           movement (already the same)
 *   Jump                   Space             jump (already the same)
 *   Hand-Left / Right      LMB / RMB         attack / use (the hands)
 *   Sprint                 Left Shift        sprint (was Left Ctrl)
 *   Crouch                 Left Ctrl         sneak (was Left Shift)
 *   Inventory              Tab               inventory (was E); player list moves off Tab
 * Not mapped (no Minecraft equivalent, or they would clash with the hotbar): Drop-Left 1, Hand-Swap 2,
 * Drop-Right 3, Pocket-Left Q, Pocket-Right E, Focus F, Rotate R.
 */
public final class ClimbKeybinds {
	private ClimbKeybinds() {}

	public static void applyOnce(Minecraft mc) {
		if (ClimbConfig.wkKeybindsApplied || mc.options == null) return;
		apply(mc.options);
		ClimbConfig.wkKeybindsApplied = true;
		ClimbConfig.save();
	}

	public static void apply(Options o) {
		set(o.keySprint, GLFW.GLFW_KEY_LEFT_SHIFT);
		set(o.keyShift, GLFW.GLFW_KEY_LEFT_CONTROL);
		set(o.keyInventory, GLFW.GLFW_KEY_TAB);
		o.keyPlayerList.setKey(InputConstants.UNKNOWN);
		set(o.keyJump, GLFW.GLFW_KEY_SPACE);
		o.keyAttack.setKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_LEFT));
		o.keyUse.setKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
		KeyMapping.resetMapping();
		o.save();
	}

	private static void set(KeyMapping key, int glfwKey) {
		key.setKey(InputConstants.Type.KEYSYM.getOrCreate(glfwKey));
	}
}
