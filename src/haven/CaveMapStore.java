package haven;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

/**
 * User-local cave rasters, positioned relative to a confirmed surface entry
 * tile. Cave server grid coordinates are session-local, so they are never
 * stored as a world coordinate.
 */
final class CaveMapStore {
	private final File root;
	private final Map<Coord, Entry> entries = new HashMap<Coord, Entry>();
	private final Set<String> loading = new HashSet<String>();
	/* TexI retains both its source image and GPU texture data. Keep a hard
	 * process-wide ceiling for this optional overlay instead of retaining every
	 * cave tile ever viewed. */
	private static final int MAX_LOADED_TEXTURES = 96;
	private int loadedTextureCount = 0;
	/* PNG compression can stall the OpenGL/render thread for noticeable periods.
	 * Keep cave capture serialized on one daemon worker and bound the queue so a
	 * large explored area cannot consume unbounded memory. */
	private final ArrayDeque<SaveRequest> pendingSaves =
			new ArrayDeque<SaveRequest>();
	private final Set<String> queuedSaves = new HashSet<String>();
	private static final int MAX_PENDING_SAVES = 64;
	private HackThread saveWorker;
	private boolean loaded;

	private static final class SaveRequest {
		final Coord entrance;
		final Coord offset;
		final BufferedImage image;
		final String key;
		SaveRequest(Coord entrance, Coord offset, BufferedImage image,
				String key) {
			this.entrance = new Coord(entrance);
			this.offset = new Coord(offset);
			this.image = image;
			this.key = key;
		}
	}

	private static final class Entry {
		final Coord entrance;
		final File directory;
		final Map<Coord, Tex> textures = new HashMap<Coord, Tex>();
		final Set<Coord> tiles = new HashSet<Coord>();
		Entry(Coord entrance, File directory) {
			this.entrance = new Coord(entrance);
			this.directory = directory;
		}
	}

	CaveMapStore(File root) { this.root = root; }

