package thaumicenergistics.parts;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IAspectContainer;
import thaumicenergistics.container.ContainerPartEssentiaIOBus;
import thaumicenergistics.gui.GuiEssentiaIO;
import thaumicenergistics.integration.tc.EssentiaItemContainerHelper;
import thaumicenergistics.network.IAspectSlotPart;
import thaumicenergistics.network.packet.client.Packet_C_AspectSlot;
import thaumicenergistics.network.packet.client.Packet_C_EssentiaIOBus;
import thaumicenergistics.registries.AEPartsEnum;
import thaumicenergistics.registries.EnumCache;
import thaumicenergistics.util.EffectiveSide;
import thaumicenergistics.util.IInventoryUpdateReceiver;
import appeng.api.AEApi;
import appeng.api.config.RedstoneMode;
import appeng.api.definitions.IMaterials;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.PartItemStack;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class AbstractAEPartEssentiaIOBus
	extends AbstractAEPartBase
	implements IGridTickable, IInventoryUpdateReceiver, IAspectSlotPart, IAEAppEngInventory
{
	/**
	 * How much essentia can be transfered per second.
	 */
	private final static int BASE_TRANSFER_PER_SECOND = 4;

	/**
	 * How much additional essentia can be transfered per upgrade.
	 */
	private final static int ADDITIONAL_TRANSFER_PER_SECOND = 8;

	private final static int MINIMUM_TICKS_PER_OPERATION = 10;

	private final static int MAXIMUM_TICKS_PER_OPERATION = 40;

	private final static int MAXIMUM_TRANSFER_PER_SECOND = 64;

	private final static int MINIMUM_TRANSFER_PER_SECOND = 1;

	/**
	 * Maximum number of filter slots.
	 */
	private final static int MAX_FILTER_SIZE = 9;

	private final static int BASE_SLOT_INDEX = 4;

	private final static int[] TIER2_INDEXS = { 0, 2, 6, 8 };

	private final static int[] TIER1_INDEXS = { 1, 3, 5, 7 };

	private final static int UPGRADE_INVENTORY_SIZE = 4;

	/**
	 * How much AE power is required to keep the part active.
	 */
	private static final double IDLE_POWER_DRAIN = 0.7;

	/**
	 * Default redstone mode for the bus.
	 */
	private static final RedstoneMode DEFAULT_REDSTONE_MODE = RedstoneMode.IGNORE;

	private boolean lastRedstone;

	private int[] availableFilterSlots = { AbstractAEPartEssentiaIOBus.BASE_SLOT_INDEX };

	private UpgradeInventory upgradeInventory = new StackUpgradeInventory( this.associatedItem, this,
					AbstractAEPartEssentiaIOBus.UPGRADE_INVENTORY_SIZE );

	private List<ContainerPartEssentiaIOBus> listeners = new ArrayList<ContainerPartEssentiaIOBus>();

	/**
	 * How the bus responds to redstone.
	 */
	private RedstoneMode redstoneMode = AbstractAEPartEssentiaIOBus.DEFAULT_REDSTONE_MODE;

	/**
	 * Network source representing this part.
	 */
	protected MachineSource asMachineSource;

	protected List<Aspect> filteredAspects = new ArrayList<Aspect>( AbstractAEPartEssentiaIOBus.MAX_FILTER_SIZE );

	protected IAspectContainer facingContainer;

	protected byte filterSize;

	protected byte upgradeSpeedCount = 0;

	protected boolean redstoneControlled;

	/**
	 * NBT Keys
	 */
	private static final String NBT_KEY_REDSTONE_MODE = "redstoneMode", NBT_KEY_FILTER_NUMBER = "AspectFilter#",
					NBT_KEY_UPGRADE_INV = "upgradeInventory";

	public AbstractAEPartEssentiaIOBus( final AEPartsEnum associatedPart )
	{
		super( associatedPart );

		// Initialize the list
		for( int index = 0; index < AbstractAEPartEssentiaIOBus.MAX_FILTER_SIZE; index++ )
		{
			this.filteredAspects.add( null );
		}

		// Create the source
		this.asMachineSource = new MachineSource( this );
	}

	private boolean canDoWork()
	{
		boolean canWork = true;

		if( this.redstoneControlled )
		{
			switch ( this.getRedstoneMode() )
			{
			case HIGH_SIGNAL:
				canWork = this.isReceivingRedstonePower();

				break;
			case IGNORE:
				break;

			case LOW_SIGNAL:
				canWork = !this.isReceivingRedstonePower();

				break;
			case SIGNAL_PULSE:
				canWork = false;
				break;
			}
		}

		return canWork;

	}

	private int getTransferAmountPerSecond()
	{
		return BASE_TRANSFER_PER_SECOND + ( this.upgradeSpeedCount * ADDITIONAL_TRANSFER_PER_SECOND );
	}

	private void notifyListenersOfFilterAspectChange()
	{
		for( ContainerPartEssentiaIOBus listener : this.listeners )
		{
			listener.setFilteredAspect( this.filteredAspects );
		}
	}

	private void notifyListenersOfFilterSizeChange()
	{
		for( ContainerPartEssentiaIOBus listener : this.listeners )
		{
			listener.setFilterSize( this.filterSize );
		}
	}

	private void notifyListenersOfRedstoneControlledChange()
	{
		for( ContainerPartEssentiaIOBus listener : this.listeners )
		{
			listener.setRedstoneControlled( this.redstoneControlled );
		}
	}

	private void notifyListenersOfRedstoneModeChange()
	{
		for( ContainerPartEssentiaIOBus listener : this.listeners )
		{
			listener.setRedstoneMode( this.redstoneMode );
		}

	}

	private void resizeAvailableArray()
	{
		// Resize the available slots
		this.availableFilterSlots = new int[1 + ( this.filterSize * 4 )];

		// Add the base slot
		this.availableFilterSlots[0] = AbstractAEPartEssentiaIOBus.BASE_SLOT_INDEX;

		if( this.filterSize < 2 )
		{
			// Reset tier 2 slots
			for( int i = 0; i < AbstractAEPartEssentiaIOBus.TIER2_INDEXS.length; i++ )
			{
				this.filteredAspects.set( AbstractAEPartEssentiaIOBus.TIER2_INDEXS[i], null );
			}

			if( this.filterSize < 1 )
			{
				// Reset tier 1 slots
				for( int i = 0; i < AbstractAEPartEssentiaIOBus.TIER1_INDEXS.length; i++ )
				{
					this.filteredAspects.set( AbstractAEPartEssentiaIOBus.TIER1_INDEXS[i], null );
				}
			}
			else
			{
				// Tier 1 slots
				System.arraycopy( AbstractAEPartEssentiaIOBus.TIER1_INDEXS, 0, this.availableFilterSlots, 1, 4 );
			}
		}
		else
		{
			// Add both
			System.arraycopy( AbstractAEPartEssentiaIOBus.TIER1_INDEXS, 0, this.availableFilterSlots, 1, 4 );
			System.arraycopy( AbstractAEPartEssentiaIOBus.TIER2_INDEXS, 0, this.availableFilterSlots, 5, 4 );
		}
	}

	public boolean addFilteredAspectFromItemstack( final EntityPlayer player, final ItemStack itemStack )
	{
		Aspect itemAspect = EssentiaItemContainerHelper.INSTANCE.getFilterAspectFromItem( itemStack );

		if( itemAspect != null )
		{
			// Are we already filtering this aspect?
			if( this.filteredAspects.contains( itemAspect ) )
			{
				return true;
			}

			// Add to the first open slot
			for( int avalibleIndex = 0; avalibleIndex < this.availableFilterSlots.length; avalibleIndex++ )
			{
				int filterIndex = this.availableFilterSlots[avalibleIndex];

				// Is this space empty?
				if( this.filteredAspects.get( filterIndex ) == null )
				{
					// Is this server side?
					if( EffectiveSide.isServerSide() )
					{
						// Set the filter
						this.setAspect( filterIndex, itemAspect, player );
					}

					return true;
				}
			}
		}

		return false;
	}

	public void addListener( final ContainerPartEssentiaIOBus container )
	{
		if( !this.listeners.contains( container ) )
		{
			this.listeners.add( container );
		}
	}

	public abstract boolean aspectTransferAllowed( Aspect aspect );

	@Override
	public int cableConnectionRenderTo()
	{
		return 5;
	}

	public abstract boolean doWork( int transferAmount );

	@Override
	public Object getClientGuiElement( final EntityPlayer player )
	{
		return new GuiEssentiaIO( this, player );
	}

	@Override
	public void getDrops( final List<ItemStack> drops, final boolean wrenched )
	{
		// Were we wrenched?
		if( wrenched )
		{
			// No drops
			return;
		}

		// Add upgrades to drops
		for( int slotIndex = 0; slotIndex < AbstractAEPartEssentiaIOBus.UPGRADE_INVENTORY_SIZE; slotIndex++ )
		{
			// Get the upgrade card in this slot
			ItemStack slotStack = this.upgradeInventory.getStackInSlot( slotIndex );

			// Is it not null?
			if( ( slotStack != null ) && ( slotStack.stackSize > 0 ) )
			{
				// Add to the drops
				drops.add( slotStack );
			}
		}
	}

	/**
	 * Determines how much power the part takes for just
	 * existing.
	 */
	@Override
	public double getIdlePowerUsage()
	{
		return AbstractAEPartEssentiaIOBus.IDLE_POWER_DRAIN;
	}

	/**
	 * Produces a small amount of light when active.
	 */
	@Override
	public int getLightLevel()
	{
		return( this.isActive() ? 4 : 0 );
	}

	public RedstoneMode getRedstoneMode()
	{
		return this.redstoneMode;
	}

	@Override
	public Object getServerGuiElement( final EntityPlayer player )
	{
		return new ContainerPartEssentiaIOBus( this, player );
	}

	@Override
	public TickingRequest getTickingRequest( final IGridNode arg0 )
	{
		return new TickingRequest( MINIMUM_TICKS_PER_OPERATION, MAXIMUM_TICKS_PER_OPERATION, false, false );
	}

	public UpgradeInventory getUpgradeInventory()
	{
		return this.upgradeInventory;
	}

	@Override
	public boolean onActivate( final EntityPlayer player, final Vec3 position )
	{
		boolean activated = super.onActivate( player, position );

		this.onInventoryChanged( null );

		return activated;
	}

	@Override
	public void onChangeInventory( final IInventory inv, final int slot, final InvOperation mc, final ItemStack removedStack, final ItemStack newStack )
	{
		if( inv == this.upgradeInventory )
		{
			this.onInventoryChanged( inv );
		}
	}

	/**
	 * Called when a player has clicked the redstone button in the gui.
	 * 
	 * @param player
	 */
	public void onClientRequestChangeRedstoneMode( final EntityPlayer player )
	{
		// Get the current ordinal, and increment it
		int nextOrdinal = this.redstoneMode.ordinal() + 1;

		// Bounds check
		if( nextOrdinal >= EnumCache.AE_REDSTONE_MODES.length )
		{
			nextOrdinal = 0;
		}

		// Set the mode
		this.redstoneMode = EnumCache.AE_REDSTONE_MODES[nextOrdinal];

		// Notify listeners
		this.notifyListenersOfRedstoneModeChange();
	}

	/**
	 * Called when a client gui is requesting a full update.
	 * 
	 * @param player
	 */
	public void onClientRequestFullUpdate( final EntityPlayer player )
	{
		// Set the filter list
		Packet_C_AspectSlot.setFilterList( this.filteredAspects, player );

		// Set the state of the bus
		Packet_C_EssentiaIOBus.sendBusState( player, this.redstoneMode, this.filterSize, this.redstoneControlled );
	}

	@Override
	public void onInventoryChanged( final IInventory sourceInventory )
	{
		int oldFilterSize = this.filterSize;

		this.filterSize = 0;
		this.redstoneControlled = false;
		this.upgradeSpeedCount = 0;

		IMaterials aeMaterals = AEApi.instance().definitions().materials();

		for( int i = 0; i < this.upgradeInventory.getSizeInventory(); i++ )
		{
			ItemStack slotStack = this.upgradeInventory.getStackInSlot( i );

			if( slotStack != null )
			{
				if( aeMaterals.cardCapacity().isSameAs( slotStack ) )
				{
					this.filterSize++ ;
				}
				else if( aeMaterals.cardRedstone().isSameAs( slotStack ) )
				{
					this.redstoneControlled = true;
				}
				else if( aeMaterals.cardSpeed().isSameAs( slotStack ) )
				{
					this.upgradeSpeedCount++ ;
				}
			}
		}

		// Did the filter size change?
		if( oldFilterSize != this.filterSize )
		{
			this.resizeAvailableArray();
		}

		// Is this client side?
		if( EffectiveSide.isClientSide() )
		{
			return;
		}

		this.notifyListenersOfFilterSizeChange();

		this.notifyListenersOfRedstoneControlledChange();
	}

	@Override
	public void onNeighborChanged()
	{
		// Call super
		super.onNeighborChanged();

		// Ignored client side
		if( EffectiveSide.isClientSide() )
		{
			return;
		}

		// Set that we are not facing a container
		this.facingContainer = null;

		// Get the tile we are facing
		TileEntity tileEntity = this.getFacingTile();

		// Are we facing a container?
		if( tileEntity instanceof IAspectContainer )
		{
			this.facingContainer = (IAspectContainer)tileEntity;
		}

		// Is the bus pulse controlled?
		if( this.redstoneMode == RedstoneMode.SIGNAL_PULSE )
		{
			// Did the state of the redstone change?
			if( this.isReceivingRedstonePower() != this.lastRedstone )
			{
				// Set the previous redstone state
				this.lastRedstone = this.isReceivingRedstonePower();

				// Do work
				this.doWork( this.getTransferAmountPerSecond() );
			}
		}
	}

	/**
	 * Called client-side to keep the client-side part in sync
	 * with the server-side part. This aids in keeping the
	 * gui in sync even in high network lag enviroments.
	 * 
	 * @param filteredAspects
	 */
	@SideOnly(Side.CLIENT)
	public void onReceiveFilterList( final List<Aspect> filteredAspects )
	{
		this.filteredAspects = filteredAspects;
	}

	/**
	 * Called client-side to keep the client-side part in sync
	 * with the server-side part. This aids in keeping the
	 * gui in sync even in high network lag enviroments.
	 * 
	 * @param filterSize
	 */
	@SideOnly(Side.CLIENT)
	public void onReceiveFilterSize( final byte filterSize )
	{
		this.filterSize = filterSize;

		this.resizeAvailableArray();
	}

	@Override
	public void readFromNBT( final NBTTagCompound data )
	{
		// Call super
		super.readFromNBT( data );

		// Read redstone mode
		if( data.hasKey( AbstractAEPartEssentiaIOBus.NBT_KEY_REDSTONE_MODE ) )
		{
			this.redstoneMode = EnumCache.AE_REDSTONE_MODES[data.getInteger( AbstractAEPartEssentiaIOBus.NBT_KEY_REDSTONE_MODE )];
		}

		// Read filters
		for( int index = 0; index < AbstractAEPartEssentiaIOBus.MAX_FILTER_SIZE; index++ )
		{
			if( data.hasKey( AbstractAEPartEssentiaIOBus.NBT_KEY_FILTER_NUMBER + index ) )
			{
				Aspect filterAspect = null;

				// Get the name of the aspect
				String aspectTag = data.getString( AbstractAEPartEssentiaIOBus.NBT_KEY_FILTER_NUMBER + index );
				if( !aspectTag.equals( "" ) )
				{
					filterAspect = Aspect.aspects.get( aspectTag );

				}

				this.filteredAspects.set( index, filterAspect );
			}
		}

		// Read upgrade inventory
		if( data.hasKey( AbstractAEPartEssentiaIOBus.NBT_KEY_UPGRADE_INV ) )
		{
			this.upgradeInventory.readFromNBT( data, AbstractAEPartEssentiaIOBus.NBT_KEY_UPGRADE_INV );

			this.onInventoryChanged( this.upgradeInventory );
		}
	}

	public void removeListener( final ContainerPartEssentiaIOBus container )
	{
		this.listeners.remove( container );
	}

	/**
	 * Called when the internal inventory changes.
	 */
	@Override
	public void saveChanges()
	{
		this.markForSave();
	}

	@Override
	public final void setAspect( final int index, final Aspect aspect, final EntityPlayer player )
	{
		// Set the filter
		this.filteredAspects.set( index, aspect );

		// Update the listeners
		this.notifyListenersOfFilterAspectChange();
	}

	@Override
	public TickRateModulation tickingRequest( final IGridNode node, final int ticksSinceLastCall )
	{
		if( this.canDoWork() )
		{
			// Calculate the amount to transfer per second
			int transferAmountPerSecond = this.getTransferAmountPerSecond();

			// Calculate amount to transfer this operation
			int transferAmount = (int)( transferAmountPerSecond * ( ticksSinceLastCall / 20.F ) );

			// Clamp
			if( transferAmount < MINIMUM_TRANSFER_PER_SECOND )
			{
				transferAmount = MINIMUM_TRANSFER_PER_SECOND;
			}
			else if( transferAmount > MAXIMUM_TRANSFER_PER_SECOND )
			{
				transferAmount = MAXIMUM_TRANSFER_PER_SECOND;
			}

			if( this.doWork( transferAmount ) )
			{
				return TickRateModulation.URGENT;
			}
		}

		return TickRateModulation.IDLE;
	}

	@Override
	public void writeToNBT( final NBTTagCompound data, final PartItemStack saveType )
	{
		// Call super
		super.writeToNBT( data, saveType );

		// Write the redstone mode
		if( this.redstoneMode != AbstractAEPartEssentiaIOBus.DEFAULT_REDSTONE_MODE )
		{
			data.setInteger( AbstractAEPartEssentiaIOBus.NBT_KEY_REDSTONE_MODE, this.redstoneMode.ordinal() );
		}

		// Write each filter
		for( int i = 0; i < AbstractAEPartEssentiaIOBus.MAX_FILTER_SIZE; i++ )
		{
			Aspect aspect = this.filteredAspects.get( i );
			String aspectTag = "";

			if( aspect != null )
			{
				aspectTag = aspect.getTag();
			}

			data.setString( AbstractAEPartEssentiaIOBus.NBT_KEY_FILTER_NUMBER + i, aspectTag );
		}

		// Write the upgrade inventory
		if( !this.upgradeInventory.isEmpty() )
		{
			this.upgradeInventory.writeToNBT( data, AbstractAEPartEssentiaIOBus.NBT_KEY_UPGRADE_INV );
		}
	}
}
