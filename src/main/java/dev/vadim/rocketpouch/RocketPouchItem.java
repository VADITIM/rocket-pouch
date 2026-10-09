package dev.vadim.rocketpouch;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RocketPouchItem extends Item {
	public RocketPouchItem(Item.Properties properties) {
		super(properties);
	}

	// --- Acting like a firework rocket (mirrors FireworkRocketItem) ---

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack pouch = player.getItemInHand(hand);
		PouchContents contents = pouch.get(RocketPouch.CONTENTS);
		if (!player.isFallFlying() || contents == null) {
			return InteractionResult.PASS;
		}

		if (level instanceof ServerLevel serverLevel) {
			if (player.dropAllLeashConnections(null)) {
				level.playSound(null, player, SoundEvents.LEAD_BREAK, SoundSource.NEUTRAL, 1.0F, 1.0F);
			}

			ItemStack rocket = contents.createStack(1);
			Projectile.spawnProjectile(new FireworkRocketEntity(level, rocket, player), serverLevel, rocket);
			consumeOne(pouch, contents, player);
			player.awardStat(Stats.ITEM_USED.get(Items.FIREWORK_ROCKET));
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		ItemStack pouch = context.getItemInHand();
		PouchContents contents = pouch.get(RocketPouch.CONTENTS);
		if (contents == null || player != null && player.isFallFlying()) {
			return InteractionResult.PASS;
		}

		if (context.getLevel() instanceof ServerLevel serverLevel) {
			Vec3 click = context.getClickLocation();
			Direction face = context.getClickedFace();
			ItemStack rocket = contents.createStack(1);
			Projectile.spawnProjectile(
				new FireworkRocketEntity(
					serverLevel,
					player,
					click.x + face.getStepX() * 0.15,
					click.y + face.getStepY() * 0.15,
					click.z + face.getStepZ() * 0.15,
					rocket
				),
				serverLevel,
				rocket
			);
			consumeOne(pouch, contents, player);
		}

		return InteractionResult.SUCCESS;
	}

	private static void consumeOne(ItemStack pouch, PouchContents contents, @Nullable Player player) {
		if (player == null || !player.hasInfiniteMaterials()) {
			setCount(pouch, contents.rocket(), contents.count() - 1);
		}
	}

	// --- Filling and emptying in inventories (bundle-style clicks) ---

	/** Cursor holds a stack and clicks the pouch: left-click inserts rockets, right-click with an empty cursor takes a stack out. */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack pouch, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		if (!slot.allowModification(player)) {
			return false;
		}

		if (action == ClickAction.PRIMARY && other.is(Items.FIREWORK_ROCKET)) {
			playInsertSound(player, insert(pouch, other));
			return true;
		}

		if (action == ClickAction.SECONDARY && other.isEmpty()) {
			ItemStack taken = takeStack(pouch);
			if (!taken.isEmpty()) {
				carried.set(taken);
				playRemoveSound(player);
			}
			return true;
		}

		return false;
	}

	/** Cursor holds the pouch and clicks a slot: left-click pulls rockets in, right-click on an empty slot puts a stack there. */
	@Override
	public boolean overrideStackedOnOther(ItemStack pouch, Slot slot, ClickAction action, Player player) {
		ItemStack other = slot.getItem();

		if (action == ClickAction.PRIMARY && other.is(Items.FIREWORK_ROCKET)) {
			int room = room(pouch, other);
			int inserted = room > 0 ? insert(pouch, slot.safeTake(other.getCount(), room, player)) : 0;
			playInsertSound(player, inserted);
			return true;
		}

		if (action == ClickAction.SECONDARY && other.isEmpty()) {
			ItemStack taken = takeStack(pouch);
			if (!taken.isEmpty()) {
				ItemStack remainder = slot.safeInsert(taken);
				if (remainder.isEmpty()) {
					playRemoveSound(player);
				} else {
					insert(pouch, remainder);
				}
			}
			return true;
		}

		return false;
	}

	/** How many of {@code rocket} would fit. 0 if a different kind of rocket is already stored. */
	private static int room(ItemStack pouch, ItemStack rocket) {
		PouchContents contents = pouch.get(RocketPouch.CONTENTS);
		if (contents == null) {
			return PouchContents.CAPACITY;
		}
		return contents.matches(rocket) ? PouchContents.CAPACITY - contents.count() : 0;
	}

	/** Moves as many rockets as fit from {@code rocket} into the pouch, shrinking it. Returns the amount moved. */
	private static int insert(ItemStack pouch, ItemStack rocket) {
		int moved = Math.min(rocket.getCount(), room(pouch, rocket));
		if (moved > 0) {
			setCount(pouch, ItemStackTemplate.fromNonEmptyStack(rocket).withCount(1), count(pouch) + moved);
			rocket.shrink(moved);
		}
		return moved;
	}

	private static ItemStack takeStack(ItemStack pouch) {
		PouchContents contents = pouch.get(RocketPouch.CONTENTS);
		if (contents == null) {
			return ItemStack.EMPTY;
		}
		int amount = Math.min(contents.count(), Items.FIREWORK_ROCKET.getDefaultMaxStackSize());
		setCount(pouch, contents.rocket(), contents.count() - amount);
		return contents.createStack(amount);
	}

	private static int count(ItemStack pouch) {
		PouchContents contents = pouch.get(RocketPouch.CONTENTS);
		return contents == null ? 0 : contents.count();
	}

	private static void setCount(ItemStack pouch, ItemStackTemplate rocket, int count) {
		if (count <= 0) {
			pouch.remove(RocketPouch.CONTENTS);
		} else {
			pouch.set(RocketPouch.CONTENTS, new PouchContents(rocket, count));
		}
	}

	private static void playInsertSound(Player player, int inserted) {
		if (inserted > 0) {
			player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
		} else {
			player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
		}
	}

	private static void playRemoveSound(Player player) {
		player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
	}

	// --- Fill display: the durability bar shows how full the pouch is ---

	@Override
	public boolean isBarVisible(ItemStack pouch) {
		return pouch.has(RocketPouch.CONTENTS);
	}

	@Override
	public int getBarWidth(ItemStack pouch) {
		return Mth.clamp(Math.round(count(pouch) * (float) MAX_BAR_WIDTH / PouchContents.CAPACITY), 1, MAX_BAR_WIDTH);
	}

	@Override
	public int getBarColor(ItemStack pouch) {
		float fill = (float) count(pouch) / PouchContents.CAPACITY;
		return Mth.hsvToRgb(fill / 3.0F, 1.0F, 1.0F);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack pouch, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		PouchContents contents = pouch.get(RocketPouch.CONTENTS);
		if (contents == null) {
			builder.accept(Component.translatable("item.rocketpouch.rocket_pouch.empty").withStyle(ChatFormatting.GRAY));
			return;
		}

		builder.accept(Component.translatable("item.rocketpouch.rocket_pouch.count", contents.count(), PouchContents.CAPACITY).withStyle(ChatFormatting.GRAY));
		ItemStack rocket = contents.createStack(1);
		Fireworks fireworks = rocket.get(DataComponents.FIREWORKS);
		if (fireworks != null) {
			fireworks.addToTooltip(context, builder, flag, rocket);
		}
	}
}
