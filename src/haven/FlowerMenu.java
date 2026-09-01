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

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import static java.lang.Math.PI;

public class FlowerMenu extends Widget {
	public static Color pink = new Color(255, 0, 128);
	public static IBox pbox;
	static Color ptc = Color.WHITE;
	static Text.Foundry ptf = new Text.Foundry(new Font("SansSerif",
			Font.PLAIN, 12));
	static int ph = 30, ppl = 8;
	Petal[] menuOptions;
	Anim anim;
	private static final long ITEM_RECIPE_TIMEOUT = 3000;
	private static final long ITEM_RECIPE_BUTTON_TIMEOUT = 8000;
	private static final int ITEM_RECIPE_BUTTON_WIDTH = 180;
	private static final long BULK_ITEM_MENU_TIMEOUT = 5000;
	private static final long BULK_ITEM_RESULT_TIMEOUT = 10000;
	private static final long BULK_ITEM_STEP_DELAY = 500;
	private static final Set<String> BULK_ITEM_ACTIONS = new HashSet<String>();
	private static ItemRecipeRequest pendingItemRecipe;
	private static BulkItemActionRunner pendingBulkItemAction;
	private static BulkItemActionRunner activeBulkItemAction;
	private ItemRecipeRequest itemRecipeRequest;
	private ItemEquipRequest itemEquipRequest;
	private final List<Button> itemBulkButtons = new ArrayList<Button>();
	private BulkItemActionRunner bulkItemAction;
	private BulkItemActionRunner bulkItemActionAfterBind;
	private static final String[] EQUIP_OPTIONS = { "Equip", "Wear", "Wield" };
	private static final int LEFT_HAND_SLOT = 6;
	private static final int RIGHT_HAND_SLOT = 7;

	private static class ItemRecipeRequest {
		final UI ui;
		final Item item;
		final String name;
		final String resource;
		final long created;
		ItemRecipeButton button;
		FlowerMenu menu;

		ItemRecipeRequest(UI ui, Item item, String name, String resource) {
			this.ui = ui;
			this.item = item;
			this.name = name;
			this.resource = resource;
			created = System.currentTimeMillis();
		}
	}

	private static class ItemEquipRequest {
		final UI ui;
		final Item item;
		final long created;
		ItemEquipButton button;
		FlowerMenu menu;

		ItemEquipRequest(UI ui, Item item) {
			this.ui = ui;
			this.item = item;
			created = System.currentTimeMillis();
		}
	}

	private static class ItemRecipeButton extends Button {
		private final ItemRecipeRequest request;

		ItemRecipeButton(Coord c, UI ui, ItemRecipeRequest request) {
			super(c, ITEM_RECIPE_BUTTON_WIDTH, ui.root,
					"Recipes using this item");
			this.request = request;
		}

		public void click() {
			FlowerMenu menu = request.menu;
			request.menu = null;
			if ((menu != null) && (menu.parent != null))
				menu.cancelForLocalRecipe();
			clearPendingItemRecipe(request);
			request.button = null;
			if (parent != null)
				ui.destroy(this);
			KnowledgeWindow.openForItem(ui, request.name, request.resource);
		}

		public void update(long dt) {
			super.update(dt);
			if ((System.currentTimeMillis() - request.created)
					> ITEM_RECIPE_BUTTON_TIMEOUT) {
				clearPendingItemRecipe(request);
				request.button = null;
				if (parent != null)
					ui.destroy(this);
			}
		}
	}

	private class ItemBulkActionButton extends Button {
		private final String action;

		ItemBulkActionButton(String action) {
			super(Coord.z, ITEM_RECIPE_BUTTON_WIDTH, FlowerMenu.this.ui.root,
					action + " All");
			this.action = action;
		}

		public void click() {
			startBulkItemAction(action);
		}
	}

	private static class ItemEquipButton extends Button {
		private final ItemEquipRequest request;

		ItemEquipButton(Coord c, UI ui, ItemEquipRequest request) {
			super(c, ITEM_RECIPE_BUTTON_WIDTH, ui.root, "Equip");
			this.request = request;
		}

