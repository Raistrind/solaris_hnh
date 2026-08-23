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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Optional, local-only specialization guidance. It never restricts gameplay. */
public final class Specialization {
	public static final String NONE = "none";
	public static final String CUSTOM = "custom";
	private static final File STATE_FILE = new File("specialization-plan.conf");
	private static final Map<String, Path> paths = new LinkedHashMap<String, Path>();
	private static final Map<String, Integer> goalStates = new HashMap<String, Integer>();
	private static final Set<String> pinnedGoals = new HashSet<String>();
	private static final List<String> orderedGoals = new ArrayList<String>();
	private static final List<CustomGoal> customGoals = new ArrayList<CustomGoal>();
	private static final Map<String, Reminder> reminders = new LinkedHashMap<String, Reminder>();
	private static final Map<String, Discovered> discovered = new LinkedHashMap<String, Discovered>();
	private static String customKeywords = "";
	private static int generation = 0;
	private static long lastReminderCheck = 0;

	public static class Focus {
		public final String id;
		public final String name;
		public final String reason;

		Focus(String id, String name, String reason) {
			this.id = id;
			this.name = name;
			this.reason = reason;
		}
	}

	public static class Goal {
		public final String id;
		public final String stage;
		public final String title;
		public final String detail;

		Goal(String id, String stage, String title, String detail) {
			this.id = id;
			this.stage = stage;
			this.title = title;
			this.detail = detail;
		}
	}

	public static class Path {
		public final String id;
		public final String name;
		public final String summary;
		public final List<Focus> stats;
		public final List<Focus> skills;
		public final String[] keywords;
		public final String[] categories;
		public final String tools;
		public final String food;
		public final String curiosities;
		public final String locations;
		public final List<Goal> goals;

		Path(String id, String name, String summary, List<Focus> stats,
				List<Focus> skills, String keywords, String categories,
				String tools, String food, String curiosities, String locations,
				List<Goal> goals) {
			this.id = id;
			this.name = name;
			this.summary = summary;
			this.stats = stats;
			this.skills = skills;
			this.keywords = words(keywords);
			this.categories = words(categories);
			this.tools = tools;
			this.food = food;
			this.curiosities = curiosities;
			this.locations = locations;
			this.goals = goals;
		}

		public String documentId() {
			return "path-" + id;
		}
	}

	public static class GoalView {
		public final String ref;
		public final String pathId;
		public final String stage;
		public final String title;
		public final String detail;
		public final boolean custom;

		GoalView(String ref, String pathId, String stage, String title,
				String detail, boolean custom) {
			this.ref = ref;
			this.pathId = pathId;
			this.stage = stage;
			this.title = title;
			this.detail = detail;
			this.custom = custom;
		}
	}

	private static class CustomGoal {
		final String id;
		String title;

		CustomGoal(String id, String title) {
			this.id = id;
			this.title = clean(title);
		}
	}

	private static class Reminder {
		final String ref;
		final String title;
		final long due;

		Reminder(String ref, String title, long due) {
			this.ref = ref;
			this.title = title;
			this.due = due;
		}
	}

	private static class Discovered {
		final String resource;
		final String name;
		final String category;

		Discovered(String resource, String name, String category) {
			this.resource = clean(resource);
			this.name = clean(name);
			this.category = clean(category);
		}
	}

	static {
		registerBuiltIns();
		loadState();
	}

	private Specialization() {
	}

	private static List<Focus> focus(String[][] values) {
		List<Focus> result = new ArrayList<Focus>();
		for (String[] value : values)
			result.add(new Focus(value[0], value[1], value[2]));
		return result;
	}

	private static List<Goal> goals(String[][] values) {
		List<Goal> result = new ArrayList<Goal>();
		for (String[] value : values)
			result.add(new Goal(value[0], value[1], value[2], value[3]));
		return result;
	}

	private static void add(Path path) {
		paths.put(path.id, path);
	}

