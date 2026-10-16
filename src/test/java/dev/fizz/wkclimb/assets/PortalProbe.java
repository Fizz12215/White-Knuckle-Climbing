package dev.fizz.wkclimb.assets;

import java.nio.file.Path;

/**
 * Manual check of the portal client, outside Minecraft (also from inside a Flatpak sandbox):
 *   java -cp build/classes/java/main:build/classes/java/test dev.fizz.wkclimb.assets.PortalProbe read
 *   ... PortalProbe pick [start folder]     (opens the real folder picker)
 */
public final class PortalProbe {
	public static void main(String[] args) throws Exception {
		try (PortalFolderPicker portal = PortalFolderPicker.connect()) {
			if (args.length > 0 && args[0].equals("pick")) {
				System.out.println(portal.pickFolder("Choose a folder (test)", args.length > 1 ? Path.of(args[1]) : null));
			} else {
				System.out.println("color-scheme = " + portal.readSetting("org.freedesktop.appearance", "color-scheme"));
			}
		}
	}
}
