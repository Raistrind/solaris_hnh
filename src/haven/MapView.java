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
import haven.MCache.Overlay;
import haven.Resource.Tile;
import haven.Coord;
import haven.resutil.GrowingPlant;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import union.APXUtils;
import union.KerriUtils;
import union.JSBotUtils;

public class MapView extends Widget implements DTarget, Console.Directory {
	static Color[] olc = new Color[31];
	static Map<String, Class<? extends Camera>> camtypes = new HashMap<String, Class<? extends Camera>>();
	public Coord mc = new Coord(0, 0), mousepos = new Coord(0, 0), pmousepos = new Coord(0, 0), mouseAtTile = new Coord(0, 0);
	Camera cam;
	Sprite.Part[] clickable = {};
	List<Sprite.Part> obscured = Collections.emptyList();
	private int[] visol = new int[31];
	private long olftimer = 0;
	private int olflash = 0;
	Grabber grab = null;
	ILM mask;
	final MCache map;
	public final Glob glob;
	Collection<Gob> plob = null;
	boolean plontile;
	int plrad = 0;
	int playergob = -1;
	public Profile prof = new Profile(300);
	private Profile.Frame curf;
	Coord plfpos = null;
	long lastmove = 0;
	Sprite.Part obscpart = null;
	Gob obscgob = null;
	static Text.Foundry polownertf = new Text.Foundry("serif", 20);
	public Text polownert = null;
	public String polowner = null;
	public Coord moveto = null;
	long polchtm = 0;
	int si = 4;
	double _scale = 1;
	double scales[] = { 0.5, 0.66, 0.8, 0.9, 1, 1.25, 1.5, 1.75, 2 };
	Map<String, Integer> radiuses;
	Map<String, Integer> terobjradiuses;
	private static final long CART_DEPOSIT_TIMEOUT = 60000;
	private boolean pendingCartDeposit;
	private Item pendingCartDepositItem;
	private long pendingCartDepositUntil;
	int beast_check_delay = 0;
	public boolean player_moving = false;
	public boolean objectSelecting = false; //used for JS selectObject
	public boolean waitForSelect = false; 	//used for JS selectObject
	public Gob objectUnderMouse = null;   	//current object under mouse (hover)
	public Coord myLastCoord;				//my last coord
	private Gob flowerMenuTarget = null;
	private long flowerMenuTargetTime = 0;
	/* Kept until the corresponding flower-menu choice is acknowledged. This is
	 * separate from the display target, which the menu constructor consumes. */
	private Gob qualitySurveyTarget = null;
	private Coord qualitySurveyTargetTile = null;
	private long qualitySurveyTargetTime = 0;
	//private ArrayList<Integer> ignoredObjects = new ArrayList<Integer>();
	
	
	 private static int dup(int paramInt)
	  {
	    return paramInt << 4 | paramInt;
	  }
	
	private static Color int16toc(int paramInt) {
	    return new Color(dup((paramInt & 0xF000) >> 12), dup((paramInt & 0xF00) >> 8),
	    		dup((paramInt & 0xF0) >> 4), dup((paramInt & 0xF) >> 0));
	  }

	public double getScale() {
		return Config.zoom ? _scale : 1;
	}

	public void setScale(double value) {
		_scale = value;
		// mask.dispose();
		// mask = new ILM(MainFrame.getScreenSize().div(_scale), glob.oc);
	}

	public static final Comparator<Sprite.Part> clickcmp = new Comparator<Sprite.Part>() {
		public int compare(Sprite.Part a, Sprite.Part b) {
			return (-Sprite.partidcmp.compare(a, b));
		}
	};

	static {
		Widget.addtype("mapview", new WidgetFactory() {
			public Widget create(Coord c, Widget parent, Object[] args) {
				Coord sz = MainFrame.getInnerSize();
				Coord mc = (Coord) args[1];
				int pgob = -1;
				if (args.length > 2) {
					pgob = (Integer) args[2];
					JSBotUtils.playerID = pgob;
				}
				return (new MapView(c, sz, parent, mc, pgob));
			}
		});
		olc[0] = new Color(255, 0, 128);
		olc[1] = new Color(0, 0, 255);
		olc[2] = new Color(255, 0, 0);
		olc[3] = new Color(128, 0, 255);
		olc[16] = new Color(0, 255, 0);
		olc[17] = new Color(255, 255, 0);
	}

	public interface Grabber {
		void mmousedown(Coord mc, int button);
		void mmouseup(Coord mc, int button);
		void mmousemove(Coord mc);
	}

	@SuppressWarnings("serial")
	public static class GrabberException extends RuntimeException {
	}

	public static class Camera {
		public void setpos(MapView mv, Gob player, Coord sz) {
		}

		public boolean click(MapView mv, Coord sc, Coord mc, int button) {
			return (false);
		}

		public void move(MapView mv, Coord sc, Coord mc) {
		}

		public boolean release(MapView mv, Coord sc, Coord mc, int button) {
			return (false);
		}

		public void moved(MapView mv) {
		}

		public static void borderize(MapView mv, Gob player, Coord sz,
				Coord border) {
			if (Config.noborders) {
				return;
			}
			Coord mc = mv.mc;
			Coord oc = m2s(mc).inv();
			int bt = -((sz.y / 2) - border.y);
			int bb = (sz.y / 2) - border.y;
			int bl = -((sz.x / 2) - border.x);
			int br = (sz.x / 2) - border.x;
			Coord sc = m2s(player.position()).add(oc);
			if (sc.x < bl)
				mc = mc.add(s2m(new Coord(sc.x - bl, 0)));
			if (sc.x > br)
				mc = mc.add(s2m(new Coord(sc.x - br, 0)));
			if (sc.y < bt)
				mc = mc.add(s2m(new Coord(0, sc.y - bt)));
			if (sc.y > bb)
				mc = mc.add(s2m(new Coord(0, sc.y - bb)));
			mv.mc = mc;
		}

		public void reset() {
		}
	}

	private static abstract class DragCam extends Camera {
		Coord o, mo;
		boolean dragging = false;
		boolean needreset = false;

		public boolean click(MapView mv, Coord sc, Coord mc, int button) {
			if (button == 2) {
				mv.ui.grabmouse(mv);
				o = sc;
				mo = null;
				dragging = true;
				return (true);
			}
			return (false);
		}

		public void move(MapView mv, Coord sc, Coord mc) {
			if (dragging) {
				Coord off = sc.add(o.inv());
				if ((mo == null) && (off.dist(Coord.z) > 5))
					mo = mv.mc;
				if (mo != null) {
					mv.mc = mo.add(s2m(off).inv());
					moved(mv);
				}
			}
		}

		public void reset() {
			needreset = true;
		}

		public boolean release(MapView mv, Coord sc, Coord mc, int button) {
			if ((button == 2) && dragging) {
				mv.ui.grabmouse(null);
				dragging = false;
				if (mo == null) {
					mv.mc = mc;
					moved(mv);
				}
				return (true);
			}
			return (false);
		}
	}

	static class OrigCam extends Camera {
		public final Coord border = new Coord(250, 150);

		public void setpos(MapView mv, Gob player, Coord sz) {
			borderize(mv, player, sz, border);
		}

		public boolean click(MapView mv, Coord sc, Coord mc, int button) {
			if (button == 1)
				mv.mc = mc;
			return (false);
		}
	}

	static {
		camtypes.put("orig", OrigCam.class);
	}

	static class OrigCam2 extends DragCam {
		public final Coord border = new Coord(250, 125);
		private final double v;
		private Coord tgt = null;
		private long lmv;

		public OrigCam2(double v) {
			this.v = Math.log(v) / 0.02; /* 1 / 50 FPS = 0.02 s */
		}

		public OrigCam2() {
			this(0.9);
		}

		public OrigCam2(String... args) {
			this((args.length < 1) ? 0.9 : Double.parseDouble(args[0]));
		}

		public void setpos(MapView mv, Gob player, Coord sz) {

			if (needreset) {
				needreset = false;
				mv.mc = player.position();
			}

			if (tgt != null) {
				if (mv.mc.dist(tgt) < 10) {
					tgt = null;
				} else {
					long now = System.currentTimeMillis();
					double dt = (now - lmv) / 1000.0;
					lmv = now;
					mv.mc = tgt.add(mv.mc.add(tgt.inv()).mul(Math.exp(v * dt)));
				}
			}
			borderize(mv, player, sz, border);
		}

		public boolean click(MapView mv, Coord sc, Coord mc, int button) {
			if ((button == 1) && (mv.ui.root.cursor == RootWidget.defcurs)) {
				tgt = mc;
				lmv = System.currentTimeMillis();
			}
			return (super.click(mv, sc, mc, button));
		}

		public void moved(MapView mv) {
			tgt = null;
		}

		public void reset() {
			super.reset();
			tgt = null;
		}
	}

	static {
		camtypes.put("clicktgt", OrigCam2.class);
	}

	static class WrapCam extends Camera {
		public final Coord region = new Coord(200, 150);

		public void setpos(MapView mv, Gob player, Coord sz) {
			Coord sc = m2s(player.position().add(mv.mc.inv()));
			if (sc.x < -region.x)
				mv.mc = mv.mc.add(s2m(new Coord(-region.x * 2, 0)));
			if (sc.x > region.x)
				mv.mc = mv.mc.add(s2m(new Coord(region.x * 2, 0)));
			if (sc.y < -region.y)
				mv.mc = mv.mc.add(s2m(new Coord(0, -region.y * 2)));
			if (sc.y > region.y)
				mv.mc = mv.mc.add(s2m(new Coord(0, region.y * 2)));
		}
	}

	static {
		camtypes.put("kingsquest", WrapCam.class);
	}

	static class BorderCam extends DragCam {
		public final Coord border = new Coord(250, 150);

		public void setpos(MapView mv, Gob player, Coord sz) {
			if (needreset) {
				needreset = false;
				mv.mc = player.position();
			}
			borderize(mv, player, sz, border);
		}
	}

	static {
		camtypes.put("border", BorderCam.class);
	}

	static class PredictCam extends DragCam {
		private double xa = 0, ya = 0;
		private boolean reset = true;
		private final double speed = 0.15, rspeed = 0.15;
		private double sincemove = 0;
		private long last = System.currentTimeMillis();

		public void setpos(MapView mv, Gob player, Coord sz) {
			long now = System.currentTimeMillis();
			double dt = ((double) (now - last)) / 1000.0;
			last = now;

			if (needreset) {
				needreset = false;
				mv.mc = player.position();
			}

			Coord mc = mv.mc.add(s2m(sz.add(mv.sz.inv()).div(2)));
			Coord sc = m2s(player.position()).add(m2s(mc).inv());
			if (reset) {
				xa = (double) sc.x / (double) sz.x;
				ya = (double) sc.y / (double) sz.y;
				if (xa < -0.25)
					xa = -0.25;
				if (xa > 0.25)
					xa = 0.25;
				if (ya < -0.15)
					ya = -0.15;
				if (ya > 0.25)
					ya = 0.25;
				reset = false;
			}
			Coord vsz = sz.div(16);
			Coord vc = new Coord((int) (sz.x * xa), (int) (sz.y * ya));
			boolean moved = false;
			if (sc.x < vc.x - vsz.x) {
				if (xa < 0.25)
					xa += speed * dt;
				moved = true;
				mc = mc.add(s2m(new Coord(sc.x - (vc.x - vsz.x) - 4, 0)));
			}
			if (sc.x > vc.x + vsz.x) {
				if (xa > -0.25)
					xa -= speed * dt;
				moved = true;
				mc = mc.add(s2m(new Coord(sc.x - (vc.x + vsz.x) + 4, 0)));
			}
			if (sc.y < vc.y - vsz.y) {
				if (ya < 0.25)
					ya += speed * dt;
				moved = true;
				mc = mc.add(s2m(new Coord(0, sc.y - (vc.y - vsz.y) - 2)));
			}
			if (sc.y > vc.y + vsz.y) {
				if (ya > -0.15)
					ya -= speed * dt;
				moved = true;
				mc = mc.add(s2m(new Coord(0, sc.y - (vc.y + vsz.y) + 2)));
			}
			if (!moved) {
				sincemove += dt;
				if (sincemove > 1) {
					if (xa < -0.1)
						xa += rspeed * dt;
					if (xa > 0.1)
						xa -= rspeed * dt;
					if (ya < -0.1)
						ya += rspeed * dt;
					if (ya > 0.1)
						ya -= rspeed * dt;
				}
			} else {
				sincemove = 0;
			}
			mv.mc = mc.add(s2m(mv.sz.add(sz.inv()).div(2)));
		}

