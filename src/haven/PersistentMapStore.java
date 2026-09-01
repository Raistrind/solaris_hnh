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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;

/** Persistent minimap tiles and their atlas coordinates. */
final class PersistentMapStore {
	private final File root;
	private final File tileDirectory;
	private final File indexFile;
	private final File metadataFile;
	private final Map<String, Coord> mappings = new TreeMap<String, Coord>();
	private final Map<String, Component> components = new TreeMap<String, Component>();
	private boolean loaded = false;
	private boolean dirty = false;
	private volatile int detailedScanProgress = 0;
	private long detailedScanNumber = 0;
	/* PNG grid identifiers are not stable across every server session.  Keep a
	 * small, read-only water signature cache so an existing surface atlas can be
	 * recognized from its coastline when an identifier cannot be resolved. */
	private final Map<String, WaterPattern> waterPatterns =
			new HashMap<String, WaterPattern>();
	/* Unlike the water-only fallback, this is a compact representation of the
	 * actual detailed minimap PNG. It is intentionally derived from the same
	 * images that the minimap renders and persists. */
	private final Map<String, DetailedPattern> detailedPatterns =
			new HashMap<String, DetailedPattern>();

	static final class DetailedPattern {
		static final int SIDE = 16;
		final byte[] red, green, blue;
		final boolean[] occluded;

		private DetailedPattern(byte[] red, byte[] green, byte[] blue,
				boolean[] occluded) {
			this.red = red;
			this.green = green;
			this.blue = blue;
			this.occluded = occluded;
		}

		static DetailedPattern from(BufferedImage image) {
			if (image == null)
				return null;
			byte[] red = new byte[SIDE * SIDE];
			byte[] green = new byte[SIDE * SIDE];
			byte[] blue = new byte[SIDE * SIDE];
			boolean[] occluded = new boolean[SIDE * SIDE];
			for (int by = 0; by < SIDE; by++) {
				int y0 = (by * image.getHeight()) / SIDE;
				int y1 = ((by + 1) * image.getHeight()) / SIDE;
				for (int bx = 0; bx < SIDE; bx++) {
					int x0 = (bx * image.getWidth()) / SIDE;
					int x1 = ((bx + 1) * image.getWidth()) / SIDE;
					int pixels = Math.max(1, (x1 - x0) * (y1 - y0));
					int dark = 0, samples = 0;
					long rs = 0, gs = 0, bs = 0;
					for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
						int rgb = image.getRGB(x, y);
						int r = (rgb >> 16) & 0xff;
						int g = (rgb >> 8) & 0xff;
						int b = rgb & 0xff;
						if ((r < 45) && (g < 45) && (b < 45)) {
							dark++;
							continue;
						}
						rs += r; gs += g; bs += b; samples++;
					}
					int i = (by * SIDE) + bx;
					/* Tree silhouettes can be present in one saved copy but not
					 * another. Do not let that volatile overlay reject a coastline. */
					occluded[i] = (dark * 3 >= pixels) || (samples == 0);
					if (samples > 0) {
						red[i] = (byte)(rs / samples);
						green[i] = (byte)(gs / samples);
						blue[i] = (byte)(bs / samples);
					}
				}
			}
			return new DetailedPattern(red, green, blue, occluded);
		}