		public void click() {
			FlowerMenu menu = request.menu;
			if ((menu == null) || (menu.parent == null))
				return;
			String reason = menu.equipBlockedReason();
			if (reason != null) {
				if (ui.slenhud != null)
					ui.slenhud.error(reason);
				return;
			}
			String option = menu.firstEquipOption();
			if (option == null) {
				if (ui.slenhud != null)
					ui.slenhud.error("This item has no equip action available.");
				return;
			}
			clearPendingItemEquip(request);
			request.menu = null;
			request.button = null;
			if (parent != null)
				ui.destroy(this);
			menu.cancelLocalEquip(option);
		}

		public void update(long dt) {
			super.update(dt);
			if ((System.currentTimeMillis() - request.created)
					> ITEM_RECIPE_BUTTON_TIMEOUT) {
				clearPendingItemEquip(request);
				request.button = null;
				if (parent != null)
					ui.destroy(this);
			}
		}
	}

	/** Runs one server-owned item action at a time and waits for each menu. */
	private static class BulkItemActionRunner extends Widget {
		private static final int WAITING_ACTION = 0;
		private static final int WAITING_RESULT = 1;
		private static final int WAITING_DELAY = 2;
		private static final int WAITING_MENU = 3;
		private final Inventory inventory;
		private final String resource;
		private final String action;
		private final List<Item> remaining;
		private FlowerMenu menu;
		private Item currentItem;
		private int state = WAITING_ACTION;
		private int processed = 0;
		private int skipped = 0;
		private long deadline;
		private boolean accepted;
		private boolean skipping;
		private boolean finished;

		BulkItemActionRunner(Item source, String action) {
			super(Coord.z, Coord.z, source.ui.root);
			inventory = (Inventory) source.parent;
			resource = source.GetResName();
			this.action = action;
			remaining = matchingItems(source);
			remaining.remove(source);
			currentItem = source;
			activateBulkItemAction(this);
		}

		void start(FlowerMenu menu) {
			select(menu);
		}

		private void select(FlowerMenu menu) {
			this.menu = menu;
			menu.bulkItemAction = this;
			accepted = false;
			skipping = false;
			state = WAITING_ACTION;
			deadline = System.currentTimeMillis() + BULK_ITEM_MENU_TIMEOUT;
			menu.SelectOpt(action);
		}

		void menuOpened(FlowerMenu menu) {
			if (finished || (state != WAITING_MENU)) {
				menu.closeMenu();
				return;
			}
			this.menu = menu;
			menu.bulkItemAction = this;
			accepted = false;
			state = WAITING_ACTION;
			deadline = System.currentTimeMillis() + BULK_ITEM_MENU_TIMEOUT;
			if (menu.haveOpt(action)) {
				skipping = false;
				menu.SelectOpt(action);
			} else {
				skipping = true;
				menu.closeMenu();
			}
		}

		void menuAccepted(FlowerMenu menu) {
			if (!finished && (this.menu == menu))
				accepted = true;
		}

		void menuCancelled(FlowerMenu menu) {
			if (!finished && (this.menu == menu) && !skipping)
				accepted = false;
		}

		void menuClosed(FlowerMenu menu) {
			if (finished || (this.menu != menu))
				return;
			this.menu = null;
			if (skipping) {
				skipped++;
				currentItem = null;
				scheduleNext();
			} else if (accepted) {
				waitForResult();
			} else {
				finish(action + " All stopped after " + processed
						+ " item" + ((processed == 1) ? "" : "s") + ".");
			}
		}

		private void waitForResult() {
			state = WAITING_RESULT;
			deadline = System.currentTimeMillis() + BULK_ITEM_RESULT_TIMEOUT;
		}

		private void scheduleNext() {
			state = WAITING_DELAY;
			deadline = System.currentTimeMillis() + BULK_ITEM_STEP_DELAY;
		}

		private boolean usable(Item item) {
			return (item != null) && (item.parent == inventory) && item.visible
					&& !item.isDragging && (ui.getId(item) >= 0)
					&& resource.equals(item.GetResName());
		}

		private void requestNext() {
			while (!remaining.isEmpty()) {
				Item item = remaining.remove(0);
				if (!usable(item)) {
					skipped++;
					continue;
				}
				currentItem = item;
				setPendingBulkItemAction(this);
				state = WAITING_MENU;
				deadline = System.currentTimeMillis() + BULK_ITEM_MENU_TIMEOUT;
				item.wdgmsg("iact", Coord.z);
				return;
			}
			String message = action + " All finished: " + processed + " item"
					+ ((processed == 1) ? "" : "s");
			if (skipped > 0)
				message += ", " + skipped + " skipped";
			finish(message + ".");
		}