		public void moved(MapView mv) {
			reset = true;
		}

		public void reset() {
			reset = true;
			xa = ya = 0;
			super.reset();
		}
	}

	static {
		camtypes.put("predict", PredictCam.class);
	}

	static class FixedCam extends DragCam {
		public final Coord border = new Coord(250, 150);
		private Coord off = Coord.z;
		private boolean setoff = false;

		public void setpos(MapView mv, Gob player, Coord sz) {
			if (setoff) {
				borderize(mv, player, sz, border);
				off = mv.mc.add(player.position().inv());
				setoff = false;
			}
			mv.mc = player.position().add(off);
		}

		public void moved(MapView mv) {
			setoff = true;
		}

		public void reset() {
			off = Coord.z;
		}
	}

	static {
		camtypes.put("fixed", FixedCam.class);
	}

	static class CakeCam extends Camera {
		private Coord border = new Coord(250, 150);
		private Coord size, center, diff;

		public void setpos(MapView mv, Gob player, Coord sz) {
			if (size == null || !size.equals(sz)) {
				size = new Coord(sz);
				center = size.div(2);
				diff = center.sub(border);
			}
			if (player != null && mv.pmousepos != null)
				mv.mc = player.position().sub(
						s2m(center.sub(mv.pmousepos).mul(diff).div(center)));
		}
	}

	static {
		camtypes.put("cake", CakeCam.class);
	}

	static class FixedCakeCam extends DragCam {
		public final Coord border = new Coord(250, 150);
		private Coord size, center, diff;
		private boolean setoff = false;
		private Coord off = Coord.z;
		private Coord tgt = null;
		private Coord cur = off;
		private double vel = 0.2;

		public FixedCakeCam(double vel) {
			this.vel = Math.min(1.0, Math.max(0.1, vel));
		}

		public FixedCakeCam(String... args) {
			this(args.length < 1 ? 0.2 : Double.parseDouble(args[0]));
		}

		public void setpos(MapView mv, Gob player, Coord sz) {
			if (setoff) {
				borderize(mv, player, sz, border);
				off = mv.mc.add(player.position().inv());
				setoff = false;
			}
			if (mv.pmousepos != null
					&& (mv.pmousepos.x == 0 || mv.pmousepos.x == sz.x - 1
							|| mv.pmousepos.y == 0 || mv.pmousepos.y == sz.y - 1)) {
				if (size == null || !size.equals(sz)) {
					size = new Coord(sz);
					center = size.div(2);
					diff = center.sub(border);
				}
				if (player != null && mv.pmousepos != null)
					tgt = player
							.position()
							.sub(s2m(center.sub(mv.pmousepos).mul(diff)
									.div(center))).sub(player.position());
			} else {
				tgt = off;
			}
			cur = cur.add(tgt.sub(cur).mul(vel));
			mv.mc = player.position().add(cur);
		}

		public void moved(MapView mv) {
			setoff = true;
		}

		public void reset() {
			off = new Coord();
		}
	}

	static {
		camtypes.put("fixedcake", FixedCakeCam.class);
	}

	@SuppressWarnings("serial")
	private class Loading extends Exception {
	}