	private static void registerBuiltIns() {
		add(new Path(NONE, "None", "No specialization guidance is active.",
				focus(new String[][] {}), focus(new String[][] {}), "", "", "",
				"", "", "", goals(new String[][] {})));
		add(new Path("generalist", "Beginner / Generalist",
				"Build a safe foundation and sample every major activity before committing to a narrower role.",
				focus(new String[][] {
						{ "perc", "Perception", "Helps find useful resources while exploring." },
						{ "intel", "Intelligence", "Supports study capacity and flexible learning." },
						{ "cons", "Constitution", "Makes travel and accidents more forgiving." } }),
				focus(new String[][] {
						{ "explore", "Exploration", "Makes early foraging more reliable." },
						{ "survive", "Survival", "Supports gathering, hunting and basic tools." },
						{ "carpentry", "Carpentry", "Unlocks broadly useful wooden infrastructure." } }),
				"axe,branch,stone,bone,rope,board,block,food,curiosity,container,seed",
				"tool,food,curiosity,material,container,forageable",
				"Axe, saw, digging tool, water container and a dependable light source.",
				"Favor variety and avoid spending Hunger without checking the attributes offered.",
				"Keep Attention filled with curiosities whose completion times fit your sessions.",
				"Explore nearby terrain first and mark water, clay, forageables and dangerous animals.",
				goals(new String[][] {
						{ "basic-tools", "Early", "Assemble basic tools", "Secure an axe, digging tool, water container and material for emergency fires." },
						{ "safe-home", "Early", "Establish a safe home area", "Choose storage, water access and an escape route before accumulating valuable materials." },
						{ "study-cycle", "Early", "Start a reliable study cycle", "Fill Attention with curiosities that will not leave the Study inventory empty for long." },
						{ "sample-crafts", "Intermediate", "Try each major craft family", "Open farming, cooking, carpentry, sewing and metalworking recipes to learn what feels useful." },
						{ "choose-focus", "Advanced", "Choose a primary focus", "Select a narrower primary or secondary path after learning which activities you enjoy." } })));
		add(new Path("explorer", "Explorer and Forager",
				"Find terrain, wild resources and safe routes while building a useful local map.",
				focus(new String[][] {
						{ "perc", "Perception", "Combines with Exploration for forageable visibility." },
						{ "agil", "Agility", "Supports mobility and escape planning." },
						{ "cons", "Constitution", "Improves travel endurance and safety." } }),
				focus(new String[][] {
						{ "explore", "Exploration", "The core value for seeing wild resources." },
						{ "survive", "Survival", "Improves many gathered and wilderness products." },
						{ "stealth", "Stealth", "Useful when route planning involves risk or traces." } }),
				"herb,forage,berry,mushroom,water,clay,cave,boat,rope,torch,map",
				"forageable,container,tool,curiosity",
				"Water container, light source, boat when appropriate, spare food and route markers.",
				"Carry efficient travel food and reserve Hunger for Perception, Agility and Constitution gains.",
				"Prioritize forageable curiosities that can be replaced during normal routes.",
				"Survey biome boundaries, shorelines, caves and safe return routes; mark rare finds immediately.",
				goals(new String[][] {
						{ "travel-kit", "Early", "Prepare a travel kit", "Carry water, food, light and materials that prevent a small problem from ending the trip." },
						{ "first-route", "Early", "Map a safe local circuit", "Create a repeatable route through several terrain types and mark hazards." },
						{ "raise-sight", "Intermediate", "Improve forageable sight", "Develop Perception and Exploration together instead of raising only one side." },
						{ "resource-atlas", "Intermediate", "Build a resource atlas", "Mark water, clay, caves, useful trees and recurring forageable areas." },
						{ "expedition", "Advanced", "Plan a long expedition", "Prepare supplies, fallback waypoints and enough carrying capacity before leaving familiar terrain." } })));
		add(new Path("farmer", "Farmer",
				"Develop reliable crop cycles, improve seed quality and turn fields into dependable supplies.",
				focus(new String[][] {
						{ "perc", "Perception", "Participates in the Baking softcap and supports field scouting." },
						{ "cons", "Constitution", "Useful for sustained field work and carrying." },
						{ "dxt", "Dexterity", "Supports processing paths that often accompany farming." } }),
				focus(new String[][] {
						{ "farming", "Farming", "The main crop and seed progression value." },
						{ "cooking", "Cooking", "Turns harvests into useful food." },
						{ "sewing", "Sewing", "Uses fiber crops and supports farm-related processing." } }),
				"seed,crop,carrot,wheat,flax,hemp,pumpkin,beet,grape,field,soil,flour",
				"seed,crop,food,ingredient",
				"Seed containers, digging tool, storage organized by crop and processing stations.",
				"Use surplus harvests to support the attributes needed by your primary and secondary paths.",
				"Prefer curiosities whose schedules fit planting and harvest checks.",
				"Use accessible flat land near storage and water; keep seed-quality groups separate.",
				goals(new String[][] {
						{ "starter-seeds", "Early", "Collect starter seeds", "Acquire several useful crop types without mixing visibly different quality groups." },
						{ "first-field", "Early", "Plant an organized field", "Leave room for movement and keep each crop or quality group clearly separated." },
						{ "harvest-cycle", "Intermediate", "Complete a full crop cycle", "Observe stages, harvest timing and the relationship between seeds and later crops." },
						{ "processing", "Intermediate", "Build crop processing", "Add storage and the stations needed for flour, fiber, oil or other chosen products." },
						{ "quality-line", "Advanced", "Maintain a quality seed line", "Reserve the best seeds for replanting and keep lower-quality produce for consumption or processing." } })));
		add(new Path("cook", "Cook",
				"Turn gathered ingredients into efficient attribute growth and dependable settlement food.",
				focus(new String[][] {
						{ "perc", "Perception", "Combines with Cooking for the Baking softcap." },
						{ "dxt", "Dexterity", "Supports several ingredient-processing crafts." },
						{ "psy", "Psyche", "Relevant to advanced crafting branches and a balanced food plan." } }),
				focus(new String[][] {
						{ "cooking", "Cooking", "The central value for many prepared foods." },
						{ "farming", "Farming", "Provides consistent ingredients." },
						{ "survive", "Survival", "Supports wild, fish and animal ingredient quality." } }),
				"food,meat,fish,egg,milk,flour,bread,pie,cheese,oven,fire,water,spice",
				"food,ingredient,liquid,container",
				"Water containers, fire and baking stations, ingredient storage and quality-separated cupboards.",
				"Plan menus by FEP type, Hunger cost, available quality and the attributes your group wants next.",
				"Use food production downtime for steady, low-maintenance curiosities.",
				"Keep ingredient sources, water, fuel and ovens close enough to reduce hauling.",
				goals(new String[][] {
						{ "kitchen", "Early", "Build a basic kitchen", "Organize water, fuel, fire and ingredient storage around the first reliable recipes." },
						{ "fep-menu", "Early", "Create a varied FEP menu", "Identify foods for several attributes instead of repeatedly eating one convenient recipe." },
						{ "ingredient-map", "Intermediate", "Secure ingredient sources", "Coordinate farms, animal products, fishing and forageables for repeatable meals." },
						{ "quality-control", "Intermediate", "Separate ingredient quality", "Avoid lowering an expensive batch by mixing it with an unnoticed low-quality ingredient." },
						{ "settlement-menu", "Advanced", "Maintain a settlement menu", "Balance production time, Hunger efficiency and the attribute needs of several roles." } })));
		add(new Path("hunter", "Hunter",
				"Track animals, manage risk and turn successful hunts into food and materials.",
				focus(new String[][] {
						{ "perc", "Perception", "Supports spotting, ranged work and wilderness awareness." },
						{ "agil", "Agility", "Helps positioning and escape." },
						{ "str", "Strength", "Supports combat and carrying a harvest home." } }),
				focus(new String[][] {
						{ "ranged", "Marksmanship", "Supports ranged hunting approaches." },
						{ "survive", "Survival", "Important for wilderness products and butchering outcomes." },
						{ "melee", "Melee Combat", "Provides a close-range option when plans fail." } }),
				"bow,arrow,sling,weapon,armor,animal,meat,hide,bone,leather,rope,trap",
				"weapon,equipment,creature,food,material",
				"Appropriate weapon, spare ammunition, armor, water, first-aid supplies and an escape route.",
				"Favor Strength, Agility, Perception and Constitution foods according to hunting method.",
				"Use animal and wilderness curiosities when their Attention efficiency is competitive.",
				"Scout open approaches and obstacles before engaging; mark dangerous populations and safe fallback points.",
				goals(new String[][] {
						{ "safe-prey", "Early", "Learn safe prey and hazards", "Distinguish low-risk opportunities from animals that can endanger a beginner." },
						{ "hunting-kit", "Early", "Prepare a hunting kit", "Carry the weapon, ammunition, water and processing tools needed for the chosen method." },
						{ "first-harvest", "Intermediate", "Complete a full animal harvest", "Bring home meat, hide and bones without losing materials to missing tools or inventory space." },
						{ "combat-margin", "Intermediate", "Build a safety margin", "Improve relevant combat values and equipment before moving to more dangerous targets." },
						{ "supply-chain", "Advanced", "Maintain a hunting supply chain", "Keep ammunition, repairs, storage and processing ready before each expedition." } })));
		add(new Path("miner", "Miner",
				"Explore underground safely and establish a dependable stone, ore and fuel supply.",
				focus(new String[][] {
						{ "str", "Strength", "Central to heavy labor and many metalworking checks." },
						{ "cons", "Constitution", "Supports endurance and safety underground." },
						{ "perc", "Perception", "Useful for scouting and complementary exploration." } }),
				focus(new String[][] {
						{ "smithing", "Smithing", "Connects ore extraction to metal production." },
						{ "survive", "Survival", "Supports wilderness preparation and early material quality." },
						{ "carpentry", "Carpentry", "Provides structural and storage support." } }),
				"pickaxe,stone,ore,metal,coal,charcoal,cave,mine,torch,support,ladder",
				"tool,material,equipment",
				"Mining tool, dependable light, food, water, storage and any required structural support.",
				"Strength and Constitution foods are common priorities; preserve enough variety for other needs.",
				"Choose compact curiosities that do not compete with essential underground supplies.",
				"Mark entrances, branches, hazards, ore veins and the route back to the surface.",
				goals(new String[][] {
						{ "mine-kit", "Early", "Prepare an underground kit", "Bring light, water, food, tools and enough inventory space before entering." },
						{ "safe-route", "Early", "Mark a safe cave route", "Record the entrance and important branches so retreat does not depend on memory." },
						{ "ore-source", "Intermediate", "Locate useful stone or ore", "Record the material and route before committing to long extraction sessions." },
						{ "processing", "Intermediate", "Connect mining to processing", "Prepare fuel, storage and the stations needed to turn extraction into usable material." },
						{ "network", "Advanced", "Develop a maintained mine network", "Keep routes legible, supplied and clearly marked for repeated group use." } })));
		add(new Path("blacksmith", "Blacksmith",
				"Convert ore and fuel into tools, weapons, armor and advanced metal components.",
				focus(new String[][] {
						{ "str", "Strength", "Combines with Smithing for Metalworking." },
						{ "psy", "Psyche", "Combines with Smithing for Goldsmithing." },
						{ "cons", "Constitution", "Supports heavy production and hauling." } }),
				focus(new String[][] {
						{ "smithing", "Smithing", "The central value for metal crafts." },
						{ "carpentry", "Carpentry", "Supports handles, structures and workshop organization." },
						{ "melee", "Melee Combat", "Helps evaluate weapon and armor production needs." } }),
				"ore,metal,bar,anvil,hammer,forge,smelter,coal,charcoal,weapon,armor,tool",
				"material,tool,weapon,equipment",
				"Smelter or forge chain, fuel, hammering tools, anvils and quality-separated storage.",
				"Strength and Psyche support different smithing softcaps; select food for the crafts you actually perform.",
				"Study efficient curiosities while long fuel and production cycles run.",
				"Place fuel, ore, bars and workstations to minimize repeated heavy hauling.",
				goals(new String[][] {
						{ "fuel-chain", "Early", "Secure a fuel chain", "Estimate fuel before starting batches and store enough for complete processing cycles." },
						{ "first-metal", "Early", "Produce the first usable metal", "Track ore, fuel and output quality through the entire process." },
						{ "workshop", "Intermediate", "Organize a smithing workshop", "Separate ore, bars, fuel, tools and finished goods by purpose and quality." },
						{ "softcaps", "Intermediate", "Develop smithing softcaps", "Raise Smithing with Strength or Psyche according to the recipes being produced." },
						{ "production-line", "Advanced", "Maintain a production line", "Keep replacement tools, fuel and common metal components available for the settlement." } })));
		add(new Path("carpenter", "Carpenter",
				"Supply wooden tools, containers, furniture and structures efficiently.",
				focus(new String[][] {
						{ "dxt", "Dexterity", "Supports careful production and related crafts." },
						{ "str", "Strength", "Helps logging, construction and hauling." },
						{ "perc", "Perception", "Useful when sourcing and selecting materials." } }),
				focus(new String[][] {
						{ "carpentry", "Carpentry", "The central wooden crafting value." },
						{ "survive", "Survival", "Supports sourcing natural materials." },
						{ "farming", "Farming", "Complements tree planting and renewable supply." } }),
				"wood,tree,log,branch,block,board,saw,axe,carpentry,container,furniture",
				"tree,tool,material,container",
				"Axe, saw, hauling capacity, organized lumber storage and a safe work area.",
				"Dexterity and Strength foods support the production and harvesting sides of the role.",
				"Use wood- or exploration-related curiosities that fit workshop downtime.",
				"Map useful tree species and keep logs, blocks and boards near their consuming stations.",
				goals(new String[][] {
						{ "tool-pair", "Early", "Secure an axe and saw", "Keep replacement materials available so a broken tool does not stop all production." },
						{ "lumber-store", "Early", "Organize lumber storage", "Separate logs, blocks, boards and high-quality material by intended use." },
						{ "workshop", "Intermediate", "Build a carpentry workshop", "Place tools, stock and common component storage around the main work area." },
						{ "tree-supply", "Intermediate", "Develop renewable wood supply", "Track useful species and coordinate planting or sustainable harvesting." },
						{ "standard-stock", "Advanced", "Maintain standard components", "Keep commonly requested boards, blocks, containers and replacement tools ready." } })));
		add(new Path("tailor", "Tailor and Leatherworker",
				"Turn fibers, hides and leather into clothing, containers and specialized equipment.",
				focus(new String[][] {
						{ "dxt", "Dexterity", "Combines with Sewing for Weaving." },
						{ "psy", "Psyche", "Combines with Sewing for Psycrafting." },
						{ "perc", "Perception", "Supports gathering and ingredient selection." } }),
				focus(new String[][] {
						{ "sewing", "Sewing", "The central textile and leather crafting value." },
						{ "farming", "Farming", "Provides repeatable fiber crops." },
						{ "survive", "Survival", "Supports hides and wild material sourcing." } }),
				"fiber,string,yarn,cloth,flax,hemp,hide,leather,fleece,wool,sewing,needle,clothes,bag",
				"material,equipment,container,crop",
				"Needle or sewing tool, fiber processing, tanning chain and material separated by quality.",
				"Dexterity supports Weaving; Psyche supports Psycrafting. Choose according to intended products.",
				"Balance workshop curiosities against the Attention needed for your next LP purchases.",
				"Coordinate farms, hunters and animal keepers; place processing beside material storage.",
				goals(new String[][] {
						{ "first-fiber", "Early", "Secure fiber and string", "Establish at least one renewable source instead of relying only on chance finds." },
						{ "hide-chain", "Early", "Prepare hide processing", "Have the tools and storage ready before valuable hides arrive." },
						{ "workshop", "Intermediate", "Organize a sewing workshop", "Separate raw fiber, cloth, hides, leather and finished equipment by quality." },
						{ "softcap-choice", "Intermediate", "Choose a sewing softcap", "Develop Dexterity or Psyche alongside Sewing based on the products you prioritize." },
						{ "equipment-stock", "Advanced", "Maintain essential equipment stock", "Keep bags, clothing and replacement utility items available for other roles." } })));
		add(new Path("animal", "Animal Keeper",
				"Develop safe husbandry routines and dependable milk, wool, meat and breeding stock.",
				focus(new String[][] {
						{ "csm", "Charisma", "Relevant to social and animal-related interactions." },
						{ "cons", "Constitution", "Supports sustained farm work." },
						{ "perc", "Perception", "Helps evaluation and complementary food production." } }),
				focus(new String[][] {
						{ "farming", "Farming", "Supports feed production and agricultural integration." },
						{ "survive", "Survival", "Supports animal products and wilderness sourcing." },
						{ "cooking", "Cooking", "Turns animal products into settlement value." } }),
				"animal,cattle,cow,sheep,pig,hen,chicken,milk,wool,fleece,egg,meat,feed,trough,pen",
				"creature,food,material,container",
				"Secure pens, gates, feed storage, product containers and safe separation for dangerous animals.",
				"Support Constitution, Charisma and the attributes needed by the processing role paired with husbandry.",
				"Use predictable curiosities that fit regular feeding, breeding and collection checks.",
				"Keep pens near feed and processing but far enough from traffic to prevent accidental escapes.",
				goals(new String[][] {
						{ "safe-pen", "Early", "Prepare a secure pen", "Check gates and movement space before introducing valuable animals." },
						{ "feed-plan", "Early", "Establish a feed plan", "Store enough feed and understand who will replenish it during absences." },
						{ "first-products", "Intermediate", "Collect and process animal products", "Prepare containers and stations before milk, eggs, wool or hides accumulate." },
						{ "records", "Intermediate", "Track breeding stock", "Use names or notes to avoid losing the qualities you intend to improve." },
						{ "stable-herd", "Advanced", "Maintain a stable herd", "Balance feed cost, breeding goals, product demand and available pen space." } })));
		add(new Path("builder", "Builder",
				"Plan settlement growth, stage materials and complete construction without blocking daily life.",
				focus(new String[][] {
						{ "str", "Strength", "Supports hauling and heavy construction work." },
						{ "cons", "Constitution", "Improves endurance during long projects." },
						{ "dxt", "Dexterity", "Complements precise component production." } }),
				focus(new String[][] {
						{ "carpentry", "Carpentry", "Supplies many structures and components." },
						{ "smithing", "Smithing", "Provides metal fittings and advanced tools." },
						{ "farming", "Farming", "Supports land organization and renewable materials." } }),
				"build,construction,wall,house,road,palisade,brick,clay,stone,board,block,rope,metal",
				"material,tool,container",
				"Construction tools, hauling capacity, staging containers and clearly separated project stock.",
				"Strength and Constitution help long projects; coordinate food with the settlement cook.",
				"Use long-duration curiosities during material collection and construction sessions.",
				"Plan traffic, expansion, claims, water access and escape routes before placing permanent structures.",
				goals(new String[][] {
						{ "site-plan", "Early", "Sketch the site plan", "Consider movement, storage, water, future expansion and defensive consequences before building." },
						{ "material-list", "Early", "Prepare a material list", "Break the project into components and avoid scattering partial stock across unrelated containers." },
						{ "staging", "Intermediate", "Create a staging area", "Put materials and replacement tools near the project without blocking normal traffic." },
						{ "finish-zone", "Intermediate", "Complete one usable zone", "Prefer finished, functional areas over many simultaneous incomplete foundations." },
						{ "maintenance", "Advanced", "Plan maintenance and expansion", "Keep spare materials and reserve space for repairs, roads and later buildings." } })));
		add(new Path("fighter", "Fighter",
				"Develop combat values, equipment, supplies and decision-making with safety margins.",
				focus(new String[][] {
						{ "str", "Strength", "Supports damage and heavy equipment paths." },
						{ "agil", "Agility", "Supports positioning and combat tempo." },
						{ "cons", "Constitution", "Improves the margin for surviving mistakes." } }),
				focus(new String[][] {
						{ "unarmed", "Unarmed Combat", "Core value for unarmed combat approaches." },
						{ "melee", "Melee Combat", "Core value for weapon-based close combat." },
						{ "ranged", "Marksmanship", "Supports ranged options and hunting." } }),
				"weapon,armor,shield,sword,axe,bow,arrow,helmet,boots,combat,bandage,medicine",
				"weapon,equipment,food,tool",
				"Appropriate armor and weapon, healing supplies, water, food and a planned escape route.",
				"Prioritize Strength, Agility and Constitution while retaining enough Intelligence for continued learning.",
				"Select high-value curiosities that fit the Attention and downtime available between training sessions.",
				"Practice in controlled terrain; mark safe regroup points and avoid unknown fights near obstacles.",
				goals(new String[][] {
						{ "combat-basics", "Early", "Learn the combat controls", "Practice movement, targeting and disengagement where failure is recoverable." },
						{ "starter-kit", "Early", "Assemble a complete combat kit", "Include healing, water and an exit plan rather than evaluating only weapon damage." },
						{ "balanced-values", "Intermediate", "Develop balanced combat values", "Raise the value used by your chosen style without neglecting survival attributes." },
						{ "gear-check", "Intermediate", "Establish a gear check", "Compare armor, repair state, supplies and replacement cost before each risky action." },
						{ "group-role", "Advanced", "Define a group combat role", "Coordinate equipment, targets, retreat calls and recovery supplies with allies." } })));
		add(new Path("scholar", "Curiosity and Learning Specialist",
				"Maximize reliable Learning Point progress by managing Attention, timing and replacement curiosities.",
				focus(new String[][] {
						{ "intel", "Intelligence", "Provides Attention capacity for studying curiosities." },
						{ "perc", "Perception", "Supports finding many wild curiosities." },
						{ "psy", "Psyche", "Complements advanced crafting and a broad learning plan." } }),
				focus(new String[][] {
						{ "explore", "Exploration", "Improves access to forageable curiosities." },
						{ "stealth", "Stealth", "Combines with Intelligence for Evasion and supports trace awareness." },
						{ "survive", "Survival", "Supports wilderness curiosity quality." } }),
				"curiosity,study,attention,learning,lp,herb,forage,craft",
				"curiosity,forageable,material",
				"Organized curiosity storage, replacement stock and enough carrying space for foraging routes.",
				"Intelligence food expands Attention; balance it with the attributes required by curiosity sources.",
				"Compare LP per hour and LP per Attention, then combine durations that match real login times.",
				"Build repeatable foraging and crafting routes rather than depending on a single rare curiosity.",
				goals(new String[][] {
						{ "fill-study", "Early", "Keep Study filled", "Use accessible curiosities first and avoid empty Attention while waiting for a perfect set." },
						{ "compare-efficiency", "Early", "Compare curiosity efficiency", "Evaluate LP per hour, LP per Attention and whether replacement stock is realistic." },
						{ "schedule", "Intermediate", "Match study times to sessions", "Combine short and long curiosities so completions happen when someone can replace them." },
						{ "replacement-stock", "Intermediate", "Maintain replacement stock", "Store the next set before the current Study inventory finishes." },
						{ "learning-plan", "Advanced", "Maintain an LP purchase plan", "Pin the next skills and avoid spending LP without considering the path they unlock." } })));
		add(new Path(CUSTOM, "Custom",
				"Use your own focus keywords and goals. Keywords match items, recipes, categories and stat identifiers.",
				focus(new String[][] {}), focus(new String[][] {}), "", "", 
				"Choose tools that directly support your custom goals.",
				"Choose foods for the attributes named in your focus keywords.",
				"Choose curiosities according to your available Attention and LP plan.",
				"Add map markers from selected goals whenever a location matters.",
				goals(new String[][] {})));
	}

