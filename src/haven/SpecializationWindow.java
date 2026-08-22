package haven;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.List;

/** Interactive, local-only specialization planner. */
public class SpecializationWindow extends Window {
	private static final Coord CONTENT_SIZE = new Coord(780, 480);
	private static final Text.Foundry LIST_FONT = new Text.Foundry(new Font(
			"SansSerif", Font.PLAIN, 11), Color.WHITE);
	private static final RichText.Foundry BODY_FONT = new RichText.Foundry(
			TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, 11);
	private static SpecializationWindow instance;
	private final Label primaryLabel;
	private final Label secondaryLabel;
	private final GoalList goalList;
	private final RichTextBox details;
	private final TextEntry customGoal;
	private final TextEntry customFocus;
	private final Label status;
	private Specialization.GoalView selected;
	private int lastGeneration = -1;

	public static void toggle(UI ui) {
		if ((ui == null) || (ui.root == null))
			return;
		if ((instance != null) && (instance.ui == ui)) {
			ui.destroy(instance);
			return;
		}
		open(ui);
	}

	public static void open(UI ui) {
		if ((ui == null) || (ui.root == null))
			return;
		if ((instance == null) || (instance.ui != ui))
			instance = new SpecializationWindow(ui);
		else {
			instance.show();
			instance.raise();
		}
	}

	private SpecializationWindow(UI ui) {
		super(MainFrame.getCenterPoint().sub(CONTENT_SIZE.div(2)), CONTENT_SIZE,
				ui.root, "Specialization Planner");
		justclose = true;
		primaryLabel = new Label(new Coord(0, 2), this, "");
		secondaryLabel = new Label(new Coord(200, 2), this, "");
		new Button(new Coord(0, 23), 185, this, "Choose primary path") {
			public void click() {
				SpecializationPicker.open(ui, true);
			}
		};
		new Button(new Coord(200, 23), 185, this, "Choose secondary path") {
			public void click() {
				SpecializationPicker.open(ui, false);
			}
		};
		new Button(new Coord(600, 23), 170, this, "Open path handbook") {
			public void click() {
				KnowledgeWindow.open(ui, "specialization-current");
			}
		};

		goalList = new GoalList(new Coord(0, 55), new Coord(360, 292), this);
		details = new RichTextBox(new Coord(370, 55), new Coord(400, 292), this,
				"", BODY_FONT);
		details.bg = new Color(10, 10, 10, 210);

		new Button(new Coord(0, 356), 58, this, "Done") {
			public void click() {
				if (selected != null)
					Specialization.toggleDone(selected.ref);
			}
		};
		new Button(new Coord(62, 356), 58, this, "Skip") {
			public void click() {
				if (selected != null)
					Specialization.toggleSkipped(selected.ref);
			}
		};
		new Button(new Coord(124, 356), 58, this, "Pin") {
			public void click() {
				if (selected != null)
					Specialization.togglePinned(selected.ref);
			}
		};
		new Button(new Coord(186, 356), 78, this, "Remind...") {
			public void click() {
				if (selected != null)
					new ReminderEditor(MainFrame.getCenterPoint().sub(155, 70),
							ui.root, selected);
			}
		};
		new Button(new Coord(268, 356), 92, this, "Mark here") {
			public void click() {
				if (selected == null)
					return;
				if (Specialization.markCurrentLocation(ui, selected.title))
					showStatus("Added a world-map marker at the current position.");
				else
					showStatus("Player position is not available yet.");
			}
		};

		new Label(new Coord(0, 389), this, "Custom goal:");
		customGoal = new TextEntry(new Coord(78, 386), new Coord(345, 20),
				this, "");
		new Button(new Coord(428, 386), 55, this, "Add") {
			public void click() {
				Specialization.addCustomGoal(customGoal.text);
				customGoal.settext("");
			}
		};
		new Button(new Coord(488, 386), 55, this, "Up") {
			public void click() {
				if (selected != null)
					Specialization.moveGoal(selected.ref, -1);
			}
		};
		new Button(new Coord(548, 386), 55, this, "Down") {
			public void click() {
				if (selected != null)
					Specialization.moveGoal(selected.ref, 1);
			}
		};
		new Button(new Coord(608, 386), 75, this, "Remove") {
			public void click() {
				if ((selected != null) && selected.custom) {
					Specialization.removeCustomGoal(selected.ref);
					selected = null;
				}
			}
		};

		new Label(new Coord(0, 420), this, "Custom focus:");
		customFocus = new TextEntry(new Coord(88, 417), new Coord(485, 20),
				this, Specialization.customKeywords());
		customFocus.tooltip = "Comma-separated item, recipe, category or stat keywords";
		new Button(new Coord(578, 417), 105, this, "Save keywords") {
			public void click() {
				Specialization.setCustomKeywords(customFocus.text);
				showStatus("Saved custom focus keywords.");
			}
		};
		status = new Label(new Coord(0, 452), this,
				"Select a goal; progress is advisory and saved locally.");
		refresh();
	}

