package haven;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

/** A conservative, surface-only notebook of qualities the client has actually observed. */
final class QualitySurveyStore {
    static final String FORAGE = "forage", SOIL = "soil", CLAY = "clay", WATER = "water", FISH = "fish";
    /* A Dig gesture is known to yield one of these two resources, but the
     * terrain alone cannot always distinguish them (e.g. clay under water). */
    static final String SOIL_OR_CLAY = "soil-or-clay";
    /* v3 adds Fish. Keep v2 parsing separate so a malformed/hand-edited v2
     * file cannot silently acquire a new category. Older clients reject v3. */
    private static final String HEADER = "# Solaris quality survey v3";
    private static final String V2_HEADER = "# Solaris quality survey v2";
    private static final String LEGACY_HEADER = "# Solaris quality survey v1";
    private static final int MAX = 5000, NEAR = 28;
    /* This is deliberately expressed in the same tile coordinates displayed
     * by the World Map hover/status readout. A survey observation therefore
     * affects an estimated field for roughly fifty displayed map tiles, no
     * matter which World Map zoom is selected. */
    private static final int HEAT_RADIUS = 50;
    /* A World Map heat field is deliberately coarse: it needs to communicate
     * a broad estimate, not re-evaluate thousands of tiny pixels every frame. */
    private static final int HEAT_CELL = 8;
    /* Fixed world-space bins make source reduction independent of viewport and
     * zoom. The nearest-neighbour query visits only nearby bins, not every
     * historical sample for every rendered heat-field cell. */
    private static final int HEAT_BUCKET = 16;
    private static final int HEAT_MAX_RING = (HEAT_RADIUS / HEAT_BUCKET) + 2;
    private static final int MAX_HEAT_CELLS = 1024;
    private static final long PENDING_MS = 12000;
	private static final long FISH_PENDING_MS = 90000;
	private static final Set<String> FISH_CATCH_RESOURCES = new HashSet<String>(Arrays.asList(
			"gfx/invobjs/perch", "gfx/invobjs/sturgeon",
			"gfx/invobjs/fish-pike", "gfx/invobjs/fish-perch",
			"gfx/invobjs/fish-plaice", "gfx/invobjs/fish-roach",
			"gfx/invobjs/fish-salmon", "gfx/invobjs/fish-bream",
			"gfx/invobjs/fish-eel", "gfx/invobjs/fish-sturgeon",
			"gfx/invobjs/fish-brill"));
    private final File file;
    /* A small cross-file journal closes the gap between replacing an atlas
     * identity and moving survey samples that reference it.  It is replayed
     * only once the atlas confirms that the old identity is gone and the new
     * one occupies the same coordinate. */
    private final File migrationFile;
    private final Map<String, Sample> samples = new LinkedHashMap<String, Sample>();
    private Pending pending;
    private boolean loaded;
    /* The World Map is redrawn continuously while the player moves. Keep the
     * expensive spatial interpolation as a world-space raster and reuse it
     * until the notebook, enabled layers, viewport, or an 8-tile camera cell
     * changes. Drawing the cached rectangles allocates nothing per frame. */
    private long surveyRevision = 0;
    private VisualData cachedVisualData;
    private HeatData cachedHeatData;
    private HeatRaster cachedHeatRaster;

