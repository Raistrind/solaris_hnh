package haven;

import static haven.MCache.tileSize;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WorldMapWindow extends Window {
	private static final Coord MIN_CONTENT_SIZE = new Coord(620, 400);
	private static final Coord DEFAULT_CONTENT_SIZE = new Coord(720, 500);
	private static final Coord RESIZE_GRIP_SIZE = new Coord(16, 17);
	private static final int SIDEBAR_WIDTH = 190;
	private static final Color[] MARKER_COLORS = { Color.YELLOW, Color.RED,
			Color.GREEN, Color.CYAN, Color.WHITE, new Color(220, 120, 255) };
	private static final String[] MARKER_COLOR_NAMES = { "Yellow", "Red",
			"Green", "Cyan", "White", "Purple" };
	private static final Text.Foundry LIST_FONT = new Text.Foundry(new Font(
			"SansSerif", Font.PLAIN, 12), Color.WHITE);
	private static WorldMapWindow instance;

	private final WorldMapMarkerStore markerStore;
	private final WorldMapCanvas canvas;
	private final MarkerList markerList;
	private final Label markerTitle;
	private final TextEntry search;
	private final Label status;
	private final Label zoom;
	private final Button gridButton;
	private final Button centerMarkerButton;
	private final Button editButton;
	private final Button waypointButton;
	private final Button deleteButton;
	private Coord contentSize;
	private boolean resizing = false;
	private Coord resizeOrigin = Coord.z;
	private String lastSearch = null;
	private String lastStatus = null;
	private WorldMapMarkerStore.Marker selected;
	private WorldMapMarkerStore.Marker relocating;
	private String notice;
	private long noticeUntil;

	public static void toggle(UI ui) {
		if ((ui == null) || (ui.sess == null) || (ui.mapview == null))
			return;
		if ((instance == null) || (instance.ui != ui)) {
			instance = new WorldMapWindow(ui);
		} else {
			ui.destroy(instance);
		}
	}

	private static Coord savedContentSize() {
		synchronized (Config.window_props) {
			Coord size = new Coord(Config.window_props.getProperty(
					"worldmap_sz", DEFAULT_CONTENT_SIZE.toString()));
			return new Coord(Math.max(MIN_CONTENT_SIZE.x, size.x), Math.max(
					MIN_CONTENT_SIZE.y, size.y));
		}
	}

	private static Coord initialPosition(Coord size) {
		return MainFrame.getCenterPoint().sub(size.div(2));
	}

	private WorldMapWindow(UI ui) {
		this(ui, savedContentSize());
	}

	private WorldMapWindow(UI ui, Coord initialSize) {
		super(initialPosition(initialSize), initialSize, ui.root, "World Map");
		justclose = true;
		contentSize = new Coord(initialSize);
		markerStore = WorldMapMarkerStore.getShared();
		canvas = new WorldMapCanvas(new Coord(0, 30), new Coord(500, 400),
				this, ui.mapview);
		canvas.grid = true;
		canvas.scale = 2;

		new Button(new Coord(0, 2), 90, this, "Center player") {
			public void click() {
				centerPlayer();
			}
		};
		gridButton = new Button(new Coord(95, 2), 65, this, "Grid: on") {
			public void click() {
				canvas.grid = !canvas.grid;
				updateGridButton();
			}
		};
		zoom = new Label(new Coord(170, 5), this, "");
		new Button(new Coord(255, 2), 75, this, "Clear trail") {
			public void click() {
				BreadcrumbTrail.clear();
			}
		};
		new Label(new Coord(335, 5), this, "Drag | Wheel | Right-click");

		markerTitle = new Label(Coord.z, this, "Markers");
		search = new TextEntry(Coord.z, new Coord(SIDEBAR_WIDTH, 20), this,
				"");
		search.tooltip = "Filter markers by name";
		markerList = new MarkerList(Coord.z, new Coord(SIDEBAR_WIDTH, 200),
				this);

		centerMarkerButton = new Button(Coord.z, 90, this, "Center") {
			public void click() {
				if (selected == null)
					return;
				if (!selected.anchored()) {
					relocating = selected;
					showNotice("Right-click this marker's correct map position.");
					return;
				}
				Coord tile = markerSessionTile(selected);
				if (tile != null)
					canvas.centerOnTile(tile);
				else
					showNotice("The saved map position is not available yet.");
			}
		};
		editButton = new Button(Coord.z, 90, this, "Edit") {
			public void click() {
				if (selected != null)
					openMarkerEditor(selected, anchorForMarker(selected));
			}
		};
		waypointButton = new Button(Coord.z, 90, this, "Set waypoint") {
			public void click() {
				if (selected != null) {
					if (!selected.anchored()) {
						if (selected.waypoint) {
							markerStore.setWaypoint(selected);
							refreshMarkers();
							return;
						}
						showNotice("Re-place this marker before using it as a waypoint.");
						return;
					}
					markerStore.setWaypoint(selected);
					refreshMarkers();
				}
			}
		};
		deleteButton = new Button(Coord.z, 90, this, "Delete") {
			public void click() {
				confirmDeleteSelected();
			}
		};
		status = new Label(Coord.z, this, "");

		layoutContent();
		refreshMarkers();
		centerPlayer();
		updateGridButton();
		updateZoomLabel();
	}

	private void layoutContent() {
		ssz = new Coord(contentSize);
		recalcsz(contentSize);
		placecbtn();
		int sidebarX = contentSize.x - SIDEBAR_WIDTH;
		int mapWidth = sidebarX - 10;
		int mapHeight = contentSize.y - 55;
		canvas.c = new Coord(0, 30);
		canvas.sz = new Coord(mapWidth, mapHeight);

		markerTitle.c = new Coord(sidebarX, 3);
		search.c = new Coord(sidebarX, 23);
		search.sz = new Coord(SIDEBAR_WIDTH, 20);
		markerList.c = new Coord(sidebarX, 48);
		markerList.sz = new Coord(SIDEBAR_WIDTH, contentSize.y - 148);

		int buttonY = contentSize.y - 95;
		centerMarkerButton.c = new Coord(sidebarX, buttonY);
		editButton.c = new Coord(sidebarX + 98, buttonY);
		waypointButton.c = new Coord(sidebarX, buttonY + 25);
		deleteButton.c = new Coord(sidebarX + 98, buttonY + 25);
		status.c = new Coord(0, contentSize.y - 18);
	}

	private void updateGridButton() {
		gridButton.change(canvas.grid ? "Grid: on" : "Grid: off", Color.WHITE);
	}

	private void updateZoomLabel() {
		zoom.settext(String.format(Locale.US, "Zoom: %.0f%%",
				canvas.getScale() * 100));
	}

	private Coord playerTile() {
		if ((ui.mapview == null) || (ui.sess == null))
			return null;
		Gob player = ui.sess.glob.oc.getgob(ui.mapview.playergob);
		return (player == null) ? null : player.position().div(tileSize);
	}

	private MiniMap.MapAnchor anchorForTile(Coord tile) {
		if ((tile == null) || (ui.sess == null))
			return null;
		return MiniMap.anchorForSessionTile(ui.sess.glob.map, tile);
	}

	private MiniMap.MapAnchor anchorForMarker(
			WorldMapMarkerStore.Marker marker) {
		if ((marker == null) || !marker.anchored())
			return null;
		return new MiniMap.MapAnchor(marker.gridName, marker.gridOffset);
	}

	private Coord markerSessionTile(WorldMapMarkerStore.Marker marker) {
		return ((marker == null) || !marker.anchored()) ? null
				: MiniMap.sessionTileForAnchor(marker.gridName, marker.gridOffset);
	}

	private Coord markerPersistentTile(WorldMapMarkerStore.Marker marker) {
		return ((marker == null) || !marker.anchored()) ? null
				: MiniMap.persistentTileForAnchor(marker.gridName,
						marker.gridOffset);
	}

	private Coord playerPersistentTile() {
		MiniMap.MapAnchor anchor = anchorForTile(playerTile());
		return (anchor == null) ? null : MiniMap.persistentTileForAnchor(
				anchor.gridName, anchor.offset);
	}

	private void centerPlayer() {
		Coord player = playerTile();
		if (player != null)
			canvas.centerOnTile(player);
	}

	private void selectMarker(WorldMapMarkerStore.Marker marker) {
		if (relocating != marker)
			relocating = null;
		selected = marker;
		markerList.selected = marker;
		updateWaypointButton();
	}

	private void updateWaypointButton() {
		centerMarkerButton.change((selected != null) && !selected.anchored()
				? "Re-place" : "Center", Color.WHITE);
		waypointButton.change((selected != null) && selected.waypoint
				? "Clear waypoint" : "Set waypoint", Color.WHITE);
	}

	private void refreshMarkers() {
		lastSearch = search.text;
		List<WorldMapMarkerStore.Marker> filtered = markerStore.search(lastSearch);
		if ((selected != null) && !markerStore.markers().contains(selected))
			selected = null;
		markerList.setMarkers(filtered);
		markerList.selected = selected;
		updateWaypointButton();
	}

	private void openMarkerEditor(WorldMapMarkerStore.Marker marker,
			MiniMap.MapAnchor anchor) {
		new MarkerEditor(MainFrame.getCenterPoint().sub(150, 80), ui.root,
				marker, anchor);
	}

	private void showNotice(String text) {
		notice = text;
		noticeUntil = System.currentTimeMillis() + 6000;
		lastStatus = null;
	}

	private void confirmDeleteSelected() {
		if (selected == null)
			return;
		final WorldMapMarkerStore.Marker marker = selected;
		new ConfirmWnd(c.add(40, 40), ui.root, "Delete marker '" + marker.name
				+ "'?", new ConfirmWnd.Callback() {
			public void result(Boolean confirmed) {
				if (confirmed) {
					markerStore.remove(marker);
					if (selected == marker)
						selected = null;
					refreshMarkers();
				}
			}
		});
	}

	private void updateStatus() {
		WorldMapMarkerStore.Marker waypoint = markerStore.waypoint();
		Coord player = playerPersistentTile();
		String text;
		if ((notice != null) && (System.currentTimeMillis() < noticeUntil)) {
			text = notice;
		} else if ((waypoint != null) && waypoint.anchored() &&
				(player != null) && (markerPersistentTile(waypoint) != null)) {
			Coord waypointTile = markerPersistentTile(waypoint);
			long distance = Math.round(player.dist(waypointTile));
			text = "Waypoint: " + waypoint.name + " - " + distance
					+ " tiles " + direction(player, waypointTile);
		} else if ((selected != null) && !selected.anchored()) {
			text = "This old marker needs re-placement before it can be mapped.";
		} else if (canvas.mouseTile != null) {
			text = "Map position: " + canvas.mouseTile;
		} else {
			text = "No active waypoint";
		}
		if (!text.equals(lastStatus)) {
			status.settext(text);
			lastStatus = text;
		}
	}

	static String direction(Coord from, Coord to) {
		int dx = to.x - from.x;
		int dy = to.y - from.y;
		if ((dx == 0) && (dy == 0))
			return "here";
		double angle = Math.atan2(dy, dx);
		String[] directions = { "E", "SE", "S", "SW", "W", "NW", "N",
				"NE" };
		int index = (int) Math.round(angle / (Math.PI / 4));
		index = (index + 8) % 8;
		return directions[index];
	}

	public void update(long dt) {
		if ((lastSearch == null) || !lastSearch.equals(search.text))
			refreshMarkers();
		updateStatus();
		super.update(dt);
	}

	public void draw(GOut g) {
		super.draw(g);
		g.image(grip, sz.sub(RESIZE_GRIP_SIZE));
	}

	public boolean mousedown(Coord c, int button) {
		if ((button == 1) && c.isect(sz.sub(RESIZE_GRIP_SIZE),
				RESIZE_GRIP_SIZE)) {
			resizing = true;
			resizeOrigin = c;
			ui.grabmouse(this);
			return true;
		}
		return super.mousedown(c, button);
	}

	public void mousemove(Coord c) {
		if (resizing) {
			Coord delta = c.sub(resizeOrigin);
			contentSize = new Coord(Math.max(MIN_CONTENT_SIZE.x, contentSize.x
					+ delta.x), Math.max(MIN_CONTENT_SIZE.y, contentSize.y
					+ delta.y));
			resizeOrigin = c;
			layoutContent();
		} else {
			super.mousemove(c);
		}
	}

	public boolean mouseup(Coord c, int button) {
		if (resizing && (button == 1)) {
			resizing = false;
			ui.grabmouse(null);
			Config.setWindowOpt("worldmap_sz", contentSize.toString());
			return true;
		}
		return super.mouseup(c, button);
	}

	public void destroy() {
		Config.setWindowOpt("worldmap_sz", contentSize.toString());
		if (instance == this)
			instance = null;
		super.destroy();
	}

	private class MarkerList extends Widget {
		private static final int ROW_HEIGHT = 20;
		private List<WorldMapMarkerStore.Marker> markers = new ArrayList<WorldMapMarkerStore.Marker>();
		private List<Text> labels = new ArrayList<Text>();
		private int scroll = 0;
		WorldMapMarkerStore.Marker selected;

		MarkerList(Coord c, Coord sz, Widget parent) {
			super(c, sz, parent);
		}

		void setMarkers(List<WorldMapMarkerStore.Marker> markers) {
			for (Text label : labels)
				label.tex().dispose();
			this.markers = markers;
			labels = new ArrayList<Text>();
			for (WorldMapMarkerStore.Marker marker : markers) {
				String label = marker.name + (marker.anchored() ? ""
						: " [re-place]") + (marker.waypoint ? " [waypoint]" : "");
				labels.add(LIST_FONT.render(label));
			}
			clampScroll();
		}

		private int visibleRows() {
			return Math.max(1, sz.y / ROW_HEIGHT);
		}

		private void clampScroll() {
			scroll = Math.max(0, Math.min(scroll, Math.max(0, markers.size()
					- visibleRows())));
		}

		public void draw(GOut g) {
			clampScroll();
			g.chcolor(new Color(20, 20, 20, 180));
			g.frect(Coord.z, sz);
			g.chcolor();
			int rows = visibleRows();
			for (int row = 0; row < rows; row++) {
				int index = scroll + row;
				if (index >= markers.size())
					break;
				WorldMapMarkerStore.Marker marker = markers.get(index);
				int y = row * ROW_HEIGHT;
				if (marker == selected) {
					g.chcolor(new Color(80, 80, 100, 220));
					g.frect(new Coord(0, y), new Coord(sz.x, ROW_HEIGHT));
				}
				g.chcolor(Color.BLACK);
				g.frect(new Coord(3, y + 4), new Coord(12, 12));
				g.chcolor(marker.color);
				g.frect(new Coord(5, y + 6), new Coord(8, 8));
				g.chcolor();
				g.image(labels.get(index).tex(), new Coord(20, y + 2));
			}
			g.chcolor(Color.GRAY);
			g.rect(Coord.z, sz);
			g.chcolor();
		}

		public boolean mousedown(Coord c, int button) {
			if (button != 1)
				return false;
			int index = scroll + (c.y / ROW_HEIGHT);
			if ((index >= 0) && (index < markers.size()))
				selectMarker(markers.get(index));
			return true;
		}

		public boolean mousewheel(Coord c, int amount) {
			scroll += amount;
			clampScroll();
			return true;
		}
	}

	private class WorldMapCanvas extends MiniMap {
		private boolean dragging = false;
		private boolean dragged = false;
		private Coord dragOrigin = Coord.z;
		Coord mouseTile = null;

		WorldMapCanvas(Coord c, Coord sz, Widget parent, MapView mv) {
			super(c, sz, parent, mv, false);
		}

		protected void drawMapOverlay(GOut g, Coord tc, Coord hsz) {
			WorldMapMarkerStore.Marker waypoint = markerStore.waypoint();
			Coord player = playerTile();
			Coord waypointTile = markerSessionTile(waypoint);
			if ((waypointTile != null) && (player != null)) {
				g.chcolor(new Color(255, 255, 0, 180));
				g.line(player.sub(tc).add(hsz.div(2)), waypointTile.sub(tc).add(
						hsz.div(2)), 2);
			}

			for (WorldMapMarkerStore.Marker marker : markerStore.markers()) {
				Coord markerTile = markerSessionTile(marker);
				if (markerTile == null)
					continue;
				Coord mc = markerTile.sub(tc).add(hsz.div(2));
				if (!mc.isect(new Coord(-40, -20), hsz.add(80, 40)))
					continue;
				int radius = (marker == selected) ? 6 : 4;
				g.chcolor(Color.BLACK);
				g.fellipse(mc, new Coord(radius + 2, radius + 2));
				g.chcolor(marker.color);
				g.fellipse(mc, new Coord(radius, radius));
				g.chcolor(Color.WHITE);
				g.atext(marker.name, mc.add(radius + 4, 0), 0, 0.5);
			}

			if (player != null) {
				Coord pc = player.sub(tc).add(hsz.div(2));
				g.chcolor(Color.BLACK);
				g.fellipse(pc, new Coord(6, 6));
				g.chcolor(Color.WHITE);
				g.fellipse(pc, new Coord(4, 4));
			}
			g.chcolor();
		}

		private WorldMapMarkerStore.Marker markerAt(Coord c) {
			WorldMapMarkerStore.Marker found = null;
			double closest = Double.MAX_VALUE;
			for (WorldMapMarkerStore.Marker marker : markerStore.markers()) {
				Coord markerTile = markerSessionTile(marker);
				if (markerTile == null)
					continue;
				double distance = tileToLocal(markerTile).dist(c);
				if ((distance <= 10) && (distance < closest)) {
					found = marker;
					closest = distance;
				}
			}
			return found;
		}

		public boolean mousedown(Coord c, int button) {
			if (button == 1) {
				dragging = true;
				dragged = false;
				dragOrigin = c;
				ui.grabmouse(this);
				return true;
			} else if (button == 3) {
				MiniMap.MapAnchor clicked = anchorForTile(localToTile(c));
				if (clicked == null) {
					showNotice("That saved map position is not available yet.");
					return true;
				}
				if (relocating != null) {
					WorldMapMarkerStore.Marker marker = relocating;
					relocating = null;
					openMarkerEditor(marker, clicked);
					return true;
				}
				WorldMapMarkerStore.Marker marker = markerAt(c);
				openMarkerEditor(marker, (marker == null) ? clicked
						: anchorForMarker(marker));
				return true;
			}
			return false;
		}

		public void mousemove(Coord c) {
			MiniMap.MapAnchor anchor = anchorForTile(localToTile(c));
			mouseTile = (anchor == null) ? null : MiniMap.persistentTileForAnchor(
					anchor.gridName, anchor.offset);
			if (dragging) {
				Coord delta = dragOrigin.sub(c);
				if (delta.dist(Coord.z) > 1)
					dragged = true;
				off = off.add(delta);
				dragOrigin = c;
			}
		}

		public boolean mouseup(Coord c, int button) {
			if (dragging && (button == 1)) {
				dragging = false;
				ui.grabmouse(null);
				if (!dragged)
					selectMarker(markerAt(c));
				return true;
			}
			return false;
		}

		public boolean mousewheel(Coord c, int amount) {
			setScale(scale - amount);
			updateZoomLabel();
			return true;
		}

		public Object tooltip(Coord c, boolean again) {
			WorldMapMarkerStore.Marker marker = markerAt(c);
			if (marker == null)
				return null;
			Coord player = playerPersistentTile();
			Coord markerTile = markerPersistentTile(marker);
			if ((player == null) || (markerTile == null))
				return marker.name;
			return marker.name + " - " + Math.round(player.dist(markerTile))
					+ " tiles " + direction(player, markerTile);
		}
	}

	private class MarkerEditor extends Window {
		private final WorldMapMarkerStore.Marker marker;
		private final MiniMap.MapAnchor markerAnchor;
		private final TextEntry name;
		private final Button[] colorButtons = new Button[MARKER_COLORS.length];
		private Color selectedColor;

		MarkerEditor(Coord c, Widget parent,
				WorldMapMarkerStore.Marker marker, MiniMap.MapAnchor markerAnchor) {
			super(c, new Coord(310, 125), parent, (marker == null) ? "Add Marker"
					: (!marker.anchored() && (markerAnchor != null))
							? "Re-place Marker" : "Edit Marker");
			justclose = true;
			this.marker = marker;
			this.markerAnchor = markerAnchor;
			selectedColor = (marker == null) ? Color.YELLOW : marker.color;
			new Label(new Coord(0, 2), this, "Name:");
			name = new TextEntry(new Coord(45, 0), new Coord(255, 20), this,
					(marker == null) ? "Marker" : marker.name);
			new Label(new Coord(0, 28), this, "Color:");
			for (int i = 0; i < MARKER_COLORS.length; i++) {
				final int colorIndex = i;
				colorButtons[i] = new Button(new Coord(i * 50, 48), 47, this,
						MARKER_COLOR_NAMES[i]) {
					public void click() {
						selectColor(colorIndex);
					}
				};
			}
			new Button(new Coord(105, 78), 95, this, "Save") {
				public void click() {
					saveMarker();
				}
			};
			Coord mapTile = (markerAnchor == null) ? null
					: MiniMap.persistentTileForAnchor(markerAnchor.gridName,
							markerAnchor.offset);
			new Label(new Coord(0, 105), this, (mapTile == null)
					? "Position: needs re-placement" : "Map position: " + mapTile);
			selectColor(indexOfColor(selectedColor));
		}

		private int indexOfColor(Color color) {
			for (int i = 0; i < MARKER_COLORS.length; i++) {
				if (MARKER_COLORS[i].getRGB() == color.getRGB())
					return i;
			}
			return 0;
		}

		private void selectColor(int index) {
			selectedColor = MARKER_COLORS[index];
			for (int i = 0; i < colorButtons.length; i++) {
				String prefix = (i == index) ? "*" : "";
				colorButtons[i].change(prefix + MARKER_COLOR_NAMES[i],
						MARKER_COLORS[i]);
			}
		}

		private void saveMarker() {
			WorldMapMarkerStore.Marker saved = marker;
			if (saved == null) {
				if (markerAnchor == null)
					return;
				saved = markerStore.add(markerAnchor.gridName,
						markerAnchor.offset, name.text, selectedColor);
			} else {
				markerStore.update(saved, (markerAnchor == null) ? null
						: markerAnchor.gridName, (markerAnchor == null) ? null
						: markerAnchor.offset, name.text, selectedColor);
			}
			refreshMarkers();
			selectMarker(saved);
			ui.destroy(this);
		}
	}
}
