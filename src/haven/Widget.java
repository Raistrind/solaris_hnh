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

import java.util.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;


public class Widget {
	public UI ui;
	public Coord c, sz, hsz;
	public Widget next, prev;
	public Widget child;
	public Widget lchild;
	public Widget parent;
	boolean focustab = false, focusctl = false, hasfocus = false;
	public boolean visible = true;
	private boolean canfocus = false, autofocus = false;
	public boolean canactivate = false, cancancel = false;
	Widget focused;
	public Resource cursor = null;
	public Object tooltip = null;
	public boolean canhastrash = true;
	private Widget prevtt;
	public boolean isui = true;
	static Map<String, WidgetFactory> types = new TreeMap<String, WidgetFactory>();
	static Class<?>[] barda = { Img.class, TextEntry.class, MapView.class,
			FlowerMenu.class, Window.class, Button.class, Inventory.class,
			Item.class, Listbox.class, Makewindow.class, Chatwindow.class,
			Textlog.class, Equipory.class, IButton.class, Cal.class,
			Avaview.class, NpcChat.class, Label.class, Progress.class,
			VMeter.class, Partyview.class, MenuGrid.class, SlenHud.class,
			HWindow.class, CheckBox.class, Logwindow.class, MapMod.class,
			ISBox.class, ComMeter.class, Fightview.class, IMeter.class,
			GiveButton.class, Charlist.class, ComWin.class, CharWnd.class,
			BuddyWnd.class, ChatHW.class, Speedget.class, Bufflist.class
			};

	static {
		addtype("cnt", new WidgetFactory() {
			public Widget create(Coord c, Widget parent, Object[] args) {
				return (new Widget(c, (Coord) args[0], parent));
			}
		});
	}

	public static void initbardas() {
		try {
			for (Class<?> c : barda)
				Class.forName(c.getName(), true, c.getClassLoader());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			throw (new Error(e));
		}
	}

	public static void addtype(String name, WidgetFactory fct) {
		synchronized (types) {
			types.put(name, fct);
		}
	}

	public static WidgetFactory gettype(String name) {
		synchronized (types) {
			return (types.get(name));
		}
	}

	public Widget(UI ui, Coord c, Coord sz) {
		this.ui = ui;
		this.c = c;
		this.sz = sz;
	}

	public Widget(Coord c, Coord sz, Widget parent) {
		synchronized (parent.ui) {
			this.ui = parent.ui;
			this.c = c;
			this.sz = sz;
			this.parent = parent;
			link();
		}
	}

	public void link() {
		synchronized (ui) {
			if (parent.lchild != null)
				parent.lchild.next = this;
			if (parent.child == null)
				parent.child = this;
			this.prev = parent.lchild;
			parent.lchild = this;
		}
	}

	public void unlink() {
		synchronized (ui) {
			if (next != null)
				next.prev = prev;
			if (prev != null)
				prev.next = next;
			if (parent.child == this)
				parent.child = next;
			if (parent.lchild == this)
				parent.lchild = prev;
			next = null;
			prev = null;
		}
	}
	
