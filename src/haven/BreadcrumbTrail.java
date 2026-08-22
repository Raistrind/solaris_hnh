package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Session-local, bounded movement trail rendered by both map views. */
public final class BreadcrumbTrail {
	private static final int MAX_POINTS = 300;
	private static final double MIN_TILE_DISTANCE = 2.0;
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
		if (!Config.showBreadcrumbTrail || (points.size() < 2))
			return;
		Coord half = mapSize.div(2);
		g.chcolor(new Color(255, 190, 70, 180));
		Coord previous = points.get(0).sub(center).add(half);
		for (int i = 1; i < points.size(); i++) {
			Coord current = points.get(i).sub(center).add(half);
			g.line(previous, current, 2);
			previous = current;
		}
		g.chcolor(new Color(255, 230, 120, 230));
		g.fellipse(previous, new Coord(3, 3));
		g.chcolor();
	}
}