	private static String[] words(String value) {
		value = clean(value).toLowerCase(Locale.ENGLISH);
		if (value.length() == 0)
			return new String[0];
		String[] raw = value.split(",");
		List<String> result = new ArrayList<String>();
		for (String word : raw) {
			word = word.trim();
			if (word.length() > 0)
				result.add(word);
		}
		return result.toArray(new String[result.size()]);
	}

	private static String clean(String value) {
		return (value == null) ? "" : value.trim();
	}

	private static String normalize(String value) {
		return clean(value).toLowerCase(Locale.ENGLISH);
	}

	private static String quote(String value) {
		return RichText.Parser.quote(clean(value));
	}

	private static String shorten(String value, int limit) {
		value = clean(value);
		return (value.length() <= limit) ? value : value.substring(0,
				Math.max(0, limit - 3)) + "...";
	}

	public static synchronized int generation() {
		return generation;
	}

	public static synchronized List<Path> paths() {
		return new ArrayList<Path>(paths.values());
	}

	public static synchronized Path path(String id) {
		Path found = paths.get(normalize(id));
		return (found == null) ? paths.get(NONE) : found;
	}

	public static synchronized String pathName(String id) {
		return path(id).name;
	}

	public static synchronized void setPath(boolean primary, String id) {
		id = path(id).id;
		if (primary) {
			Config.specializationPrimary = id;
			if (!id.equals(NONE) && id.equals(Config.specializationSecondary))
				Config.specializationSecondary = NONE;
		} else {
			Config.specializationSecondary = (!id.equals(NONE)
					&& id.equals(Config.specializationPrimary)) ? NONE : id;
		}
		generation++;
		Config.saveOptions();
	}