	public void destroyAll() {
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			wdg.destroyAll();
		}
		unlink();
	}

	public Coord xlate(Coord c, boolean in) {
		return (c);
	}

	/** Scale applied to this widget and all of its descendants. */
	public double getDisplayScale() {
		return 1.0;
	}

	public Coord getDisplaySize() {
		return scaleSize(sz, getDisplayScale());
	}

	/** Returns true when this widget or one of its parents changes display scale. */
	public boolean hasDisplayScaling() {
		for (Widget widget = this; widget != null; widget = widget.parent) {
			if (widget.getDisplayScale() != 1.0)
				return true;
		}
		return false;
	}

	/** True when scaling can make this widget's saved position leave the screen. */
	protected boolean needsScaledBoundsCheck() {
		return (Config.getActiveUIScale() != 1) || (getDisplayScale() != 1.0);
	}

	/** Limit a requested element scale so its unscaled bounds fit its parent. */
	protected double fitDisplayScale(double desired, Coord bounds) {
		if ((parent == null) || (parent.sz.x < 1) || (parent.sz.y < 1)
				|| (bounds.x < 1) || (bounds.y < 1))
			return desired;
		double horizontal = parent.sz.x / (double) bounds.x;
		double vertical = parent.sz.y / (double) bounds.y;
		return Math.min(desired, Math.min(horizontal, vertical));
	}

	public static Coord scaleSize(Coord value, double scale) {
		return new Coord((int) Math.ceil(value.x * scale), (int) Math.ceil(value.y
				* scale));
	}

	protected Coord localToParent(Coord value) {
		double scale = getDisplayScale();
		return new Coord((int) Math.round(value.x * scale), (int) Math.round(value.y
				* scale));
	}

	protected Coord parentToLocal(Coord value) {
		double scale = getDisplayScale();
		return new Coord((int) Math.floor(value.x / scale), (int) Math.floor(value.y
				/ scale));
	}

	public Coord outerToRoot(Coord outer) {
		Coord scaled = localToParent(outer);
		if (parent == null)
			return scaled;
		return parent.outerToRoot(parent.xlate(c, true).add(scaled));
	}

	public Coord rootToOuter(Coord root) {
		if (parent == null)
			return parentToLocal(root);
		Coord inParent = parent.rootToOuter(root);
		return parentToLocal(inParent.sub(parent.xlate(c, true)));
	}

	public Coord rootpos() {
		/* Preserve the legacy coordinate path exactly for the normal 100% case. */
		if (!hasDisplayScaling()) {
			if (parent == null)
				return new Coord(0, 0);
			return xlate(parent.rootpos().add(c), true);
		}
		return outerToRoot(xlate(Coord.z, true));
	}

	public boolean hasparent(Widget w2) {
		for (Widget w = this; w != null; w = w.parent) {
			if (w == w2)
				return (true);
		}
		return (false);
	}

	public void gotfocus() {
		if (focusctl && (focused != null)) {
			focused.hasfocus = true;
			focused.gotfocus();
		}
	}

	public void destroy() {
		if (canfocus)
			setcanfocus(false);
	}

	public void lostfocus() {
		if (focusctl && (focused != null)) {
			focused.hasfocus = false;
			focused.lostfocus();
		}
	}

	public void setfocus(Widget w) {
		if (focusctl) {
			if (w != focused) {
				Widget last = focused;
				focused = w;
				if (last != null)
					last.hasfocus = false;
				w.hasfocus = true;
				if (last != null)
					last.lostfocus();
				w.gotfocus();
				if ((ui != null) && ui.rwidgets.containsKey(w))
					wdgmsg("focus", ui.rwidgets.get(w));
			}
			if (parent != null)
				parent.setfocus(this);
		} else {
			parent.setfocus(w);
		}
	}

	public void setcanfocus(boolean canfocus) {
		this.autofocus = this.canfocus = canfocus;
		if (parent != null) {
			if (canfocus) {
				parent.newfocusable(this);
			} else {
				parent.delfocusable(this);
			}
		}
	}

	public void newfocusable(Widget w) {
		if (focusctl) {
			if (focused == null)
				setfocus(w);
		} else {
			parent.newfocusable(w);
		}
	}

	public void delfocusable(Widget w) {
		if (focusctl) {
			if (focused == w)
				findfocus();
		} else {
			parent.delfocusable(w);
		}
	}

	private void findfocus() {
		/* XXX: Might need to check subwidgets recursively */
		focused = null;
		for (Widget w = lchild; w != null; w = w.prev) {
			if (w.autofocus) {
				focused = w;
				focused.hasfocus = true;
				w.gotfocus();
				break;
			}
		}
	}

	public void setfocusctl(boolean focusctl) {
		if (this.focusctl = focusctl) {
			findfocus();
			setcanfocus(true);
		}
	}

	public void setfocustab(boolean focustab) {
		if (focustab && !focusctl)
			setfocusctl(true);
		this.focustab = focustab;
	}

	public void uimsg(String msg, Object... args) {
		if (msg == "tabfocus") {
			setfocustab(((Integer) args[0] != 0));
		} else if (msg == "act") {
			canactivate = (Integer) args[0] != 0;
		} else if (msg == "cancel") {
			cancancel = (Integer) args[0] != 0;
		} else if (msg == "autofocus") {
			autofocus = (Integer) args[0] != 0;
		} else if (msg == "focus") {
			Widget w = ui.widgets.get((Integer) args[0]);
			if (w != null) {
				if (w.canfocus)
					setfocus(w);
			}
		} else if (msg == "curs") {
			if (args.length == 0)
				cursor = null;
			else
				cursor = Resource.load((String) args[0], (Integer) args[1]);
		} else {
			System.err.println("Unhandled widget message: " + msg);
		}
	}

	public void binded() {

	}

	public void wdgmsg(String msg, Object... args) {
		wdgmsg(this, msg, args);
	}

	public void wdgmsg(Widget sender, String msg, Object... args) {
		if (parent == null)
			ui.wdgmsg(sender, msg, args);
		else
			parent.wdgmsg(sender, msg, args);
	}

	public void draw(GOut g) {
		Widget next;

		for (Widget wdg = child; wdg != null; wdg = next) {
			next = wdg.next;
			if (!wdg.visible || (!ui.root.visible && wdg.isui))
				continue;
			Coord cc = xlate(wdg.c, true);
			drawChild(g, wdg, cc);
		}
	}

	private void drawChild(GOut g, Widget child, Coord childPosition) {
		double scale = child.getDisplayScale();
		if (scale == 1.0) {
			child.draw(g.reclip(childPosition, child.sz));
			return;
		}
		/* Scale submitted vertices while retaining logical clipping coordinates. */
		Coord origin = g.ul.add(childPosition);
		GOut childGraphics = g.reclip(childPosition, child.sz).scaled(scale,
				origin);
		child.draw(childGraphics);
	}

	private static Coord hitSize(Widget widget) {
		Coord size = (widget.hsz == null) ? widget.sz : widget.hsz;
		return scaleSize(size, widget.getDisplayScale());
	}

	private static Coord childPoint(Coord point, Coord childPosition,
			Widget child) {
		return child.parentToLocal(point.sub(childPosition));
	}

	public boolean mousedown(Coord c, int button) {
		for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
			if (!wdg.visible)
				continue;
			Coord cc = xlate(wdg.c, true);
			if (wdg.getDisplayScale() == 1.0) {
				if (c.isect(cc, (wdg.hsz == null) ? wdg.sz : wdg.hsz)
						&& wdg.mousedown(c.add(cc.inv()), button))
					return true;
				continue;
			}
			if (c.isect(cc, hitSize(wdg))) {
				if (wdg.mousedown(childPoint(c, cc, wdg), button)) {
					return (true);
				}
			}
		}
		return (false);
	}

	public boolean mouseup(Coord c, int button) {
		for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
			if (!wdg.visible)
				continue;
			Coord cc = xlate(wdg.c, true);
			if (wdg.getDisplayScale() == 1.0) {
				if (c.isect(cc, (wdg.hsz == null) ? wdg.sz : wdg.hsz)
						&& wdg.mouseup(c.add(cc.inv()), button))
					return true;
				continue;
			}
			if (c.isect(cc, hitSize(wdg))) {
				if (wdg.mouseup(childPoint(c, cc, wdg), button)) {
					return (true);
				}
			}
		}
		return (false);
	}

	public boolean mousewheel(Coord c, int amount) {
		for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
			if (!wdg.visible)
				continue;
			Coord cc = xlate(wdg.c, true);
			if (wdg.getDisplayScale() == 1.0) {
				if (c.isect(cc, (wdg.hsz == null) ? wdg.sz : wdg.hsz)
						&& wdg.mousewheel(c.add(cc.inv()), amount))
					return true;
				continue;
			}
			if (c.isect(cc, hitSize(wdg))) {
				if (wdg.mousewheel(childPoint(c, cc, wdg), amount)) {
					return (true);
				}
			}
		}
		return (false);
	}

	public void mousemove(Coord c) {
		for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
			if (!wdg.visible)
				continue;
			Coord cc = xlate(wdg.c, true);
			if (wdg.getDisplayScale() == 1.0)
				wdg.mousemove(c.add(cc.inv()));
			else
				wdg.mousemove(childPoint(c, cc, wdg));
		}
	}

	public boolean globtype(char key, KeyEvent ev) {
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			if (wdg.globtype(key, ev))
				return (true);
		}
		return (false);
	}

	public boolean type(char key, KeyEvent ev) {
		if (canactivate) {
			if (key == 10) {
				wdgmsg("activate");
				return (true);
			}
		}
		if (cancancel) {
			if (key == 27) {
				wdgmsg("cancel");
				return (true);
			}
		}
		if (focusctl) {
			if (focused != null) {
				if (focused.type(key, ev))
					return (true);
				if (focustab) {
					if (key == '\t') {
						Widget f = focused;
						while (true) {
							if ((ev.getModifiers() & InputEvent.SHIFT_MASK) == 0)
								f = (f.next == null) ? child : f.next;
							else
								f = (f.prev == null) ? lchild : f.prev;
							if (f.canfocus)
								break;
						}
						setfocus(f);
						return (true);
					} else {
						return (false);
					}
				} else {
					return (false);
				}
			} else {
				return (false);
			}
		} else {
			for (Widget wdg = child; wdg != null; wdg = wdg.next) {
				if (wdg.type(key, ev))
					return (true);
			}
			return (false);
		}
	}

	public boolean keydown(KeyEvent ev) {
		if (focusctl) {
			if (focused != null) {
				if (focused.keydown(ev))
					return (true);
				return (false);
			} else {
				return (false);
			}
		} else {
			for (Widget wdg = child; wdg != null; wdg = wdg.next) {
				if (wdg.keydown(ev))
					return (true);
			}
		}
		return (false);
	}

	public boolean keyup(KeyEvent ev) {
		if (focusctl) {
			if (focused != null) {
				if (focused.keyup(ev))
					return (true);
				return (false);
			} else {
				return (false);
			}
		} else {
			for (Widget wdg = child; wdg != null; wdg = wdg.next) {
				if (wdg.keyup(ev))
					return (true);
			}
		}
		return (false);
	}

	public void raise() {
		synchronized (ui) {
			unlink();
			link();
		}
	}

	@Deprecated
	public <T extends Widget> T findchild(Class<T> cl) {
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			if (cl.isInstance(wdg))
				return (cl.cast(wdg));
			T ret = wdg.findchild(cl);
			if (ret != null)
				return (ret);
		}
		return (null);
	}

	public Resource getcurs(Coord c) {
		Resource ret;

		for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
			if (!wdg.visible)
				continue;
			Coord cc = xlate(wdg.c, true);
			if (wdg.getDisplayScale() == 1.0) {
				Coord size = (wdg.hsz == null) ? wdg.sz : wdg.hsz;
				if (c.isect(cc, size)
						&& ((ret = wdg.getcurs(c.add(cc.inv()))) != null))
					return ret;
				continue;
			}
			if (c.isect(cc, hitSize(wdg))) {
				if ((ret = wdg.getcurs(childPoint(c, cc, wdg))) != null)
					return (ret);
			}
		}
		return (cursor);
	}

	public Object tooltip(Coord c, boolean again) {
		if (tooltip != null) {
			prevtt = null;
			return (tooltip);
		}
		for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
			if (!wdg.visible)
				continue;
			Coord cc = xlate(wdg.c, true);
			if (wdg.getDisplayScale() == 1.0) {
				Coord size = (wdg.hsz == null) ? wdg.sz : wdg.hsz;
				if (c.isect(cc, size)) {
					Object ret = wdg.tooltip(c.add(cc.inv()), again
							&& (wdg == prevtt));
					if (ret != null) {
						prevtt = wdg;
						return ret;
					}
				}
				continue;
			}
			if (c.isect(cc, hitSize(wdg))) {
				Object ret = wdg.tooltip(childPoint(c, cc, wdg), again
						&& (wdg == prevtt));
				if (ret != null) {
					prevtt = wdg;
					return (ret);
				}
			}
		}
		prevtt = null;
		return (null);
	}

	public void hide() {
		visible = false;
	}

	public void show() {
		visible = true;
	}

	public void update(long dt) {
		Widget next;

		for (Widget wdg = child; wdg != null; wdg = next) {
			next = wdg.next;
			wdg.update(dt);
		}
	}
}
