package haven;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline, data-driven help used by beginner tooltips and the handbook. */
public final class KnowledgeBase {
	public static final String ALL = "All";
	public static final String GUIDES = "Guides";
	public static final String TERMS = "Terms";
	public static final String STATS = "Stats";
	public static final String RECIPES = "Recipes";
	public static final String PATHS = "Paths";
	private static final File RECIPE_FILE = new File("knowledge-recipes.conf");
	private static final Pattern ARMOR = Pattern.compile(
			"Armor class:\\s*(\\d+)\\s*/\\s*(\\d+)",
			Pattern.CASE_INSENSITIVE);
	private static final Map<String, Document> documents = new LinkedHashMap<String, Document>();
	private static final Map<String, Recipe> recipes = new TreeMap<String, Recipe>();
	private static final List<InfoRule> infoRules = new ArrayList<InfoRule>();
	private static int generation = 0;

	public static class Document {
		public final String id;
		public final String title;
		public final String category;
		public final String body;

		public Document(String id, String title, String category, String body) {
			this.id = id;
			this.title = title;
			this.category = category;
			this.body = body;
		}

		boolean matches(String query) {
			String q = normalize(query);
			return (q.length() == 0) || normalize(title).contains(q)
					|| normalize(category).contains(q) || normalize(body).contains(q);
		}
	}

	public static class Ingredient {
		public final String resource;
		public final String name;
		public final int count;

		public Ingredient(String resource, String name, int count) {
			this.resource = clean(resource);
			this.name = (clean(name).length() == 0) ? humanize(resource) : clean(name);
			this.count = (count > 0) ? count : 1;
		}
	}

	public static class Recipe {
		public String name;
		public String description = "";
		public String requirements = "";
		public List<Ingredient> inputs = new ArrayList<Ingredient>();
		public List<Ingredient> outputs = new ArrayList<Ingredient>();

		Recipe(String name) {
			this.name = clean(name);
		}

		public String id() {
			return "recipe-" + normalize(name).replaceAll("[^a-z0-9]+", "-");
		}
	}

	public static class ItemInfo {
		public String category = "Object";
		public String usedFor = "";
		public String obtainedFrom = "";
		public String requirements = "";
		public String location = "";
		public String quality = "";
	}

	private static class InfoRule {
		final String match;
		final String category;
		final String usedFor;
		final String obtainedFrom;
		final String requirements;
		final String location;
		final String quality;

		InfoRule(String match, String category, String usedFor,
				String obtainedFrom, String requirements, String location,
				String quality) {
			this.match = normalize(match);
			this.category = category;
			this.usedFor = usedFor;
			this.obtainedFrom = obtainedFrom;
			this.requirements = requirements;
			this.location = location;
			this.quality = quality;
		}

		boolean matches(String name, String resource) {
			return normalize(name + " " + resource).contains(match);
		}
	}

	static {
		initDocuments();
		initInfoRules();
		loadRecipes();
	}

	private KnowledgeBase() {
	}

	private static String clean(String value) {
		return (value == null) ? "" : value.trim();
	}

	private static String normalize(String value) {
		return clean(value).toLowerCase(Locale.ENGLISH);
	}

	private static String q(String value) {
		return RichText.Parser.quote(clean(value));
	}

	private static String link(String id, String label) {
		return "$a[doc:" + id + "]{$col[120,190,255]{$u{" + label + "}}}";
	}

	private static void addDoc(String id, String title, String category,
			String body) {
		documents.put(id, new Document(id, title, category, body));
	}

