/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import static haven.MCache.cmaps;
import static haven.MCache.tileSize;
import haven.INIFile.Pair;
import haven.MCache.Grid;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

import javax.imageio.ImageIO;

import union.KerriUtils;

public class MiniMap extends Widget {
	static Map<String, Tex> grids = new WeakHashMap<String, Tex>();
	static Set<String> loading = new HashSet<String>();
	/* A failed PNG must not become a permanent null entry in grids: detailed
	 * persistent-map recovery keeps asking for the same nine textures after a
	 * reconnect. Keep the retry state separately so a temporary map-server
	 * failure recovers, without requesting a missing resource every frame. */
	static Map<String, TileLoadFailure> gridFailures =
			new HashMap<String, TileLoadFailure>();
	static Loader loader = new Loader();
	static volatile Coord mappingStartPoint = null;
	static long mappingSession = 0;
	/* Quality-survey anchors are resolved against the current session origin.
	 * Consumers cache their projected coordinates, so make every change of that
	 * origin (including a new unresolved session) observable. */
	private static volatile long mappingRevision = 0;
	static Map<String, Coord> gridsHashes = java.util.Collections
			.synchronizedMap(new TreeMap<String, Coord>());
	static Map<Coord, String> coordHashes = java.util.Collections
			.synchronizedMap(new TreeMap<Coord, String>());
	static Map<Coord, Tex> caveTex = new TreeMap<Coord, Tex>();
	private static final CaveMapStore recordedCaves =
			new CaveMapStore(new File("map/persistent/caves"));
	/* Avoid repeatedly encoding the same live cave grid from the draw loop. */
	private static final Set<String> recordedCaveTiles =
			java.util.Collections.synchronizedSet(new HashSet<String>());
	/* Persistence conflicts are encountered from the render loop until the
	 * conflicting tile is resolved. Keep the first diagnostic, but do not print
	 * the same coordinate/hash pair every frame. */
	private static final Set<String> reportedPersistenceConflicts =
			java.util.Collections.synchronizedSet(new HashSet<String>());
	private static final PersistentMapStore persistentMap =
			new PersistentMapStore(new File("map/persistent"));
	private static final QualitySurveyStore qualitySurvey =
			new QualitySurveyStore(new File("map/persistent"));
	private static volatile boolean awaitingPersistentAnchor = false;
	private static volatile String persistentAnchorStatus = "Map position unresolved";
	/** A plausible water-pattern origin which was deliberately not applied
	 * automatically. Accepting it changes the current coordinate transform; a
	 * complete accepted match may also refresh the corresponding saved tiles. */
	public static final class PersistentAnchorSuggestion {
		public final Coord origin;
		public final int matchedGrids;
		public final double confidence;
		public final double secondConfidence;
		public final boolean ambiguous;
		public final boolean lowConfidence;
		private PersistentAnchorSuggestion(PersistentMapStore.WaterAnchorMatch match) {
			origin = new Coord(match.origin);
			matchedGrids = match.grids;
			confidence = match.confidence;
			secondConfidence = match.secondConfidence;
			ambiguous = (secondConfidence >= 0.0) &&
					((confidence - secondConfidence) < 0.02);
			lowConfidence = confidence < 0.96;
		}
		private PersistentAnchorSuggestion(PersistentMapStore.DetailedAnchorMatch match) {
			origin = new Coord(match.origin);
			matchedGrids = match.grids;
			confidence = match.confidence;
			secondConfidence = match.secondConfidence;
			ambiguous = (secondConfidence >= 0.0) &&
					((confidence - secondConfidence) < 0.02);
			lowConfidence = confidence < 0.90;
		}
	}
	private static volatile PersistentAnchorSuggestion persistentAnchorSuggestion = null;
	private static volatile Coord dismissedSuggestionOrigin = null;
	/* A complete accepted detailed match authorizes replacing refreshed tile
	 * identities only within the matched session-local 3x3 neighbourhood. */
	private static volatile Coord detailedReplacementCenter = null;
	/* A reconnect gives the client only session-local grid coordinates. Keep a
	 * bounded, invisible surface-water observation buffer until it can safely be
	 * matched to the saved atlas. These observations are never persisted. */
	private static final int UNANCHORED_WATER_BUFFER_LIMIT = 64;
	private static final TreeMap<Coord, PersistentMapStore.WaterPattern>
			unanchoredWaterPatterns = new TreeMap<Coord, PersistentMapStore.WaterPattern>();
	/* Matching the temporary sample against the saved atlas is intentionally
	 * incremental. resolvePersistentAnchor() runs from the render loop, so do
	 * not rescan every saved PNG on every frame when the observed 3x3 has not
	 * changed. */
	private static int unanchoredWaterPatternRevision = 0;
	private static int lastWaterAnchorMatchRevision = -1;
	/* Detailed 3x3 samples use the same PNG-backed image as the minimap.
	 * The expensive saved-atlas comparison is done once per changed observation
	 * in a worker, never in MiniMap.draw(). */
	private static final TreeMap<Coord, PersistentMapStore.DetailedPattern>
			unanchoredDetailedPatterns = new TreeMap<Coord, PersistentMapStore.DetailedPattern>();
	private static final int UNANCHORED_DETAILED_BUFFER_LIMIT = 64;
	private static int unanchoredDetailedPatternRevision = 0;
	private static int lastLoggedDetailedPatternRevision = -1;
	private static int lastDetailedAnchorMatchRevision = -1;
	private static volatile Coord lastDetailedAnchorMatchCenter = null;
	private static volatile PersistentMapStore.DetailedAnchorMatch detailedAnchorResult = null;
	private static volatile int detailedAnchorResultRevision = -1;
	private static volatile Coord detailedAnchorResultCenter = null;
	private static volatile boolean detailedAnchorWorkerRunning = false;
	/* Session-only cave entrance context. Cave grids are never assigned an
	 * inferred durable world coordinate. */
	private static volatile MapAnchor exteriorContextAnchor = null;
	private static volatile Coord exteriorContextTile = null;
	private static volatile Coord interiorEntranceTile = null;
	/* The exterior tile is copied at the actual outside-to-inside transition.
	 * It is cleared on cave-to-cave transitions so an old entrance can never be
	 * silently used to place another underground area on the surface atlas. */
	private static volatile Coord recordedCaveEntranceTile = null;
	/* Set when a cave transition is observed before the player gob has supplied
	 * a usable tile. It keeps capture retryable without persisting any link. */
	private static volatile boolean interiorEntrancePending = false;
	/* Only the primary minimap may establish interior state. The world-map
	 * canvas is another MiniMap instance and must never alter session state. */
	private static volatile boolean primaryInterior = false;
	public static final Tex bg = Resource.loadtex("gfx/hud/mmap/ptex");
	public static final Tex nomap = Resource.loadtex("gfx/hud/mmap/nomap");
	public static final Resource plx = Resource.load("gfx/hud/mmap/x");
	public Coord off = new Coord(0, 0), doff = new Coord(0, 0);
	boolean hidden = false, grid = false;;
	MapView mv;
	boolean dm = false;
	private final boolean primary;
	public int scale = 4;
	double scales[] = { 0.5, 0.66, 0.8, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2 };