	public static synchronized List<Path> activePaths() {
		List<Path> result = new ArrayList<Path>();
		Path primary = path(Config.specializationPrimary);
		Path secondary = path(Config.specializationSecondary);
		if (!primary.id.equals(NONE))
			result.add(primary);
		if (!secondary.id.equals(NONE) && !secondary.id.equals(primary.id))
			result.add(secondary);
		return result;
	}

	public static synchronized boolean active() {
		return Config.showSpecializationAdvice && !activePaths().isEmpty();
	}

	private static boolean contains(String[] values, String target) {
		target = normalize(target);
		for (String value : values) {
			if (target.contains(value) || value.contains(target))
				return true;
		}
		return false;
	}

	private static boolean focusMatches(Path path, String id) {
		id = normalize(id);
		for (Focus focus : path.stats)
			if (focus.id.equals(id))
				return true;
		for (Focus focus : path.skills)
			if (focus.id.equals(id))
				return true;
		if (path.id.equals(CUSTOM))
			return contains(words(customKeywords), id);
		return false;
	}

	public static synchronized boolean isRecommendedStat(String id) {
		if (!active())
			return false;
		for (Path path : activePaths())
			if (focusMatches(path, id))
				return true;
		return false;
	}

	public static synchronized String statAdvice(String id) {
		if (!active())
			return "";
		StringBuilder result = new StringBuilder();
		for (Path path : activePaths()) {
			for (Focus focus : path.stats) {
				if (focus.id.equals(id)) {
					if (result.length() == 0)
						result.append("\nSpecialization: ");
					else
						result.append(" ");
					result.append(path.name).append(" recommends this: ")
							.append(focus.reason);
				}
			}
			for (Focus focus : path.skills) {
				if (focus.id.equals(id)) {
					if (result.length() == 0)
						result.append("\nSpecialization: ");
					else
						result.append(" ");
					result.append(path.name).append(" recommends this: ")
							.append(focus.reason);
				}
			}
			if (path.id.equals(CUSTOM) && focusMatches(path, id)) {
				if (result.length() == 0)
					result.append("\nSpecialization: ");
				result.append("This matches a Custom focus keyword.");
			}
		}
		return result.toString();
	}

