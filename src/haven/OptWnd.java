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
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OptWnd extends Window {
    public static final RichText.Foundry foundry = new RichText.Foundry(TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, 10);
	public static final RichText.Foundry foundry11 = new RichText.Foundry(TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, 11);
	public static final RichText.Foundry foundry12 = new RichText.Foundry(TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, 12);
	public static final RichText.Foundry foundry13 = new RichText.Foundry(TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, 13);
	public static final RichText.Foundry foundry14 = new RichText.Foundry(TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, 14);
    private Tabs body;
    private String curcam;
    private Map<String, CamInfo> caminfomap = new HashMap<String, CamInfo>();
    private Map<String, String> camname2type = new HashMap<String, String>();
    private Map<String, String[]> camargs = new HashMap<String, String[]>();
    private Comparator<String> camcomp = new Comparator<String>() {
	public int compare(String a, String b) {
	    if(a.startsWith("The ")) a = a.substring(4);
	    if(b.startsWith("The ")) b = b.substring(4);
	    return (a.compareTo(b));
	}
    };

    private static class CamInfo {
	String name, desc;
	Tabs.Tab args;

	public CamInfo(String name, String desc, Tabs.Tab args) {
	    this.name = name;
	    this.desc = desc;
	    this.args = args;
	}
    }

	public OptWnd(Coord c, Widget parent) {
	super(c, new Coord(550, 540), parent, "Options");

	body = new Tabs(Coord.z, new Coord(520, 540), this) {
	    public void changed(Tab from, Tab to) {
		Utils.setpref("optwndtab", to.btn.text.text);
		from.btn.c.y = 0;
		to.btn.c.y = 0;
		}};
	Widget tab;

	{ /* GENERAL TAB */
	    tab = body.new Tab(new Coord(0, 0), 60, "General");

	    new Button(new Coord(250, 505), 125, tab, "Quit") {
		public void click() {
		    HackThread.tg().interrupt();
		}};
	    new Button(new Coord(50, 505), 125, tab, "Log out") {
		public void click() {
		    ui.sess.close();
		}};

		// removed fullscreen toggle
		
		(new CheckBox(new Coord(10, 30), tab, "Toggle Criminal Acts @ Login") {
			public void changed(boolean val) {
			    Config.toggleCA = val;
			    Config.saveOptions();
			}
		    }).a = Config.toggleCA;
		
		(new CheckBox(new Coord(10, 60), tab, "Toggle Tracking @ Login") {
			public void changed(boolean val) {
			    Config.toggleTR = val;
			    Config.saveOptions();
			}
		    }).a = Config.toggleTR;

	    (new CheckBox(new Coord(10, 120), tab, "New Style Map (Restart Required)") {
		public void changed(boolean val) {
		    Config.new_minimap = val;
		    Config.saveOptions();
		}
	    }).a = Config.new_minimap;
	    
	    (new CheckBox(new Coord(10, 150), tab, "New Style Chat (Restart Required)") {
		public void changed(boolean val) {
		    Config.new_chat = val;
		    Config.saveOptions();
		}
	    }).a = Config.new_chat;
	    
	    (new CheckBox(new Coord(10, 210), tab, "Show Timestamp In Chat") {
		public void changed(boolean val) {
		    Config.timestamp = val;
		    Config.saveOptions();
		}
	    }).a = Config.timestamp;
	    
	    (new CheckBox(new Coord(10, 90), tab, "Show Dowsing Direction") {
		public void changed(boolean val) {
		    Config.showDirection = val;
		    Config.saveOptions();
		}
	    }).a = Config.showDirection;
	    
	    (new CheckBox(new Coord(10, 240), tab, "Display Hearthling Names") {
		public void changed(boolean val) {
		    Config.showNames = val;
		    Config.saveOptions();
		}
	    }).a = Config.showNames;
	    
	    (new CheckBox(new Coord(10, 270), tab, "Always Display Kin Names") {
		public void changed(boolean val) {
		    Config.showOtherNames = val;
		    Config.saveOptions();
		}
	    }).a = Config.showOtherNames;

		(new CheckBox(new Coord(10, 300), tab, "Show Gob Health %") {
			public void changed(boolean val) {
				Config.show_gob_health = val;
				Config.saveOptions();
			}
		}).a = Config.showOtherNames;

		(new CheckBox(new Coord(10, 330), tab, "Show Liquid Meters") {
			public void changed(boolean val) {
				Config.flaskMeters = val;
				Config.saveOptions();
			}
		}).a = Config.showOtherNames;
	    
	    (new CheckBox(new Coord(10, 180), tab, "Enable Emoji's In Chat") {
		public void changed(boolean val) {
		    Config.use_smileys = val;
		    Config.saveOptions();
		}
	    }).a = Config.use_smileys;
	    
	    (new CheckBox(new Coord(220, 90), tab, "Always Show Item Quality") {
		public void changed(boolean val) {
		    Config.showq = val;
		    Config.saveOptions();
		}
	    }).a = Config.showq;
	    
	    (new CheckBox(new Coord(220, 60), tab, "Show Player Moving Path") {
		public void changed(boolean val) {
		    Config.showpath = val;
		    Config.saveOptions();
		}
	    }).a = Config.showpath;
	    
	    (new CheckBox(new Coord(220, 30), tab, "Show All Mob Paths") {
			public void changed(boolean val) {
			    Config.showpathAll = val;
			    Config.saveOptions();
			}
		}).a = Config.showpathAll;

	    (new CheckBox(new Coord(220, 120), tab, "Highlight Objects (Mouse Hover)") {
		public void changed(boolean val) {
		    Config.objectHighlighting = val;
		    Config.saveOptions();
		}
	    }).a = Config.objectHighlighting;
	    
	    (new CheckBox(new Coord(220, 180), tab, "Optimize Claim Highlighting") {
		public void changed(boolean val) {
		    Config.newclaim = val;
		    Config.saveOptions();
		}
	    }).a = Config.newclaim;
	    
	    (new CheckBox(new Coord(220, 210), tab, "Kin Online/Offline Notifications") {
		public void changed(boolean val) {
		    Config.onlineNotifier = val;
		    Config.saveOptions();
		}
	    }).a = Config.onlineNotifier;

	    (new CheckBox(new Coord(220, 240), tab, "Show Day Time") {
	    	public void changed(boolean val) {
	    		Config.showDayTime = val;
	    		Config.saveOptions();
	    	}
	    }).a = Config.showDayTime;
	    
	    (new CheckBox(new Coord(220, 150), tab, "Highlight Objects (Alt Key)") {
		public void changed(boolean val) {
		    Config.objectBlink = val;
		    Config.saveOptions();
		}
	    }).a = Config.objectBlink;
	    
	    (new CheckBox(new Coord(220, 270), tab, "Save Minimaps") {
		public void changed(boolean val) {
		    Config.autoSaveMinimaps = val;
		    Config.saveOptions();
		}
	    }).a = Config.autoSaveMinimaps;
	    
	    (new CheckBox(new Coord(220, 300), tab, "Draw Highlighted Herbs As Icons") {
			public void changed(boolean val) {
			    Config.drawIcons = val;
			    Config.saveOptions();
			}
		}).a = Config.drawIcons;

	    new Label(new Coord(10, 375), tab, "Display scale (restart required):");
	    final boolean[] scaleReady = { false };
	    final RadioGroup displayScale = new RadioGroup(tab) {
		public void changed(int btn, String lbl) {
		    if (scaleReady[0])
			Config.setUIScale(btn + 1);
		}
	    };
	    displayScale.add("1×", new Coord(20, 400));
	    displayScale.add("2×", new Coord(105, 400));
	    displayScale.add("3×", new Coord(190, 400));
	    displayScale.check(Config.uiScale - 1);
	    scaleReady[0] = true;

	}

	{ /* CAMERA TAB */
	    curcam = Utils.getpref("defcam", "border");
	    tab = body.new Tab(new Coord(70, 0), 60, "Camera");

	    final RichTextBox caminfo = new RichTextBox(new Coord(180, 70), new Coord(210, 180), tab, "", foundry);
	    caminfo.bg = new java.awt.Color(0, 0, 0, 64);
	    String dragcam = "\n\n$col[225,200,100,255]{You can drag and recenter with the middle mouse button.}";
	    String fscam = "\n\n$col[225,200,100,255]{Should be used in full-screen mode.}";
	    addinfo("orig",       "The Original",  "The camera centers where you left-click.", null);
	    addinfo("predict",    "The Predictor", "The camera tries to predict where your character is heading - à la Super Mario World - and moves ahead of your character. Works unlike a charm." + dragcam, null);
	    addinfo("border",     "Freestyle",     "You can move around freely within the larger area of the window; the camera only moves along to ensure the character does not reach the edge of the window. Boom chakalak!" + dragcam, null);
	    addinfo("fixed",      "The Fixator",   "The camera is fixed, relative to your character." + dragcam, null);
	    addinfo("kingsquest", "King's Quest",  "The camera is static until your character comes close enough to the edge of the screen, at which point the camera snaps around the edge.", null);
	    addinfo("cake",       "Pan-O-Rama",    "The camera centers at the point between your character and the mouse cursor. It's pantastic!", null);

	    final Tabs cambox = new Tabs(new Coord(100, 60), new Coord(300, 200), tab);
	    Tabs.Tab ctab;
	    ctab = cambox.new Tab();
	    new Label(new Coord(45, 10), ctab, "Fast");
	    new Label(new Coord(45, 180), ctab, "Slow");
	    new Scrollbar(new Coord(60, 20), 160, ctab, 0, 20) {
		{
		    val = Integer.parseInt(Utils.getpref("clicktgtarg1", "10"));
		    setcamargs("clicktgt", calcarg());
		}
		public boolean mouseup(Coord c, int button) {
		    if (super.mouseup(c, button)) {
			setcamargs(curcam, calcarg());
			setcamera(curcam);
			Utils.setpref("clicktgtarg1", String.valueOf(val));
			return (true);
		    }
		    return (false);
		}
		private String calcarg() {
		    return (String.valueOf(Math.cbrt(Math.cbrt(val / 24.0))));
		}};
	    addinfo("clicktgt", "The Target Seeker", "The camera recenters smoothly where you left-click." + dragcam, ctab);
	    /* fixedcake arg */
	    ctab = cambox.new Tab();
	    new Label(new Coord(45, 10), ctab, "Fast");
	    new Label(new Coord(45, 180), ctab, "Slow");
	    new Scrollbar(new Coord(60, 20), 160, ctab, 0, 20) {
		{
			val = Integer.parseInt(Utils.getpref("fixedcakearg1", "10"));
		    setcamargs("fixedcake", calcarg());
		}
		public boolean mouseup(Coord c, int button) {
		    if (super.mouseup(c, button)) {
			setcamargs(curcam, calcarg());
			setcamera(curcam);
			Utils.setpref("fixedcakearg1", String.valueOf(val));
			return (true);
		    }
		    return (false);
		}
		private String calcarg() {
		    return (String.valueOf(Math.pow(1 - (val / 20.0), 2)));
		}};
	    addinfo("fixedcake", "The Borderizer", "The camera is fixed, relative to your character unless you touch one of the screen's edges with the mouse, in which case the camera peeks in that direction." + dragcam + fscam, ctab);

	    final RadioGroup cameras = new RadioGroup(tab) {
		public void changed(int btn, String lbl) {
		    if (camname2type.containsKey(lbl))
				lbl = camname2type.get(lbl);
		    if (!lbl.equals(curcam)) {
				if (camargs.containsKey(lbl))
					setcamargs(lbl, camargs.get(lbl));
				setcamera(lbl);
		    }
		    CamInfo inf = caminfomap.get(lbl);
		    if (inf == null) {
			cambox.showtab(null);
			caminfo.settext("");
		    } else {
			cambox.showtab(inf.args);
			    caminfo.settext(String.format("$size[12]{%s}\n\n$col[200,175,150,255]{%s}", inf.name, inf.desc));
		    }
		    }};
	    List<String> clist = new ArrayList<String>();
	    for (String camtype : MapView.camtypes.keySet())
			clist.add(caminfomap.containsKey(camtype) ? caminfomap.get(camtype).name : camtype);
		Collections.sort(clist, camcomp);
	    	int y = 25;
	    	for (String camname : clist)
				cameras.add(camname, new Coord(10, y += 25));
			cameras.check(caminfomap.containsKey(curcam) ? caminfomap.get(curcam).name : curcam);
	    (new CheckBox(new Coord(10, 270), tab, "Allow zooming with mouse wheel") {
		public void changed(boolean val) {
		    Config.zoom = val;
		    Config.saveOptions();
		}
	    }).a = Config.zoom;
	    (new CheckBox(new Coord(10, 300), tab, "Disable camera borders") {

		public void changed(boolean val) {
		    Config.noborders = val;
		    Config.saveOptions();
		}
	    }).a = Config.noborders;

		new Label(new Coord(10, 360), tab, "WARNING: Disabling camera borders can cause issues when entering a house or cave.");
		new Label(new Coord(10, 380), tab, "Check and uncheck this option if you need to fix this issue.");

	}

		{ /* AUDIO TAB */
			tab = body.new Tab(new Coord(140, 0), 60, "Audio");

			new Label(new Coord(10, 40), tab, "Sound");
			new Frame(new Coord(10, 65), new Coord(20, 206), tab);
			new Label(new Coord(210, 40), tab, "Music");
			new Frame(new Coord(210, 65), new Coord(20, 206), tab);
			final Label sfxvol = new Label(new Coord(35, 69 + (int)(Config.sfxVol * 1.86)),  tab, String.valueOf(100 - getsfxvol()) + " %");
			final Label musicvol = new Label(new Coord(235, 69 + (int)(Config.musicVol * 1.86)),  tab, String.valueOf(100 - getsfxvol()) + " %");
			(new Scrollbar(new Coord(25, 70), 196, tab, 0, 100) {{ val = 100 - Config.sfxVol; }
				public void changed() {
					Config.sfxVol = 100 - val;
					sfxvol.c.y = 69 + (int) (val * 1.86);
					sfxvol.settext(String.valueOf(100 - val) + " %");
					Config.saveOptions();
				}
				public boolean mousewheel(Coord c, int amount) {
					val = Utils.clip(val + amount, min, max);
					changed();
					return (true);
				}
			}).changed();
			(new Scrollbar(new Coord(225, 70), 196, tab, 0, 100) {{ val = 100 - Config.musicVol; }
				public void changed() {
					Config.musicVol = 100 - val;
					Music.setVolume(Config.getMusicVolume());
					musicvol.c.y = 69 + (int) (val * 1.86);
					musicvol.settext(String.valueOf(100 - val) + " %");
					Config.saveOptions();
				}
				public boolean mousewheel(Coord c, int amount) {
					val = Utils.clip(val + amount, min, max);
					changed();
					return (true);
				}
			}).changed();
			(new CheckBox(new Coord(10, 270), tab, "Sound On") {
				public void changed(boolean val) {
					Config.isSoundOn = val;
				}}).a = Config.isSoundOn;

			(new CheckBox(new Coord(210, 270), tab, "Music On") {
				public void changed(boolean val) {
					Config.isMusicOn = val;
					Music.setVolume(Config.getMusicVolume());
				}}).a = Config.isMusicOn;
		}

	{ /* HIDE OBJECTS TAB */
	    tab = body.new Tab(new Coord(210, 0), 80, "Hide Objects");

	    String[][] checkboxesList = { { "Walls", "gfx/arch/walls" },
		    { "Gates", "gfx/arch/gates" },
		    { "Wooden Houses", "gfx/arch/cabin" },
		    { "Stone Mansions", "gfx/arch/inn" },
		    { "Plants", "gfx/terobjs/plants" },
		    { "Trees", "gfx/terobjs/trees" },
		    { "Stones", "gfx/terobjs/bumlings" },
		    { "Bushes", "gfx/tiles/wald" },
		    { "Thicket", "gfx/tiles/dwald" },
		    { "Ridges", "gfx/terobjs/ridges/grass/" }, 
		    { "Mountain Ridges", "gfx/terobjs/ridges/mountain/" }, 
		    { "Blood", "gfx/terobjs/blood" } };//Kerri: for Frey
	    int y = 0;
	    for (final String[] checkbox : checkboxesList) {
		CheckBox chkbox = new CheckBox(new Coord(10, y += 30), tab,
			checkbox[0]) {

		    public void changed(boolean val) {
			if (val) {
			    Config.addhide(checkbox[1]);
			} else {
			    Config.remhide(checkbox[1]);
			}
			Config.saveOptions();
		    }
		};
		chkbox.a = Config.hideObjectList.contains(checkbox[1]);
	    }

	    //scents
	    (new CheckBox(new Coord(200, 30), tab, "Hide Tresspass Scents") {
			public void changed(boolean val) {
			    Config.hideTressp = val;
			    Config.saveOptions();
			}}).a = Config.hideTressp;
	    (new CheckBox(new Coord(200, 60), tab, "Hide Theft Scents") {
			public void changed(boolean val) {
			    Config.hideTheft = val;
			    Config.saveOptions();
			}}).a = Config.hideTheft;
	    (new CheckBox(new Coord(200, 90), tab, "Hide Assault Scents") {
			public void changed(boolean val) {
			    Config.hideAsslt = val;
			    Config.saveOptions();
			}}).a = Config.hideAsslt;
	    (new CheckBox(new Coord(200, 120), tab, "Hide Vandalism Scents") {
			public void changed(boolean val) {
			    Config.hideVand = val;
			    Config.saveOptions();
			}}).a = Config.hideVand;
	    (new CheckBox(new Coord(200, 150), tab, "Hide Battery Scents") {
			public void changed(boolean val) {
			    Config.hideBatt = val;
			    Config.saveOptions();
			}}).a = Config.hideBatt;
	    (new CheckBox(new Coord(200, 180), tab, "Hide Murder Scents") {
			public void changed(boolean val) {
			    Config.hideMurd = val;
			    Config.saveOptions();
			}}).a = Config.hideMurd;
	}

	{ /* CUSTOM TAB */
			tab = body.new Tab(new Coord(300, 0), 60, "Custom");

		new Label(new Coord(15, 40), tab, "R");
		new Label(new Coord(40, 40), tab, "G");
		new Label(new Coord(65, 40), tab, "B");
		new Label(new Coord(90, 40), tab, "A");

		new Frame(new Coord(10, 65), new Coord(20, 160), tab);
		new Frame(new Coord(35, 65), new Coord(20, 160), tab);
		new Frame(new Coord(60, 65), new Coord(20, 160), tab);
		new Frame(new Coord(85, 65), new Coord(20, 160), tab);

		final RichTextBox uiinfo = new RichTextBox(new Coord(140, 65), new Coord(200, 160), tab, "Use the sliders on the left to change the UI color / transparency. These changes will automatically save.", foundry12);
		uiinfo.bg = new java.awt.Color(0, 0, 0, 64);

		(new Scrollbar(new Coord(25, 70), 150, tab, 0, 255) {{ val = max - Config.uiColor.getRed(); }
			public void changed() {
				Config.uiColor = new Color(max - val, Config.uiColor.getGreen(),
						Config.uiColor.getBlue(), Config.uiColor.getAlpha());
				Config.saveOptions();
			}
			public boolean mousewheel(Coord c, int amount) {
				val = Utils.clip(val + amount, min, max);
				changed();
				return (true);
			}
		}).changed();
		(new Scrollbar(new Coord(50, 70), 150, tab, 0, 255) {{ val = max - Config.uiColor.getGreen(); }
			public void changed() {
				Config.uiColor = new Color(Config.uiColor.getRed(), max - val,
						Config.uiColor.getBlue(), Config.uiColor.getAlpha());
				Config.saveOptions();
			}
			public boolean mousewheel(Coord c, int amount) {
				val = Utils.clip(val + amount, min, max);
				changed();
				return (true);
			}
		}).changed();
		(new Scrollbar(new Coord(75, 70), 150, tab, 0, 255) {{ val = max - Config.uiColor.getBlue(); }
			public void changed() {
				Config.uiColor = new Color(Config.uiColor.getRed(), Config.uiColor.getGreen(),
						max - val, Config.uiColor.getAlpha());
				Config.saveOptions();
			}
			public boolean mousewheel(Coord c, int amount) {
				val = Utils.clip(val + amount, min, max);
				changed();
				return (true);
			}
		}).changed();

		(new Scrollbar(new Coord(100, 70), 150, tab, 0, 255) {{ val = max - Config.uiColor.getAlpha(); }
			public void changed() {
				Config.uiColor = new Color(Config.uiColor.getRed(), Config.uiColor.getGreen(),
						Config.uiColor.getBlue(), max - val);
				Config.saveOptions();
			}
			public boolean mousewheel(Coord c, int amount) {
				val = Utils.clip(val + amount, min, max);
				changed();
				return (true);
			}
		}).changed();

		new Label(new Coord(15, 270), tab, "R");
		new Label(new Coord(40, 270), tab, "G");
		new Label(new Coord(65, 270), tab, "B");
		new Label(new Coord(90, 270), tab, "A");

		new Frame(new Coord(10, 295), new Coord(20, 160), tab);
		new Frame(new Coord(35, 295), new Coord(20, 160), tab);
		new Frame(new Coord(60, 295), new Coord(20, 160), tab);
		new Frame(new Coord(85, 295), new Coord(20, 160), tab);

		final RichTextBox hdinfo = new RichTextBox(new Coord(140, 295), new Coord(200, 160), tab, "Use the sliders on the left to change the color of the hidden object bound boxes. These changes will automatically save. ", foundry12);
		hdinfo.bg = new java.awt.Color(0, 0, 0, 64);

		(new Scrollbar(new Coord(25, 300), 150, tab, 0, 255) {{ val = max - Config.hideColor.getRed(); }
			public void changed() {
				Config.hideColor = new Color(max - val, Config.hideColor.getGreen(),
						Config.hideColor.getBlue(), Config.hideColor.getAlpha());
				Config.saveOptions();
			}
			public boolean mousewheel(Coord c, int amount) {
				val = Utils.clip(val + amount, min, max);
				changed();
				return (true);
			}
		}).changed();
		(new Scrollbar(new Coord(50, 300), 150, tab, 0, 255) {{ val = max - Config.hideColor.getGreen(); }
			public void changed() {
				Config.hideColor = new Color(Config.hideColor.getRed(), max - val,
						Config.hideColor.getBlue(), Config.hideColor.getAlpha());
				Config.saveOptions();
			}
			public boolean mousewheel(Coord c, int amount) {
				val = Utils.clip(val + amount, min, max);
				changed();
				return (true);
			}
		}).changed();
		(new Scrollbar(new Coord(75, 300), 150, tab, 0, 255) {{ val = max - Config.hideColor.getBlue(); }
			public void changed() {
				Config.hideColor = new Color(Config.hideColor.getRed(), Config.hideColor.getGreen(),
						max - val, Config.hideColor.getAlpha());
				Config.saveOptions();
			}
			public boolean mousewheel(Coord c, int amount) {
				val = Utils.clip(val + amount, min, max);
				changed();
				return (true);
			}
		}).changed();
		(new Scrollbar(new Coord(100, 300), 150, tab, 0, 255) {{ val = max - Config.hideColor.getAlpha(); }
			public void changed() {
				Config.hideColor = new Color(Config.hideColor.getRed(), Config.hideColor.getGreen(),
						Config.hideColor.getBlue(), max - val);
				Config.saveOptions();
			}
			public boolean mousewheel(Coord c, int amount) {
				val = Utils.clip(val + amount, min, max);
				changed();
				return (true);
			}
		}).changed();

		(new CheckBox(new Coord(10, 485), tab, "Show Flavor Objects (Restart Required)") {
			public void changed(boolean val) {
				Config.showFlavors = val;
				Config.saveOptions();
			}}).a = Config.showFlavors;

		(new CheckBox(new Coord(250, 485), tab, "Tile Blending") {
			public void changed(boolean val) {
				Config.tileAA = val;
				Config.saveOptions();
			}
		}).a = Config.tileAA;

	}

		{ /* INFO TAB */
			tab = body.new Tab(new Coord(370, 0), 60, "Client Info");

			final RichTextBox helpinfo = new RichTextBox(new Coord(5, 30), new Coord(400, 160), tab, "To report an issue with the client (or provide suggestions) please contact the developer, @trevorhnh, on discord.", foundry14);
			helpinfo.bg = new java.awt.Color(0, 0, 0, 0);
			final RichTextBox helpinfo2 = new RichTextBox(new Coord(5, 90), new Coord(400, 160), tab, "To update the client, simply download the latest files from the Legacy H&H discord and place it into the folder containing the client files. This will overwrite the old  files and update the client.", foundry14);
			helpinfo2.bg = new java.awt.Color(0, 0, 0, 0);
			final RichTextBox helpinfo3 = new RichTextBox(new Coord(5, 210), new Coord(400, 160), tab, "(this client is a fork from the original Union Client source)", foundry12);
			helpinfo3.bg = new java.awt.Color(0, 0, 0, 0);

		}

		{ /* HELP AND ONBOARDING TAB */
			tab = body.new Tab(new Coord(440, 0), 65, "Help");
			new Label(new Coord(10, 30), tab, "Explanation detail:");
			final boolean[] explanationReady = { false };
			final RadioGroup explanation = new RadioGroup(tab) {
				public void changed(int btn, String lbl) {
					if (explanationReady[0]) {
						Config.explanationLevel = Config
								.validateExplanationLevel(btn);
						Config.saveOptions();
					}
				}
			};
			explanation.add("Minimal", new Coord(20, 55));
			explanation.add("Beginner", new Coord(120, 55));
			explanation.add("Detailed", new Coord(230, 55));
			explanation.check(Config.explanationLevel);
			explanationReady[0] = true;

			(new CheckBox(new Coord(10, 95), tab, "Expanded world-object tooltips") {
				public void changed(boolean val) {
					Config.showObjectContext = val;
					Config.saveOptions();
				}
			}).a = Config.showObjectContext;
			(new CheckBox(new Coord(10, 125), tab, "Item uses, sources and requirements") {
				public void changed(boolean val) {
					Config.showItemContext = val;
					Config.saveOptions();
				}
			}).a = Config.showItemContext;
			(new CheckBox(new Coord(10, 155), tab, "Equipment comparison") {
				public void changed(boolean val) {
					Config.showEquipmentComparison = val;
					Config.saveOptions();
				}
			}).a = Config.showEquipmentComparison;
			(new CheckBox(new Coord(10, 185), tab, "Clickable terminology and stat links") {
				public void changed(boolean val) {
					Config.showTerminologyLinks = val;
					Config.saveOptions();
				}
			}).a = Config.showTerminologyLinks;
			(new CheckBox(new Coord(10, 215), tab, "Inventory category borders") {
				public void changed(boolean val) {
					Config.showInventoryCategories = val;
					Config.saveOptions();
				}
			}).a = Config.showInventoryCategories;
			(new CheckBox(new Coord(10, 245), tab, "Map breadcrumb trail") {
				public void changed(boolean val) {
					Config.showBreadcrumbTrail = val;
					Config.saveOptions();
				}
			}).a = Config.showBreadcrumbTrail;
			(new CheckBox(new Coord(10, 275), tab, "Hotkey overlay (Ctrl+Shift+F1)") {
				public void changed(boolean val) {
					Config.showHotkeyOverlay = val;
					if (!val)
						HotkeyOverlay.hide();
					Config.saveOptions();
				}
			}).a = Config.showHotkeyOverlay;

			new RichTextBox(new Coord(10, 315), new Coord(485, 95), tab,
					"Hold $b{Shift} while hovering for the detailed level without changing this setting. Middle-click an inventory item for reverse recipe search. The recipe index stays local and learns exact ingredients when crafting windows are opened.",
					foundry12).bg = new Color(0, 0, 0, 100);
			new Button(new Coord(150, 430), 180, tab, "Open Offline Handbook") {
				public void click() {
					KnowledgeWindow.open(ui, "home");
				}
			};
		}

	new Frame(new Coord(0, 0), new Coord(0, 0), this);
	String last = Utils.getpref("optwndtab", "");
	for (Tabs.Tab t : body.tabs) {
	    if (t.btn.text.text.equals(last))
			body.showtab(t);
	}
    }


    private void setcamera(String camtype) {
	curcam = camtype;
	Utils.setpref("defcam", curcam);
	String[] args = camargs.get(curcam);
	if(args == null) args = new String[0];

	MapView mv = ui.mapview;
	if (mv != null) {
	    if     (curcam.equals("clicktgt"))   mv.cam = new MapView.OrigCam2(args);
	    else if(curcam.equals("fixedcake"))  mv.cam = new MapView.FixedCakeCam(args);
	    else {
		try {
		    mv.cam = MapView.camtypes.get(curcam).newInstance();
		} catch (InstantiationException e) {
		} catch(IllegalAccessException e) {}
	    }
	}
    }

    private void setcamargs(String camtype, String... args) {
	camargs.put(camtype, args);
	if (args.length > 0 && curcam.equals(camtype))
	    Utils.setprefb("camargs", Utils.serialize(args));
    }

    private int getsfxvol() {
	return ((int) (100 - Double.parseDouble(Utils.getpref("sfxvol", "1.0")) * 100));
    }

    private void addinfo(String camtype, String title, String text, Tabs.Tab args) {
	caminfomap.put(camtype, new CamInfo(title, text, args));
	camname2type.put(title, camtype);
    }

    public void wdgmsg(Widget sender, String msg, Object... args) {
	if ((sender == cbtn) || (sender == fbtn))
	    super.wdgmsg(sender, msg, args);
    }

    public class Frame extends Widget {
	private IBox box;

	public Frame(Coord c, Coord sz, Widget parent) {
	    super(c, sz, parent);
	    box = new IBox("gfx/hud", "tl", "tr", "bl", "br", "extvl", "extvr", "extht", "exthb");
	}

	public void draw(GOut og) {
	    super.draw(og);
	    GOut g = og.reclip(Coord.z, sz);
	    g.chcolor(150, 200, 125, 255);
	    box.draw(g, Coord.z, sz);
	}
    }
}
