package haven;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Conservative, client-side fuel reference for documented Legacy recipes.
 *
 * The crafting protocol supplies ingredients and results, but no workstation
 * or burn-time metadata. Keep this table deliberately small: an absent entry
 * means that the client does not know the exact fuel requirement.
 */
public final class LegacyFuelGuide {
	private static final Map<String, Integer> OVEN_BRANCHES = new HashMap<String, Integer>();
	private static final Map<String, Integer> KILN_BRANCHES = new HashMap<String, Integer>();
	private static final Map<String, String> SMELTER_FUEL = new HashMap<String, String>();

	static {
		putOven(1, "Honey Bun");
		putOven(2, "Carrot Cake", "Raisin Butter-Cake");
		putOven(3, "Apple Pie", "Baked Birchbark Bream", "Blueberry Pie",
				"Bread", "Chantrelle & Onion Pirozhki", "Pea Pie", "Pumpkin Pie",
				"Ring of Brodgar (Baking)", "Ring of Brodgar", "Wellplaiced Pie");
		putOven(4, "Bark Bread", "Pumpkin Bread", "Shewbread");
		/* Only values explicitly documented on the Legacy item pages. */
		putKiln(2, "Brick");
		putKiln(4, "Raw Glass"); // the Legacy page documents this as a minimum
		putSmelter("Bar of Cast Iron", "Bar of Copper", "Bar of Tin",
				"Gold Nugget", "Silver Nugget", "Stone");
	}

	private LegacyFuelGuide() {
	}

	private static void putOven(int branches, String... recipes) {
		for (String recipe : recipes)
			OVEN_BRANCHES.put(key(recipe), branches);
	}

	private static void putKiln(int branches, String... recipes) {
		for (String recipe : recipes)
			KILN_BRANCHES.put(key(recipe), branches);
	}

	private static void putSmelter(String... recipes) {
		for (String recipe : recipes)
			SMELTER_FUEL.put(key(recipe),
					"Legacy ore smelter: at least 4 charcoal per load (1 charcoal = 1 tick). Wiki reference.");
	}

	private static String key(String value) {
		if (value == null)
			return "";
		String normalized = value.toLowerCase(Locale.ENGLISH)
				.replaceAll("[^a-z0-9]+", " ").trim();
		/* Preserve e.g. \"Baked Birchbark Bream\" while accepting \"Bake Bread\". */
		normalized = normalized.replaceFirst(
				"^(craft|make|bake|cook|prepare|roast)\\s+", "");
		return normalized.replace(" ", "");
	}

	/**
	 * Returns a display-ready exact requirement, or an empty string when the
	 * Legacy reference does not document this recipe.
	 */
	public static String forRecipe(String recipeName) {
		String normalized = key(recipeName);
		Integer branches = OVEN_BRANCHES.get(normalized);
		String station = "oven";
		if (branches == null) {
			branches = KILN_BRANCHES.get(normalized);
			station = "kiln";
		}
		if (branches == null)
			return SMELTER_FUEL.containsKey(normalized) ? SMELTER_FUEL.get(normalized) : "";
		String unit = (branches.intValue() == 1) ? "branch" : "branches";
		String minimum = (station.equals("kiln") && normalized.equals(key("Raw Glass")))
				? "minimum " : "";
		return "Legacy " + station + " fuel: " + minimum + branches + " " + unit + " ("
				+ branches + " " + ((branches.intValue() == 1) ? "tick" : "ticks")
				+ "); board/block/charcoal = 2 branch-equivalents (wiki reference).";
	}

	/** Returns the documented branch requirement, or -1 for non-branch fuel/unknown recipes. */
	public static int branchFuelForRecipe(String recipeName) {
		String normalized = key(recipeName);
		Integer branches = OVEN_BRANCHES.get(normalized);
		if (branches == null)
			branches = KILN_BRANCHES.get(normalized);
		return (branches == null) ? -1 : branches.intValue();
	}

	/**
	 * Returns recipe fuel for raw cooking ingredients whose recipe may not yet
	 * have been indexed by the client.  The server does not send workstation
	 * metadata, so this deliberately covers only unambiguous Legacy names.
	 */
	public static String forIngredient(String itemName, String resourceName) {
		String item = key(itemName);
		String resource = key(resourceName);
		String recipe = "";
		if ((item.contains("honeybun") && item.contains("dough"))
				|| (resource.contains("honeybun") && resource.contains("dough")))
			recipe = "Honey Bun";
		else if ((item.contains("carrotcake") && item.contains("dough"))
				|| (resource.contains("carrotcake") && resource.contains("dough")))
			recipe = "Carrot Cake";
		else if ((item.contains("blueberrypie") && item.contains("dough"))
				|| (resource.contains("blueberrypie") && resource.contains("dough")))
			recipe = "Blueberry Pie";
		if (recipe.length() == 0)
			return "";
		String fuel = forRecipe(recipe);
		return (fuel.length() == 0) ? "" : recipe + ": " + fuel;
	}

	/** Returns the documented branch requirement for a directly recognized ingredient, or -1. */
	public static int branchFuelForIngredient(String itemName, String resourceName) {
		String item = key(itemName);
		String resource = key(resourceName);
		if ((item.contains("honeybun") && item.contains("dough"))
				|| (resource.contains("honeybun") && resource.contains("dough")))
			return branchFuelForRecipe("Honey Bun");
		if ((item.contains("carrotcake") && item.contains("dough"))
				|| (resource.contains("carrotcake") && resource.contains("dough")))
			return branchFuelForRecipe("Carrot Cake");
		if ((item.contains("blueberrypie") && item.contains("dough"))
				|| (resource.contains("blueberrypie") && resource.contains("dough")))
			return branchFuelForRecipe("Blueberry Pie");
		return -1;
	}

	/** General, non-recipe-specific fuel reference for places where it is useful. */
	public static String fuelTypeForStation(String station) {
		String name = key(station);
		if (name.contains("oven") || name.contains("kiln") || name.contains("cauldron"))
			return "Wood-fired fuel: 1 branch = 1 tick; a board, block or charcoal = 2 branch-equivalents.";
		if (name.contains("fineryforge"))
			return "Legacy forge fuel is measured in ticks; exact fuel equivalence depends on the fuel type and job.";
		if (name.contains("smelter") || name.contains("alloyingcrucible")
				|| name.contains("steelcrucible"))
			return "This station uses charcoal fuel rather than branches; exact charcoal depends on the job.";
		return "";
	}
}