	private static int matchScore(Path path, String name, String resource,
			String category) {
		String text = normalize(name + " " + resource + " " + category);
		int score = 0;
		for (String keyword : path.keywords)
			if (text.contains(keyword))
				score++;
		String cat = normalize(category);
		for (String value : path.categories)
			if (cat.equals(value))
				score += 2;
		if (path.id.equals(CUSTOM)) {
			for (String keyword : words(customKeywords))
				if (text.contains(keyword))
					score += 2;
		}
		return score;
	}

	public static synchronized boolean itemRelevant(String name, String resource,
			String category) {
		if (!active())
			return false;
		for (Path path : activePaths())
			if (matchScore(path, name, resource, category) > 0)
				return true;
		return false;
	}

	public static synchronized Color itemColor(String name, String resource,
			String category) {
		List<Path> active = activePaths();
		if (!active.isEmpty()
				&& (matchScore(active.get(0), name, resource, category) > 0))
			return new Color(255, 225, 80, 245);
		if ((active.size() > 1)
				&& (matchScore(active.get(1), name, resource, category) > 0))
			return new Color(80, 220, 255, 245);
		return null;
	}

	public static synchronized String itemAdvice(String name, String resource,
			String category, boolean detailed) {
		if (!active())
			return "";
		StringBuilder result = new StringBuilder();
		Path firstMatched = null;
		for (Path path : activePaths()) {
			if (matchScore(path, name, resource, category) <= 0)
				continue;
			if (firstMatched == null)
				firstMatched = path;
			if (result.length() == 0)
				result.append("\n$col[255,220,100]{Specialization:} ");
			else
				result.append("; ");
			result.append(quote(path.name));
			if (detailed) {
				GoalView next = nextGoalForPath(path.id);
				if (next != null)
					result.append(" - next: ").append(quote(next.title));
			}
		}
		if (detailed && (firstMatched != null)) {
			if (category.equals("Food"))
				result.append("\n$col[255,220,100]{Path food guidance:} ")
						.append(quote(firstMatched.food));
			else if (category.equals("Curiosity"))
				result.append("\n$col[255,220,100]{Path study guidance:} ")
						.append(quote(firstMatched.curiosities));
			else if (category.equals("Tool") || category.equals("Weapon")
					|| category.equals("Equipment"))
				result.append("\n$col[255,220,100]{Path equipment checklist:} ")
						.append(quote(firstMatched.tools));
		}
		if (result.length() > 0)
			discover(name, resource, category);
		return result.toString();
	}

