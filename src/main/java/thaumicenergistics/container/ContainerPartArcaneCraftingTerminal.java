package thaumicenergistics.container;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.common.items.wands.ItemWandCasting;
import thaumicenergistics.container.slot.SlotArcaneCraftingResult;
import thaumicenergistics.container.slot.SlotArmor;
import thaumicenergistics.container.slot.SlotRestrictive;
import thaumicenergistics.gui.GuiArcaneCraftingTerminal;
import thaumicenergistics.gui.ThEGuiHandler;
import thaumicenergistics.integration.tc.ArcaneRecipeHelper;
import thaumicenergistics.network.packet.client.Packet_C_ArcaneCraftingTerminal;
import thaumicenergistics.parts.AEPartArcaneCraftingTerminal;
import thaumicenergistics.util.EffectiveSide;
import thaumicenergistics.util.GuiHelper;
import thaumicenergistics.util.ThEUtils;
import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerCraftAmount;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ContainerPartArcaneCraftingTerminal
	extends ContainerWithPlayerInventory
	implements IMEMonitorHandlerReceiver<IAEItemStack>
{
	/**
	 * Holds a single aspect cost for the current recipe.
	 * 
	 * @author Nividica
	 * 
	 */
	public class ArcaneCrafingCost
	{
		/**
		 * How much vis does the recipe require?
		 */
		public final float visCost;

		/**
		 * Which aspect?
		 */
		public final Aspect primal;

		/**
		 * Do we have enough of this aspect in the wand to perform the craft?
		 */
		public final boolean hasEnoughVis;

		public ArcaneCrafingCost( final float visCost, final Aspect primal, final boolean hasEnough )
		{
			// Round to 1 decimal place
			this.visCost = Math.round( visCost * 10.0F ) / 10.0F;

			this.primal = primal;

			this.hasEnoughVis = hasEnough;
		}
	}

	/**
	 * Y position for the player and hotbar inventory.
	 */
	private static int PLAYER_INV_POSITION_Y = 162, HOTBAR_INV_POSITION_Y = 220;

	/**
	 * Row and Column counts of the crafting grid.
	 */
	private static int CRAFTING_GRID_SIZE = 3;

	/**
	 * Total number of slots in the crafting grid
	 */
	public static int CRAFTING_GRID_TOTAL_SIZE = CRAFTING_GRID_SIZE * CRAFTING_GRID_SIZE;

	/**
	 * Starting position for crafting slots.
	 */
	public static int CRAFTING_SLOT_X_POS = 44, CRAFTING_SLOT_Y_POS = 90;

	/**
	 * Position of the output slot.
	 */
	private static int RESULT_SLOT_X_POS = 116, RESULT_SLOT_Y_POS = 126;

	/**
	 * Position of the wand slot.
	 */
	private static int WAND_SLOT_XPOS = 116, WAND_SLOT_YPOS = 90;

	/**
	 * Starting position of the view slots
	 */
	public static int VIEW_SLOT_XPOS = 206, VIEW_SLOT_YPOS = 8;

	/**
	 * Starting position for armor slots.
	 */
	public static int ARMOR_SLOT_X_POS = 8, ARMOR_SLOT_Y_POS = 81, ARMOR_SLOT_COUNT = 4;

	/**
	 * Cache the crafting manager instance
	 */
	private static CraftingManager CRAFT_MANAGER = CraftingManager.getInstance();

	/**
	 * The arcane crafting terminal associated with the container.
	 */
	private AEPartArcaneCraftingTerminal arcaneCraftingTerminalPart;

	/**
	 * The player associated with this container.
	 */
	private EntityPlayer player;

	/**
	 * Slot number of the first and last crafting slots.
	 */
	private int firstCraftingSlotNumber = -1, lastCraftingSlotNumber = -1;

	/**
	 * Slot number of the first and last view slots.
	 */
	private int firstViewSlotNumber = -1, lastViewSlotNumber = -1;

	/**
	 * Slot number of the wand.
	 */
	private int wandSlotNumber = -1;

	/**
	 * Slot number of the result.
	 */
	private int resultSlotNumber = -1;

	/**
	 * The wand currently in the wand slot.
	 */
	private ItemStack wand;

	/**
	 * The required aspects for the current recipe.
	 */
	private AspectList requiredAspects;

	/**
	 * The required aspects, costs, and missing for the current recipe.
	 */
	private List<ArcaneCrafingCost> craftingCost = new ArrayList<ArcaneCrafingCost>();

	/**
	 * The AE network item monitor we are attached to.
	 */
	private IMEMonitor<IAEItemStack> monitor;

	/**
	 * Network source representing the player who is interacting with the
	 * container.
	 */
	private PlayerSource playerSource;

	/**
	 * Creates the container
	 * 
	 * @param terminal
	 * @param player
	 */
	public ContainerPartArcaneCraftingTerminal( final AEPartArcaneCraftingTerminal terminal, final EntityPlayer player )
	{
		// Set the part
		this.arcaneCraftingTerminalPart = terminal;

		// Set the player
		this.player = player;

		// Set the player source
		this.playerSource = new PlayerSource( this.player, terminal );

		// Bind to the players inventory
		this.bindPlayerInventory( player.inventory, ContainerPartArcaneCraftingTerminal.PLAYER_INV_POSITION_Y,
			ContainerPartArcaneCraftingTerminal.HOTBAR_INV_POSITION_Y );

		// Add crafting slots
		Slot craftingSlot = null;
		for( int row = 0; row < ContainerPartArcaneCraftingTerminal.CRAFTING_GRID_SIZE; row++ )
		{
			for( int column = 0; column < ContainerPartArcaneCraftingTerminal.CRAFTING_GRID_SIZE; column++ )
			{
				// Calculate the index
				int slotIndex = ( row * ContainerPartArcaneCraftingTerminal.CRAFTING_GRID_SIZE ) + column;

				// Create the slot
				craftingSlot = new Slot( terminal, slotIndex, ContainerPartArcaneCraftingTerminal.CRAFTING_SLOT_X_POS +
								( column * ContainerWithPlayerInventory.SLOT_SIZE ), ContainerPartArcaneCraftingTerminal.CRAFTING_SLOT_Y_POS +
								( row * ContainerWithPlayerInventory.SLOT_SIZE ) );

				// Add the slot
				this.addSlotToContainer( craftingSlot );

				// Check first crafting slot number
				if( ( row + column ) == 0 )
				{
					this.firstCraftingSlotNumber = craftingSlot.slotNumber;
				}
			}
		}

		// Set last crafting slot number
		if( craftingSlot != null )
		{
			this.lastCraftingSlotNumber = craftingSlot.slotNumber;
		}

		// Create the result slot
		SlotArcaneCraftingResult resultSlot = new SlotArcaneCraftingResult( player, this, terminal, terminal,
						AEPartArcaneCraftingTerminal.RESULT_SLOT_INDEX, ContainerPartArcaneCraftingTerminal.RESULT_SLOT_X_POS,
						ContainerPartArcaneCraftingTerminal.RESULT_SLOT_Y_POS );

		// Add the result slot
		this.addSlotToContainer( resultSlot );

		// Set the result slot number
		this.resultSlotNumber = resultSlot.slotNumber;

		// Create the wand slot
		SlotRestrictive wandSlot = new SlotRestrictive( terminal, AEPartArcaneCraftingTerminal.WAND_SLOT_INDEX,
						ContainerPartArcaneCraftingTerminal.WAND_SLOT_XPOS, ContainerPartArcaneCraftingTerminal.WAND_SLOT_YPOS );

		// Add the wand slot
		this.addSlotToContainer( wandSlot );

		// Set wand slot number
		this.wandSlotNumber = wandSlot.slotNumber;

		// Create the view slots
		SlotRestrictive viewSlot = null;
		for( int viewSlotID = AEPartArcaneCraftingTerminal.VIEW_SLOT_MIN; viewSlotID <= AEPartArcaneCraftingTerminal.VIEW_SLOT_MAX; viewSlotID++ )
		{
			// Calculate the y position
			int row = viewSlotID - AEPartArcaneCraftingTerminal.VIEW_SLOT_MIN;
			int yPos = ContainerPartArcaneCraftingTerminal.VIEW_SLOT_YPOS + ( row * ContainerWithPlayerInventory.SLOT_SIZE );

			// Create the slot
			viewSlot = new SlotRestrictive( terminal, viewSlotID, ContainerPartArcaneCraftingTerminal.VIEW_SLOT_XPOS, yPos );

			// Add the slot
			this.addSlotToContainer( viewSlot );

			// Check first view slot
			if( row == 0 )
			{
				this.firstViewSlotNumber = viewSlot.slotNumber;
			}
		}

		// Set last view slot number
		if( viewSlot != null )
		{
			this.lastViewSlotNumber = viewSlot.slotNumber;
		}

		// Create the armor slots
		for( int armorIndex = 0; armorIndex < ContainerPartArcaneCraftingTerminal.ARMOR_SLOT_COUNT; ++armorIndex )
		{
			// Calculate y position
			int yPos = ContainerPartArcaneCraftingTerminal.ARMOR_SLOT_Y_POS + ( ContainerWithPlayerInventory.SLOT_SIZE * armorIndex );

			// Create the slot
			SlotArmor armorSlot = new SlotArmor( terminal, AEPartArcaneCraftingTerminal.ARMOR_SLOT_MIN + armorIndex,
							ContainerPartArcaneCraftingTerminal.ARMOR_SLOT_X_POS, yPos, armorIndex, false );

			// Add to container
			this.addSlotToContainer( armorSlot );

			/**
			 * Notes about the hidden slots
			 * I hate this, but I have tried everything I can think of, and the
			 * equipped armor will not change immediately unless
			 * the notification comes from the open container, this container.
			 * TODO: *sigh*... player.inventory.markDirty() exists .-.
			 */
			// Create the 'hidden'slot
			armorSlot = new SlotArmor( player.inventory, 36 + ( 3 - armorIndex ), 0, -1000, armorIndex, false );

			// Add to container
			this.addSlotToContainer( armorSlot );

		}

		// Is this server side?
		if( EffectiveSide.isServerSide() )
		{
			// Register with part
			this.registerForUpdates();

			// Get the AE monitor
			this.monitor = terminal.getItemInventory();

			// Did we get a monitor?
			if( this.monitor != null )
			{
				// Register with the monitor.
				this.monitor.addListener( this, null );
			}
		}
	}

	/**
	 * Attempts to clear the crafting grid by placing the items
	 * back in the ME network.
	 * 
	 * @param sendUpdate
	 * If true, any changes made are sent across the network
	 * @return
	 */
	private boolean clearCraftingGrid( final boolean sendUpdate )
	{
		// Ignored client side
		if( EffectiveSide.isClientSide() )
		{
			return false;
		}

		// Assume the grid is clear
		boolean clearedAll = true;

		for( int index = this.firstCraftingSlotNumber; index <= this.lastCraftingSlotNumber; index++ )
		{
			// Get the slot
			Slot slot = (Slot)this.inventorySlots.get( index );

			// Ensure the slot is not null and has a stack
			if( ( slot == null ) || ( !slot.getHasStack() ) )
			{
				continue;
			}

			// Set the stack
			ItemStack slotStack = slot.getStack();

			// Inject into the ME network
			boolean didMerge = this.mergeWithMENetwork( slotStack );

			// Did any merge?
			if( !didMerge )
			{
				// Items are left over in the grid
				clearedAll = false;
				continue;
			}

			// Did the merger drain the stack?
			if( ( slotStack == null ) || ( slotStack.stackSize == 0 ) )
			{
				// Set the slot to have no item
				slot.putStack( null );
			}
			else
			{
				// Items are left over in the grid
				clearedAll = false;

				// Inform the slot its stack changed;
				slot.onSlotChanged();
			}
		}

		// Update
		this.detectAndSendChanges();

		// Return if we cleared the whole grid or not
		return clearedAll;
	}

	/**
	 * Handles automatically crafting items when the crafting
	 * output slot is shift+clicked
	 * 
	 * @param player
	 */
	private void doShiftAutoCrafting( final EntityPlayer player )
	{
		// Tracks if a crafting result could be placed in the players inventory
		boolean didMerge;

		// Tracks how many items we have made
		int autoCraftCounter = 0;

		// Get the result slot
		SlotArcaneCraftingResult resultSlot = (SlotArcaneCraftingResult)this.getSlot( this.resultSlotNumber );

		// Get the current crafting result.
		ItemStack resultStack = resultSlot.getStack();

		// Make a copy of it
		ItemStack slotStackOriginal = resultStack.copy();

		for( autoCraftCounter = slotStackOriginal.stackSize; autoCraftCounter <= 64; autoCraftCounter += slotStackOriginal.stackSize )
		{
			// Attempt to merge with the player inventory
			didMerge = ( this.mergeSlotWithPlayerInventory( resultStack ) || this.mergeSlotWithHotbarInventory( resultStack ) );

			// Were we able to merge?
			if( didMerge )
			{
				// Let the result slot know it was picked up
				resultSlot.onPickupFromSlotViaTransfer( player, resultStack );

				// Update the matrix
				this.onCraftMatrixChanged( null );

				// Get the stack in the result slot now.
				resultStack = resultSlot.getStack();

				// Is it empty?
				if( ( resultStack == null ) || ( resultStack.stackSize == 0 ) )
				{
					// Can't craft anymore, break the loop
					break;
				}
				// Does it still match the output item?
				if( !resultStack.getItem().equals( slotStackOriginal.getItem() ) )
				{
					// Crafting result changed, break the loop
					break;
				}
			}
			else
			{
				// Unable to merge results, break the loop
				break;
			}

		}

		// Did we do any crafting?
		if( autoCraftCounter > 0 )
		{
			// Mark the result slot as dirty
			resultSlot.onSlotChanged();

			// Send changes
			this.detectAndSendChanges();
		}
	}

	/**
	 * Checks if two stacks match. Either directly, or by ore dictionary.
	 * 
	 * @param keyStack
	 * @param potentialMatch
	 * @return
	 */
	private boolean doStacksMatch( final IAEItemStack keyStack, final IAEItemStack potentialMatch )
	{
		// Do the stacks directly match?
		if( keyStack.getItemStack().isItemEqual( potentialMatch.getItemStack() ) )
		{
			return true;
		}

		// No direct match, see if they match via the ore dictionary

		// Get the ore dictionary Id's
		int[] keyIDs = OreDictionary.getOreIDs( keyStack.getItemStack() );
		int[] matchIDs = OreDictionary.getOreIDs( potentialMatch.getItemStack() );

		// Is either item not registered?
		if( ( keyIDs.length == 0 ) || ( matchIDs.length == 0 ) )
		{
			return false;
		}

		// Do the keys match?
		for( int keyID : keyIDs )
		{
			for( int matchID : matchIDs )
			{
				if( keyID == matchID )
				{
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Searches for a matching arcane crafting recipe result
	 * 
	 * @return ItemStack of the result if found, null otherwise.
	 */
	private ItemStack findMatchingArcaneResult()
	{
		ItemStack arcaneResult = null;

		// Is there a matching recipe?
		IArcaneRecipe matchingRecipe = ArcaneRecipeHelper.INSTANCE.findMatchingArcaneResult( this.arcaneCraftingTerminalPart, 0,
			ContainerPartArcaneCraftingTerminal.CRAFTING_GRID_TOTAL_SIZE, this.player );

		if( matchingRecipe != null )
		{
			// Found a match, validate it.
			arcaneResult = this.validateWandVisAmount( matchingRecipe );
		}

		// Return the result
		return arcaneResult;
	}

	/**
	 * Searches for a matching regular(non arcane) crafting recipe result
	 * 
	 * @return ItemStack of the result if found, null otherwise.
	 */
	private ItemStack findMatchingRegularResult()
	{
		// Create a new crafting inventory
		InventoryCrafting craftingInventory = new InventoryCrafting( new ContainerInternalCrafting(),
						ContainerPartArcaneCraftingTerminal.CRAFTING_GRID_SIZE, ContainerPartArcaneCraftingTerminal.CRAFTING_GRID_SIZE );

		// Load the inventory based on what is in the part's inventory
		for( int slotIndex = 0; slotIndex < ( ContainerPartArcaneCraftingTerminal.CRAFTING_GRID_TOTAL_SIZE ); slotIndex++ )
		{
			// Set the slot
			craftingInventory.setInventorySlotContents( slotIndex, this.arcaneCraftingTerminalPart.getStackInSlot( slotIndex ) );
		}

		// Return the result
		return CRAFT_MANAGER.findMatchingRecipe( craftingInventory, this.arcaneCraftingTerminalPart.getWorldObj() );
	}

	/**
	 * Gets the view cells in the terminal.
	 * 
	 * @return
	 */
	private ItemStack[] getViewCells()
	{
		List<ItemStack> viewCells = new ArrayList<ItemStack>();

		Slot viewSlot;
		for( int viewSlotIndex = this.firstViewSlotNumber; viewSlotIndex <= this.lastViewSlotNumber; viewSlotIndex++ )
		{
			// Get the slot
			viewSlot = this.getSlot( viewSlotIndex );

			// Ensure the slot is not empty
			if( !viewSlot.getHasStack() )
			{
				continue;
			}

			// Add the cell
			viewCells.add( viewSlot.getStack() );
		}

		return viewCells.toArray( new ItemStack[viewCells.size()] );
	}

	/**
	 * Sets 'wand' if there is a wand in the wand slot.
	 */
	private void getWand()
	{
		// Get the wand slot
		Slot wandSlot = this.getSlot( this.wandSlotNumber );

		// Ensure the slot is not null
		if( wandSlot != null )
		{
			// Is this the same wand that we have cached?
			if( this.wand == wandSlot.getStack() )
			{
				// Nothing has changed
				return;
			}

			// Is the item a valid crafting wand?
			if( ThEUtils.isItemValidWand( wandSlot.getStack(), false ) )
			{
				// Set the wand
				this.wand = wandSlot.getStack();

				return;
			}
		}

		// Set the wand to null
		this.wand = null;
	}

	/**
	 * Attempts to inject an itemstack into the ME network.
	 * Adjusts the stack size of the specified itemstack according to
	 * the results of the merger.
	 * 
	 * @param itemStack
	 * @return True if any amount was merged, False otherwise.
	 */
	private boolean mergeWithMENetwork( final ItemStack itemStack )
	{
		// Attempt to place in the ME system
		IAEItemStack toInject = AEApi.instance().storage().createItemStack( itemStack );

		// Get what is left over after the injection
		IAEItemStack leftOver = this.monitor.injectItems( toInject, Actionable.MODULATE, this.playerSource );

		// Do we have any left over?
		if( ( leftOver != null ) && ( leftOver.getStackSize() > 0 ) )
		{
			// Did we inject any?
			if( leftOver.getStackSize() == toInject.getStackSize() )
			{
				// No injection occurred
				return false;
			}

			// Some was injected, adjust the slot stack size
			itemStack.stackSize = (int)leftOver.getStackSize();

			return true;
		}

		// All was injected
		itemStack.stackSize = 0;

		return true;
	}

	/**
	 * Attempts to add the itemstack to the view cell slots
	 * 
	 * @param itemStack
	 * @return True if was moved, False otherwise.
	 */
	private boolean mergeWithViewCells( final ItemStack itemStack )
	{
		// Ensure the item a view cell
		if( !this.arcaneCraftingTerminalPart.isItemValidForSlot( AEPartArcaneCraftingTerminal.VIEW_SLOT_MIN, itemStack ) )
		{
			return false;
		}

		Slot viewSlot;
		for( int viewSlotIndex = this.firstViewSlotNumber; viewSlotIndex <= this.lastViewSlotNumber; viewSlotIndex++ )
		{
			// Get the slot
			viewSlot = this.getSlot( viewSlotIndex );

			// Is there a slot?
			if( viewSlot == null )
			{
				// Somehow, there is a null slot
				continue;
			}

			// Ensure the slot is empty
			if( viewSlot.getHasStack() )
			{
				continue;
			}

			// Insert the view cell
			viewSlot.putStack( itemStack.copy() );

			// Clear the source stack
			itemStack.stackSize = 0;

			// Merge/move complete
			return true;
		}

		// Unable to move
		return false;
	}

	/**
	 * Informs the GUI that the view cells have changed
	 */
	@SideOnly(Side.CLIENT)
	private void updateGUIViewCells()
	{
		// Get the current screen being displayed to the user
		Gui gui = Minecraft.getMinecraft().currentScreen;

		// Is that screen the gui for the ACT?
		if( gui instanceof GuiArcaneCraftingTerminal )
		{
			( (GuiArcaneCraftingTerminal)gui ).onViewCellsChanged( this.getViewCells() );
		}
	}

	/**
	 * Checks if the wand has enough vis to complete the craft.
	 * Takes into consideration the players multiplier.
	 * 
	 * @param forRecipe
	 * @return ItemStack of the result if wand has enough vis, null otherwise.
	 */
	private ItemStack validateWandVisAmount( final IArcaneRecipe forRecipe )
	{
		boolean hasAll = true;
		AspectList wandAspectList = null;
		ItemWandCasting wandItem = null;

		// Get the cost
		this.requiredAspects = ArcaneRecipeHelper.INSTANCE.getRecipeAspectCost( this.arcaneCraftingTerminalPart, 0,
			ContainerPartArcaneCraftingTerminal.CRAFTING_GRID_TOTAL_SIZE, forRecipe );

		// Ensure there is a cost
		if( this.requiredAspects == null )
		{
			return null;
		}

		// Cache the recipes aspects
		Aspect[] recipeAspects = this.requiredAspects.getAspects();

		// Do we have a wand?
		if( this.wand != null )
		{
			// Get the wand item
			wandItem = ( (ItemWandCasting)this.wand.getItem() );

			// Cache the wand's aspect list
			wandAspectList = wandItem.getAllVis( this.wand );
		}

		// Check the wand amounts vs recipe aspects
		for( Aspect currentAspect : recipeAspects )
		{
			// Get the base required vis
			int baseVis = this.requiredAspects.getAmount( currentAspect );

			// Get the adjusted amount
			int requiredVis = baseVis * 100;

			// Assume we do not have enough
			boolean hasEnough = false;

			// Do we have a wand?
			if( ( wandItem != null ) && ( wandAspectList != null ) )
			{
				// Adjust the required amount by the wand modifier
				requiredVis = (int)( requiredVis * wandItem.getConsumptionModifier( this.wand, this.player, currentAspect, true ) );

				// Does the wand not have enough of vis of this aspect?
				hasEnough = ( wandAspectList.getAmount( currentAspect ) >= requiredVis );
			}

			if( !hasEnough )
			{
				// Mark that we do not have enough vis to complete crafting
				hasAll = false;
			}

			// Add to the cost list
			this.craftingCost.add( new ArcaneCrafingCost( requiredVis / 100.0F, currentAspect, hasEnough ) );
		}

		// Did we have all the vis required?
		if( hasAll )
		{
			// Get the result of the recipe.
			return ArcaneRecipeHelper.INSTANCE.getRecipeOutput( this.arcaneCraftingTerminalPart, 0,
				ContainerPartArcaneCraftingTerminal.CRAFTING_GRID_TOTAL_SIZE, forRecipe );
		}

		return null;

	}

	/**
	 * Checks if the slot number belongs to the crafting matrix
	 * 
	 * @param slotNumber
	 * @return
	 */
	protected boolean slotClickedWasInCraftingInventory( final int slotNumber )
	{
		return ( slotNumber >= this.firstCraftingSlotNumber ) && ( slotNumber <= this.lastCraftingSlotNumber );
	}

	/**
	 * Who can interact with this?
	 */
	@Override
	public boolean canInteractWith( final EntityPlayer player )
	{
		return true;
	}

	/**
	 * Changes the Y offset for all slots, except the view slots, in this
	 * container.
	 * 
	 * @param deltaY
	 */
	public void changeSlotsYOffset( final int deltaY )
	{
		for( Object slotObj : this.inventorySlots )
		{
			// Get the slot
			Slot slot = (Slot)slotObj;

			// Skip view slots
			if( ( slot.slotNumber >= this.firstViewSlotNumber ) && ( slot.slotNumber <= this.lastViewSlotNumber ) )
			{
				continue;
			}

			// Adjust Y pos
			slot.yDisplayPosition += deltaY;
		}
	}

	/**
	 * Gets the aspect cost and how much is missing for the current recipe.
	 * 
	 * @return Null if not an arcane recipe, cost otherwise.
	 */
	public List<ArcaneCrafingCost> getCraftingCost( final boolean forceUpdate )
	{
		if( forceUpdate )
		{
			this.craftingCost.clear();
			this.findMatchingArcaneResult();
		}

		// Does this recipe have costs?
		if( this.craftingCost.isEmpty() )
		{
			return null;
		}

		// Return required and missing
		//return Collections.unmodifiableList( this.craftingCost );
		return this.craftingCost;
	}

	/**
	 * Called by the monitor to ensure we still want updates
	 */
	@Override
	public boolean isValid( final Object authToken )
	{
		return true;
	}

	public void onClientNEIRequestSetCraftingGrid( final EntityPlayer player, final IAEItemStack[] gridItems )
	{
		// Attempt to clear the crafting grid
		if( this.clearCraftingGrid( false ) )
		{
			for( int craftingSlotIndex = 0; craftingSlotIndex < 9; craftingSlotIndex++ )
			{
				// Get the stack
				IAEItemStack slotStack = gridItems[craftingSlotIndex];

				// Ensure the slot was not null
				if( slotStack == null )
				{
					// Skip null items
					continue;
				}

				// Find a matching stack.
				ItemStack matchingStack = this.requestCraftingReplenishment( slotStack.getItemStack() );

				// Ensure a stack was found
				if( matchingStack != null )
				{
					// Get the slot
					Slot slot = (Slot)this.inventorySlots.get( this.firstCraftingSlotNumber + craftingSlotIndex );

					// Set the slot contents
					slot.putStack( matchingStack );
				}
			}

			// Update clients
			this.detectAndSendChanges();
		}

	}

	/**
	 * Called when a client has clicked on a craftable item.
	 * 
	 * @param player
	 * @param result
	 */
	public void onClientRequestAutoCraft( final EntityPlayer player, final IAEItemStack result )
	{
		// Get the host tile
		TileEntity te = this.arcaneCraftingTerminalPart.getHostTile();

		// Get the sided GUI ID
		int sidedID = ThEGuiHandler.generateSidedID( ThEGuiHandler.AUTO_CRAFTING_AMOUNT, this.arcaneCraftingTerminalPart.getSide() );

		// Launch the GUI
		ThEGuiHandler.launchGui( sidedID, player, te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord );

		// Setup the amount container
		if( player.openContainer instanceof ContainerCraftAmount )
		{
			// Get the container
			ContainerCraftAmount cca = (ContainerCraftAmount)this.player.openContainer;

			// Create the open context
			cca.setOpenContext( new ContainerOpenContext( te ) );
			cca.getOpenContext().setWorld( te.getWorldObj() );
			cca.getOpenContext().setX( te.xCoord );
			cca.getOpenContext().setY( te.yCoord );
			cca.getOpenContext().setZ( te.zCoord );
			cca.getOpenContext().setSide( this.arcaneCraftingTerminalPart.getSide() );

			// Set the item
			cca.getCraftingItem().putStack( result.getItemStack() );
			cca.setItemToCraft( result );

			// Issue update
			cca.detectAndSendChanges();
		}
	}

	/**
	 * Called when a client has clicked the clear grid button
	 */
	public void onClientRequestClearCraftingGrid( final EntityPlayer player )
	{
		this.clearCraftingGrid( true );
	}

	/**
	 * A client player is requesting to deposit their held item
	 * into the ME network.
	 */
	public void onClientRequestDeposit( final EntityPlayer player, final int mouseButton )
	{
		// Ensure there is a player
		if( player == null )
		{
			return;
		}

		// Get what the player is holding
		ItemStack playerHolding = player.inventory.getItemStack();

		// Is the player holding anything?
		if( playerHolding == null )
		{
			return;
		}

		// Create the AE itemstack representation of the itemstack
		IAEItemStack toInjectStack = AEApi.instance().storage().createItemStack( playerHolding );

		// Was it a right click or wheel movement?
		boolean depositOne = ( mouseButton == GuiHelper.MOUSE_BUTTON_RIGHT ) || ( mouseButton == GuiHelper.MOUSE_WHEEL_MOTION );

		if( depositOne )
		{
			// Set stack size to 1
			toInjectStack.setStackSize( 1 );
		}

		// Attempt to inject
		IAEItemStack leftOverStack = this.monitor.injectItems( toInjectStack, Actionable.MODULATE, this.playerSource );

		// Was there anything left over?
		if( ( leftOverStack != null ) && ( leftOverStack.getStackSize() > 0 ) )
		{
			// Were we only trying to inject one?
			if( toInjectStack.getStackSize() == 1 )
			{
				// No changes made
				return;
			}

			// Set what was left over as the itemstack being held
			player.inventory.setItemStack( leftOverStack.getItemStack() );
		}
		else
		{
			// Are we only depositing one, and there was more than 1 item?
			if( ( depositOne ) && ( playerHolding.stackSize > 1 ) )
			{
				// Set the player holding one less
				playerHolding.stackSize-- ;
				player.inventory.setItemStack( playerHolding );

				// Set the leftover stack to match
				leftOverStack = AEApi.instance().storage().createItemStack( playerHolding );
			}
			else
			{
				// Set the player as holding nothing
				player.inventory.setItemStack( null );
			}
		}

		// Send the update to the client
		Packet_C_ArcaneCraftingTerminal.setPlayerHeldItem( player, leftOverStack );
	}

	/**
	 * A client has requested that a region(inventory) be deposited into the ME
	 * network.
	 * 
	 * @param player
	 * @param slotNumber
	 */
	public void onClientRequestDepositRegion( final EntityPlayer player, final int slotNumber )
	{
		List<Slot> slotsToDeposit = null;

		// Was the slot part of the player inventory?
		if( this.slotClickedWasInPlayerInventory( slotNumber ) )
		{
			// Get the items in the player inventory
			slotsToDeposit = this.getNonEmptySlotsFromPlayerInventory();
		}
		// Was the slot part of the hotbar?
		else if( this.slotClickedWasInHotbarInventory( slotNumber ) )
		{
			// Get the items in the hotbar
			slotsToDeposit = this.getNonEmptySlotsFromHotbar();
		}

		// Do we have any slots to transfer?
		if( slotsToDeposit != null )
		{
			for( Slot slot : slotsToDeposit )
			{
				// Ensure the slot is not null and has a stack
				if( ( slot == null ) || ( !slot.getHasStack() ) )
				{
					continue;
				}

				// Set the stack
				ItemStack slotStack = slot.getStack();

				// Inject into the ME network
				boolean didMerge = this.mergeWithMENetwork( slotStack );

				// Did any merge?
				if( !didMerge )
				{
					continue;
				}

				// Did the merger drain the stack?
				if( ( slotStack == null ) || ( slotStack.stackSize == 0 ) )
				{
					// Set the slot to have no item
					slot.putStack( null );
				}
				else
				{
					// Inform the slot its stack changed;
					slot.onSlotChanged();
				}
			}

			// Update
			this.detectAndSendChanges();
		}
	}

	/**
	 * A client player is requesting to extract an item stack out
	 * of the ME network.
	 * 
	 * @param player
	 * @param requestedStack
	 * @param mouseButton
	 */
	public void onClientRequestExtract( final EntityPlayer player, final IAEItemStack requestedStack, final int mouseButton, final boolean isShiftHeld )
	{
		// Ensure there is a player
		if( player == null )
		{
			return;
		}

		// Ensure there is an itemstack
		if( ( requestedStack == null ) || ( requestedStack.getStackSize() == 0 ) )
		{
			return;
		}

		// Get the maximum stack size for the requested itemstack
		int maxStackSize = requestedStack.getItemStack().getMaxStackSize();

		// Determine the amount to extract
		int amountToExtract = 0;
		switch ( mouseButton )
		{
		case GuiHelper.MOUSE_BUTTON_LEFT:
			// Full amount up to maxStackSize
			amountToExtract = (int)Math.min( maxStackSize, requestedStack.getStackSize() );
			break;

		case GuiHelper.MOUSE_BUTTON_RIGHT:
			// Is shift being held?
			if( isShiftHeld )
			{
				// Extract 1
				amountToExtract = 1;
			}
			else
			{
				// Half amount up to half of maxStackSize
				double halfRequest = requestedStack.getStackSize() / 2.0D;
				double halfMax = maxStackSize / 2.0D;
				halfRequest = Math.ceil( halfRequest );
				halfMax = Math.ceil( halfMax );
				amountToExtract = (int)Math.min( halfMax, halfRequest );
			}
			break;

		case GuiHelper.MOUSE_WHEEL_MOTION:
			// Shift must be held
			if( isShiftHeld )
			{
				// Extract 1
				amountToExtract = 1;
			}
		}

		// Ensure we have some amount to extract
		if( amountToExtract <= 0 )
		{
			// Nothing to extract
			return;
		}

		// Create the stack to extract
		IAEItemStack toExtract = requestedStack.copy();

		// Set the size
		toExtract.setStackSize( amountToExtract );

		// Simulate the extraction
		IAEItemStack extractedStack = this.monitor.extractItems( toExtract, Actionable.SIMULATE, this.playerSource );

		// Did we extract anything?
		if( ( extractedStack != null ) && ( extractedStack.getStackSize() > 0 ) )
		{
			// Was this a left-click and is shift being held?
			if( ( mouseButton == GuiHelper.MOUSE_BUTTON_LEFT ) && isShiftHeld )
			{
				// Can we merge the item with the player inventory
				if( player.inventory.addItemStackToInventory( extractedStack.getItemStack() ) )
				{
					// Merged with player inventory, extract the item
					this.monitor.extractItems( toExtract, Actionable.MODULATE, this.playerSource );

					// Do not attempt to merge with what the player is holding.
					return;
				}

			}

			// Get what the player is holding
			ItemStack playerHolding = player.inventory.getItemStack();

			// Is the player holding anything?
			if( playerHolding != null )
			{
				// Can we merge with what the player is holding?
				if( ( playerHolding.stackSize < maxStackSize ) && ( playerHolding.isItemEqual( extractedStack.getItemStack() ) ) )
				{
					// Determine how much room is left in the player holding stack
					amountToExtract = Math.min( amountToExtract, maxStackSize - playerHolding.stackSize );

					// Is there any room?
					if( amountToExtract <= 0 )
					{
						// Can't merge, not enough space
						return;
					}

					// Increment what the player is holding
					playerHolding.stackSize += amountToExtract;

					// Set what the player is holding
					player.inventory.setItemStack( playerHolding );

					// Adjust extraction size
					toExtract.setStackSize( amountToExtract );
				}
				else
				{
					// Can't merge, not enough space or items don't match
					return;
				}
			}
			else
			{
				// Set the extracted item(s) as what the player is holding
				player.inventory.setItemStack( extractedStack.getItemStack() );
			}

			// Extract the item(s) from the network
			this.monitor.extractItems( toExtract, Actionable.MODULATE, this.playerSource );

			// Send the update to the client
			Packet_C_ArcaneCraftingTerminal.setPlayerHeldItem( player,
				AEApi.instance().storage().createItemStack( player.inventory.getItemStack() ) );
		}

	}

	/**
	 * A client has requested the full list of all items in the ME network.
	 * 
	 * @param player
	 */
	public void onClientRequestFullUpdate( final EntityPlayer player )
	{
		// Send the sorting info
		Packet_C_ArcaneCraftingTerminal.sendModeChange( player, this.arcaneCraftingTerminalPart.getSortingOrder(),
			this.arcaneCraftingTerminalPart.getSortingDirection(), this.arcaneCraftingTerminalPart.getViewMode() );

		// Ensure we have a monitor
		if( this.monitor != null )
		{
			// Get the full list
			IItemList<IAEItemStack> fullList = this.monitor.getStorageList();

			// Send to the client
			Packet_C_ArcaneCraftingTerminal.sendAllNetworkItems( player, fullList );
		}
	}

	/**
	 * A client has request that the stored sorting order be changed.
	 * 
	 * @param order
	 * @param dir
	 */
	public void onClientRequestSetSort( final SortOrder order, final SortDir dir, final ViewItems viewMode )
	{
		// Inform the terminal
		this.arcaneCraftingTerminalPart.setSorts( order, dir, viewMode );
	}

	/**
	 * Called when a client has requested they swap their equipped armor with
	 * the stored armor.
	 * 
	 * @param player
	 */
	public void onClientRequestSwapArmor( final EntityPlayer player )
	{
		this.arcaneCraftingTerminalPart.swapStoredArmor( player );
		this.detectAndSendChanges();
		Packet_C_ArcaneCraftingTerminal.updateAspectCost( player );
	}

	/**
	 * Unregister this container
	 */
	@Override
	public void onContainerClosed( final EntityPlayer player )
	{
		// Pass to super
		super.onContainerClosed( player );

		if( this.arcaneCraftingTerminalPart != null )
		{
			this.arcaneCraftingTerminalPart.removeListener( this );
		}

		// Is this server side?
		if( EffectiveSide.isServerSide() )
		{
			if( this.monitor != null )
			{
				this.monitor.removeListener( this );
			}
		}
	}

	/**
	 * Called when crafting inputs are changed.
	 */
	@Override
	public void onCraftMatrixChanged( final IInventory inventory )
	{
		// Reset crafting aspects
		this.requiredAspects = null;
		this.craftingCost.clear();

		// Ensure wand
		this.getWand();

		// Get the matching regular crafting recipe.
		ItemStack craftResult = this.findMatchingRegularResult();

		// Was there not a regular match?
		if( ( craftResult == null ) )
		{
			// Get the matching arcane crafting recipe.
			craftResult = this.findMatchingArcaneResult();
		}

		// Get the result slot
		SlotArcaneCraftingResult resultSlot = (SlotArcaneCraftingResult)this.getSlot( this.resultSlotNumber );

		// Set the result slot aspects and wand
		resultSlot.setResultAspects( this.requiredAspects );
		resultSlot.setWand( this.wand );

		// Set the result
		this.arcaneCraftingTerminalPart.setInventorySlotContentsWithoutNotify( AEPartArcaneCraftingTerminal.RESULT_SLOT_INDEX, craftResult );

	}

	/**
	 * AE API: called when the list updates its contents, this is mostly for
	 * handling power events.
	 */
	@Override
	public void onListUpdate()
	{
		// Ignored
	}

	/**
	 * The view cells have been changed, inform the gui.
	 */
	public void onViewCellChange()
	{
		// Only client side
		if( EffectiveSide.isClientSide() )
		{
			// Update the gui
			this.updateGUIViewCells();
		}
	}

	/**
	 * Called when the amount of an item on the network changes.
	 */
	@Override
	public void postChange( final IBaseMonitor<IAEItemStack> monitor, final Iterable<IAEItemStack> changes, final BaseActionSource actionSource )
	{
		for( IAEItemStack change : changes )
		{
			// Get the total amount of the item in the network
			IAEItemStack newAmount = this.monitor.getStorageList().findPrecise( change );

			// Is there no more?
			if( newAmount == null )
			{
				// Copy the item type from the change
				newAmount = change.copy();

				// Set amount to 0
				newAmount.setStackSize( 0 );
			}

			// Send the change to the client
			Packet_C_ArcaneCraftingTerminal.stackAmountChanged( this.player, newAmount );
		}
	}

	/**
	 * Registers the container for updates from the terminal part.
	 */
	public void registerForUpdates()
	{
		// Register the container with terminal
		this.arcaneCraftingTerminalPart.registerListener( this );
	}

	/**
	 * Attempts to extract an item from the network.
	 * Used when crafting to replenish the crafting grid.
	 * 
	 * @param itemStack
	 * @return
	 */
	public ItemStack requestCraftingReplenishment( final ItemStack itemStack )
	{
		// Create the request stack
		IAEItemStack requestStack = AEApi.instance().storage().createItemStack( itemStack );

		// Set the request amount to one
		requestStack.setStackSize( 1 );

		// Attempt an extraction
		IAEItemStack replenishment = this.monitor.extractItems( requestStack, Actionable.MODULATE, this.playerSource );

		// Did we get a replenishment?
		if( replenishment != null )
		{
			return replenishment.getItemStack();
		}

		// Did not get a replenishment, search for items that match. 

		// Get a list of all items in the ME network
		IItemList<IAEItemStack> networkItems = this.monitor.getStorageList();

		// Search all items
		for( IAEItemStack potentialMatch : networkItems )
		{
			// Does the request match?
			if( this.doStacksMatch( requestStack, potentialMatch ) )
			{
				// Found a match
				requestStack = potentialMatch.copy();

				// Set the request amount to one
				requestStack.setStackSize( 1 );

				// Attempt an extraction
				replenishment = this.monitor.extractItems( requestStack, Actionable.MODULATE, this.playerSource );

				// Did we get a replenishment?
				if( ( replenishment != null ) && ( replenishment.getStackSize() > 0 ) )
				{
					return replenishment.getItemStack();
				}
			}

		}

		// No matches at all :(
		return null;
	}

	/**
	 * Called when a slot is clicked by the player.
	 */
	@Override
	public ItemStack slotClick( final int slotID, final int button, final int flag, final EntityPlayer player )
	{
		if( ( slotID == this.resultSlotNumber ) && ( button == GuiHelper.MOUSE_BUTTON_RIGHT ) )
		{
			// If right clicking on result slot, change it to left click
			return super.slotClick( slotID, GuiHelper.MOUSE_BUTTON_LEFT, flag, player );
		}

		// Pass to super
		return super.slotClick( slotID, button, flag, player );

	}

	/**
	 * Called when the player shift+clicks on a slot
	 */
	@Override
	public ItemStack transferStackInSlot( final EntityPlayer player, final int slotNumber )
	{
		// Is this client side?
		if( EffectiveSide.isClientSide() )
		{
			// Do nothing.
			return null;
		}

		// Get the slot that was shift-clicked
		Slot slot = (Slot)this.inventorySlots.get( slotNumber );

		// Is there a valid slot with and item?
		if( ( slot != null ) && ( slot.getHasStack() ) )
		{
			boolean didMerge = false;

			// Get the itemstack in the slot
			ItemStack slotStack = slot.getStack();

			// Was the slot clicked in the crafting grid or wand?
			if( ( slotNumber == this.wandSlotNumber ) || this.slotClickedWasInCraftingInventory( slotNumber ) )
			{
				// Attempt to merge with the ME network
				didMerge = this.mergeWithMENetwork( slotStack );

				// Did we merge?
				if( !didMerge )
				{
					// Attempt to merge with the hotbar
					didMerge = this.mergeSlotWithHotbarInventory( slotStack );

					// Did we merge?
					if( !didMerge )
					{
						// Attempt to merge with the player inventory
						didMerge = this.mergeSlotWithPlayerInventory( slotStack );
					}
				}
			}
			// Was the slot clicked in the player or hotbar inventory?
			else if( this.slotClickedWasInPlayerInventory( slotNumber ) || this.slotClickedWasInHotbarInventory( slotNumber ) )
			{

				// Is the item a valid wand?
				if( this.getSlot( this.wandSlotNumber ).isItemValid( slotStack ) )
				{
					// Attempt to merge with the wand
					didMerge = this.mergeItemStack( slotStack, this.wandSlotNumber, this.wandSlotNumber + 1, false );
				}

				// Did we merge?
				if( !didMerge )
				{
					// Attempt to merge with view cells
					didMerge = this.mergeWithViewCells( slotStack );

					// Did we merge?
					if( !didMerge )
					{
						// Attempt to merge with the ME network
						didMerge = this.mergeWithMENetwork( slotStack );

						// Did we merge?
						if( !didMerge )
						{
							// Attempt to merge with the crafting grid
							didMerge = this.mergeItemStack( slotStack, this.firstCraftingSlotNumber, this.lastCraftingSlotNumber + 1, false );

							// Did we merge?
							if( !didMerge )
							{
								// Attempt to swap hotbar<->player inventory
								didMerge = this.swapSlotInventoryHotbar( slotNumber, slotStack );
							}
						}
					}
				}
			}
			// Was the slot clicked the crafting result?
			else if( slotNumber == this.resultSlotNumber )
			{
				// Start the autocrafting loop
				this.doShiftAutoCrafting( player );

				return null;
			}
			// Was the slot clicked a view cell?
			else if( ( slotNumber >= this.firstViewSlotNumber ) && ( slotNumber <= this.lastViewSlotNumber ) )
			{
				// Attempt to merge with the hotbar
				didMerge = this.mergeSlotWithHotbarInventory( slotStack );

				// Did we merge?
				if( !didMerge )
				{
					// Attempt to merge with the player inventory
					didMerge = this.mergeSlotWithPlayerInventory( slotStack );
				}
			}

			// Did we merge?
			if( didMerge )
			{

				// Did the merger drain the stack?
				if( ( slotStack == null ) || ( slotStack.stackSize == 0 ) )
				{
					// Set the slot to have no item
					slot.putStack( null );
				}
				else
				{
					// Inform the slot its stack changed;
					slot.onSlotChanged();
				}

				// Send changes
				this.detectAndSendChanges();
			}

		}

		// All taken care of!
		return null;
	}
}