		private void finish(String message) {
			if (finished)
				return;
			finished = true;
			clearBulkItemAction(this);
			if ((ui.slenhud != null) && (message != null))
				ui.slenhud.error(message);
			ui.destroy(this);
		}

		public void update(long dt) {
			if (finished)
				return;
			long now = System.currentTimeMillis();
			if (state == WAITING_RESULT) {
				if (!usable(currentItem)) {
					processed++;
					currentItem = null;
					scheduleNext();
				} else if (now >= deadline) {
					finish(action + " All stopped because the previous item "
							+ "did not finish processing.");
				}
			} else if ((state == WAITING_DELAY) && (now >= deadline)) {
				requestNext();
			} else if (((state == WAITING_MENU) ||
					(state == WAITING_ACTION)) && (now >= deadline)) {
				finish(action + " All stopped because the server did not respond.");
			}
		}
	}

	private static List<Item> matchingItems(Item source) {
		List<Item> matches = new ArrayList<Item>();
		if ((source == null) || !(source.parent instanceof Inventory))
			return matches;
		String resource = source.GetResName();
		for (Widget child = source.parent.child; child != null;
				child = child.next) {
			if (!(child instanceof Item))
				continue;
			Item item = (Item) child;
			if (item.visible && !item.isDragging &&
					resource.equals(item.GetResName()))
				matches.add(item);
		}
		return matches;
	}

	private static boolean isBulkItemAction(String action) {
		return (action != null) && BULK_ITEM_ACTIONS.contains(action.trim()
				.toLowerCase(Locale.ENGLISH));
	}

	private static synchronized void activateBulkItemAction(
			BulkItemActionRunner runner) {
		BulkItemActionRunner previous = activeBulkItemAction;
		activeBulkItemAction = runner;
		if ((previous != null) && (previous != runner))
			previous.finish(previous.action + " All cancelled by a new bulk action.");
	}

	private static synchronized void setPendingBulkItemAction(
			BulkItemActionRunner runner) {
		pendingBulkItemAction = runner;
	}

	private static synchronized BulkItemActionRunner consumeBulkItemAction(
			UI ui) {
		BulkItemActionRunner runner = pendingBulkItemAction;
		if ((runner == null) || (runner.ui != ui) || runner.finished)
			return null;
		pendingBulkItemAction = null;
		return runner;
	}

	private static synchronized void clearBulkItemAction(
			BulkItemActionRunner runner) {
		if (pendingBulkItemAction == runner)
			pendingBulkItemAction = null;
		if (activeBulkItemAction == runner)
			activeBulkItemAction = null;
	}

	/** Adds a local reverse-recipe choice to the next server item menu. */
	public static synchronized void expectItemRecipeOption(UI ui, Item item,
			String name, String resource, Coord clickPosition) {
		if ((pendingItemRecipe != null) &&
				(pendingItemRecipe.button != null) &&
				(pendingItemRecipe.button.parent != null))
			pendingItemRecipe.button.ui.destroy(pendingItemRecipe.button);
		ItemRecipeRequest request = new ItemRecipeRequest(ui, item, name, resource);
		Coord position = clickPosition.add(-(ITEM_RECIPE_BUTTON_WIDTH / 2), 24);
		position.x = Math.max(0, Math.min(position.x,
				ui.root.sz.x - ITEM_RECIPE_BUTTON_WIDTH));
		if ((position.y + 19) > ui.root.sz.y)
			position.y = Math.max(0, clickPosition.y - 43);
		if (Config.showItemRecipeMenu)
			request.button = new ItemRecipeButton(position, ui, request);
		pendingItemRecipe = request;
	}