	public static synchronized void discover(String name, String resource,
			String category) {
		if (!itemRelevant(name, resource, category))
			return;
		String key = normalize(resource);
		if (key.length() == 0)
			key = normalize(name);
		if ((key.length() == 0) || discovered.containsKey(key))
			return;
		if (discovered.size() >= 250) {
			String first = discovered.keySet().iterator().next();
			discovered.remove(first);
		}
		discovered.put(key, new Discovered(resource, name, category));
		generation++;
		saveState();
	}

	private static boolean recipeMatches(Path path, KnowledgeBase.Recipe recipe) {
		StringBuilder text = new StringBuilder(recipe.name);
		for (KnowledgeBase.Ingredient ingredient : recipe.inputs)
			text.append(' ').append(ingredient.name).append(' ')
					.append(ingredient.resource);
		for (KnowledgeBase.Ingredient ingredient : recipe.outputs)
			text.append(' ').append(ingredient.name).append(' ')
					.append(ingredient.resource);
		return matchScore(path, text.toString(), "", "") > 0;
	}

	public static synchronized boolean recipeRelevant(KnowledgeBase.Recipe recipe) {
		if (!active() || (recipe == null))
			return false;
		for (Path path : activePaths())
			if (recipeMatches(path, recipe))
				return true;
		return false;
	}

	public static synchronized boolean recipeRelevant(String name) {
		if (!active())
			return false;
		for (Path path : activePaths())
			if (matchScore(path, name, "", "") > 0)
				return true;
		return false;
	}

	public static synchronized String recipeAdvice(String name) {
		if (!recipeRelevant(name))
			return "";
		StringBuilder names = new StringBuilder();
		for (Path path : activePaths()) {
			if (matchScore(path, name, "", "") > 0) {
				if (names.length() > 0)
					names.append(" and ");
				names.append(path.name);
			}
		}
		return "Relevant to " + names + ". Check the planner for its current milestones and material checklist.";
	}

	public static synchronized String recipeDetails(KnowledgeBase.Recipe recipe) {
		if (!recipeRelevant(recipe))
			return "";
		StringBuilder result = new StringBuilder();
		result.append("\n$b{Specialization relevance}\n");
		for (Path path : activePaths())
			if (recipeMatches(path, recipe))
				result.append("- ").append(quote(path.name)).append("\n");
		return result.toString();
	}

	private static String goalRef(String pathId, Goal goal) {
		return pathId + "." + goal.id;
	}

	public static synchronized List<GoalView> goals() {
		List<GoalView> result = new ArrayList<GoalView>();
		for (Path path : activePaths()) {
			for (Goal goal : path.goals)
				result.add(new GoalView(goalRef(path.id, goal), path.id,
						goal.stage, goal.title, goal.detail, false));
		}
		for (CustomGoal goal : customGoals)
			result.add(new GoalView("custom." + goal.id, CUSTOM, "Custom",
					goal.title, "A locally defined goal. It remains available when paths change.",
					true));
		Collections.sort(result, new Comparator<GoalView>() {
			public int compare(GoalView a, GoalView b) {
				int ai = orderedGoals.indexOf(a.ref);
				int bi = orderedGoals.indexOf(b.ref);
				if ((ai < 0) && (bi < 0))
					return 0;
				if (ai < 0)
					return 1;
				if (bi < 0)
					return -1;
				return ai - bi;
			}
		});
		return result;
	}

	public static synchronized int state(String ref) {
		Integer state = goalStates.get(ref);
		return (state == null) ? 0 : state;
	}

	public static synchronized boolean pinned(String ref) {
		return pinnedGoals.contains(ref);
	}

	public static synchronized void toggleDone(String ref) {
		if (state(ref) == 1)
			goalStates.remove(ref);
		else
			goalStates.put(ref, 1);
		generation++;
		saveState();
	}

	public static synchronized void toggleSkipped(String ref) {
		if (state(ref) == 2)
			goalStates.remove(ref);
		else
			goalStates.put(ref, 2);
		generation++;
		saveState();
	}

	public static synchronized void togglePinned(String ref) {
		if (!pinnedGoals.remove(ref))
			pinnedGoals.add(ref);
		generation++;
		saveState();
	}

	public static synchronized GoalView goal(String ref) {
		for (GoalView goal : goals())
			if (goal.ref.equals(ref))
				return goal;
		return null;
	}

	private static GoalView nextGoalForPath(String pathId) {
		GoalView first = null;
		for (GoalView goal : goals()) {
			if (!goal.pathId.equals(pathId) || (state(goal.ref) != 0))
				continue;
			if (pinned(goal.ref))
				return goal;
			if (first == null)
				first = goal;
		}
		return first;
	}

	public static synchronized List<GoalView> nextGoals(int count) {
		List<GoalView> open = new ArrayList<GoalView>();
		for (GoalView goal : goals())
			if (state(goal.ref) == 0)
				open.add(goal);
		Collections.sort(open, new Comparator<GoalView>() {
			public int compare(GoalView a, GoalView b) {
				if (pinned(a.ref) != pinned(b.ref))
					return pinned(a.ref) ? -1 : 1;
				return 0;
			}
		});
		if (open.size() > count)
			return new ArrayList<GoalView>(open.subList(0, count));
		return open;
	}

	public static synchronized int[] progress() {
		int done = 0, total = 0;
		for (GoalView goal : goals()) {
			if (state(goal.ref) == 2)
				continue;
			total++;
			if (state(goal.ref) == 1)
				done++;
		}
		return new int[] { done, total };
	}

	public static synchronized void addCustomGoal(String title) {
		title = clean(title);
		if (title.length() == 0)
			return;
		customGoals.add(new CustomGoal(Long.toString(System.currentTimeMillis())
				+ "-" + customGoals.size(), title));
		generation++;
		saveState();
	}

	public static synchronized void removeCustomGoal(String ref) {
		String id = ref.startsWith("custom.") ? ref.substring(7) : ref;
		for (int i = 0; i < customGoals.size(); i++) {
			if (customGoals.get(i).id.equals(id)) {
				customGoals.remove(i);
				goalStates.remove("custom." + id);
				pinnedGoals.remove("custom." + id);
				orderedGoals.remove("custom." + id);
				reminders.remove("custom." + id);
				generation++;
				saveState();
				return;
			}
		}
	}

	public static synchronized void moveCustomGoal(String ref, int direction) {
		moveGoal(ref, direction);
	}

	public static synchronized void moveGoal(String ref, int direction) {
		List<GoalView> current = goals();
		int index = -1;
		for (int i = 0; i < current.size(); i++)
			if (current.get(i).ref.equals(ref))
				index = i;
		int target = index + direction;
		if ((index < 0) || (target < 0) || (target >= current.size()))
			return;
		GoalView moved = current.remove(index);
		current.add(target, moved);
		for (GoalView goal : current)
			orderedGoals.remove(goal.ref);
		for (GoalView goal : current)
			orderedGoals.add(goal.ref);
		generation++;
		saveState();
	}

