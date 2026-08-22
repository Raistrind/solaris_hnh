package haven;

import java.awt.Color;

/** Clickable in-client handbook link used beside contextual UI values. */
public class KnowledgeLink extends Label {
	private final String documentId;
	private final String help;
	private final String specializationStat;

	public KnowledgeLink(Coord c, Widget parent, String text, String documentId,
			String help) {
		this(c, parent, text, documentId, help, null);
	}

	public KnowledgeLink(Coord c, Widget parent, String text, String documentId,
			String help, String specializationStat) {
		super(c, parent, text);
		this.documentId = documentId;
		this.help = help;
		this.specializationStat = specializationStat;
		updateColor();
	}

	private void updateColor() {
		Color target = (specializationStat != null)
				&& Specialization.isRecommendedStat(specializationStat)
				? new Color(255, 215, 90) : new Color(150, 205, 255);
		if (!target.equals(col))
			setcolor(target);
	}

	public void draw(GOut g) {
		updateColor();
		super.draw(g);
	}

	public Object tooltip(Coord c, boolean again) {
		KnowledgeWindow.setDocumentContext(documentId);
		String currentHelp = (specializationStat == null) ? help
				: KnowledgeBase.statTooltip(specializationStat);
		return currentHelp + (Config.showTerminologyLinks
				? "\nClick to open the offline handbook entry." : "");
	}

	public boolean mousedown(Coord c, int button) {
		if ((button == 1) && Config.showTerminologyLinks) {
			KnowledgeWindow.open(ui, documentId);
			return true;
		}
		return false;
	}
}
