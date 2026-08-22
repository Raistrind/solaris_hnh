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

import static haven.Utils.getprop;

import haven.INIFile.Pair;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;


import ender.GoogleTranslator;

public class Config {
	public static byte[] authck;
	public static String authuser;
	public static String authserv;
	public static String defserv;
	public static URL resurl, mapurl;
	public static boolean fullscreen;
	public static boolean dbtext;
	public static boolean bounddb;
	public static boolean profile;
	public static boolean nolocalres;
	public static String resdir;
	public static boolean nopreload;
	public static String loadwaited, allused;
	public static boolean xray;
	public static boolean hide;
	public static boolean hide_all;
	public static boolean grid;
	public static boolean timestamp;
	public static boolean new_chat;
	public static boolean showDebug = false;
	public static boolean use_smileys;
	public static boolean zoom;
	public static boolean noborders;
	public static boolean new_minimap;
	public static boolean simple_plants = true;
	public static boolean aimacrazyman = false;
	public static Set<String> hideObjectList;
	public static HashMap<Pattern, String> smileys;
	public static boolean nightvision;
	public static String currentCharName;
	public static Properties options, window_props;
	public static int sfxVol;
	public static int musicVol;
	public static int uiScale = 1;
	private static int activeUIScale = 1;
	public static boolean isMusicOn = false;
	public static boolean isSoundOn = true;
	public static boolean showRadius = true;
	public static boolean showHidden = true;
	public static boolean showBeast = true;
	public static boolean showDirection;
	public static boolean showNames;
	public static boolean showOtherNames;
	public static boolean objectHighlighting;
	public static boolean onlineNotifier;
	public static boolean fastFlowerAnim = true;
	public static boolean mgridDebug = false;
	public static boolean objectBlink;
	public static boolean autoSaveMinimaps;
	public static boolean useSimpleMap;
	public static boolean showMyAvatar = true;
	public static boolean toggleCA;
	public static boolean toggleTR;
	public static boolean toggleCL;
	public static boolean tileAA;
	public static boolean showDayTime;
	public static boolean sshot_compress;
	public static boolean sshot_noui;
	public static boolean sshot_nonames;
	public static boolean newclaim;
	public static boolean showq;
	public static boolean showpath;
	public static boolean showpathAll;
	public static boolean showFlavors = false;
	public static boolean drawIcons;
	public static Color hideColor = new Color(0, 0, 255, 150);
	public static Color uiColor = new Color(0, 0, 0, 200);
	public static int hideAlpha;
	public static boolean hideTressp;
	public static boolean hideTheft;
	public static boolean hideAsslt;
	public static boolean hideBatt;
	public static boolean hideVand;
	public static boolean hideMurd;
	public static boolean flaskMeters = true;

	public static boolean assign_to_tile = false;

	public static boolean show_minimap_profits = true;
	public static boolean show_minimap_players = true;
	public static boolean show_minimap_radius = true;
	public static boolean show_gob_health = true;
	public static boolean no_scent_smoke = true;
	public static boolean render_enable = true;
	public static boolean use_wasd = true;
	public static ArrayList<Pair<String, Color>> minimap_highlights = new ArrayList<Pair<String, Color>>();
	public static HashMap<String, HashMap<String, Float>> FEPMap = new HashMap<String, HashMap<String, Float>>();
	public static HashMap<String, CuriosityStat> CurioMap = new HashMap<String, CuriosityStat>();
	public static HashMap<String, Color> FEPColorMap = new HashMap<String, Color>();

	public static boolean map_show_curiosities = true;

	static {
		FEPColorMap.put("STR", new Color(100, 100, 100));
		FEPColorMap.put("AGI", new Color(100, 100, 100));
		FEPColorMap.put("INT", new Color(100, 100, 100));
		FEPColorMap.put("CON", new Color(100, 100, 100));
		FEPColorMap.put("PER", new Color(100, 100, 100));
		FEPColorMap.put("CHA", new Color(100, 100, 100));
		FEPColorMap.put("DEX", new Color(100, 100, 100));
		FEPColorMap.put("PSY", new Color(100, 100, 100));
	}

