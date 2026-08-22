/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Bjorn Johannessen <bjorn@dolda2000.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3.
 */

package haven;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class InventorySorter {
	private static final long STEP_TIMEOUT = 10000;
	private static final int WAIT_TAKE = 1;
	private static final int WAIT_TARGET = 2;
	private static final int WAIT_SOURCE = 3;

	private final Inventory inventory;
	private final Button button;
	private Run run;

	private static class Key {
		final String resourceName;
		final String displayName;
		final int quality;
		final Coord size;

		Key(String resourceName, String displayName, int quality, Coord size) {
			this.resourceName = resourceName;
			this.displayName = displayName;
			this.quality = quality;
			this.size = new Coord(size);
		}

		boolean matches(Key other) {
			return (other != null) && resourceName.equals(other.resourceName) &&
					(quality == other.quality) && size.equals(other.size);
		}
	}

	private static class Entry {
		final Key key;
		Coord position;
		boolean fixed;

		Entry(Key key, Coord position) {
			this.key = key;
			this.position = new Coord(position);
		}

		Entry copy() {
			return new Entry(key, position);
		}
	}

	private static class Desired {
		final Key key;
		final Coord position;

		Desired(Key key, Coord position) {
			this.key = key;
			this.position = new Coord(position);
		}
	}

	private static class Move {
		final Key moving;
		final Key displaced;
		final Coord from;
		final Coord to;

		Move(Key moving, Key displaced, Coord from, Coord to) {
			this.moving = moving;
			this.displaced = displaced;
			this.from = new Coord(from);
			this.to = new Coord(to);
		}
	}

	private static class Plan {
		final List<Move> moves;
		final boolean keptLargeItems;

		Plan(List<Move> moves, boolean keptLargeItems) {
			this.moves = moves;
			this.keptLargeItems = keptLargeItems;
		}
	}

	private static class Run {
		final Plan plan;
		int index;
		int phase;
		long deadline;

		Run(Plan plan) {
			this.plan = plan;
		}

		Move move() {
			return plan.moves.get(index);
		}
	}

	private static final Comparator<Entry> ENTRY_ORDER =
			new Comparator<Entry>() {
		public int compare(Entry a, Entry b) {
			int areaA = a.key.size.x * a.key.size.y;
			int areaB = b.key.size.x * b.key.size.y;
			if (areaA != areaB)
				return areaB - areaA;
			int byName = a.key.displayName.compareToIgnoreCase(b.key.displayName);
			if (byName != 0)
				return byName;
			int byResource = a.key.resourceName.compareTo(b.key.resourceName);
			if (byResource != 0)
				return byResource;
			if (a.key.quality != b.key.quality)
				return b.key.quality - a.key.quality;
			if (a.key.size.y != b.key.size.y)
				return b.key.size.y - a.key.size.y;
			if (a.key.size.x != b.key.size.x)
				return b.key.size.x - a.key.size.x;
			if (a.position.y != b.position.y)
				return a.position.y - b.position.y;
			return a.position.x - b.position.x;
		}
	};

	InventorySorter(Inventory inventory, Button button) {
		this.inventory = inventory;
		this.button = button;
	}

	void toggle() {
		if (run != null) {
			notifyUser("Inventory sorting is already in progress.");
			return;
		}
		if (draggingItem() != null) {
			notifyUser("Put down the held item before sorting.");
			return;
		}

		List<Entry> entries = collectEntries();
		if (entries == null) {
			notifyUser("Cannot sort while an item is still loading.");
			return;
		}
		if (entries.size() < 2) {
			notifyUser("Nothing to sort.");
			return;
		}

		Plan plan = makePlan(entries);
		if (plan == null) {
			notifyUser("This inventory cannot be rearranged safely.");
			return;
		}
		if (plan.moves.isEmpty()) {
			notifyUser(plan.keptLargeItems ?
					"Small items are already sorted; large items were kept in place." :
					"Inventory is already sorted.");
			return;
		}

		run = new Run(plan);
		button.change("Sorting...");
		beginMove();
	}

	void update() {
		if (run == null)
			return;
		if (System.currentTimeMillis() > run.deadline) {
			abort("Inventory sorting timed out.", true);
			return;
		}

		Move move = run.move();
		Item hand = draggingItem();
		if (run.phase == WAIT_TAKE) {
			if (hand == null)
				return;
			if (!matches(hand, move.moving)) {
				abort("Inventory changed while sorting.", true);
				return;
			}
			if (!targetReady(move)) {
				abort("Sort target changed before the move completed.", true);
				return;
			}
			inventory.wdgmsg("drop", new Coord(move.to));
			run.phase = WAIT_TARGET;
			run.deadline = System.currentTimeMillis() + STEP_TIMEOUT;
		} else if (run.phase == WAIT_TARGET) {
			if (move.displaced == null) {
				if ((hand == null) && itemMatchesAt(move.to, move.moving))
					completeMove();
			} else if ((hand != null) && matches(hand, move.displaced) &&
					itemMatchesAt(move.to, move.moving)) {
				inventory.wdgmsg("drop", new Coord(move.from));
				run.phase = WAIT_SOURCE;
				run.deadline = System.currentTimeMillis() + STEP_TIMEOUT;
			}
		} else if (run.phase == WAIT_SOURCE) {
			if ((hand == null) && itemMatchesAt(move.to, move.moving) &&
					itemMatchesAt(move.from, move.displaced))
				completeMove();
		}
	}

	private void beginMove() {
		if (run.index >= run.plan.moves.size()) {
			finish();
			return;
		}
		Move move = run.move();
		if (draggingItem() != null) {
			abort("Inventory sorting stopped because an item is being held.", false);
			return;
		}
		Item item = itemAt(move.from, move.moving);
		if (item == null) {
			abort("Inventory changed while sorting.", false);
			return;
		}
		item.wdgmsg("take", Coord.z);
		run.phase = WAIT_TAKE;
		run.deadline = System.currentTimeMillis() + STEP_TIMEOUT;
	}

	private void completeMove() {
		run.index++;
		beginMove();
	}

	private void finish() {
		boolean keptLargeItems = run.plan.keptLargeItems;
		run = null;
		button.change("Sort", Color.WHITE);
		notifyUser(keptLargeItems ?
				"Inventory sorted; multi-slot items were kept in place." :
				"Inventory sorted.");
	}

	private void abort(String message, boolean recoverHeldItem) {
		if ((run != null) && recoverHeldItem) {
			Item hand = draggingItem();
			if (hand != null)
				inventory.wdgmsg("drop", new Coord(run.move().from));
		}
		run = null;
		button.change("Sort", Color.WHITE);
		notifyUser(message);
	}

	private boolean targetReady(Move move) {
		List<Item> blocking = itemsOverlapping(move.to, move.moving.size);
		if (move.displaced == null)
			return blocking.isEmpty();
		return (blocking.size() == 1) &&
				blocking.get(0).coord().equals(move.to) &&
				matches(blocking.get(0), move.displaced);
	}

	private List<Entry> collectEntries() {
		List<Entry> entries = new ArrayList<Entry>();
		for (Widget wdg = inventory.child; wdg != null; wdg = wdg.next) {
			if (!wdg.visible || !(wdg instanceof Item))
				continue;
			Item item = (Item) wdg;
			String resourceName = item.GetResName();
			if (resourceName == null)
				return null;
			Coord size = normalizedSize(item);
			Coord position = item.coord();
			if (!within(position, size, inventory.size()))
				return null;
			Resource resource = item.res.get();
			String displayName = (resource == null) ? null :
					KnowledgeBase.displayName(resource);
			if ((displayName == null) || (displayName.length() == 0))
				displayName = KnowledgeBase.humanize(resourceName);
			entries.add(new Entry(new Key(resourceName, displayName,
					item.quality, size), position));
		}
		return entries;
	}

	private Plan makePlan(List<Entry> entries) {
		List<Desired> desired = packedLayout(entries);
		if (desired != null) {
			List<Move> moves = simulate(entries, desired, false);
			if (moves != null)
				return new Plan(moves, false);
		}

		List<Desired> smallDesired = fixedLargeLayout(entries);
		if (smallDesired == null)
			return null;
		List<Move> smallMoves = simulate(entries, smallDesired, true);
		return (smallMoves == null) ? null : new Plan(smallMoves, true);
	}

	private List<Desired> packedLayout(List<Entry> entries) {
		List<Entry> ordered = copies(entries);
		Collections.sort(ordered, ENTRY_ORDER);
		boolean[][] occupied = new boolean[inventory.size().x][inventory.size().y];
		List<Desired> desired = new ArrayList<Desired>();
		for (Entry entry : ordered) {
			Coord target = firstFree(occupied, entry.key.size);
			if (target == null)
				return null;
			mark(occupied, target, entry.key.size);
			desired.add(new Desired(entry.key, target));
		}
		return desired;
	}

	private List<Desired> fixedLargeLayout(List<Entry> entries) {
		boolean[][] occupied = new boolean[inventory.size().x][inventory.size().y];
		List<Entry> small = new ArrayList<Entry>();
		for (Entry entry : entries) {
			if (isOneCell(entry.key.size))
				small.add(entry.copy());
			else
				mark(occupied, entry.position, entry.key.size);
		}
		Collections.sort(small, ENTRY_ORDER);
		List<Desired> desired = new ArrayList<Desired>();
		for (Entry entry : small) {
			Coord target = firstFree(occupied, entry.key.size);
			if (target == null)
				return null;
			mark(occupied, target, entry.key.size);
			desired.add(new Desired(entry.key, target));
		}
		return desired;
	}

	private List<Move> simulate(List<Entry> original,
			List<Desired> desired, boolean keepLargeItems) {
		List<Entry> entries = copies(original);
		if (keepLargeItems) {
			for (Entry entry : entries)
				entry.fixed = !isOneCell(entry.key.size);
		}
		List<Move> moves = new ArrayList<Move>();
		for (Desired target : desired) {
			Entry alreadyThere = matchingAt(entries, target);
			if (alreadyThere != null) {
				alreadyThere.fixed = true;
				continue;
			}
			Entry moving = matchingUnfixed(entries, target.key);
			if (moving == null)
				return null;
			Coord source = new Coord(moving.position);
			List<Entry> blocking = overlaps(entries, moving, target.position,
					target.key.size);
			if (blocking.isEmpty()) {
				moves.add(new Move(moving.key, null, source, target.position));
				moving.position = new Coord(target.position);
			} else if (blocking.size() == 1) {
				Entry displaced = blocking.get(0);
				if (displaced.fixed || !displaced.position.equals(target.position) ||
						!displaced.key.size.equals(moving.key.size) ||
						!regionFree(entries, moving, displaced, source,
								displaced.key.size))
					return null;
				moves.add(new Move(moving.key, displaced.key, source,
						target.position));
				moving.position = new Coord(target.position);
				displaced.position = source;
			} else {
				return null;
			}
			moving.fixed = true;
		}
		return moves;
	}

	private Entry matchingAt(List<Entry> entries, Desired desired) {
		for (Entry entry : entries) {
			if (!entry.fixed && entry.position.equals(desired.position) &&
					entry.key.matches(desired.key))
				return entry;
		}
		return null;
	}

	private Entry matchingUnfixed(List<Entry> entries, Key key) {
		for (Entry entry : entries) {
			if (!entry.fixed && entry.key.matches(key))
				return entry;
		}
		return null;
	}

	private List<Entry> overlaps(List<Entry> entries, Entry excluded,
			Coord position, Coord size) {
		List<Entry> found = new ArrayList<Entry>();
		for (Entry entry : entries) {
			if ((entry != excluded) && rectanglesOverlap(entry.position,
					entry.key.size, position, size))
				found.add(entry);
		}
		return found;
	}

	private boolean regionFree(List<Entry> entries, Entry firstExcluded,
			Entry secondExcluded, Coord position, Coord size) {
		if (!within(position, size, inventory.size()))
			return false;
		for (Entry entry : entries) {
			if ((entry != firstExcluded) && (entry != secondExcluded) &&
					rectanglesOverlap(entry.position, entry.key.size,
							position, size))
				return false;
		}
		return true;
	}

	private Coord firstFree(boolean[][] occupied, Coord size) {
		for (int y = 0; y <= inventory.size().y - size.y; y++) {
			for (int x = 0; x <= inventory.size().x - size.x; x++) {
				boolean free = true;
				for (int yy = 0; yy < size.y && free; yy++) {
					for (int xx = 0; xx < size.x; xx++) {
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

	private static void mark(boolean[][] occupied, Coord position,
			Coord size) {
		for (int y = 0; y < size.y; y++)
			for (int x = 0; x < size.x; x++)
				occupied[position.x + x][position.y + y] = true;
	}

	private static List<Entry> copies(List<Entry> entries) {
		List<Entry> copies = new ArrayList<Entry>();
		for (Entry entry : entries)
			copies.add(entry.copy());
		return copies;
	}

	private Item itemAt(Coord position, Key key) {
		for (Widget wdg = inventory.child; wdg != null; wdg = wdg.next) {
			if (wdg.visible && wdg instanceof Item) {
				Item item = (Item) wdg;
				if (item.coord().equals(position) && matches(item, key))
					return item;
			}
		}
		return null;
	}

	private boolean itemMatchesAt(Coord position, Key key) {
		return itemAt(position, key) != null;
	}

	private List<Item> itemsOverlapping(Coord position, Coord size) {
		List<Item> items = new ArrayList<Item>();
		for (Widget wdg = inventory.child; wdg != null; wdg = wdg.next) {
			if (wdg.visible && wdg instanceof Item) {
				Item item = (Item) wdg;
				if (rectanglesOverlap(item.coord(), normalizedSize(item),
						position, size))
					items.add(item);
			}
		}
		return items;
	}

	private Item draggingItem() {
		for (Widget wdg = inventory.ui.root.child; wdg != null;
				wdg = wdg.next) {
			if ((wdg instanceof Item) && ((Item) wdg).isDragging)
				return (Item) wdg;
		}
		return null;
	}

	private boolean matches(Item item, Key key) {
		String resourceName = item.GetResName();
		return (resourceName != null) && resourceName.equals(key.resourceName) &&
				(item.quality == key.quality) &&
				normalizedSize(item).equals(key.size);
	}

	private static Coord normalizedSize(Item item) {
		Coord size = item.size();
		return new Coord(Math.max(1, size.x), Math.max(1, size.y));
	}

	private static boolean isOneCell(Coord size) {
		return (size.x == 1) && (size.y == 1);
	}

	private static boolean within(Coord position, Coord size, Coord bounds) {
		return (position.x >= 0) && (position.y >= 0) &&
				(position.x + size.x <= bounds.x) &&
				(position.y + size.y <= bounds.y);
	}

	private static boolean rectanglesOverlap(Coord firstPosition,
			Coord firstSize, Coord secondPosition, Coord secondSize) {
		return (firstPosition.x < secondPosition.x + secondSize.x) &&
				(firstPosition.x + firstSize.x > secondPosition.x) &&
				(firstPosition.y < secondPosition.y + secondSize.y) &&
				(firstPosition.y + firstSize.y > secondPosition.y);
	}

	private void notifyUser(String message) {
		if (inventory.ui.slenhud != null)
			inventory.ui.slenhud.error(message);
		else
			inventory.ui.cons.out.println(message);
	}
}
