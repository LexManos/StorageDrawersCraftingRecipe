package net.minecraftforge.lex;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.SpecialRecipe;
import net.minecraft.item.crafting.SpecialRecipeSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import com.jaquadro.minecraft.storagedrawers.block.tile.tiledata.UpgradeData;
import com.jaquadro.minecraft.storagedrawers.core.ModItems;
import com.jaquadro.minecraft.storagedrawers.item.ItemDrawers;
import com.jaquadro.minecraft.storagedrawers.item.ItemUpgrade;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import sun.misc.Unsafe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod(StorageDrawerCraftingRecipe.MOD_ID)
public class StorageDrawerCraftingRecipe {
    public static final String MOD_ID = "sdcr";
    private static final DeferredRegister<IRecipeSerializer<?>> RECIPES = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MOD_ID);
    public static final RegistryObject<IRecipeSerializer<AddUpgradeRecipe>> UPGRADE_RECIPE_SERIALIZER = RECIPES.register("add_upgrade", () -> new SpecialRecipeSerializer<>(AddUpgradeRecipe::new));

    @SuppressWarnings("restriction")
    private static final Unsafe UNSAFE = getUnsafe();
    @SuppressWarnings("restriction")
    private static final Unsafe getUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe)f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Unable to get Unsafe reference, this should never be possible," +
                    " be sure to report this will exact details on what JVM you're running.", e);
        }
    }
    private static final long upgradesIndex = getOffset(UpgradeData.class, "upgrades");
    @SuppressWarnings("restriction")
    private static long getOffset(Class<?> clz, String name) {
        try {
            return UNSAFE.objectFieldOffset(clz.getDeclaredField(name));
        } catch (Exception e) {
            throw new RuntimeException("Unable to get index for " + clz.getName() + "." + name + ", " +
                    " be sure to report this will exact details on what JVM you're running.", e);
        }
    }
    @SuppressWarnings("restriction")
    private static final ItemStack[] getUpgrades(UpgradeData data) {
        return (ItemStack[])UNSAFE.getObject(data, upgradesIndex);
    }

    public StorageDrawerCraftingRecipe() {
        RECIPES.register(FMLJavaModLoadingContext.get().getModEventBus());
        //MinecraftForge.EVENT_BUS.register(this);
    }

    /* TODO: Add tooltips showing what upgrades are installed... If I care.
    @SubscribeEvent
    private void onTooltip(ItemTooltipEvent event) {
    }
    */

    private static class AddUpgradeRecipe extends SpecialRecipe {
        public AddUpgradeRecipe(ResourceLocation name) {
            super(name);
        }

        @Override
        public boolean matches(CraftingInventory inv, World world) {
            return findContext(inv) != null;
        }

        @Override
        public ItemStack assemble(CraftingInventory inv) {
            Context ctx = findContext(inv);
            if (ctx == null)
                return ItemStack.EMPTY;
            ItemStack ret = ctx.drawer.copy();
            ret.getOrCreateTag().put("tile", ctx.data.write(ret.getOrCreateTag().getCompound("tile")));
            return ret;
        }

        private static class Context {
            ItemStack drawer = ItemStack.EMPTY;
            List<ItemStack> upgrades = new ArrayList<>();
            UpgradeData data = null;
        }

        @Nullable
        private Context findContext(CraftingInventory inv) {
            Context ret = new Context();
            for (int x = 0; x < inv.getContainerSize(); x++) {
                ItemStack stack = inv.getItem(x);
                if (stack.isEmpty())
                    continue;

                if (stack.getItem() instanceof ItemDrawers) {
                    if (!ret.drawer.isEmpty())
                        return null;
                    ret.drawer = stack;
                } else if (stack.getItem() instanceof ItemUpgrade)
                    ret.upgrades.add(stack);
                else
                    return null;
            }

            if (ret.drawer.isEmpty() || ret.upgrades.isEmpty())
                return null;

            ret.data = new UpgradeData(7) { //Hard coded to 7 as the only use is TileEntityDrawers$DrawerUpgradeData
                @Override
                public boolean setUpgrade(int slot, @Nonnull ItemStack upgrade) { //Override this to bypass a lot of the complex logic
                    if (upgrade.isEmpty())
                        return false;
                    upgrade = upgrade.copy();
                    upgrade.setCount(1);
                    getUpgrades(this)[slot] = upgrade;
                    return true;
                }
            };

            if (ret.drawer.hasTag() && ret.drawer.getTag().contains("tile"))
                ret.data.read(ret.drawer.getTag().getCompound("tile"));

            for (ItemStack upgrade : ret.upgrades) {
                if (upgrade.getItem() == ModItems.ONE_STACK_UPGRADE)
                    return null; //I don't want to dig into finding the stack sizes to check if we can downgrade. So just don't allow this one >.>
                if (!hasEmptySlot(ret.data) || !ret.data.canAddUpgrade(upgrade))
                    return null;
                ret.data.addUpgrade(upgrade);
            }

            return ret;
        }

        private boolean hasEmptySlot(UpgradeData data) {
            ItemStack[] upgrades = getUpgrades(data);
            if (upgrades == null)
                return false;

            for (ItemStack stack : upgrades) {
                if (stack == null || stack.isEmpty())
                    return true;
            }

            return false;
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return width * height >= 2;
        }

        @Override
        public IRecipeSerializer<?> getSerializer() {
            return UPGRADE_RECIPE_SERIALIZER.get();
        }
    }
}