	static {
		try {
			String p;
			if ((p = getprop("haven.authck", null)) != null)
				authck = Utils.hex2byte(p);
			authuser = getprop("haven.authuser", null);
			authserv = getprop("haven.authserv", null);
			defserv = getprop("haven.defserv", null);
			if (!(p = getprop("haven.resurl",
					"http://legacy.havenandhearth.com/res/")).equals(""))
				resurl = new URL(p);
			if (!(p = getprop("haven.mapurl",
					"http://legacy.havenandhearth.com/mm/")).equals(""))
				mapurl = new URL(p);
			fullscreen = getprop("haven.fullscreen", "off").equals("on");
			loadwaited = getprop("haven.loadwaited", null);
			allused = getprop("haven.allused", null);
			dbtext = getprop("haven.dbtext", "off").equals("on");
			bounddb = getprop("haven.bounddb", "off").equals("on");
			profile = getprop("haven.profile", "off").equals("on");
			nolocalres = getprop("haven.nolocalres", "").equals("yesimsure");
			resdir = getprop("haven.resdir", null);
			nopreload = getprop("haven.nopreload", "no").equals("yes");
			xray = false;
			hide = true;
			grid = false;
			timestamp = false;
			nightvision = true;
			zoom = false;
			new_minimap = true;
			GoogleTranslator.lang = "en";
			GoogleTranslator.turnedon = false;
			currentCharName = "";
			options = new Properties();
			window_props = new Properties();
			hideObjectList = Collections.synchronizedSet(new HashSet<String>());
			loadOptions();
			activeUIScale = uiScale;
			loadWindowOptions();
			loadSmileys();
			loadFEP();
			loadCurio();
		} catch (java.net.MalformedURLException e) {
			throw (new RuntimeException(e));
		}
	}

	public static class CuriosityStat {
		public double baseLP;
		public int studyTime;
		public int attention;

		public CuriosityStat(double blp, float stime, int att) {
			baseLP = blp;
			studyTime = (int) (stime * 60);
			attention = att;
		}
	}

	private static void loadCurio() {
		try {
			FileInputStream fstream = new FileInputStream("curio.conf");
			InputStreamReader in = new InputStreamReader(fstream, "UTF-8");
			BufferedReader br = new BufferedReader(in);
			String strLine;
			while ((strLine = br.readLine()) != null) {
				String[] info = strLine.split("=");
				double blp = 0;
				int att = 0;
				float stime = 0.0f;

				for (String stat : info[1].split(" ")) {
					String[] tmp = stat.split(":");
					if (tmp[0].equals("LP"))
						blp = (double) Float.valueOf(tmp[1]);
					else if (tmp[0].equals("AT"))
						att = Integer.valueOf(tmp[1]);
					else if (tmp[0].equals("TIME"))
						stime = Float.valueOf(tmp[1]);
				}
				CurioMap.put(info[0], new CuriosityStat(blp, stime, att));
			}
			br.close();
			in.close();
			fstream.close();
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		}

	}