	/** A tile position bound to the stable identity of a saved map PNG. */
	public static final class MapAnchor {
		public final String gridName;
		public final Coord offset;

		public MapAnchor(String gridName, Coord offset) {
			this.gridName = gridName;
			this.offset = new Coord(offset);
		}
	}

	/* New minimap functions */
	public Set<Pair<Coord, Color>> profits = new HashSet<Pair<Coord, Color>>();
	public Set<Pair<Coord, String>> hherbs = new HashSet<Pair<Coord, String>>();
	public Set<Pair<Coord, Color>> players = new HashSet<Pair<Coord, Color>>();

	/* End of new functions */

	public double getScale() {
		return scales[scale];
	}

	public void setScale(int scale) {
		this.scale = Math.max(0, Math.min(scale, scales.length - 1));
	}

	private static Coord persistentMappingOrigin() {
		Coord origin = mappingStartPoint;
		if (!Config.autoSaveMinimaps || awaitingPersistentAnchor ||
				(origin == null))
			return null;
		return new Coord(origin);
	}

	public static String persistentAnchorStatus() {
		return persistentAnchorStatus;
	}
	public static PersistentAnchorSuggestion persistentAnchorSuggestion() {
		return persistentAnchorSuggestion;
	}
	public static boolean acceptPersistentAnchorSuggestion() {
		PersistentAnchorSuggestion suggestion = persistentAnchorSuggestion;
		if (!Config.autoSaveMinimaps || !awaitingPersistentAnchor ||
				primaryInterior || (suggestion == null))
			return false;
		mappingStartPoint = new Coord(suggestion.origin);
		awaitingPersistentAnchor = false;
		persistentAnchorSuggestion = null;
		dismissedSuggestionOrigin = null;
		detailedReplacementCenter = (detailedAnchorResultCenter == null) ? null
				: new Coord(detailedAnchorResultCenter);
		changedMappingAnchor();
		persistentAnchorStatus = "Persistent map anchored from accepted detailed-map suggestion";
		System.out.println("[PersistentMap] accepted anchor suggestion: origin=" +
				suggestion.origin + ", score=" +
				String.format(java.util.Locale.US, "%.2f%%", suggestion.confidence * 100.0) +
				", compared=" + suggestion.matchedGrids + " tiles");
		return true;
	}
	public static void dismissPersistentAnchorSuggestion() {
		PersistentAnchorSuggestion suggestion = persistentAnchorSuggestion;
		if (suggestion != null)
			dismissedSuggestionOrigin = new Coord(suggestion.origin);
		if (suggestion != null)
			System.out.println("[PersistentMap] dismissed anchor suggestion: origin=" +
					suggestion.origin + ", score=" +
					String.format(java.util.Locale.US, "%.2f%%", suggestion.confidence * 100.0));
		persistentAnchorSuggestion = null;
		if (awaitingPersistentAnchor)
			persistentAnchorStatus = "Suggested map location dismissed; continuing to explore";
	}
	public static long mappingRevision() { return mappingRevision; }
	private static void changedMappingAnchor() { mappingRevision++; }
	public static boolean isPrimaryInterior() { return primaryInterior; }
	public static void armQualitySurvey(MCache map, Coord tile, String type) { qualitySurvey.arm(anchorForSessionTile(map, tile), type); }
	public static void clearQualitySurveyPending() { qualitySurvey.clearPending(); }
	public static long observeQualitySurvey(Item item) { return qualitySurvey.observe(item); }
	public static void noteQualitySurveyItemCreated(Item item) { qualitySurvey.noteItemCreated(item); }
	public static void cancelQualitySurveyForInventoryMove(Item item) { qualitySurvey.cancelForInventoryMove(item); }

	/** Returns the last confirmed exterior atlas tile from this session. */
	public static Coord exteriorContextTile() {
		Coord tile = exteriorContextTile;
		return (tile == null) ? null : new Coord(tile);
	}

	/** Session-local cave tile occupied when the current interior was entered. */
	public static Coord interiorEntranceTile() {
		Coord tile = interiorEntranceTile;
		return (tile == null) ? null : new Coord(tile);
	}

	/** Projects a persistent-atlas tile into the current surface session. */
	static Coord sessionTileForPersistentTile(Coord persistentTile) {
		Coord origin = persistentMappingOrigin();
		return ((origin == null) || (persistentTile == null)) ? null
				: persistentTile.add(origin.mul(cmaps));
	}

	static void drawRecordedCaves(GOut g, Coord sessionCenter, Coord halfSize) {
		if (Config.showRecordedCaveOverlay && !primaryInterior)
			recordedCaves.draw(g, sessionCenter, halfSize,
					Config.recordedCaveOverlayOpacity);
	}

	private static void rememberExteriorAnchor(MapAnchor anchor) {
		if (anchor == null)
			return;
		Coord tile = persistentTileForAnchor(anchor.gridName, anchor.offset);
		if (tile == null)
			return;
		exteriorContextAnchor = new MapAnchor(anchor.gridName, anchor.offset);
		exteriorContextTile = new Coord(tile);
	}

	/**
	 * Binds a session-local tile to the grid name used by the persistent map
	 * PNG. Session coordinates change after reconnecting; the grid name and the
	 * offset inside that grid do not.
	 */
	public static MapAnchor anchorForSessionTile(MCache map, Coord sessionTile) {
		if ((map == null) || (sessionTile == null))
			return null;
		if (primaryInterior)
			return null;
		Coord origin = persistentMappingOrigin();
		if (origin == null)
			return null;
		Coord sessionGrid = sessionTile.div(cmaps);
		Coord persistentGrid = sessionGrid.sub(origin);
		String gridName = coordHashes.get(persistentGrid);
		if (gridName == null) {
			synchronized (map.req) {
				synchronized (map.grids) {
					Grid grid = map.grids.get(sessionGrid);
					if (grid != null)
						gridName = grid.mnm;
				}
			}
			Coord saved = (gridName == null) ? null : gridsHashes.get(gridName);
			if ((saved == null) || !saved.equals(persistentGrid))
				return null;
		}
		return new MapAnchor(gridName, sessionTile.mod(cmaps));
	}

	/** Returns this PNG-bound position in the persistent atlas coordinate space. */
	public static Coord persistentTileForAnchor(String gridName, Coord offset) {
		if ((gridName == null) || (offset == null))
			return null;
		Coord persistentGrid = gridsHashes.get(gridName);
		if (persistentGrid == null)
			return null;
		return persistentGrid.mul(cmaps).add(offset.mod(cmaps));
	}

