package haven;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;

/** Persistent minimap tiles and their atlas coordinates. */
final class PersistentMapStore {
	private final File root;
	private final File tileDirectory;
	private final File indexFile;
	private final Map<String, Coord> mappings = new TreeMap<String, Coord>();
	private boolean loaded = false;
	private boolean dirty = false;

	PersistentMapStore(File root) {
		this.root = root;
		tileDirectory = new File(root, "tiles");
		indexFile = new File(root, "index.conf");
	}

	synchronized void loadInto(Map<String, Coord> byGrid,
			Map<Coord, String> byCoordinate) throws IOException {
		load();
		for (Map.Entry<String, Coord> entry : mappings.entrySet()) {
			Coord coordinate = new Coord(entry.getValue());
			if (byCoordinate.containsKey(coordinate))
				continue;
			byGrid.put(entry.getKey(), coordinate);
			byCoordinate.put(coordinate, entry.getKey());
		}
	}

	synchronized Coord nextComponentOrigin() throws IOException {
		load();
		if (mappings.isEmpty())
			return Coord.z;
		int maximumX = Integer.MIN_VALUE;
		for (Coord coordinate : mappings.values())
			maximumX = Math.max(maximumX, coordinate.x);
		return new Coord(maximumX + 20, 0);
	}

	synchronized void record(String gridName, Coord coordinate)
			throws IOException {
		load();
		Coord existing = mappings.get(gridName);
		if ((existing != null) && existing.equals(coordinate) && !dirty)
			return;
		for (Iterator<Map.Entry<String, Coord>> iterator = mappings.entrySet()
				.iterator(); iterator.hasNext();) {
			Map.Entry<String, Coord> entry = iterator.next();
			if (!entry.getKey().equals(gridName) &&
					entry.getValue().equals(coordinate))
				iterator.remove();
		}
		mappings.put(gridName, new Coord(coordinate));
		dirty = true;
		saveIndex();
		dirty = false;
	}

	synchronized InputStream openTile(String gridName) throws IOException {
		File tile = tileFile(gridName);
		if (!tile.exists())
			throw new FileNotFoundException(tile.getPath());
		return new FileInputStream(tile);
	}

	synchronized void saveTile(String gridName, BufferedImage image)
			throws IOException {
		if ((gridName == null) || (image == null))
			return;
		ensureDirectory(tileDirectory);
		File tile = tileFile(gridName);
		if (tile.exists())
			return;
		File temporary = new File(tile.getPath() + ".new");
		if (!ImageIO.write(image, "png", temporary))
			throw new IOException("No PNG writer is available");
		if (!temporary.renameTo(tile)) {
			if (tile.exists()) {
				temporary.delete();
			} else {
				temporary.delete();
				throw new IOException("Could not install minimap tile " + tile);
			}
		}
	}

	synchronized void replaceTile(String gridName, BufferedImage image)
			throws IOException {
		File tile = tileFile(gridName);
		if (tile.exists() && !tile.delete())
			throw new IOException("Could not replace minimap tile " + tile);
		saveTile(gridName, image);
	}

	private void load() throws IOException {
		if (loaded)
			return;
		mappings.clear();
		if (!indexFile.exists()) {
			loaded = true;
			return;
		}
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(
					indexFile), "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if ((line.length() == 0) || line.startsWith("#"))
					continue;
				String[] values = line.split("\\|", 3);
				if (values.length != 3)
					continue;
				try {
					Coord coordinate = new Coord(Integer.parseInt(values[0]),
							Integer.parseInt(values[1]));
					String gridName = URLDecoder.decode(values[2], "UTF-8");
					if (gridName.length() > 0)
						mappings.put(gridName, coordinate);
				} catch (Exception e) {
				}
			}
		} finally {
			if (reader != null)
				reader.close();
		}
		loaded = true;
		dirty = false;
	}

	private void saveIndex() throws IOException {
		ensureDirectory(root);
		File temporary = new File(indexFile.getPath() + ".new");
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(temporary), "UTF-8"));
			writer.write("# Solaris persistent minimap atlas v1");
			writer.newLine();
			for (Map.Entry<String, Coord> entry : mappings.entrySet()) {
				Coord coordinate = entry.getValue();
				writer.write(Integer.toString(coordinate.x));
				writer.write('|');
				writer.write(Integer.toString(coordinate.y));
				writer.write('|');
				writer.write(URLEncoder.encode(entry.getKey(), "UTF-8"));
				writer.newLine();
			}
		} finally {
			if (writer != null)
				writer.close();
		}
		File backup = new File(indexFile.getPath() + ".bak");
		if (backup.exists() && !backup.delete()) {
			temporary.delete();
			throw new IOException("Could not replace minimap index backup");
		}
		boolean hadIndex = indexFile.exists();
		if (hadIndex && !indexFile.renameTo(backup)) {
			temporary.delete();
			throw new IOException("Could not back up minimap index");
		}
		if (temporary.renameTo(indexFile)) {
			if (backup.exists())
				backup.delete();
		} else {
			if (hadIndex)
				backup.renameTo(indexFile);
			throw new IOException("Could not install minimap index");
		}
	}

	private File tileFile(String gridName) throws IOException {
		return new File(tileDirectory, URLEncoder.encode(gridName, "UTF-8")
				+ ".png");
	}

	static void ensureDirectory(File directory) throws IOException {
		if (directory.isDirectory())
			return;
		if (directory.exists() || !directory.mkdirs())
			throw new IOException("Could not create directory " + directory);
	}
}
