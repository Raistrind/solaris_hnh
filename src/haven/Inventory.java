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

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class Inventory extends Widget implements DTarget {
	public static final Tex invsq; // InvisibleSquare = 1x1 cell
	public static final Coord invSqSize; // size of invsq
	public static final Coord invSqSizeSubOne; // size of invsq.sub(1,1)
	protected static BufferedImage[] tbtni = new BufferedImage[] {
			Resource.loadimg("gfx/hud/trashu"),
			Resource.loadimg("gfx/hud/trashd"),
			Resource.loadimg("gfx/hud/trashh") };
	Coord isz;
	private final IButton trash;
	private final Button transferAll;
	private final Button transferPresent;
	private final Button sortButton;
	private final Button takeEggs;
	private final InventorySorter sorter;
	private final AtomicBoolean wait = new AtomicBoolean(false);

	static {
		invsq = Resource.loadtex("gfx/hud/invsq"); // InvisibleSquare = 1x1 cell
		invSqSize = invsq.sz(); // 32x32
		invSqSizeSubOne = Inventory.invSqSize.sub(1, 1);
	}

	static {
		Widget.addtype("inv", new WidgetFactory() {
			public Widget create(Coord c, Widget parent, Object[] args) {
				return (new Inventory(c, (Coord) args[0], parent));
			}
		});
	}

	public void draw(GOut g) {
		Coord c = new Coord();
		Coord sz = invSqSizeSubOne;
		for (c.y = 0; c.y < isz.y; c.y++) {
			for (c.x = 0; c.x < isz.x; c.x++) {
				g.image(invsq, c.mul(sz));
			}
		}
		super.draw(g);
	}

	public Inventory(Coord c, Coord sz, Widget parent) {
		super(c, invSqSizeSubOne.mul(sz).add(new Coord(17, 1)), parent);
		isz = sz;
		if ((parent instanceof Window) &&
				isStorageWindow((Window) parent)) {
			Window wnd = (Window) parent;
			boolean playerInventory = isPlayerInventoryWindow(wnd);
			transferAll = new Button(Coord.z, 110, this,
					playerInventory ? "Deposit All" : "Take All") {
				public void click() {
					transferAllItems();
				}
			};
			transferPresent = new Button(Coord.z, 110, this,
					playerInventory ? "Deposit If Present" : "Take If Present") {
				public void click() {
					transferPresentItems();
				}
			};
			sortButton = new Button(Coord.z, 110, this, "Sort") {
				public void click() {
					if (sorter != null)
						sorter.toggle();
				}
			};
			sorter = new InventorySorter(this, sortButton);
			takeEggs = null;
		} else if ((parent instanceof Window) &&
				isChickenCoopWindow((Window) parent)) {
			transferAll = null;
			transferPresent = null;
			sortButton = null;
			sorter = null;
			takeEggs = new Button(Coord.z, 110, this, "Take Eggs") {
				public void click() {
					takeEggItems();
				}
			};
		} else {
			transferAll = null;
			transferPresent = null;
			sortButton = null;
			sorter = null;
			takeEggs = null;
		}

		// removed trash can from inventory -trev
		/*
		if (parent.canhastrash) {
			trash = new IButton(Coord.z, this, tbtni[0], tbtni[1], tbtni[2]);
			trash.visible = true;
		} else {
			trash = null;
		}
		 */

		trash = null;
		recalcsz();
	}

	public boolean mousewheel(Coord c, int amount) {
		int mod = ui.modflags();
		if ((mod & 6) == 6) { //
			mod = 7;
		}
		if (amount < 0)
			wdgmsg("xfer", -1, mod);
		if (amount > 0)
			wdgmsg("xfer", 1, mod);
		return (true);
	}

	public void update(long dt) {
		super.update(dt);
		if (sorter != null)
			sorter.update();
	}
	
	public Coord size() {
		return isz;
	}

	public Coord firstFreeSlot(Item candidate) {
		Coord candidateSize = normalizedItemSize(candidate);
		if ((candidateSize.x > isz.x) || (candidateSize.y > isz.y))
			return null;

		boolean[][] occupied = new boolean[isz.x][isz.y];
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			if (!wdg.visible || !(wdg instanceof Item))
				continue;
			Item item = (Item) wdg;
			Coord position = item.coord();
			Coord itemSize = normalizedItemSize(item);
			if (!withinInventory(position, itemSize))
				return null;
			for (int y = 0; y < itemSize.y; y++)
				for (int x = 0; x < itemSize.x; x++)
					occupied[position.x + x][position.y + y] = true;
		}

		for (int y = 0; y <= isz.y - candidateSize.y; y++) {
			for (int x = 0; x <= isz.x - candidateSize.x; x++) {
				boolean free = true;
				for (int yy = 0; yy < candidateSize.y && free; yy++) {
					for (int xx = 0; xx < candidateSize.x; xx++) {
						if (occupied[x + xx][y + yy]) {
							free = false;
							break;
						}
					}
				}
				if (free)
					return new Coord(x, y);
			}
		}
		return null;
	}

	/**
	 * Returns whether all of the supplied items can be placed in this
	 * inventory at the same time. The optional ignored item is treated as
	 * removed while checking; this is useful when an item is being moved out
	 * of the inventory to equip it.
	 */
	boolean canFitItems(List<Item> candidates, Item ignored) {
		if (candidates == null || candidates.isEmpty())
			return true;
		boolean[][] occupied = new boolean[isz.x][isz.y];
		for (Widget wdg = child; wdg != null; wdg = wdg.next) {
			if (!wdg.visible || !(wdg instanceof Item) || wdg == ignored)
				continue;
			Item item = (Item) wdg;
			Coord position = item.coord();
			Coord itemSize = normalizedItemSize(item);
			if (!withinInventory(position, itemSize))
				return false;
			for (int y = 0; y < itemSize.y; y++)
				for (int x = 0; x < itemSize.x; x++)
					occupied[position.x + x][position.y + y] = true;
		}
		List<Coord> sizes = new ArrayList<Coord>();
		for (Item item : candidates) {
			if (item == null)
				continue;
			Coord size = normalizedItemSize(item);
			if ((size.x > isz.x) || (size.y > isz.y))
				return false;
			sizes.add(size);
		}
		return canFitItemSizes(occupied, sizes, 0);
	}

	private boolean canFitItemSizes(boolean[][] occupied, List<Coord> sizes,
			int index) {
		if (index >= sizes.size())
			return true;
		Coord size = sizes.get(index);
		for (int y = 0; y <= isz.y - size.y; y++) {
			for (int x = 0; x <= isz.x - size.x; x++) {
				boolean free = true;
				for (int yy = 0; yy < size.y && free; yy++) {
					for (int xx = 0; xx < size.x; xx++) {
						if (occupied[x + xx][y + yy]) {
							free = false;
							break;
						}
					}
				}
				if (!free)
					continue;
				for (int yy = 0; yy < size.y; yy++)
					for (int xx = 0; xx < size.x; xx++)
						occupied[x + xx][y + yy] = true;
				if (canFitItemSizes(occupied, sizes, index + 1))
					return true;
				for (int yy = 0; yy < size.y; yy++)
					for (int xx = 0; xx < size.x; xx++)
						occupied[x + xx][y + yy] = false;
			}
		}
		return false;
	}

	private static Coord normalizedItemSize(Item item) {
		Coord size = item.size();
		return new Coord(Math.max(1, size.x), Math.max(1, size.y));
	}

	private boolean withinInventory(Coord position, Coord itemSize) {
		return (position.x >= 0) && (position.y >= 0) &&
				(position.x + itemSize.x <= isz.x) &&
				(position.y + itemSize.y <= isz.y);
	}

	public boolean drop(Coord cc, Coord ul) {
		wdgmsg("drop", ul.add(new Coord(15, 15)).div(invSqSize));
		return (true);
	}

	public boolean iteminteract(Coord cc, Coord ul) {
		return (false);
	}

	public void uimsg(String msg, Object... args) {
		if (msg == "sz") {
			isz = (Coord) args[0];
			recalcsz();
		}
	}

	public void wdgmsg(Widget sender, String msg, Object... args) {
		if (checkTrashButton(sender)) {
			if (wait.get()) {
				return;
			}
			wait.set(true);
			new ConfirmWnd(parent.c.add(c).add(trash.c), ui.root, getmsg(),
					new ConfirmWnd.Callback() {
						public void result(Boolean res) {
							wait.set(false);
							if (res) {
								empty();
							}
						}
					});
		} else if (msg.equals("drop_such_all")) {
			for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
				if (wdg.visible && wdg instanceof Item) {
					if (((Item) wdg).GetResName().equals((String) args[0]))
						wdg.wdgmsg("drop", Coord.z);
				}
			}
		} else if (msg.equals("transfer_such_all")) {
			for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
				if (wdg.visible && wdg instanceof Item) {
					if (((Item) wdg).GetResName().equals((String) args[0]))
						wdg.wdgmsg("transfer", Coord.z);
				}
			}
		} else if (msg.equals("transfer_such_all_ql")) {
			List<Item> il = new ArrayList<Item>();
			Item.ItemQualityComparator comp = new Item.ItemQualityComparator();
			for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
				if (wdg.visible && wdg instanceof Item) {
					if (((Item) wdg).GetResName().equals((String) args[0]))
						il.add((Item) wdg);
				}
			}
			Collections.sort(il, comp);
			for (int i = 0; i < il.size(); i++) {
				il.get(i).wdgmsg("transfer", Coord.z);
			}
		} else if (msg.equals("transfer_such_all_qldesc")) {
			List<Item> il = new ArrayList<Item>();
			Item.ItemQualityComparator comp = new Item.ItemQualityComparator(
					true);
			for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
				if (wdg.visible && wdg instanceof Item) {
					if (((Item) wdg).GetResName().equals((String) args[0]))
						il.add((Item) wdg);
				}
			}
			Collections.sort(il, comp);
			for (int i = 0; i < il.size(); i++) {
				il.get(i).wdgmsg("transfer", Coord.z);
			}
		} else {
			super.wdgmsg(sender, msg, args);
		}
	}

	// Commented out because not used
	// public void showtrash(boolean visible){
	// if (visible) {
	// if (trash == null) {
	// trash = new IButton(Coord.z, this, trashButtonImages);
	// }
	// trash.visible = visible;
	// } else {
	// if (trash != null) {
	// trash.visible = visible;
	// }
	// }
	// recalculateSize();
	// }

	private String getmsg() {
		if (parent instanceof Window) {
			String str = ((Window) parent).cap.text;
			return "Drop all items from the " + str.toLowerCase()
					+ " to ground?";
		}
		return "Drop all items to ground?";
	}

	private void empty() {
		for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
			if (wdg.visible && wdg instanceof Item) {
				wdg.wdgmsg("drop", Coord.z);
			}
		}
	}

	private void transferAllItems() {
		List<Item> items = new ArrayList<Item>();
		for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
			if (wdg.visible && wdg instanceof Item)
				items.add((Item) wdg);
		}
		for (Item item : items)
			item.wdgmsg("transfer", Coord.z);
	}

	private void transferPresentItems() {
		Set<String> present = new HashSet<String>();
		for (Inventory inventory : destinationInventories()) {
			for (Widget wdg = inventory.lchild; wdg != null; wdg = wdg.prev) {
				if (wdg.visible && wdg instanceof Item) {
					String resourceName = ((Item) wdg).GetResName();
					if (resourceName != null)
						present.add(resourceName);
				}
			}
		}
		if (present.isEmpty())
			return;

		List<Item> items = new ArrayList<Item>();
		for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
			if (wdg.visible && wdg instanceof Item) {
				Item item = (Item) wdg;
				String resourceName = item.GetResName();
				if ((resourceName != null) && present.contains(resourceName))
					items.add(item);
			}
		}
		for (Item item : items)
			item.wdgmsg("transfer", Coord.z);
	}

	private List<Inventory> destinationInventories() {
		List<Inventory> inventories = new ArrayList<Inventory>();
		if (!(parent instanceof Window))
			return inventories;

		Window source = (Window) parent;
		Widget desktop = source.parent;
		if (desktop == null)
			return inventories;

		boolean depositing = isPlayerInventoryWindow(source);
		for (Widget wdg = desktop.lchild; wdg != null; wdg = wdg.prev) {
			if (!wdg.visible || !(wdg instanceof Window) || (wdg == source))
				continue;
			Window candidate = (Window) wdg;
			if (!isStorageWindow(candidate))
				continue;
			if (depositing == isPlayerInventoryWindow(candidate))
				continue;
			collectInventories(candidate, inventories);
			if (!inventories.isEmpty())
				break;
		}
		return inventories;
	}

	private static void collectInventories(Window wnd,
			List<Inventory> inventories) {
		for (Widget wdg = wnd.child; wdg != null; wdg = wdg.next) {
			if (wdg.visible && wdg instanceof Inventory)
				inventories.add((Inventory) wdg);
		}
	}

	private static boolean isPlayerInventoryWindow(Window wnd) {
		return (wnd.cap != null) && wnd.cap.text.equals("Inventory");
	}

	private static boolean isStorageWindow(Window wnd) {
		if (wnd.cap == null)
			return false;
		String title = wnd.cap.text;
		return title.equals("Inventory") || title.equals("Cupboard") ||
				title.equals("Chest") || title.equals("Coffer") ||
				title.equals("Seedbag") ||
				title.equals("Barrel") || title.equals("Crate") ||
				title.equals("Basket");
	}

	private static boolean isChickenCoopWindow(Window wnd) {
		return (wnd != null) && (wnd.cap != null) &&
				wnd.cap.text.equalsIgnoreCase("Chicken Coop");
	}

	private void takeEggItems() {
		List<Item> eggs = new ArrayList<Item>();
		for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
			if (wdg.visible && (wdg instanceof Item) && isEgg((Item) wdg))
				eggs.add((Item) wdg);
		}
		for (Item egg : eggs)
			egg.wdgmsg("transfer", Coord.z);
	}

	private static boolean isEgg(Item item) {
		if (item == null)
			return false;
		String resource = item.GetResName();
		if ((resource != null) && resource.toLowerCase().contains("egg"))
			return true;
		String name = item.name();
		return (name != null) && name.toLowerCase().contains("egg");
	}

	private boolean needshift() {
		if (parent instanceof Window) {
			Window wnd = (Window) parent;
			if (wnd.cap != null) {
				String str = wnd.cap.text;
				if (str.equals("Oven") || str.equals("Finery Forge")
						|| str.equals("Steel Crucible")) {
					return true;
				}
			}
		}
		return false;
	}

	private void recalcsz() {
		Coord gridsz = invSqSizeSubOne.mul(isz).add(new Coord(1, 1));
		sz = gridsz;
		if (transferAll != null) {
			transferAll.c = new Coord(0, gridsz.y + 3);
			transferAll.sz.x = Math.max(110, gridsz.x);
			transferPresent.c = new Coord(0,
					transferAll.c.y + transferAll.sz.y + 3);
			transferPresent.sz.x = transferAll.sz.x;
			if (sortButton != null) {
				sortButton.c = new Coord(0,
						transferPresent.c.y + transferPresent.sz.y + 3);
				sortButton.sz.x = transferAll.sz.x;
				sz = new Coord(sortButton.sz.x,
						sortButton.c.y + sortButton.sz.y);
			} else {
				sz = new Coord(transferPresent.sz.x,
						transferPresent.c.y + transferPresent.sz.y);
			}
		}
		if ((trash != null) && (trash.visible)) {
			trash.c = sz.sub(0, invSqSize.y);
			hsz = sz.add(16, 0);
			if (needshift()) {// small inventory, button should be shifted
								// (Finery forge, oven, crucible)
				trash.c.x += 18;
				hsz.x += 18;
			}
		} else {
			hsz = null;
		}
		if ((transferAll != null) && (parent instanceof Window))
			((Window) parent).growToFit(this);
		else if ((takeEggs != null) && (parent instanceof Window)) {
			takeEggs.c = new Coord(0, gridsz.y + 3);
			takeEggs.sz.x = Math.max(110, gridsz.x);
			sz = new Coord(takeEggs.sz.x,
					takeEggs.c.y + takeEggs.sz.y);
			((Window) parent).growToFit(this);
		}
	}

	protected boolean checkTrashButton(Widget w) {
		return trash != null && w == trash;
	}
}
