package haven;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WorldMapMarkerStore {
	public static class Marker {
		public final long id;
		public String gridName;
		public Coord gridOffset;
		public Coord legacyTile;
		public String name;
		public Color color;
		public boolean waypoint;

		Marker(long id, String gridName, Coord gridOffset, Coord legacyTile,
				String name, Color color, boolean waypoint) {
			this.id = id;
			this.gridName = gridName;
			this.gridOffset = (gridOffset == null) ? null : new Coord(gridOffset);
			this.legacyTile = (legacyTile == null) ? null : new Coord(legacyTile);
			this.name = name;
			this.color = color;
			this.waypoint = waypoint;
		}

		public boolean anchored() {
			return (gridName != null) && (gridOffset != null);
		}
	}

	private static WorldMapMarkerStore shared;
	private final File file;
	private final List<Marker> markers = new ArrayList<Marker>();
	private long nextId = 1;

	public static synchronized WorldMapMarkerStore getShared() {
		if (shared == null) {
			shared = new WorldMapMarkerStore(new File(
					"map/worldmap-markers.conf"));
			shared.load();
		}
		return shared;
	}

	WorldMapMarkerStore(File file) {
		this.file = file;
	}

	public synchronized List<Marker> markers() {
		return new ArrayList<Marker>(markers);
	}

	public synchronized List<Marker> search(String query) {
		String needle = (query == null) ? "" : query.trim().toLowerCase(
				Locale.ENGLISH);
		List<Marker> found = new ArrayList<Marker>();
		for (Marker marker : markers) {
			if ((needle.length() == 0) || marker.name.toLowerCase(Locale.ENGLISH)
					.contains(needle))
				found.add(marker);
		}
		return found;
	}

	public synchronized Marker add(String gridName, Coord gridOffset, String name,
			Color color) {
		if ((gridName == null) || (gridName.length() == 0) ||
				(gridOffset == null))
			return null;
		Marker marker = new Marker(nextId++, gridName, gridOffset, null,
				cleanName(name), safeColor(color), false);
		markers.add(marker);
		save();
		return marker;
	}

	public synchronized void update(Marker marker, String gridName,
			Coord gridOffset, String name, Color color) {
		if ((marker == null) || !markers.contains(marker))
			return;
		if ((gridName != null) && (gridName.length() > 0) &&
				(gridOffset != null)) {
			marker.gridName = gridName;
			marker.gridOffset = new Coord(gridOffset);
			marker.legacyTile = null;
		}
		marker.name = cleanName(name);
		marker.color = safeColor(color);
		save();
	}

	public synchronized void remove(Marker marker) {
		if ((marker != null) && markers.remove(marker))
			save();
	}

	public synchronized void setWaypoint(Marker marker) {
		boolean enable = (marker != null) && !marker.waypoint;
		for (Marker existing : markers)
			existing.waypoint = false;
		if (enable && markers.contains(marker))
			marker.waypoint = true;
		save();
	}

	public synchronized Marker waypoint() {
		for (Marker marker : markers) {
			if (marker.waypoint)
				return marker;
		}
		return null;
	}

	private static String cleanName(String name) {
		if (name == null)
			return "Marker";
		name = name.trim();
		return (name.length() == 0) ? "Marker" : name;
	}

	private static Color safeColor(Color color) {
		return (color == null) ? Color.YELLOW : color;
	}

	synchronized void load() {
		markers.clear();
		nextId = 1;
		if (!file.exists())
			return;
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(
					file), "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if ((line.length() == 0) || line.startsWith("#"))
					continue;
				try {
					String[] values = line.split("\\|", 8);
					long id;
					String gridName = null;
					Coord gridOffset = null;
					Coord legacyTile = null;
					Color color;
					boolean waypoint;
					String name;
					if (values.length == 8) {
						id = Long.parseLong(values[0]);
						if (values[1].equals("G")) {
							gridName = URLDecoder.decode(values[2], "UTF-8");
							gridOffset = new Coord(Integer.parseInt(values[3]),
									Integer.parseInt(values[4]));
						} else if (values[1].equals("L")) {
							legacyTile = new Coord(Integer.parseInt(values[3]),
									Integer.parseInt(values[4]));
						} else {
							continue;
						}
						color = new Color(Integer.parseInt(values[5]), true);
						waypoint = values[6].equals("1");
						name = URLDecoder.decode(values[7], "UTF-8");
					} else if (values.length == 6) {
						// v1 stored session-local coordinates. They cannot be
						// reconstructed after a restart, so retain them for manual
						// re-placement instead of drawing them at a false position.
						id = Long.parseLong(values[0]);
						legacyTile = new Coord(Integer.parseInt(values[1]),
								Integer.parseInt(values[2]));
						color = new Color(Integer.parseInt(values[3]), true);
						waypoint = values[4].equals("1");
						name = URLDecoder.decode(values[5], "UTF-8");
					} else {
						continue;
					}
					if (waypoint) {
						for (Marker existing : markers)
							existing.waypoint = false;
					}
					markers.add(new Marker(id, gridName, gridOffset, legacyTile,
							cleanName(name), color, waypoint));
					nextId = Math.max(nextId, id + 1);
				} catch (Exception e) {
				}
			}
		} catch (IOException e) {
			System.out.println("Could not load world-map markers: " + e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private synchronized void save() {
		File parent = file.getParentFile();
		if ((parent != null) && !parent.exists())
			parent.mkdirs();
		File temporary = new File(file.getPath() + ".new");
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(temporary), "UTF-8"));
			writer.write("# Solaris world-map markers v2");
			writer.newLine();
			for (Marker marker : markers) {
				writer.write(Long.toString(marker.id));
				writer.write('|');
				writer.write(marker.anchored() ? "G" : "L");
				writer.write('|');
				writer.write(marker.anchored() ? URLEncoder.encode(marker.gridName,
						"UTF-8") : "");
				writer.write('|');
				Coord coordinate = marker.anchored() ? marker.gridOffset
						: marker.legacyTile;
				if (coordinate == null)
					coordinate = Coord.z;
				writer.write(Integer.toString(coordinate.x));
				writer.write('|');
				writer.write(Integer.toString(coordinate.y));
				writer.write('|');
				writer.write(Integer.toString(marker.color.getRGB()));
				writer.write('|');
				writer.write(marker.waypoint ? "1" : "0");
				writer.write('|');
				writer.write(URLEncoder.encode(marker.name, "UTF-8"));
				writer.newLine();
			}
		} catch (IOException e) {
			System.out.println("Could not save world-map markers: " + e);
			return;
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
				}
			}
		}

		File backup = new File(file.getPath() + ".bak");
		if (backup.exists())
			backup.delete();
		boolean hadOriginal = file.exists();
		if (hadOriginal && !file.renameTo(backup)) {
			temporary.delete();
			return;
		}
		if (temporary.renameTo(file)) {
			if (backup.exists())
				backup.delete();
		} else if (hadOriginal) {
			backup.renameTo(file);
		}
	}
}