	private static void loadFEP() {
		FEPMap.clear();
		File inputFile = new File("fep.conf");
		if (!inputFile.exists())
			inputFile = new File("etc/needed/fep.conf");
		if (!inputFile.exists())
			return;

		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(
					inputFile), "UTF-8"));
			String strLine;
			while ((strLine = br.readLine()) != null) {
				strLine = strLine.trim();
				if ((strLine.length() == 0) || strLine.startsWith("#"))
					continue;
				String[] fields = strLine.split("=", 2);
				if (fields.length != 2)
					continue;
				HashMap<String, Float> fep = new HashMap<String, Float>();
				String name = fields[0].trim().toLowerCase(Locale.ENGLISH);
				for (String itm : fields[1].trim().split("\\s+")) {
					String values[] = itm.split(":", 2);
					if (values.length != 2)
						continue;
					String key = values[0].trim();
					if (key.equalsIgnoreCase("isItem"))
						key = "isItem";
					else
						key = key.toUpperCase(Locale.ENGLISH);
					try {
						fep.put(key, Float.valueOf(values[1]).floatValue());
					} catch (NumberFormatException e) {
					}
				}
				if ((name.length() > 0) && !fep.isEmpty())
					FEPMap.put(name, fep);
			}
		} catch (IOException e) {
			System.out.println(e);
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public static String mksmiley(String str) {
		synchronized (smileys) {
			for (Pattern p : Config.smileys.keySet()) {
				String res = Config.smileys.get(p);
				str = p.matcher(str).replaceAll(res);
			}
		}
		return str;
	}

	private static void usage(PrintStream out) {
		out.println("usage: haven.jar [-hdPf] [-u USER] [-C HEXCOOKIE] [-r RESDIR] [-U RESURL] [-A AUTHSERV] [SERVER]");
	}

	public static void cmdline(String[] args) {
		PosixArgs opt = PosixArgs.getopt(args, "hdPU:fr:A:u:C:");
		if (opt == null) {
			usage(System.err);
			System.exit(1);
		}
		for (char c : opt.parsed()) {
			switch (c) {
			case 'h':
				usage(System.out);
				System.exit(0);
				break;
			case 'd':
				dbtext = true;
				break;
			case 'P':
				profile = true;
				break;
			case 'f':
				fullscreen = true;
				break;
			case 'r':
				resdir = opt.arg;
				break;
			case 'A':
				authserv = opt.arg;
				break;
			case 'U':
				try {
					resurl = new URL(opt.arg);
				} catch (java.net.MalformedURLException e) {
					System.err.println(e);
					System.exit(1);
				}
				break;
			case 'u':
				authuser = opt.arg;
				break;
			case 'C':
				authck = Utils.hex2byte(opt.arg);
				break;
			}
		}
		if (opt.rest.length > 0)
			defserv = opt.rest[0];
	}

	public static double getSFXVolume() {
		return (double) sfxVol / 100;
	}

	public static int getMusicVolume() {
		return isMusicOn ? musicVol : 0;
	}

	private static void loadSmileys() {
		smileys = new HashMap<Pattern, String>();
		try {
			FileInputStream fstream;
			fstream = new FileInputStream("smileys.conf");
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			while ((strLine = br.readLine()) != null) {
				String[] tmp = strLine.split("\t");
				String smile, res;
				smile = tmp[0];
				res = "\\$img\\[smiley\\/" + tmp[1] + "\\]";
				smileys.put(
						Pattern.compile(smile, Pattern.CASE_INSENSITIVE
								| Pattern.LITERAL), res);
			}
			br.close();
			in.close();
			fstream.close();
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		}

	}

	private static void loadWindowOptions() {
		File inputFile = new File("windows.conf");
		if (!inputFile.exists()) {
			return;
		}
		try {
			window_props.load(new FileInputStream(inputFile));
		} catch (IOException e) {
			System.out.println(e);
		}
	}

	private static void loadOptions() {
		File inputFile = new File("haven.conf");
		if (!inputFile.exists()) {
			return;
		}
		try {
			options.load(new FileInputStream("haven.conf"));
		} catch (IOException e) {
			System.out.println(e);
		}
		uiScale = parseUIScale(options.getProperty("uiScale", "1"));
		String hideObjects = options.getProperty("hideObjects", "");
		String hideHighlight = options.getProperty("hcolor", "255,0,0,128");
		String uiHighlight = options.getProperty("uicolor", "0,0,0,128");

		String clist[] = hideHighlight.split(",");
		if(clist.length != 4) {
			clist[0] = "255"; clist[1] = "0"; clist[2] = "0"; clist[0] = "128";
		}
		String rlist[] = uiHighlight.split(",");
		if(rlist.length != 4) {
			rlist[0] = "255"; rlist[1] = "0"; rlist[2] = "0"; rlist[0] = "128";
		}

		hideColor = new Color(Integer.valueOf(clist[0]), Integer.valueOf(clist[1]), Integer.valueOf(clist[2]), Integer.valueOf(clist[3]));
		uiColor = new Color(Integer.valueOf(rlist[0]), Integer.valueOf(rlist[1]), Integer.valueOf(rlist[2]), Integer.valueOf(rlist[3]));

		GoogleTranslator.apikey = options.getProperty("GoogleAPIKey", "AIzaSyCuo-ukzI_J5n-inniu2U7729ZfadP16_0");

		zoom = options.getProperty("zoom", "false").equals("true");
		noborders = options.getProperty("noborders", "false").equals("true");
		new_minimap = options.getProperty("new_minimap", "true").equals("true");
		new_chat = options.getProperty("new_chat", "true").equals("true");
		use_smileys = options.getProperty("use_smileys", "true").equals("true");
		isMusicOn = options.getProperty("music_on", "true").equals("true");
		isSoundOn = options.getProperty("sound_on", "true").equals("true");
		showDirection = options.getProperty("show_direction", "true").equals("true");
		showNames = options.getProperty("showNames", "true").equals("true");
		showOtherNames = options.getProperty("showOtherNames", "false").equals("true");
		showBeast = options.getProperty("showBeast", "false").equals("true");
		showRadius = options.getProperty("showRadius", "false").equals("true");
		showHidden = options.getProperty("showHidden", "false").equals("true");
		simple_plants = options.getProperty("simple_plants", "false").equals("true");
		objectHighlighting = options.getProperty("objMouseHLight", "false").equals("true");
		onlineNotifier = options.getProperty("onlineNotifier", "false").equals("true");
		showDayTime = options.getProperty("showDayTime", "false").equals("true");
		objectBlink = options.getProperty("objectBlink", "false").equals("true");
		autoSaveMinimaps = options.getProperty("autoSaveMinimaps", "false").equals("true");
		sshot_compress = options.getProperty("sshot_compress", "false").equals("true");
		sshot_noui = options.getProperty("sshot_noui", "false").equals("true");
		sshot_nonames = options.getProperty("sshot_nonames", "false").equals("true");
		newclaim = options.getProperty("newclaim", "true").equals("true");
		showq = options.getProperty("showq", "true").equals("true");
		showpath = options.getProperty("showpath", "false").equals("true");
		showFlavors = options.getProperty("showFlavors", "false").equals("true");
		showpathAll = options.getProperty("showpathAll", "false").equals("true");
		tileAA = options.getProperty("tileAA", "false").equals("true");
		drawIcons = options.getProperty("drawIcons", "false").equals("true");
		flaskMeters = options.getProperty("flaskMeters", "false").equals("true");
		hideTressp = options.getProperty("hideTressp", "false").equals("true");
		hideTheft = options.getProperty("hideTheft", "false").equals("true");
		hideAsslt = options.getProperty("hideAsslt", "false").equals("true");
		hideBatt = options.getProperty("hideBatt", "false").equals("true");
		hideVand = options.getProperty("hideVand", "false").equals("true");
		hideMurd = options.getProperty("hideMurd", "false").equals("true");
		//
		show_minimap_profits = options.getProperty("show_minimap_profits",
				"false").equals("true");
		toggleCA = options.getProperty("toggleCA", "false").equals("true");
		toggleTR = options.getProperty("toggleTR", "false").equals("true");
		toggleCL = options.getProperty("toggleCL", "false").equals("true");
		show_gob_health = options.getProperty("show_gob_health", "false")
				.equals("true");
		show_minimap_players = options.getProperty("show_minimap_players",
				"false").equals("true");
		show_minimap_radius = options.getProperty("show_minimap_radius",
				"false").equals("true");
		sfxVol = Integer.parseInt(options.getProperty("sfx_vol", "100"));
		musicVol = Integer.parseInt(options.getProperty("music_vol", "100"));
		hideObjectList.clear();
		if (!hideObjects.isEmpty()) {
			for (String objectName : hideObjects.split(",")) {
				if (!objectName.isEmpty()) {
					hideObjectList.add(objectName);
				}
			}
		}
		Resource.checkhide();
		timestamp = options.getProperty("timestamp", "false").equals("true");

		try {
			INIFile ifile = new INIFile("haven.ini");
			minimap_highlights = ifile.getSectionColors("HIGHLIGHT", "");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static int parseUIScale(String value) {
		try {
			return validateUIScale(Integer.parseInt(value));
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	public static int validateUIScale(int value) {
		return ((value >= 1) && (value <= 3)) ? value : 1;
	}

	public static int getActiveUIScale() {
		return activeUIScale;
	}

	public static void setUIScale(int value) {
		uiScale = validateUIScale(value);
		saveOptions();
	}

	public static synchronized void setWindowOpt(String key, String value) {
		synchronized (window_props) {
			String prev_val = window_props.getProperty(key);
			if ((prev_val != null) && prev_val.equals(value))
				return;
			window_props.setProperty(key, value);
		}
		saveWindowOpt();
	}

	public static synchronized void setWindowOpt(String key, Boolean value) {
		setWindowOpt(key, value ? "true" : "false");
	}

	public static void saveWindowOpt() {
		synchronized (window_props) {
			try {
				window_props.store(new FileOutputStream("windows.conf"),
						"Window config options");
			} catch (IOException e) {
				System.out.println(e);
			}
		}
	}

	public static void addhide(String str) {
		hideObjectList.add(str);
		Resource.checkhide();
	}

	public static void remhide(String str) {
		hideObjectList.remove(str);
		Resource.checkhide();
	}

	public static void saveOptions() {
		String hideObjects = "";
		for (String objectName : hideObjectList) {
			hideObjects += objectName + ",";
		}

		String scolor = hideColor.getRed()+","+hideColor.getGreen()+","+hideColor.getBlue()+","+hideColor.getAlpha();
		String ucolor = uiColor.getRed()+","+uiColor.getGreen()+","+uiColor.getBlue()+","+uiColor.getAlpha();

		options.setProperty("hcolor", scolor);
		options.setProperty("uicolor", ucolor);
		uiScale = validateUIScale(uiScale);
		options.setProperty("uiScale", String.valueOf(uiScale));
		options.setProperty("hideObjects", hideObjects);
		options.setProperty("GoogleAPIKey", GoogleTranslator.apikey);
		options.setProperty("timestamp", (timestamp) ? "true" : "false");
		options.setProperty("zoom", zoom ? "true" : "false");
		options.setProperty("noborders", noborders ? "true" : "false");
		options.setProperty("new_minimap", new_minimap ? "true" : "false");
		options.setProperty("new_chat", new_chat ? "true" : "false");
		options.setProperty("use_smileys", use_smileys ? "true" : "false");
		options.setProperty("sfx_vol", String.valueOf(sfxVol));
		options.setProperty("music_vol", String.valueOf(musicVol));
		options.setProperty("music_on", isMusicOn ? "true" : "false");
		options.setProperty("sound_on", isSoundOn ? "true" : "false");
		options.setProperty("show_direction", showDirection ? "true" : "false");
		options.setProperty("showNames", showNames ? "true" : "false");
		options.setProperty("showOtherNames", showOtherNames ? "true" : "false");
		options.setProperty("showBeast", showBeast ? "true" : "false");
		options.setProperty("showRadius", showRadius ? "true" : "false");
		options.setProperty("showHidden", showHidden ? "true" : "false");
		options.setProperty("simple_plants", simple_plants ? "true" : "false");
		options.setProperty("objMouseHLight", objectHighlighting ? "true" : "false");
		options.setProperty("onlineNotifier", onlineNotifier ? "true" : "false");
		options.setProperty("autoSaveMinimaps", autoSaveMinimaps ? "true" : "false");
		options.setProperty("showDayTime", showDayTime ? "true" : "false");
		options.setProperty("objectBlink", objectBlink ? "true" : "false");
		options.setProperty("toggleCA", toggleCA ? "true" : "false");
		options.setProperty("toggleTR", toggleTR ? "true" : "false");
		options.setProperty("toggleCL", toggleCL ? "true" : "false");
		options.setProperty("tileAA", tileAA ? "true" : "false");
		options.setProperty("drawIcons", drawIcons ? "true" : "false");
		options.setProperty("sshot_compress", sshot_compress ? "true" : "false");
		options.setProperty("sshot_noui", sshot_noui ? "true" : "false");
		options.setProperty("sshot_nonames", sshot_nonames ? "true" : "false");
		options.setProperty("newclaim", newclaim ? "true" : "false");
		options.setProperty("showq", showq ? "true" : "false");
		options.setProperty("showpath", showpath ? "true" : "false");
		options.setProperty("showFlavors", showFlavors ? "true" : "false");
		options.setProperty("showpathAll", showpathAll ? "true" : "false");
		options.setProperty("flaskMeters", flaskMeters ? "true":"false");
		options.setProperty("hideTressp", hideTressp ? "true" : "false");
		options.setProperty("hideTheft", hideTheft ? "true" : "false");
		options.setProperty("hideAsslt", hideAsslt ? "true" : "false");
		options.setProperty("hideBatt", hideBatt ? "true" : "false");
		options.setProperty("hideVand", hideVand ? "true" : "false");
		options.setProperty("hideMurd", hideMurd ? "true" : "false");
		options.setProperty("show_minimap_profits", show_minimap_profits ? "true" : "false");
		options.setProperty("show_gob_health", show_gob_health ? "true" : "false");
		options.setProperty("show_minimap_players", show_minimap_players ? "true" : "false");
		options.setProperty("show_minimap_radius", show_minimap_radius ? "true" : "false");

		try {
			options.store(new FileOutputStream("haven.conf"),
					"Custom config options");
		} catch (IOException e) {
			System.out.println(e);
		}
	}
}
