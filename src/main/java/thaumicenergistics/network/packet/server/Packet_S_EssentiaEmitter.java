package thaumicenergistics.network.packet.server;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import thaumicenergistics.network.NetworkHandler;
import thaumicenergistics.network.packet.ThEBasePacket;
import thaumicenergistics.network.packet.ThEServerPacket;
import thaumicenergistics.parts.AEPartEssentiaLevelEmitter;

public class Packet_S_EssentiaEmitter
	extends ThEServerPacket
{
	private static final byte MODE_REQUEST_UPDATE = 0;

	private static final byte MODE_SEND_WANTED = 1;

	private static final byte MODE_ADJUST_WANTED = 2;

	private static final byte MODE_TOGGLE_REDSTONE = 3;

	private AEPartEssentiaLevelEmitter part;

	private long wantedAmount;

	private int adjustmentAmount;

	/**
	 * Creates the packet
	 * 
	 * @param player
	 * @param mode
	 * @return
	 */
	private static Packet_S_EssentiaEmitter newPacket( final EntityPlayer player, final byte mode )
	{
		// Create the packet
		Packet_S_EssentiaEmitter packet = new Packet_S_EssentiaEmitter();

		// Set the player & mode
		packet.player = player;
		packet.mode = mode;

		return packet;
	}

	public static void sendRedstoneModeToggle( final AEPartEssentiaLevelEmitter part, final EntityPlayer player )
	{
		Packet_S_EssentiaEmitter packet = newPacket( player, MODE_TOGGLE_REDSTONE );

		// Set the part
		packet.part = part;

		// Send it
		NetworkHandler.sendPacketToServer( packet );
	}

	/**
	 * Creates a packet to let the server know a client would like a full
	 * update.
	 * 
	 * @param part
	 * @param player
	 * @return
	 */
	public static void sendUpdateRequest( final AEPartEssentiaLevelEmitter part, final EntityPlayer player )
	{
		Packet_S_EssentiaEmitter packet = newPacket( player, MODE_REQUEST_UPDATE );

		// Set the part
		packet.part = part;

		// Send it
		NetworkHandler.sendPacketToServer( packet );
	}

	/**
	 * Creates a packet to update the wanted amount on the server
	 * 
	 * @param wantedAmount
	 * @param part
	 * @param player
	 * @return
	 */
	public static void sendWantedAmount( final long wantedAmount, final AEPartEssentiaLevelEmitter part,
											final EntityPlayer player )
	{
		Packet_S_EssentiaEmitter packet = newPacket( player, MODE_SEND_WANTED );

		// Set the part
		packet.part = part;

		// Set the wanted amount
		packet.wantedAmount = wantedAmount;

		// Send it
		NetworkHandler.sendPacketToServer( packet );
	}

	/**
	 * Creates a packet to adjust the wanted amount on the server
	 * 
	 * @param adjustmentAmount
	 * @param part
	 * @param player
	 * @return
	 */
	public static void sendWantedAmountDelta( final int adjustmentAmount, final AEPartEssentiaLevelEmitter part,
												final EntityPlayer player )
	{
		Packet_S_EssentiaEmitter packet = newPacket( player, MODE_ADJUST_WANTED );

		// Set the part
		packet.part = part;

		// Set the adjustment
		packet.adjustmentAmount = adjustmentAmount;

		// Send it
		NetworkHandler.sendPacketToServer( packet );
	}

	@Override
	public void execute()
	{
		switch ( this.mode )
		{
		case Packet_S_EssentiaEmitter.MODE_REQUEST_UPDATE:
			// Request the full update
			this.part.onClientUpdateRequest( this.player );
			break;

		case Packet_S_EssentiaEmitter.MODE_SEND_WANTED:
			// Set the wanted amount
			this.part.onClientSetWantedAmount( this.wantedAmount, this.player );
			break;

		case Packet_S_EssentiaEmitter.MODE_ADJUST_WANTED:
			// Set the adjustment amount
			this.part.onClientAdjustWantedAmount( this.adjustmentAmount, this.player );
			break;

		case Packet_S_EssentiaEmitter.MODE_TOGGLE_REDSTONE:
			// Toggle the redstone mode
			this.part.onClientToggleRedstoneMode( this.player );
			break;
		}
	}

	@Override
	public void readData( final ByteBuf stream )
	{
		switch ( this.mode )
		{
		case Packet_S_EssentiaEmitter.MODE_REQUEST_UPDATE:
			// Read the part
			this.part = (AEPartEssentiaLevelEmitter)ThEBasePacket.readPart( stream );
			break;

		case Packet_S_EssentiaEmitter.MODE_SEND_WANTED:
			// Read the part
			this.part = (AEPartEssentiaLevelEmitter)ThEBasePacket.readPart( stream );

			// Read the wanted amount
			this.wantedAmount = stream.readLong();
			break;

		case Packet_S_EssentiaEmitter.MODE_ADJUST_WANTED:
			// Read the part
			this.part = (AEPartEssentiaLevelEmitter)ThEBasePacket.readPart( stream );

			// Read the adjustment amount
			this.adjustmentAmount = stream.readInt();
			break;

		case Packet_S_EssentiaEmitter.MODE_TOGGLE_REDSTONE:
			// Read the part
			this.part = (AEPartEssentiaLevelEmitter)ThEBasePacket.readPart( stream );
			break;
		}
	}

	@Override
	public void writeData( final ByteBuf stream )
	{
		switch ( this.mode )
		{
		case Packet_S_EssentiaEmitter.MODE_REQUEST_UPDATE:
			// Write the part
			ThEBasePacket.writePart( this.part, stream );
			break;

		case Packet_S_EssentiaEmitter.MODE_SEND_WANTED:
			// Write the part
			ThEBasePacket.writePart( this.part, stream );

			// Write wanted amount
			stream.writeLong( this.wantedAmount );
			break;

		case Packet_S_EssentiaEmitter.MODE_ADJUST_WANTED:
			// Write the part
			ThEBasePacket.writePart( this.part, stream );

			// Write the adjustment amount
			stream.writeInt( this.adjustmentAmount );
			break;

		case Packet_S_EssentiaEmitter.MODE_TOGGLE_REDSTONE:
			// Write the part
			ThEBasePacket.writePart( this.part, stream );
			break;
		}
	}

}
