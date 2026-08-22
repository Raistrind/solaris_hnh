package haven;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.List;

/** Path chooser shared by Options and the specialization planner. */
public class SpecializationPicker extends Window {
	private static final Text.Foundry LIST_FONT = new Text.Foundry(new Font(
			"SansSerif", Font.PLAIN, 12), Color.WHITE);
	private static final RichText.Foundry BODY_FONT = new RichText.Foundry(
			TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, 11);
	private final boolean primary;
	private final PathList list;
	private final RichTextBox details;
	private final Button choose;
	private Specialization.Path selected;

	public static void open(UI ui, boolean primary) {
		if ((ui == null) || (ui.root == null))
			return;
		new SpecializationPicker(MainFrame.getCenterPoint().sub(310, 205),
				ui.root, primary);
	}

	private SpecializationPicker(Coord c, Widget parent, boolean primary) {
		super(c, new Coord(620, 410), parent, primary ? "Choose Primary Path"
				: "Choose Secondary Path");
		this.primary = primary;
		justclose = true;
		new Label(new Coord(0, 2), this,
				"Paths are recommendations only and never restrict actions.");
		list = new PathList(new Coord(0, 27), new Coord(215, 330), this,
				Specialization.paths());
		details = new RichTextBox(new Coord(225, 27), new Coord(385, 330),
				this, "", BODY_FONT);
		details.bg = new Color(10, 10, 10, 210);
		choose = new Button(new Coord(330, 370), 170, this, "Use selected path") {
			public void click() {
				if (selected != null) {
					Specialization.setPath(SpecializationPicker.this.primary,
							selected.id);
					ui.destroy(SpecializationPicker.this);
				}
			}
		};
		new Button(new Coord(505, 370), 105, this, "Cancel") {
			public void click() {
				ui.destroy(SpecializationPicker.this);
			}
		};
		String current = primary ? Config.specializationPrimary
				: Config.specializationSecondary;
		select(Specialization.path(current));
	}

	private void select(Specialization.Path path) {
		selected = path;
		list.selected = path;
		StringBuilder body = new StringBuilder();
		body.append("$size[15]{$b{").append(RichText.Parser.quote(path.name))
				.append("}}\n\n").append(RichText.Parser.quote(path.summary));
		if (!path.id.equals(Specialization.NONE)) {
			body.append("\n\n$b{Recommended attributes}\n");
			if (path.stats.isEmpty())
				body.append("Defined by your custom focus keywords.\n");
			for (Specialization.Focus focus : path.stats)
				body.append("- ").append(focus.name).append("\n");
			body.append("\n$b{Skill priorities}\n");
			if (path.skills.isEmpty())
				body.append("Defined by your custom focus keywords.\n");
			for (Specialization.Focus focus : path.skills)
				body.append("- ").append(focus.name).append("\n");
			body.append("\n$b{First milestone}\n");
			if (path.goals.isEmpty())
				body.append("Add custom goals in the planner.");
			else
				body.append(RichText.Parser.quote(path.goals.get(0).title));
		}
		details.settext(body.toString());
		choose.change(path.id.equals(Specialization.NONE) ? "Use None"
				: "Use selected path");
	}

	private class PathList extends Widget {
		private static final int ROW_HEIGHT = 22;
		private final List<Specialization.Path> entries;
		private final List<Text> labels = new ArrayList<Text>();
		private int scroll = 0;
		Specialization.Path selected;

		PathList(Coord c, Coord sz, Widget parent,
				List<Specialization.Path> entries) {
			super(c, sz, parent);
			this.entries = entries;
			for (Specialization.Path path : entries)
				labels.add(LIST_FONT.render(path.name));
		}

		private int visibleRows() {
			return Math.max(1, sz.y / ROW_HEIGHT);
		}

		private void clamp() {
			scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size()
					- visibleRows())));
		}

		public void draw(GOut g) {
			clamp();
			g.chcolor(new Color(20, 20, 20, 200));
			g.frect(Coord.z, sz);
			g.chcolor();
			for (int row = 0; row < visibleRows(); row++) {
				int index = scroll + row;
				if (index >= entries.size())
					break;
				Specialization.Path path = entries.get(index);
				int y = row * ROW_HEIGHT;
				if (path == selected) {
					g.chcolor(new Color(80, 95, 120, 220));
					g.frect(new Coord(0, y), new Coord(sz.x, ROW_HEIGHT));
					g.chcolor();
				}
				if (path.id.equals(Specialization.NONE)) {
					g.chcolor(new Color(160, 160, 160));
					g.frect(new Coord(4, y + 5), new Coord(5, 12));
				} else if (path.id.equals(Specialization.CUSTOM)) {
					g.chcolor(new Color(220, 130, 255));
					g.frect(new Coord(4, y + 5), new Coord(5, 12));
				} else {
					g.chcolor(new Color(235, 195, 75));
					g.frect(new Coord(4, y + 5), new Coord(5, 12));
				}
				g.chcolor();
				g.image(labels.get(index).tex(), new Coord(14, y + 3));
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
				select(entries.get(index));
			return true;
		}

		public boolean mousewheel(Coord c, int amount) {
			scroll += amount;
			clamp();
			return true;
		}
	}
}