	private static void initDocuments() {
		addDoc("home", "Offline Handbook", GUIDES,
				"$size[16]{$b{Solaris Offline Handbook}}\n\n"
				+ "This guide is stored inside the client and never needs an internet connection. Search on the left, choose a section, or follow a blue link.\n\n"
				+ "$b{Good first pages}\n"
				+ link("getting-started", "Getting started") + "   "
				+ link("inventory-crafting", "Inventory and crafting") + "\n"
				+ link("food-fep", "Food and FEP") + "   "
				+ link("exploration-map", "Exploration and maps") + "\n"
				+ link("specialization-current", "My specialization path") + "\n\n"
				+ "$b{Context controls}\n"
				+ "Hold Shift while hovering for detailed information. Middle-click an inventory item to find recipes that use it. Ctrl+F1 opens this handbook; Ctrl+Shift+F1 toggles the hotkey overlay; Ctrl+Shift+P opens the specialization planner.");
		addDoc("getting-started", "Getting Started", GUIDES,
				"$size[15]{$b{Getting Started}}\n\n"
				+ "Begin with food, water, basic tools, storage and a recognizable route home. Avoid committing every material to one project until you understand how it is obtained.\n\n"
				+ "$b{Useful early habits}\n"
				+ "- Study curiosities whenever Attention is available.\n"
				+ "- Keep food varied so different attributes can grow.\n"
				+ "- Mark home and dangerous locations on the world map.\n"
				+ "- Read an action before using it on valuable objects.\n"
				+ "- Higher quality is useful, but access to the right tools and skills matters first.\n\n"
				+ "Related: " + link("term-lp", "Learning Points") + ", "
				+ link("term-attention", "Attention") + ", "
				+ link("term-quality", "Quality") + ".");
		addDoc("controls", "Controls and Hotkeys", GUIDES,
				"$size[15]{$b{Controls and Hotkeys}}\n\n"
				+ "$b{Help}\nCtrl+F1: handbook or current context\nCtrl+Shift+F1: hotkey overlay\nCtrl+Shift+P: specialization planner\nShift-hover: advanced information\nMiddle-click item: recipes using it\n\n"
				+ "$b{Maps and view}\nCtrl+M: minimap\nCtrl+Shift+M: world map\nCtrl+G: grid\nHome: reset camera\nEnd: screenshot\n\n"
				+ "$b{Movement}\nAlt+Q/W/E/R: crawl, walk, run, sprint\nCtrl+N: night vision\nCtrl+X: x-ray\nCtrl+H: hide configured objects\n\n"
				+ "Toolbar assignments can override some function keys. The on-screen overlay lists the client-level bindings added by Solaris.");
		addDoc("inventory-crafting", "Inventory and Crafting", GUIDES,
				"$size[15]{$b{Inventory and Crafting}}\n\n"
				+ "Colored item borders identify broad categories. Hold Shift over an item for sources, common uses, requirements, location hints and quality context.\n\n"
				+ "$b{Category colors}\nGreen: food   Purple: curiosity   Blue: tool   Red: weapon   Gold: equipment\nBrown: material   Pink: forageable   Orange: crop   Cyan: liquid   Gray: other\n\n"
				+ "$b{Specialization corners}\nA small gold corner marks an item relevant to the primary path; cyan marks one relevant only to the secondary path. These markers can be disabled in Options > Help.\n\n"
				+ "$b{Recipe encyclopedia}\nUnlocked crafting pages are indexed automatically. When a recipe is opened, the server-supplied ingredient and result list is saved locally. This lets the handbook remember exact ingredients and perform reverse searches later.\n\n"
				+ "Middle-click any inventory item to list recipes that use it. A recipe not yet opened can still appear by name, but exact ingredients become available only after the crafting window has supplied them.\n\n"
				+ "Related: " + link("term-quality", "Quality") + " and "
				+ link("term-softcap", "Softcaps") + ".");
		addDoc("food-fep", "Food, Hunger and FEP", GUIDES,
				"$size[15]{$b{Food, Hunger and FEP}}\n\n"
				+ "Food Event Points accumulate toward an attribute increase. Different foods provide different FEP types, so variety helps you control growth. Food quality commonly scales its FEP contribution.\n\n"
				+ "The item tooltip shows calculated FEP values when known. Hunger is the cost of eating; avoid spending it without checking whether the food supports your current goal.\n\n"
				+ "Related: " + link("term-fep", "FEP") + ", "
				+ link("term-hunger", "Hunger") + ", "
				+ link("term-quality", "Quality") + ".");
		addDoc("curiosities-lp", "Curiosities and Learning", GUIDES,
				"$size[15]{$b{Curiosities and Learning}}\n\n"
				+ "Curiosities placed in the Study inventory consume Attention and award Learning Points after their study time completes. Compare LP per hour and LP per Attention, not only the final LP number.\n\n"
				+ "Learning Ability modifies the final LP reward. A strong beginner set usually keeps Attention filled without leaving long empty periods.\n\n"
				+ "Related: " + link("term-lp", "LP") + ", "
				+ link("term-attention", "Attention") + ", "
				+ link("term-learning", "Learning Ability") + ".");
		addDoc("quality-softcaps", "Quality and Softcaps", GUIDES,
				"$size[15]{$b{Quality and Softcaps}}\n\n"
				+ "Quality is a general strength value used by many foods, tools, materials and crafted results. The exact effect depends on the item.\n\n"
				+ "Crafting quality is commonly limited by both ingredient quality and a relevant attribute/skill combination. Improving only one side can stop helping once another input becomes the limiting factor.\n\n"
				+ "Related: " + link("term-quality", "Quality") + " and "
				+ link("term-softcap", "Softcap") + ".");
		addDoc("attributes-skills", "Attributes and Skills", GUIDES,
				"$size[15]{$b{Attributes and Skills}}\n\n"
				+ "Base attributes grow mainly through food. Skill values are bought with Learning Points. Many checks and crafting formulas combine one attribute with one skill.\n\n"
				+ "The Character Sheet labels are clickable: click a blue stat name to open its explanation. Buffed values are shown separately from base values.\n\n"
				+ "Do not raise every value equally at the start. Improve the values that support what you currently gather, craft or fight.");
		addDoc("exploration-map", "Exploration, Maps and Breadcrumbs", GUIDES,
				"$size[15]{$b{Exploration, Maps and Breadcrumbs}}\n\n"
				+ "The world map shows terrain already known by the client. Ctrl+Shift+M opens it; drag to pan, use the wheel to zoom, and right-click to create markers.\n\n"
				+ "The breadcrumb trail records recent movement for the current session and appears on both map views. It is deliberately limited in length. Use Clear Trail in the world map after returning home or beginning a new trip.\n\n"
				+ "Forageable visibility often depends on Perception multiplied by Exploration. A missing object may be undiscovered rather than absent.");
		addDoc("farming-foraging", "Farming and Foraging", GUIDES,
				"$size[15]{$b{Farming and Foraging}}\n\n"
				+ "Crop actions show the plant name and growth stage. Wild Pick actions show the object being collected. Farming affects crop work and quality; Perception and Exploration are central to finding many wild resources.\n\n"
				+ "Keep some seeds before consuming or processing a harvest. Compare seed quality, and avoid replacing all planting stock until the new seeds are confirmed better.");
		addDoc("equipment-combat", "Equipment and Combat", GUIDES,
				"$size[15]{$b{Equipment and Combat}}\n\n"
				+ "Armor tooltips show defensive and absorption values when the server provides them. The comparison shown by Solaris is a reference against currently equipped armor; slot compatibility still has to be checked by the player.\n\n"
				+ "Quality is not enough to compare unrelated equipment. Read armor, bonuses, wear, slot and intended use. Avoid aggressive creatures until you understand movement, defense and escape routes.");
		addDoc("safety-claims", "Safety, Claims and Irreversible Actions", GUIDES,
				"$size[15]{$b{Safety and Claims}}\n\n"
				+ "Actions on claimed property can have consequences. Read unfamiliar flower-menu actions before choosing them, especially theft, vandalism, destruction and aggression.\n\n"
				+ "Swimming and combat can become lethal when stamina or health runs low. Carry water, know the way back, and do not treat shallow-looking water as automatically safe.");

		addDoc("term-quality", "Quality", TERMS,
				"$size[15]{$b{Quality}}\n\nA general value representing how strong or effective an item is. Food FEP, tool performance, equipment and crafted results may use it differently. Crafting is often limited by ingredient quality and a relevant softcap. Higher is usually better when comparing otherwise identical items.");
		addDoc("term-fep", "Food Event Points (FEP)", TERMS,
				"$size[15]{$b{Food Event Points}}\n\nPoints gained by eating food. Filling the FEP meter raises an attribute, with the distribution of eaten foods influencing which attribute is selected. Item tooltips show calculated values when the client has data for that food.");
		addDoc("term-lp", "Learning Points (LP)", TERMS,
				"$size[15]{$b{Learning Points}}\n\nThe currency used to buy skills and skill values. Curiosities are the main continuing source. LP/hour measures speed; LP/Attention measures how efficiently Study capacity is used.");
		addDoc("term-attention", "Attention", TERMS,
				"$size[15]{$b{Attention}}\n\nStudy capacity consumed by active curiosities. Intelligence raises the available limit. A curiosity with excellent LP can still be inconvenient if it occupies too much Attention for too long.");
		addDoc("term-softcap", "Softcap", TERMS,
				"$size[15]{$b{Softcap}}\n\nAn attribute/skill value used as a quality limit in an activity or recipe. When the softcap is lower than the materials, the output can be reduced toward that limit. The Character Sheet shows several common combinations.");
		addDoc("term-learning", "Learning Ability", TERMS,
				"$size[15]{$b{Learning Ability}}\n\nA percentage multiplier applied to Learning Point rewards. The curiosity tooltip uses the current value when estimating effective LP and efficiency.");
		addDoc("term-hunger", "Hunger", TERMS,
				"$size[15]{$b{Hunger}}\n\nThe cost or capacity pressure associated with eating. Efficient food gives useful FEP for the hunger it consumes. Exact behavior can depend on the server era and food data.");
		addDoc("term-stamina", "Stamina", TERMS,
				"$size[15]{$b{Stamina}}\n\nShort-term energy spent by movement and work. Water restores it. Running, heavy work and swimming can drain it quickly; reaching zero in a dangerous situation can prevent escape.");
		addDoc("term-health", "SHP and HHP", TERMS,
				"$size[15]{$b{SHP and HHP}}\n\nSoft Health is the immediately recoverable layer; Hard Health represents more lasting injury. A character can recover from ordinary exertion more easily than from serious damage.");
		addDoc("term-attributes", "Base Attributes", TERMS,
				"$size[15]{$b{Base Attributes}}\n\nStrength, Agility, Intelligence, Constitution, Perception, Charisma, Dexterity and Psyche. Food raises their base values; equipment and effects can modify the current value.");
		addDoc("term-skills", "Skill Values", TERMS,
				"$size[15]{$b{Skill Values}}\n\nTrainable values such as Exploration, Farming and Carpentry. They cost LP and commonly combine with a base attribute for detection, crafting or combat checks.");

		addStatDocs();
	}