		double similarity(DetailedPattern other) {
			if (other == null)
				return 0.0;
			long distance = 0;
			int compared = 0;
			for (int i = 0; i < red.length; i++) if (!occluded[i] &&
					!other.occluded[i]) {
				distance += Math.abs((red[i] & 0xff) - (other.red[i] & 0xff));
				distance += Math.abs((green[i] & 0xff) - (other.green[i] & 0xff));
				distance += Math.abs((blue[i] & 0xff) - (other.blue[i] & 0xff));
				compared++;
			}
			return (compared == 0) ? 0.0 : 1.0 -
					(((double)distance) / (compared * 765.0));
		}
	}

	static final class DetailedAnchorMatch {
		final Coord origin;
		final int grids;
		final int observedGrids;
		final double confidence;
		final double secondConfidence;
		DetailedAnchorMatch(Coord origin, int grids, int observedGrids,
				double confidence, double secondConfidence) {
			this.origin = new Coord(origin);
			this.grids = grids;
			this.observedGrids = observedGrids;
			this.confidence = confidence;
			this.secondConfidence = secondConfidence;
		}
	}

	static final class WaterPattern {
		static final int SIDE = 8;
		final byte[] coverage;
		final boolean[] occluded;
		final int waterCells;

		private WaterPattern(byte[] coverage, boolean[] occluded, int waterCells) {
			this.coverage = coverage;
			this.occluded = occluded;
			this.waterCells = waterCells;
		}

		static WaterPattern from(BufferedImage image) {
			if (image == null)
				return null;
			byte[] coverage = new byte[SIDE * SIDE];
			boolean[] occluded = new boolean[SIDE * SIDE];
			int waterCells = 0;
			for (int by = 0; by < SIDE; by++) {
				int y0 = (by * image.getHeight()) / SIDE;
				int y1 = ((by + 1) * image.getHeight()) / SIDE;
				for (int bx = 0; bx < SIDE; bx++) {
					int x0 = (bx * image.getWidth()) / SIDE;
					int x1 = ((bx + 1) * image.getWidth()) / SIDE;
					int total = Math.max(1, (x1 - x0) * (y1 - y0));
					int blue = 0;
					int dark = 0;
					for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
						int rgb = image.getRGB(x, y);
						int r = (rgb >> 16) & 0xff;
						int g = (rgb >> 8) & 0xff;
						int b = rgb & 0xff;
						if ((r < 45) && (g < 45) && (b < 45))
							dark++;
						/* Both deep and shallow water are blue-dominant in the
						 * minimap PNG palette. Deliberately ignore all land detail. */
						if ((b > 72) && (b > r + 20) && (b > g + 8))
							blue++;
					}
					int value = Math.min(4, (blue * 5) / total);
					int index = (by * SIDE) + bx;
					coverage[index] = (byte)value;
					/* Black tree silhouettes may hide the water underneath. Treat a
					 * visibly occluded coarse cell as a wildcard during comparison. */
					occluded[index] = (dark * 20) >= total;
					if (value > 0)
						waterCells++;
				}
			}
			return new WaterPattern(coverage, occluded, waterCells);
		}

		boolean hasWater() { return waterCells >= 2; }

		double similarity(WaterPattern other) {
			if (other == null)
				return 0.0;
			int distance = 0;
			int compared = 0;
			for (int i = 0; i < coverage.length; i++)
				if (!occluded[i] && !other.occluded[i]) {
					distance += Math.abs(coverage[i] - other.coverage[i]);
					compared++;
				}
			return (compared == 0) ? 0.0
					: 1.0 - (((double)distance) / (compared * 4.0));
		}
	}

	static final class WaterAnchorMatch {
		final Coord origin;
		final int grids;
		final double confidence;
		final double secondConfidence;
		final boolean automatic;
		WaterAnchorMatch(Coord origin, int grids, double confidence,
				double secondConfidence, boolean automatic) {
			this.origin = new Coord(origin);
			this.grids = grids;
			this.confidence = confidence;
			this.secondConfidence = secondConfidence;
			this.automatic = automatic;
		}
	}

	private static class Component {
		final String gridName;
		final Coord origin;
		final String layer;

		Component(String gridName, Coord origin, String layer) {
			this.gridName = gridName;
			this.origin = new Coord(origin);
			this.layer = layer;
		}
	}

	PersistentMapStore(File root) {
		this.root = root;
		tileDirectory = new File(root, "tiles");
		indexFile = new File(root, "index.conf");
		metadataFile = new File(root, "metadata.conf");
	}

	int detailedScanProgress() {
		return detailedScanProgress;
	}

	/* Additive v2 metadata. The legacy index remains the authoritative source
	 * for tiles and is never rewritten into a new incompatible format. */
	synchronized void recordComponent(String gridName, Coord origin,
			String layer) throws IOException {
		if ((gridName == null) || (origin == null))
			return;
		load();
		String actualLayer = (layer == null) ? "unknown" : layer;
		Component previous = components.get(gridName);
		if ((previous != null) && previous.origin.equals(origin) &&
				previous.layer.equals(actualLayer))
			return;
		/* Do not mutate the in-memory record until its complete replacement file
		 * has been installed. A failed write can then be retried safely. */
		Map<String, Component> replacement = new TreeMap<String, Component>(components);
		replacement.put(gridName, new Component(gridName, origin, actualLayer));
		saveMetadata(replacement);
		components.clear();
		components.putAll(replacement);
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
		System.out.println("[PersistentMap] loaded atlas records=" + mappings.size() +
				", components=" + components.size() + ", tile directory=" +
				tileDirectory.getPath());
	}

	synchronized Coord nextComponentOrigin() throws IOException {
		load();
		if (mappings.isEmpty() && components.isEmpty())
			return Coord.z;
		int maximumX = Integer.MIN_VALUE;
		for (Coord coordinate : mappings.values())
			maximumX = Math.max(maximumX, coordinate.x);
		/* Legacy atlases have no component metadata, so their tile bounds are
		 * included. Leave a full 1024-grid band rather than a small guessed gap. */
		for (Component component : components.values())
			maximumX = Math.max(maximumX, component.origin.x);
		return new Coord(maximumX + 1024, 0);
	}

	synchronized void record(String gridName, Coord coordinate)
			throws IOException {
		load();
		Coord existing = mappings.get(gridName);
		if ((existing != null) && existing.equals(coordinate) && !dirty)
			return;
		for (Map.Entry<String, Coord> entry : mappings.entrySet()) {
			if (!entry.getKey().equals(gridName) &&
					entry.getValue().equals(coordinate))
				throw new IOException("Persistent minimap coordinate collision at " +
						coordinate + " between " + entry.getKey() + " and " + gridName);
		}
		mappings.put(gridName, new Coord(coordinate));
		dirty = true;
		saveIndex();
		dirty = false;
	}

	/**
	 * Replaces the tile currently occupying a coordinate after the caller has
	 * independently verified that the new hash belongs there. The old PNG is
	 * deliberately retained as an orphaned historical copy; the index changes
	 * atomically so a failed write cannot lose the previous mapping.
	 */
	synchronized boolean replaceAtConfirmedLocation(String gridName,
			Coord coordinate, BufferedImage image) throws IOException {
		if ((gridName == null) || (coordinate == null) || (image == null))
			return false;
		load();
		String previousGrid = null;
		for (Map.Entry<String, Coord> entry : mappings.entrySet()) {
			if (entry.getValue().equals(coordinate)) {
				previousGrid = entry.getKey();
				break;
			}
		}
		if ((previousGrid == null) || previousGrid.equals(gridName))
			return false;
		Coord existing = mappings.get(gridName);
		if ((existing != null) && !existing.equals(coordinate))
			return false;
		/* Install the image first. The index never points at an absent tile. */
		saveTile(gridName, image);
		Map<String, Coord> replacement = new TreeMap<String, Coord>(mappings);
		replacement.remove(previousGrid);
		replacement.put(gridName, new Coord(coordinate));
		Map<String, Coord> original = new TreeMap<String, Coord>(mappings);
		mappings.clear();
		mappings.putAll(replacement);
		try {
			saveIndex();
		} catch (IOException ex) {
			mappings.clear();
			mappings.putAll(original);
			throw ex;
		}
		dirty = false;
		waterPatterns.remove(previousGrid);
		detailedPatterns.remove(previousGrid);
		return true;
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
		waterPatterns.remove(gridName);
		detailedPatterns.remove(gridName);
	}

	/**
	 * Scores a currently visible 3x3 set of detailed map PNGs against every
	 * possible persistent centre tile and its stored neighbours. The returned
	 * origin is never applied here; callers must explicitly ask the player to
	 * accept it. Keeping the centre fixed prevents a handful of matching river
	 * tiles from sliding a partial sample to an arbitrary place in the atlas.
	 */
	synchronized DetailedAnchorMatch findDetailedAnchor(Coord observedCenter,
			Map<Coord, DetailedPattern> observed) throws IOException {
		long scan = ++detailedScanNumber;
		detailedScanProgress = 0;
		load();
		System.out.println("[PersistentMap] detailed scan #" + scan + " started: " +
				"observed=" + ((observed == null) ? 0 : observed.size()) +
				"/9, atlas records=" + mappings.size() + ", center=" + observedCenter);
		if ((observedCenter == null) || (observed == null) ||
				(observed.size() != 9) || mappings.isEmpty()) {
			System.out.println("[PersistentMap] detailed scan #" + scan +
					" skipped: complete 9/9 observed neighbourhood required or empty atlas");
			return null;
		}
		/* Coord has value equality but intentionally lacks hashCode in this
		 * legacy client. Use TreeMap so equal coordinate values resolve reliably. */
		Map<Coord, DetailedPattern> saved = new TreeMap<Coord, DetailedPattern>();
		int savedTotal = Math.max(1, mappings.size());
		int loadedCount = 0;
		int missingCount = 0;
		for (Map.Entry<String, Coord> entry : mappings.entrySet()) {
			DetailedPattern pattern = detailedPattern(entry.getKey());
			if (pattern != null)
				saved.put(new Coord(entry.getValue()), pattern);
			else
				missingCount++;
			loadedCount++;
			detailedScanProgress = Math.min(45, (loadedCount * 45) / savedTotal);
		}
		System.out.println("[PersistentMap] detailed scan #" + scan + " loaded " +
				saved.size() + "/" + mappings.size() + " detailed atlas tiles" +
				((missingCount == 0) ? "" : " (missing=" + missingCount + ")"));
		DetailedAnchorMatch best = null;
		double second = -1.0;
		int candidateIndex = 0;
		int candidateTotal = Math.max(1, saved.size());
		for (Coord persistentCenter : saved.keySet()) {
			Coord origin = observedCenter.sub(persistentCenter);
			int compared = 0;
			double total = 0.0;
			for (Map.Entry<Coord, DetailedPattern> current : observed.entrySet()) {
				DetailedPattern candidate = saved.get(current.getKey().sub(origin));
				if (candidate == null)
					continue;
				compared++;
				total += current.getValue().similarity(candidate);
			}
			/* Every tile in the observed 3x3 neighbourhood must be present in the
			 * candidate. Partial matches can otherwise anchor at a coincidental
			 * river/coastline fragment. */
			if (compared != 9)
				continue;
			double confidence = total / compared;
			if ((best == null) || (confidence > best.confidence)) {
				if (best != null)
					second = Math.max(second, best.confidence);
				best = new DetailedAnchorMatch(origin, compared, observed.size(),
						confidence, -1.0);
			} else {
				second = Math.max(second, confidence);
			}
			detailedScanProgress = 45 + ((++candidateIndex * 55) / candidateTotal);
		}
		detailedScanProgress = 100;
		if (best == null) {
			System.out.println("[PersistentMap] detailed scan #" + scan +
					" completed: no complete 9/9 candidate");
			return null;
		}
		System.out.println("[PersistentMap] detailed scan #" + scan +
				" completed: best=" + String.format(java.util.Locale.US, "%.2f%%", best.confidence * 100.0) +
				" origin=" + best.origin + " compared=" + best.grids +
				"/" + best.observedGrids + " tiles, runnerUp=" +
				((second < 0.0) ? "none" : String.format(java.util.Locale.US, "%.2f%%", second * 100.0)) +
				", gap=" + ((second < 0.0) ? "n/a" : String.format(java.util.Locale.US, "%.2f%%", (best.confidence - second) * 100.0)));
		return new DetailedAnchorMatch(best.origin, best.grids,
				best.observedGrids, best.confidence, second);
	}

	/**
	 * Finds a session-grid -> persistent-grid origin using only blue-water
	 * layout. This is intentionally conservative: three independently loaded
	 * water-bearing grids must agree and the best candidate must beat every
	 * other candidate. It never writes the atlas or supplies a guessed origin.
	 */
	synchronized WaterAnchorMatch findWaterAnchor(
			Map<Coord, WaterPattern> observed) throws IOException {
		load();
		if ((observed == null) || (observed.size() < 3) || mappings.isEmpty())
			return null;
		Map<Coord, WaterPattern> saved = new TreeMap<Coord, WaterPattern>();
		List<Coord> savedWater = new ArrayList<Coord>();
		for (Map.Entry<String, Coord> entry : mappings.entrySet()) {
			WaterPattern pattern = waterPattern(entry.getKey());
			if (pattern == null)
				continue;
			saved.put(new Coord(entry.getValue()), pattern);
			if (pattern.hasWater())
				savedWater.add(new Coord(entry.getValue()));
		}
		Map<Coord, Integer> candidates = new TreeMap<Coord, Integer>();
		for (Map.Entry<Coord, WaterPattern> current : observed.entrySet()) {
			if (!current.getValue().hasWater())
				continue;
			for (Coord persistent : savedWater) {
				WaterPattern candidate = saved.get(persistent);
				if (current.getValue().similarity(candidate) < 0.94)
					continue;
				Coord origin = current.getKey().sub(persistent);
				Integer count = candidates.get(origin);
				candidates.put(origin, (count == null) ? 1 : count + 1);
			}
		}
		WaterAnchorMatch best = null;
		double second = -1.0;
		for (Map.Entry<Coord, Integer> entry : candidates.entrySet()) {
			int compared = 0;
			double total = 0.0;
			for (Map.Entry<Coord, WaterPattern> current : observed.entrySet()) {
				if (!current.getValue().hasWater())
					continue;
				WaterPattern candidate = saved.get(current.getKey().sub(entry.getKey()));
				if (candidate == null)
					continue;
				compared++;
				total += current.getValue().similarity(candidate);
			}
			if (compared == 0)
				continue;
			double confidence = total / compared;
			if (best == null || confidence > best.confidence) {
				if (best != null)
					second = Math.max(second, best.confidence);
				best = new WaterAnchorMatch(entry.getKey(), entry.getValue(), confidence,
						-1.0, false);
			} else {
				second = Math.max(second, confidence);
			}
		}
		if (best == null)
			return null;
		/* A coastline match must be both strong and unique enough to avoid
		 * merging separate rivers or lakes in the user's atlas. */
		boolean automatic = (best.grids >= 3) && (best.confidence >= 0.96) &&
				((second < 0.0) || ((best.confidence - second) >= 0.02));
		return new WaterAnchorMatch(best.origin, best.grids, best.confidence,
				second, automatic);
	}

	private WaterPattern waterPattern(String gridName) throws IOException {
		WaterPattern cached = waterPatterns.get(gridName);
		if (cached != null)
			return cached;
		InputStream input = null;
		try {
			input = openTile(gridName);
			BufferedImage image = ImageIO.read(input);
			if (image == null)
				return null;
			WaterPattern pattern = WaterPattern.from(image);
			waterPatterns.put(gridName, pattern);
			return pattern;
		} catch (FileNotFoundException e) {
			return null;
		} finally {
			if (input != null)
				input.close();
		}
	}

	private DetailedPattern detailedPattern(String gridName) throws IOException {
		DetailedPattern cached = detailedPatterns.get(gridName);
		if (cached != null)
			return cached;
		InputStream input = null;
		try {
			input = openTile(gridName);
			BufferedImage image = ImageIO.read(input);
			if (image == null)
				return null;
			DetailedPattern pattern = DetailedPattern.from(image);
			detailedPatterns.put(gridName, pattern);
			return pattern;
		} catch (FileNotFoundException e) {
			return null;
		} finally {
			if (input != null)
				input.close();
		}
	}

	private void load() throws IOException {
		if (loaded)
			return;
		mappings.clear();
		components.clear();
		File source = recoverableIndexFile();
		if (source != null)
			loadIndex(source);
		loadMetadata();
		loaded = true;
		dirty = false;
	}

	/* A rename can be interrupted after index.conf has moved to .bak or before
	 * index.conf.new has been installed. Prefer the installed file, but never
	 * silently interpret a recoverable index as an empty atlas. */
	private File recoverableFile(File file) {
		if (file.exists())
			return file;
		File backup = new File(file.getPath() + ".bak");
		if (backup.exists())
			return backup;
		File temporary = new File(file.getPath() + ".new");
		return temporary.exists() ? temporary : null;
	}

	private File recoverableIndexFile() throws IOException {
		/* A complete installed index is authoritative, even if an interrupted
		 * earlier write left a larger stale .bak beside it. Recovery files are
		 * considered only when index.conf is absent or invalid. */
		if (indexFile.exists() && (validIndex(indexFile) >= 0))
			return indexFile;
		File[] candidates = { new File(indexFile.getPath() + ".new"),
				new File(indexFile.getPath() + ".bak") };
		File best = null;
		int bestRecords = -1;
		for (File candidate : candidates) {
			if (!candidate.exists())
				continue;
			int records = validIndex(candidate);
			/* On ties retain recovery order: complete .new before .bak. A
			 * header-only partial file never beats a recoverable record set. */
			if (records > bestRecords) {
				best = candidate;
				bestRecords = records;
			}
		}
		return best;
	}

	/** Returns every validated record count, or -1 when any record is invalid. */
	private int validIndex(File candidate) throws IOException {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(
					candidate), "UTF-8"));
			String line;
			int records = 0;
			boolean header = false;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0)
					continue;
				if ("# Solaris persistent minimap atlas v1".equals(line)) {
					header = true;
					continue;
				}
				if (line.startsWith("#"))
					continue;
				String[] values = line.split("\\|", 3);
				if (values.length != 3)
					return -1;
				try {
					Integer.parseInt(values[0]);
					Integer.parseInt(values[1]);
					if (URLDecoder.decode(values[2], "UTF-8").length() == 0)
						return -1;
					records++;
				} catch (Exception e) {
					return -1;
				}
			}
			/* An empty first-save index is valid only when it carries the exact
			 * format header written by saveIndex(); comments/zero bytes are not. */
			return header ? records : -1;
		} finally {
			if (reader != null)
				reader.close();
		}
	}

	private void loadIndex(File source) throws IOException {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(
					source), "UTF-8"));
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
	}

	private void loadMetadata() throws IOException {
		File source = recoverableFile(metadataFile);
		if (source == null)
			return;
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(
					source), "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				String[] values = line.trim().split("\\|", 5);
				if ((values.length != 5) || !"component".equals(values[0]))
					continue;
				try {
					String gridName = URLDecoder.decode(values[1], "UTF-8");
					Coord origin = new Coord(Integer.parseInt(values[2]),
							Integer.parseInt(values[3]));
					String layer = URLDecoder.decode(values[4], "UTF-8");
					if (gridName.length() > 0)
						components.put(gridName, new Component(gridName, origin, layer));
				} catch (Exception e) {
				}
			}
		} finally {
			if (reader != null)
				reader.close();
		}
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
		installAtomically(indexFile, temporary);
	}

	private void saveMetadata(Map<String, Component> source) throws IOException {
		ensureDirectory(root);
		File temporary = new File(metadataFile.getPath() + ".new");
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(temporary), "UTF-8"));
			writer.write("# Solaris persistent minimap component metadata v2");
			writer.newLine();
			for (Component component : source.values()) {
				writer.write("component|");
				writer.write(URLEncoder.encode(component.gridName, "UTF-8"));
				writer.write('|');
				writer.write(Integer.toString(component.origin.x));
				writer.write('|');
				writer.write(Integer.toString(component.origin.y));
				writer.write('|');
				writer.write(URLEncoder.encode(component.layer, "UTF-8"));
				writer.newLine();
			}
		} finally {
			if (writer != null)
				writer.close();
		}
		installAtomically(metadataFile, temporary);
	}

	private void installAtomically(File destination, File temporary)
			throws IOException {
		File backup = new File(destination.getPath() + ".bak");
		if (backup.exists() && !backup.delete()) {
			temporary.delete();
			throw new IOException("Could not replace minimap metadata backup");
		}
		boolean hadDestination = destination.exists();
		if (hadDestination && !destination.renameTo(backup)) {
			temporary.delete();
			throw new IOException("Could not back up persistent minimap data");
		}
		if (temporary.renameTo(destination)) {
			if (backup.exists())
				backup.delete();
		} else {
			if (hadDestination)
				backup.renameTo(destination);
			throw new IOException("Could not install persistent minimap data");
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
