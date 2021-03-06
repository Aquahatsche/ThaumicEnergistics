package thaumicenergistics.network.packet.client;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.player.EntityPlayer;
import thaumicenergistics.gui.GuiEssentiaStorageBus;
import thaumicenergistics.network.NetworkHandler;
import thaumicenergistics.network.packet.ThEClientPacket;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class Packet_C_EssentiaStorageBus
	extends ThEClientPacket
{
	private static final byte MODE_SET_VOID = 0;

	private boolean isVoidAllowed;

	public static void sendIsVoidAllowed( final EntityPlayer player, final boolean isVoidAllowed )
	{
		Packet_C_EssentiaStorageBus packet = new Packet_C_EssentiaStorageBus();

		// Set the player
		packet.player = player;

		// Set the mode
		packet.mode = Packet_C_EssentiaStorageBus.MODE_SET_VOID;

		// Set void
		packet.isVoidAllowed = isVoidAllowed;

		// Send it
		NetworkHandler.sendPacketToClient( packet );
	}

	@Override
	protected void readData( final ByteBuf stream )
	{
		switch ( this.mode )
		{
		case Packet_C_EssentiaStorageBus.MODE_SET_VOID:
			// Read void mode
			this.isVoidAllowed = stream.readBoolean();
			break;
		}
	}

	@SideOnly(Side.CLIENT)
	@Override
	protected void wrappedExecute()
	{
		// Get the gui
		Gui gui = Minecraft.getMinecraft().currentScreen;

		// Ensure the gui is a GuiEssentiaStorageBus
		if( !( gui instanceof GuiEssentiaStorageBus ) )
		{
			return;
		}

		switch ( this.mode )
		{
		case Packet_C_EssentiaStorageBus.MODE_SET_VOID:
			// Set void mode
			( (GuiEssentiaStorageBus)gui ).onServerSentVoidMode( this.isVoidAllowed );
			break;
		}

	}

	@Override
	protected void writeData( final ByteBuf stream )
	{
		switch ( this.mode )
		{
		case Packet_C_EssentiaStorageBus.MODE_SET_VOID:
			// Write void mode
			stream.writeBoolean( this.isVoidAllowed );
			break;
		}
	}

}