	private static void addStatDocs() {
		addStat("str", "Strength", "Physical force. Commonly supports heavy labor, melee damage and Strength-based crafting softcaps.");
		addStat("agil", "Agility", "Speed and responsiveness in combat-related systems. Useful when comparing combat timing and movement-oriented checks.");
		addStat("intel", "Intelligence", "Raises Attention capacity and combines with Stealth for the Evasion softcap shown by this client.");
		addStat("cons", "Constitution", "Physical endurance and resilience. It is associated with surviving damage and demanding activity.");
		addStat("perc", "Perception", "Detection and awareness. Perception multiplied by Exploration controls discovery of many forageables; it also contributes to Baking here.");
		addStat("csm", "Charisma", "Social influence. It supports social and group-oriented mechanics where Charisma is checked.");
		addStat("dxt", "Dexterity", "Fine manipulation. It combines with Sewing for the Weaving softcap and supports precision-oriented crafting.");
		addStat("psy", "Psyche", "Mental and artistic aptitude. It contributes to Goldsmithing and Psycrafting softcaps.");
		addStat("unarmed", "Unarmed Combat", "Skill value for fighting without a conventional melee weapon and for unarmed combat actions.");
		addStat("melee", "Melee Combat", "Skill value for weapon-based close combat. Equipment and combat maneuvers can modify its practical effect.");
		addStat("ranged", "Marksmanship", "Skill value for ranged attacks. Weapon choice, distance and aiming mechanics also matter.");
		addStat("explore", "Exploration", "Combines with Perception to reveal many forageables and other hidden discoveries.");
		addStat("stealth", "Stealth", "Helps conceal criminal traces and combines with Intelligence for the Evasion softcap displayed here.");
		addStat("sewing", "Sewing", "Supports textile and leather crafting. It combines with Dexterity for Weaving and Psyche for Psycrafting.");
		addStat("smithing", "Smithing", "Supports metal crafting. It combines with Strength for Metalworking and Psyche for Goldsmithing.");
		addStat("carpentry", "Carpentry", "Supports woodworking, boards, furniture, buildings and many wooden tools or components.");
		addStat("cooking", "Cooking", "Supports prepared food quality and combines with Perception for the Baking softcap shown here.");
		addStat("farming", "Farming", "Supports crop work, seeds and many agricultural products. It commonly limits farming quality.");
		addStat("survive", "Survival", "Supports wilderness gathering, hunting products and many basic natural-resource quality checks.");
		addStat("sight", "Sight", "Perception multiplied by Exploration. It summarizes the character's ability to discover many hidden forageables.");
		addStat("baking", "Baking", "The geometric mean of Perception and Cooking shown as a common baking softcap.");
		addStat("goldsmithing", "Goldsmithing", "The geometric mean of Psyche and Smithing shown for fine metalwork.");
		addStat("metalworking", "Metalworking", "The geometric mean of Strength and Smithing shown for metal crafting.");
		addStat("evasion", "Evasion", "The geometric mean of Intelligence and Stealth shown by the Character Sheet.");
		addStat("weaving", "Weaving", "The geometric mean of Dexterity and Sewing shown for textile work.");
		addStat("psycrafting", "Psycrafting", "The geometric mean of Psyche and Sewing shown for psyche-sensitive crafting.");
	}

	private static void addStat(String id, String title, String body) {
		addDoc("stat-" + id, title, STATS,
				"$size[15]{$b{" + title + "}}\n\n" + body
				+ "\n\nClick other stat names in the Character Sheet or browse the Stats section for related values.");
	}