	/** Converts a PNG-bound position back to the current session coordinates. */
	public static Coord sessionTileForAnchor(String gridName, Coord offset) {
		Coord origin = persistentMappingOrigin();
		Coord persistentGrid = (gridName == null) ? null : gridsHashes
				.get(gridName);
		if ((origin == null) || (persistentGrid == null) || (offset == null))
			return null;
		return persistentGrid.add(origin).mul(cmaps).add(offset.mod(cmaps));
	}

	static class TileLoadFailure {
		private static final long TRANSIENT_RETRY_INITIAL_MS = 2000;
		private static final long TRANSIENT_RETRY_MAX_MS = 60000;
		private static final long MISSING_RETRY_INITIAL_MS = 60000;
		private static final long MISSING_RETRY_MAX_MS = 600000;
		private static final long LOG_INTERVAL_MS = 60000;
		private int attempts = 0;
		private long nextRetryAt = 0;
		private long lastLoggedAt = 0;

		boolean eligible(long now) {
			return now >= nextRetryAt;
		}

		void failed(long now, boolean missing) {
			attempts++;
			long initial = missing ? MISSING_RETRY_INITIAL_MS :
					TRANSIENT_RETRY_INITIAL_MS;
			long maximum = missing ? MISSING_RETRY_MAX_MS :
					TRANSIENT_RETRY_MAX_MS;
			long delay = initial;
			for (int i = 1; (i < attempts) && (delay < maximum); i++)
				delay = Math.min(maximum, delay * 2);
			nextRetryAt = now + delay;
		}

		boolean shouldLog(long now) {
			if ((lastLoggedAt == 0) || ((now - lastLoggedAt) >= LOG_INTERVAL_MS)) {
				lastLoggedAt = now;
				return true;
			}
			return false;
		}
	}

	static class Loader implements Runnable {
		Thread me = null;

		private URL gridUrl(String nm) throws IOException {
			return new URL(Config.mapurl, nm + ".png");
		}

		private InputStream getreal(String nm) throws IOException {
			URL url = gridUrl(nm);
			URLConnection c = url.openConnection();
			c.addRequestProperty("User-Agent", "Haven/1.0");
			InputStream s = c.getInputStream();
			return (s);
		}

		private InputStream getcached(String nm) throws IOException {
			if (Config.autoSaveMinimaps) {
				try {
					return persistentMap.openTile(nm);
				} catch (FileNotFoundException e) {
				}
			}
			if (mappingSession > 0) {
				String fileName;
				if (gridsHashes.containsKey(nm)) {
					Coord coordinates = gridsHashes.get(nm);
					fileName = "tile_" + coordinates.x + "_" + coordinates.y;
				} else {
					fileName = nm;
				}

				File inputfile = new File("map/"
						+ Utils.sessdate(mappingSession) + "/" + fileName
						+ ".png");
				if (!inputfile.exists())
					throw (new FileNotFoundException("Minimap cache not found"));
				return new FileInputStream(inputfile);
			}
			throw (new FileNotFoundException("No resource cache installed"));
		}

		public void run() {
			try {
				while (true) {
					String grid;
					synchronized (grids) {
						grid = null;
						for (String cg : loading) {
							grid = cg;
							break;
						}
					}
					if (grid == null)
						break;
					try {
						boolean cached;
						boolean replacePersistentTile = false;
						BufferedImage img;
						try {
							img = readImage(getcached(grid), grid);
							cached = true;
						} catch (IOException e) {
							img = readImage(getreal(grid), grid);
							cached = false;
							replacePersistentTile = true;
						}
						if ((!cached) & (mappingSession > 0) && Config.autoSaveMinimaps) {
							try {
								if (replacePersistentTile)
									persistentMap.replaceTile(grid, img);
								else
									persistentMap.saveTile(grid, img);
								saveSessionTile(grid, img);
							} catch (IOException ex) {
								System.out.println("Could not save minimap tile " +
										grid + ": " + ex);
							}
						}
						Tex tex = new TexI(img);
						synchronized (grids) {
							grids.put(grid, tex);
							gridFailures.remove(grid);
							loading.remove(grid);
						}
					} catch (IOException e) {
						synchronized (grids) {
							/* A missing network tile is generally permanent, while all
							 * other I/O failures can follow a server restart. Both are
							 * retried, but the former is deliberately much slower. */
							TileLoadFailure failure = gridFailures.get(grid);
							if (failure == null) {
								failure = new TileLoadFailure();
								gridFailures.put(grid, failure);
							}
							long now = System.currentTimeMillis();
							failure.failed(now, e instanceof FileNotFoundException);
							grids.remove(grid);
							loading.remove(grid);
							if (failure.shouldLog(now)) {
								String url;
								try {
									url = gridUrl(grid).toString();
								} catch (IOException ignored) {
									url = String.valueOf(Config.mapurl) + grid + ".png";
								}
								System.out.println("[MiniMap] could not load tile " +
										url + ": " + e + "; retrying with backoff");
							}
						}
					}
				}
			} finally {
				synchronized (this) {
					me = null;
				}
			}
		}

		void start() {
			synchronized (this) {
				if (me == null) {
					me = new HackThread(this, "Minimap loader");
					me.setDaemon(true);
					me.start();
				}
			}
		}

		void req(String nm) {
			synchronized (grids) {
				if (loading.contains(nm))
					return;
				TileLoadFailure failure = gridFailures.get(nm);
				if ((failure != null) && !failure.eligible(System.currentTimeMillis()))
					return;
				loading.add(nm);
				start();
			}
		}
	}

	public static void newMappingSession() {
		qualitySurvey.clearPending();
		changedMappingAnchor();
		long newSession = System.currentTimeMillis();
		String date = Utils.sessdate(newSession);
		mappingSession = newSession;
		mappingStartPoint = null;
		persistentAnchorSuggestion = null;
		dismissedSuggestionOrigin = null;
		gridsHashes.clear();
		coordHashes.clear();
		synchronized (grids) {
			gridFailures.clear();
		}
		reportedPersistenceConflicts.clear();
		synchronized (unanchoredWaterPatterns) {
			unanchoredWaterPatterns.clear();
		}
		synchronized (unanchoredDetailedPatterns) {
			unanchoredDetailedPatterns.clear();
		}
		unanchoredWaterPatternRevision = 0;
		lastWaterAnchorMatchRevision = -1;
		unanchoredDetailedPatternRevision = 0;
		lastLoggedDetailedPatternRevision = -1;
		lastDetailedAnchorMatchRevision = -1;
		lastDetailedAnchorMatchCenter = null;
		detailedAnchorResult = null;
		detailedAnchorResultRevision = -1;
		detailedAnchorResultCenter = null;
		detailedAnchorWorkerRunning = false;
		synchronized (caveTex) {
			caveTex.clear();
		}
		recordedCaveTiles.clear();
		recordedCaves.reload();
		awaitingPersistentAnchor = false;
		persistentAnchorStatus = "Map position unresolved";
		exteriorContextAnchor = null;
		exteriorContextTile = null;
		interiorEntranceTile = null;
		recordedCaveEntranceTile = null;
		interiorEntrancePending = false;
		primaryInterior = false;
		detailedReplacementCenter = null;
		if (!Config.autoSaveMinimaps)
			return;
		try {
			prepareSessionDirectory(date);
			persistentMap.loadInto(gridsHashes, coordHashes);
			/* Complete any survey identity migration left behind by a crash. The
			 * journal is replayed only when the atlas already contains the new hash
			 * and no longer contains the old one, so a pre-replacement crash is safe
			 * to retry on the next confirmed replacement. */
			qualitySurvey.replayPendingMigrations(gridsHashes);
			System.out.println("[PersistentMap] started mapping session " + date +
					": atlas records=" + gridsHashes.size());
			/* Always wait for a loaded grid identity. A session-local coordinate is
			 * never a durable anchor, even for an empty persistent atlas. */
			awaitingPersistentAnchor = true;
		} catch (IOException ex) {
			System.out.println("Could not initialize persistent minimap: " + ex);
		}
	}