	/** Adds a client-only Equip button to the next inventory item menu. */
	public static synchronized void expectItemEquipOption(UI ui, Item item,
			Coord clickPosition) {
		if ((item == null) || !isEquipableItem(item)
				|| !isPlayerInventoryItem(item)) {
			if ((pendingItemEquip != null) && (pendingItemEquip.button != null)
					&& (pendingItemEquip.button.parent != null))
				pendingItemEquip.button.ui.destroy(pendingItemEquip.button);
			pendingItemEquip = null;
			return;
		}
		if ((pendingItemEquip != null) && (pendingItemEquip.button != null)
				&& (pendingItemEquip.button.parent != null))
			pendingItemEquip.button.ui.destroy(pendingItemEquip.button);
		ItemEquipRequest request = new ItemEquipRequest(ui, item);
		Coord position = clickPosition.add(-(ITEM_RECIPE_BUTTON_WIDTH / 2), 24);
		position.x = Math.max(0, Math.min(position.x,
				ui.root.sz.x - ITEM_RECIPE_BUTTON_WIDTH));
		if ((position.y + 19) > ui.root.sz.y)
			position.y = Math.max(0, clickPosition.y - 43);
		request.button = new ItemEquipButton(position, ui, request);
		pendingItemEquip = request;
	}

	private static ItemEquipRequest pendingItemEquip;

	private static synchronized void clearPendingItemEquip(
			ItemEquipRequest request) {
		if (pendingItemEquip == request)
			pendingItemEquip = null;
	}

	private static synchronized ItemEquipRequest consumeItemEquipOption(UI ui) {
		ItemEquipRequest request = pendingItemEquip;
		pendingItemEquip = null;
		if ((request == null) || (request.ui != ui)
				|| ((System.currentTimeMillis() - request.created)
						> ITEM_RECIPE_TIMEOUT))
			return null;
		return request;
	}

	private static boolean isEquipableItem(Item item) {
		Resource resource = (item == null || item.res == null) ? null
				: item.res.get();
		String name = (resource == null) ? item.name()
				: KnowledgeBase.displayName(resource);
		String resourceName = (resource == null) ? item.GetResName()
				: resource.name;
		String category = KnowledgeBase.infoFor(name, resourceName).category;
		return category.equals("Tool") || category.equals("Weapon")
				|| category.equals("Equipment");
	}

	private static boolean isPlayerInventoryItem(Item item) {
		if ((item == null) || !(item.parent instanceof Inventory))
			return false;
		Widget parent = item.parent.parent;
		return (parent instanceof Window) && (((Window) parent).cap != null)
				&& ((Window) parent).cap.text.equals("Inventory");
	}

	private static synchronized void clearPendingItemRecipe(
			ItemRecipeRequest request) {
		if (pendingItemRecipe == request)
			pendingItemRecipe = null;
	}

	private static synchronized ItemRecipeRequest consumeItemRecipeOption(UI ui) {
		ItemRecipeRequest request = pendingItemRecipe;
		pendingItemRecipe = null;
		if ((request == null) || (request.ui != ui)
				|| ((System.currentTimeMillis() - request.created)
						> ITEM_RECIPE_TIMEOUT))
			return null;
		return request;
	}

	static {
		// Keep this list limited to repeatable processing. Consumption,
		// destruction, equipment and movement actions must remain single-item.
		String[] bulkActions = { "Butcher", "Clean", "Fillet", "Gut",
				"Pluck", "Skin", "Debone", "Crack", "Peel", "Shell",
				"Slice", "Split" };
		for (String action : bulkActions)
			BULK_ITEM_ACTIONS.add(action.toLowerCase(Locale.ENGLISH));
		Widget.addtype("sm", new WidgetFactory() {
			public Widget create(Coord c, Widget parent, Object[] args) {
				if ((c.x == -1) && (c.y == -1))
					c = parent.ui.lcc;
				String[] opts = new String[args.length];
				for (int i = 0; i < args.length; i++)
					opts[i] = (String) args[i];
				return (new FlowerMenu(c, parent, opts));
			}
		});
		pbox = new IBox("gfx/hud", "tl", "tr", "bl", "br", "extvl", "extvr",
				"extht", "exthb");
	}

	public class Petal extends Widget {
		public String name;
		public double ta, tr;
		public int num;
		Text text;
		double a = 1;

		public Petal(String name) {
			this(name, name);
		}

		public Petal(String name, String displayName) {
			super(Coord.z, Coord.z, FlowerMenu.this);
			this.name = name;
			text = ptf.render(displayName, ptc);
			sz = new Coord(text.sz().x + 25, ph);
		}

		public void move(Coord c) {
			this.c = c.add(sz.div(2).inv());
		}

		public void move(double a, double r) {
			move(Coord.sc(a, r));
		}