	private static void initInfoRules() {
		addRule("stoneaxe", "Tool", "Cutting trees, logs and wooden materials.", "Crafted from basic natural materials.", "An axe must be equipped or available for chopping actions.", "Early tool recipe in the crafting menu.", "Higher quality can improve tool effectiveness.");
		addRule("stone", "Material", "Early tools, paving, construction and stoneworking.", "Pick loose stones or chip suitable boulders.", "Loose stones are often free to pick; chipping a boulder requires a pickaxe.", "Rocky terrain, caves, mountains and suitable boulders.", "Stone quality can limit tools, paving and crafted stone products.");
		addRule("pickaxe", "Tool", "Mining and chipping stone or ore-bearing rock.", "Crafted from a head and handle materials.", "Required by many mining and stone actions.", "Crafting and metalworking progression.", "Higher quality can improve tool performance.");
		addRule("bonesaw", "Tool", "Sawing logs into boards and other fine woodworking.", "Crafted from bone and supporting materials.", "A saw is required for board-making actions.", "Early crafting after obtaining bone.", "Higher quality can improve tool performance and affected outputs.");
		addRule("branch", "Material", "Early tools, fires, handles and construction.", "Use Pick Branch on suitable trees or collect loose branches.", "Usually no tool is required to pick an available branch.", "Trees and wooded areas.", "Its quality can limit crafted items that use it.");
		addRule("taproot", "Forageable", "Early cordage, bindings and recipes needing string-like material.", "Pick the wild forageable.", "Foraging visibility depends on Perception and Exploration.", "Commonly encountered while exploring grassland and woodland.", "Quality can affect crafted results that consume it.");
		addRule("string", "Material", "Bindings, tools, textiles and construction components.", "Made or gathered from fibre-producing resources such as taproots.", "The exact source may require Foraging or a processing recipe.", "Forageables and fibre-processing recipes.", "Material quality can limit crafted results.");
		addRule("bone", "Material", "Tools, curiosities, glue-related work and other crafts.", "Obtained while butchering animals.", "Animal processing and an appropriate cutting tool may be required.", "Hunted or domesticated animals.", "Quality commonly follows the source animal and can limit crafts.");
		addRule("leather", "Material", "Equipment, containers, armor and many durable crafts.", "Produced by tanning prepared hides.", "Requires hide processing, a tanning setup and time.", "Animal processing followed by tanning.", "Leather quality can strongly limit crafted equipment.");
		addRule("hide", "Material", "Processed into leather or used in hide-based crafts.", "Obtained from butchered animals.", "Usually requires animal processing and a cutting tool.", "Animals obtained through hunting or husbandry.", "Source and processing quality affect later leather.");
		addRule("board", "Material", "Buildings, furniture, containers and carpentry recipes.", "Saw a suitable log.", "Requires a saw; Carpentry commonly matters for woodworking.", "Logs from felled trees.", "Board quality can limit wooden constructions and crafts.");
		addRule("block", "Material", "Construction, fires and wooden crafting components.", "Chop blocks from logs or stumps.", "Requires an axe for many block-making actions.", "Felled trees, logs and stumps.", "Block quality can limit crafted results.");
		addRule("clay", "Material", "Pottery, kilns, bricks and construction.", "Gathered from suitable terrain or shallow-water clay locations.", "Some clay gathering requires the correct terrain and digging action.", "Shorelines, mudflats and clay-bearing ground.", "Clay quality limits pottery and brick products.");
		addRule("water", "Liquid", "Drinking, cooking, tanning and many crafting processes.", "Fill a suitable container at a water source.", "Requires a container that can hold water.", "Rivers, lakes, wells and other water sources.", "Water quality can affect recipes that use it.");
		addRule("seed", "Seed", "Planting crops and some food or processing recipes.", "Saved from harvested crops or gathered from suitable plants.", "Farming is important when growing and improving crops.", "Fields and crop harvests.", "Prefer better seeds for future planting when practical.");
		addRule("flour", "Ingredient", "Dough, bread and other cooking recipes.", "Grind suitable grain.", "Requires grain and an appropriate grinding station.", "Farming followed by grain processing.", "Flour quality can limit baked output.");
		addRule("meat", "Food", "Cooking, sausages and other prepared foods.", "Obtained by butchering animals.", "Usually requires animal processing and a cutting tool.", "Hunted or domesticated animals.", "Meat quality affects food and crafted dishes.");
		addRule("fish", "Food", "Cooked food and fish-based recipes.", "Caught through fishing.", "Requires suitable fishing equipment, location and bait or lure where applicable.", "Water with fish populations.", "Fish quality affects prepared food.");
		addRule("egg", "Food", "Cooking and baking recipes.", "Collected from birds or husbandry-related sources.", "Source access depends on the bird or structure.", "Wild nests or domesticated birds.", "Quality affects dishes that use it.");
		addRule("milk", "Food", "Drinking, cheese and cooking.", "Collected from a milk-producing domesticated animal.", "Requires husbandry access and a liquid container.", "Animal husbandry.", "Quality affects dairy products.");
	}

	private static void addRule(String match, String category, String usedFor,
			String obtainedFrom, String requirements, String location,
			String quality) {
		infoRules.add(new InfoRule(match, category, usedFor, obtainedFrom,
				requirements, location, quality));
	}

	public static String humanize(String resourceName) {
		String base = clean(resourceName);
		int slash = base.lastIndexOf('/');
		if (slash >= 0)
			base = base.substring(slash + 1);
		base = base.replace('-', ' ').replace('_', ' ');
		StringBuilder out = new StringBuilder();
		for (String word : base.split("\\s+")) {
			if (word.length() == 0)
				continue;
			if (out.length() > 0)
				out.append(' ');
			out.append(word.substring(0, 1).toUpperCase(Locale.ENGLISH));
			out.append(word.substring(1));
		}
		return (out.length() == 0) ? "Unknown object" : out.toString();
	}

	public static String displayName(Resource resource) {
		if (resource == null)
			return "Unknown object";
		Resource.Tooltip tooltip = resource.layer(Resource.tooltip);
		if ((tooltip != null) && (clean(tooltip.t).length() > 0))
			return tooltip.t.trim();
		return humanize(resource.name);
	}

	private static boolean hasFEP(String name) {
		return Config.FEPMap.containsKey(normalize(name));
	}

	private static boolean isCuriosity(String name) {
		for (String curio : Config.CurioMap.keySet()) {
			if (curio.equalsIgnoreCase(clean(name)))
				return true;
		}
		return false;
	}