	public static synchronized String customKeywords() {
		return customKeywords;
	}

	public static synchronized void setCustomKeywords(String value) {
		customKeywords = clean(value);
		generation++;
		saveState();
	}

	public static synchronized void setReminder(String ref, String title,
			int minutes) {
		minutes = Math.max(1, Math.min(10080, minutes));
		reminders.put(ref, new Reminder(ref, clean(title), System
				.currentTimeMillis() + (minutes * 60000L)));
		generation++;
		saveState();
	}

	public static synchronized void clearReminder(String ref) {
		if (reminders.remove(ref) != null) {
			generation++;
			saveState();
		}
	}

	public static synchronized String reminderText(String ref) {
		Reminder reminder = reminders.get(ref);
		if (reminder == null)
			return "";
		long minutes = Math.max(0, (reminder.due - System.currentTimeMillis()
				+ 59999L) / 60000L);
		return "Reminder in " + minutes + " minute" + ((minutes == 1) ? "" : "s");
	}

	public static synchronized void tick(UI ui) {
		long now = System.currentTimeMillis();
		if ((now - lastReminderCheck) < 1000)
			return;
		lastReminderCheck = now;
		List<Reminder> due = new ArrayList<Reminder>();
		for (Reminder reminder : reminders.values())
			if (reminder.due <= now)
				due.add(reminder);
		if (due.isEmpty())
			return;
		for (Reminder reminder : due) {
			if ((ui != null) && (ui.slenhud != null))
				ui.slenhud.error("Specialization reminder: " + reminder.title);
			reminders.remove(reminder.ref);
		}
		generation++;
		saveState();
	}

	public static synchronized boolean markCurrentLocation(UI ui, String title) {
		if ((ui == null) || (ui.mapview == null) || (ui.sess == null))
			return false;
		Gob player = ui.sess.glob.oc.getgob(ui.mapview.playergob);
		if (player == null)
			return false;
		Coord tile = player.position().div(MCache.tileSize);
		WorldMapMarkerStore.getShared().add(tile, "Goal: " + clean(title),
				new Color(255, 205, 70));
		return true;
	}

	public static synchronized void drawOverlay(GOut g, Coord screenSize) {
		if (!Config.showSpecializationOverlay || !active()
				|| HotkeyOverlay.visible())
			return;
		List<GoalView> next = nextGoals(1);
		int[] progress = progress();
		String pathText = pathName(Config.specializationPrimary);
		if (!path(Config.specializationSecondary).id.equals(NONE))
			pathText += " + " + pathName(Config.specializationSecondary);
		String goalText = next.isEmpty() ? "No open goals" : next.get(0).title;
		pathText = shorten(pathText, 46);
		goalText = shorten(goalText, 48);
		Coord size = new Coord(330, 58);
		double desiredScale = Config.elementScale(Config.helperScale);
		double fitScale = Math.min((screenSize.x - 10) / (double) size.x,
				(screenSize.y - 10) / (double) size.y);
		double scale = Math.max(0.1, Math.min(desiredScale, fitScale));
		Coord displaySize = Widget.scaleSize(size, scale);
		Coord origin = new Coord(Math.max(5, screenSize.x - displaySize.x - 8), 8);
		GOut overlayGraphics = g.reclip(origin, size).scaled(scale, origin);
		overlayGraphics.chcolor(new Color(0, 0, 0, 190));
		overlayGraphics.frect(Coord.z, size);
		overlayGraphics.chcolor(new Color(235, 195, 75, 230));
		overlayGraphics.rect(Coord.z, size);
		overlayGraphics.atext("Path: " + pathText, new Coord(8, 14), 0, 0.5);
		overlayGraphics.chcolor(Color.WHITE);
		overlayGraphics.atext("Next: " + goalText, new Coord(8, 32), 0, 0.5);
		overlayGraphics.chcolor(new Color(170, 205, 240));
		overlayGraphics.atext("Progress: " + progress[0] + "/" + progress[1]
				+ "   Ctrl+Shift+P: planner", new Coord(8, 49), 0, 0.5);
		overlayGraphics.chcolor();
	}

	public static synchronized List<KnowledgeBase.Document> documents(
			String category, String query) {
		List<KnowledgeBase.Document> result = new ArrayList<KnowledgeBase.Document>();
		if (!category.equals(KnowledgeBase.ALL)
				&& !category.equals(KnowledgeBase.PATHS))
			return result;
		KnowledgeBase.Document current = currentDocument();
		if (current.matches(query))
			result.add(current);
		for (Path path : paths.values()) {
			if (path.id.equals(NONE))
				continue;
			KnowledgeBase.Document document = pathDocument(path);
			if (document.matches(query))
				result.add(document);
		}
		return result;
	}

	public static synchronized KnowledgeBase.Document document(String id) {
		if (id == null)
			return null;
		if (id.equals("specialization-current"))
			return currentDocument();
		if (id.startsWith("path-")) {
			Path path = paths.get(id.substring(5));
			if ((path != null) && !path.id.equals(NONE))
				return pathDocument(path);
		}
		return null;
	}

	private static String link(String documentId, String label) {
		return "$a[doc:" + documentId + "]{" + quote(label) + "}";
	}

	private static KnowledgeBase.Document currentDocument() {
		StringBuilder body = new StringBuilder();
		body.append("$size[15]{$b{My Specialization}}\n\n");
		List<Path> active = activePaths();
		if (active.isEmpty()) {
			body.append("No path is selected. This is a valid choice and disables specialization recommendations. Open the planner with Ctrl+Shift+P or choose a path in Options > Help.");
		} else {
			body.append("$b{Active paths}\n");
			for (Path path : active)
				body.append("- ").append(link(path.documentId(), path.name))
						.append(": ").append(quote(path.summary)).append("\n");
			body.append("\n$b{Next objectives}\n");
			List<GoalView> next = nextGoals(3);
			if (next.isEmpty())
				body.append("All non-skipped goals are complete. Add a custom goal in the planner.\n");
			for (GoalView goal : next)
				body.append("- ").append(quote(goal.title)).append(" (")
						.append(quote(goal.stage)).append(")\n");
			int[] progress = progress();
			body.append("\nProgress: ").append(progress[0]).append(" / ")
					.append(progress[1]).append(" goals.\n");
			body.append("\nOpen the planner with $b{Ctrl+Shift+P} to complete, skip, pin or reorder goals, create reminders, add custom goals or mark the current map position.");
		}
		return new KnowledgeBase.Document("specialization-current",
				"My Specialization", KnowledgeBase.PATHS, body.toString());
	}

