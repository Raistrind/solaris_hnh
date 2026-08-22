package haven;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.List;

/** Searchable offline handbook and locally learned recipe encyclopedia. */
public class KnowledgeWindow extends Window {
	private static final Coord CONTENT_SIZE = new Coord(700, 410);
	private static final RichText.Foundry BODY_FONT = new RichText.Foundry(
			TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, 11);
	private static final Text.Foundry LIST_FONT = new Text.Foundry(new Font(
			"SansSerif", Font.PLAIN, 12), Color.WHITE);
	private static KnowledgeWindow instance;
	private static String contextDocument = "home";
	private static String contextItemName = null;
	private static String contextItemResource = null;
	private static boolean itemContext = false;

	private final TextEntry search;
	private final DocumentList list;
	private final RichTextBox content;
	private final Label title;
	private final Label status;
	private String category = KnowledgeBase.ALL;
	private String lastSearch = null;
	private int lastGeneration = -1;
	private int lastSpecializationGeneration = -1;
	private KnowledgeBase.Document selected;

	public static void toggle(UI ui) {
		if ((instance != null) && (instance.ui == ui)) {
			ui.destroy(instance);
			return;
		}
		open(ui, "home");
	}

	public static void open(UI ui, String documentId) {
		if ((ui == null) || (ui.root == null))
			return;
		if ((instance == null) || (instance.ui != ui))
			instance = new KnowledgeWindow(ui);
		else {
			instance.show();
			instance.raise();
		}
		instance.showDocument(KnowledgeBase.document(documentId));
	}

	public static void openContext(UI ui) {
		if (itemContext && (contextItemName != null)) {
			openForItem(ui, contextItemName, contextItemResource);
		} else {
			open(ui, contextDocument);
		}
	}

	public static void openForItem(UI ui, String name, String resource) {
		open(ui, "inventory-crafting");
		if (instance != null)
			instance.showDocument(KnowledgeBase.reverseDocument(name, resource));
	}

	public static void setDocumentContext(String documentId) {
		if (documentId == null)
			return;
		contextDocument = documentId;
		itemContext = false;
	}

	public static void setItemContext(String name, String resource) {
		if (name == null)
			return;
		contextItemName = name;
		contextItemResource = resource;
		itemContext = true;
	}

	private KnowledgeWindow(UI ui) {
		super(MainFrame.getCenterPoint().sub(CONTENT_SIZE.div(2)), CONTENT_SIZE,
				ui.root, "Handbook");
		justclose = true;
		KnowledgeBase.syncAvailableRecipes(ui);

		search = new TextEntry(new Coord(0, 2), new Coord(205, 20), this, "");
		search.tooltip = "Search guides, terms, stats and recipes";
		addFilterButton(0, 28, 32, KnowledgeBase.ALL);
		addFilterButton(34, 28, 48, KnowledgeBase.GUIDES);
		addFilterButton(84, 28, 44, KnowledgeBase.TERMS);
		addFilterButton(0, 50, 40, KnowledgeBase.STATS);
		addFilterButton(42, 50, 58, KnowledgeBase.RECIPES);
		addFilterButton(102, 50, 48, KnowledgeBase.PATHS);

		list = new DocumentList(new Coord(0, 77), new Coord(210, 308), this);
		title = new Label(new Coord(220, 3), this, "Offline Handbook",
				new Text.Foundry(new Font("SansSerif", Font.BOLD, 14), Color.WHITE));
		content = new RichTextBox(new Coord(220, 25), new Coord(470, 355), this,
				"", BODY_FONT);
		content.bg = new Color(10, 10, 10, 210);
		content.registerclicks = true;
		status = new Label(new Coord(220, 387), this,
				"Shift-hover: details | Middle-click item: reverse recipes");
		refreshList();
		showDocument(KnowledgeBase.document("home"));
	}

	private void addFilterButton(int x, int y, int width, final String filter) {
		new Button(new Coord(x, y), width, this, filter.equals(KnowledgeBase.RECIPES)
				? "Recipes" : filter) {
			public void click() {
				category = filter;
				refreshList();
			}
		};
	}