	private void showStatus(String message) {
		status.settext(message);
		if ((ui != null) && (ui.slenhud != null))
			ui.slenhud.error(message);
	}

	private void refresh() {
		lastGeneration = Specialization.generation();
		primaryLabel.settext("Primary: " + Specialization
				.pathName(Config.specializationPrimary));
		secondaryLabel.settext("Secondary: " + Specialization
				.pathName(Config.specializationSecondary));
		String selectedRef = (selected == null) ? null : selected.ref;
		goalList.setGoals(Specialization.goals());
		selected = (selectedRef == null) ? null : Specialization.goal(selectedRef);
		goalList.selected = selected;
		showDetails();
	}

	private void select(Specialization.GoalView goal) {
		selected = goal;
		goalList.selected = goal;
		showDetails();
	}

	private void showDetails() {
		if (selected == null) {
			List<Specialization.GoalView> next = Specialization.nextGoals(3);
			StringBuilder body = new StringBuilder();
			body.append("$size[15]{$b{Next objectives}}\n\n");
			if (next.isEmpty())
				body.append("Select a path or add a custom goal.");
			for (Specialization.GoalView goal : next)
				body.append("- ").append(RichText.Parser.quote(goal.title))
						.append("\n");
			int[] progress = Specialization.progress();
			body.append("\nProgress: ").append(progress[0]).append(" / ")
					.append(progress[1]).append(" non-skipped goals.\n\n")
					.append("Select a row to see its reason and controls.");
			details.settext(body.toString());
			return;
		}
		StringBuilder body = new StringBuilder();
		body.append("$size[15]{$b{").append(RichText.Parser.quote(selected.title))
				.append("}}\n\n")
				.append("Path: ").append(RichText.Parser.quote(Specialization
						.pathName(selected.pathId))).append("\n")
				.append("Stage: ").append(RichText.Parser.quote(selected.stage))
				.append("\nStatus: ");
		int state = Specialization.state(selected.ref);
		body.append(state == 1 ? "Completed" : state == 2 ? "Skipped" : "Open");
		if (Specialization.pinned(selected.ref))
			body.append(" and pinned");
		body.append("\n\n").append(RichText.Parser.quote(selected.detail));
		String reminder = Specialization.reminderText(selected.ref);
		if (reminder.length() > 0)
			body.append("\n\n$col[255,210,100]{")
					.append(RichText.Parser.quote(reminder)).append("}");
		Specialization.Path path = Specialization.path(selected.pathId);
		if (!path.id.equals(Specialization.NONE)) {
			body.append("\n\n$b{Useful tools}\n")
					.append(RichText.Parser.quote(path.tools))
					.append("\n\n$b{Location guidance}\n")
					.append(RichText.Parser.quote(path.locations));
		}
		body.append("\n\n$b{Controls}\nDone toggles completion. Skip keeps it out of progress. Pin moves it into the next-goal list. Up and Down reorder goals. Remind creates a local timer. Mark here adds a world-map marker.");
		details.settext(body.toString());
	}

