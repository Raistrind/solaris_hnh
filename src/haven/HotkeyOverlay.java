package haven;

import java.awt.Color;

/** Toggleable, screen-level reference for client and onboarding controls. */
public final class HotkeyOverlay {
	private static boolean visible = false;
	private static final String[][] HOTKEYS = {
			{ "Help", "Ctrl+F1", "Open handbook or current context" },
			{ "Help", "Ctrl+Shift+F1", "Toggle this overlay" },
			{ "Planning", "Ctrl+Shift+P", "Open specialization planner" },
			{ "Context", "Shift-hover", "Show advanced item/object details" },
			{ "Context", "Right-click item", "Choose interaction or recipes" },
			{ "Context", "Middle-click item", "Open recipes using it directly" },
			{ "Maps", "Ctrl+M", "Toggle minimap" },
			{ "Maps", "Ctrl+Shift+M", "Toggle world map" },
			{ "Maps", "Ctrl+G", "Toggle map grid" },
			{ "View", "Home", "Reset camera" },
			{ "View", "End", "Take screenshot" },
			{ "View", "Ctrl+N", "Toggle night vision" },
			{ "View", "Ctrl+X", "Toggle x-ray" },
			{ "View", "Ctrl+H", "Toggle hidden objects" },
			{ "Speed", "Alt+Q/W/E/R", "Crawl / walk / run / sprint" } };

	private HotkeyOverlay() {
	}

	public static void toggle() {
		if (Config.showHotkeyOverlay)
			visible = !visible;
	}

	public static boolean visible() {
		return visible && Config.showHotkeyOverlay;
	}

	public static void hide() {
		visible = false;
	}

	public static void draw(GOut g, Coord screenSize) {
		if (!visible())
			return;
		Coord size = new Coord(450, 42 + (HOTKEYS.length * 18));
		double desiredScale = Config.elementScale(Config.helperScale);
		double fitScale = Math.min((screenSize.x - 10) / (double) size.x,
				(screenSize.y - 10) / (double) size.y);
		double scale = Math.max(0.1, Math.min(desiredScale, fitScale));
		Coord displaySize = Widget.scaleSize(size, scale);
		Coord origin = screenSize.sub(displaySize).div(2);
		GOut overlayGraphics = g.reclip(origin, size).scaled(scale, origin);
		overlayGraphics.chcolor(new Color(0, 0, 0, 225));
		overlayGraphics.frect(Coord.z, size);
		overlayGraphics.chcolor(new Color(125, 175, 230, 255));
		overlayGraphics.rect(Coord.z, size);
		overlayGraphics.chcolor();
		overlayGraphics.atext("Solaris Hotkeys", new Coord(size.x / 2, 18), 0.5, 0.5);
		for (int i = 0; i < HOTKEYS.length; i++) {
			int y = 40 + (i * 18);
			overlayGraphics.chcolor(new Color(160, 205, 255));
			overlayGraphics.atext(HOTKEYS[i][0], new Coord(12, y), 0, 0.5);
			overlayGraphics.chcolor(new Color(255, 220, 120));
			overlayGraphics.atext(HOTKEYS[i][1], new Coord(92, y), 0, 0.5);
			overlayGraphics.chcolor(Color.WHITE);
			overlayGraphics.atext(HOTKEYS[i][2], new Coord(225, y), 0, 0.5);
		}
		overlayGraphics.chcolor(new Color(170, 170, 170));
		overlayGraphics.atext("Ctrl+Shift+F1 or Escape to close", new Coord(
				size.x / 2, size.y - 9), 0.5, 0.5);
		overlayGraphics.chcolor();
	}
}