		public void draw(GOut g) {
			// Set background color from Config
			g.chcolor(Config.uiColor);  // Use the background color from Config
			g.frect(Coord.z, sz);  // Draw filled rectangle with background color

			g.chcolor();

			// Optionally draw the box with custom color
			pbox.draw(g, Coord.z, sz);

			// Draw the text with transparency
			g.chcolor(new Color(255, 255, 255, (int) (255 * a)));
			g.image(text.tex(), sz.div(2).add(text.sz().div(2).inv()));
		}

		public boolean mousedown(Coord c, int button) {
			wdgmsg(FlowerMenu.this, "cl", num);
			return (true);
		}
	}

	public abstract class Anim {
		long st = System.currentTimeMillis();
		int ms = 250;
		double s = 0.0;

		public void tick() {
			int dt = (int) (System.currentTimeMillis() - st);
			int animlength = (Config.fastFlowerAnim) ? 0 : ms;
			if (dt < animlength)
				s = (double) dt / animlength;
			else
				s = 1;
			if (dt >= animlength)
				end();
			tick2();
		}

		public void end() {
			anim = null;
		}

		public abstract void tick2();
	}

	public class Opening extends Anim {
		public void tick2() {
			for (Petal p : menuOptions) {
				p.move(p.ta + ((1 - s) * PI), p.tr * s);
				p.a = s;
			}
		}
	}

	public class Chosen extends Anim {
		Petal chosen;

		Chosen(Petal c) {
			ms = 750;
			chosen = c;
		}

		public void tick2() {
			for (Petal p : menuOptions) {
				if (p == chosen) {
					if (s > 0.6) {
						p.a = 1 - ((s - 0.6) / 0.4);
					} else if (s < 0.3) {
						p.move(p.ta, p.tr * (1 - (s / 0.3)));
					}
				} else {
					if (s > 0.3)
						p.a = 0;
					else
						p.a = 1 - (s / 0.3);
				}
			}
		}

		public void end() {
			ui.destroy(FlowerMenu.this);
		}
	}

	public class Cancel extends Anim {
		public void tick2() {
			for (Petal p : menuOptions) {
				p.move(p.ta + ((s) * PI), p.tr * (1 - s));
				p.a = 1 - s;
			}
		}

		public void end() {
			ui.destroy(FlowerMenu.this);
		}
	}

	private static void organize(Petal[] opts) {
		int l = 1, p = 0, i = 0;
		int lr = -1;
		for (i = 0; i < opts.length; i++) {
			if (lr == -1) {
				// lr = (int)(ph / (1 - Math.cos((2 * PI) / (ppl * l))));
				lr = 75 + (50 * (l - 1));
			}
			opts[i].ta = (PI / 2) - (p * (2 * PI / (l * ppl)));
			opts[i].tr = lr;
			if (++p >= (ppl * l)) {
				l++;
				p = 0;
				lr = -1;
			}
		}
	}

	static String cropOptionLabel(String option, MapView.CropInfo crop) {
		MapView.FlowerMenuTargetInfo target = (crop == null) ? null
				: new MapView.FlowerMenuTargetInfo(crop.name, crop);
		return MapView.flowerMenuOptionLabel(option, target);
	}

	static String targetOptionLabel(String option,
			MapView.FlowerMenuTargetInfo target) {
		return MapView.flowerMenuOptionLabel(option, target);
	}