	public void update(long dt) {
		if (lastGeneration != Specialization.generation())
			refresh();
		super.update(dt);
	}

	public void destroy() {
		if (instance == this)
			instance = null;
		super.destroy();
	}

	private class GoalList extends Widget {
		private static final int ROW_HEIGHT = 21;
		private List<Specialization.GoalView> entries = new ArrayList<Specialization.GoalView>();
		private List<Text> labels = new ArrayList<Text>();
		private int scroll = 0;
		Specialization.GoalView selected;

		GoalList(Coord c, Coord sz, Widget parent) {
			super(c, sz, parent);
		}

		void setGoals(List<Specialization.GoalView> entries) {
			for (Text label : labels)
				label.tex().dispose();
			this.entries = entries;
			labels = new ArrayList<Text>();
			for (Specialization.GoalView goal : entries) {
				int state = Specialization.state(goal.ref);
				String marker = state == 1 ? "[x]" : state == 2 ? "[-]"
						: Specialization.pinned(goal.ref) ? "[P]" : "[ ]";
				labels.add(LIST_FONT.render(marker + " " + goal.stage + " - "
						+ goal.title));
			}
			clamp();
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
				Specialization.GoalView goal = entries.get(index);
				int y = row * ROW_HEIGHT;
				if ((selected != null) && selected.ref.equals(goal.ref)) {
					g.chcolor(new Color(70, 90, 120, 220));
					g.frect(new Coord(0, y), new Coord(sz.x, ROW_HEIGHT));
					g.chcolor();
				}
				if (Specialization.state(goal.ref) == 1)
					g.chcolor(new Color(120, 215, 130));
				else if (Specialization.state(goal.ref) == 2)
					g.chcolor(new Color(145, 145, 145));
				else if (Specialization.pinned(goal.ref))
					g.chcolor(new Color(255, 215, 90));
				g.image(labels.get(index).tex(), new Coord(6, y + 2));
				g.chcolor();
			}
			g.chcolor(Color.GRAY);
			g.rect(Coord.z, sz);
			g.chcolor();
		}

		public boolean mousedown(Coord c, int button) {
			int index = scroll + (c.y / ROW_HEIGHT);
			if ((index < 0) || (index >= entries.size()))
				return false;
			Specialization.GoalView goal = entries.get(index);
			select(goal);
			if (button == 2)
				Specialization.togglePinned(goal.ref);
			else if (button == 3)
				Specialization.toggleSkipped(goal.ref);
			return true;
		}

		public boolean mousewheel(Coord c, int amount) {
			scroll += amount;
			clamp();
			return true;
		}
	}

	private class ReminderEditor extends Window {
		private final Specialization.GoalView goal;
		private final TextEntry minutes;

		ReminderEditor(Coord c, Widget parent, Specialization.GoalView goal) {
			super(c, new Coord(310, 125), parent, "Goal Reminder");
			this.goal = goal;
			justclose = true;
			new Label(new Coord(0, 5), this, goal.title, 300);
			new Label(new Coord(0, 36), this, "Remind in minutes:");
			minutes = new TextEntry(new Coord(120, 33), new Coord(80, 20), this,
					"60");
			new Button(new Coord(0, 70), 100, this, "Set reminder") {
				public void click() {
					try {
						int value = Integer.parseInt(ReminderEditor.this.minutes.text);
						Specialization.setReminder(ReminderEditor.this.goal.ref,
								ReminderEditor.this.goal.title, value);
						ui.destroy(ReminderEditor.this);
					} catch (NumberFormatException e) {
						showStatus("Reminder minutes must be a number.");
					}
				}
			};
			new Button(new Coord(105, 70), 100, this, "Clear reminder") {
				public void click() {
					Specialization.clearReminder(ReminderEditor.this.goal.ref);
					ui.destroy(ReminderEditor.this);
				}
			};
		}
	}
}
