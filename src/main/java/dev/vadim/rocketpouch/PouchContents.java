package dev.vadim.rocketpouch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/** One kind of rocket (as a count-1 template) and how many of it are stored. An empty pouch has no component. */
public record PouchContents(ItemStackTemplate rocket, int count) {
	public static final int CAPACITY = 64 * 5;

	public static final Codec<PouchContents> CODEC = RecordCodecBuilder.create(i -> i.group(
		ItemStackTemplate.CODEC.fieldOf("rocket").forGetter(PouchContents::rocket),
		Codec.intRange(1, CAPACITY).fieldOf("count").forGetter(PouchContents::count)
	).apply(i, PouchContents::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, PouchContents> STREAM_CODEC = StreamCodec.composite(
		ItemStackTemplate.STREAM_CODEC, PouchContents::rocket,
		ByteBufCodecs.VAR_INT, PouchContents::count,
		PouchContents::new
	);

	public boolean matches(ItemStack stack) {
		return ItemStack.isSameItemSameComponents(stack, rocket.create());
	}

	public ItemStack createStack(int amount) {
		return rocket.withCount(amount).create();
	}
}