	public static void enableMapSaving() {
		if (!Config.autoSaveMinimaps)
			return;
		newMappingSession();
		saveLoadedTiles();
	}

	private static void prepareSessionDirectory(String date) throws IOException {
		File directory = new File("map", date);
		PersistentMapStore.ensureDirectory(directory);
		Writer writer = null;
		try {
			writer = new FileWriter(new File("map", "currentsession.js"));
			writer.write("var currentSession = '" + date + "';\n");
		} finally {
			if (writer != null)
				writer.close();
		}
	}

	private static void saveSessionTile(String gridName, BufferedImage image)
			throws IOException {
		String date = Utils.sessdate(mappingSession);
		File directory = new File("map", date);
		PersistentMapStore.ensureDirectory(directory);
		String fileName;
		Coord coordinate = gridsHashes.get(gridName);
		if (coordinate == null) {
			fileName = URLEncoder.encode(gridName, "UTF-8");
		} else {
			fileName = "tile_" + coordinate.x + "_" + coordinate.y;
		}
		if (!ImageIO.write(image, "png", new File(directory, fileName + ".png")))
			throw new IOException("No PNG writer is available");
	}

	private static void saveLoadedTiles() {
		synchronized (grids) {
			for (Map.Entry<String, Tex> entry : grids.entrySet()) {
				if (entry.getValue() instanceof TexI) {
					try {
						persistentMap.saveTile(entry.getKey(),
								((TexI) entry.getValue()).back);
					} catch (IOException ex) {
						System.out.println("Could not save minimap tile " +
								entry.getKey() + ": " + ex);
					}
				}
			}
		}
	}

	private static BufferedImage readImage(InputStream input, String grid)
			throws IOException {
		try {
			BufferedImage image = ImageIO.read(input);
			if (image == null)
				throw new IOException("Invalid minimap image " + grid);
			return image;
		} finally {
			Utils.readtileof(input);
			input.close();
		}
	}

	public MiniMap(Coord c, Coord sz, Widget parent, MapView mv) {
		this(c, sz, parent, mv, true);
	}

	protected MiniMap(Coord c, Coord sz, Widget parent, MapView mv,
			boolean primary) {
		super(c, sz, parent);
		this.mv = mv;
		this.primary = primary;
		off = new Coord();
		if (primary) {
			newMappingSession();
			ui.minimap = this;
		}
	}
	
	public void unlink(){
		if (primary && (ui.minimap == this))
			ui.minimap = null;
		super.unlink();
	}

	public static Tex getgrid(final String nm) {
		return (AccessController.doPrivileged(new PrivilegedAction<Tex>() {
			public Tex run() {
				synchronized (grids) {
					Tex grid = grids.get(nm);
					if (grid != null)
						return grid;
					loader.req(nm);
					return null;
				}
			}
		}));
	}

	public Coord xlate(Coord c, boolean in) {
		if (in) {
			return c.div(getScale());
		} else {
			return c.mul(getScale());
		}
	}
	
	protected Coord viewCenter() {
		return mv.mc.div(tileSize).add(off.div(getScale()));
	}

	protected Coord localToTile(Coord lc) {
		Coord hsz = sz.div(getScale());
		return viewCenter().add(lc.div(getScale())).sub(hsz.div(2));
	}

	protected Coord tileToLocal(Coord tc) {
		Coord hsz = sz.div(getScale());
		return tc.sub(viewCenter()).add(hsz.div(2)).mul(getScale());
	}

	public void centerOnTile(Coord tc) {
		off = tc.sub(mv.mc.div(tileSize)).mul(getScale());
	}

	private Coord localToReal(Coord lc) {
		Gob pl = ui.sess.glob.oc.getgob(mv.playergob);
		if (pl == null) return Coord.z;
		// MAGIC!~
		return pl.rc.add(lc.sub(pl.rc.div(tileSize).add(mv.mc.div(tileSize).add(off.div(scales[scale])).inv()).add(sz.div(scales[scale]).div(2))).mul(tileSize));
	}

	private void resolvePersistentAnchor(Coord centerGrid) {
		if (!Config.autoSaveMinimaps || !awaitingPersistentAnchor)
			return;
		Grid newComponentGrid = null;
		Coord newComponentCoordinate = null;
		synchronized (ui.sess.glob.map.req) {
			synchronized (ui.sess.glob.map.grids) {
				for (int radius = 0; radius <= 2; radius++) {
					for (int y = -radius; y <= radius; y++) {
						for (int x = -radius; x <= radius; x++) {
							Coord gridCoordinate = centerGrid.add(x, y);
							Grid candidate = ui.sess.glob.map.grids
									.get(gridCoordinate);
							if ((candidate == null) || (candidate.mnm == null))
								continue;
							if ((x == 0) && (y == 0) &&
									(newComponentGrid == null)) {
								newComponentGrid = candidate;
								newComponentCoordinate = gridCoordinate;
							}
						}
					}
				}
			}
		}
		/* Grid names can change between sessions. Before refusing an existing
		 * atlas, compare the visible detailed 3x3 PNG neighbourhood with every
		 * saved centre tile and its saved neighbours. */
		if (!gridsHashes.isEmpty()) {
			Map<Coord, PersistentMapStore.DetailedPattern> observed =
					collectUnanchoredDetailedPatterns(centerGrid);
			if (lastLoggedDetailedPatternRevision != unanchoredDetailedPatternRevision) {
				lastLoggedDetailedPatternRevision = unanchoredDetailedPatternRevision;
				System.out.println("[PersistentMap] current detailed map neighbourhood: " +
						observed.size() + "/9 tiles loaded, center=" + centerGrid);
			}
			if (observed.size() == 9) {
				startDetailedAnchorMatch(centerGrid, observed);
				applyDetailedAnchorMatchIfReady(centerGrid);
				if (detailedAnchorWorkerRunning)
					persistentAnchorStatus = "Comparing detailed 9/9 map neighbourhood with saved atlas (" +
						persistentMap.detailedScanProgress() + "%)";
			} else {
				persistentAnchorStatus = "Loading complete detailed map neighbourhood for recovery (" +
						observed.size() + "/9)";
			}
			return;
		}
		/* An empty atlas has no earlier identity to match. Its first loaded
		 * server grid is a confirmed new component; existing atlases stay
		 * unresolved until a saved grid identity is seen. */
		if ((newComponentGrid == null) || !gridsHashes.isEmpty()) {
			persistentAnchorStatus = (newComponentGrid == null)
					? "Waiting for a saved map tile to load"
					: "Saved map tile not found; confirm Add map area for a disconnected atlas.";
			return;
		}
		try {
			Coord origin = persistentMap.nextComponentOrigin();
			persistentMap.recordComponent(newComponentGrid.mnm, origin, "surface");
			mappingStartPoint = newComponentCoordinate.sub(origin);
			awaitingPersistentAnchor = false;
			changedMappingAnchor();
			persistentAnchorStatus = "New persistent map component anchored";
		} catch (IOException ex) {
			persistentAnchorStatus = "Could not create persistent map component";
			System.out.println("Could not place a new minimap atlas section: " +
					ex);
		}
	}