	private static String inferCategory(String name, String resource) {
		String n = normalize(name + " " + resource);
		if (hasFEP(name))
			return "Food";
		if (isCuriosity(name))
			return "Curiosity";
		if (n.contains("/herbs/"))
			return "Forageable";
		if (n.contains("/plants/"))
			return "Crop";
		if (n.contains("/trees/"))
			return "Tree";
		if (n.contains("/kritter/") || n.contains("/borka/"))
			return n.contains("/borka/") ? "Hearthling" : "Creature";
		if (n.contains("seed") || n.contains("pip") || n.contains("grain"))
			return "Seed";
		if (n.contains("axe") || n.contains("saw") || n.contains("pickaxe")
				|| n.contains("shovel") || n.contains("hammer")
				|| n.contains("scythe"))
			return "Tool";
		if (n.contains("sword") || n.contains("sling") || n.contains("bow")
				|| n.contains("spear"))
			return "Weapon";
		if (n.contains("armor") || n.contains("helmet") || n.contains("boots")
				|| n.contains("pants") || n.contains("shirt")
				|| n.contains("cloak") || n.contains("cape") || n.contains("ring"))
			return "Equipment";
		if (n.contains("bucket") || n.contains("flask") || n.contains("bottle")
				|| n.contains("waterskin"))
			return "Container";
		if (resource.startsWith("gfx/invobjs/"))
			return "Material";
		return "Object";
	}

	public static ItemInfo infoFor(String name, String resource) {
		ItemInfo info = new ItemInfo();
		info.category = inferCategory(name, clean(resource));
		for (InfoRule rule : infoRules) {
			if (rule.matches(name, resource)) {
				info.category = rule.category;
				info.usedFor = rule.usedFor;
				info.obtainedFrom = rule.obtainedFrom;
				info.requirements = rule.requirements;
				info.location = rule.location;
				info.quality = rule.quality;
				break;
			}
		}
		String n = normalize(resource);
		if (n.contains("/herbs/")) {
			info.category = "Forageable";
			if (info.usedFor.length() == 0)
				info.usedFor = "Food, curiosities or crafting depending on the species.";
			if (info.obtainedFrom.length() == 0)
				info.obtainedFrom = "Use Pick on the wild object.";
			if (info.requirements.length() == 0)
				info.requirements = "Foraging visibility commonly depends on Perception multiplied by Exploration.";
			if (info.location.length() == 0)
				info.location = "Explore suitable terrain; different forageables favor different biomes.";
		}
		if (n.contains("/plants/")) {
			info.category = "Crop";
			if (info.usedFor.length() == 0)
				info.usedFor = "Food, seeds and agricultural processing.";
			if (info.obtainedFrom.length() == 0)
				info.obtainedFrom = "Grown and harvested from a planted field.";
			if (info.requirements.length() == 0)
				info.requirements = "Farming is the main relevant skill; harvest stage affects the result.";
			if (info.quality.length() == 0)
				info.quality = "Crop and seed quality are central to improving later harvests.";
		}
		if (n.contains("/trees/")) {
			info.category = "Tree";
			if (info.usedFor.length() == 0)
				info.usedFor = "Branches, bark, logs, blocks, boards and tree-specific products.";
			if (info.obtainedFrom.length() == 0)
				info.obtainedFrom = "Wild or planted tree.";
			if (info.requirements.length() == 0)
				info.requirements = "Picking may be free; chopping requires an axe and is destructive.";
			if (info.location.length() == 0)
				info.location = "Woodland or planted areas; species vary by terrain.";
		}
		if (n.contains("/kritter/")) {
			info.category = "Creature";
			if (info.usedFor.length() == 0)
				info.usedFor = "Hunting, husbandry or animal products depending on species.";
			if (info.requirements.length() == 0)
				info.requirements = "May be dangerous. Appropriate combat knowledge and an escape route are recommended.";
		}
		if (info.category.equals("Food")) {
			if (info.usedFor.length() == 0)
				info.usedFor = "Eating for Food Event Points, or as an ingredient in prepared food.";
			if (info.obtainedFrom.length() == 0)
				info.obtainedFrom = "Foraging, farming, hunting or cooking depending on the food.";
			if (info.requirements.length() == 0)
				info.requirements = "Check FEP and Hunger before eating; recipes may need a cooking station or tool.";
		}
		if (info.category.equals("Curiosity")) {
			if (info.usedFor.length() == 0)
				info.usedFor = "Studied to earn Learning Points.";
			if (info.obtainedFrom.length() == 0)
				info.obtainedFrom = "Foraged, crafted or obtained from an activity depending on the curiosity.";
			if (info.requirements.length() == 0)
				info.requirements = "Requires free Attention in the Study inventory.";
			if (info.location.length() == 0)
				info.location = "Check whether it is a forageable or a crafted curiosity.";
		}
		if (info.category.equals("Tool") || info.category.equals("Weapon")) {
			if (info.usedFor.length() == 0)
				info.usedFor = info.category.equals("Weapon") ? "Combat or hunting."
						: "Performing resource-gathering or crafting actions.";
			if (info.obtainedFrom.length() == 0)
				info.obtainedFrom = "Usually crafted from materials and intermediate components.";
			if (info.requirements.length() == 0)
				info.requirements = "The recipe may require a learned skill, workstation or another tool.";
		}
		if (info.category.equals("Equipment")) {
			if (info.usedFor.length() == 0)
				info.usedFor = "Worn for armor, bonuses, utility or appearance.";
			if (info.obtainedFrom.length() == 0)
				info.obtainedFrom = "Usually crafted from processed materials.";
			if (info.requirements.length() == 0)
				info.requirements = "Requires a compatible equipment slot; crafting may need specialized skills and tools.";
		}
		if (info.category.equals("Material") || info.category.equals("Ingredient")) {
			if (info.usedFor.length() == 0)
				info.usedFor = "Crafting or construction; indexed recipes appear here after being opened once.";
			if (info.obtainedFrom.length() == 0)
				info.obtainedFrom = "Gathered, processed or crafted depending on the material.";
			if (info.requirements.length() == 0)
				info.requirements = "Source actions may require an appropriate skill, tool or workstation.";
		}
		if (info.quality.length() == 0)
			info.quality = "Quality effects depend on the specific item; compare otherwise identical items.";
		return info;
	}