	public FlowerMenu(Coord c, Widget parent, String... options) {
		super(c, Coord.z, parent);
		BulkItemActionRunner requestedBulkAction = consumeBulkItemAction(ui);
		MapView.FlowerMenuTargetInfo target = (ui.mapview == null) ? null
				: ui.mapview.consumeFlowerMenuTargetInfo();
		ItemRecipeRequest requestedItem = consumeItemRecipeOption(ui);
		ItemEquipRequest requestedEquip = consumeItemEquipOption(ui);
		// A remembered map target identifies an object menu, so never attach
		// inventory-only bulk actions to it if an earlier item request was stale.
		itemRecipeRequest = (target == null) ? requestedItem : null;
		itemEquipRequest = (target == null) ? requestedEquip : null;
		if (target != null) {
			if ((requestedItem != null) && (requestedItem.button != null))
				ui.destroy(requestedItem.button);
			if ((requestedEquip != null) && (requestedEquip.button != null))
				ui.destroy(requestedEquip.button);
		}
		menuOptions = new Petal[options.length];
		for (int i = 0; i < options.length; i++) {
			menuOptions[i] = new Petal(options[i], targetOptionLabel(options[i],
					target));
			menuOptions[i].num = i;
		}
		organize(menuOptions);
		keepOnScreen();
		if (itemRecipeRequest != null) {
			itemRecipeRequest.menu = this;
			createItemBulkButtons();
		}
		if (itemEquipRequest != null)
			itemEquipRequest.menu = this;
		if ((itemEquipRequest != null) && (firstEquipOption() == null)) {
			if (itemEquipRequest.button != null)
				ui.destroy(itemEquipRequest.button);
			clearPendingItemEquip(itemEquipRequest);
			itemEquipRequest.button = null;
			itemEquipRequest = null;
		}
		if ((itemRecipeRequest != null) || (itemEquipRequest != null))
			positionLocalItemButtons();
		ui.grabmouse(this);
		ui.grabkeys(this);
		anim = new Opening();
		ui.popupMenu = this; //Kerri: sets after it ready
		bulkItemActionAfterBind = requestedBulkAction;
	}

	@Override
	public void binded() {
		super.binded();
		BulkItemActionRunner runner = bulkItemActionAfterBind;
		bulkItemActionAfterBind = null;
		if (runner != null)
			runner.menuOpened(this);
	}

	private String firstEquipOption() {
		for (String candidate : EQUIP_OPTIONS) {
			for (Petal option : menuOptions) {
				if (option.name.equalsIgnoreCase(candidate))
					return option.name;
			}
		}
		return null;
	}

	private boolean itemIsTwoHanded(Item item) {
		if (item == null)
			return false;
		StringBuilder text = new StringBuilder();
		if (item.tooltip != null)
			text.append(item.tooltip).append(' ');
		String name = item.name();
		if (name != null)
			text.append(name).append(' ');
		String resource = item.GetResName();
		if (resource != null)
			text.append(resource);
		String lower = text.toString().toLowerCase(Locale.ENGLISH)
				.replace('-', ' ');
		return lower.contains("two handed") || lower.contains("both hands")
				|| lower.contains("2 handed") || lower.contains("2h")
				|| lower.contains("twohand") || lower.contains("greatsword")
				|| lower.contains("longbow") || lower.contains("crossbow")
				|| lower.contains("pickaxe") || lower.contains("shovel")
				|| lower.contains("scythe");
	}

	private String equipBlockedReason() {
		if ((itemEquipRequest == null) || !itemIsTwoHanded(itemEquipRequest.item)
				|| (ui.equip == null)
				|| !(itemEquipRequest.item.parent instanceof Inventory))
			return null;
		Inventory inventory = (Inventory) itemEquipRequest.item.parent;
		if (!(inventory.parent instanceof Window)
				|| ((Window) inventory.parent).cap == null
				|| !((Window) inventory.parent).cap.text.equals("Inventory"))
			return null;
		List<Item> displaced = new ArrayList<Item>();
		if (ui.equip.equed.size() > LEFT_HAND_SLOT
				&& ui.equip.equed.get(LEFT_HAND_SLOT) != null)
			displaced.add(ui.equip.equed.get(LEFT_HAND_SLOT));
		if (ui.equip.equed.size() > RIGHT_HAND_SLOT
				&& ui.equip.equed.get(RIGHT_HAND_SLOT) != null)
			displaced.add(ui.equip.equed.get(RIGHT_HAND_SLOT));
		if (!inventory.canFitItems(displaced, itemEquipRequest.item))
			return "Not enough inventory space for the items leaving your hands.";
		return null;
	}

	private void cancelLocalEquip(String option) {
		SelectOpt(option);
		Petal petal = findPetal(option);
		if (petal != null)
			anim = new Chosen(petal);
		ui.grabmouse(null);
		ui.grabkeys(null);
	}

	private Petal findPetal(String name) {
		for (Petal petal : menuOptions)
			if (petal.name.equals(name))
				return petal;
		return menuOptions.length == 0 ? null : menuOptions[0];
	}

	private void createItemBulkButtons() {
		if ((itemRecipeRequest == null) || (itemRecipeRequest.item == null)
				|| (matchingItems(itemRecipeRequest.item).size() < 2))
			return;
		Set<String> added = new HashSet<String>();
		for (Petal petal : menuOptions) {
			String key = petal.name.trim().toLowerCase(Locale.ENGLISH);
			if (isBulkItemAction(petal.name) && added.add(key))
				itemBulkButtons.add(new ItemBulkActionButton(petal.name));
		}
	}