	/** Captures the current visible 3x3 from the exact PNG-backed TexI that is
	 * rendered by the minimap. Missing textures are requested by getgrid() and
	 * are picked up on a later frame; each coordinate is converted once. */
	private Map<Coord, PersistentMapStore.DetailedPattern>
			collectUnanchoredDetailedPatterns(Coord centerGrid) {
		synchronized (ui.sess.glob.map.req) {
			synchronized (ui.sess.glob.map.grids) {
				for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
					Coord coordinate = centerGrid.add(x, y);
					Grid candidate = ui.sess.glob.map.grids.get(coordinate);
					if ((candidate == null) || (candidate.mnm == null))
						continue;
					Tex texture = getgrid(candidate.mnm);
					if (!(texture instanceof TexI) || ((TexI)texture).back == null)
						continue;
					synchronized (unanchoredDetailedPatterns) {
						if (!unanchoredDetailedPatterns.containsKey(coordinate)) {
							PersistentMapStore.DetailedPattern pattern =
									PersistentMapStore.DetailedPattern.from(((TexI)texture).back);
							if (pattern == null)
								continue;
							unanchoredDetailedPatterns.put(new Coord(coordinate), pattern);
							unanchoredDetailedPatternRevision++;
							while (unanchoredDetailedPatterns.size() >
									UNANCHORED_DETAILED_BUFFER_LIMIT) {
								java.util.Iterator<Coord> iterator =
										unanchoredDetailedPatterns.keySet().iterator();
								iterator.next();
								iterator.remove();
							}
						}
					}
				}
			}
		}
		synchronized (unanchoredDetailedPatterns) {
			Map<Coord, PersistentMapStore.DetailedPattern> current =
					new TreeMap<Coord, PersistentMapStore.DetailedPattern>();
			for (Map.Entry<Coord, PersistentMapStore.DetailedPattern> entry :
					unanchoredDetailedPatterns.entrySet()) {
				Coord coordinate = entry.getKey();
				if ((Math.abs(coordinate.x - centerGrid.x) <= 1) &&
						(Math.abs(coordinate.y - centerGrid.y) <= 1))
					current.put(new Coord(coordinate), entry.getValue());
			}
			return current;
		}
	}

	private void startDetailedAnchorMatch(final Coord centerGrid,
			final Map<Coord, PersistentMapStore.DetailedPattern> observed) {
		final int revision = unanchoredDetailedPatternRevision;
		if (((revision == lastDetailedAnchorMatchRevision) &&
				(lastDetailedAnchorMatchCenter != null) &&
				lastDetailedAnchorMatchCenter.equals(centerGrid)) || detailedAnchorWorkerRunning)
			return;
		lastDetailedAnchorMatchRevision = revision;
		lastDetailedAnchorMatchCenter = new Coord(centerGrid);
		detailedAnchorWorkerRunning = true;
		final long session = mappingSession;
		HackThread worker = new HackThread(new Runnable() {
			public void run() {
				try {
					PersistentMapStore.DetailedAnchorMatch match =
							persistentMap.findDetailedAnchor(centerGrid, observed);
					if ((mappingSession == session) && awaitingPersistentAnchor) {
						detailedAnchorResult = match;
						detailedAnchorResultRevision = revision;
						detailedAnchorResultCenter = new Coord(centerGrid);
						if (match == null)
							System.out.println("[PersistentMap] detailed recovery found no candidate for center=" + centerGrid);
					}
				} catch (Exception ex) {
					if ((mappingSession == session) && awaitingPersistentAnchor) {
						detailedAnchorResult = null;
						detailedAnchorResultRevision = revision;
						detailedAnchorResultCenter = new Coord(centerGrid);
						persistentAnchorStatus = "Could not compare saved detailed map tiles";
					}
					System.out.println("[PersistentMap] detailed recovery exception for center=" +
							centerGrid + ": " + ex);
				} finally {
					detailedAnchorWorkerRunning = false;
				}
			}
		}, "Persistent minimap matcher");
		worker.setDaemon(true);
		worker.start();
		persistentAnchorStatus = "Comparing detailed 9/9 map neighbourhood with saved atlas";
	}

	private void applyDetailedAnchorMatchIfReady(Coord centerGrid) {
		if ((detailedAnchorResultRevision != unanchoredDetailedPatternRevision) ||
				(lastDetailedAnchorMatchCenter == null) ||
			!lastDetailedAnchorMatchCenter.equals(detailedAnchorResultCenter) ||
				!lastDetailedAnchorMatchCenter.equals(centerGrid))
			return;
		PersistentMapStore.DetailedAnchorMatch match = detailedAnchorResult;
		if ((match != null) && (match.confidence >= 0.99) && (match.grids == 9)) {
			/* A near-identical detailed neighbourhood is sufficiently strong to
			 * merge without another click and authorize refreshed tiles in this
			 * matched 3x3 area. */
			mappingStartPoint = new Coord(match.origin);
			awaitingPersistentAnchor = false;
			persistentAnchorSuggestion = null;
			dismissedSuggestionOrigin = null;
			detailedReplacementCenter = new Coord(centerGrid);
			changedMappingAnchor();
			persistentAnchorStatus = "Persistent map anchored automatically from detailed match";
			System.out.println("[PersistentMap] automatically accepted detailed anchor: origin=" +
					match.origin + ", score=" +
					String.format(java.util.Locale.US, "%.2f%%", match.confidence * 100.0) +
					", compared=" + match.grids + " tiles");
			return;
		}
		if ((match != null) && (match.confidence >= 0.90) &&
				((dismissedSuggestionOrigin == null) ||
				!dismissedSuggestionOrigin.equals(match.origin))) {
			persistentAnchorSuggestion = new PersistentAnchorSuggestion(match);
			persistentAnchorStatus = "Detailed map match found; confirm merge in World Map";
		} else if ((match != null) && (dismissedSuggestionOrigin != null) &&
				dismissedSuggestionOrigin.equals(match.origin)) {
			persistentAnchorStatus = "Suggested map location dismissed; continuing to explore";
		} else if (match != null) {
			persistentAnchorStatus = "Best detailed map match below 90%; continuing to explore";
		} else if (!detailedAnchorWorkerRunning) {
			persistentAnchorStatus = "Detailed map neighbourhood did not match saved atlas";
		}
	}

	private boolean detailedReplacementApplies(Coord sessionGrid) {
		Coord center = detailedReplacementCenter;
		return (center != null) && (sessionGrid != null) &&
				(Math.abs(sessionGrid.x - center.x) <= 1) &&
				(Math.abs(sessionGrid.y - center.y) <= 1);
	}

	/**
	 * A changed tile hash may be a refreshed copy of an existing atlas tile.
	 * Require two independently known neighboring hashes to agree with the
	 * expected persistent coordinates before allowing replacement.
	 */
	private boolean confirmedPersistentReplacement(Coord sessionGrid,
			Coord persistentGrid) {
		if ((sessionGrid == null) || (persistentGrid == null) || (ui.sess == null))
			return false;
		int matches = 0;
		synchronized (ui.sess.glob.map.req) {
			synchronized (ui.sess.glob.map.grids) {
				for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
					if ((x == 0) && (y == 0))
						continue;
					Grid neighbor = ui.sess.glob.map.grids.get(sessionGrid.add(x, y));
					if ((neighbor == null) || (neighbor.mnm == null))
						continue;
					Coord known = gridsHashes.get(neighbor.mnm);
					if ((known != null) && known.equals(persistentGrid.add(x, y)))
						matches++;
				}
			}
		}
		return matches >= 2;
	}

	/** Adds loaded surface-water masks from the current 3x3 to an invisible,
	 * session-only buffer. A mask is enough for matching and avoids retaining
	 * map images or displaying an unverified atlas position. Land-only masks are
	 * retained as well, so the temporary explored area remains a real 3x3 sample
	 * until water becomes available. */
	private Map<Coord, PersistentMapStore.WaterPattern>
			collectUnanchoredWaterPatterns(Coord centerGrid) {
		synchronized (ui.sess.glob.map.req) {
			synchronized (ui.sess.glob.map.grids) {
				for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
					Coord coordinate = centerGrid.add(x, y);
					Grid candidate = ui.sess.glob.map.grids.get(coordinate);
					if ((candidate == null) || (candidate.mnm == null))
						continue;
					/* Grid.img is MCache's generated tile-colour raster. Persistent
					 * minimap tiles, however, are the downloaded PNGs rendered by this
					 * widget (and are what the user sees in the map). Comparing those
					 * two representations makes an identical coastline look different.
					 * Request/use the exact PNG-backed TexI instead; it may be absent for
					 * one or two frames while the loader fetches it. */
					Tex loaded = getgrid(candidate.mnm);
					if (!(loaded instanceof TexI) || ((TexI) loaded).back == null)
						continue;
					java.awt.image.BufferedImage image = ((TexI) loaded).back;
					synchronized (unanchoredWaterPatterns) {
						PersistentMapStore.WaterPattern pattern =
								unanchoredWaterPatterns.get(coordinate);
						if (pattern == null) {
							pattern = PersistentMapStore.WaterPattern.from(image);
							if (pattern == null)
								continue;
							unanchoredWaterPatterns.put(new Coord(coordinate), pattern);
							unanchoredWaterPatternRevision++;
						}
						while (unanchoredWaterPatterns.size() >
								UNANCHORED_WATER_BUFFER_LIMIT) {
							java.util.Iterator<Coord> iterator =
									unanchoredWaterPatterns.keySet().iterator();
							iterator.next();
							iterator.remove();
						}
					}
				}
			}
		}
		synchronized (unanchoredWaterPatterns) {
			return new TreeMap<Coord, PersistentMapStore.WaterPattern>(
					unanchoredWaterPatterns);
		}
	}

	/** True only when an explicitly confirmed disconnected component is safe. */
	public static boolean canCreatePersistentComponent(MCache map,
			Coord sessionTile) {
		if (!Config.autoSaveMinimaps || !awaitingPersistentAnchor ||
				primaryInterior || (map == null) || (sessionTile == null) ||
				gridsHashes.isEmpty())
			return false;
		Coord centerGrid = sessionTile.div(cmaps);
		synchronized (map.req) {
			synchronized (map.grids) {
				Grid center = map.grids.get(centerGrid);
				return (center != null) && (center.mnm != null) &&
						!gridsHashes.containsKey(center.mnm);
			}
		}
	}

	/** Creates a disconnected surface component after an explicit user action. */
	public static boolean createPersistentComponent(MCache map,
			Coord sessionTile) {
		if (!canCreatePersistentComponent(map, sessionTile))
			return false;
		Coord centerGrid = sessionTile.div(cmaps);
		Grid center;
		synchronized (map.req) {
			synchronized (map.grids) {
				center = map.grids.get(centerGrid);
			}
		}
		if ((center == null) || (center.mnm == null))
			return false;
		Coord previousStart = mappingStartPoint;
		boolean previousAwaiting = awaitingPersistentAnchor;
		try {
			Coord origin = persistentMap.nextComponentOrigin();
			persistentMap.recordComponent(center.mnm, origin, "surface");
			mappingStartPoint = centerGrid.sub(origin);
			awaitingPersistentAnchor = false;
			changedMappingAnchor();
			persistentAnchorStatus = "New disconnected map area anchored";
			return true;
		} catch (IOException ex) {
			mappingStartPoint = previousStart;
			awaitingPersistentAnchor = previousAwaiting;
			persistentAnchorStatus = "Could not create persistent map component";
			System.out.println("Could not place a disconnected minimap section: " + ex);
			return false;
		}
	}

	public void draw(GOut og) {
		double scale = getScale();
		Coord hsz = sz.div(scale);

		Coord tc = viewCenter();
		if (primary)
			resolvePersistentAnchor(tc.div(cmaps));
		Coord ulg = tc.div(cmaps);
		while ((ulg.x * cmaps.x) - tc.x + (hsz.x / 2) > 0)
			ulg.x--;
		while ((ulg.y * cmaps.y) - tc.y + (hsz.y / 2) > 0)
			ulg.y--;

		if (!hidden) {
			Coord s = bg.sz();
			for (int y = 0; (y * s.y) < sz.y; y++) {
				for (int x = 0; (x * s.x) < sz.x; x++) {
					og.image(bg, new Coord(x * s.x, y * s.y));
				}
			}
		}

		GOut g = og.reclip(og.ul.mul((1 - scale) / scale), hsz);
		g.gl.glPushMatrix();
		g.scale(scale);

		synchronized (caveTex) {
			/* MCache.trimall() clears this cache when the server replaces the
			 * current map. While already underground that is the reliable session
			 * signal for an interior-to-interior transition. */
			if (primary && primaryInterior && caveTex.isEmpty()) {
				interiorEntranceTile = null;
				interiorEntrancePending = true;
				recordedCaveEntranceTile = null;
				recordedCaveTiles.clear();
			}

			for (int y = ulg.y; (y * cmaps.y) - tc.y + (hsz.y / 2) < hsz.y; y++) {
				for (int x = ulg.x; (x * cmaps.x) - tc.x + (hsz.x / 2) < hsz.x; x++) {
					Coord cg = new Coord(x, y);
					Grid grid;
					synchronized (ui.sess.glob.map.req) {
						synchronized (ui.sess.glob.map.grids) {
							grid = ui.sess.glob.map.grids.get(cg);
							if ((grid == null) && primary)
								ui.sess.glob.map.request(cg);
						}
					}
					if (mappingStartPoint == null) {
						/* Preserve a local visual map while refusing to assign it an
						 * unverified persistent position. Cave detection must still run
						 * here, because a direct interior login has no atlas anchor. */
						Tex unresolved = null;
						if ((grid != null) && (grid.mnm != null))
							unresolved = getgrid(grid.mnm);
						/* The detailed PNG may still be loading. Keep the local generated
						 * raster as a temporary fallback, but never use it for matching. */
						if ((unresolved == null) && (grid != null))
							unresolved = grid.getTex();
						/* A loaded surface identity proves that any retained cave cache
						 * is stale (for example after leaving a direct-cave session). */
						if (primary && (grid != null) && (grid.mnm != null))
							caveTex.clear();
						if (primary && (grid != null) && (grid.mnm == null) &&
								(unresolved != null))
							caveTex.put(cg, unresolved);
						if ((unresolved != null) && !hidden)
							g.image(unresolved, cg.mul(cmaps).add(tc.inv())
									.add(hsz.div(2)));
						continue;
					}
					Coord relativeCoordinates = cg.sub(mappingStartPoint);
					String mnm = null;

					if (grid == null) {
						if (!awaitingPersistentAnchor)
							mnm = coordHashes.get(relativeCoordinates);
					} else {
						mnm = grid.mnm;
					}

					Tex tex = null;

					if (mnm != null) {
						if (primary)
							caveTex.clear();
						if (awaitingPersistentAnchor &&
								!gridsHashes.containsKey(mnm)) {
							tex = grid.getTex();
						} else if (!gridsHashes.containsKey(mnm)) {
							if (!Config.autoSaveMinimaps &&
									((Math.abs(relativeCoordinates.x) > 450)
									|| (Math.abs(relativeCoordinates.y) > 450))) {
								newMappingSession();
								mappingStartPoint = cg;
								relativeCoordinates = new Coord(0, 0);
							}
							String previousGrid = coordHashes.get(relativeCoordinates);
							boolean coordinateConflict = (previousGrid != null) &&
									!previousGrid.equals(mnm);
							if (coordinateConflict) {
				if (detailedReplacementApplies(cg) ||
						confirmedPersistentReplacement(cg, relativeCoordinates)) {
									Tex replacement = getgrid(mnm);
									if ((replacement instanceof TexI) &&
											((TexI) replacement).back != null) {
										try {
												qualitySurvey.prepareGridIdentityMigration(previousGrid, mnm);
											if (persistentMap.replaceAtConfirmedLocation(mnm,
													relativeCoordinates, ((TexI) replacement).back)) {
												coordHashes.put(relativeCoordinates, mnm);
																	gridsHashes.remove(previousGrid);
																	gridsHashes.put(mnm, relativeCoordinates);
																	try {
														qualitySurvey.replaceGridIdentity(previousGrid, mnm);
														qualitySurvey.completeGridIdentityMigration(previousGrid, mnm);
														} catch (IOException ex) {
															try {
																/* Retry immediately while the atlas still proves that
																 * this journal entry is committed. */
																qualitySurvey.replayPendingMigrations(gridsHashes);
															} catch (IOException retry) {
																System.out.println("Quality survey migration retry failed: " + retry);
															}
															String surveyKey = "survey|" + previousGrid + "|" + mnm;
																			if (reportedPersistenceConflicts.add(surveyKey))
																				System.out.println("Could not migrate quality survey anchors from " +
																					previousGrid + " to " + mnm + ": " + ex);
																	}
																	System.out.println("[PersistentMap] replaced confirmed tile at " +
														relativeCoordinates + ": " + previousGrid + " -> " + mnm);
											}
										} catch (IOException ex) {
											String failureKey = "replace|" + relativeCoordinates + "|" + mnm;
											if (reportedPersistenceConflicts.add(failureKey))
												System.out.println("Could not replace confirmed minimap tile " +
														relativeCoordinates + " for " + mnm + ": " + ex);
										}
									}
								}
								if (!gridsHashes.containsKey(mnm)) {
									String conflictKey = "collision|" + relativeCoordinates + "|" + mnm;
									if (reportedPersistenceConflicts.add(conflictKey))
										System.out.println("Persistent minimap coordinate collision at " +
											relativeCoordinates + " for " + mnm);
								}
							} else {
								boolean recorded = true;
								if (Config.autoSaveMinimaps) {
									try {
										/* Persist first: record() rejects collisions without
										 * changing its map, so the live atlas stays retryable. */
										persistentMap.record(mnm, relativeCoordinates);
									} catch (IOException ex) {
										recorded = false;
										String failureKey = "index|" + relativeCoordinates + "|" + mnm;
										if (reportedPersistenceConflicts.add(failureKey))
											System.out.println("Could not index minimap tile " +
													mnm + ": " + ex);
									}
								}
								if (recorded) {
									coordHashes.put(relativeCoordinates, mnm);
									gridsHashes.put(mnm, relativeCoordinates);
								}
							}
						} else {
							Coord coordinates = gridsHashes.get(mnm);
							if (!coordinates.equals(relativeCoordinates)) {
								mappingStartPoint = mappingStartPoint
										.add(relativeCoordinates
												.sub(coordinates));
								changedMappingAnchor();
							}
							awaitingPersistentAnchor = false;
							changedMappingAnchor();
						}

						if (tex == null)
							tex = getgrid(mnm);
						if ((tex == null) && (grid != null)) {
							tex = grid.getTex();
						}
					} else {
						if (grid != null) {
							tex = grid.getTex();
							if (tex != null) {
								if (primary)
									caveTex.put(cg, tex);
							}
						}
						tex = caveTex.get(cg);
					}

					// caveTex.isEmpty();

					if (tex == null)
						continue;

					if (!hidden)
						g.image(tex, cg.mul(cmaps).add(tc.inv())
								.add(hsz.div(2)));
				}
			}
		}
		boolean enteredInterior = false;
		if (primary) {
			synchronized (caveTex) {
				boolean nowInterior = !caveTex.isEmpty();
				enteredInterior = !primaryInterior && nowInterior;
				primaryInterior = nowInterior;
			}
			if (enteredInterior)
				qualitySurvey.clearPending();
			if (enteredInterior) {
				Coord exterior = exteriorContextTile();
				recordedCaveEntranceTile = (exterior == null) ? null
						: new Coord(exterior);
				recordedCaveTiles.clear();
			}
			if ((enteredInterior || interiorEntrancePending ||
					(interiorEntranceTile == null)) && primaryInterior &&
					(mv != null) && (ui != null) && (ui.sess != null)) {
				Gob player = ui.sess.glob.oc.getgob(mv.playergob);
				if (player != null) {
					interiorEntranceTile = player.position().div(tileSize);
					interiorEntrancePending = false;
				} else {
					interiorEntrancePending = true;
				}
			} else if (!primaryInterior) {
				interiorEntranceTile = null;
				interiorEntrancePending = false;
				recordedCaveEntranceTile = null;
			}
		}
		captureRecordedCaveTiles();
		/* Record exterior context continuously while it is actually confirmed by
		 * the persistent atlas. When cave tiles are present this is skipped, so
		 * entering an interior freezes the last trustworthy exterior position. */
		if (primary && !primaryInterior && (mv != null) && (ui != null) && (ui.sess != null)) {
			Gob player = ui.sess.glob.oc.getgob(mv.playergob);
			if (player != null) {
				MapAnchor anchor = anchorForSessionTile(ui.sess.glob.map,
						player.position().div(tileSize));
				rememberExteriorAnchor(anchor);
			}
		}
		// grid
		if (grid && !hidden) {
			g.chcolor(200, 32, 64, 255);
			Coord c1, c2;
			c1 = new Coord();
			c2 = new Coord(hsz.x, 0);
			for (int y = ulg.y + 1; (y * cmaps.y) - tc.y + (hsz.y / 2) < hsz.y; y++) {
				c1.y = (y * cmaps.y) - tc.y + (hsz.y / 2);
				c2.y = c1.y;
				g.line(c1, c2, 1);
			}
			c1 = new Coord();
			c2 = new Coord(0, hsz.y);
			for (int x = ulg.x + 1; (x * cmaps.x) - tc.x + (hsz.x / 2) < hsz.x; x++) {
				c1.x = (x * cmaps.x) - tc.x + (hsz.x / 2);
				c2.x = c1.x;
				g.line(c1, c2, 1);
			}
			g.chcolor();
		}
		// end of grid

		if ((!plx.loading) && (!hidden)) {
			synchronized (ui.sess.glob.party.memb) {
				for (Party.Member m : ui.sess.glob.party.memb.values()) {
					Coord ptc = m.getc();
					if (ptc == null)
						continue;
					ptc = ptc.div(tileSize).add(tc.inv()).add(hsz.div(2));
					g.chcolor(m.col.getRed(), m.col.getGreen(),
							m.col.getBlue(), 128);
					g.image(plx.layer(Resource.imgc).tex(),
							ptc.add(plx.layer(Resource.negc).cc.inv()));
					g.chcolor();
				}
			}
		}
		if (!hidden) {
			if (Config.show_minimap_radius) {
				KerriUtils.drawVisSquare(g, tc, hsz);
			}
			if (Config.show_minimap_profits) {
				KerriUtils.drawProfitMinimap(g, tc, hsz);
				KerriUtils.drawHerbsMinimap(g, tc, hsz);
			}
			if (Config.show_minimap_players) {
				KerriUtils.drawPlayersAtMinimap(g, tc, hsz);
			}
		}
		if (!hidden)
			BreadcrumbTrail.draw(g, tc, hsz);
		if (!hidden)
		/* The interpolated field is intentionally a World Map feature. Rendering
		 * it in the always-open minimap made the normal game loop pay its cost
		 * every frame, while the exact sample dots remain available there. */
		qualitySurvey.draw(g, tc, hsz, !primary,
				(int)Math.round(scale * 1000));
		drawMapOverlay(g, tc, hsz);
		g.gl.glPopMatrix();
		super.draw(og);
	}

	/**
	 * Persists cave rasters as offsets from the pair of positions observed at a
	 * verified surface-to-cave transition. This intentionally does nothing for
	 * direct cave logins and interior-to-interior transitions: neither has a
	 * trustworthy exterior anchor.
	 */
	private void captureRecordedCaveTiles() {
		if (!primary || !Config.autoSaveMinimaps || !primaryInterior)
			return;
		Coord entrance = recordedCaveEntranceTile;
		Coord interior = interiorEntranceTile();
		if ((entrance == null) || (interior == null))
			return;
		synchronized (caveTex) {
			for (Map.Entry<Coord, Tex> entry : caveTex.entrySet()) {
				if (!(entry.getValue() instanceof TexI)) continue;
				Coord offset = entry.getKey().mul(cmaps).sub(interior);
				String key = entrance.x + ":" + entrance.y + ":" + offset.x + ":" + offset.y;
				if (!recordedCaveTiles.add(key)) continue;
				if (!recordedCaves.queueSave(entrance, offset,
						((TexI) entry.getValue()).back)) {
					recordedCaveTiles.remove(key);
				}
			}
		}
	}

	protected void drawMapOverlay(GOut g, Coord tc, Coord hsz) {
	}

	public boolean isCave() {
		return primaryInterior;
	}

	public void saveCaveMaps() {
		synchronized (caveTex) {
			Coord rc = null;
			String sess = Utils.sessdate(System.currentTimeMillis());
			File outputfile = new File("cave/" + sess);
			try {
				Writer currentSessionFile = new FileWriter(
						"cave/currentsession.js");
				currentSessionFile.write("var currentSessionC = '" + sess
						+ "';\n");
				currentSessionFile.close();
			} catch (IOException e1) {
			}
			outputfile.mkdirs();
			for (Coord c : caveTex.keySet()) {
				if (rc == null) {
					rc = c;
				}
				TexI tex = (TexI) caveTex.get(c);
				c = c.sub(rc);
				String fileName = "tile_" + c.x + "_" + c.y;
				outputfile = new File("cave/" + sess + "/" + fileName + ".png");
				try {
					ImageIO.write(tex.back, "png", outputfile);
				} catch (IOException e) {
				}
			}
		}
	}

	public boolean mousedown(Coord c, int button) {
		if (button == 1) {
			if (ui.modctrl) {
				ui.grabmouse(this);
				dm = true;
				doff = c;
			} else {
				Coord tc = localToReal(c);
				mv.map_abs_click(tc.x, tc.y, button, 0);
				return false;
			}
		}
		return (true);
	}

	public Object tooltip(Coord c, boolean again) {
		String survey = qualitySurvey.tooltip(localToTile(c));
		return (survey == null) ? super.tooltip(c, again) : survey;
	}

	public boolean mouseup(Coord c, int button) {
		if (dm) {
			ui.grabmouse(null);
			dm = false;
			return true;
		} else {
			return super.mouseup(c, button);
		}
	}

	public void mousemove(Coord c) {
		if (dm) {
			off = off.add(doff.sub(c));
			doff = c;
		} else {
			super.mousemove(c);
		}
	}

	public void hide() {
		hidden = true;
	}

	public void show() {
		hidden = false;
	}
}