	private static Camera makecam(Class<? extends Camera> ct, String... args)
			throws ClassNotFoundException {
		try {
			try {
				Constructor<? extends Camera> cons = ct
						.getConstructor(String[].class);
				return (cons.newInstance(new Object[] { args }));
			} catch (IllegalAccessException e) {
			} catch (NoSuchMethodException e) {
			}
			try {
				return (ct.newInstance());
			} catch (IllegalAccessException e) {
			}
		} catch (InstantiationException e) {
			throw (new Error(e));
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof RuntimeException)
				throw ((RuntimeException) e.getCause());
			throw (new RuntimeException(e));
		}
		throw (new ClassNotFoundException(
				"No valid constructor found for camera " + ct.getName()));
	}

	private static Camera restorecam() {
		Class<? extends Camera> ct = camtypes.get(Utils.getpref("defcam",
				"border"));
		if (ct == null)
			return (new BorderCam());
		String[] args = (String[]) Utils.deserialize(Utils.getprefb("camargs",
				null));
		if (args == null)
			args = new String[0];
		try {
			return (makecam(ct, args));
		} catch (ClassNotFoundException e) {
			return (new BorderCam());
		}
	}

	public MapView(Coord c, Coord sz, Widget parent, Coord mc, int playergob) {
		super(c, sz, parent);
		isui = false;
		this.mc = mc;
		this.playergob = playergob;
		this.cam = restorecam();
		setcanfocus(true);
		glob = ui.sess.glob;
		map = glob.map;
		mask = new ILM(MainFrame.getPhysicalInnerSize(), glob.oc);
		radiuses = new HashMap<String, Integer>();
		terobjradiuses = new HashMap<String, Integer>();
		//terobjradiuses.put("");
		radiuses.put("gfx/terobjs/mining/minesupport", 100);
		radiuses.put("gfx/terobjs/bhive", 150);
		radiuses.put("gfx/terobjs/bhived", 150);
		BreadcrumbTrail.reset();

	}

	public void resetcam() {
		if (cam != null) {
			cam.reset();
		}
	}

	// kerri
	public int getTileFix(Coord tileCoord) {
		int r = map.gettilen(tileCoord);
		return r;
	}

	public static Coord m2s(Coord c) {
		return (new Coord((c.x * 2) - (c.y * 2), c.x + c.y));
	}

	public static Coord s2m(Coord c) {
		return (new Coord((c.x / 4) + (c.y / 2), (c.y / 2) - (c.x / 4)));
	}

	static Coord viewoffset(Coord sz, Coord vc) {
		return (m2s(vc).inv().add(sz.div(2)));
	}

	public void grab(Grabber grab) {
		this.grab = grab;
	}

	public void release(Grabber grab) {
		if (this.grab == grab)
			this.grab = null;
	}

	private Gob gobatpos(Coord c) {
		for (Sprite.Part d : obscured) {
			Gob gob = (Gob) d.owner;
			if (gob == null)
				continue;
			if (d.checkhit(c.add(gob.sc.inv())))
				return (gob);
		}
		for (Sprite.Part d : clickable) {
			Gob gob = (Gob) d.owner;
			if (gob == null)
				continue;
			if (d.checkhit(c.add(gob.sc.inv())))
				return (gob);
		}
		return (null);
	}

	public static class CropInfo {
		public final String name;
		public final int stage;
		public final int stages;

		public CropInfo(String name, int stage, int stages) {
			this.name = name;
			this.stage = stage;
			this.stages = stages;
		}
	}

	public static class FlowerMenuTargetInfo {
		public final String name;
		public final CropInfo crop;

		public FlowerMenuTargetInfo(String name, CropInfo crop) {
			this.name = name;
			this.crop = crop;
		}
	}

	static String flowerMenuOptionLabel(String option,
			FlowerMenuTargetInfo target) {
		if (target == null)
			return option;
		if (option.equalsIgnoreCase("Pick"))
			return "Pick - " + target.name;
		CropInfo crop = target.crop;
		if ((crop == null) || !option.equalsIgnoreCase("Harvest"))
			return option;
		StringBuilder label = new StringBuilder("Harvest - ");
		label.append(crop.name);
		if ((crop.stage >= 0) && (crop.stages > 1)
				&& (crop.stage < crop.stages)) {
			label.append(" (Stage ").append(crop.stage + 1);
			label.append('/').append(crop.stages).append(')');
		}
		return label.toString();
	}

	private static String displayBaseName(String baseName) {
		String[] words = baseName.split("[-_]");
		StringBuilder name = new StringBuilder();
		for (String word : words) {
			if (word.length() == 0)
				continue;
			if (name.length() > 0)
				name.append(' ');
			name.append(word.substring(0, 1).toUpperCase(Locale.ENGLISH));
			name.append(word.substring(1));
		}
		return (name.length() > 0) ? name.toString() : "Object";
	}

	static String cropDisplayName(String resourceName) {
		if (resourceName == null)
			return null;
		String prefix = "gfx/terobjs/plants/";
		if (!resourceName.startsWith(prefix))
			return null;
		String baseName = resourceName.substring(prefix.length());
		if (baseName.length() == 0)
			return "Crop";
		if (baseName.equals("wine"))
			return "Grapevine";
		return displayBaseName(baseName);
	}

	static String targetDisplayName(String resourceName, String tooltip) {
		if ((tooltip != null) && (tooltip.trim().length() > 0))
			return tooltip.trim();
		String cropName = cropDisplayName(resourceName);
		if (cropName != null)
			return cropName;
		if (resourceName == null)
			return null;
		int separator = resourceName.lastIndexOf('/');
		String baseName = (separator < 0) ? resourceName : resourceName
				.substring(separator + 1);
		return displayBaseName(baseName);
	}

	private CropInfo cropInfo(Gob gob) {
		if (gob == null)
			return null;
		Resource resource = gob.getres();
		if (resource == null)
			return null;
		String cropName = cropDisplayName(resource.name);
		if (cropName == null)
			return null;
		Resource.Tooltip tooltip = resource.layer(Resource.tooltip);
		if ((tooltip != null) && (tooltip.t != null)
				&& (tooltip.t.trim().length() > 0))
			cropName = tooltip.t.trim();

		int stage = -1;
		int stages = 0;
		ResDrawable drawable = gob.getattr(ResDrawable.class);
		if (drawable != null) {
			drawable.init();
			if (drawable.spr instanceof GrowingPlant) {
				GrowingPlant plant = (GrowingPlant) drawable.spr;
				stage = plant.stage();
				stages = plant.stages();
			}
		}
		return new CropInfo(cropName, stage, stages);
	}

	private FlowerMenuTargetInfo flowerMenuTargetInfo(Gob gob) {
		if (gob == null)
			return null;
		Resource resource = gob.getres();
		if (resource == null)
			return null;
		CropInfo crop = cropInfo(gob);
		Resource.Tooltip tooltip = resource.layer(Resource.tooltip);
		String tooltipText = (tooltip == null) ? null : tooltip.t;
		String name = targetDisplayName(resource.name, tooltipText);
		return (name == null) ? null : new FlowerMenuTargetInfo(name, crop);
	}

	private void rememberFlowerMenuTarget(Gob target) {
		flowerMenuTarget = target;
		flowerMenuTargetTime = System.currentTimeMillis();
	}

	private void rememberQualitySurveyTarget(Gob target, Coord tile) {
		qualitySurveyTarget = target;
		qualitySurveyTargetTile = (tile == null) ? null : new Coord(tile);
		qualitySurveyTargetTime = System.currentTimeMillis();
	}

	private void clearQualitySurveyTarget() {
		qualitySurveyTarget = null;
		qualitySurveyTargetTile = null;
		qualitySurveyTargetTime = 0;
	}

	private String terrainSurveyType(Coord tile) {
		return (tile == null) ? null : QualitySurveyStore.terrainType(map.gettileres(tile));
	}

	private void armDigQualitySurvey(Coord tile) {
		String terrain = (tile == null) ? null : map.gettileres(tile);
		/* Terrain cannot reliably identify soil versus clay (grass and shallow
		 * water clay nodes are both normal cases). The returned item resolves it. */
		if ((terrain == null) || (MiniMap.isPrimaryInterior())) return;
		MiniMap.armQualitySurvey(map, tile, QualitySurveyStore.SOIL_OR_CLAY);
	}

	/* Arm only after the player selected a qualifying server flower action.
	 * This avoids attributing a cancelled menu or another action to the prior
	 * right-click target. */
	public void armQualitySurveyForFlowerAction(String action) {
		MiniMap.clearQualitySurveyPending();
		if ((qualitySurveyTargetTime == 0) || ((System.currentTimeMillis() - qualitySurveyTargetTime) > 10000)) {
			clearQualitySurveyTarget();
			return;
		}
		String type = null;
		Coord tile = qualitySurveyTargetTile;
		if ("pick".equalsIgnoreCase(action) && isLikelyForage(qualitySurveyTarget)) {
			type = QualitySurveyStore.FORAGE;
			tile = qualitySurveyTarget.position().div(tileSize);
		} else if ("dig".equalsIgnoreCase(action)) {
			clearQualitySurveyTarget();
			armDigQualitySurvey(tile);
			return;
		} else if ("collect".equalsIgnoreCase(action)) {
			type = terrainSurveyType(tile);
			if (!QualitySurveyStore.WATER.equals(type)) type = null;
		} else if (("fish".equalsIgnoreCase(action) || "fishing".equalsIgnoreCase(action))
				&& QualitySurveyStore.WATER.equals(terrainSurveyType(tile))) {
			/* A catch is recorded only after the server accepted a Fish action on
			 * an actual water tile. Item.observe additionally requires the result
			 * resource itself to be fish. */
			type = QualitySurveyStore.FISH;
		}
		clearQualitySurveyTarget();
		if ((type != null) && !MiniMap.isPrimaryInterior()) MiniMap.armQualitySurvey(map, tile, type);
	}

	public void cancelQualitySurveyFlowerAction() {
		MiniMap.clearQualitySurveyPending();
		clearQualitySurveyTarget();
	}

	private boolean isLikelyForage(Gob gob) {
		String name = (gob == null) ? "" : gob.resname().toLowerCase(Locale.US);
		return name.contains("/herbs/") || name.contains("/herb/") ||
				name.contains("/forage/") || name.contains("mushroom");
	}

	public FlowerMenuTargetInfo consumeFlowerMenuTargetInfo() {
		Gob target = flowerMenuTarget;
		long targetTime = flowerMenuTargetTime;
		flowerMenuTarget = null;
		flowerMenuTargetTime = 0;
		if ((target == null)
				|| ((System.currentTimeMillis() - targetTime) > 10000))
			return null;
		return flowerMenuTargetInfo(target);
	}

	public CropInfo consumeFlowerMenuCropInfo() {
		FlowerMenuTargetInfo target = consumeFlowerMenuTargetInfo();
		return (target == null) ? null : target.crop;
	}

	public boolean mousedown(Coord c, int button) {
		setfocus(this);
		Coord c0 = c;
		c = new Coord((int) (c.x / getScale()), (int) (c.y / getScale()));
		Gob hit = gobatpos(c);
		if (button == 3) {
			/* Every candidate starts clean: a cancelled/failed action must never
			 * leave an older target armed for a later item result. */
			MiniMap.clearQualitySurveyPending();
			clearQualitySurveyTarget();
			if (Config.quickDepositToCart && (hit != null))
				armPendingCartDeposit(null);
		}
		// A fresh normal click supersedes an active route. Preserve the explicit
		// Ctrl+Shift-right path-interaction gesture long enough to replace it.
		if ((path != null) && !((button == 3) && ui.modctrl && ui.modshift))
			clear_pf_path();
		//Kerri
		if(objectSelecting)
		{
			objectUnderMouse = hit;
			objectSelecting = false;
			return true;
		}
		Coord mc = s2m(c.add(viewoffset(sz, this.mc).inv()));
		boolean digCursor = false;
		if (button == 1) {
			/* Normal Dig is a MenuGrid cursor followed by a left map click, not a
			 * flower-menu option. Replace any older candidate before considering it. */
			MiniMap.clearQualitySurveyPending();
			clearQualitySurveyTarget();
			String cursor = ((ui.root == null) || (ui.root.cursor == null)) ? null : ui.root.cursor.name;
			if ("gfx/hud/curs/dig".equals(cursor)) {
				digCursor = true;
				Coord tile = mc.div(tileSize);
				armDigQualitySurvey(tile);
			}
		}
		if (button == 3) {
			rememberFlowerMenuTarget(hit);
			rememberQualitySurveyTarget(hit, mc.div(tileSize));
		}
		if (grab != null) {
			try {
				grab.mmousedown(mc, button);
				return true;
			} catch (GrabberException e) {
			}
		}

		if ((cam != null) && cam.click(this, c, mc, button)) {
			/* Nothing */
		} else if (plob != null) {
			Gob gob = null;
			for (Gob g : plob)
				gob = g;
			wdgmsg("place", gob.rc, button, ui.modflags());
		} else {
			//Kerri
			if (button == 1 && ui.modctrl && ui.modmeta) {
				KerriUtils.addOL(hit);
			}
			//Kerri
			if(button == 1 && ui.modctrl && ui.modshift) {
				if(hit != null)
					hit.setDrawOlay(!hit.getDrawOlay());
			}
			// PF
			if (button == 3 && ui.modctrl && ui.modshift && !isInBoat()) {
				clear_pf_path();
				rememberFlowerMenuTarget(null);
				clearQualitySurveyTarget();
				if (hit == null)
					path_interact_object = null;
				else
					path_interact_object = hit;
				path = APXUtils._pf_find_path(mc, path_interact_object != null ? path_interact_object.id : 0);
				begin_pf_path(path, path_interact_object);
				update_pf_moving();
				return true;
			}
			/* Keep normal right-clicks as immediate world interactions (doors,
			 * items, and flower menus). Plain left-click is the pathfinding move
			 * gesture; modifiers and placement mode retain their native behavior. */
			if ((button == 1) && (ui.modflags() == 0) && !digCursor) {
				/* A new player movement command supersedes any queued local move and
				 * is sent immediately below, so the server retargets from the current
				 * position rather than finishing the old route first. */
				glob.oc.movequeue.clear();
				moveto = null;
				if (!isPushingPlow() && !isInBoat()) {
					Coord destination = Config.assign_to_tile ? tilify(mc) : mc;
					path = APXUtils._pf_find_path(destination, 0);
					if ((path != null) && (path.size() > 1)) {
						boolean directFinal = APXUtils._pf_is_safe_segment(
								path.get(path.size() - 2), destination);
						if (directFinal)
							path.set(path.size() - 1, new Coord(destination));
						begin_pf_path(path, null);
						path_final_exact = directFinal;
						update_pf_moving();
						return true;
					}
					/* The local map can be incomplete or the point can be unreachable.
					 * Preserve the server's normal click handling in that case. */
					clear_pf_path();
				}
				/* Plowing has server-side movement constraints. Let the native direct
				 * click handle it instead of issuing a queued route. */
				if (isPushingPlow())
					clear_pf_path();
			}
			if (hit == null) {
				if (button == 1 && ui.modshift) {
					glob.oc.enqueue(mc);
					glob.oc.checkqueue();
				} else {
					glob.oc.movequeue.clear();
					if (Config.assign_to_tile) {
						mc = tilify(mc);
					}
					wdgmsg("click", c0, mc, button, ui.modflags());
				}
			} else {
				//Kerri: option
				if (ui.modmeta && Config.objectBlink)
					ui.chat.getawnd().wdgmsg("msg", "@$[" + hit.id + "]");
				if (Config.assign_to_tile) {
					mc = tilify(mc);
				}
				wdgmsg("click", c0, mc, button, ui.modflags(), hit.id,
						hit.position());
			}
		}
		return (true);
	}

	public boolean mouseup(Coord c, int button) {
		c = new Coord((int) (c.x / getScale()), (int) (c.y / getScale()));
		Coord mc = s2m(c.add(viewoffset(sz, this.mc).inv()));
		if (grab != null) {
			try {
				grab.mmouseup(mc, button);
				return (true);
			} catch (GrabberException e) {
			}
		}
		if ((cam != null) && cam.release(this, c, mc, button)) {
			return (true);
		} else {
			return (true);
		}
	}

	public void mousemove(Coord c) {
		c = new Coord((int) (c.x / getScale()), (int) (c.y / getScale()));
		this.pmousepos = c;
		Coord mc = s2m(c.add(viewoffset(sz, this.mc).inv()));
		this.mousepos = mc;
		mouseAtTile = tilify(mousepos); //Kerri
		Collection<Gob> plob = this.plob;
		if (cam != null)
			cam.move(this, c, mc);

		if (grab != null) {
			try {
				grab.mmousemove(mc);
				return;
			} catch (GrabberException e) {
			}
		}
		if (plob != null) {
			Gob gob = null;
			for (Gob g : plob)
				gob = g;
			boolean plontile = this.plontile ^ ui.modshift;
			gob.move(plontile ? tilify(mc) : mc);
		}
		//Kerri
		if(pmousepos != null)
			objectUnderMouse = gobatpos(c);
		else
			objectUnderMouse = null;
		if(objectUnderMouse == null) {
			tip = null;
			tips = null;
		}
	}
	
	String tips;
	Text tip = null;
	public Object tooltip(Coord c,boolean again){
		int mode = Config.showObjectContext ? KnowledgeBase.detailMode(ui) : 0;
		String next = KnowledgeBase.objectTooltip(objectUnderMouse, mode);
		if ((tips == null) ? (next != null) : !tips.equals(next)) {
			tips = next;
			tip = (next == null) ? null : RichText.render(next, 320);
		}
		return(tip);
	}

	public boolean mousewheel(Coord c, int amount) {
		if (!Config.zoom)
			return false;
		si = Math.min(8, Math.max(0, si - amount));
		setScale(scales[si]);
		return (true);
	}

	public void move(Coord mc) {
		this.mc = mc;
	}

	public static Coord tilify(Coord c) {
		c = c.div(tileSize);
		c = c.mul(tileSize);
		c = c.add(tileSize.div(2));
		return (c);
	}
	
	public static Coord tilefy_ns(Coord c) {
		c = c.div(tileSize);
		c = c.mul(tileSize);
		return (c);
	}

	private void unflashol() {
		for (int i = 0; i < visol.length; i++) {
			if ((olflash & (1 << i)) != 0)
				visol[i]--;
		}
		olflash = 0;
		olftimer = 0;
	}

	public void uimsg(String msg, Object... args) {
		if (msg == "move") {
			move((Coord) args[0]);
			if (cam != null)
				cam.moved(this);
		} else if (msg == "flashol") {
			unflashol();
			olflash = (Integer) args[0];
			for (int i = 0; i < visol.length; i++) {
				if ((olflash & (1 << i)) != 0)
					visol[i]++;
			}
			olftimer = System.currentTimeMillis() + (Integer) args[1];
		} else if (msg == "place") {
			Collection<Gob> plob = this.plob;
			if (plob != null) {
				this.plob = null;
				glob.oc.lrem(plob);
			}
			plob = new LinkedList<Gob>();
			plontile = (Integer) args[2] != 0;
			Gob gob = new Gob(glob, plontile ? tilify(mousepos) : mousepos);
			Resource res = Resource.load((String) args[0], (Integer) args[1]);
			gob.setattr(new ResDrawable(gob, res));
			plob.add(gob);
			glob.oc.ladd(plob);
			if (args.length > 3) {
				plrad = (Integer) args[3];
				radiuses.put(res.name, plrad);
				if (res.name.equals("gfx/terobjs/bhive"))
					radiuses.put("gfx/terobjs/bhived", plrad);
			}
			this.plob = plob;
		} else if (msg == "unplace") {
			if (plob != null)
				glob.oc.lrem(plob);
			plob = null;
			plrad = 0;
		} else if (msg == "polowner") {
			String o = ((String) args[0]).intern();
			if (o != polowner) {
				if (o.length() == 0) {
					if (this.polowner != null)
						this.polownert = polownertf.render("Leaving "
								+ this.polowner);
					this.polowner = null;
				} else {
					this.polowner = o;
					this.polownert = polownertf.render("Entering " + o);
				}
				this.polchtm = System.currentTimeMillis();
			}
		} else {
			super.uimsg(msg, args);
		}
	}

	public void enol(int... overlays) {
		for (int ol : overlays)
			visol[ol]++;
	}

	public void disol(int... overlays) {
		for (int ol : overlays)
			visol[ol]--;
	}

	@SuppressWarnings("unused")
	public int gettilen(Coord tc) {
		int r = map.gettilen(tc);
		return (r);
	}

	private Tile getground(Coord tc) throws Loading {
		Tile r = map.getground(tc);
		if (r == null)
			throw (new Loading());
		return (r);
	}

	private Tile[] gettrans(Coord tc) throws Loading {
		Tile[] r = map.gettrans(tc);
		if (r == null)
			throw (new Loading());
		return (r);
	}

	private int getol(Coord tc) throws Loading {
		int ol = map.getol(tc);
		if (ol == -1)
			throw (new Loading());
		return (ol);
	}

	private void drawtile(GOut g, Coord tc, Coord sc) {
		Tile t;

		try {
			t = getground(tc);
			// t = gettile(tc).ground.pick(0);
			g.image(t.tex(), sc);
			// g.setColor(FlowerMenu.pink);
			// Utils.drawtext(g, Integer.toString(t.i), sc);
			if (Config.tileAA) {
				for (Tile tt : gettrans(tc)) {
					g.image(tt.tex(), sc);
				}
			}
		} catch (Loading e) {
		}
	}

	// JSBot
	public void map_move(int obj_id, Coord offset) {
		Coord oc, sc;
		int btn = 1;
		int modflags = 0;
		Gob gob;
		synchronized (glob.oc) {
			gob = glob.oc.getgob(obj_id);
		}
		if (gob == null)
			return;
		sc = new Coord((int) Math.round(Math.random() * 200 + sz.x / 2 - 100),
				(int) Math.round(Math.random() * 200 + sz.y / 2 - 100));
		oc = gob.position();
		oc = oc.add(offset);
		wdgmsg("click", sc, oc, btn, modflags, obj_id, oc);
	}

	public void map_move_step(int x, int y) {
		Gob pgob;
		int btn = 1;
		int modflags = 0;
		synchronized (glob.oc) {
			pgob = glob.oc.getgob(playergob);
		}
		if (pgob == null)
			return;
		Coord mc = tilify(pgob.position());
		Coord offset = new Coord(x, y).mul(tileSize);
		mc = mc.add(offset);
		wdgmsg("click", JSBotUtils.getCenterScreenCoord(), mc, btn, modflags);
	}

	public void map_place(int x, int y, int btn, int mod) {
		if (plob != null) {
			Gob pgob;
			synchronized (glob.oc) {
				pgob = glob.oc.getgob(playergob);
			}
			if (pgob == null)
				return;
			Coord mc = tilify(pgob.position());
			Coord offset = new Coord(x, y).mul(tileSize);
			mc = mc.add(offset);
			wdgmsg("place", mc, btn, mod);
		}
	}

	public void map_click(int x, int y, int btn, int mod) {
		Gob pgob;
		synchronized (glob.oc) {
			pgob = glob.oc.getgob(playergob);
		}
		if (pgob == null)
			return;
		Coord mc = tilify(pgob.position());
		Coord offset = new Coord(x, y).mul(tileSize);
		mc = mc.add(offset);
		wdgmsg("click", JSBotUtils.getCenterScreenCoord(), mc, btn, mod);
	}
	
	public void map_click_ntf(int x, int y, int btn, int mod) {
		Gob pgob;
		synchronized (glob.oc) {
			pgob = glob.oc.getgob(playergob);
		}
		if (pgob == null)
			return;
		Coord mc = pgob.position();
		Coord offset = new Coord(x, y).mul(tileSize);
		mc = mc.add(offset);
		wdgmsg("click", JSBotUtils.getCenterScreenCoord(), mc, btn, mod);
	}

	public void map_abs_click(int x, int y, int btn, int mod) {
		Coord mc = new Coord(x, y);
		wdgmsg("click", JSBotUtils.getCenterScreenCoord(), mc, btn, mod);
	}

	public void map_interact_click(int x, int y, int mod) {
		Gob pgob;
		synchronized (glob.oc) {
			pgob = glob.oc.getgob(playergob);
		}
		if (pgob == null)
			return;
		Coord mc = tilify(pgob.position());
		Coord offset = new Coord(x, y).mul(tileSize);
		mc = mc.add(offset);
		wdgmsg("itemact", JSBotUtils.getCenterScreenCoord(), mc, mod);
	}

	public void map_abs_interact_click(int x, int y, int mod) {
		Gob pgob;
		synchronized (glob.oc) {
			pgob = glob.oc.getgob(playergob);
		}
		if (pgob == null)
			return;
		Coord mc = new Coord(x, y);
		wdgmsg("itemact", JSBotUtils.getCenterScreenCoord(), mc, mod);
	}

	public void map_interact_click(int id, int mod) {
		Gob pgob, gob;
		synchronized (glob.oc) {
			pgob = glob.oc.getgob(playergob);
			gob = glob.oc.getgob(id);
		}
		if (pgob == null || gob == null)
			return;
		Coord mc = gob.position();
		wdgmsg("itemact", JSBotUtils.getCenterScreenCoord(), mc, mod, id, mc);
	}

	public void drop_thing(int mod) {
		wdgmsg("drop", mod);
	}

	private void drawol(GOut g, Coord tc, Coord sc) {
		int ol;
		int i;
		double w = 2;

		try {
			ol = getol(tc);

			if (ol == 0)
				return;
			Coord c1 = sc;
			Coord c2 = sc.add(m2s(new Coord(0, tileSize.y)));
			Coord c3 = sc.add(m2s(new Coord(tileSize.x, tileSize.y)));
			Coord c4 = sc.add(m2s(new Coord(tileSize.x, 0)));
			for (i = 0; i < olc.length; i++) {
				if (olc[i] == null)
					continue;
				if (((ol & (1 << i)) == 0) || (visol[i] < 1))
					continue;
				Color fc = new Color(olc[i].getRed(), olc[i].getGreen(),
						olc[i].getBlue(), 32);
				g.chcolor(fc);
				g.frect(c1, c2, c3, c4);
				if (((ol & ~getol(tc.add(new Coord(-1, 0)))) & (1 << i)) != 0) {
					g.chcolor(olc[i]);
					g.line(c2, c1, w);
				}
				if (((ol & ~getol(tc.add(new Coord(0, -1)))) & (1 << i)) != 0) {
					g.chcolor(olc[i]);
					g.line(c1.add(1, 0), c4.add(1, 0), w);
				}
				if (((ol & ~getol(tc.add(new Coord(1, 0)))) & (1 << i)) != 0) {
					g.chcolor(olc[i]);
					g.line(c4.add(1, 0), c3.add(1, 0), w);
				}
				if (((ol & ~getol(tc.add(new Coord(0, 1)))) & (1 << i)) != 0) {
					g.chcolor(olc[i]);
					g.line(c3, c2, w);
				}
			}
			g.chcolor(Color.WHITE);
		} catch (Loading e) {
		}
	}

	private void drawradius(GOut g, Coord c, int radius) {
		g.fellipse(c, new Coord((int) (radius * 4 * Math.sqrt(0.5)),
				(int) (radius * 2 * Math.sqrt(0.5))));
	}

	private void drawPlaceObjectEffect(GOut g) {
		if (plob == null)
			return;
		Gob gob = null;
		for (Gob tg : plob)
			gob = tg;
		if (gob.sc == null)
			return;
		if (plrad > 0) {
			String name = gob.resname();
			g.chcolor(0, 0, 0, 32);
			synchronized (glob.oc) {
				for (Gob tg : glob.oc)
					if ((tg.sc != null) && (tg.resname() == name))
						drawradius(g, tg.sc, plrad);
			}
			g.chcolor();
		}
	}

	private void drawObjectRadius(GOut g) {
		String name;
		g.chcolor(0, 255, 0, 32);
		synchronized (glob.oc) {
			for (Gob tg : glob.oc) {
				name = tg.resname();
				if (radiuses.containsKey(name) && (tg.sc != null)) {
					drawradius(g, tg.sc, radiuses.get(name));
				}
			}
		}
		g.chcolor();
	}

	private void drawBeastRadius(GOut g) {
		String name;
		g.chcolor(255, 0, 0, 96);
		synchronized (glob.oc) {
			for (Gob tg : glob.oc) {
				name = tg.resname();
				if ((tg.sc != null)
						&& (name.indexOf("/cdv") < 0)
						&& ((name.indexOf("kritter/boar") >= 0) || (name
								.indexOf("kritter/bear") >= 0))) {
					drawradius(g, tg.sc, 100);
				}
			}
		}
		g.chcolor();
	}

	private void drawcurioses(GOut g) {
		try {
			String name;
			g.chcolor(255, 153, 51, 96);
			ui.minimappanel.mm.profits.clear();
			ui.minimappanel.mm.hherbs.clear();
			ui.minimappanel.mm.players.clear();
			synchronized (glob.oc) {
				for (Gob tg : glob.oc) {
					name = tg.resname();
					if (tg.sc != null) {
						int hit = 0;
						for (Pair<String, Color> pp : Config.minimap_highlights) {
							if (name.contains(pp.fst) && !name.contains("/cdv")) {
								if (name.contains("herbs") && Config.drawIcons) {
									String tmp;
									if (name.contains("mussel")) {
										// маму Лофтара ебал кароч
										tmp = name.replace("terobjs/herbs", "invobjs");
									} else {
										tmp = name.replace("terobjs", "invobjs");
									}
									ui.minimappanel.mm.hherbs.add(new Pair<Coord, String>(tg.rc, tmp));
								}
								else
									ui.minimappanel.mm.profits.add(new Pair<Coord, Color>(tg.rc, pp.snd));
								hit++;
							}
						}
						if (name.contains("gfx/borka")) {
							if (!Config.show_minimap_players)
								continue;
							// Fucking rabbits and chikens
							Avatar ava = tg.getattr(Avatar.class);
							if (ava != null) {
								if (!ava.isPlayer())
									continue;
							}
							boolean included = true;
							synchronized (ui.sess.glob.party.memb) {
								for (Party.Member m : ui.sess.glob.party.memb.values()) {
									if (m.gobid == tg.id)
										included = false;
								}
							}
							if (!included)
								continue;
							KinInfo k = tg.getattr(KinInfo.class);
							if (k != null) {
								boolean villager = (k.type & 2) != 0;
								if (villager && k.type <= 0) {
									ui.minimappanel.mm.players.add(new Pair<Coord, Color>(tg.rc, BuddyWnd.gc[1]));
								} else {
									ui.minimappanel.mm.players.add(new Pair<Coord, Color>(tg.rc, BuddyWnd.gc[k.group]));
								}
							} else {
								ui.minimappanel.mm.players.add(new Pair<Coord, Color>(tg.rc, Color.decode("0xFF0000")));
							}
						}
						if (hit > 0 && Config.show_minimap_profits && tg.getres().name.contains("herbs"))
							drawradius(g, tg.sc, 15);
					}
				}
			}
			g.chcolor();
		} catch (Exception e) {

		}
	}

	private void drawtracking(GOut g) {
		g.chcolor(255, 0, 255, 128);
		Coord oc = viewoffset(sz, mc);
		for (int i = 0; i < TrackingWnd.instances.size(); i++) {
			TrackingWnd wnd = TrackingWnd.instances.get(i);
			if (wnd.pos == null) {
				continue;
			}
			Coord c = m2s(wnd.pos).add(oc);
			g.fellipse(c, new Coord(100, 50), wnd.a1, wnd.a2);
		}
		g.chcolor();
	}

	private boolean follows(Gob g1, Gob g2) {
		Following flw;
		if ((flw = g1.getattr(Following.class)) != null) {
			if (flw.tgt() == g2)
				return (true);
		}
		if ((flw = g2.getattr(Following.class)) != null) {
			if (flw.tgt() == g1)
				return (true);
		}
		return (false);
	}

	private List<Sprite.Part> findobsc() {
		ArrayList<Sprite.Part> obsc = new ArrayList<Sprite.Part>();
		if (obscgob == null)
			return (obsc);
		boolean adding = false;
		for (Sprite.Part p : clickable) {
			Gob gob = (Gob) p.owner;
			if (gob == null)
				continue;
			if (gob == obscgob) {
				adding = true;
				continue;
			}
			if (follows(gob, obscgob))
				continue;
			if (adding && obscpart.checkhit(gob.sc.add(obscgob.sc.inv())))
				obsc.add(p);
		}
		return (obsc);
	}

	private void drawols(GOut g, Coord sc) {
		synchronized (map.grids) {
			for (Coord gc : map.grids.keySet()) {
				Grid grid = map.grids.get(gc);
				for (Overlay lol : grid.ols) {
					int id = getolid(lol.mask);
					if (visol[id] < 1) {
						continue;
					}
					Coord c0 = gc.mul(cmaps);
					drawol2(g, id, c0.add(lol.c1), c0.add(lol.c2), sc);
				}
			}
		}
		for (Overlay lol : map.ols) {
			int id = getolid(lol.mask);
			if (visol[id] < 1) {
				continue;
			}
			drawol2(g, id, lol.c1, lol.c2, sc);
		}
		g.chcolor();
	}

	private int getolid(int mask) {
		for (int i = 0; i < olc.length; i++) {
			if ((mask & (1 << i)) != 0) {
				return i;
			}
		}
		return 0;
	}

	private void drawol2(GOut g, int id, Coord c0, Coord cx, Coord sc) {
		cx = cx.add(1, 1);
		Coord c1 = m2s(c0.mul(tileSize)).add(sc);
		Coord c2 = m2s(new Coord(c0.x, cx.y).mul(tileSize)).add(sc);
		Coord c3 = m2s(cx.mul(tileSize)).add(sc);
		Coord c4 = m2s(new Coord(cx.x, c0.y).mul(tileSize)).add(sc);

		Color fc = new Color(olc[id].getRed(), olc[id].getGreen(),
				olc[id].getBlue(), 32);
		g.chcolor(fc);
		g.frect(c1, c2, c3, c4);
		cx = cx.sub(1, 1);
		drawline(g, new Coord(0, -1), c0.y, id, c0, cx, sc);
		drawline(g, new Coord(0, 1), cx.y, id, c0, cx, sc);
		drawline(g, new Coord(1, 0), cx.x, id, c0, cx, sc);
		drawline(g, new Coord(-1, 0), c0.x, id, c0, cx, sc);
		g.chcolor();
	}

	private void drawline(GOut g, Coord d, int med, int id, Coord c0, Coord cx,
			Coord sc) {

		Coord m = d.abs();
		Coord r = m.swap();
		Coord off = m.mul(med).add(d.add(m).div(2));
		int min = c0.mul(r).sum();
		int max = cx.mul(r).sum() + 1;
		boolean t = false;
		int begin = min;
		int ol = 1 << id;
		g.chcolor(olc[id]);
		for (int i = min; i <= max; i++) {
			Coord c = r.mul(i).add(m.mul(med)).add(d);
			int ol2;
			try {
				ol2 = getol(c);
			} catch (Loading e) {
				ol2 = ol;
			}
			if (t) {
				if (((ol2 & ol) != 0) || i == max) {
					t = false;
					Coord cb = m2s(tileSize.mul(r.mul(begin).add(off))).add(sc);
					Coord ce = m2s(tileSize.mul(r.mul(i).add(off))).add(sc);
					g.line(cb, ce, 2);
				}
			} else {
				if ((ol2 & ol) == 0) {
					t = true;
					begin = i;
				}
			}
		}
	}

	public void drawmap(GOut g) {
		int x, y, i;
		int stw, sth;
		Coord oc, tc, ctc, sc;

		if (Config.profile)
			curf = prof.new Frame();
		//Kerri
		Coord mouseTilePos = Coord.z;
		if (mousepos != null && Config.assign_to_tile)
			mouseTilePos = mouseAtTile.div(tileSize);
		stw = (tileSize.x * 4) - 2;
		sth = tileSize.y * 2;
		oc = viewoffset(sz, mc);
		tc = mc.div(tileSize);
		tc.x += -(sz.x / (2 * stw)) - (sz.y / (2 * sth)) - 2;
		tc.y += (sz.x / (2 * stw)) - (sz.y / (2 * sth));
		for (y = 0; y < (sz.y / sth) + 2; y++) {
			for (x = 0; x < (sz.x / stw) + 3; x++) {
				for (i = 0; i < 2; i++) {
					ctc = tc.add(new Coord(x + y, -x + y + i));
					sc = m2s(ctc.mul(tileSize)).add(oc);
					sc.x -= tileSize.x * 2;
					drawtile(g, ctc, sc);
					sc.x += tileSize.x * 2;
					if (!Config.newclaim) {
						drawol(g, ctc, sc);
					}
					if (mousepos != null && Config.assign_to_tile)
						if (mouseTilePos.y == ctc.y && mouseTilePos.x == ctc.x)
							drawTileSelection(g, ctc, sc);
				}
			}
		}//for
		//Kerri for JSRect
		if(JSBotUtils.rectOffset != null && JSBotUtils.rectSize != null){
			if(JSBotUtils.rectSize.x > 0 && JSBotUtils.rectSize.y > 0){
				for (y = 0; y < (sz.y / sth) + 2; y++) {
					for (x = 0; x < (sz.x / stw) + 3; x++) {
						for (i = 0; i < 2; i++) {
							ctc = tc.add(new Coord(x + y, -x + y + i));
							sc = m2s(ctc.mul(tileSize)).add(oc);
							sc.x -= tileSize.x * 2;
							sc.x += tileSize.x * 2;
							if( JSBotUtils.MyCoord().div(tileSize).add(JSBotUtils.rectOffset).x == ctc.x && 
									JSBotUtils.MyCoord().div(tileSize).add(JSBotUtils.rectOffset).y == ctc.y)
								drawJSRect(g, sc, JSBotUtils.rectSize);
						}
					}
				}
			}
		}//for
		
		/* PF Draw Map */
		if (draw_pf_map) {
			int[][] map = APXUtils.PF_Map();
			int dim = APXUtils.PF_Dim();
			Coord qcoord;
			for (int vx = 0; vx < dim; vx++) {
				for (int vy = 0; vy < dim; vy++) {
					int type = map[vx][vy];
					Color qcolor = type == APXUtils.PF_MAP_CELL_FREE ? Color.green : Color.red;
					qcoord = APXUtils._pf_map2real_stored(new Coord(vx, vy));
					drawQuad(g, m2s(qcoord.sub(MCache.tileSize.div(2))).add(oc), qcolor);
				}
			}
		}

		if (Config.newclaim) {
			drawols(g, oc);
		}
		if (Config.grid) {
			g.chcolor(new Color(40, 40, 40));
			Coord c1, c2, d;
			d = tc.mul(tileSize);
			int hy = (sz.y / sth) * tileSize.y;
			int hx = (sz.x / stw) * tileSize.x;
			c1 = d.add(0, 0);
			c2 = d.add(5 * hx / 2, 0);
			for (y = d.y - hy; y < d.y + hy; y = y + tileSize.y) {
				c1.y = y;
				c2.y = c1.y;
				g.line(m2s(c1).add(oc), m2s(c2).add(oc), 1);
			}
			c1 = d.add(0, -hy);
			c2 = d.add(0, hy);

			for (x = d.x; x < d.x + 5 * hx / 2; x = x + tileSize.x) {
				c1.x = x;
				c2.x = c1.x;
				g.line(m2s(c1).add(oc), m2s(c2).add(oc), 1);
			}
			g.chcolor();
			//minimap grid
			g.chcolor(new Color(0, 0, 255));
			synchronized (map.grids) {
				for (Grid grid : map.grids.values()) {
					Coord gg1 = grid.gc.mul(100, 100).mul(tileSize);
					Coord gg2 = grid.gc.mul(100, 100).add(100, 0).mul(tileSize);
					Coord gg3 = grid.gc.mul(100, 100).add(100, 100).mul(tileSize);
					Coord gg4 = grid.gc.mul(100, 100).add(0, 100).mul(tileSize);
					g.line(m2s(gg1).add(oc), m2s(gg2).add(oc), 2);
					g.line(m2s(gg2).add(oc), m2s(gg3).add(oc), 2);
					g.line(m2s(gg3).add(oc), m2s(gg4).add(oc), 2);
					g.line(m2s(gg4).add(oc), m2s(gg1).add(oc), 2);
				}
			}
			g.chcolor();
		}
		if (curf != null)
			curf.tick("map");

		if (Config.showRadius)
			drawObjectRadius(g);
		else
			drawPlaceObjectEffect(g);

		if (Config.showBeast) {
			drawBeastRadius(g);
		}
		drawcurioses(g);
		drawtracking(g);

		if (curf != null)
			curf.tick("plobeff");

		final List<Sprite.Part> sprites = new ArrayList<Sprite.Part>();
		ArrayList<Speaking> speaking = new ArrayList<Speaking>();
		ArrayList<KinInfo> kin = new ArrayList<KinInfo>();
		class GobMapper implements Sprite.Drawer {
			Gob cur = null;
			Sprite.Part.Effect fx = null;
			int szo = 0;

			public void chcur(Gob cur) {
				this.cur = cur;
				GobHealth hlt = cur.getattr(GobHealth.class);
				fx = null;
				if (hlt != null)
					fx = hlt.getfx();
				if (cur.highlight != null) {
					long t = System.currentTimeMillis() - cur.highlight.time;
					if (t > 5000) {
						cur.highlight = null;
					}
				}
				if (cur.highlight != null) {
					fx = cur.highlight;
				}
				Following flw = cur.getattr(Following.class);
				szo = 0;
				if (flw != null)
					szo = flw.szo;
			}

			public void addpart(Sprite.Part p) {
				p.effect = fx;
				if ((p.ul.x >= sz.x) || (p.ul.y >= sz.y) || (p.lr.x < 0)
						|| (p.lr.y < 0))
					return;
				sprites.add(p);
				p.owner = cur;
				p.szo = szo;
			}
		}
		
		//Kerri
		if(KerriUtils.iObj.size() > 0) {
			g.chcolor(20, 20, 250, 108);
			synchronized (glob.oc) {
				for (Gob gob : glob.oc) {
					if(KerriUtils.iObj.contains(Integer.valueOf(gob.id))) {
						Resource.Neg neg = gob.getneg();
						if (neg == null) {
							continue;
						}
						if ((neg.bs.x > 0) && (neg.bs.y > 0)) {
							Coord c1 = gob.position().add(neg.bc);
							Coord c2 = c1.add(neg.bs);
							g.frect(m2s(c1).add(oc), m2s(new Coord(c2.x, c1.y))
									.add(oc), m2s(c2).add(oc),
									m2s(new Coord(c1.x, c2.y)).add(oc));
						}
					}
				}
			}
			g.chcolor();
		}
		
		if(ui.modctrl) {
			g.chcolor(20, 20, 250, 108);
			synchronized (glob.oc) {
				for (Gob gob : glob.oc) {
					if(gob.id == 0) {
						Resource.Neg neg = gob.getneg();
						if (neg == null) {
							continue;
						}
						if ((neg.bs.x > 0) && (neg.bs.y > 0)) {
							Coord c1 = gob.position().add(neg.bc);
							Coord c2 = c1.add(neg.bs);
							g.frect(m2s(c1).add(oc), m2s(new Coord(c2.x, c1.y))
									.add(oc), m2s(c2).add(oc),
									m2s(new Coord(c1.x, c2.y)).add(oc));
						}
						break;
					}
				}
			}
			g.chcolor();
		}

		if (Config.showHidden && Config.hide) {
			if(!Config.aimacrazyman)
				g.chcolor(Config.hideColor);
			synchronized (glob.oc) {
				for (Gob gob : glob.oc) {
					if(Config.aimacrazyman) {
						Random r = new Random();
						g.chcolor(r.nextInt(20), r.nextInt(20), r.nextInt(20), r.nextInt(20));
					}
					Resource res = gob.getres();
					if (!Config.hide_all) {
						if (!gob.hide || ((res != null) && (res.skiphighlight)))
							continue;
					}
					if (res.name != null && res.name.contains("gfx/arch/sign")) {
						Coord c1 = gob.position();
						Coord c2 = c1.add(new Coord(2, 2));
						g.frect(m2s(c1).add(oc), m2s(new Coord(c2.x, c1.y))
								.add(oc), m2s(c2).add(oc),
								m2s(new Coord(c1.x, c2.y)).add(oc));
					}
					Resource.Neg neg = gob.getneg();
					if (neg == null) {
						continue;
					}
					if ((neg.bs.x > 0) && (neg.bs.y > 0)) {
						Coord c1 = gob.position().add(neg.bc);
						Coord c2 = c1.add(neg.bs);
						g.frect(m2s(c1).add(oc), m2s(new Coord(c2.x, c1.y))
								.add(oc), m2s(c2).add(oc),
								m2s(new Coord(c1.x, c2.y)).add(oc));
					}
				}
			}
			g.chcolor();
		}

		GobMapper drawer = new GobMapper();
		synchronized (glob.oc) {
			for (Gob gob : glob.oc) {
				Integer tmp /*lol*/ = Integer.valueOf(gob.id);
				if(ui.modctrl && gob.id == 0) continue;
				if(KerriUtils.iObj.contains(tmp)) continue;
				Resource res = gob.getres();
				if(res != null) {
					if(res.name.contains("gfx/terobjs/clue")) {
						//scent checker
						LinkedList<Gob.Overlay> olays = (LinkedList<haven.Gob.Overlay>) gob.ols;
						int scent = olays.get(0).sdt.uint16K();
						if(KerriUtils.isAssault(scent) && Config.hideAsslt) continue;
						if(KerriUtils.isTheft(scent) && Config.hideTheft) continue;
						if(KerriUtils.isTresspass(scent) && Config.hideTressp) continue;
						if(KerriUtils.isVandalism(scent) && Config.hideVand) continue;
						if(KerriUtils.isBattery(scent) && Config.hideBatt) continue;
						if(KerriUtils.isMurder(scent) && Config.hideMurd) continue;
					}
				}
				drawer.chcur(gob);
				Coord dc = m2s(gob.position()).add(oc);
				gob.sc = dc;
				gob.drawsetup(drawer, dc, sz);
				Speaking s = gob.getattr(Speaking.class);
				if (s != null)
					speaking.add(s);
				KinInfo k = gob.getattr(KinInfo.class);
				if (k != null)
					kin.add(k);
			}
			if (curf != null)
				curf.tick("setup");
			Collections.sort(sprites, Sprite.partidcmp);
			{
				Sprite.Part[] clickable = new Sprite.Part[sprites.size()];
				for (int o = 0, u = clickable.length - 1; o < clickable.length; o++, u--)
					clickable[u] = sprites.get(o);
				this.clickable = clickable;
			}
			if (curf != null)
				curf.tick("sort");
			//Kerri
			if(pmousepos != null)
				objectUnderMouse = gobatpos(pmousepos);
			else
				objectUnderMouse = null;
			obscured = findobsc();
			if (curf != null)
				curf.tick("obsc");
			for (Sprite.Part part : sprites) {
				if (part.effect != null)
					part.draw(part.effect.apply(g));
				else
					part.draw(g);
			}
			for (Sprite.Part part : obscured) {
				GOut g2 = new GOut(g);
				GobHealth hlt;
				if ((part.owner != null)
						&& (part.owner instanceof Gob)
						&& ((hlt = ((Gob) part.owner).getattr(GobHealth.class)) != null))
					g2.chcolor(255, (int) (hlt.asfloat() * 255), 0, 255);
				else
					g2.chcolor(255, 255, 0, 255);
				part.drawol(g2);
			}

			if (curf != null)
				curf.tick("draw");
			g.image(mask, Coord.z, sz);
			long now = System.currentTimeMillis();
			RootWidget.names_ready = (RootWidget.screenshot && Config.sshot_nonames);
			if (!RootWidget.names_ready) {
				for (KinInfo k : kin) {
					Tex t = k.rendered();
					Coord gc = k.gob.sc;
					String name = k.gob.resname();
					boolean isother = name.contains("hearth")
							|| name.contains("skeleton");
					if (gc.isect(Coord.z, sz)) {
						if (k.seen == 0)
							k.seen = now;
						int tm = (int) (now - k.seen);
						Color show = null;
						boolean auto = (k.type & 1) == 0;
						if ((isother && Config.showOtherNames)
								|| (!isother && Config.showNames)
								|| (k.gob == objectUnderMouse)) {
							show = Color.WHITE;
						} else if (auto && (tm < 7500)) {
							show = Utils.clipcol(255, 255, 255,
									255 - ((255 * tm) / 7500));
						}
						if (show != null) {
							g.chcolor(show);
							g.image(t, gc.add(-t.sz().x / 2, -40 - t.sz().y));
							g.chcolor();
						}
					} else {
						k.seen = 0;
					}
				}
			}
			for (Speaking s : speaking) {
				s.draw(g, s.gob.sc.add(s.off));
			}
			if (curf != null) {
				curf.tick("aux");
				curf.fin();
				curf = null;
			}
			if (Config.show_gob_health) {
				for (Gob gob : glob.oc) {
					GobHealth hlt = gob.getattr(GobHealth.class);
					if (hlt != null) {
						int percent = (int) (hlt.asfloat() * 100);
						if (percent < 100) {
							g.atext(Integer.toString(percent) + "%", gob.sc, 0,
									1);
						}
					}
				}
			}
		}
	}

	public void drawarrows(GOut g) {
		Coord oc = viewoffset(sz, mc);
		Coord hsz = sz.div(2); //half size
		double ca = -Coord.z.angle(hsz);
		for (Party.Member partyMember : glob.party.memb.values()) {
			// Gob gob = glob.oc.getgob(id);
			Coord memberCoords = partyMember.getc();
			if (memberCoords == null)
				continue;
			Coord sc = m2s(memberCoords).add(oc);
			if (!sc.isect(Coord.z, sz)) {
				double a = -hsz.angle(sc);
				Coord ac;
				if ((a > ca) && (a < -ca)) {
					ac = new Coord(sz.x, hsz.y - (int) (Math.tan(a) * hsz.x));
				} else if ((a > -ca) && (a < Math.PI + ca)) {
					ac = new Coord(hsz.x
							- (int) (Math.tan(a - Math.PI / 2) * hsz.y), 0);
				} else if ((a > -Math.PI - ca) && (a < ca)) {
					ac = new Coord(hsz.x
							+ (int) (Math.tan(a + Math.PI / 2) * hsz.y), sz.y);
				} else {
					ac = new Coord(0, hsz.y + (int) (Math.tan(a) * hsz.x));
				}
				g.chcolor(partyMember.col);
				Coord bc = ac.add(Coord.sc(a, -10));
				g.line(bc, bc.add(Coord.sc(a, -40)), 2);
				g.line(bc, bc.add(Coord.sc(a + Math.PI / 4, -10)), 2);
				g.line(bc, bc.add(Coord.sc(a - Math.PI / 4, -10)), 2);
				g.chcolor(Color.WHITE);
			}
		}
	}

	private void checkplmove() {
		Gob pl;
		long now = System.currentTimeMillis();
		if ((playergob >= 0) && ((pl = glob.oc.getgob(playergob)) != null)
				&& (pl.sc != null)) {
			Coord plp = pl.position();
			if ((plfpos == null) || !plfpos.equals(plp)) {
				lastmove = now;
				plfpos = plp;
				if ((obscpart != null)
						&& !obscpart.checkhit(pl.sc.add(obscgob.sc.inv()))) {
					obscpart = null;
					obscgob = null;
				}
			} else if (now - lastmove > 500) {
				for (Sprite.Part p : clickable) {
					Gob gob = (Gob) p.owner;
					if ((gob == null) || (gob.sc == null))
						continue;
					if (gob == pl)
						break;
					if (p.checkhit(pl.sc.add(gob.sc.inv()))) {
						obscpart = p;
						obscgob = gob;
						break;
					}
				}
			}
		}
		player_moving = ((now - lastmove) < 400);
	}

	private void checkmappos() {
		if (cam == null)
			return;
		Coord sz = this.sz;
		SlenHud slen = ui.slenhud;
		if (slen != null)
			sz = sz.add(0, -slen.foldheight());
		Gob player = glob.oc.getgob(playergob);
		if (player != null)
			cam.setpos(this, player, sz);
	}
	
	//Kerri	
	Coord lastWASD = new Coord(0, 0);
	public void update(long dt)
	{
		if (Config.use_wasd) {
			Coord newWASD = APXUtils.updateWASD();
			if (!newWASD.equals(lastWASD)) {
				lastWASD = newWASD;
				map_click_ntf(lastWASD.x, lastWASD.y, 1, 0);
			}
		}
		
		Gob pl = glob.oc.getgob(playergob);
		Coord myCurrentCoord = null;
		if(pl != null)
		{
			myCurrentCoord = pl.position();
			if(myCurrentCoord != null && myLastCoord != null)
				if(myCurrentCoord.dist(myLastCoord) > 30*11 && cam != null) {
					cam.reset();
				}
		}
		myLastCoord = myCurrentCoord;
		BreadcrumbTrail.record(myCurrentCoord);
		
		update_pf_moving();
	}
	
	//Kerri
	private void drawTileSelection(GOut g, Coord tc, Coord sc) {
		Coord c1 = sc;
		Coord c2 = sc.add(m2s(new Coord(0, tileSize.y)));
		Coord c3 = sc.add(m2s(new Coord(tileSize.x, tileSize.y)));
		Coord c4 = sc.add(m2s(new Coord(tileSize.x, 0)));
		Color cl = Color.red;
		g.chcolor(new Color(cl.getRed(), cl.getGreen(), cl.getBlue(), 32));
		g.frect(c1, c2, c3, c4);
		g.chcolor(cl);
		g.line(c2, c1, 2);
		g.line(c1.add(1, 0), c4.add(1, 0), 2);
		g.line(c4.add(1, 0), c3.add(1, 0), 2);
		g.line(c3, c2, 2);
		g.chcolor();
	}
	
	private void drawJSRect(GOut g, Coord sc, Coord size) {
		Coord c1 = sc;
		Coord c2 = sc.add(m2s(new Coord(0, tileSize.y * size.y)));
		Coord c3 = sc.add(m2s(new Coord(tileSize.x * size.x, tileSize.y * size.y)));
		Coord c4 = sc.add(m2s(new Coord(tileSize.x * size.x, 0)));
		Color cl = Color.cyan;
		g.chcolor(new Color(cl.getRed(), cl.getGreen(), cl.getBlue(), 32));
		g.frect(c1, c2, c3, c4);
		g.chcolor(cl);
		g.line(c2, c1, 2);
		g.line(c1.add(1, 0), c4.add(1, 0), 2);
		g.line(c4.add(1, 0), c3.add(1, 0), 2);
		g.line(c3, c2, 2);
		g.chcolor();
	}
	
	public void drawQuad(GOut g, Coord sc, Color c) {
		Coord c1 = sc;
		Coord c2 = sc.add(m2s(new Coord(0, tileSize.y)));
		Coord c3 = sc.add(m2s(new Coord(tileSize.x, tileSize.y)));
		Coord c4 = sc.add(m2s(new Coord(tileSize.x, 0)));
		g.chcolor(c);
		g.frect(c1, c2, c3, c4);
		g.chcolor();
	}

	public void draw(GOut og) {
		if(!Config.render_enable)
		{
			Coord requl = mc.add(-500, -500).div(tileSize).div(cmaps);
			Coord reqbr = mc.add(500, 500).div(tileSize).div(cmaps);
			Coord cgc = new Coord(0, 0);
			for (cgc.y = requl.y; cgc.y <= reqbr.y; cgc.y++) {
				for (cgc.x = requl.x; cgc.x <= reqbr.x; cgc.x++) {
					if (map.grids.get(cgc) == null)
						map.request(new Coord(cgc));
				}
			}
			map.sendreqs();
			checkplmove();
			return;
		}
		if (moveto != null) {
			wdgmsg("click", moveto, moveto, 1, 0);
			moveto = null;
		}
		hsz = MainFrame.getInnerSize();
		sz = hsz.mul(1 / getScale());
		Coord physicalSize = MainFrame.getPhysicalInnerSize();
		if (!mask.sz().equals(physicalSize)) {
			mask.dispose();
			mask = new ILM(physicalSize, glob.oc);
		}
		mask.renderScale = Config.getActiveUIScale() * getScale();
		GOut g = og.reclip(Coord.z, sz);
		g.gl.glPushMatrix();
		g.scale(getScale());
		checkmappos();
		Coord requl = mc.add(-500, -500).div(tileSize).div(cmaps);
		Coord reqbr = mc.add(500, 500).div(tileSize).div(cmaps);
		Coord cgc = new Coord(0, 0);
		for (cgc.y = requl.y; cgc.y <= reqbr.y; cgc.y++) {
			for (cgc.x = requl.x; cgc.x <= reqbr.x; cgc.x++) {
				if (map.grids.get(cgc) == null)
					map.request(new Coord(cgc));
			}
		}
		long now = System.currentTimeMillis();
		if ((olftimer != 0) && (olftimer < now))
			unflashol();
		map.sendreqs();
		checkplmove();
		// try {
		if (((mask.amb = glob.amblight) == null) || Config.nightvision)
			mask.amb = new Color(0, 0, 0, 0);
		drawmap(g);
		
		// movement highlight
		try {
			if (Config.showpath) {
				Coord oc = viewoffset(sz, mc);
				Coord pc, cc;
				synchronized (glob.oc) {
					for (Gob gob : glob.oc) {
						if(gob == null) continue;
						if(gob.sc == null) continue;
						if (!(gob.isPlayer() || gob.resname().contains("boat")) && !Config.showpathAll) continue;
						Moving move_attr = gob.getattr(Moving.class);
						if(move_attr == null) continue;
						if (move_attr instanceof LinMove) {
							LinMove lin_move = (LinMove) move_attr;
							pc = m2s(lin_move.t).add(oc);
							if(gob.id == playergob)
						    	g.chcolor(Color.GREEN);
						    else
						    	g.chcolor(Color.WHITE);
							g.line(gob.sc, pc, 2);
							if (gob.id == playergob) {
								for(Coord c:glob.oc.movequeue){
								    cc = m2s(c).add(oc);
								    g.line(pc, cc, 2);
								    pc = cc;
								}
							}
						}
					}
				}
				g.chcolor();
				//Gob player = glob.oc.getgob(playergob);
				/*if (player != null) {
					Moving m = player.getattr(Moving.class);
					g.chcolor(Color.GREEN);
					if ((m != null) && (m instanceof LinMove)) {
						LinMove lm = (LinMove) m;
						pc = m2s(lm.t).add(oc);
						g.line(player.sc, pc, 2);
						for (Coord c : glob.oc.movequeue) {
							cc = m2s(c).add(oc);
							g.line(pc, cc, 2);
							pc = cc;
						}
					}
					g.chcolor();
				}*/
			}
		} catch (Exception ex) {
			
		}
		// ###############
		// PF 
		if (path != null && path_step < path.size()) {
			Coord oc = viewoffset(sz, mc);
			g.chcolor(Color.green);
			Coord from = myLastCoord;
			if (from == null)
				from = path.get(path_step);
			for (int i = path_step; i < path.size(); i++) {
				Coord to = path.get(i);
				g.line(m2s(from).add(oc), m2s(to).add(oc), 2);
				from = to;
			}
			g.chcolor();
		}
		drawarrows(g);
		g.chcolor(Color.WHITE);

		// } catch(Loading l) {
		// String text = "Loading...";
		// g.chcolor(Color.BLACK);
		// g.frect(Coord.z, sz);
		// g.chcolor(Color.WHITE);
		// g.atext(text, sz, 0.5, 0.5);
		// }
		long poldt = now - polchtm;
		if ((polownert != null) && (poldt < 6000)) {
			int a;
			if (poldt < 1000)
				a = (int) ((255 * poldt) / 1000);
			else if (poldt < 4000)
				a = 255;
			else
				a = (int) ((255 * (2000 - (poldt - 4000))) / 2000);
			g.chcolor(255, 255, 255, a);
			g.aimage(polownert.tex(), sz.div(2), 0.5, 0.5);
			g.chcolor();
		}
		g.gl.glPopMatrix();
		//Kerri. debug
		if (Config.showDebug) {
			int ay = 120;
			int margin = 15;
			g.atext("FPS: " + MainFrame.havenPanel.fps, new Coord(10, ay), 0, 1);
			ay = ay + margin;
			if (objectUnderMouse != null) {
				g.atext("Object id under mouse: " + objectUnderMouse.id + " Coords: "
						+ objectUnderMouse.position(), new Coord(10, ay), 0, 1);
				ay = ay + margin;
				g.atext("Resource name: "+objectUnderMouse.resname()+
						" BLOB: "+objectUnderMouse.GetBlob(0), new Coord(10, ay), 0, 1);
				ay = ay + margin;
			} else {
				g.atext("Object id under mouse: NULL", new Coord(10, ay), 0, 1);
				ay = ay + margin;
			}
			if (mousepos != null) {
				g.atext("Mouse map pos: " + mousepos.toString() + " Tile: " + getTileFix(mousepos.div(tileSize))  + "TilifNS: " + tilefy_ns(mousepos).toString(), new Coord(10, ay), 0, 1);
				ay = ay + margin;
				g.atext("Tile coord: " + mouseAtTile.toString(), new Coord(10, ay), 0, 1);
				ay = ay + margin;
			}
			g.atext("Current cursor: " + UI.instance.root.cursor.name.replace("gfx/hud/curs/", ""),
					new Coord(10, ay), 0, 1);
			ay = ay + margin;
			g.atext("Player id: " + playergob, new Coord(10, ay), 0, 1);
		}//debug
		if (Config.dbtext)
			g.atext(mc.toString(), new Coord(10, 560), 0, 1);
		super.draw(og);
	}

	public boolean drop(Coord cc, Coord ul) {
		wdgmsg("drop", ui.modflags());
		return (true);
	}

	public boolean iteminteract(Coord cc, Coord ul) {
		return iteminteract(cc, ul, null);
	}

	public boolean iteminteract(Coord cc, Coord ul, Item heldItem) {
		/* Item use is a new collection candidate even when it ultimately is not a
		 * surveyable one; discard any prior pending action first. */
		MiniMap.clearQualitySurveyPending();
		clearQualitySurveyTarget();
		Coord cc0 = cc;
		cc = new Coord((int) (cc.x / getScale()), (int) (cc.y / getScale()));
		Gob hit = gobatpos(cc);
		if (Config.quickDepositToCart &&
				quickDepositToCart(hit, heldItem))
			return (true);
		Coord mc = s2m(cc.add(viewoffset(sz, this.mc).inv()));
		String surveyType = null;
		if (QualitySurveyStore.isWaterContainer(heldItem)) {
			surveyType = terrainSurveyType(mc.div(tileSize));
			String targetName = (hit == null) ? "" : hit.resname().toLowerCase(Locale.US);
			if (!QualitySurveyStore.WATER.equals(surveyType)
					&& (targetName.contains("water") || targetName.contains("well")))
				surveyType = QualitySurveyStore.WATER;
			if (!QualitySurveyStore.WATER.equals(surveyType)) surveyType = null;
		}
		if ((surveyType != null) && !MiniMap.isPrimaryInterior())
			MiniMap.armQualitySurvey(map, mc.div(tileSize), surveyType);
		if (Config.assign_to_tile) mc = tilify(mc);
		if (hit == null)
			wdgmsg("itemact", cc0, mc, ui.modflags());
		else
			wdgmsg("itemact", cc0, mc, ui.modflags(), hit.id, hit.position());
		return (true);
	}

	private boolean quickDepositToCart(Gob hit, Item held) {
		if (held == null)
			held = heldItem();
		if (held == null) {
			clearPendingCartDeposit();
			return false;
		}
		Inventory cart = singleOpenCartInventory();
		if (cart == null) {
			if (hit != null)
				armPendingCartDeposit(held);
			return false;
		}
		if (!isCart(hit)) {
			clearPendingCartDeposit();
			return false;
		}
		clearPendingCartDeposit();
		Coord slot = cart.firstFreeSlot(held);
		if (slot == null)
			return false;
		dropIntoCartSlot(cart, slot);
		return true;
	}

	private static boolean isCart(Gob gob) {
		if (gob == null)
			return false;
		for (String resourceName : gob.resnames()) {
			String name = resourceName.toLowerCase(Locale.ENGLISH);
			if (name.equals("gfx/kritter/cart") ||
					name.startsWith("gfx/kritter/cart/"))
				return true;
		}
		return false;
	}

	private void armPendingCartDeposit(Item held) {
		pendingCartDeposit = true;
		pendingCartDepositItem = held;
		pendingCartDepositUntil = System.currentTimeMillis() +
				CART_DEPOSIT_TIMEOUT;
	}

	public void completePendingCartDeposit(Window cartWindow) {
		if (!pendingCartDeposit)
			return;
		if (!Config.quickDepositToCart ||
				(System.currentTimeMillis() > pendingCartDepositUntil)) {
			clearPendingCartDeposit();
			return;
		}
		List<Avaview> avatarSlots = new ArrayList<Avaview>();
		collectAvatarSlots(cartWindow, avatarSlots);
		if (!avatarSlots.isEmpty()) {
			clearPendingCartDeposit();
			for (Avaview slot : avatarSlots) {
				if (slot.isEmptySlot()) {
					slot.mousedown(slot.sz.div(2), 1);
					break;
				}
			}
			return;
		}

		List<Inventory> inventories = new ArrayList<Inventory>();
		collectVisibleInventories(cartWindow, inventories);
		if (inventories.size() == 1) {
			Item held = pendingCartDepositItem;
			if ((held == null) || !held.isDragging ||
					(held.ui != ui) || (ui.getId(held) < 0))
				held = heldItem();
			if (held == null)
				return;
			Coord slot = inventories.get(0).firstFreeSlot(held);
			clearPendingCartDeposit();
			if (slot != null)
				dropIntoCartSlot(inventories.get(0), slot);
		}
	}

	private static void dropIntoCartSlot(Inventory cart, Coord slot) {
		Coord point = slot.mul(Inventory.invSqSize);
		cart.drop(point, point);
	}

	private void clearPendingCartDeposit() {
		pendingCartDeposit = false;
		pendingCartDepositItem = null;
		pendingCartDepositUntil = 0;
	}

	private Item heldItem() {
		return findHeldItem(ui.root);
	}

	private static Item findHeldItem(Widget widget) {
		for (Widget child = widget.child; child != null; child = child.next) {
			if ((child instanceof Item) && ((Item) child).isDragging)
				return (Item) child;
			Item found = findHeldItem(child);
			if (found != null)
				return found;
		}
		return null;
	}

	private Inventory singleOpenCartInventory() {
		List<Inventory> found = new ArrayList<Inventory>();
		collectOpenCartInventories(ui.root, found);
		return (found.size() == 1) ? found.get(0) : null;
	}

	private static void collectOpenCartInventories(Widget widget,
			List<Inventory> found) {
		if (!widget.visible)
			return;
		if (widget instanceof Window) {
			Window window = (Window) widget;
			if ((window.cap != null) && window.cap.text.equals("Cart")) {
				collectVisibleInventories(window, found);
				return;
			}
		}
		for (Widget child = widget.child; child != null; child = child.next)
			collectOpenCartInventories(child, found);
	}

	private static void collectVisibleInventories(Widget widget,
			List<Inventory> found) {
		for (Widget child = widget.child; child != null; child = child.next) {
			if (!child.visible)
				continue;
			if (child instanceof Inventory)
				found.add((Inventory) child);
			else
				collectVisibleInventories(child, found);
		}
	}

	private static void collectAvatarSlots(Widget widget,
			List<Avaview> found) {
		for (Widget child = widget.child; child != null; child = child.next) {
			if (!child.visible)
				continue;
			if (child instanceof Avaview)
				found.add((Avaview) child);
			else
				collectAvatarSlots(child, found);
		}
	}

	private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();
	{
		cmdmap.put("cam", new Console.Command() {
			public void run(Console cons, String[] args) {
				if (args.length >= 2) {
					Class<? extends Camera> ct = camtypes.get(args[1]);
					String[] cargs = new String[args.length - 2];
					System.arraycopy(args, 2, cargs, 0, cargs.length);
					if (ct != null) {
						try {
							MapView.this.cam = makecam(ct, cargs);
							Utils.setpref("defcam", args[1]);
							Utils.setprefb("camargs", Utils.serialize(cargs));
						} catch (ClassNotFoundException e) {
							throw (new RuntimeException("no such camera: "
									+ args[1]));
						}
					} else {
						throw (new RuntimeException("no such camera: "
								+ args[1]));
					}
				}
			}
		});
		cmdmap.put("plol", new Console.Command() {
			public void run(Console cons, String[] args) {
				Indir<Resource> res = Resource.load(args[1]).indir();
				Message sdt;
				if (args.length > 2)
					sdt = new Message(0, Utils.hex2byte(args[2]));
				else
					sdt = new Message(0);
				Gob pl;
				if ((playergob >= 0)
						&& ((pl = glob.oc.getgob(playergob)) != null))
					pl.ols.add(new Gob.Overlay(-1, res, sdt));
			}
		});
	}

	public Map<String, Console.Command> findcmds() {
		return (cmdmap);
	}
	
	ArrayList<Coord> path;
	int path_step = 0;
	boolean path_moving = false;
	Gob path_interact_object = null;
	private static final long PF_STALL_TIMEOUT = 10000;
	private static final long PF_FORCED_RETRY_DELAY = 750;
	private static final int PF_ARRIVAL_TOLERANCE = 3;
	private Coord path_last_position;
	private long path_last_progress;
	private boolean path_force_next;
	private Coord path_force_target;
	private long path_force_retry_at;
	private long path_force_progress_at;
	private boolean path_force_retried;
	private boolean path_final_exact;
	boolean draw_pf_map = false;

	private void clear_pf_path() {
		path = null;
		path_step = 0;
		path_moving = false;
		path_interact_object = null;
		path_last_position = null;
		path_last_progress = 0;
		path_force_next = false;
		path_force_target = null;
		path_force_retry_at = 0;
		path_force_progress_at = 0;
		path_force_retried = false;
		path_final_exact = false;
	}

	private void begin_pf_path(ArrayList<Coord> route, Gob interactionTarget) {
		clear_pf_path();
		path = route;
		path_interact_object = interactionTarget;
		/* The first A* node is the current tile. Never walk back to its centre
		 * before taking the newly requested route. */
		if ((route != null) && !route.isEmpty())
			path_step = 1;
		if (myLastCoord != null)
			path_last_position = new Coord(myLastCoord);
		path_last_progress = System.currentTimeMillis();
		path_force_next = true;
	}

	private boolean isPushingPlow() {
		if (playergob < 0)
			return false;
		synchronized (glob.oc) {
			for (Gob gob : glob.oc) {
				if (gob.id == playergob)
					continue;
				Following following = gob.getattr(Following.class);
				if ((following == null) || (following.tgt != playergob))
					continue;
				for (String resourceName : gob.resnames()) {
					if (resourceName.startsWith("gfx/kritter/plow/"))
						return true;
				}
			}
		}
		return false;
	}

	/**
	 * A player seated in a boat is represented by the server as following the
	 * boat gob.  Pathfinding is inappropriate in that state: movement commands
	 * are interpreted relative to the vessel and can interfere with sailing.
	 */
	private boolean isInBoat() {
		if (playergob < 0)
			return false;
		Gob player;
		synchronized (glob.oc) {
			player = glob.oc.getgob(playergob);
		}
		if (player == null)
			return false;
		Following following = player.getattr(Following.class);
		if (following == null)
			return false;
		Gob target = following.tgt();
		if (target == null)
			return false;
		for (String resourceName : target.resnames()) {
			if ((resourceName != null) && resourceName.toLowerCase(Locale.US).contains("boat"))
				return true;
		}
		return false;
	}
	
	public void toggle_draw_pf() {
		if (!draw_pf_map) {
			draw_pf_map = true;
			APXUtils._pf_compute(0);
		} else {
			draw_pf_map = false;
		}
	}
	
	public int map_pf_move(Coord real_coord) {
		if (isInBoat()) {
			clear_pf_path();
			return -1;
		}
		ArrayList<Coord> route = APXUtils._pf_find_path(real_coord, 0);
		begin_pf_path(route, null);
		update_pf_moving();
		return path == null ? -1 : path.size();
	}
	
	public int map_pf_interact(int id) {
		if (isInBoat()) {
			clear_pf_path();
			return -1;
		}
		Gob pgob;
		synchronized (glob.oc) {
			pgob = glob.oc.getgob(id);
		}
		if (pgob == null)
			return -1;
		ArrayList<Coord> route = APXUtils._pf_find_path(pgob.position(), id);
		begin_pf_path(route, pgob);
		update_pf_moving();
		return path == null ? -1 : path.size();
	}
	
	public void update_pf_moving() {
		if (path == null)
			return;
		if (isPushingPlow() || isInBoat()) {
			clear_pf_path();
			return;
		}
		long now = System.currentTimeMillis();
		if (myLastCoord == null) {
			clear_pf_path();
			return;
		}
		if ((path_last_position == null) || !path_last_position.equals(myLastCoord)) {
			path_last_position = new Coord(myLastCoord);
			path_last_progress = now;
		}
		if ((now - path_last_progress) > PF_STALL_TIMEOUT) {
			clear_pf_path();
			return;
		}
		if ((path_force_target != null) && !path_force_retried
				&& (now >= path_force_retry_at)
				&& (path_last_progress <= path_force_progress_at)
				&& (myLastCoord.dist(path_force_target) > PF_ARRIVAL_TOLERANCE)) {
			/* A fresh movement command normally replaces the server target. Retry
			 * the same collision-checked waypoint once promptly if it was ignored. */
			wdgmsg("click", JSBotUtils.getCenterScreenCoord(),
					path_command_destination(path_force_target), 1, 0);
			path_force_retried = true;
			path_moving = false;
		}
		if (JSBotUtils.isMoving() && !path_force_next) return;
		
		if (path_step < path.size()) {
			if (myLastCoord.dist(path.get(path_step)) <= PF_ARRIVAL_TOLERANCE) {
				path_moving = false;
				path_step++;
				path_last_progress = now;
				path_force_target = null;
			}
		}
		if (path_moving && !path_force_next) return;
		if (path_step < path.size()) {
			path_moving = true;
			Coord togo = path.get(path_step);
			wdgmsg("click", JSBotUtils.getCenterScreenCoord(),
					path_command_destination(togo), 1, 0);
			if (path_force_next) {
				path_force_target = togo;
				path_force_retry_at = now + PF_FORCED_RETRY_DELAY;
				path_force_progress_at = path_last_progress;
			}
			path_force_next = false;
		} else {
			if (path_interact_object != null) {
				rememberFlowerMenuTarget(path_interact_object);
				wdgmsg("click", JSBotUtils.getCenterScreenCoord(), path_interact_object.position(), 3, 0, path_interact_object.id, path_interact_object.position());
			}
			clear_pf_path();
		}
	}

	private Coord path_command_destination(Coord waypoint) {
		if (path_final_exact && (path_step == path.size() - 1))
			return waypoint;
		return tilify(waypoint);
	}
	
	@Override
	public boolean keydown(KeyEvent ev) {
		if (ev.getKeyCode() == KeyEvent.VK_UP)
			APXUtils.wPressed = true;
		if (ev.getKeyCode() == KeyEvent.VK_LEFT)
			APXUtils.aPressed = true;
		if (ev.getKeyCode() == KeyEvent.VK_DOWN)
			APXUtils.sPressed = true;
		if (ev.getKeyCode() == KeyEvent.VK_RIGHT)
			APXUtils.dPressed = true;
		if (ev.getKeyCode() == KeyEvent.VK_RIGHT
				|| ev.getKeyCode() == KeyEvent.VK_DOWN
				|| ev.getKeyCode() == KeyEvent.VK_LEFT
				|| ev.getKeyCode() == KeyEvent.VK_UP)
			return true;
		else
			return false;
	}
	
	@Override
	public boolean keyup(KeyEvent ev) {
		if (ev.getKeyCode() == KeyEvent.VK_UP)
			APXUtils.wPressed = false;
		if (ev.getKeyCode() == KeyEvent.VK_LEFT)
			APXUtils.aPressed = false;
		if (ev.getKeyCode() == KeyEvent.VK_DOWN)
			APXUtils.sPressed = false;
		if (ev.getKeyCode() == KeyEvent.VK_RIGHT)
			APXUtils.dPressed = false;
		if (ev.getKeyCode() == KeyEvent.VK_RIGHT
				|| ev.getKeyCode() == KeyEvent.VK_DOWN
				|| ev.getKeyCode() == KeyEvent.VK_LEFT
				|| ev.getKeyCode() == KeyEvent.VK_UP)
			return true;
		else
			return false;
	}
}
