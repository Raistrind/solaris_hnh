package union;

import java.util.TreeMap;

import union.jsbot.JSGUI_Button;
import union.jsbot.JSGUI_CheckBox;
import union.jsbot.JSGUI_Label;
import union.jsbot.JSGUI_TextEntry;
import union.jsbot.JSGUI_Window;

import haven.*;

public class JSGUI {
	private static TreeMap<Integer, Widget> local_widgets = new TreeMap<Integer, Widget>();
	private static int local_index = 0;
	
	public static abstract class JSGUI_Widget {
		protected int wdgid;
		
		public JSGUI_Widget(int id) {
			wdgid = id;
		}
		
		protected Widget wdg() {
			Object wdg;
			synchronized (local_widgets) {
				wdg = local_widgets.get(wdgid);
			}
			if (wdg instanceof Widget) {
				return (Widget) wdg;
			} else
				return null;
		}
		
		public boolean isActual() {
			return wdg() != null;
		}
		
		public void destroy() {
			Widget widget = wdg();
			if (widget == null)
				return;
			synchronized (widget.ui) {
				widget.destroyAll();
				synchronized (local_widgets) {
					local_widgets.remove(wdgid);
				}
			}
		}
	}

	private static int registerWidget(Widget widget) {
		synchronized (local_widgets) {
			local_index++;
			local_widgets.put(local_index, widget);
			return local_index;
		}
	}
	
	public static JSGUI_Window createWindow(Coord pos, Coord size, String caption) {
		synchronized (UI.instance) {
			Window wnd = new Window(pos, size, UI.instance.root, caption);
			return new JSGUI_Window(registerWidget(wnd));
		}
	}
	
	public static JSGUI_Button createButton(JSGUI_Widget parent, Coord pos, int width, String text) {
		Widget parentWidget = parent.wdg();
		synchronized (parentWidget.ui) {
			Button btn = new Button(pos, width, parentWidget, text);
			return new JSGUI_Button(registerWidget(btn));
		}
	}
	
	public static JSGUI_Label createLabel(JSGUI_Widget parent, Coord pos, String text) {
		Widget parentWidget = parent.wdg();
		synchronized (parentWidget.ui) {
			Label lbl = new Label(pos, parentWidget, text);
			return new JSGUI_Label(registerWidget(lbl));
		}
	}
	
	public static JSGUI_TextEntry createEntry(JSGUI_Widget parent, Coord pos, Coord size, String deftext) {
		Widget parentWidget = parent.wdg();
		synchronized (parentWidget.ui) {
			TextEntry entry = new TextEntry(pos, size, parentWidget, deftext);
			return new JSGUI_TextEntry(registerWidget(entry));
		}
	}
	
	public static JSGUI_CheckBox createBox(JSGUI_Widget parent, Coord pos, String text) {
		Widget parentWidget = parent.wdg();
		synchronized (parentWidget.ui) {
			CheckBox cbox = new CheckBox(pos, parentWidget, text);
			return new JSGUI_CheckBox(registerWidget(cbox));
		}
	}
	
	public static JSGUI_Widget unWrapGUI_Widget(Object obj) {
		if (obj instanceof org.mozilla.javascript.Wrapper) {
			Object temp = ((org.mozilla.javascript.Wrapper)obj).unwrap();
			if (temp instanceof JSGUI_Widget)
				return (JSGUI_Widget) temp;
		}
		return null;
	}
}