	private void refreshList() {
		lastSearch = search.text;
		lastGeneration = KnowledgeBase.generation();
		lastSpecializationGeneration = Specialization.generation();
		List<KnowledgeBase.Document> found = KnowledgeBase.documents(category,
				lastSearch);
		list.setDocuments(found);
		list.selected = selected;
		status.settext(found.size() + " entries | Shift-hover: details | Middle-click item: reverse recipes");
	}

	private void showDocument(KnowledgeBase.Document document) {
		if (document == null)
			return;
		selected = document;
		list.selected = document;
		title.settext(document.title + " - " + document.category);
		content.settext(document.body);
		if (!document.id.equals("reverse"))
			setDocumentContext(document.id);
	}

	private void handleAction(String action) {
		if ((action != null) && action.startsWith("doc:"))
			showDocument(KnowledgeBase.document(action.substring(4)));
	}

	public void update(long dt) {
		if ((lastSearch == null) || !lastSearch.equals(search.text)
				|| (lastGeneration != KnowledgeBase.generation())
				|| (lastSpecializationGeneration != Specialization.generation()))
			refreshList();
		super.update(dt);
	}

	public void wdgmsg(Widget sender, String msg, Object... args) {
		if ((sender == content) && msg.equals("click") && (args.length > 0)) {
			handleAction((String) args[0]);
			return;
		}
		super.wdgmsg(sender, msg, args);
	}

	public void destroy() {
		if (instance == this)
			instance = null;
		super.destroy();
	}

	private class DocumentList extends Widget {
		private static final int ROW_HEIGHT = 20;
		private List<KnowledgeBase.Document> entries = new ArrayList<KnowledgeBase.Document>();
		private List<Text> labels = new ArrayList<Text>();
		private int scroll = 0;
		KnowledgeBase.Document selected;

		DocumentList(Coord c, Coord sz, Widget parent) {
			super(c, sz, parent);
		}

		void setDocuments(List<KnowledgeBase.Document> entries) {
			for (Text label : labels)
				label.tex().dispose();
			this.entries = entries;
			scroll = 0;
			labels = new ArrayList<Text>();
			for (KnowledgeBase.Document entry : entries)
				labels.add(LIST_FONT.render(entry.title));
			clampScroll();
		}

		private int visibleRows() {
			return Math.max(1, sz.y / ROW_HEIGHT);
		}

		private void clampScroll() {
			scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size()
					- visibleRows())));
		}

		public void draw(GOut g) {
			clampScroll();
			g.chcolor(new Color(20, 20, 20, 190));
			g.frect(Coord.z, sz);
			g.chcolor();
			for (int row = 0; row < visibleRows(); row++) {
				int index = scroll + row;
				if (index >= entries.size())
					break;
				KnowledgeBase.Document entry = entries.get(index);
				int y = row * ROW_HEIGHT;
				if ((selected != null) && entry.id.equals(selected.id)) {
					g.chcolor(new Color(70, 90, 120, 220));
					g.frect(new Coord(0, y), new Coord(sz.x, ROW_HEIGHT));
					g.chcolor();
				}
				Color categoryColor = entry.category.equals(KnowledgeBase.RECIPES)
						? new Color(220, 170, 70) : entry.category
								.equals(KnowledgeBase.STATS) ? new Color(100, 210, 210)
								: entry.category.equals(KnowledgeBase.TERMS)
										? new Color(190, 130, 240)
										: new Color(100, 190, 110);
				g.chcolor(categoryColor);
				g.frect(new Coord(3, y + 4), new Coord(4, 12));
				g.chcolor();
				g.image(labels.get(index).tex(), new Coord(11, y + 2));
			}
			g.chcolor(Color.GRAY);
			g.rect(Coord.z, sz);
			g.chcolor();
		}

		public boolean mousedown(Coord c, int button) {
			if (button != 1)
				return false;
			int index = scroll + (c.y / ROW_HEIGHT);
			if ((index >= 0) && (index < entries.size()))
				showDocument(entries.get(index));
			return true;
		}

		public boolean mousewheel(Coord c, int amount) {
			scroll += amount;
			clampScroll();
			return true;
		}
	}
}
