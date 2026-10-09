package dev.vadim.rocketpouch;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class RocketPouch implements ModInitializer {
	public static final String MOD_ID = "rocketpouch";

	public static final DataComponentType<PouchContents> CONTENTS = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		id("contents"),
		DataComponentType.<PouchContents>builder().persistent(PouchContents.CODEC).networkSynchronized(PouchContents.STREAM_CODEC).build()
	);

	private static final ResourceKey<Item> POUCH_KEY = ResourceKey.create(Registries.ITEM, id("rocket_pouch"));
	public static final Item POUCH = Registry.register(
		BuiltInRegistries.ITEM,
		POUCH_KEY,
		new RocketPouchItem(new Item.Properties().setId(POUCH_KEY).stacksTo(1).fireResistant())
	);

	@Override
	public void onInitialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.insertAfter(Items.FIREWORK_ROCKET, POUCH));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
