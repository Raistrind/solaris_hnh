package haven;

import java.awt.Color;

/** Clickable in-client handbook link used beside contextual UI values. */
public class KnowledgeLink extends Label {
	private final String documentId;
	private final String help;

	public KnowledgeLink(Coord c, Widget parent, String text, String documentId,
			String help) {
		super(c, parent, text);
		this.documentId = documentId;
		this.help = help;
		setcolor(new Color(150, 205, 255));
	}

	public Object tooltip(Coord c, boolean again) {
		KnowledgeWindow.setDocumentContext(documentId);
		return help + (Config.showTerminologyLinks
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