	private void startBulkItemAction(String action) {
		Item source = (itemRecipeRequest == null) ? null : itemRecipeRequest.item;
		if ((source == null) || !(source.parent instanceof Inventory)
				|| (matchingItems(source).size() < 2)) {
			if (ui.slenhud != null)
				ui.slenhud.error(action + " All needs at least two matching items.");
			return;
		}
		clearLocalItemButtons();
		BulkItemActionRunner runner = new BulkItemActionRunner(source, action);
		runner.start(this);
	}

	private void clearLocalItemButtons() {
		for (Button button : itemBulkButtons)
			ui.destroy(button);
		itemBulkButtons.clear();
		if ((itemRecipeRequest != null) && (itemRecipeRequest.button != null)) {
			ItemRecipeButton button = itemRecipeRequest.button;
			itemRecipeRequest.button = null;
			ui.destroy(button);
		}
		if ((itemEquipRequest != null) && (itemEquipRequest.button != null)) {
			ItemEquipButton button = itemEquipRequest.button;
			itemEquipRequest.button = null;
			clearPendingItemEquip(itemEquipRequest);
			ui.destroy(button);
		}
	}

	/** Places client-only item actions outside the server-owned petals. */
	private void positionLocalItemButtons() {
		List<Button> buttons = new ArrayList<Button>(itemBulkButtons);
		if ((itemRecipeRequest != null) && (itemRecipeRequest.button != null))
			buttons.add(itemRecipeRequest.button);
		if ((itemEquipRequest != null) && (itemEquipRequest.button != null))
			buttons.add(itemEquipRequest.button);
		if (buttons.isEmpty())
			return;
		double scale = getDisplayScale();
		Coord centerRoot = outerToRoot(Coord.z);
		int top = centerRoot.y;
		int bottom = centerRoot.y;
		for (Petal petal : menuOptions) {
			Coord center = outerToRoot(Coord.sc(petal.ta, petal.tr));
			int halfHeight = (int) Math.ceil((petal.sz.y * scale) / 2.0);
			top = Math.min(top, center.y - halfHeight);
			bottom = Math.max(bottom, center.y + halfHeight);
		}
		int gap = 3;
		int totalHeight = -gap;
		for (Button button : buttons)
			totalHeight += button.sz.y + gap;
		int y = Math.max(centerRoot.y + 24, bottom + 5);
		if ((y + totalHeight) > ui.root.sz.y)
			y = top - totalHeight - 5;
		y = Math.max(0, Math.min(y, ui.root.sz.y - totalHeight));
		for (Button button : buttons) {
			int x = centerRoot.x - (button.sz.x / 2);
			x = Math.max(0, Math.min(x, ui.root.sz.x - button.sz.x));
			button.c = new Coord(x, y);
			y += button.sz.y + gap;
		}
	}

	private Button localItemButtonAt(Coord localPoint) {
		Coord rootPoint = outerToRoot(localPoint);
		for (Button button : itemBulkButtons) {
			if (rootPoint.isect(button.c, button.sz))
				return button;
		}
		if ((itemRecipeRequest != null) && (itemRecipeRequest.button != null)
				&& rootPoint.isect(itemRecipeRequest.button.c,
						itemRecipeRequest.button.sz))
			return itemRecipeRequest.button;
		if ((itemEquipRequest != null) && (itemEquipRequest.button != null)
				&& rootPoint.isect(itemEquipRequest.button.c,
						itemEquipRequest.button.sz))
			return itemEquipRequest.button;
		return null;
	}

	public double getDisplayScale() {
		return Config.elementScale(Config.contextScale);
	}