    static final class Sample {
        final String grid, type; final Coord off;
        int q, latest; long observed, latestObserved;
        Sample(String grid, Coord off, String type, int q, long observed) {
            this.grid = grid; this.off = new Coord(off); this.type = type;
            this.q = q; this.observed = observed; this.latest = q; this.latestObserved = observed;
        }
    }
    static final class Pending { MiniMap.MapAnchor anchor; final String hint; final long until;
		Item candidate;
		Pending(MiniMap.MapAnchor a, String h) {
			anchor = a; hint = h;
			until = System.currentTimeMillis() + (FISH.equals(h) ? FISH_PENDING_MS : PENDING_MS);
		}}
    private static final class Visible { final Sample sample; final Coord tile, screen;
        Visible(Sample sample, Coord tile, Coord screen) { this.sample = sample; this.tile = tile; this.screen = screen; }}
    private static final class HeatPoint {
        final Sample sample; final Coord tile;
        HeatPoint(Sample sample, Coord tile) { this.sample = sample; this.tile = tile; }
    }
    private static final class HeatBucket {
        final ArrayList<HeatPoint> points = new ArrayList<HeatPoint>();
        int add(HeatPoint point) {
            int previous = points.size();
            points.add(point);
            if (points.size() <= 3) return points.size() - previous;
            /* Keep the extrema plus one spatially distinct representative.
             * This preserves both the historical high and low evidence when a
             * dense survey is reduced into one fixed world-space bucket. */
            HeatPoint high = points.get(0), low = points.get(0);
            for (HeatPoint candidate : points) {
                if (candidate.sample.q > high.sample.q) high = candidate;
                if (candidate.sample.q < low.sample.q) low = candidate;
            }
            ArrayList<HeatPoint> retained = new ArrayList<HeatPoint>();
            retained.add(high);
            /* Preserve a genuinely different low when qualities differ. With
             * equal qualities, pick the farthest point first so three nearby
             * same-q observations still form usable spatial evidence. */
            if (low != high) retained.add(low);
            while ((retained.size() < 3) && (retained.size() < points.size())) {
                HeatPoint choice = null;
                long bestDistance = Long.MIN_VALUE;
                for (HeatPoint candidate : points) {
                    if (retained.contains(candidate)) continue;
                    long nearest = Long.MAX_VALUE;
                    for (HeatPoint kept : retained)
                        nearest = Math.min(nearest, distanceSquared(candidate.tile, kept.tile));
                    if (nearest > bestDistance) { bestDistance = nearest; choice = candidate; }
                }
                if (choice == null) break;
                retained.add(choice);
            }
            points.clear(); points.addAll(retained);
            return points.size() - previous;
        }
    }
    private static final class HeatIndex {
        final Map<String, Map<Coord, HeatBucket>> buckets = new HashMap<String, Map<Coord, HeatBucket>>();
        final Map<String, Integer> counts = new HashMap<String, Integer>();
        final Map<String, Integer> retained = new HashMap<String, Integer>();
        void add(String family, HeatPoint point) {
            Map<Coord, HeatBucket> map = buckets.get(family);
            if (map == null) { map = new HashMap<Coord, HeatBucket>(); buckets.put(family, map); }
            Coord key = new Coord(floorDiv(point.tile.x, HEAT_BUCKET), floorDiv(point.tile.y, HEAT_BUCKET));
            HeatBucket bucket = map.get(key);
            if (bucket == null) { bucket = new HeatBucket(); map.put(key, bucket); }
            int retainedDelta = bucket.add(point);
            Integer count = counts.get(family);
            counts.put(family, Integer.valueOf((count == null) ? 1 : count.intValue() + 1));
            Integer retainedCount = retained.get(family);
            retained.put(family, Integer.valueOf((retainedCount == null) ? retainedDelta : retainedCount.intValue() + retainedDelta));
        }
        int count(String family) { Integer count = counts.get(family); return (count == null) ? 0 : count.intValue(); }
        int retainedCount(String family) { Integer count = retained.get(family); return (count == null) ? 0 : count.intValue(); }
        List<HeatPoint> nearest(String family, Coord tile) {
            Map<Coord, HeatBucket> map = buckets.get(family);
            ArrayList<HeatPoint> result = new ArrayList<HeatPoint>();
            if (map == null) return result;
            int target = Math.min(4, retainedCount(family));
            /* With exactly three (or four) observations there is no reason to
             * probe sixteen empty bucket rings for every heat cell. */
            if (count(family) <= 4) {
                for (HeatBucket bucket : map.values()) for (HeatPoint point : bucket.points)
                    insertNearest(result, point, tile);
                return result;
            }
            Coord base = new Coord(floorDiv(tile.x, HEAT_BUCKET), floorDiv(tile.y, HEAT_BUCKET));
            for (int ring = 0; ring <= HEAT_MAX_RING; ring++) {
                for (int y = base.y - ring; y <= base.y + ring; y++) for (int x = base.x - ring; x <= base.x + ring; x++) {
                    if ((ring > 0) && (x != base.x - ring) && (x != base.x + ring) && (y != base.y - ring) && (y != base.y + ring)) continue;
                    HeatBucket bucket = map.get(new Coord(x, y));
                    if (bucket != null) for (HeatPoint point : bucket.points) insertNearest(result, point, tile);
                }
                if (result.size() >= target) {
                    long outsideDistance = (long)(Math.max(0, ring - 1) * HEAT_BUCKET) * (long)(Math.max(0, ring - 1) * HEAT_BUCKET);
                    if (outsideDistance > distanceSquared(result.get(result.size() - 1).tile, tile)) break;
                }
            }
            return result;
        }
    }
    private static final class Range {
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        void include(int quality) { low = Math.min(low, quality); high = Math.max(high, quality); }
        boolean isEmpty() { return low == Integer.MAX_VALUE; }
    }
    private static final class HeatData {
        final long revision, mappingRevision; final int filters; final HeatIndex index;
        final Map<String, Range> ranges;
        HeatData(long revision, long mappingRevision, int filters, HeatIndex index, Map<String, Range> ranges) {
            this.revision = revision; this.mappingRevision = mappingRevision;
            this.filters = filters; this.index = index; this.ranges = ranges;
        }
    }
    private static final class VisualData {
        final long revision, mappingRevision; final int filters; final Collection<Sample> samples; final Map<String, Range> ranges;
        VisualData(long revision, long mappingRevision, int filters, Collection<Sample> samples, Map<String, Range> ranges) {
            this.revision = revision; this.mappingRevision = mappingRevision;
            this.filters = filters; this.samples = samples; this.ranges = ranges;
        }
    }
    private static final class HeatRaster {
        final long revision, mappingRevision; final int filters, bucketX, bucketY, width, height, cell, scaleKey;
        final Tex texture;
        HeatRaster(long revision, long mappingRevision, int filters, int bucketX, int bucketY,
                int width, int height, int cell, int scaleKey, Tex texture) {
            this.revision = revision; this.mappingRevision = mappingRevision; this.filters = filters;
            this.bucketX = bucketX; this.bucketY = bucketY; this.width = width; this.height = height;
            this.cell = cell; this.scaleKey = scaleKey; this.texture = texture;
        }
    }

