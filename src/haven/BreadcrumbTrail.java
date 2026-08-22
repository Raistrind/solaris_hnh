package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Session-local, bounded movement trail rendered by both map views. */
public final class BreadcrumbTrail {
	private static final int MAX_POINTS = 1000;
	private static final double MIN_TILE_DISTANCE = 10.0;
	private static final int DOT_RADIUS = 2;
	private static final int ENDPOINT_RADIUS = 3;
	private static final List<Coord> points = new ArrayList<Coord>();

	private BreadcrumbTrail() {
	}

	public static synchronized void reset() {
		points.clear();
	}

	public static synchronized void clear() {
		points.clear();
	}

	public static synchronized void record(Coord realPosition) {
		if ((realPosition == null) || !Config.showBreadcrumbTrail)
			return;
		Coord tile = realPosition.div(MCache.tileSize);
		if (!points.isEmpty()) {
			double distance = points.get(points.size() - 1).dist(tile);
			if (distance > 50)
				points.clear();
			else if (distance < MIN_TILE_DISTANCE)
				return;
		}
		points.add(new Coord(tile));
		while (points.size() > MAX_POINTS)
			points.remove(0);
	}

	public static synchronized int size() {
		return points.size();
	}

	public static synchronized void draw(GOut g, Coord center, Coord mapSize) {
		if (!Config.showBreadcrumbTrail || points.isEmpty())
			return;
		Coord half = mapSize.div(2);
		g.chcolor(new Color(255, 190, 70, 180));
		Coord latest = null;
		for (int i = 0; i < points.size(); i++) {
			Coord dot = points.get(i).sub(center).add(half);
			if (i == (points.size() - 1)) {
				latest = dot;
			} else if (inside(dot, mapSize, DOT_RADIUS)) {
				g.fellipse(dot, new Coord(DOT_RADIUS, DOT_RADIUS));
			}
		}
		if ((latest != null) && inside(latest, mapSize, ENDPOINT_RADIUS)) {
			g.chcolor(new Color(255, 230, 120, 230));
			g.fellipse(latest, new Coord(ENDPOINT_RADIUS,
					ENDPOINT_RADIUS));
		}
		g.chcolor();
	}

	private static boolean inside(Coord point, Coord size, int margin) {
		return (point.x >= margin) && (point.y >= margin) &&
				(point.x < (size.x - margin)) &&
				(point.y < (size.y - margin));
	}
}
