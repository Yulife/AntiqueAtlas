package hunternif.mc.atlas.network;

import hunternif.mc.atlas.AntiqueAtlasMod;
import hunternif.mc.atlas.marker.Marker;
import hunternif.mc.atlas.marker.MarkersData;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import cpw.mods.fml.common.network.ByteBufUtils;

/** Sends markers set via API from server to client, sends player-defined
 * markers from client to server. Only one dimension per packet.
 * @author Hunternif
 */
public class MarkersPacket extends ModPacket {
	protected int atlasID;
	protected int dimension;
	protected final ListMultimap<String, Marker> markersByType = ArrayListMultimap.create();
	
	public MarkersPacket() {}
	
	public MarkersPacket(int atlasID, int dimension, Marker... markers) {
		this.atlasID = atlasID;
		this.dimension = dimension;
		for (Marker marker : markers) {
			markersByType.put(marker.getType(), marker);
		}
	}
	
	public MarkersPacket putMarker(Marker marker) {
		markersByType.put(marker.getType(), marker);
		return this;
	}

	@Override
	public void encodeInto(ChannelHandlerContext ctx, ByteBuf buffer) {
		buffer.writeShort(atlasID);
		buffer.writeShort(dimension);
		Set<String> types = markersByType.keySet();
		buffer.writeShort(types.size());
		for (String type : types) {
			ByteBufUtils.writeUTF8String(buffer, type);
			List<Marker> markers = markersByType.get(type);
			buffer.writeShort(markers.size());
			for (Marker marker : markers) {
				ByteBufUtils.writeUTF8String(buffer, marker.getLabel());
				buffer.writeInt(marker.getX());
				buffer.writeInt(marker.getY());
			}
		}
	}

	@Override
	public void decodeInto(ChannelHandlerContext ctx, ByteBuf buffer) {
		atlasID = buffer.readShort();
		dimension = buffer.readShort();
		int typesLength = buffer.readShort();
		for (int i = 0; i < typesLength; i++) {
			String type = ByteBufUtils.readUTF8String(buffer);
			int markersLength = buffer.readShort();
			for (int j = 0; j < markersLength; j++) {
				Marker marker = new Marker(type, ByteBufUtils.readUTF8String(buffer), buffer.readInt(), buffer.readInt());
				markersByType.put(type, marker);
			}
		}
	}

	@Override
	public void handleClientSide(EntityPlayer player) {
		MarkersData markersData = AntiqueAtlasMod.itemAtlas.getClientMarkersData(atlasID);
		for (Marker marker : markersByType.values()) {
			markersData.putMarker(dimension, marker);
		}
	}
	
	@Override
	public void handleServerSide(EntityPlayer player) {
		MarkersData markersData = AntiqueAtlasMod.itemAtlas.getMarkersData(atlasID, player.worldObj);
		for (Marker marker : markersByType.values()) {
			markersData.putMarker(dimension, marker);
		}
		markersData.markDirty();
		// If these are a manually set markers sent from the client, forward
		// them to other players. Including the original sender, because he
		// waits on the server to verify his marker.
		AntiqueAtlasMod.packetPipeline.sendToWorld(this, player.worldObj);
	}
	
	public boolean isEmpty() {
		return markersByType.isEmpty();
	}

}