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
			{ "Context", "Middle-click item", "Find recipes using the item" },
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
		Coord origin = screenSize.sub(size).div(2);
		g.chcolor(new Color(0, 0, 0, 225));
		g.frect(origin, size);
		g.chcolor(new Color(125, 175, 230, 255));
		g.rect(origin, size);
		g.chcolor();
		g.atext("Solaris Hotkeys", origin.add(size.x / 2, 18), 0.5, 0.5);
		for (int i = 0; i < HOTKEYS.length; i++) {
			int y = origin.y + 40 + (i * 18);
			g.chcolor(new Color(160, 205, 255));
			g.atext(HOTKEYS[i][0], new Coord(origin.x + 12, y), 0, 0.5);
			g.chcolor(new Color(255, 220, 120));
			g.atext(HOTKEYS[i][1], new Coord(origin.x + 92, y), 0, 0.5);
			g.chcolor(Color.WHITE);
			g.atext(HOTKEYS[i][2], new Coord(origin.x + 225, y), 0, 0.5);
		}
		g.chcolor(new Color(170, 170, 170));
		g.atext("Ctrl+Shift+F1 or Escape to close", origin.add(size.x / 2,
				size.y - 9), 0.5, 0.5);
		g.chcolor();
	}
}