	public static Color categoryColor(String name, String resource) {
		String category = infoFor(name, resource).category;
		if (category.equals("Food"))
			return new Color(90, 210, 90, 220);
		if (category.equals("Curiosity"))
			return new Color(210, 125, 255, 230);
		if (category.equals("Seed") || category.equals("Crop"))
			return new Color(245, 155, 55, 225);
		if (category.equals("Tool"))
			return new Color(80, 165, 255, 225);
		if (category.equals("Weapon"))
			return new Color(235, 85, 85, 225);
		if (category.equals("Equipment"))
			return new Color(235, 195, 75, 225);
		if (category.equals("Container") || category.equals("Liquid"))
			return new Color(70, 210, 220, 225);
		if (category.equals("Forageable"))
			return new Color(245, 120, 185, 225);
		if (category.equals("Material") || category.equals("Ingredient"))
			return new Color(185, 145, 95, 220);
		return new Color(155, 155, 155, 210);
	}

	public static int detailMode(UI ui) {
		if ((ui != null) && ui.modshift)
			return 2;
		return Math.max(0, Math.min(2, Config.explanationLevel));
	}

	private static void appendInfo(StringBuilder text, String label, String value) {
		if (clean(value).length() == 0)
			return;
		text.append("\n$col[190,210,255]{").append(label).append(":} ");
		text.append(q(value));
	}

	public static String itemTooltip(Item item, int mode) {
		if ((item == null) || (mode <= 0))
			return null;
		Resource resource = item.res.get();
		String resourceName = (resource == null) ? item.GetResName() : resource.name;
		String name = (resource == null) ? clean(item.name()) : displayName(resource);
		if (name.length() == 0)
			name = humanize(resourceName);
		ItemInfo info = infoFor(name, resourceName);
		StringBuilder text = new StringBuilder();
		appendInfo(text, "Category", info.category);
		if (mode == 1) {
			if (info.obtainedFrom.length() > 0)
				appendInfo(text, "Source", info.obtainedFrom);
			text.append(Specialization.itemAdvice(name, resourceName,
					info.category, false));
			text.append("\n$col[170,170,170]{Hold Shift for uses, requirements, location and quality help.}");
		} else {
			List<Recipe> using = recipesUsing(name, resourceName);
			String used = info.usedFor;
			if (!using.isEmpty()) {
				StringBuilder exact = new StringBuilder();
				for (int i = 0; (i < using.size()) && (i < 5); i++) {
					if (i > 0)
						exact.append(", ");
					exact.append(using.get(i).name);
				}
				used = (used.length() == 0) ? "Indexed recipes: " + exact
						: used + " Indexed recipes: " + exact + ".";
			}
			appendInfo(text, "Used for", used);
			appendInfo(text, "Obtained from", info.obtainedFrom);
			appendInfo(text, "Requires", info.requirements);
			appendInfo(text, "Where", info.location);
			String quality = info.quality;
			if (item.get_quality() > 0)
				quality = "This item is quality " + item.get_quality() + ". " + quality;
			appendInfo(text, "Quality", quality);
			if (Config.showEquipmentComparison)
				text.append(equipmentComparison(item));
			text.append(Specialization.itemAdvice(name, resourceName,
					info.category, true));
			text.append("\n$col[170,170,170]{Middle-click: recipes using this item.");
			if (Config.showTerminologyLinks)
				text.append(" Ctrl+F1: handbook context.");
			text.append("}");
		}
		KnowledgeWindow.setItemContext(name, resourceName);
		return text.toString();
	}

	private static int[] armor(Item item) {
		if ((item == null) || (item.tooltip == null))
			return null;
		Matcher matcher = ARMOR.matcher(item.tooltip);
		if (!matcher.find())
			return null;
		return new int[] { Integer.parseInt(matcher.group(1)),
				Integer.parseInt(matcher.group(2)) };
	}

	private static String equipmentComparison(Item item) {
		int[] candidate = armor(item);
		if ((candidate == null) || (item.ui == null) || (item.ui.equip == null))
			return "";
		if (item.hasparent(item.ui.equip))
			return "\n$col[120,220,220]{Equipment:} Currently equipped armor piece.";
		int totalDef = 0, totalAbs = 0, weakest = Integer.MAX_VALUE;
		for (Item equipped : item.ui.equip.equed) {
			int[] values = armor(equipped);
			if (values == null)
				continue;
			totalDef += values[0];
			totalAbs += values[1];
			weakest = Math.min(weakest, values[0] + values[1]);
		}
		StringBuilder text = new StringBuilder();
		text.append("\n$col[120,220,220]{Equipment comparison:} Armor ");
		text.append(candidate[0]).append('/').append(candidate[1]);
		text.append("; equipped total ").append(totalDef).append('/').append(totalAbs);
		if (weakest != Integer.MAX_VALUE) {
			int delta = candidate[0] + candidate[1] - weakest;
			text.append("; ").append((delta >= 0) ? "+" : "").append(delta);
			text.append(" total vs weakest equipped armor");
		}
		text.append(". Slot compatibility is not considered.");
		return text.toString();
	}

	public static String objectTooltip(Gob gob, int mode) {
		if ((gob == null) || (mode <= 0))
			return null;
		Resource resource;
		try {
			resource = gob.getres();
		} catch (RuntimeException e) {
			return null;
		}
		if (resource == null)
			return null;
		String name = displayName(resource);
		ItemInfo info = infoFor(name, resource.name);
		StringBuilder text = new StringBuilder();
		text.append("$size[13]{$b{").append(q(name)).append("}}");
		appendInfo(text, "Category", info.category);
		if (mode == 1) {
			appendInfo(text, "Common use", info.usedFor);
			text.append(Specialization.itemAdvice(name, resource.name,
					info.category, false));
			text.append("\n$col[170,170,170]{Hold Shift for source, requirements and location.}");
		} else {
			appendInfo(text, "Used for", info.usedFor);
			appendInfo(text, "Obtained from", info.obtainedFrom);
			appendInfo(text, "Requires", info.requirements);
			appendInfo(text, "Where", info.location);
			appendInfo(text, "Quality", info.quality);
			text.append(Specialization.itemAdvice(name, resource.name,
					info.category, true));
			text.append("\n$col[130,130,130]{Resource: ").append(q(resource.name));
			text.append(" | Object id: ").append(gob.id).append("}");
			if (Config.showTerminologyLinks)
				text.append("\n$col[170,170,170]{Ctrl+F1: open handbook context.}");
		}
		KnowledgeWindow.setDocumentContext(categoryDocument(info.category));
		return text.toString();
	}

