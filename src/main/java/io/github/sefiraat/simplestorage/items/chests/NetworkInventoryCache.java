package io.github.sefiraat.simplestorage.items.chests;

import io.github.mooy1.infinitylib.items.StackUtils;
import io.github.sefiraat.simplestorage.SimpleStorage;
import io.github.sefiraat.simplestorage.items.SimpleStorageItemStacks;
import io.github.sefiraat.simplestorage.items.Skulls;
import io.github.sefiraat.simplestorage.items.chests.network.NetworkElement;
import io.github.sefiraat.simplestorage.items.chests.network.NetworkElement.NetworkElementType;
import io.github.sefiraat.simplestorage.items.chests.network.RemovalSet;
import io.github.sefiraat.simplestorage.runnables.RunnableHighlight;
import io.github.sefiraat.simplestorage.utils.Theme;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NetworkInventoryCache extends AbstractCache {

    private int tick;
    private final Map<Integer, NetworkElement> inventories = new HashMap<>();

    public NetworkInventoryCache(BlockMenu blockMenu) {
        super(blockMenu);
        process();
    }

    public void updateInventories() {
        inventories.clear();
        getInventories(blockMenu.getBlock(), SimpleStorage.inst().getManagerConfiguration().getVals().getNetworkRange());
        pages = (int) Math.ceil(inventories.size() / 45.0);
    }

    public void getInventories(@Nonnull Block block, int size) {
        int i = 0;
        for(int x = block.getLocation().getBlockX() - size; x <= block.getLocation().getBlockX() + size; x++) {
            for(int y = block.getLocation().getBlockY() - size; y <= block.getLocation().getBlockY() + size; y++) {
                for (int z = block.getLocation().getBlockZ() - size; z <= block.getLocation().getBlockZ() + size; z++) {
                    NetworkElement networkElement = getNetworkElement(block.getLocation().getWorld().getBlockAt(x, y, z));
                    if (networkElement != null) {
                        inventories.put(i, networkElement);
                        i++;
                    }
                }
            }
        }
    }

    @Nullable
    private NetworkElement getNetworkElement(@Nonnull Block block) {
        String s = BlockStorage.getLocationInfo(block.getLocation(), "id");
        if (s != null) {
            if (SimpleStorageItemStacks.INVENTORY_CELL.getItemId().equals(s)) { // Mine here - default regardless so who cares
                return getNetworkCell(block);
            } else {
                return checkBarrels(block, s);
            }
        }
        return null;
    }

    @Nullable
    private NetworkElement checkBarrels(Block block, String s) {
        if (hasUpgradeBarrel()) {
            if (NetworkInventoryCache.ACCEPTED_BASIC_INFINITY.contains(s)) { // Infinity (No skulls)
                return getNetworkBarrel(block, new int[]{8, 7, 6}, NetworkElementType.INFINITY_BARREL);
            } else if (NetworkInventoryCache.ACCEPTED_BASIC_FLUFFY.contains(s)) { // Fluffy Barrels
                return getNetworkBarrel(block, new int[]{8, 7, 6}, NetworkElementType.FLUFFY_BARREL);
            }
        }
        return null;
    }

    @Nonnull
    private NetworkElement getNetworkCell(Block block) {
        Location l = block.getLocation();
        NetworkElement networkElement = new NetworkElement(block, 8, 7, 6, getCellDisplayName(l), getCellDisplayMaterial(l));
        networkElement.setSkullTexture(Skulls.BLOCK_CELL_BASIC);
        networkElement.setType(NetworkElementType.INVENTORY_CELL);
        return networkElement;
    }

    @Nonnull
    private NetworkElement getNetworkBarrel(Block block, int[] slots, NetworkElementType type) {
        Location l = block.getLocation();
        NetworkElement networkElement = new NetworkElement(block, slots[0], slots[1], slots[2], getCellDisplayName(l), getCellDisplayMaterial(l));
        networkElement.setMaterial(block.getType());
        networkElement.setType(type);
        String storedAmount = BlockStorage.getLocationInfo(block.getLocation(), "stored");
        if (storedAmount != null) {
            networkElement.setBarrelAmount(Integer.parseInt(storedAmount));
        } else {
            networkElement.setBarrelAmount(0);
        }
        return networkElement;
    }

    private boolean hasUpgradeBarrel() {
        for (int i : NetworkChest.AUGMENT_SLOTS) {
            ItemStack itemStack = blockMenu.getItemInSlot(i);
            if (itemStack != null) {
                String id = StackUtils.getID(itemStack);
                if (id != null && id.equals(SimpleStorageItemStacks.UPGRADE_BARREL.getItemId())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private String getCellDisplayName(Location location) {
        return BlockStorage.getLocationInfo(location, "cellname");
    }

    @Nullable
    private Material getCellDisplayMaterial(Location location) {
        if (BlockStorage.getLocationInfo(location, "cellmaterial") != null) {
            return Material.valueOf(BlockStorage.getLocationInfo(location, "cellmaterial"));
        }
        return null;
    }

    private boolean validTick() {
        if (tick >= SimpleStorage.inst().getManagerConfiguration().getVals().getNetworkTickRate()) {
            tick = 1;
            return true;
        } else {
            tick++;
            return false;
        }
    }

    public void process() {
        if (blockMenu.hasViewer()) {
            if (page != prevPage || validTick()) {
                updateInventories();
                updateView();
                prevPage = page;
            }
        }
    }

    public void kill(Location location) {
        BlockStorage.clearBlockInfo(location);
    }

    public void updateView() {
        for (int i = 0; i < 45; i++) {
            int slotNo = i + 9;
            int listSlotNo = ((page - 1) * 45) + i;

            NetworkElement networkElement = inventories.get(listSlotNo);

            if (networkElement != null) {
                processCell(networkElement, slotNo);
            } else {
                blockMenu.replaceExistingItem(slotNo, GuiItems.menuMasterDummy());
                blockMenu.addMenuClickHandler(slotNo, (player, i1, itemStack1, clickAction) -> false);
            }
        }
    }

    private void processCell(@Nonnull NetworkElement networkElement, int slotNo) {
        // Set item and add handler
        blockMenu.replaceExistingItem(slotNo, GuiItems.menuCell(networkElement));
        blockMenu.addMenuClickHandler(slotNo, (player, i1, itemStack1, clickAction) -> guiItemClick(networkElement, player, clickAction));
    }

    private boolean guiItemClick(@Nonnull NetworkElement networkElement, Player player, @Nonnull ClickAction clickAction) {
        Block block = networkElement.getBlock();
        if (clickAction.isRightClicked()) {
            new RunnableHighlight(block.getLocation().clone().add(0.5, 0.5, 0.5)).runTaskTimer(SimpleStorage.inst(), 0, 40L);
            player.closeInventory();
            return false;
        } else {
            return guiItemLeftClick(networkElement, player);
        }
    }

    private boolean guiItemLeftClick(@Nonnull NetworkElement networkElement, Player player) {

        Block block = networkElement.getBlock();

        BlockMenu blockMenu = BlockStorage.getInventory(block);
        RemovalSet removalSet = new RemovalSet(
                blockMenu.getItemInSlot(networkElement.getSlotClose()),
                blockMenu.getMenuClickHandler(networkElement.getSlotClose()),
                blockMenu.getItemInSlot(networkElement.getSlotRename()),
                blockMenu.getMenuClickHandler(networkElement.getSlotRename()),
                blockMenu.getItemInSlot(networkElement.getSlotSetBlock()),
                blockMenu.getMenuClickHandler(networkElement.getSlotRename())
        );

        // Add Close item to drilled down inventory
        blockMenu.replaceExistingItem(networkElement.getSlotClose(), GuiItems.menuClose());
        blockMenu.addMenuClickHandler(networkElement.getSlotClose(), (player2, i2, itemStack2, clickAction2) -> guiClose(player2, blockMenu, networkElement, removalSet, true));

        // Add Rename item to drilled down inventory
        blockMenu.replaceExistingItem(networkElement.getSlotRename(), GuiItems.menuRenameCell());
        blockMenu.addMenuClickHandler(networkElement.getSlotRename(), (player2, i2, itemStack2, clickAction2) -> guiRename(block, player2));

        // Add SetMaterial item to drilled down inventory
        blockMenu.replaceExistingItem(networkElement.getSlotSetBlock(), GuiItems.menuSetMaterial());
        blockMenu.addMenuClickHandler(networkElement.getSlotSetBlock(), (player2, i2, itemStack2, clickAction2) -> guiSetMaterial(block, player2));

        blockMenu.addMenuCloseHandler(player1 -> guiClose(player1, blockMenu, networkElement, removalSet,false));

        blockMenu.open(player);
        return false;

    }

    private boolean guiClose(Player player, @Nonnull BlockMenu invMenu, @Nonnull NetworkElement networkElement, @Nonnull RemovalSet removalSet, boolean back) {
        invMenu.replaceExistingItem(networkElement.getSlotClose(), removalSet.getOriginalGuiClose());
        invMenu.addMenuClickHandler(networkElement.getSlotClose(), removalSet.getCloseMenuClickHandler());
        invMenu.replaceExistingItem(networkElement.getSlotRename(), removalSet.getOriginalGuiRename());
        invMenu.addMenuClickHandler(networkElement.getSlotRename(), removalSet.getRenameMenuClickHandler());
        invMenu.replaceExistingItem(networkElement.getSlotSetBlock(), removalSet.getOriginalGuiBlock());
        invMenu.addMenuClickHandler(networkElement.getSlotSetBlock(), removalSet.getBlockMenuClickHandler());
        if (back) {
            BlockMenu blockMenu = this.blockMenu;
            blockMenu.open(player);
        }
        return false;
    }

    private boolean guiRename(Block block, @Nonnull Player player) {
        // Prepare for item name in chat, flag rename item for removal
        player.closeInventory();
        player.sendMessage(Theme.WARNING + "Enter a new name for this inventory cell, supports colour codes");
        ChatUtils.awaitInput(player, message -> {
            BlockStorage.addBlockInfo(block, "cellname", message);
            player.sendMessage(Theme.SUCCESS + "Cell name updated");
        });
        return false;
    }

    private boolean guiSetMaterial(Block block, @Nonnull Player player) {
        Material m = player.getItemOnCursor().getType();
        if (m != Material.AIR) {
            BlockStorage.addBlockInfo(block, "cellmaterial", m.toString());
            player.sendMessage(Theme.SUCCESS + "Icon material set");
        } else {
            player.sendMessage(Theme.ERROR + "You must have an item on your cursor");
        }
        return false;
    }

    public static final List<String> ACCEPTED_BASIC_INFINITY = new ArrayList<>(Arrays.asList(
            "BASIC_STORAGE",
            "ADVANCED_STORAGE",
            "REINFORCED_STORAGE",
            "VOID_STORAGE",
            "INFINITY_STORAGE"
    ));

    public static final List<String> ACCEPTED_BASIC_FLUFFY = new ArrayList<>(Arrays.asList(
            "SMALL_FLUFFY_BARREL",
            "MEDIUM_FLUFFY_BARREL",
            "BIG_FLUFFY_BARREL",
            "LARGE_FLUFFY_BARREL",
            "MASSIVE_FLUFFY_BARREL",
            "BOTTOMLESS_FLUFFY_BARREL"
    ));



}