	private static KnowledgeBase.Document pathDocument(Path path) {
		StringBuilder body = new StringBuilder();
		body.append("$size[15]{$b{").append(quote(path.name)).append("}}\n\n")
				.append(quote(path.summary)).append("\n\n");
		body.append("$b{Recommended attributes}\n");
		if (path.stats.isEmpty())
			body.append("Use custom focus keywords for the attributes you want to track.\n");
		for (Focus focus : path.stats)
			body.append("- ").append(link("stat-" + focus.id, focus.name))
					.append(": ").append(quote(focus.reason)).append("\n");
		body.append("\n$b{LP and skill priorities}\n");
		if (path.skills.isEmpty())
			body.append("Add skill identifiers to the custom focus keywords.\n");
		for (Focus focus : path.skills)
			body.append("- ").append(link("stat-" + focus.id, focus.name))
					.append(": ").append(quote(focus.reason)).append("\n");
		body.append("\n$b{Tool and equipment checklist}\n")
				.append(quote(path.tools)).append("\n\n$b{Food guidance}\n")
				.append(quote(path.food)).append("\n\n$b{Curiosity guidance}\n")
				.append(quote(path.curiosities)).append("\n\n$b{Location hints}\n")
				.append(quote(path.locations)).append("\n\n$b{Milestones}\n");
		for (Goal goal : path.goals) {
			String ref = goalRef(path.id, goal);
			String marker = (state(ref) == 1) ? "[done]" : (state(ref) == 2)
					? "[skipped]" : pinned(ref) ? "[pinned]" : "[ ]";
			body.append("- ").append(marker).append(" ").append(quote(goal.stage))
					.append(": ").append(quote(goal.title)).append(" - ")
					.append(quote(goal.detail)).append("\n");
		}

		List<KnowledgeBase.Recipe> relevantRecipes = new ArrayList<KnowledgeBase.Recipe>();
		for (KnowledgeBase.Recipe recipe : KnowledgeBase.allRecipes())
			if (recipeMatches(path, recipe))
				relevantRecipes.add(recipe);
		if (!relevantRecipes.isEmpty()) {
			body.append("\n$b{Relevant discovered recipes}\n");
			for (int i = 0; (i < relevantRecipes.size()) && (i < 20); i++) {
				KnowledgeBase.Recipe recipe = relevantRecipes.get(i);
				body.append("- ").append(link(recipe.id(), recipe.name)).append("\n");
			}
		}

		int shown = 0;
		StringBuilder found = new StringBuilder();
		for (Discovered value : discovered.values()) {
			if (matchScore(path, value.name, value.resource, value.category) <= 0)
				continue;
			if (shown++ >= 20)
				break;
			found.append("- ").append(quote(value.name));
			if (value.category.length() > 0)
				found.append(" (").append(quote(value.category)).append(")");
			found.append("\n");
		}
		if (found.length() > 0)
			body.append("\n$b{Locally discovered relevant resources}\n")
					.append(found);
		return new KnowledgeBase.Document(path.documentId(), path.name,
				KnowledgeBase.PATHS, body.toString());
	}

	private static String encode(String value) throws IOException {
		return URLEncoder.encode(clean(value), "UTF-8");
	}

	private static String decode(String value) throws IOException {
		return URLDecoder.decode(value, "UTF-8");
	}

	private static synchronized void loadState() {
		if (!STATE_FILE.exists())
			return;
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(
					STATE_FILE), "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				if ((line.length() == 0) || line.startsWith("#"))
					continue;
				String[] fields = line.split("\\|", -1);
				try {
					if (fields[0].equals("S") && (fields.length >= 4)) {
						String ref = decode(fields[1]);
						int state = Integer.parseInt(fields[2]);
						if ((state == 1) || (state == 2))
							goalStates.put(ref, state);
						if (fields[3].equals("1"))
							pinnedGoals.add(ref);
					} else if (fields[0].equals("C") && (fields.length >= 3)) {
						customGoals.add(new CustomGoal(decode(fields[1]),
								decode(fields[2])));
					} else if (fields[0].equals("K") && (fields.length >= 2)) {
						customKeywords = decode(fields[1]);
					} else if (fields[0].equals("O") && (fields.length >= 2)) {
						String ref = decode(fields[1]);
						if (!orderedGoals.contains(ref))
							orderedGoals.add(ref);
					} else if (fields[0].equals("R") && (fields.length >= 4)) {
						String ref = decode(fields[1]);
						reminders.put(ref, new Reminder(ref, decode(fields[2]),
								Long.parseLong(fields[3])));
					} else if (fields[0].equals("D") && (fields.length >= 4)) {
						Discovered value = new Discovered(decode(fields[1]),
								decode(fields[2]), decode(fields[3]));
						String key = normalize(value.resource);
						if (key.length() == 0)
							key = normalize(value.name);
						discovered.put(key, value);
					}
				} catch (Exception e) {
				}
			}
		} catch (IOException e) {
			System.out.println("Could not load specialization plan: " + e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private static synchronized void saveState() {
		File temporary = new File(STATE_FILE.getPath() + ".new");
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(temporary), "UTF-8"));
			writer.write("# Solaris specialization plan v1");
			writer.newLine();
			for (String ref : goalStates.keySet()) {
				writer.write("S|" + encode(ref) + "|" + state(ref) + "|"
						+ (pinned(ref) ? "1" : "0"));
				writer.newLine();
			}
			for (String ref : pinnedGoals) {
				if (goalStates.containsKey(ref))
					continue;
				writer.write("S|" + encode(ref) + "|0|1");
				writer.newLine();
			}
			for (CustomGoal goal : customGoals) {
				writer.write("C|" + encode(goal.id) + "|" + encode(goal.title));
				writer.newLine();
			}
			writer.write("K|" + encode(customKeywords));
			writer.newLine();
			for (String ref : orderedGoals) {
				writer.write("O|" + encode(ref));
				writer.newLine();
			}
			for (Reminder reminder : reminders.values()) {
				writer.write("R|" + encode(reminder.ref) + "|"
						+ encode(reminder.title) + "|" + reminder.due);
				writer.newLine();
			}
			for (Discovered value : discovered.values()) {
				writer.write("D|" + encode(value.resource) + "|"
						+ encode(value.name) + "|" + encode(value.category));
				writer.newLine();
			}
		} catch (IOException e) {
			System.out.println("Could not save specialization plan: " + e);
			return;
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
				}
			}
		}
		File backup = new File(STATE_FILE.getPath() + ".bak");
		if (backup.exists())
			backup.delete();
		boolean hadOriginal = STATE_FILE.exists();
		if (hadOriginal && !STATE_FILE.renameTo(backup)) {
			temporary.delete();
			return;
		}
		if (temporary.renameTo(STATE_FILE)) {
			if (backup.exists())
				backup.delete();
		} else if (hadOriginal) {
			backup.renameTo(STATE_FILE);
		}
	}
}