	private void keepOnScreen() {
		if ((parent == null) || (menuOptions.length == 0)
				|| !needsScaledBoundsCheck())
			return;
		int minimumX = 0;
		int minimumY = 0;
		int maximumX = 0;
		int maximumY = 0;
		for (Petal petal : menuOptions) {
			Coord center = Coord.sc(petal.ta, petal.tr);
			Coord upperLeft = center.sub(petal.sz.div(2));
			minimumX = Math.min(minimumX, upperLeft.x);
			minimumY = Math.min(minimumY, upperLeft.y);
			maximumX = Math.max(maximumX, upperLeft.x + petal.sz.x);
			maximumY = Math.max(maximumY, upperLeft.y + petal.sz.y);
		}
		double scale = getDisplayScale();
		int displayLeft = (int) Math.floor(minimumX * scale);
		int displayTop = (int) Math.floor(minimumY * scale);
		int displayRight = (int) Math.ceil(maximumX * scale);
		int displayBottom = (int) Math.ceil(maximumY * scale);
		if ((c.x + displayLeft) < 0)
			c.x = -displayLeft;
		else if ((c.x + displayRight) > parent.sz.x)
			c.x = parent.sz.x - displayRight;
		if ((c.y + displayTop) < 0)
			c.y = -displayTop;
		else if ((c.y + displayBottom) > parent.sz.y)
			c.y = parent.sz.y - displayBottom;
	}

	public boolean mousedown(Coord c, int button) {
		if (anim != null)
			return (true);
		Button localButton = localItemButtonAt(c);
		if (localButton != null) {
			Coord rootPoint = outerToRoot(c);
			return localButton.mousedown(rootPoint.sub(localButton.c), button);
		}
		if (!super.mousedown(c, button))
			wdgmsg("cl", -1);
		return (true);
	}

	// Kerri:
	public void SelectOpt(String OptName) {
		for (int i = 0; i < menuOptions.length; i++) {
			if (menuOptions[i].name.equals(OptName)) {
				wdgmsg(this, "cl", menuOptions[i].num);
				break;
			}
		}
	}

	// Kerri:
	public boolean haveOpt(String name) {
		for (int i = 0; i < menuOptions.length; i++) {
			if (menuOptions[i].name.equals(name))
				return true;
		}
		return false;
	}
	
	// Kerri:
	public void closeMenu() {
		wdgmsg("cl", -1);
	}

	private void cancelForLocalRecipe() {
		wdgmsg("cl", -1);
		anim = new Cancel();
		ui.grabmouse(null);
		ui.grabkeys(null);
	}

	public void uimsg(String msg, Object... args) {
		if (msg == "cancel") {
			if (ui.mapview != null)
				ui.mapview.cancelQualitySurveyFlowerAction();
			if (bulkItemAction != null)
				bulkItemAction.menuCancelled(this);
			anim = new Cancel();
			ui.grabmouse(null);
			ui.grabkeys(null);
		} else if (msg == "act") {
			int option = (Integer) args[0];
			if ((option < 0) || (option >= menuOptions.length)) {
				if (ui.mapview != null)
					ui.mapview.cancelQualitySurveyFlowerAction();
				anim = new Cancel();
				ui.grabmouse(null);
				ui.grabkeys(null);
				return;
			}
			if (ui.mapview != null)
				ui.mapview.armQualitySurveyForFlowerAction(menuOptions[option].name);
			if (bulkItemAction != null)
				bulkItemAction.menuAccepted(this);
			anim = new Chosen(menuOptions[option]);
			ui.grabmouse(null);
			ui.grabkeys(null);
		}
	}

	public void draw(GOut g) {
		if (anim != null)
			anim.tick();
		super.draw(g);
	}
	
	

	@Override
	public void unlink() {
		BulkItemActionRunner runner = bulkItemAction;
		bulkItemAction = null;
		if (itemRecipeRequest != null) {
			itemRecipeRequest.menu = null;
			clearLocalItemButtons();
			itemRecipeRequest = null;
		}
		if (itemEquipRequest != null) {
			itemEquipRequest.menu = null;
			clearLocalItemButtons();
			itemEquipRequest = null;
		}
		super.unlink();
		if (ui.popupMenu == this)
			ui.popupMenu = null;
		if (runner != null)
			runner.menuClosed(this);
	}

	public boolean type(char key, java.awt.event.KeyEvent ev) {
		if ((key >= '0') && (key <= '9')) {
			int opt = (key == '0') ? 10 : (key - '1');
			if (opt >= menuOptions.length)
				return true;
			wdgmsg("cl", opt);
			ui.grabkeys(null);
			return (true);
		} else if (key == 27) {
			wdgmsg("cl", -1);
			ui.grabkeys(null);
			return (true);
		}
		return (false);
	}
}