	private static String categoryDocument(String category) {
		if (category.equals("Food"))
			return "food-fep";
		if (category.equals("Curiosity"))
			return "curiosities-lp";
		if (category.equals("Crop") || category.equals("Forageable")
				|| category.equals("Tree"))
			return "farming-foraging";
		if (category.equals("Equipment") || category.equals("Weapon")
				|| category.equals("Creature"))
			return "equipment-combat";
		return "inventory-crafting";
	}

	public static String statDocument(String id) {
		return "stat-" + id;
	}

	public static String statTooltip(String id) {
		Document doc = documents.get(statDocument(id));
		if (doc == null)
			return "Click for an offline explanation.";
		String plain = doc.body.replaceAll("\\$[^\\{]+\\{", "").replace("}", "");
		int split = plain.indexOf("\n\n");
		if (split >= 0)
			plain = plain.substring(split + 2);
		int end = plain.indexOf("\n\n");
		if (end >= 0)
			plain = plain.substring(0, end);
		return plain + Specialization.statAdvice(id)
				+ "\nClick to open the handbook entry.";
	}

	public static String craftingQualityText() {
		return "Crafted quality is commonly limited by ingredient quality and the relevant attribute/skill softcap. Shift-hover ingredients for context.";
	}

	public static Ingredient ingredient(Item item) {
		if (item == null)
			return null;
		Resource resource = item.res.get();
		if (resource == null)
			return null;
		return new Ingredient(resource.name, displayName(resource), item.num);
	}

	public static synchronized int generation() {
		return generation;
	}

	public static synchronized List<Recipe> allRecipes() {
		return new ArrayList<Recipe>(recipes.values());
	}

	public static synchronized void recordRecipe(String name,
			List<Ingredient> inputs, List<Ingredient> outputs) {
		if (clean(name).length() == 0)
			return;
		Recipe recipe = recipeFor(name);
		recipe.inputs = copyIngredients(inputs);
		recipe.outputs = copyIngredients(outputs);
		generation++;
		saveRecipes();
	}

	private static List<Ingredient> copyIngredients(List<Ingredient> values) {
		List<Ingredient> copy = new ArrayList<Ingredient>();
		if (values != null)
			copy.addAll(values);
		return copy;
	}

	private static Recipe recipeFor(String name) {
		String key = normalize(name);
		Recipe recipe = recipes.get(key);
		if (recipe == null) {
			recipe = new Recipe(name);
			recipes.put(key, recipe);
		}
		return recipe;
	}

	public static synchronized void syncAvailableRecipes(UI ui) {
		if ((ui == null) || (ui.sess == null) || (ui.sess.glob == null))
			return;
		Collection<Resource> available;
		synchronized (ui.sess.glob.paginae) {
			available = new ArrayList<Resource>(ui.sess.glob.paginae);
		}
		boolean changed = false;
		for (Resource resource : available) {
			if ((resource == null) || resource.loading)
				continue;
			Resource.AButton action = resource.layer(Resource.action);
			if ((action == null) || (action.ad == null) || (action.ad.length == 0)
					|| !action.ad[0].equals("craft"))
				continue;
			boolean newRecipe = !recipes.containsKey(normalize(action.name));
			Recipe recipe = recipeFor(action.name);
			if (newRecipe)
				changed = true;
			String description = "";
			Resource.Pagina page = resource.layer(Resource.pagina);
			if ((page != null) && (page.text != null))
				description = page.text;
			String requirement = "";
			if ((action.parent != null) && !action.parent.loading) {
				Resource.AButton parent = action.parent.layer(Resource.action);
				if (parent != null)
					requirement = "Crafting menu: " + parent.name;
			}
			if ((recipe.description.length() == 0) && (description.length() > 0)) {
				recipe.description = description;
				changed = true;
			}
			if ((recipe.requirements.length() == 0) && (requirement.length() > 0)) {
				recipe.requirements = requirement;
				changed = true;
			}
		}
		if (changed) {
			generation++;
			saveRecipes();
		}
	}

	public static synchronized List<Recipe> recipesUsing(String name,
			String resource) {
		String targetName = normalize(name);
		String targetResource = normalize(resource);
		List<Recipe> found = new ArrayList<Recipe>();
		for (Recipe recipe : recipes.values()) {
			for (Ingredient ingredient : recipe.inputs) {
				boolean resourceMatch = (targetResource.length() > 0)
						&& normalize(ingredient.resource).equals(targetResource);
				boolean nameMatch = (targetName.length() > 0)
						&& normalize(ingredient.name).equals(targetName);
				if (resourceMatch || nameMatch) {
					found.add(recipe);
					break;
				}
			}
		}
		return found;
	}

	public static synchronized Document reverseDocument(String name,
			String resource) {
		List<Recipe> found = recipesUsing(name, resource);
		StringBuilder body = new StringBuilder();
		body.append("$size[15]{$b{Recipes using ").append(q(name)).append("}}\n\n");
		if (found.isEmpty()) {
			body.append("No indexed recipe currently uses this item. Open recipes in the crafting menu once so their exact server-supplied ingredients can be saved locally.");
		} else {
			body.append("The following locally indexed recipes use this item:\n\n");
			for (Recipe recipe : found)
				body.append("- ").append(link(recipe.id(), q(recipe.name))).append("\n");
		}
		body.append("\n$col[170,170,170]{This index is offline and contains recipes discovered by this client installation.}");
		return new Document("reverse", "Used for: " + name, RECIPES,
				body.toString());
	}