	private static String entryName(Coord c) {
		return "entry_" + c.x + "_" + c.y;
	}
	private static String tileName(Coord c) {
		return "tile_" + c.x + "_" + c.y + ".png";
	}
	private static Coord parse(String value, String prefix, String suffix) {
		if (!value.startsWith(prefix) || !value.endsWith(suffix)) return null;
		String[] parts = value.substring(prefix.length(), value.length() - suffix.length()).split("_", -1);
		if (parts.length != 2) return null;
		try { return new Coord(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])); }
		catch (NumberFormatException e) { return null; }
	}

	private synchronized void load() {
		if (loaded) return;
		loaded = true;
		File[] dirs = root.listFiles();
		if (dirs == null) return;
		for (File dir : dirs) {
			if (!dir.isDirectory()) continue;
			Coord entrance = parse(dir.getName(), "entry_", "");
			if (entrance == null) continue;
			Entry entry = new Entry(entrance, dir);
			File[] files = dir.listFiles();
			if (files != null) for (File file : files) {
				Coord offset = parse(file.getName(), "tile_", ".png");
				if ((offset != null) && file.isFile()) entry.tiles.add(offset);
			}
			if (!entry.tiles.isEmpty()) entries.put(entry.entrance, entry);
		}
	}

	synchronized void reload() {
		clearTextures(); entries.clear(); loading.clear(); pendingSaves.clear();
		queuedSaves.clear(); loaded = false; load();
	}

	private void clearTextures() {
		for (Entry entry : entries.values()) {
			for (Tex tex : entry.textures.values()) tex.dispose();
			entry.textures.clear();
		}
		loadedTextureCount = 0;
	}

	/**
	 * Schedule persistence without doing image compression from MiniMap.draw().
	 * False means the bounded queue is temporarily full; the caller may retry
	 * on a later render pass.
	 */
	synchronized boolean queueSave(Coord entrance, Coord offset,
			BufferedImage image) {
		if ((entrance == null) || (offset == null) || (image == null))
			return true;
		String key = entryName(entrance) + "|" + tileName(offset);
		if (queuedSaves.contains(key))
			return true;
		if (pendingSaves.size() >= MAX_PENDING_SAVES)
			return false;
		queuedSaves.add(key);
		pendingSaves.addLast(new SaveRequest(entrance, offset, image, key));
		if (saveWorker == null) {
			saveWorker = new HackThread(new Runnable() {
				public void run() { runSaveWorker(); }
			}, "Recorded cave map writer");
			saveWorker.setDaemon(true);
			saveWorker.start();
		}
		notifyAll();
		return true;
	}

	private void runSaveWorker() {
		for (;;) {
			SaveRequest request;
			synchronized (this) {
				while (pendingSaves.isEmpty()) {
					try { wait(); }
					catch (InterruptedException e) { return; }
				}
				request = pendingSaves.removeFirst();
			}
			try {
				save(request.entrance, request.offset, request.image);
			} catch (IOException e) {
				System.out.println("Could not save recorded cave map tile: " + e);
			} finally {
				synchronized (this) { queuedSaves.remove(request.key); }
			}
		}
	}

	void save(Coord entrance, Coord offset, BufferedImage image) throws IOException {
		if ((entrance == null) || (offset == null) || (image == null)) return;
		Entry entry;
		synchronized (this) {
			load();
			entry = entries.get(entrance);
			if (entry == null) {
				entry = new Entry(entrance, new File(root, entryName(entrance)));
				entries.put(entry.entrance, entry);
			}
			PersistentMapStore.ensureDirectory(entry.directory);
			entry.tiles.add(new Coord(offset));
		}
		if (!ImageIO.write(image, "png", new File(entry.directory, tileName(offset))))
			throw new IOException("No PNG writer is available");
	}

	private void request(final Entry entry, final Coord offset) {
		final String key = entry.directory.getPath() + "|" + offset;
		synchronized (this) {
			if (loading.contains(key) || entry.textures.containsKey(offset)) return;
			loading.add(key);
		}
		HackThread worker = new HackThread(new Runnable() {
			public void run() {
				Tex tex = null;
				try {
					BufferedImage image = ImageIO.read(new File(entry.directory, tileName(offset)));
					if (image != null) tex = new TexI(image);
				} catch (IOException e) {
					System.out.println("Could not load recorded cave map tile: " + e);
				}
				synchronized (CaveMapStore.this) {
					if (tex != null) {
						if (loadedTextureCount >= MAX_LOADED_TEXTURES)
							clearTextures();
						entry.textures.put(new Coord(offset), tex);
						loadedTextureCount++;
					}
					loading.remove(key);
				}
			}
		}, "Recorded cave map loader");
		worker.setDaemon(true); worker.start();
	}

	void draw(GOut g, Coord sessionCenter, Coord halfSize, int alpha) {
		List<Entry> copy;
		synchronized (this) { load(); copy = new ArrayList<Entry>(entries.values()); }
		for (Entry entry : copy) {
			/* Cave tiles are only added while underground. This surface-only draw
			 * path can iterate the stable set directly, avoiding a full allocation
			 * of every recorded tile on each OpenGL frame. */
			for (Coord offset : entry.tiles) {
				Coord persistentTile = entry.entrance.add(offset);
				Coord tile = MiniMap.sessionTileForPersistentTile(persistentTile);
				if (tile == null) continue;
				/* sessionCenter is the World Map centre, while halfSize is the full
				 * logical canvas size. Match MiniMap.draw(): tile coordinates are
				 * offset by half the canvas, not by the entire canvas. */
				Coord ul = tile.sub(sessionCenter).add(halfSize.div(2));
				if (!ul.isect(new Coord(-MCache.cmaps.x, -MCache.cmaps.y), halfSize.mul(2).add(MCache.cmaps.mul(2)))) continue;
				Tex tex;
				synchronized (this) { tex = entry.textures.get(offset); }
				if (tex == null) { request(entry, offset); continue; }
				g.chcolor(255, 255, 255, alpha);
				g.image(tex, ul);
			}
		}
		g.chcolor();
	}
}