    QualitySurveyStore(File root) {
        file = new File(root, "quality-survey.conf");
        migrationFile = new File(root, "quality-survey-migrations.conf");
    }
    synchronized void arm(MiniMap.MapAnchor anchor, String hint) {
        pending = null;
        if ((anchor != null) && (hint != null)) pending = new Pending(anchor, hint);
    }
    synchronized void clearPending() { pending = null; }

    synchronized void prepareGridIdentityMigration(String oldGrid, String newGrid)
            throws IOException {
        if ((oldGrid == null) || (newGrid == null) || oldGrid.equals(newGrid))
            return;
        Map<String, String> migrations = loadMigrations();
        if (!newGrid.equals(migrations.get(oldGrid))) {
            migrations.put(oldGrid, newGrid);
            saveMigrations(migrations);
        }
    }

    synchronized void completeGridIdentityMigration(String oldGrid, String newGrid)
            throws IOException {
        Map<String, String> migrations = loadMigrations();
        if (newGrid.equals(migrations.get(oldGrid))) {
            migrations.remove(oldGrid);
            saveMigrations(migrations);
        }
    }

    /** Replay only migrations whose atlas replacement is already committed. */
    synchronized void replayPendingMigrations(Map<String, Coord> atlas)
            throws IOException {
        Map<String, String> migrations = loadMigrations();
        if (migrations.isEmpty()) return;
        boolean changed = false;
        Map<String, String> snapshot = new LinkedHashMap<String, String>(migrations);
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            String oldGrid = entry.getKey(), newGrid = entry.getValue();
            if ((atlas.containsKey(oldGrid)) || !atlas.containsKey(newGrid)) continue;
            replaceGridIdentity(oldGrid, newGrid);
            migrations.remove(oldGrid);
            changed = true;
        }
        if (changed) saveMigrations(migrations);
    }

    /** Move survey anchors when a confirmed map tile receives a refreshed
     * server identity. The map coordinate and offsets remain unchanged; only
     * the grid hash used to resolve them changes. */
    synchronized void replaceGridIdentity(String oldGrid, String newGrid)
            throws IOException {
        if ((oldGrid == null) || (newGrid == null) || oldGrid.equals(newGrid))
            return;
        load();
        Map<String, Sample> original = new LinkedHashMap<String, Sample>(samples);
        boolean changed = false;
        for (Map.Entry<String, Sample> entry : original.entrySet()) {
            Sample sample = entry.getValue();
            if (!oldGrid.equals(sample.grid))
                continue;
            String replacementKey = key(newGrid, sample.off, sample.type);
            Sample replacement = samples.get(replacementKey);
            if (replacement == null) {
                replacement = new Sample(newGrid, sample.off, sample.type,
                        sample.q, sample.observed);
                replacement.latest = sample.latest;
                replacement.latestObserved = sample.latestObserved;
                samples.put(replacementKey, replacement);
            } else {
                if (sample.q > replacement.q) {
                    replacement.q = sample.q;
                    replacement.observed = sample.observed;
                }
                if (sample.latestObserved > replacement.latestObserved) {
                    replacement.latest = sample.latest;
                    replacement.latestObserved = sample.latestObserved;
                }
            }
            samples.remove(entry.getKey());
            changed = true;
        }
        if (!changed) {
            if ((pending != null) && (pending.anchor != null) &&
                    oldGrid.equals(pending.anchor.gridName))
                pending.anchor = new MiniMap.MapAnchor(newGrid, pending.anchor.offset);
            return;
        }
        surveyRevision++;
        cachedVisualData = null;
        cachedHeatData = null;
        if ((cachedHeatRaster != null) && (cachedHeatRaster.texture != null))
            cachedHeatRaster.texture.dispose();
        cachedHeatRaster = null;
        try {
            save();
        } catch (IOException ex) {
            samples.clear();
            samples.putAll(original);
            surveyRevision--;
            cachedVisualData = null;
            cachedHeatData = null;
            if ((cachedHeatRaster != null) && (cachedHeatRaster.texture != null))
                cachedHeatRaster.texture.dispose();
            cachedHeatRaster = null;
            throw ex;
        }
        if ((pending != null) && (pending.anchor != null) &&
                oldGrid.equals(pending.anchor.gridName))
            pending.anchor = new MiniMap.MapAnchor(newGrid, pending.anchor.offset);
    }

	 synchronized void noteItemCreated(Item item) {
		if ((pending != null) && FISH.equals(pending.hint) && (pending.candidate == null)
				&& (item != null) && (item.parent instanceof Inventory)
				&& !MiniMap.isPrimaryInterior() && (System.currentTimeMillis() <= pending.until))
			pending.candidate = item;
	}

	/* A manual inventory operation after a cast makes any remaining action-to-
	 * result association unsafe (e.g. transfers/trades), so discard it. */
	synchronized void cancelForInventoryMove(Item item) {
		if ((pending != null) && FISH.equals(pending.hint)) pending = null;
	}

    synchronized long observe(Item item) {
        if (!Config.qualitySurvey || (item == null) || (item.get_quality() <= 0) || (pending == null) || (System.currentTimeMillis() > pending.until)) {
            if ((pending != null) && (System.currentTimeMillis() > pending.until)) pending = null;
            return 0;
        }
		/* Fish samples are tied to the one new inventory item created after the
		 * accepted cast. Existing item updates cannot consume this longer window. */
		if (FISH.equals(pending.hint) && (pending.candidate != item)) return 0;
		String resource = item.GetResName();
		if ((resource == null) || (resource.length() == 0))
			return FISH.equals(pending.hint) ? pending.until : 0;
        String type = classify(resource, item.tooltip);
		if (FISH.equals(pending.hint) && (type == null)) { pending = null; return 0; }
        if ((type == null) || !matchesHint(type, pending.hint) || !enabled(type) || MiniMap.isPrimaryInterior()) return 0;
        MiniMap.MapAnchor anchor = pending.anchor;
        pending = null;
        try {
            load();
            String key = key(anchor.gridName, anchor.offset, type);
            Sample old = samples.get(key);
            int latest = item.get_quality(); long now = System.currentTimeMillis();
            Sample saved = new Sample(anchor.gridName, anchor.offset, type,
                    (old == null) ? latest : Math.max(old.q, latest),
                    ((old == null) || (latest > old.q)) ? now : old.observed);
            saved.latest = latest; saved.latestObserved = now;
            samples.put(key, saved);
            surveyRevision++;
            trim(); save();
        } catch (IOException e) { System.out.println("Could not save quality survey: " + e); }
		return 0;
    }

    static String classify(String res, String tip) { String resource = (res == null ? "" : res).toLowerCase(Locale.US), tooltip = (tip == null ? "" : tip).toLowerCase(Locale.US); String s = resource + " " + tooltip;
		/* Fish is intentionally recognized only while a verified Fish action is
		 * pending (see observe), and only from catch resources known to this
		 * client. This excludes bait, fish bones, cooked food and arbitrary items. */
		if (FISH_CATCH_RESOURCES.contains(resource)) return FISH;
        if (s.contains("clay")) return CLAY; if (s.contains("soil")) return SOIL;
		/* Empty flasks/skins retain water-like resource names. Only a separate
		 * Water content tooltip identifies their filled result; generic empty
		 * containers therefore cannot consume a pending water observation. */
		boolean container = resource.contains("bucket") || resource.contains("flask") || resource.contains("skin");
		if (resource.contains("water") && !container) return WATER;
		/* A container resource can be changed before its resulting tooltip
		 * arrives. Never treat that intermediate resource as water: its encoded
		 * quality may still be the bucket/flask's own quality. The Legacy server
		 * identifies the collected result in the tooltip ("... quality N water"),
		 * and Item.settip parses that value into q2 before retrying observation. */
		if (container && hasWaterContent(tooltip)) return WATER;
        if (s.contains("gfx/invobjs/") && (s.contains("herb") || s.contains("flower") || s.contains("mushroom") || s.contains("forage"))) return FORAGE;
        return null; }
    private static boolean hasWaterContent(String tooltip) { return tooltip.matches(".*\\bwater\\b.*"); }
    static boolean enabled(String type) { return (FORAGE.equals(type) && Config.qualitySurveyForage) || (SOIL.equals(type) && Config.qualitySurveySoil) || (CLAY.equals(type) && Config.qualitySurveyClay) || (WATER.equals(type) && Config.qualitySurveyWater) || (FISH.equals(type) && Config.qualitySurveyFish); }
    private static boolean matchesHint(String observed, String hint) { return observed.equals(hint) || (SOIL_OR_CLAY.equals(hint) && (SOIL.equals(observed) || CLAY.equals(observed))); }
    static String terrainType(String resourceName) {
        String name = (resourceName == null) ? "" : resourceName.toLowerCase(Locale.US);
        if (name.contains("water")) return WATER;
        if (name.contains("clay")) return CLAY;
        if (name.contains("soil") || name.contains("dirt")) return SOIL;
        return null;
    }
    static boolean isWaterContainer(Item item) {
        String name = (item == null || item.GetResName() == null) ? "" : item.GetResName().toLowerCase(Locale.US);
        return name.contains("bucket") || name.contains("waterflask") || name.contains("waterskin");
    }

    synchronized void draw(GOut g, Coord tc, Coord hsz, boolean drawHeatField, int scaleKey) {
        if (!Config.qualitySurvey || MiniMap.isPrimaryInterior()) return;
        try { load(); } catch (IOException e) { return; }
        int filters = heatFilterMask();
        VisualData visuals = visualData(filters, MiniMap.mappingRevision());
        Collection<Sample> visualSamples = visuals.samples;
        ArrayList<Visible> visible = new ArrayList<Visible>();
        for (Sample sample : visualSamples) {
            Coord tile = MiniMap.sessionTileForAnchor(sample.grid, sample.off); if (tile == null) continue;
            Coord screen = tile.sub(tc).add(hsz.div(2));
            /* GOut clips textures, but legacy immediate-mode primitives such
             * as fellipse do not honour reclip(). Keep the entire dot inside
             * the logical map canvas rather than allowing the primitive to
             * leak into the game view or over the map frame. */
            if (inside(screen, 3, hsz)) visible.add(new Visible(sample, tile, screen));
        }
        Map<String, Range> ranges = visuals.ranges;
        if (Config.qualitySurveyHeatmap && drawHeatField)
            drawHeatmap(g, visualSamples, tc, hsz, ranges, scaleKey);
        if (Config.qualitySurveyDots) for (Visible point : visible) {
            g.chcolor(Color.BLACK); g.fellipse(point.screen, new Coord(3, 3));
			g.chcolor(color(ranges.get(family(point.sample.type)), point.sample.q, 255)); g.fellipse(point.screen, new Coord(2, 2));
        }
        g.chcolor();
    }

    /* Render a continuous field, not circles. A fixed world-coordinate index
     * supplies just the nearest evidence for each cell, so this scales with
     * visible cells rather than visible-cells times the full survey notebook. */
    private void drawHeatmap(GOut g, Collection<Sample> source, Coord tc, Coord hsz, Map<String, Range> ranges, int scaleKey) {
        int cell = Math.max(HEAT_CELL, (int)Math.ceil(Math.sqrt((hsz.x * (double)hsz.y) / MAX_HEAT_CELLS)));
        int filters = heatFilterMask();
        long mappingRevision = MiniMap.mappingRevision();
        HeatData data = heatData(source, ranges, filters, mappingRevision);
        /* The cached image is rasterized in screen coordinates. Key it by the
         * exact logical view center so it cannot drift away from the map while
         * the camera moves inside a former coarse cache bucket. It is still
         * reused for every unchanged frame, which removes the gameplay lag. */
        int bucketX = tc.x, bucketY = tc.y;
        if ((cachedHeatRaster == null) || (cachedHeatRaster.revision != surveyRevision)
                || (cachedHeatRaster.mappingRevision != mappingRevision)
                || (cachedHeatRaster.filters != filters) || (cachedHeatRaster.bucketX != bucketX)
                || (cachedHeatRaster.bucketY != bucketY) || (cachedHeatRaster.width != hsz.x)
                || (cachedHeatRaster.height != hsz.y) || (cachedHeatRaster.cell != cell)
                || (cachedHeatRaster.scaleKey != scaleKey))
            replaceHeatRaster(rasterize(data, tc, hsz, cell, bucketX, bucketY, scaleKey));
        /* One already-clipped RGBA texture replaces hundreds of GL immediate
         * rectangles. This is both substantially cheaper and guarantees that
         * no survey color can spill outside the map canvas. */
        if (cachedHeatRaster.texture != null) {
            g.chcolor();
            g.image(cachedHeatRaster.texture, Coord.z);
        }
    }

    private void replaceHeatRaster(HeatRaster next) {
        HeatRaster old = cachedHeatRaster;
        cachedHeatRaster = next;
        if ((old != null) && (old.texture != null)) old.texture.dispose();
    }

    private int heatFilterMask() {
        int mask = 0;
        /* Keep category settings independent even though forage and soil use
         * the same color/terrain-quality family once they are selected. */
        if (Config.qualitySurveyForage) mask |= 1;
        if (Config.qualitySurveySoil) mask |= 2;
        if (Config.qualitySurveyClay) mask |= 4;
        if (Config.qualitySurveyWater) mask |= 8;
        if (Config.qualitySurveyFish) mask |= 16;
        return mask;
    }

    private HeatData heatData(Collection<Sample> source, Map<String, Range> ranges, int filters, long mappingRevision) {
        if ((cachedHeatData == null) || (cachedHeatData.revision != surveyRevision)
                || (cachedHeatData.mappingRevision != mappingRevision) || (cachedHeatData.filters != filters))
            cachedHeatData = new HeatData(surveyRevision, mappingRevision, filters, heatIndex(source), ranges);
        return cachedHeatData;
    }

    private VisualData visualData(int filters, long mappingRevision) {
        if ((cachedVisualData == null) || (cachedVisualData.revision != surveyRevision)
                || (cachedVisualData.mappingRevision != mappingRevision) || (cachedVisualData.filters != filters)) {
            Collection<Sample> source = visualSamples();
            cachedVisualData = new VisualData(surveyRevision, mappingRevision, filters, source, ranges(source));
        }
        return cachedVisualData;
    }

    private HeatRaster rasterize(HeatData data, Coord tc, Coord hsz, int cell, int bucketX, int bucketY, int scaleKey) {
        int left = tc.x - (hsz.x / 2), top = tc.y - (hsz.y / 2);
        int startX = floorDiv(left, cell) * cell - cell, startY = floorDiv(top, cell) * cell - cell;
        int endX = tc.x + ((hsz.x + 1) / 2) + cell, endY = tc.y + ((hsz.y + 1) / 2) + cell;
        BufferedImage image = TexI.mkbuf(hsz);
        Graphics graphics = image.getGraphics();
        for (String type : data.index.buckets.keySet()) for (int y = startY; y < endY; y += cell) for (int x = startX; x < endX; x += cell) {
            Coord tile = new Coord(x + (cell / 2), y + (cell / 2));
            List<HeatPoint> nearest = data.index.nearest(type, tile);
            double weightedQuality = 0, totalWeight = 0, closest = Double.MAX_VALUE; int evidence = 0;
            for (HeatPoint point : nearest) {
                double distance = Math.sqrt(distanceSquared(point.tile, tile));
                if (distance > HEAT_RADIUS) continue;
                double weight = 1.0 - (distance / (HEAT_RADIUS + 1.0));
                weightedQuality += point.sample.q * weight; totalWeight += weight;
                closest = Math.min(closest, distance); evidence++;
            }
            if (evidence == 0) continue;
            double strength = Math.min(1.0, 0.36 + (0.22 * (evidence - 1)));
            double confidence = strength * (0.35 + (0.65 * Math.max(0.0, 1.0 - (closest / HEAT_RADIUS))));
            if (confidence < 0.10) continue;
            int x0 = Math.max(0, x - left), y0 = Math.max(0, y - top);
            int x1 = Math.min(hsz.x, (x - left) + cell), y1 = Math.min(hsz.y, (y - top) + cell);
            if ((x1 <= x0) || (y1 <= y0)) continue;
            graphics.setColor(color(data.ranges.get(type),
                    (int)Math.round(weightedQuality / totalWeight),
                    35 + (int)Math.round(125 * confidence)));
            graphics.fillRect(x0, y0, x1 - x0, y1 - y0);
        }
        graphics.dispose();
        return new HeatRaster(surveyRevision, data.mappingRevision, data.filters, bucketX, bucketY,
                hsz.x, hsz.y, cell, scaleKey, new TexI(image));
    }

    private HeatIndex heatIndex(Collection<Sample> source) {
        HeatIndex index = new HeatIndex();
        for (Sample sample : source) {
            Coord tile = MiniMap.sessionTileForAnchor(sample.grid, sample.off);
            if (tile != null) index.add(family(sample.type), new HeatPoint(sample, tile));
        }
        return index;
    }

    private static void insertNearest(List<HeatPoint> nearest, HeatPoint point, Coord tile) {
        long distance = distanceSquared(point.tile, tile);
        int at = 0;
        while ((at < nearest.size()) && (distanceSquared(nearest.get(at).tile, tile) <= distance)) at++;
        if (at >= 4) return;
        nearest.add(at, point);
        if (nearest.size() > 4) nearest.remove(nearest.size() - 1);
    }

    private static long distanceSquared(Coord a, Coord b) {
        long dx = a.x - (long)b.x, dy = a.y - (long)b.y;
        return (dx * dx) + (dy * dy);
    }

    private static boolean inside(Coord point, int margin, Coord bounds) {
        return (point.x >= margin) && (point.y >= margin) &&
                (point.x < bounds.x - margin) && (point.y < bounds.y - margin);
    }
    private static int floorDiv(int value, int divisor) { int result = value / divisor; return ((value < 0) && ((value % divisor) != 0)) ? result - 1 : result; }

    synchronized String tooltip(Coord tile) {
        if (!Config.qualitySurvey || MiniMap.isPrimaryInterior()) return null;
        try { load(); } catch (IOException e) { return null; }
        Sample best = null; double distance = Double.MAX_VALUE;
        for (Sample sample : samples.values()) { if (!enabled(sample.type)) continue; Coord p = MiniMap.sessionTileForAnchor(sample.grid, sample.off); if ((p != null) && (p.dist(tile) < distance)) { distance = p.dist(tile); best = sample; } }
        return ((best != null) && (distance <= 5)) ? capitalize(best.type) + " observed: best q" + best.q + ", latest q" + best.latest + " (" + ((System.currentTimeMillis() - best.latestObserved) / 60000) + "m ago). Heatmap estimates from observed historical bests." : null;
    }
    private static String capitalize(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    private static String key(String grid, Coord off, String type) { return grid + "|" + off.x + "|" + off.y + "|" + type; }
	/* Forage and soil share a terrain-quality family. Every family is scaled
	 * against its persisted historical best samples, so a newly-discovered
	 * extreme recolors all visible dots and estimates immediately. */
	/* Collapse samples that refer to the same map anchor and visual family.
	 * In particular, a plant-pick and a soil-dig on one tile must show the
	 * single strongest known terrain quality, never two conflicting dots. */
	private Collection<Sample> visualSamples() {
		Map<String, Sample> result = new LinkedHashMap<String, Sample>();
		for (Sample sample : samples.values()) {
			if (!enabled(sample.type)) continue;
			String key = sample.grid + "|" + sample.off.x + "|" + sample.off.y + "|" + family(sample.type);
			Sample old = result.get(key);
			if ((old == null) || (sample.q > old.q)) result.put(key, sample);
		}
		return result.values();
	}
	private Map<String, Range> ranges(Collection<Sample> source) {
		Map<String, Range> result = new HashMap<String, Range>();
		for (Sample sample : source) {
			String family = family(sample.type);
			Range range = result.get(family);
			if (range == null) { range = new Range(); result.put(family, range); }
			range.include(sample.q);
		}
		return result;
	}
	private static String family(String type) { return (FORAGE.equals(type) || SOIL.equals(type)) ? SOIL : type; }
	private static Color color(Range range, int q, int alpha) {
		/* One known value has no meaningful relative position, so present it as
		 * the current best rather than divide by zero or imply a low value. */
		double fraction = ((range == null) || range.isEmpty() || (range.high == range.low)) ? 1.0 : (q - range.low) / (double)(range.high - range.low);
		fraction = Math.max(0, Math.min(1, fraction));
		int red = (int)Math.round(255 * (1.0 - fraction));
		int green = (int)Math.round(48 + (207 * fraction));
		return new Color(red, green, 24, alpha);
	}
	private void trim() {
		while (samples.size() > MAX) {
			String discard = null;
			for (Map.Entry<String, Sample> entry : samples.entrySet()) {
				if ((discard == null) || lowerPriority(entry.getValue(), samples.get(discard)))
					discard = entry.getKey();
			}
			if (discard == null) return;
			samples.remove(discard);
		}
	}

	/* Bounded storage protects the strongest historical survey results first. */
	private static boolean lowerPriority(Sample candidate, Sample current) {
		return (candidate.q < current.q) || ((candidate.q == current.q)
				&& (candidate.observed < current.observed));
	}

    private void load() throws IOException {
        if (loaded) return;
        File primary = file, newer = new File(file.getPath() + ".new"), backup = new File(file.getPath() + ".bak");
        IOException failure = null;
        File[] candidates = { primary, newer, backup };
        for (File candidate : candidates) {
            if (!candidate.exists()) continue;
            try {
                Map<String, Sample> parsed = parse(candidate);
                samples.clear(); samples.putAll(parsed); surveyRevision++; loaded = true; return;
            } catch (IOException e) { failure = e; }
        }
        if (failure != null) throw failure; // Never overwrite unreadable data after a failed load.
        surveyRevision++; loaded = true;
    }

    private Map<String, Sample> parse(File source) throws IOException {
        Map<String, Sample> parsed = new LinkedHashMap<String, Sample>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(source), "UTF-8"));
        try {
            String line = reader.readLine();
            boolean legacy = false, fishAllowed = false;
            if (HEADER.equals(line)) {
				fishAllowed = true;
                line = reader.readLine();
			} else if (V2_HEADER.equals(line)) {
				line = reader.readLine();
            } else if (LEGACY_HEADER.equals(line)) {
                legacy = true;
                line = reader.readLine();
            } else if ((line != null) && line.startsWith("#")) {
                throw new IOException("Unknown quality survey header");
            } else {
                /* Headerless files were written by the initial survey build. */
                legacy = (line != null);
            }
            if (line != null) parseRecord(parsed, line, legacy, fishAllowed);
            while ((line = reader.readLine()) != null) if (line.length() != 0) parseRecord(parsed, line, legacy, fishAllowed);
            /* Stores are only created after recording a sample. Treat an empty
             * primary or interrupted header-only write as invalid so recovery
             * can still select a complete .new or .bak candidate. */
            if (parsed.isEmpty()) throw new IOException("Empty quality survey candidate");
        } catch (RuntimeException e) { throw new IOException("Invalid quality survey " + source, e); }
        finally { reader.close(); }
        return parsed;
    }

    private void parseRecord(Map<String, Sample> parsed, String line, boolean legacy, boolean fishAllowed) throws IOException {
        String[] values = line.split("\\|", -1);
        if (legacy ? values.length != 6 : values.length != 8) throw new IOException("Invalid quality survey record");
        try {
            String grid = URLDecoder.decode(values[0], "UTF-8"); Coord off = new Coord(Integer.parseInt(values[1]), Integer.parseInt(values[2]));
            String type = values[3]; int bestQ = Integer.parseInt(values[4]); long bestAt = Long.parseLong(values[5]);
            if ((grid.length() == 0) || !validType(type, fishAllowed) || (bestQ < 0) || (bestAt < 0)) throw new IOException("Invalid quality survey values");
            Sample sample = new Sample(grid, off, type, bestQ, bestAt);
            if (!legacy) { sample.latest = Integer.parseInt(values[6]); sample.latestObserved = Long.parseLong(values[7]); if ((sample.latest < 0) || (sample.latestObserved < 0)) throw new IOException("Invalid quality survey latest values"); }
            parsed.put(key(grid, off, type), sample);
        } catch (IllegalArgumentException e) { throw new IOException("Invalid quality survey record", e); }
    }
    private static boolean validType(String type, boolean fishAllowed) { return FORAGE.equals(type) || SOIL.equals(type) || CLAY.equals(type) || WATER.equals(type) || (fishAllowed && FISH.equals(type)); }

    private void save() throws IOException {
        File dir = file.getParentFile(); if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create quality survey directory");
        File newer = new File(file.getPath() + ".new"), backup = new File(file.getPath() + ".bak");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(newer), "UTF-8"));
        try {
            writer.write(HEADER); writer.write("\n");
            for (Sample sample : samples.values()) writer.write(URLEncoder.encode(sample.grid, "UTF-8") + "|" + sample.off.x + "|" + sample.off.y + "|" + sample.type + "|" + sample.q + "|" + sample.observed + "|" + sample.latest + "|" + sample.latestObserved + "\n");
        } finally { writer.close(); }
        if (backup.exists() && !backup.delete()) throw new IOException("Cannot replace quality survey backup");
        if (file.exists() && !file.renameTo(backup)) throw new IOException("Cannot stage quality survey replacement");
        if (!newer.renameTo(file)) { if (backup.exists()) backup.renameTo(file); throw new IOException("Cannot install quality survey"); }
        if (backup.exists() && !backup.delete()) System.out.println("Could not remove quality survey backup " + backup);
    }

    private Map<String, String> loadMigrations() throws IOException {
        File[] candidates = { migrationFile,
                new File(migrationFile.getPath() + ".new"),
                new File(migrationFile.getPath() + ".bak") };
        IOException failure = null;
        for (File candidate : candidates) {
            if (!candidate.exists()) continue;
            try { return parseMigrations(candidate); }
            catch (IOException e) { failure = e; }
        }
        if (failure != null) throw failure;
        return new LinkedHashMap<String, String>();
    }

    private Map<String, String> parseMigrations(File source) throws IOException {
        Map<String, String> result = new LinkedHashMap<String, String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(source), "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0 || line.startsWith("#")) continue;
                String[] values = line.split("\\|", -1);
                if (values.length != 2) continue;
                try {
                    String oldGrid = URLDecoder.decode(values[0], "UTF-8");
                    String newGrid = URLDecoder.decode(values[1], "UTF-8");
                    if (oldGrid.length() > 0 && newGrid.length() > 0 &&
                            !oldGrid.equals(newGrid)) result.put(oldGrid, newGrid);
                } catch (IllegalArgumentException e) {
                    /* Ignore an incomplete journal line; the next confirmed
                     * replacement will write a complete entry again. */
                }
            }
        } finally { reader.close(); }
        return result;
    }

    private void saveMigrations(Map<String, String> migrations) throws IOException {
        File dir = migrationFile.getParentFile();
        if (!dir.exists() && !dir.mkdirs())
            throw new IOException("Cannot create quality survey directory");
        if (migrations.isEmpty()) {
            if (migrationFile.exists() && !migrationFile.delete())
                throw new IOException("Cannot clear quality survey migration journal");
            return;
        }
        File newer = new File(migrationFile.getPath() + ".new");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(newer), "UTF-8"));
        try {
            writer.write("# Solaris quality survey migration journal v1");
            writer.newLine();
            for (Map.Entry<String, String> entry : migrations.entrySet()) {
                writer.write(URLEncoder.encode(entry.getKey(), "UTF-8"));
                writer.write('|');
                writer.write(URLEncoder.encode(entry.getValue(), "UTF-8"));
                writer.newLine();
            }
        } finally { writer.close(); }
        File backup = new File(migrationFile.getPath() + ".bak");
        if (backup.exists() && !backup.delete()) {
            newer.delete();
            throw new IOException("Cannot replace quality survey migration backup");
        }
        if (migrationFile.exists() && !migrationFile.renameTo(backup)) {
            newer.delete();
            throw new IOException("Cannot stage quality survey migration journal");
        }
        if (!newer.renameTo(migrationFile)) {
            if (backup.exists()) backup.renameTo(migrationFile);
            throw new IOException("Cannot install quality survey migration journal");
        }
        if (backup.exists() && !backup.delete())
            System.out.println("Could not remove quality survey migration backup " + backup);
    }
}