	public static synchronized List<Document> documents(String category,
			String query) {
		List<Document> found = new ArrayList<Document>();
		for (Document document : documents.values()) {
			if ((category.equals(ALL) || document.category.equals(category))
					&& document.matches(query))
				found.add(document);
		}
		for (Recipe recipe : recipes.values()) {
			Document document = recipeDocument(recipe);
			if ((category.equals(ALL) || category.equals(RECIPES))
					&& (!category.equals(RECIPES)
							|| !Config.filterSpecializationRecipes
							|| !Specialization.active()
							|| Specialization.recipeRelevant(recipe))
					&& document.matches(query))
				found.add(document);
		}
		found.addAll(Specialization.documents(category, query));
		Collections.sort(found, new Comparator<Document>() {
			public int compare(Document a, Document b) {
				if (a.category.equals(RECIPES) && b.category.equals(RECIPES)) {
					Recipe ar = recipeById(a.id);
					Recipe br = recipeById(b.id);
					boolean arelevant = Specialization.recipeRelevant(ar);
					boolean brelevant = Specialization.recipeRelevant(br);
					if (arelevant != brelevant)
						return arelevant ? -1 : 1;
				}
				int categoryOrder = a.category.compareToIgnoreCase(b.category);
				return (categoryOrder != 0) ? categoryOrder : a.title
						.compareToIgnoreCase(b.title);
			}
		});
		return found;
	}

	public static synchronized Document document(String id) {
		if (id == null)
			return null;
		Document document = documents.get(id);
		if (document != null)
			return document;
		document = Specialization.document(id);
		if (document != null)
			return document;
		for (Recipe recipe : recipes.values()) {
			if (recipe.id().equals(id))
				return recipeDocument(recipe);
		}
		return null;
	}

	private static Recipe recipeById(String id) {
		for (Recipe recipe : recipes.values())
			if (recipe.id().equals(id))
				return recipe;
		return null;
	}

	private static Document recipeDocument(Recipe recipe) {
		StringBuilder body = new StringBuilder();
		body.append("$size[15]{$b{").append(q(recipe.name)).append("}}\n\n");
		if (recipe.description.length() > 0)
			body.append(recipe.description).append("\n\n");
		if (recipe.requirements.length() > 0)
			body.append("$b{Requirements}\n").append(q(recipe.requirements)).append("\n\n");
		body.append("$b{Ingredients}\n");
		if (recipe.inputs.isEmpty()) {
			body.append("Exact ingredients have not been indexed yet. Open this recipe once in the crafting window.\n");
		} else {
			for (Ingredient ingredient : recipe.inputs)
				body.append("- ").append(ingredient.count).append(" x ")
						.append(q(ingredient.name)).append("\n");
		}
		body.append("\n$b{Result}\n");
		if (recipe.outputs.isEmpty()) {
			body.append("Open the recipe once to index its result.\n");
		} else {
			for (Ingredient ingredient : recipe.outputs)
				body.append("- ").append(ingredient.count).append(" x ")
						.append(q(ingredient.name)).append("\n");
		}
		if (!recipe.inputs.isEmpty()) {
			body.append("\n$b{Ingredient hints}\n");
			for (Ingredient ingredient : recipe.inputs) {
				ItemInfo info = infoFor(ingredient.name, ingredient.resource);
				String hint = (info.location.length() > 0) ? info.location
						: info.obtainedFrom;
				if (hint.length() > 0)
					body.append("- ").append(q(ingredient.name)).append(": ")
							.append(q(hint)).append("\n");
			}
		}
		body.append("\n$b{Quality}\n").append(q(craftingQualityText()));
		body.append(Specialization.recipeDetails(recipe));
		return new Document(recipe.id(), recipe.name, RECIPES, body.toString());
	}

	private static String encode(String value) throws IOException {
		return URLEncoder.encode(clean(value), "UTF-8");
	}

	private static String decode(String value) throws IOException {
		return URLDecoder.decode(value, "UTF-8");
	}

	private static String packIngredients(List<Ingredient> ingredients)
			throws IOException {
		StringBuilder packed = new StringBuilder();
		for (Ingredient ingredient : ingredients) {
			if (packed.length() > 0)
				packed.append('\n');
			packed.append(ingredient.count).append('\t');
			packed.append(ingredient.resource).append('\t');
			packed.append(ingredient.name);
		}
		return encode(packed.toString());
	}

	private static List<Ingredient> unpackIngredients(String packed)
			throws IOException {
		List<Ingredient> ingredients = new ArrayList<Ingredient>();
		String decoded = decode(packed);
		if (decoded.length() == 0)
			return ingredients;
		for (String line : decoded.split("\\n")) {
			String[] values = line.split("\\t", 3);
			if (values.length != 3)
				continue;
			try {
				ingredients.add(new Ingredient(values[1], values[2], Integer
						.parseInt(values[0])));
			} catch (NumberFormatException e) {
			}
		}
		return ingredients;
	}

	private static synchronized void loadRecipes() {
		if (!RECIPE_FILE.exists())
			return;
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(
					RECIPE_FILE), "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				if ((line.length() == 0) || line.startsWith("#"))
					continue;
				String[] values = line.split("\\|", 5);
				if (values.length != 5)
					continue;
				try {
					Recipe recipe = recipeFor(decode(values[0]));
					recipe.description = decode(values[1]);
					recipe.requirements = decode(values[2]);
					recipe.inputs = unpackIngredients(values[3]);
					recipe.outputs = unpackIngredients(values[4]);
				} catch (Exception e) {
				}
			}
		} catch (IOException e) {
			System.out.println("Could not load offline recipe index: " + e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private static synchronized void saveRecipes() {
		File temporary = new File(RECIPE_FILE.getPath() + ".new");
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(temporary), "UTF-8"));
			writer.write("# Solaris offline recipe index v1");
			writer.newLine();
			for (Recipe recipe : recipes.values()) {
				writer.write(encode(recipe.name));
				writer.write('|');
				writer.write(encode(recipe.description));
				writer.write('|');
				writer.write(encode(recipe.requirements));
				writer.write('|');
				writer.write(packIngredients(recipe.inputs));
				writer.write('|');
				writer.write(packIngredients(recipe.outputs));
				writer.newLine();
			}
		} catch (IOException e) {
			System.out.println("Could not save offline recipe index: " + e);
			return;
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
				}
			}
		}
		File backup = new File(RECIPE_FILE.getPath() + ".bak");
		if (backup.exists())
			backup.delete();
		boolean hadOriginal = RECIPE_FILE.exists();
		if (hadOriginal && !RECIPE_FILE.renameTo(backup)) {
			temporary.delete();
			return;
		}
		if (temporary.renameTo(RECIPE_FILE)) {
			if (backup.exists())
				backup.delete();
		} else if (hadOriginal) {
			backup.renameTo(RECIPE_FILE);
		}
	}
}
